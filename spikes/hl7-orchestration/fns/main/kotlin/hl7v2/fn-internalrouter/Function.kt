import com.google.gson.annotations.SerializedName
import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*
import com.google.gson.*
import java.util.*
import java.nio.file.Paths
import java.io.*

// HTTP Post Request Imports
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

// Blob Storage
import com.azure.storage.blob.*
import com.azure.storage.blob.specialized.*
import hl7v2.utils.*

//*Internal Router Function- Consuming Azure Service Bus, adding HLToken, sending updated message to the function */
class Router {
     companion object {
        val gson: Gson = GsonBuilder().serializeNulls().create()
        val storage_acct = "internalrouter-configuration"
    }
    @FunctionName("InternalRouter")
   fun  ServiceBusTopicTrigger(
       @ServiceBusQueueTrigger(
            name = "internalrouter",
            queueName = "incomingmessages",
            connection = "ServiceBusConnectionString")
            message: String,
       context: ExecutionContext )
   {
      
      val requestUri = System.getenv("HTTPUrl")
      // Log the queue message - Type. File
      context.getLogger().info("Message: ${message}")

      // Retrieve Config base on Case
      val azureProxy = BlobProxy( System.getenv("BlobStorageConnectionString"), storage_acct )
      // read the json format message
      val busMessage = CastGson().castToClass<HL7Token>(message)
      val configApp = getHL7Config( busMessage?.fileType, azureProxy, context )
      val functionSteps : List<FunctionStep> = CastGson().castToClassExplicit<List<FunctionStep>>(configApp)
      context.getLogger().info("fn steps: ${functionSteps}");

      val configAppObj = HL7Token(
         busMessage?.fileType,
         busMessage?.fileName,
         functionSteps
      )

      context.getLogger().info("Sending Message: ${configAppObj.toString()}");
      // Take and Send to Debatcher 
      sendHttpMessage(gson.toJson(configAppObj) , requestUri, context)
   }
 
   private fun sendHttpMessage(message: String, requestUri: String, context : ExecutionContext): String  {
    val handler = HTTPHandler(requestUri, message)
    var req = handler.createPostRequest()
    val response = handler.sendRequest(req)
    return response.body()
}

   // Method to get the claimCheckToken
   private fun getHL7Config(filetype: String?, azureProxy: BlobProxy, context : ExecutionContext): String{
    // Create a BlobServiceClient object using the connection string
    val blobClient: BlobClient = azureProxy.getBlobClient(filetype + ".json")
    // Create a BlobContainerClient using the blobServiceClient    
    var rawBlobString : String = blobClient.downloadContent().toString();
    context.logger.info("Blob info: ${rawBlobString}");
    return rawBlobString
   }
}