package gov.cdc.dex.csv.functions.activity

import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger;

import gov.cdc.dex.csv.dtos.ActivityInput
import gov.cdc.dex.csv.dtos.ActivityOutput
import gov.cdc.dex.csv.services.IBlobService
import gov.cdc.dex.csv.services.AzureBlobServiceImpl
import gov.cdc.dex.csv.constants.EnvironmentParam

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.Reader
import java.io.StringReader
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.zip.ZipInputStream

import uk.gov.nationalarchives.csv.validator.api.java.CsvValidator;
import uk.gov.nationalarchives.csv.validator.api.java.FailMessage;
import uk.gov.nationalarchives.csv.validator.api.java.Substitution;

class FnValidateSchemaEntry{
    @FunctionName("FnValidateSchema")
    fun process(
        @DurableActivityTrigger(name = "input") input: ActivityInput, 
        context: ExecutionContext 
    ):ActivityOutput{
        val blobConnectionString = EnvironmentParam.INGEST_BLOB_CONNECTION.getSystemValue()
        val blobService = AzureBlobServiceImpl(blobConnectionString)

        val numThreads = EnvironmentParam.VALIDATION_NUMBER_ACTIVE_THREADS.getSystemValue().toInt()
        val blockQueueSize = EnvironmentParam.VALIDATION_NUMBER_PENDING_THREADS.getSystemValue().toInt()
        val batchSize = EnvironmentParam.VALIDATION_BATCH_SIZE.getSystemValue().toInt()
        return FnValidateSchema().process(input, context, blobService, numThreads, blockQueueSize, batchSize)
    }
}

class FnValidateSchema {

    fun process(input: ActivityInput, context: ExecutionContext, blobService:IBlobService, numThreads:Int, blockQueueSize:Int, batchSize:Int): ActivityOutput { 
        context.logger.log(Level.INFO,"Running CSV Schema Validator for input $input");

        
        val sourceUrl = input.common.params.originalFileUrl
        if(sourceUrl.isNullOrBlank()){
            return ActivityOutput(errorMessage = "No source URL provided!")
        }
        //check for file existence
        if(!blobService.exists(sourceUrl)){
            return ActivityOutput(errorMessage = "File missing in Azure! $sourceUrl")
        }

        val futures = mutableListOf<Future<SubOutput>>()
        val fileReader = openFileReader(input, blobService)
        if(fileReader == null){
            //TODO some kind of error message
            throw RuntimeException("FILE READER NULL")
        }

        val schemaReader = openSchemaReader(input, blobService)
        if(schemaReader == null){
            //TODO some kind of error message
            throw RuntimeException("SCHEMA READER NULL")
        }

        schemaReader.use{ sr -> fileReader.use{ fr ->
            val headerLine = fr.readLine()
            var currentInBatch = 0
            var batchNumber = 1
            var toSubmit = StringBuilder(headerLine)

            val blockingQueue = ArrayBlockingQueue<Runnable>(blockQueueSize);
            val executor = ThreadPoolExecutor(numThreads, numThreads, 0, TimeUnit.MILLISECONDS, blockingQueue, ThreadPoolExecutor.CallerRunsPolicy())
            
            var readLine: String
            while (fileReader.readLine().also { readLine = it } != null) {
                currentInBatch++
                toSubmit.append("\n").append(readLine)
                if(currentInBatch > batchSize){
                    val batchReader = StringReader(toSubmit.toString())
                    val future:Future<SubOutput> = executor.submit(Callable{subRunner(batchNumber, batchReader, sr)})
                    futures.add(future)

                    batchNumber++
                    currentInBatch = 0;
                    toSubmit = StringBuilder(headerLine)
                }
            }
            if(currentInBatch > 0){
                val batchReader = StringReader(toSubmit.toString())
                val future:Future<SubOutput> = executor.submit(Callable{subRunner(batchNumber, batchReader, sr)})
                futures.add(future)
            }
        }}

        do {
            val stillRunning = futures.filter{!it.isDone()}.count()
        } while(stillRunning > 0)

        //TODO compile the output and do something with it
        return ActivityOutput()
    }

    private fun subRunner(batchNumber:Int, batchReader:Reader, schemaReader:Reader):SubOutput{

        val subList = mutableListOf<Substitution>()
        val messages = CsvValidator.validate(batchReader, schemaReader, false, subList, false, false)
        return SubOutput(batchNumber, messages)
    }

    private fun openFileReader(input: ActivityInput, blobService:IBlobService):BufferedReader?{
        val inputStream = blobService.openDownloadStream(input.common.params.originalFileUrl!!)
        val pathInZip = input.common.params.pathInZip

        if(pathInZip.isNullOrBlank()){
            return BufferedReader(InputStreamReader(inputStream))
        }else{
            val zipInputStream = ZipInputStream(inputStream)
            var zipEntry = zipInputStream.nextEntry

            while (zipEntry != null) {
                if(pathInZip.equals(zipEntry.name)){
                    return BufferedReader(InputStreamReader(zipInputStream))
                }
                else{
                    zipEntry = zipInputStream.nextEntry
                }
            }
            return null
        }
    }

    private fun openSchemaReader(input: ActivityInput, blobService:IBlobService):BufferedReader?{
        //TODO actually figure this out
        return null
    }
}

data class SubOutput (
    val batchNumber : Int,
    var messages    : List<FailMessage>
)