package hl7v2.debatcher

import com.azure.identity.*
import com.google.gson.*
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.*
import hl7v2.utils.*
import java.net.http.*
import java.util.*

class Function {
    companion object {
        val gson: Gson = GsonBuilder().serializeNulls().create()
    }
    @FunctionName("debatcher")
    fun run(
            @HttpTrigger(
                    name = "req",
                    methods = [HttpMethod.POST],
                    authLevel = AuthorizationLevel.ANONYMOUS
            )
            request: HttpRequestMessage<Optional<String>>,
            context: ExecutionContext
    ): HttpResponseMessage {

        val requestUri = System.getenv("hostURL")
        context.logger.info("HTTP trigger processed a ${request.httpMethod.name} request.")

        val hl7Token = CastGson().castToClass<HL7Token>(request.body.orElse(""))

        if ((hl7Token?.fileName == null) || (hl7Token?.fileType == null)) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("Invalid Token")
                    .build()
        }

        val fileName = hl7Token?.fileName!!

        // Get container connection string
        val cnet: String = System.getenv("BlobStorageConnectionString") ?: "default_value"
        val sourceBlob = BlobProxy(cnet, "message-drop")
        val sourceBlobClientUrl = sourceBlob.getBlobUrl(fileName)
        val listBlobs = sourceBlob.listBlobs()
        var fileExist: Boolean = false

        for (item in listBlobs) {
            var name = item.getName()
            if (fileName == name) {
                fileExist = true
                break
            }
        }

        // file not found in container
        if (!fileExist) {
            return request.createResponseBuilder(HttpStatus.NO_CONTENT).build()
        }

        val targetBlobClient = sourceBlob.getBlobClient(fileName)
        
        var fileContent = targetBlobClient.downloadContent().toString();
        //System.out.printf("Blob contents: %s%n", fileContent);
        
        var hl7Files: Array<HL7Message> = CastGson().castToClassExplicit<Array<HL7Message>>(fileContent);
        //context.getLogger().info("hl7Files: ${hl7Files}");
        var file_count = 0
        val targetBlob = BlobProxy(cnet, "demo-file-source")
        
        for(file in hl7Files){
            var uploadBlobCLient = targetBlob.getBlobClient("fileName_${file_count}.txt")
            System.out.printf("Individual File: %s%n", gson.toJson(file))
            //val uuid = UUID.randomUUID()
            var newToken = targetBlob.uploadDataToBlob(uploadBlobCLient, gson.toJson(file), hl7Token, "fileName_${file_count}.txt")
            file_count++
            System.out.printf("Count:", file_count.toString())
            sendHttpMessage(gson.toJson(newToken) , requestUri, context)
        }
        //targetBlobClient.beginCopy(sourceBlobClientUrl, null)

        return request.createResponseBuilder(HttpStatus.OK)
                .body("file $fileName copied to message drop container")
                .build()
    }

    private fun sendHttpMessage(message: String, requestUri: String, context : ExecutionContext): String  {
        val handler = HTTPHandler("${requestUri}/api/durableorchestrator", message)
        var req = handler.createPostRequest()
        val response = handler.sendRequest(req)
        return response.body()
    }
}
