package gov.cdc.dex.csv.functions

import com.microsoft.durabletask.Task
import com.microsoft.durabletask.TaskFailedException
import com.microsoft.durabletask.TaskOrchestrationContext

import gov.cdc.dex.csv.ContextMocker
import gov.cdc.dex.csv.dtos.OrchestratorInput
import gov.cdc.dex.csv.dtos.ActivityParams
import gov.cdc.dex.csv.dtos.FunctionDefinition
import gov.cdc.dex.csv.dtos.OrchestratorConfiguration
import gov.cdc.dex.csv.dtos.OrchestratorStep
import gov.cdc.dex.csv.dtos.RecursiveOrchestratorOutput
import gov.cdc.dex.csv.dtos.RecursiveOrchestratorInput
import gov.cdc.dex.csv.dtos.ActivityOutput
import gov.cdc.dex.csv.dtos.CommonInput
import gov.cdc.dex.csv.dtos.ActivityInput

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito

internal class Unit_FnOrchestrator {
    private val ranFunctions = mutableListOf<String>()

    @BeforeEach
    internal fun setup(){
        ranFunctions.clear()
    }
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //happy path
    @Test
    internal fun happyPath_noSteps(){
        System.out.println("---------------------------------------------------------------------------------------\nhappyPath_noSteps")
        val steps = mutableListOf<OrchestratorStep>()

        val config = OrchestratorConfiguration(steps, buildFunc("Global Error"))
        val initialParams = ActivityParams()
        val input = OrchestratorInput(config,initialParams)

        val response = FnOrchestrator().orchestrator(mockOrchestratorContext(input), ContextMocker.mockExecutionContext())
        System.out.println("\nRESPONSE\n   $response")
        Assertions.assertEquals(ActivityParams(), response.outputParams)
        Assertions.assertNull(response.errorMessage)
        Assertions.assertNull(response.failedStep)
        Assertions.assertTrue(ranFunctions.isEmpty(),"Steps were ran when they weren't supposed to")
    }

    @Test
    internal fun happyPath_oneStep(){
        System.out.println("---------------------------------------------------------------------------------------\nhappyPath_oneStep")
        val steps = mutableListOf<OrchestratorStep>()
        steps.add(OrchestratorStep("1", buildFunc("Single Step")))

        val config = OrchestratorConfiguration(steps, buildFunc("Global Error"))
        val initialParams = ActivityParams()
        val input = OrchestratorInput(config,initialParams)

        val response = FnOrchestrator().orchestrator(mockOrchestratorContext(input), ContextMocker.mockExecutionContext())
        System.out.println("\nRESPONSE\n   $response")
        Assertions.assertEquals(ActivityParams(currentFileUrl = "UPDATED"), response.outputParams)
        Assertions.assertNull(response.errorMessage)
        Assertions.assertNull(response.failedStep)
        Assertions.assertEquals(listOf("1 : Single Step"), ranFunctions)
    }

    @Test
    internal fun happyPath_fullFan(){
        System.out.println("---------------------------------------------------------------------------------------\nhappyPath_fullFan")
        val steps = mutableListOf<OrchestratorStep>()
        steps.add(OrchestratorStep("1", buildFunc("Fan Out Step",fanOut=true), fanOutAfter = true))
        steps.add(OrchestratorStep("2", buildFunc("Single Step")))
        steps.add(OrchestratorStep("3", buildFunc("Fan In Step"), fanInBefore = true))

        val config = OrchestratorConfiguration(steps, buildFunc("Global Error"))
        val initialParams = ActivityParams()
        val input = OrchestratorInput(config,initialParams)

        val response = FnOrchestrator().orchestrator(mockOrchestratorContext(input), ContextMocker.mockExecutionContext())
        System.out.println("\nRESPONSE\n   $response")
        Assertions.assertEquals(ActivityParams(currentFileUrl = "UPDATED"), response.outputParams)
        Assertions.assertNull(response.errorMessage)
        Assertions.assertNull(response.failedStep)
        Assertions.assertEquals(listOf("1 : Fan Out Step", "2_0 : Single Step", "2_1 : Single Step", "3 : Fan In Step"), ranFunctions)
    }

