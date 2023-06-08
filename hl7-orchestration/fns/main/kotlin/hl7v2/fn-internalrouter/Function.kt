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

//*Internal Router Function- Consuming Azure Service Bus, adding claim token, sending updated message to the function */
class Router {
     companion object {
        val gson: Gson = GsonBuilder().serializeNulls().create()
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

        // Log the queue message
        context.getLogger().info(message);

       // read the json format message
       val busMessage : HL7Token = getQueueMessageObject(message)
       context.logger.info(busMessage.fileName)

       // add claimCheckToken to the json message
       // busMessage.claimCheckToken = getClaimCheckToken()
       val jsonString = Gson().toJson(getClaimCheckToken(busMessage.fileName))
       context.logger.warning("added claimCheckToken")
       context.logger.info(jsonString)

       runFunctionStep(jsonString, requestUri, context)
       
   }
 

   // converts the given QueueMessage String into the ServiceBusMessage data class
   private fun getQueueMessageObject(message: String): HL7Token{
        //val fristDrop = message.drop(1)
        //val lastDrop = fristDrop.dropLast(1)
        return Gson().fromJson(message, HL7Token::class.java)
   }

   private fun runFunctionStep(message: String, requestUri: String, context : ExecutionContext): String  {
    // Receive Function Step, Trigger Azure Fn, Await Response
    // Response should be an object and should be stored
    val client = HttpClient.newBuilder().build()

    context.logger.info("Trigger HTTP Fn -->" + requestUri)

    val request = HttpRequest.newBuilder()
        .uri(URI.create(requestUri))
        .POST(HttpRequest.BodyPublishers.ofString(message))
        .build()

    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
    
    return response.body()
}

   // Method to get the claimCheckToken
   private fun getClaimCheckToken(FILENAME: String): String{
    // other logic if needed
    // Get container connection string
    val cnet:String = System.getenv("BlobStorageConnectionString")//
    val path : String
    // Create a BlobServiceClient object using the connection string
    val blobServiceClient: BlobServiceClient = BlobServiceClientBuilder().connectionString(cnet).buildClient()

    // Create a BlobContainerClient using the blobServiceClient.
    try{
      val sourceBlobContainerClient: BlobContainerClient = blobServiceClient.getBlobContainerClient("internalrouter-configuration")
      val sourceBlobClient: BlobClient = sourceBlobContainerClient.getBlobClient(FILENAME)
      val sourceBlobClientUrl = sourceBlobClient.getBlobUrl()
      print(sourceBlobClientUrl)
      // Download the blob to local storage
      path = Paths.get("").toAbsolutePath().toString()+"/"+FILENAME;
      sourceBlobClient.downloadToFile(path)
    }catch(e: Exception){
      return "{\"message\":\"invalid configuration file name"+FILENAME+".\"}"
    }
    

    // Read the file from local storage
    var gson = Gson()
    val bufferedReader: BufferedReader = File(path).bufferedReader()
    val inputString = bufferedReader.use { it.readText() }
    //print("inputString --->" + inputString)
    
    try{
         //Convert the Json File to Gson Object
       var settings = gson.fromJson(inputString, HL7Token::class.java)
       File(path).delete()
       return inputString
    }catch (e: Exception){
      File(path).delete()
      return "{\"message\":\"invalid configuration.\"}"
    }
   }
}