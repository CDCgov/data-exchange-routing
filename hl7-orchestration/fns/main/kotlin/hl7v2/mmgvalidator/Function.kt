package hl7v2.mmgvalidator

import com.google.gson.annotations.SerializedName

import java.util.*

import com.azure.identity.*
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.*

import com.azure.storage.blob.*
import com.azure.storage.blob.models.*
import com.azure.storage.blob.specialized.*
import com.azure.storage.blob.BlobClient
import com.azure.storage.blob.BlobServiceClient
import com.azure.storage.blob.BlobServiceClientBuilder
import com.azure.storage.blob.BlobContainerClient
import com.azure.storage.blob.BlobContainerClientBuilder
import com.azure.storage.blob.models.BlobProperties
import com.azure.storage.blob.models.ListBlobsOptions

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonElement

class Function {
    companion object {
        val gson: Gson = GsonBuilder().serializeNulls().create()
    }
    @FunctionName("mmgvalidator")
    fun run(
            @HttpTrigger(
                    name = "req",
                    methods = [HttpMethod.POST],
                    authLevel = AuthorizationLevel.ANONYMOUS) request: HttpRequestMessage<Optional<String>>,
            context: ExecutionContext): HttpResponseMessage {

        context.logger.info("HTTP trigger processed a ${request.httpMethod.name} request.")

        val query = request.queryParameters["token"]
        val body: String = request.body.orElse(query)

        if(body == null){
			return request
				.createResponseBuilder(HttpStatus.BAD_REQUEST)
				.body("Orchestration token is required the request body")
				.build()
        }
        var hl7Token : String
        hl7Token = request.body?.get().toString()
        // Retrieve Token
        var messageInfo = gson.fromJson(
            hl7Token, HL7Token::class.java
        )
        // get file name to read from container
        val jelement: JsonElement = JsonParser.parseString(body)
        val jobject: JsonObject = jelement.getAsJsonObject()
        val fileName = messageInfo.fileName // jobject.get("FileName").getAsString()
        val fileType = messageInfo.fileType // jobject.get("FileType").getAsString()

        context.logger.info("HTTP fileName ${fileName}.")
        context.logger.info("HTTP fileType ${fileType}.")

        // Get container connection string
        val cnet:String = System.getenv("blobCNet") ?: "default_value"

        // Create a BlobServiceClient object using the connection string
        val blobServiceClient: BlobServiceClient = BlobServiceClientBuilder().connectionString(cnet).buildClient()

        // Create a BlobContainerClient using the blobServiceClient.
        val sourceBlobContainerClient: BlobContainerClient = blobServiceClient.getBlobContainerClient("demo-file-source")
		val sourceBlobClient: BlobClient = sourceBlobContainerClient.getBlobClient(fileName)
		val sourceBlobClientUrl = sourceBlobClient.getBlobUrl()
		
        // does the file exist in the source container?
		// refactor -> use blob get properties to validate existance of blob instead of a loop
        var fileExist: Boolean = false
		val listBlobs =  sourceBlobContainerClient.listBlobs()     
		println("listBlobs type: " + listBlobs::class.java.typeName)
		
        for( item in listBlobs){
			var name = item.getName()
			if(fileName == name){
				fileExist = true
				break
			}
        }

		// file not found in container  
        if(!fileExist){    
			return request
			.createResponseBuilder(HttpStatus.NO_CONTENT)
            .body("file copied to target")
			.build()
        } 
        
		// if file exists,  execute the required validations

		// for POC we just copy file from source to target
        val targetBlobContainerClient: BlobContainerClient = blobServiceClient.getBlobContainerClient("demo-file-target")
		val targetBlobClient: BlobClient = targetBlobContainerClient.getBlobClient(fileName)
		val targetBlobClientUrl = targetBlobClient.getBlobUrl()
		// println("******** begin copy file ********")
		targetBlobClient.beginCopy(sourceBlobClientUrl, null)
		// println("******** end copy file ********")

        return request
			.createResponseBuilder(HttpStatus.OK)
			.body("file copied to target")
			.build()
    }
}

data class HL7Token(
        @SerializedName("fileType") val fileType: String?,
        @SerializedName("fileName") val fileName: String?,
        @SerializedName("pipelineProcess") val pipelineProcess: List<FunctionStep>     
)

data class FunctionStep(
        var functionName: String?,
        var referenceStorage: String,
        var functionURI: String,
        var result: String
)

data class StepHL7Properties(
        var field: String,
        var value: String
)

data class StepFnProperties(
        var field: String,
        var value: String
)
