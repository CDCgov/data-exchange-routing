package hl7v2.mmgvalidator

import hl7v2.utils.*
import com.microsoft.azure.functions.annotation.*
import com.microsoft.azure.functions.*
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger
import com.microsoft.azure.functions.ExecutionContext

import java.util.*

class Function {
		
	@FunctionName("mmg-validator")
	fun run(
		@DurableActivityTrigger(name = "input") 
		temp: HL7Token,
        context : ExecutionContext) : String {
			context.logger.info("Durable Trigger - mmg-validator")

			val hl7Token = temp 
			val fileName = hl7Token?.fileName!!

			// Get container connection string
			val cnet:String = System.getenv("BlobStorageConnectionString") ?: "default_value"
			val sourceBlob = BlobProxy(cnet, "demo-file-source")
			val sourceBlobClientUrl = sourceBlob.getBlobUrl(fileName)
			val listBlobs = sourceBlob.listBlobs() 
			var fileExist: Boolean = false
			// TODO - Don't Iterate. Find Blob else Exception
			for( item in listBlobs){
				var name = item.getName()
				if(fileName == name){
					fileExist = true
					break
				}
			}
				
			val targetBlob = BlobProxy(cnet, "demo-mmg-validator")
			val targetBlobClient = targetBlob.getBlobClient(fileName)
			targetBlobClient.beginCopy(sourceBlobClientUrl, null)

			return "Completed"															
		}	
}