    @Test
    internal fun happyPath_fanOut_noFanIn(){
        System.out.println("---------------------------------------------------------------------------------------\nhappyPath_fanOut_noFanIn")
        val steps = mutableListOf<OrchestratorStep>()
        steps.add(OrchestratorStep("1", buildFunc("Fan Out Step",fanOut=true), fanOutAfter = true))
        steps.add(OrchestratorStep("2", buildFunc("Single Step")))

        val config = OrchestratorConfiguration(steps, buildFunc("Global Error"))
        val initialParams = ActivityParams()
        val input = OrchestratorInput(config,initialParams)

        val response = FnOrchestrator().orchestrator(mockOrchestratorContext(input), ContextMocker.mockExecutionContext())
        System.out.println("\nRESPONSE\n   $response")
        Assertions.assertEquals(ActivityParams(currentFileUrl = "UPDATED"), response.outputParams)
        Assertions.assertNull(response.errorMessage)
        Assertions.assertNull(response.failedStep)
        Assertions.assertEquals(listOf("1 : Fan Out Step", "2_0 : Single Step", "2_1 : Single Step"), ranFunctions)
    }

    @Test
    internal fun happyPath_twoLayerFan(){
        System.out.println("---------------------------------------------------------------------------------------\nhappyPath_twoLayerFan")
        val steps = mutableListOf<OrchestratorStep>()
        steps.add(OrchestratorStep("1", buildFunc("Fan Out Step A",fanOut=true), fanOutAfter = true))
        steps.add(OrchestratorStep("2", buildFunc("Single Step B")))
        steps.add(OrchestratorStep("3", buildFunc("Fan Out Step B",fanOut=true), fanOutAfter = true))
        steps.add(OrchestratorStep("4", buildFunc("Single Step C")))
        steps.add(OrchestratorStep("5", buildFunc("Fan In Step B"), fanInBefore = true))

        val config = OrchestratorConfiguration(steps, buildFunc("Global Error"))
        val initialParams = ActivityParams()
        val input = OrchestratorInput(config,initialParams)

        val response = FnOrchestrator().orchestrator(mockOrchestratorContext(input), ContextMocker.mockExecutionContext())
        System.out.println("\nRESPONSE\n   $response")
        Assertions.assertEquals(ActivityParams(currentFileUrl = "UPDATED"), response.outputParams)
        Assertions.assertNull(response.errorMessage)
        Assertions.assertNull(response.failedStep)
        Assertions.assertEquals(listOf("1 : Fan Out Step A", 
                "2_0 : Single Step B", 
                "3_0 : Fan Out Step B", 
                    "4_0_0 : Single Step C", 
                    "4_0_1 : Single Step C", 
                "5_0 : Fan In Step B", 
                
                "2_1 : Single Step B", 
                "3_1 : Fan Out Step B", 
                    "4_1_0 : Single Step C", 
                    "4_1_1 : Single Step C", 
                "5_1 : Fan In Step B"), ranFunctions)
    }

    @Test
    internal fun happyPath_fanIn_anyFailFalse(){
        System.out.println("---------------------------------------------------------------------------------------\nhappyPath_fanIn_anyFailFalse")
        val steps = mutableListOf<OrchestratorStep>()
        steps.add(OrchestratorStep("1", buildFunc("Fan Out Step",fanOut=true), fanOutAfter = true))
        steps.add(OrchestratorStep("2", buildFunc("Single Step",errorToReturn="dummy error", failOnSingleBranch=true), 
                customErrorFunction=buildFunc("Step Error")))
        steps.add(OrchestratorStep("3", buildFunc("Single Step")))
        steps.add(OrchestratorStep("4", buildFunc("Fan In Step"), fanInBefore = true, fanInFailIfAnyFail = false))

        val config = OrchestratorConfiguration(steps, buildFunc("Global Error"))
        val initialParams = ActivityParams()
        val input = OrchestratorInput(config,initialParams)

        val response = FnOrchestrator().orchestrator(mockOrchestratorContext(input), ContextMocker.mockExecutionContext())
        System.out.println("\nRESPONSE\n   $response")
        Assertions.assertEquals(ActivityParams(currentFileUrl = "UPDATED"), response.outputParams)
        Assertions.assertNull(response.errorMessage)
        Assertions.assertNull(response.failedStep)
        Assertions.assertEquals(listOf("1 : Fan Out Step", 
                "2_0 : Single Step", 
                "2_0 : Step Error", 
                "2_1 : Single Step", 
                "3_1 : Single Step", 
            "4 : Fan In Step"), ranFunctions)
    }
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //activity errors

