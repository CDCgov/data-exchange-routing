package gov.cdc.dex.csv.functions

import com.microsoft.azure.functions.ExecutionContext
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.EventHubTrigger
import com.microsoft.azure.functions.annotation.Cardinality
import com.microsoft.durabletask.Task
import com.microsoft.durabletask.azurefunctions.DurableClientInput
import com.microsoft.durabletask.azurefunctions.DurableClientContext
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

import gov.cdc.dex.csv.dtos.ActivityParams
import gov.cdc.dex.csv.dtos.AzureBlobCreateEventMessage
import gov.cdc.dex.csv.dtos.OrchestratorInput
import gov.cdc.dex.csv.dtos.OrchestratorConfiguration
import gov.cdc.dex.csv.dtos.RouterConfiguration
import gov.cdc.dex.csv.constants.EnvironmentParam
import gov.cdc.dex.csv.services.AzureBlobServiceImpl
import gov.cdc.dex.csv.services.IBlobService

import java.io.InputStream
import java.io.BufferedReader
import java.util.logging.Level

class FnRouterEntry {

    // TODO: This Trigger needs to be replaced with whatever the ingestion router will use to send us the information
    @FunctionName("DexCsvRouter")
    fun pipelineEntry(
        @EventHubTrigger(
            cardinality = Cardinality.ONE,
            name = "msg", 
            eventHubName = "%EventHubName_Ingest%",
            connection = "EventHubConnection")
            events: List<AzureBlobCreateEventMessage>,
        @DurableClientInput(name = "durableContext") durableContext:DurableClientContext,
        context: ExecutionContext
    ) {
        val ingestBlobConnectionString = EnvironmentParam.INGEST_BLOB_CONNECTION.getSystemValue()
        val ingestBlobService = AzureBlobServiceImpl(ingestBlobConnectionString)
        
        val configBlobConnectionString = EnvironmentParam.CONFIG_BLOB_CONNECTION.getSystemValue()
        val configBlobService = AzureBlobServiceImpl(configBlobConnectionString)
        
        val baseConfigUrl = EnvironmentParam.BASE_CONFIG_URL.getSystemValue() 
        
        //TODO do we need to handle multiple in same trigger? Is that even a thing?
        val event:AzureBlobCreateEventMessage = events.get(0)
        val input = RouterInput(event, durableContext, context, ingestBlobService, configBlobService, baseConfigUrl)
        val output = FnRouter().pipelineEntry(input)
        if(output.errorMessage!=null){
            context.logger.log(Level.SEVERE,output.errorMessage)
            TODO("handle the error")
        } else if(output.orchestratorId!=null){
            context.logger.log(Level.INFO, "Orchestrator kicked off with ID ${output.orchestratorId}")
            //TODO handle successful routing
        } else {
            context.logger.log(Level.SEVERE,"Not sure what happened, both error and orchestrator ID were null... shouldn't be possible....")
            TODO("handle the error")
        }
    }
}



class FnRouter {
    private val BLOB_CREATED_EVENT_TYPE = "Microsoft.Storage.BlobCreated"
    private val jsonParser = jacksonObjectMapper()

    fun pipelineEntry(input:RouterInput):RouterOutput {
        // ensure message received is blob creation
        if ( input.event.eventType != BLOB_CREATED_EVENT_TYPE) {
            return RouterOutput(errorMessage="Recieved non-created message type $input.event.eventType")
        }

        input.context.logger.log(Level.INFO,"Received BLOB_CREATED event: --> $input.event")

        //check required event parameters
        val id = input.event.id
        val ingestUrl = input.event.data?.url
        if(id==null || ingestUrl==null){
            return RouterOutput(errorMessage="Event missing required parameter(s)")
        }

        //check for ingest file existence
        if(!input.ingestBlobService.exists(ingestUrl)){
            return RouterOutput(errorMessage="Ingest file not found: $ingestUrl")
        }

        //check for router file existence
        val routerConfigUrl = input.baseConfigUrl+"routerConfig.json"
        if(!input.configBlobService.exists(routerConfigUrl)){
            return RouterOutput(errorMessage="Router config file not found: $routerConfigUrl")
        }
        
        //parse router file
        val configList = try{
            val content = input.configBlobService.openDownloadStream(routerConfigUrl).bufferedReader().use(BufferedReader::readText)
            jsonParser.readValue<List<RouterConfiguration>>(content) 
        }catch(e:Exception){
            input.context.logger.log(Level.SEVERE,"Could not parse router config", e)
            return RouterOutput(errorMessage="Error parsing router config file $routerConfigUrl : ${e.message}")
        }

        //pull router config associated with the ingest file
        val ingestMetadata = input.ingestBlobService.getProperties(ingestUrl).metadata
        val routerConfig = pullRouterConfig(ingestMetadata, configList)
        if(routerConfig==null){
            return RouterOutput(errorMessage="Router config match not found for file $ingestUrl with metadata $ingestMetadata")
        }

        //check for orchestrator config existence
        val orchestratorConfigUrl = "${input.baseConfigUrl}orchestrator/${routerConfig.configurationFileName}"
        if(!input.configBlobService.exists(orchestratorConfigUrl)){
            return RouterOutput(errorMessage="Orchestrator config file not found: $orchestratorConfigUrl")
        }

        //parse orchestrator config
        val orchestratorConfig = try{
            val orchContent = input.configBlobService.openDownloadStream(orchestratorConfigUrl).bufferedReader().use(BufferedReader::readText)
            jsonParser.readValue<OrchestratorConfiguration>(orchContent) 
        }catch(e:Exception){
            input.context.logger.log(Level.SEVERE,"Could not parse orchestrator config", e)
            return RouterOutput(errorMessage="Error parsing orchestrator config file $routerConfigUrl : ${e.message}")
        }
        
        //build input
        val orchestratorInput = OrchestratorInput(orchestratorConfig, ActivityParams(executionId=id,originalFileUrl=ingestUrl))
        
        //start orchestrator
        val instanceId = input.durableContext.client.scheduleNewOrchestrationInstance("DexCsvOrchestrator",orchestratorInput);
        input.context.logger.log(Level.INFO, "Created new Java orchestration with instance ID = $instanceId");

        return RouterOutput(orchestratorId=instanceId);
    }

    private fun pullRouterConfig(ingestMetadata:Map<String,String>, configList:List<RouterConfiguration>):RouterConfiguration?{
        val ingestMessageType = ingestMetadata.get("messageType")
        val ingestMessageVersion = ingestMetadata.get("messageVersion")
        for(config in configList){
            if(config.messageType==ingestMessageType && config.messageVersion==ingestMessageVersion){
                return config
            }
        }

        //if get to end, no config was found
        return null
    }
}

data class RouterInput(
    val event               : AzureBlobCreateEventMessage,
    val durableContext      : DurableClientContext,
    val context             : ExecutionContext, 
    val ingestBlobService   : IBlobService, 
    val configBlobService   : IBlobService,
    val baseConfigUrl       : String
)

data class RouterOutput(
    val errorMessage    : String?=null,
    val orchestratorId  : String?=null
)