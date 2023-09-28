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
        var writtenUrls:List<String> = if(ZIP_TYPES.contains(contentType)){
            //if ZIP, then unzip and capture the files that were contained

            //stream from the existing zip, and immediately stream the unzipped data back to a new file in processed
            var downloadStream = blobService.openDownloadStream(sourceUrl)
            var uploadStreamSupplier = {destinationUrl:String -> blobService.openUploadStream(destinationUrl)}

            //TRY blocks are expressions, and variables can be returned out of them
            var writtenPathsZip:List<String> = try{
                val destinationUrl = buildOutputUrl(sourceUrl, input.common.params.executionId)
                decompressFileStream(downloadStream, uploadStreamSupplier, destinationUrl)
            }catch(e:IOException){
                context.logger.log(Level.SEVERE, "Error unzipping: $sourceUrl", e)
                return ActivityOutput(errorMessage = "Error unzipping: $sourceUrl : ${e.localizedMessage}")
            }

            if(writtenPathsZip.isEmpty()){
                //fail if zip file was empty
                return ActivityOutput(errorMessage = "Zipped file was empty: $sourceUrl")
            }

            //return the written paths to the IF statement, to be assigned to variable there
            writtenPathsZip
        } else {
            //if not a zip, pass along the pipeline
            //wrap the path in a LIST and return to the IF statement
            listOf(sourceUrl)
        }

        val fanOutParams = mutableListOf<ActivityParams>()
        for(url in writtenUrls){
            val fanParam = input.common.params.copy()
            fanParam.currentFileUrl = url
            fanOutParams.add(fanParam)
        }
        return ActivityOutput(fanOutParams = fanOutParams)
    }
    
    private fun buildOutputUrl(sourceUrl:String, executionId:String?):String{
        return sourceUrl.replaceFirst("ingest/", "processed/$executionId/").replace(".zip","-decompressed/")
    }

    private fun decompressFileStream(inputStream:InputStream, outputStreamSupplier: (String) -> OutputStream, outputUrl:String):List<String>{
        var writtenPaths : MutableList<String> = mutableListOf();

        //use is equivalent of try-with-resources, will close the input stream at the end
        inputStream.use{ str ->
            decompressFileStreamRecursive(str, outputStreamSupplier, outputUrl, writtenPaths)
        }
        return writtenPaths
    }
    

    private fun decompressFileStreamRecursive(inputStream:InputStream, outputStreamSupplier: (String) -> OutputStream, outputUrl:String, writtenPaths : MutableList<String>){
        //don't close input stream here because of recursion
        var zipInputStream = ZipInputStream(inputStream)
        var zipEntry = zipInputStream.nextEntry;
        
        while (zipEntry != null) {
            //ignore any entries that are a directory. The directory path will be part of the zipEntry.name, and Azure automatically creates the needed directories
            if (!zipEntry.isDirectory()) {
                if(zipEntry.name.endsWith(".zip")){
                    //if a nested zip, recurse
                    var localPath = zipEntry.name.replace(".zip","/")
                    decompressFileStreamRecursive(zipInputStream, outputStreamSupplier, outputUrl+localPath, writtenPaths)
                }else{
                    // write file content directly to a new blob
                    var urlToWrite = outputUrl+zipEntry.name

                    var outputStream = outputStreamSupplier.invoke(urlToWrite)

                    //use is equivalent of try-with-resources, will close the output stream when done
                    BufferedOutputStream(outputStream).use{bufferedOutputStream ->
                        val bytesIn = ByteArray(BUFFER_SIZE)
                        var read: Int
                        while (zipInputStream.read(bytesIn).also { read = it } != -1) {
                            bufferedOutputStream.write(bytesIn, 0, read)
                        }
                    }
                    //don't close input stream here because of recursion
        
                    writtenPaths.add(urlToWrite)
                }
            }
            zipEntry = zipInputStream.getNextEntry();
        }
        //don't close input stream here because of recursion
    }
}