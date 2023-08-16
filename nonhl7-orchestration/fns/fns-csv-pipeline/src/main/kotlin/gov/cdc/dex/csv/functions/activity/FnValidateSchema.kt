package gov.cdc.dex.csv.functions.activity

import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.ExecutionContext
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger
import com.univocity.parsers.csv.CsvParser
import com.univocity.parsers.csv.CsvParserSettings

import gov.cdc.dex.csv.dtos.CommonInput
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
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

import uk.gov.nationalarchives.csv.validator.api.java.CsvValidator
import uk.gov.nationalarchives.csv.validator.api.java.FailMessage
import uk.gov.nationalarchives.csv.validator.api.java.Substitution

class FnValidateCsvStructureEntry{
    @FunctionName("FnValidateCsvStructure")
    fun process(
        @DurableActivityTrigger(name = "input") input: CustomActivityInput, 
        context: ExecutionContext 
    ):ActivityOutput{
        val blobConnectionString = EnvironmentParam.INGEST_BLOB_CONNECTION.getSystemValue()
        val blobService = AzureBlobServiceImpl(blobConnectionString)

        val numThreads = EnvironmentParam.VALIDATION_NUMBER_ACTIVE_THREADS.getSystemValue().toInt()
        val blockQueueSize = EnvironmentParam.VALIDATION_NUMBER_PENDING_THREADS.getSystemValue().toInt()
        val batchSize = EnvironmentParam.VALIDATION_BATCH_SIZE.getSystemValue().toInt()
        return FnValidateCsvStructure().process(input, context, blobService, numThreads, blockQueueSize, batchSize)
    }
}

class FnValidateCsvStructure {

    fun process(input: CustomActivityInput, context: ExecutionContext, blobService:IBlobService, numThreads:Int, blockQueueSize:Int, batchSize:Int): ActivityOutput { 
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

        fileReader.use{ fr ->
            val headerLine = fr.readLine()

            val schemaReader = openSchemaReader(input, blobService, headerLine)
            if(schemaReader == null){
                //TODO some kind of error message
                throw RuntimeException("SCHEMA READER NULL")
            }

            schemaReader.use{ sr -> 

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
            }
        }

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

    private fun openFileReader(input: CustomActivityInput, blobService:IBlobService):BufferedReader?{
        val inputStream = blobService.openDownloadStream(input.common.params.originalFileUrl!!)
        val pathInZip = input.common.params.pathInZip

        if(pathInZip.isNullOrBlank()){
            return BufferedReader(InputStreamReader(inputStream))
        }else{
            var zipInputStream = ZipInputStream(inputStream)
            
            val splitPath = pathInZip.split("|~|")
            for((index, pathToNestedZip) in splitPath.withIndex()){
                var zipEntry:ZipEntry? = null
                do{
                    zipEntry = zipInputStream.nextEntry
                }while(zipEntry!=null && !pathToNestedZip.equals(zipEntry.name))

                if(zipEntry==null){
                    return null
                }

                //if the last one, return, else continue
                if(index == pathInZip.count()-1){
                    return BufferedReader(InputStreamReader(zipInputStream))
                }else{
                    zipInputStream = ZipInputStream(zipInputStream)
                }
            }
            return null
        }
    }

    private fun openSchemaReader(input: CustomActivityInput, blobService:IBlobService, headerLine:String):Reader{
        val schemaUrl = input.config.schemaUrl
        val contents = if(schemaUrl==null){
            //Digital Preservation requires a schema to do the basic structure validation
            //If one is not provided by CDC program, but they still desire structure validation, we need to build a basic schema to enforce each row has the same number of records
            buildSchema(headerLine)
        }else{
            val rawSchema = blobService.getBlobContent(schemaUrl)
            if(input.config.relaxedHeader){
                massageSchema(rawSchema, headerLine)
            }else{
                rawSchema
            }
        }
        return StringReader(contents)
    }

    private fun buildSchema(headerLine:String):String{
        //for now, using Univocity to parse the header line. This is what is used by Digital Preservation
        //kinda thinking will want to switch to Jackson...
        val settings = CsvParserSettings()
        settings.format.setDelimiter(CSV_RFC1480_SEPARATOR)
        settings.format.setQuote(CSV_RFC1480_QUOTE_CHARACTER)
        settings.format.setQuoteEscape(CSV_RFC1480_QUOTE_ESCAPE_CHARACTER)
        settings.setIgnoreLeadingWhitespaces(false)
        settings.setIgnoreTrailingWhitespaces(false)
        settings.setLineSeparatorDetectionEnabled(true)

        val parser = CsvParser(settings)
        val parsedHeader = parser.parseLine(headerLine)//returns as String[]

        val numFields = parsedHeader.count()
        var schemaString = "version 1.2\n@noHeader@totalColumns $numFields"
        for(i in 1..numFields){
            schemaString = schemaString + "\n$i"
        }
        return schemaString
    }

    private fun massageSchema(rawSchema:String, headerLine:String):String{
        //TODO
        return rawSchema
    }
}

data class CustomActivityInput (
    val config  : CustomConfig,
    val common  : CommonInput
)

data class CustomConfig (
    val schemaUrl       : String? = null,
    val relaxedHeader   : Boolean = false
)

data class SubOutput (
    val batchNumber : Int,
    var messages    : List<FailMessage>
)