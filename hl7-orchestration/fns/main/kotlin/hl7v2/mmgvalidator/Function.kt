package hl7v2.mmgvalidator

//import com.google.gson.annotations.SerializedName
import com.microsoft.azure.functions.annotation.*
import com.microsoft.azure.functions.*
import com.azure.identity.*
import com.azure.storage.blob.*
import com.google.gson.*
import java.util.*

// import com.azure.storage.blob.models.*
// import com.azure.storage.blob.specialized.*
// import com.azure.storage.blob.BlobClient
// import com.azure.storage.blob.BlobServiceClient
// import com.azure.storage.blob.BlobServiceClientBuilder
// import com.azure.storage.blob.BlobContainerClient
// import com.azure.storage.blob.BlobContainerClientBuilder
// import com.azure.storage.blob.models.BlobProperties
// import com.azure.storage.blob.models.ListBlobsOptions

// import com.google.gson.Gson
// import com.google.gson.GsonBuilder
// import com.google.gson.JsonObject
// import com.google.gson.JsonParser
// import com.google.gson.JsonElement

class Function {
    // companion object {
    //     val gson: Gson = GsonBuilder().serializeNulls().create()
    // }
    @FunctionName("mmgvalidator")
    fun run(
            @HttpTrigger(
                    name = "req",
                    methods = [HttpMethod.POST],
                    authLevel = AuthorizationLevel.ANONYMOUS) request: HttpRequestMessage<Optional<String>>,
            context: ExecutionContext): HttpResponseMessage {

        // context.logger.info("HTTP trigger processed a ${request.httpMethod.name} request.")

        val query = """ {
            'fileType':'CASE',
            'fileName":"No_File.txt'
        }"""

        val body: String = request.body.orElse(query)
        // println("Body: $body")
        // println("request body: " + request.body.orElse(query))

        if(body == null){
			return request
				.createResponseBuilder(HttpStatus.BAD_REQUEST)
				.body("Orchestration token is required the request body")
				.build()
        }

        // var hl7Token : String
        // hl7Token = request.body?.get().toString()

        // Retrieve Token
        // this is not working,  casting is returning null values
        // var messageInfo = gson.fromJson(
        //     hl7Token, HL7Token::class.java
        // )
        // val fileName = messageInfo.fileName 
        // val fileType = messageInfo.fileType 


        // get file name to read from container
        // println("parse body to json element")
        val jelement: JsonElement = JsonParser.parseString(body)

        // println("parse json element to json object")
        val jobject: JsonObject = jelement.getAsJsonObject()

        // println("json object: $jobject")

        val fileName =  jobject.get("fileName").getAsString()
        // println("File Name: $fileName")
        val fileType =  jobject.get("fileType").getAsString()
        // println("File Type: $fileType")

        context.logger.info("HTTP fileName ${fileName}.")
        context.logger.info("HTTP fileType ${fileType}.")


        // Get container connection string
        val cnet:String = System.getenv("blobCNet") ?: "default_value"
        // println("Connection String: $cnet")

        // Create a BlobServiceClient object using the connection string
        // println("create blobServiceClient")
        val blobServiceClient: BlobServiceClient = BlobServiceClientBuilder().connectionString(cnet).buildClient()

        // Create a BlobContainerClient using the blobServiceClient.
        // println("create sourceBlobContainerClient")
        val sourceBlobContainerClient: BlobContainerClient = blobServiceClient.getBlobContainerClient("demo-file-source")

        // println("create sourceBlobClient")
		val sourceBlobClient: BlobClient = sourceBlobContainerClient.getBlobClient(fileName)

        // println("create sourceBlobClientUrl")
		val sourceBlobClientUrl = sourceBlobClient.getBlobUrl()
		
        // does the file exist in the source container?
		// refactor -> use blob get properties to validate existance of blob instead of a loop
        var fileExist: Boolean = false
        // println("get list of files in source container")
		val listBlobs =  sourceBlobContainerClient.listBlobs()     
        // println("listBlobs: $listBlobs")
		
        for( item in listBlobs){
			var name = item.getName()
            // println("file name in list: $name")
			if(fileName == name){
				fileExist = true
				break
			}
        }

		// file not found in container  
        if(!fileExist){    
			return request
			.createResponseBuilder(HttpStatus.NO_CONTENT)
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

// data class HL7Token(
//         @SerializedName("fileType") val fileType: String?,
//         @SerializedName("fileName") val fileName: String?,
//         @SerializedName("pipelineProcess") val pipelineProcess: List<FunctionStep>     
// )

// data class FunctionStep(
//         var functionName: String?,
//         var referenceStorage: String,
//         var functionURI: String,
//         var result: String
// )

// data class StepHL7Properties(
//         var field: String,
//         var value: String
// )

// data class StepFnProperties(
//         var field: String,
//         var value: String
// )