    @Test
    internal fun negative_activityReturnError_globalError(){
        System.out.println("---------------------------------------------------------------------------------------\nnegative_activityReturnError_globalError")
        val steps = mutableListOf<OrchestratorStep>()
        steps.add(OrchestratorStep("1", buildFunc("Single Step",errorToReturn="dummy error")))
        steps.add(OrchestratorStep("2", buildFunc("Not Ran Step")))

        val config = OrchestratorConfiguration(steps, buildFunc("Global Error"))
        val initialParams = ActivityParams()
        val input = OrchestratorInput(config,initialParams)

        val response = FnOrchestrator().orchestrator(mockOrchestratorContext(input), ContextMocker.mockExecutionContext())
        System.out.println("\nRESPONSE\n   $response")
        Assertions.assertEquals(ActivityParams(errorMessage="dummy error"), response.outputParams)
        Assertions.assertEquals("dummy error", response.errorMessage)
        Assertions.assertEquals("1", response.failedStep)
        Assertions.assertEquals(listOf("1 : Single Step", "-1 : Global Error"), ranFunctions)
    }
    
    @Test
    internal fun negative_activityThrowError_globalError(){
        System.out.println("---------------------------------------------------------------------------------------\nnegative_activityThrowError_globalError")
        val steps = mutableListOf<OrchestratorStep>()
        steps.add(OrchestratorStep("1", buildFunc("Single Step",errorToThrow="dummy error")))
        steps.add(OrchestratorStep("2", buildFunc("Not Ran Step")))

        val config = OrchestratorConfiguration(steps, buildFunc("Global Error"))
        val initialParams = ActivityParams()
        val input = OrchestratorInput(config,initialParams)

        val response = FnOrchestrator().orchestrator(mockOrchestratorContext(input), ContextMocker.mockExecutionContext())
        System.out.println("\nRESPONSE\n   $response")
        Assertions.assertEquals(ActivityParams(errorMessage="dummy error"), response.outputParams)
        Assertions.assertEquals("dummy error", response.errorMessage)
        Assertions.assertEquals("1", response.failedStep)
        Assertions.assertEquals(listOf("1 : Single Step", "-1 : Global Error"), ranFunctions)
    }
    @Test
    internal fun negative_activityReturnError_stepError(){
        System.out.println("---------------------------------------------------------------------------------------\nnegative_activityReturnError_stepError")
        val steps = mutableListOf<OrchestratorStep>()
        steps.add(OrchestratorStep("1", buildFunc("Single Step",errorToReturn="dummy error"), customErrorFunction=buildFunc("Step Error")))
        steps.add(OrchestratorStep("2", buildFunc("Not Ran Step")))

        val config = OrchestratorConfiguration(steps, buildFunc("Global Error"))
        val initialParams = ActivityParams()
        val input = OrchestratorInput(config,initialParams)

        val response = FnOrchestrator().orchestrator(mockOrchestratorContext(input), ContextMocker.mockExecutionContext())
        System.out.println("\nRESPONSE\n   $response")
        Assertions.assertEquals(ActivityParams(errorMessage="dummy error"), response.outputParams)
        Assertions.assertEquals("dummy error", response.errorMessage)
        Assertions.assertEquals("1", response.failedStep)
        Assertions.assertEquals(listOf("1 : Single Step", "1 : Step Error", "-1 : Global Error"), ranFunctions)
    }

    @Test
    internal fun negative_activityThrowError_stepError(){
        System.out.println("---------------------------------------------------------------------------------------\nnegative_activityThrowError_stepError")
        val steps = mutableListOf<OrchestratorStep>()
        steps.add(OrchestratorStep("1", buildFunc("Single Step",errorToThrow="dummy error"), customErrorFunction=buildFunc("Step Error")))
        steps.add(OrchestratorStep("2", buildFunc("Not Ran Step")))

        val config = OrchestratorConfiguration(steps, buildFunc("Global Error"))
        val initialParams = ActivityParams()
        val input = OrchestratorInput(config,initialParams)

        val response = FnOrchestrator().orchestrator(mockOrchestratorContext(input), ContextMocker.mockExecutionContext())
        System.out.println("\nRESPONSE\n   $response")
        Assertions.assertEquals(ActivityParams(errorMessage="dummy error"), response.outputParams)
        Assertions.assertEquals("dummy error", response.errorMessage)
        Assertions.assertEquals("1", response.failedStep)
        Assertions.assertEquals(listOf("1 : Single Step", "1 : Step Error", "-1 : Global Error"), ranFunctions)
    }

