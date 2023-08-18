package gov.cdc.dex.csv.functions.activity

import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger;

import gov.cdc.dex.csv.dtos.ActivityInput
import gov.cdc.dex.csv.dtos.ActivityOutput
import gov.cdc.dex.csv.dtos.ActivityParams
import gov.cdc.dex.csv.services.IBlobService
import gov.cdc.dex.csv.services.AzureBlobServiceImpl
import gov.cdc.dex.csv.constants.EnvironmentParam

import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.IOException
import java.io.OutputStream
import java.util.zip.ZipInputStream
import java.util.logging.Level

class FnDecompressorEntry {
    @FunctionName("DexCsvDecompressor")
    fun process(
        @DurableActivityTrigger(name = "input") input: ActivityInput, 
        context: ExecutionContext 
    ):ActivityOutput{
        val blobConnectionString = EnvironmentParam.INGEST_BLOB_CONNECTION.getSystemValue()
        val blobService = AzureBlobServiceImpl(blobConnectionString)

        return FnDecompressor().process(input, context, blobService)
    }
}

class FnDecompressor {
    private val ZIP_TYPES = listOf("application/zip","application/x-zip-compressed")
    private val BUFFER_SIZE = 4096

    fun process(input: ActivityInput, context: ExecutionContext, blobService:IBlobService):ActivityOutput{
        context.logger.log(Level.INFO,"Running decompressor for input $input");
        val sourceUrl = input.common.params.originalFileUrl
        if(sourceUrl.isNullOrBlank()){
            return ActivityOutput(errorMessage = "No source URL provided!")
        }
        //check for file existence
        if(!blobService.exists(sourceUrl)){
            return ActivityOutput(errorMessage = "File missing in Azure! $sourceUrl")
        }

        val contentType = blobService.getProperties(sourceUrl).contentType

        //IF blocks are expressions, and variables can be returned out of them
        var peekedPaths:List<String?> = if(ZIP_TYPES.contains(contentType)){
            //if ZIP, then unzip and capture the files that were contained

            //stream from the existing zip
            var downloadStream = blobService.openDownloadStream(sourceUrl)

            //TRY blocks are expressions, and variables can be returned out of them
            var pathsInZip:List<String> = try{
                decompressFileStream(downloadStream)
            }catch(e:IOException){
                context.logger.log(Level.SEVERE, "Error peeking in zip: $sourceUrl", e)
                return ActivityOutput(errorMessage = "Error peeking in zip: $sourceUrl : ${e.localizedMessage}")
            }

            if(pathsInZip.isEmpty()){
                //fail if zip file was empty
                return ActivityOutput(errorMessage = "Zipped file is empty: $sourceUrl")
            }

            //return the written paths to the IF statement, to be assigned to variable there
            pathsInZip
        } else {
            //if not a zip, pass along the pipeline
            //return NULL as there are no paths in the zip
            listOf(null)
        }

        val fanOutParams = mutableListOf<ActivityParams>()
        for(path in peekedPaths){
            val fanParam = input.common.params.copy()
            fanParam.pathInZip = path
            fanOutParams.add(fanParam)
        }
        return ActivityOutput(fanOutParams = fanOutParams)
    }
    
    private fun decompressFileStream(inputStream:InputStream):List<String>{
        var pathsInZip : MutableList<String> = mutableListOf();

        //use is equivalent of try-with-resources, will close the input stream at the end
        inputStream.use{ decompressFileStreamRecursive(it, "", pathsInZip) }
        return pathsInZip
    }
    

    private fun decompressFileStreamRecursive(inputStream:InputStream, pathPrefix:String, pathsInZip : MutableList<String>){
        //don't close input stream here because of recursion
        var zipInputStream = ZipInputStream(inputStream)
        var zipEntry = zipInputStream.nextEntry;
        
        while (zipEntry != null) {
            //ignore any entries that are a directory. The directory path will be part of the zipEntry.name
            if (!zipEntry.isDirectory()) {
                if(zipEntry.name.endsWith(".zip")){
                    //if a nested zip, recurse
                    var localPath = zipEntry.name+"|~|"
                    decompressFileStreamRecursive(zipInputStream, localPath, pathsInZip)
                }else{
                    // include prefix of nested zips in path
                    var pathToWrite = pathPrefix + zipEntry.name
        
                    pathsInZip.add(pathToWrite)
                }
            }
            zipEntry = zipInputStream.getNextEntry();
        }
        //don't close input stream here because of recursion
    }
}