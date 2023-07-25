/*import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger;
import com.azure.storage.queue.QueueClientBuilder
import com.azure.storage.queue.QueueServiceClient
import com.azure.storage.queue.QueueServiceClientBuilder

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

import gov.cdc.dex.csv.constants.EnvironmentParam
import gov.cdc.dex.csv.constants.ReportingEvent

import java.util.logging.Level

class FnEnqueueReportDataEntry {
    @FunctionName("EnqueueReportData")
    fun  process(
        @DurableActivityTrigger(name = "input") input: ActivityInput, 
        context: ExecutionContext
    );ActivityOutput{
        return FnEnqueueReportData.process(input, context)
    }

}

class FnEnqueueReportData {
    val cosmosDBConnectionString = EnvironmentParam.COSMOSDB_CONNECTION.getSystemValue()
    val cosmosDBKey = EnvironmentParam.COSMOSDB_KEY.getSystemValue()

    fun process(input: ActivityInput, context: ExecutionContext):ActivityOutput{
        val queueClient:queueClient = new QueueClientBuilder()
            .connectionString(cosmosDBConnectionString)
            .queueName("nonhl7_reporting")
            .buildClient();

        val reportEvent = ReportingEvent(input.common.params.currentFileUrl,
                                            input.common.stepNumber,
                                            null,
                                            input.common.params.errorMessage)

        val objectMapper: ObjectMapper = jacksonObjectMapper()

        queueClient.sendMessage(objectMapper.writeValueAsString(reportEvent))
    }
}*/