    @Test
    internal fun negative_fanIn_anyFailTrue(){
        System.out.println("---------------------------------------------------------------------------------------\nnegative_fanIn_anyFailTrue")
        val steps = mutableListOf<OrchestratorStep>()
        steps.add(OrchestratorStep("1", buildFunc("Fan Out Step",fanOut=true), fanOutAfter = true))
        steps.add(OrchestratorStep("2", buildFunc("Single Step",errorToReturn="dummy error", failOnSingleBranch=true), 
                customErrorFunction=buildFunc("Step Error")))
        steps.add(OrchestratorStep("3", buildFunc("Single Step")))

        val config = OrchestratorConfiguration(steps, buildFunc("Global Error"))
        val initialParams = ActivityParams()
        val input = OrchestratorInput(config,initialParams)

        val response = FnOrchestrator().orchestrator(mockOrchestratorContext(input), ContextMocker.mockExecutionContext())
        System.out.println("\nRESPONSE\n   $response")
        Assertions.assertEquals(ActivityParams(currentFileUrl = "UPDATED", errorMessage="One or more fanned out branches failed with the following errors: [dummy error]"), response.outputParams)
        Assertions.assertEquals("One or more fanned out branches failed with the following errors: [dummy error]", response.errorMessage)
        Assertions.assertEquals("Fanned-out starting from 1", response.failedStep)
        Assertions.assertEquals(listOf("1 : Fan Out Step", 
                "2_0 : Single Step", 
                "2_0 : Step Error", 
                "2_1 : Single Step", 
                "3_1 : Single Step", 
            "-1 : Global Error"), ranFunctions)
    }

    @Test
    internal fun negative_fanIn_allFail(){
        System.out.println("---------------------------------------------------------------------------------------\nnegative_fanIn_allFail")
        val steps = mutableListOf<OrchestratorStep>()
        steps.add(OrchestratorStep("1", buildFunc("Fan Out Step",fanOut=true), fanOutAfter = true))
        steps.add(OrchestratorStep("2", buildFunc("Single Step",errorToReturn="dummy error"), 
                customErrorFunction=buildFunc("Step Error")))
        steps.add(OrchestratorStep("3", buildFunc("Single Step")))
        steps.add(OrchestratorStep("4", buildFunc("Fan In Step"), fanInBefore = true, fanInFailIfAnyFail = false))

        val config = OrchestratorConfiguration(steps, buildFunc("Global Error"))
        val initialParams = ActivityParams()
        val input = OrchestratorInput(config,initialParams)

        val response = FnOrchestrator().orchestrator(mockOrchestratorContext(input), ContextMocker.mockExecutionContext())
        System.out.println("\nRESPONSE\n   $response")
        Assertions.assertEquals(ActivityParams(currentFileUrl = "UPDATED", errorMessage="No successful branches. Errors: [dummy error, dummy error]"), response.outputParams)
        Assertions.assertEquals("No successful branches. Errors: [dummy error, dummy error]", response.errorMessage)
        Assertions.assertEquals("Fanned-out starting from 1", response.failedStep)
        Assertions.assertEquals(listOf("1 : Fan Out Step", 
                "2_0 : Single Step", 
                "2_0 : Step Error", 
                "2_1 : Single Step", 
                "2_1 : Step Error", 
            "-1 : Global Error"), ranFunctions)
    }
    
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //fanning errors

    @Test
    internal fun negative_fanOut_noFanOutParams(){
        System.out.println("---------------------------------------------------------------------------------------\nnegative_fanOut_noFanOutParams")
        val steps = mutableListOf<OrchestratorStep>()
        steps.add(OrchestratorStep("1", buildFunc("Fan Out Step"), fanOutAfter = true))

        val config = OrchestratorConfiguration(steps, buildFunc("Global Error"))
        val initialParams = ActivityParams()
        val input = OrchestratorInput(config,initialParams)

        val response = FnOrchestrator().orchestrator(mockOrchestratorContext(input), ContextMocker.mockExecutionContext())
        System.out.println("\nRESPONSE\n   $response")
        Assertions.assertEquals(ActivityParams(errorMessage="Tried to fan out but function did not return correct parameters"), response.outputParams)
        Assertions.assertEquals("Tried to fan out but function did not return correct parameters", response.errorMessage)
        Assertions.assertEquals("1", response.failedStep)
        Assertions.assertEquals(listOf("1 : Fan Out Step", "-1 : Global Error"), ranFunctions)
    }

