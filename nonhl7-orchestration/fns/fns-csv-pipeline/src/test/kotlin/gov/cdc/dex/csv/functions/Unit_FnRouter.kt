package gov.cdc.dex.csv.functions

import com.microsoft.durabletask.azurefunctions.DurableClientContext
import com.microsoft.durabletask.DurableTaskClient

import gov.cdc.dex.csv.BlobServiceMocker
import gov.cdc.dex.csv.ContextMocker
import gov.cdc.dex.csv.dtos.AzureBlobCreateEventMessage
import gov.cdc.dex.csv.dtos.EvHubData
import gov.cdc.dex.csv.dtos.OrchestratorInput
import gov.cdc.dex.csv.dtos.OrchestratorConfiguration
import gov.cdc.dex.csv.dtos.ActivityParams
import gov.cdc.dex.csv.dtos.FunctionDefinition
import gov.cdc.dex.csv.dtos.OrchestratorStep

import java.io.File

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito

internal class Unit_FnRouter {
    companion object{
        private val configParentDir = File("src/test/resources/testfiles/config")
        private val ingestParentDir = File("src/test/resources/testfiles")
    }

    private val ranOrchestrations = mutableListOf<OrchestratorInput>()

    @BeforeEach
    internal fun setup(){
        ranOrchestrations.clear()
    }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //happy path
    @Test
    internal fun happyPath(){
        val input = createInput(id="happyPath", url="test-upload.csv", messageType="happyPath", messageVersion="DUMMY")
        val response = FnRouter().pipelineEntry(input)
        Assertions.assertNotNull(response)
        Assertions.assertNull(response.errorMessage)
        Assertions.assertEquals("MOCK_ID",response.orchestratorId)
        Assertions.assertEquals(1, ranOrchestrations.size, "wrong number of orchestrations triggered")

        val orchInput = ranOrchestrations.get(0)
        Assertions.assertEquals(OrchestratorInput(
            config=OrchestratorConfiguration(
                steps=listOf(
                    OrchestratorStep(
                        stepNumber="1",
                        functionToRun=FunctionDefinition(functionName="1-functionToRun")
                    )
                ),
                globalErrorFunction=FunctionDefinition(functionName="globalErrorFunction")
            ),
            initialParams=ActivityParams(
                executionId="happyPath",
                originalFileUrl="test-upload.csv"
            )
        ), orchInput)

    }
    
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //TODO a lot of negative testing
    //- trigger each error message in FnRouter
    //- malform routerConfig (missing fields, extra fields, bad structure, etc)
    //- malform orch config (missing fields, extra fields, bad structure, etc)
    
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //helper functions

    private fun createInput(
        id:String?=null, 
        url:String?=null, 
        messageType:String?=null, 
        messageVersion:String?=null, 
        eventType:String? = "Microsoft.Storage.BlobCreated", 
        baseConfigUrl:String = ""
    ):RouterInput{
        val metadata = mutableMapOf<String,String>()
        if(messageType!=null){
            metadata.put("messageType", messageType)
        }
        if(messageVersion!=null){
            metadata.put("messageVersion", messageVersion)
        }
        return RouterInput(
            AzureBlobCreateEventMessage(eventType, id, EvHubData(url)), 
            mockClientContext(), 
            ContextMocker.mockExecutionContext(), 
            BlobServiceMocker.mockBlobService(ingestParentDir, metadata=metadata), 
            BlobServiceMocker.mockBlobService(configParentDir), 
            baseConfigUrl)
    }
    
    private fun mockClientContext():DurableClientContext{
        val mockContext : DurableClientContext = Mockito.mock(DurableClientContext::class.java)
        Mockito.`when`(mockContext.client).thenAnswer({mockClient()})
        return mockContext
    }

    private fun mockClient():DurableTaskClient{
        val mockClient : DurableTaskClient = Mockito.mock(DurableTaskClient::class.java)
        Mockito.`when`(mockClient.scheduleNewOrchestrationInstance(Mockito.anyString(),Mockito.any(Object::class.java)))
            .thenAnswer({mockOrchestration(it.getArgument(1))})
        return mockClient
    }

    private fun mockOrchestration(input:OrchestratorInput):String{
        ranOrchestrations.add(input)
        return "MOCK_ID"
    }
}