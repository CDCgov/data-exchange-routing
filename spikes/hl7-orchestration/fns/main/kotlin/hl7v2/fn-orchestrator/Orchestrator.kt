import com.google.gson.*
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.*
import com.microsoft.azure.functions.ExecutionContext

import com.microsoft.durabletask.Task
import com.microsoft.durabletask.DurableTaskClient
import com.microsoft.durabletask.TaskOrchestrationContext

import com.microsoft.durabletask.azurefunctions.DurableClientInput
import com.microsoft.durabletask.azurefunctions.DurableClientContext
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger
import com.microsoft.durabletask.azurefunctions.DurableOrchestrationTrigger

import hl7v2.utils.*
import com.google.gson.annotations.SerializedName

data class TokenStep(
        @SerializedName("hl7Token") var hl7Token: String = "",
        @SerializedName("step") var step: FunctionStep
)

class DurableFunctions {
  companion object {
        val gson: Gson = GsonBuilder().serializeNulls().create()
    }
    @FunctionName("durableorchestrator")
    fun durableOrchestration(
        @HttpTrigger(
            name = "req", methods = [HttpMethod.POST], authLevel = AuthorizationLevel.ANONYMOUS
        ) request: HttpRequestMessage<String>, 
        @DurableClientInput(name = "durableContext") durableContext:DurableClientContext,
            context:ExecutionContext
    ): HttpResponseMessage {
        // Start the durable orchestration
        var hl7Token : String = request.body
        val orchestrationClient = durableContext.getClient();
        val instanceId = orchestrationClient.scheduleNewOrchestrationInstance("orchestratorhandler", hl7Token)
        System.out.printf("Instance Id: %s%n ", instanceId)
        return request.createResponseBuilder(
            HttpStatus.OK).body("Durable orchestration started. Instance ID: $instanceId").build()   
    }

    @FunctionName("orchestratorhandler")
    fun durableOrchestrationHandler(
        @DurableOrchestrationTrigger(name = "input")
        orchestrationContext: TaskOrchestrationContext,
        functionContext: ExecutionContext
    ) : String {
        
        System.out.printf("OrchHandler")
        
        val input = orchestrationContext.getInput(String::class.java)
        System.out.printf("OrchHandler Input: %s%n", input)

        var messageInfo = CastGson().castToClass<HL7Token>(input)
        var processList = arrayListOf<String>()
        // Iterate through Step Functions, Pass Info
        for ( functionStep in messageInfo!!.pipelineProcess){ 
            var temp = TokenStep(
                input, 
                functionStep
            )          
            System.out.printf("Function Step: %s%n", gson.toJson(functionStep))
            // Do Stuff
            var activityResponse = orchestrationContext.callActivity( functionStep.functionName, messageInfo)// "StepFunctionHandler", temp).await()
            System.out.printf("Activity Response: %s%n", activityResponse)

        }

        var contentType = "application/json"
        // Send Message to Event Hub
        val evHubName = System.getenv("EventHubSendOkName")
        val evHubErrsName = System.getenv("EventHubSendErrsName")
        val evHubConnStr = System.getenv("EventHubConnectionString")
        val evHubSender = EventHubSender(evHubConnStr)
        
        prepareAndSend(messageInfo!!, evHubSender, evHubName)

        return "Done"
    }
    
     private fun prepareAndSend(messageContent: HL7Token, eventHubSender: EventHubSender, eventHubName: String) {
        eventHubSender.send(evHubTopicName=eventHubName, message=messageContent.toString())
    }
}