    @Test
    internal fun negative_fanIn_noFanOut(){
        System.out.println("---------------------------------------------------------------------------------------\nnegative_fanIn_noFanOut")
        val steps = mutableListOf<OrchestratorStep>()
        steps.add(OrchestratorStep("1", buildFunc("Fan In Step"), fanInBefore = true))

        val config = OrchestratorConfiguration(steps, buildFunc("Global Error"))
        val initialParams = ActivityParams()
        val input = OrchestratorInput(config,initialParams)

        val response = FnOrchestrator().orchestrator(mockOrchestratorContext(input), ContextMocker.mockExecutionContext())
        System.out.println("\nRESPONSE\n   $response")
        Assertions.assertEquals(ActivityParams(errorMessage="Orchestrator did not run the correct number of steps! Most likely an issue with the fan-out/fan-in specification"), response.outputParams)
        Assertions.assertEquals("Orchestrator did not run the correct number of steps! Most likely an issue with the fan-out/fan-in specification", response.errorMessage)
        Assertions.assertEquals("1", response.failedStep)
        Assertions.assertEquals(listOf("-1 : Global Error"), ranFunctions)
    }

    // @Test
    // internal fun negative_fanIn_mismatchIndex(){
    //     System.out.println("---------------------------------------------------------------------------------------\nnegative_fanIn_mismatchIndex")
    //     TODO("not sure how this could ever happen")
    // }
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //helper functions

    private fun mockOrchestratorContext(input:Any): TaskOrchestrationContext{
        val mockContext : TaskOrchestrationContext = Mockito.mock(TaskOrchestrationContext::class.java)

        Mockito.`when`(mockContext.getInput(Mockito.any(Class::class.java))).thenReturn(input)
        Mockito.`when`(mockContext.getIsReplaying()).thenReturn(false)
        Mockito.`when`(mockContext.callSubOrchestrator(Mockito.anyString(),Mockito.any(),Mockito.any(Class::class.java)))
            .thenAnswer({mockSubOrchestratorTask(it.getArgument(1))})
        Mockito.`when`(mockContext.callActivity(Mockito.anyString(),Mockito.any(),Mockito.any(Class::class.java)))
            .thenAnswer({mockActivityTask(it.getArgument(0), it.getArgument(1))})
        Mockito.`when`(mockContext.allOf(Mockito.any<MutableList<Task<Any>>>()))
            .thenAnswer({mockAllWait(it.getArgument(0))})

        return mockContext
    }

    private fun mockSubOrchestratorTask(subInput:Any):Task<*>{
        val mockTask = Mockito.mock(Task::class.java)
        Mockito.`when`(mockTask.await())
            .thenAnswer({
                try{FnOrchestrator().recursiveOrchestrator(mockOrchestratorContext(subInput), ContextMocker.mockExecutionContext())}
                catch(e:Exception){
                    e.printStackTrace()
                    throw e
                }
            })
        return mockTask
    }
    
    private fun mockAllWait(taskList:MutableList<Task<Any>>):Task<*>{
        val mockTask = Mockito.mock(Task::class.java)
        Mockito.`when`(mockTask.await()).thenAnswer({taskList.map{it.await()}})
        return mockTask
    }

    private fun mockActivityTask(name:String,input:ActivityInput):Task<*>{
        ranFunctions.add("${input.common.stepNumber} : $name")
        val config:DummyActivityConfig = input.config as DummyActivityConfig

        
        val mockTask = Mockito.mock(Task::class.java)
        if(config.errorToThrow != null){
            val exception = Mockito.mock(TaskFailedException::class.java)
            Mockito.`when`(exception.localizedMessage).thenReturn(config.errorToThrow)
            Mockito.`when`(mockTask.await()).thenThrow(exception)
        } else {
            val params = input.common.params.copy(currentFileUrl=config.updateParamTo)
            val fanOutParams = if(config.fanOut){listOf(params.copy(),params.copy())}else{null}
            val errorMessage = if(!config.failOnSingleBranch || input.common.stepNumber.endsWith("0")){config.errorToReturn}else{null}
            val activityOutput = ActivityOutput(updatedParams=params, errorMessage=errorMessage, fanOutParams=fanOutParams)
            Mockito.`when`(mockTask.await()).thenReturn(activityOutput)
        }

        return mockTask
    }


    private fun buildFunc(
        name                : String,
        updateParamTo       : String = "UPDATED",
        errorToReturn       : String? = null,
        errorToThrow        : String? = null,
        failOnSingleBranch  : Boolean = false,
        fanOut              : Boolean = false
    ):FunctionDefinition{
        return FunctionDefinition(functionName=name, 
                functionConfiguration = DummyActivityConfig(updateParamTo=updateParamTo, errorToReturn=errorToReturn, errorToThrow=errorToThrow, failOnSingleBranch=failOnSingleBranch, fanOut=fanOut)
            )
    }
}


data class DummyActivityConfig(
    val updateParamTo       : String? = null,
    val errorToReturn       : String? = null,
    val errorToThrow        : String? = null,
    val failOnSingleBranch  : Boolean = false,
    val fanOut              : Boolean = false
)