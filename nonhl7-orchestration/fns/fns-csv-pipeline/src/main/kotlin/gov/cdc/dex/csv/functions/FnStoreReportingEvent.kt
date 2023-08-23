package gov.cdc.dex.csv.functions

import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.EventHubTrigger
import com.microsoft.azure.functions.annotation.Cardinality
import com.microsoft.azure.functions.ExecutionContext
import gov.cdc.dex.csv.constants.EnvironmentParam
import gov.cdc.dex.csv.dtos.ReportingEventMessage
import gov.cdc.dex.csv.dtos.ReportingEvent

import com.azure.cosmos.CosmosClientBuilder
import com.azure.cosmos.CosmosClient
import com.azure.cosmos.CosmosContainer
import com.azure.cosmos.CosmosDatabase
import com.azure.cosmos.CosmosException
import com.azure.cosmos.models.CosmosItemResponse

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

import org.owasp.encoder.Encode
import org.apache.qpid.proton.codec.Encoder

import java.util.logging.Level

class FnStoreReportingEventEntry{

    @FunctionName("FnStoreReportingEvent")
    fun eventProcessor(
        @EventHubTrigger(
            cardinality = Cardinality.ONE,
            name = "msg",
            eventHubName = "%EventHubName_ReportingEvent%",
            connection = "EventHubConnection")
        message: String,
        context: ExecutionContext
    ){
        FnStoreReportingEvent().processEvent(message, context)
    }
}

class FnStoreReportingEvent {
    val cosmosDBConnectionString = EnvironmentParam.COSMOSDB_CONNECTION.getSystemValue()
    val cosmosDBKey = EnvironmentParam.COSMOSDB_KEY.getSystemValue()

    private val jsonParser = jacksonObjectMapper()

    fun processEvent(message:String, context:ExecutionContext){
        context.logger.info("Decompressor function triggered with message $message")
        
        if(message.isEmpty()){
            context.logger.log(Level.SEVERE, "Empty Azure message")
            return
        }

        // parse the message usable objects
        val event:ReportingEvent = try{
            jsonParser.readValue(message)
        }catch(e:Error){
            context.logger.log(Level.SEVERE, "Error parsing Azure message" + e)
            return
        }

        //log event
        context.logger.log(Level.INFO, "Recieved reporting event")
        context.logger.log(Level.INFO, event.toString())

        //Encode strings
        event.currentFile = Encode.forHtml(event.currentFile)
        event.stepNumber = Encode.forHtml(event.stepNumber)
        event.errorMessage = Encode.forHtml(event.errorMessage)
        event.id = event.currentFile

        //Get client
        var client:CosmosClient = CosmosClientBuilder()
                        .endpoint("**REDACTED**")
                        .key("**REDACTED**")
                        .buildClient()

        //Connect to DB -> Container -> Create Item
        var database:CosmosDatabase = client.getDatabase("nonhl7_reporting")
        var container:CosmosContainer = database.getContainer("report_events")
        var itemResponse:CosmosItemResponse<ReportingEvent> = container.createItem(event)

        context.logger.log(Level.INFO, "Item created: " + itemResponse.toString())
    }
}