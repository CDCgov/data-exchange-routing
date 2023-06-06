import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.*
import java.io.*
import java.util.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Azure Functions with HTTP Trigger + EventHub Output
 */
class Orchestrator {
    companion object {
        val gson: Gson = GsonBuilder().serializeNulls().create()
    }
    @FunctionName("orchestrator-poc")
    fun invoke(
        @HttpTrigger(
            name = "req",
            methods = [HttpMethod.POST],
            authLevel = AuthorizationLevel.ANONYMOUS)
            request: HttpRequestMessage<Optional<String>>,
            context: ExecutionContext): HttpResponseMessage {
            /*
            Receive message and pass along to function
            *Durable Function*
            - Injestion: 
                Called directly from Router
                - Each Function saves in Function Table on DataLake
                Step Functions
            - Orchestrator Triggers Next Function
                - Note, it does not wait for each function to run/respond
                - It just sends token to next function         
             */
        // Event Hub Output Setup
        val evHubName = System.getenv("EventHubSendOkName")
        context.logger.info("evhub: ${evHubName}")

        val evHubErrsName = System.getenv("EventHubSendErrsName")
        val evHubConnStr = System.getenv("EventHubConnectionString")
        val evHubSender = EventHubSender(evHubConnStr)

        // Convert Message Object into HL7 Token
        // Read Message
        context.logger.info("1")

        var hl7Token : String
        hl7Token = request.body?.get().toString()
        // Retrieve Token
        var messageInfo = getMessageInfo( hl7Token )
        var processList = arrayListOf<String>()
        context.logger.info("2")
        // Determine Step Functions to Pass
        for ( functionStep in messageInfo.pipelineProcess){            
            var stepResult = runFunctionStep(hl7Token, functionStep, context)
            processList.add(stepResult)
        }
        var contentType = "application/json"
        context.logger.info("Step Result: " + processList)

        // Send Message to Event Hub
        prepareAndSend(messageInfo, evHubSender, evHubName, context)

        // Response Function
        return try {

            return request.createResponseBuilder(HttpStatus.OK)
                .header("Content-Type", contentType)
                .body(processList)
                .build()

        } catch (e: Exception) {
            contentType = "text/plain"
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                .header("Content-Type", contentType)
                .body(processList)
                .build()
        }
         
    } // .eventHubProcess

    private fun getMessageInfo( message : String): HL7Token {
        val structuredMessage = gson.fromJson(
            message, HL7Token::class.java
        )
        // Space for Logic - If needed.
        return structuredMessage
    }

    private fun runFunctionStep(token: String, msg: FunctionStep, context : ExecutionContext): String  {
        // Receive Function Step, Trigger Azure Fn, Await Response
        // Response should be an object and should be stored
        val client = HttpClient.newBuilder().build()

        context.logger.info("Trigger HTTP Fn -->" + msg.functionURI)

        val request = HttpRequest.newBuilder()
            .uri(URI.create(msg.functionURI))
            .POST(HttpRequest.BodyPublishers.ofString(token))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        
        return response.body()
    }

    private fun prepareAndSend(messageContent: HL7Token, eventHubSender: EventHubSender, eventHubName: String, context: ExecutionContext) {
        // val contentBase64 = Base64.getEncoder().encodeToString(messageContent.joinToString("\n").toByteArray())
        context.logger.info("Sending new Event to event hub Message: --> ${messageContent.toString()}")
        eventHubSender.send(evHubTopicName=eventHubName, message=messageContent.toString())
        context.logger.info("full message: $messageContent.toString()")
    }

} // .class  Function

