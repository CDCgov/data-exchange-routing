package hl7v2.structurevalidator

import hl7v2.utils.*
import com.microsoft.azure.functions.annotation.*
import com.microsoft.azure.functions.*
import java.util.*
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger
import com.microsoft.azure.functions.ExecutionContext

class Function {

    @FunctionName("structure-validator")
    fun run(
        @DurableActivityTrigger(name="input")
        input: HL7Token,
        context : ExecutionContext ) : String {
            context.logger.info("Structure Validator Activity Triggered")

            val hl7Token = input 
            val fileName = hl7Token?.fileName!!

            // Get container connection string
            val cnet:String = System.getenv("BlobStorageConnectionString") ?: "default_value"
            val sourceBlob = BlobProxy(cnet, "demo-file-source")
            val sourceBlobClientUrl = sourceBlob.getBlobUrl(fileName)
            val listBlobs =  sourceBlob.listBlobs()     
            var fileExist: Boolean = false

            // TODO - Don't Iterate. Find Blob else Exception
            for( item in listBlobs){
                var name = item.getName()
                if(fileName == name){
                    fileExist = true
                    break
                }
            }
            
            val targetBlob = BlobProxy(cnet, "demo-structure-validator")
            val targetBlobClient = targetBlob.getBlobClient(fileName)
            targetBlobClient.beginCopy(sourceBlobClientUrl, null)
            // TODO - Rsponsd with Token or Step Object
            return "Completed"
    }
}