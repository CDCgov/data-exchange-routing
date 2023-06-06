import com.google.gson.annotations.SerializedName
import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*
import com.google.gson.*
import java.util.*

// HTTP Post Request Imports
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

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
        // Log the queue message
        context.getLogger().info(message);

       // read the json format message
       val busMessage : ServicBusMessage = getQueueMessageObject(message)
       context.logger.info(busMessage.topic)

       // add claimCheckToken to the json message
       busMessage.claimCheckToken = getClaimCheckToken()
       val jsonString = Gson().toJson(busMessage)
       context.logger.warning("added claimCheckToken")
       context.logger.info(jsonString)

       // send the message with the token to another httpRequest
        callAnotherRequest("https://postman-echo.com/post", "["+jsonString+"]")
   }

   // calls the required request according to the req. url and data after checking the claimCheckToken
   fun callAnotherRequest(requestUrl:String ,data:String){
    val url = URL(requestUrl)
    val postData = data
 
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "POST"
    conn.doOutput = true
    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
    conn.setRequestProperty("Content-Length", postData.length.toString())
    conn.useCaches = false
 
    DataOutputStream(conn.outputStream).use { it.writeBytes(postData) }
    BufferedReader(InputStreamReader(conn.inputStream)).use { br ->
        var line: String?
        while (br.readLine().also { line = it } != null) {
            println(line)
        }
    }
   }

   // converts the given QueueMessage String into the ServiceBusMessage data class
   fun getQueueMessageObject(message: String): ServicBusMessage{
        val fristDrop = message.drop(1)
        val lastDrop = fristDrop.dropLast(1)
        return Gson().fromJson(lastDrop, ServicBusMessage::class.java)
   }

   // Method to get the claimCheckToken
   fun getClaimCheckToken(): String{
    // other logic if needed
    return "Added claimCheckToken"
   }
}