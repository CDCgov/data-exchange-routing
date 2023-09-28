package gov.cdc.dex.csv.functions

import com.microsoft.azure.functions.ExecutionContext
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.durabletask.Task
import com.microsoft.durabletask.TaskOrchestrationContext
import com.microsoft.durabletask.azurefunctions.DurableOrchestrationTrigger
import com.azure.messaging.eventhubs.EventData
import com.azure.messaging.eventhubs.EventHubClientBuilder

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectWriter 

import gov.cdc.dex.csv.dtos.ActivityInput
import gov.cdc.dex.csv.dtos.ActivityOutput
import gov.cdc.dex.csv.dtos.OrchestratorInput
import gov.cdc.dex.csv.dtos.OrchestratorOutput
import gov.cdc.dex.csv.dtos.RecursiveOrchestratorOutput
import gov.cdc.dex.csv.dtos.RecursiveOrchestratorInput
import gov.cdc.dex.csv.dtos.CommonInput
import gov.cdc.dex.csv.dtos.ActivityParams
import gov.cdc.dex.csv.dtos.ReportingEvent
import gov.cdc.dex.csv.constants.EnvironmentParam

import java.util.logging.Level

class FnOrchestrator {

    @FunctionName("DexCsvOrchestrator")
    fun orchestrator(
        @DurableOrchestrationTrigger(name = "taskOrchestrationContext") taskContext:TaskOrchestrationContext,
        functionContext:ExecutionContext
    ) : OrchestratorOutput{
        
        //get input passed in from client function
        val input = try{
            taskContext.getInput(OrchestratorInput::class.java)
        }catch(e:Exception){
            log(Level.SEVERE, "Could not retrieve orchestrator input", e, taskContext, functionContext)
            throw e
        }
        log(level=Level.INFO, msg="Started Orchestration with params $input", taskContext=taskContext, functionContext=functionContext)
        sendReportEvent(input.initialParams.currentFileUrl,"0","Starting Analysis",taskContext,functionContext)
        //create input for initial recursive call
        val subInput = RecursiveOrchestratorInput(input, 0, "")
        val recursiveOutput = taskContext.callSubOrchestrator("DexCsvOrchestrator_Recursive", subInput, RecursiveOrchestratorOutput::class.java).await()

        //check overall execution
        val (errorMessage, failedStep) = if(recursiveOutput.output.errorMessage != null){
            Pair(recursiveOutput.output.errorMessage, recursiveOutput.output.failedStep)
        }else if(recursiveOutput.leaveIndex != input.config.steps.size){
            log(Level.SEVERE, "Orchestrator did not run the correct number of steps! Most likely an issue with the fan-out/fan-in specification", taskContext=taskContext, functionContext=functionContext)
            Pair("Orchestrator did not run the correct number of steps! Most likely an issue with the fan-out/fan-in specification", input.config.steps[recursiveOutput.leaveIndex].stepNumber)
        }else{
            Pair(null,null)
        }

        if(errorMessage != null){
            recursiveOutput.output.outputParams.errorMessage = errorMessage
            val errorInput = ActivityInput(input.config.globalErrorFunction.functionConfiguration, CommonInput("-1", recursiveOutput.output.outputParams))
            runActivity(taskContext, functionContext, input.config.globalErrorFunction.functionName, errorInput)
            return OrchestratorOutput(recursiveOutput.output.outputParams, errorMessage, failedStep)
        }
        
        return OrchestratorOutput(outputParams=recursiveOutput.output.outputParams)
    }

    @FunctionName("DexCsvOrchestrator_Recursive")
    fun recursiveOrchestrator(
        @DurableOrchestrationTrigger(name = "taskOrchestrationContext") taskContext:TaskOrchestrationContext,
        functionContext:ExecutionContext
    ) : RecursiveOrchestratorOutput{
        val recursiveInput = taskContext.getInput(RecursiveOrchestratorInput::class.java)

        val steps = recursiveInput.input.config.steps
        var globalParams = recursiveInput.input.initialParams

        var indexToRun = recursiveInput.enterIndex

        var paramsToFanIn:List<ActivityParams>? = null

        while(indexToRun < steps.size){
            val step = steps[indexToRun]

            val stepNumber = step.stepNumber+recursiveInput.branchIndex

            val stepInput = if(step.fanInBefore && paramsToFanIn == null){
                //we need to return to the prior level before running this step
                return RecursiveOrchestratorOutput(OrchestratorOutput(globalParams), indexToRun)
            }else if(step.fanInBefore){
                val fanInInput = ActivityInput(step.functionToRun.functionConfiguration, CommonInput(stepNumber, globalParams, paramsToFanIn))
                paramsToFanIn = null
                fanInInput
            }else{
                ActivityInput(step.functionToRun.functionConfiguration, CommonInput(stepNumber, globalParams))
            }

            //run the step
            val stepOutput = runActivity(taskContext, functionContext, step.functionToRun.functionName, stepInput)

            //check for errors
            val errorMessage = if(stepOutput.errorMessage!=null){
                stepOutput.errorMessage
            }else if(step.fanOutAfter && stepOutput.fanOutParams.isNullOrEmpty()){
                "Tried to fan out but function did not return correct parameters"
            }else{
                null
            }

            //send Reporting Event
            sendReportEvent(globalParams.originalFileUrl, stepNumber, errorMessage, taskContext, functionContext)

            if(errorMessage != null){
                log(level=Level.SEVERE, msg="Step ${stepNumber} had error $errorMessage", taskContext=taskContext, functionContext=functionContext)
                globalParams.errorMessage = errorMessage
                if(step.customErrorFunction != null){
                    val errorInput = ActivityInput(step.customErrorFunction.functionConfiguration, CommonInput(stepNumber, globalParams))
                    val errorOutput = runActivity(taskContext, functionContext, step.customErrorFunction.functionName, errorInput)
                    //TODO do we want to do anything with its output?
                }
                return RecursiveOrchestratorOutput(OrchestratorOutput(globalParams, errorMessage, stepNumber),indexToRun)
            }

            //if global parameters are updated, replace
            globalParams = stepOutput.updatedParams ?: globalParams

            //update indexToRun based on whether fanning out or not
            indexToRun = if(!step.fanOutAfter){
                indexToRun + 1
            }else{
                val parallelTasks:MutableList<Task<RecursiveOrchestratorOutput>> = mutableListOf()
                //fanned out params being null or empty was already checked in the error handling
                for((index,fannedOutParams) in stepOutput.fanOutParams!!.withIndex()){
                    val subInput = RecursiveOrchestratorInput(
                            OrchestratorInput(recursiveInput.input.config, fannedOutParams),
                            (indexToRun+1),
                            "${recursiveInput.branchIndex}_$index")
                    parallelTasks.add(taskContext.callSubOrchestrator("DexCsvOrchestrator_Recursive", subInput, RecursiveOrchestratorOutput::class.java))
                }

                val subOutputs = taskContext.allOf(parallelTasks).await()
                
                var fanInIndices = mutableSetOf<Int>()
                val errorMessages = mutableListOf<String>()
                val internalParamsToFanIn = mutableListOf<ActivityParams>()
                for(subOutput in subOutputs){
                    if(subOutput.output.errorMessage != null){
                        errorMessages.add(subOutput.output.errorMessage)
                    }else{
                        fanInIndices.add(subOutput.leaveIndex)
                        internalParamsToFanIn.add(subOutput.output.outputParams)
                    }
                }

                var fanInIndex = -1
                val fanInErrorMessage = 
                    if(fanInIndices.size > 1){
                        "Mismatched fan-in indices for successful branches $fanInIndices. Other errors $errorMessages"
                    } else if(fanInIndices.size==0){
                        "No successful branches. Errors: $errorMessages"
                    } else {
                        fanInIndex = fanInIndices.elementAt(0)
                        
                        //if no fan in step is specified, then fanInIndex will be more than the number of steps
                        //in this case, use the default to fail if any branch failed
                        if( errorMessages.isNotEmpty() &&
                            (fanInIndex >= steps.size || steps[fanInIndex].fanInFailIfAnyFail)
                        ){
                            "One or more fanned out branches failed with the following errors: $errorMessages"
                        }else{
                            null
                        }
                    }

                if(fanInErrorMessage != null){
                    return RecursiveOrchestratorOutput(
                            OrchestratorOutput(globalParams, fanInErrorMessage, "Fanned-out starting from ${stepNumber}"
                        ),fanInIndex)
                }

                //set parameters so the code knows that we are fanning in here, and not returning
                paramsToFanIn = internalParamsToFanIn

                //next to be ran is the fan in
                fanInIndex
            }
        }
        return RecursiveOrchestratorOutput(OrchestratorOutput(outputParams=globalParams),indexToRun)

    
    }

    private fun runActivity(
        taskContext:TaskOrchestrationContext, functionContext:ExecutionContext, functionName:String, activityInput:ActivityInput,
    ):ActivityOutput{
        val activityOutput = try{ 
            taskContext.callActivity(functionName, activityInput, ActivityOutput::class.java).await()
        }catch(e:com.microsoft.durabletask.TaskFailedException){
            log(Level.SEVERE, "Error in step ${activityInput.common.stepNumber}", e, taskContext, functionContext)
            ActivityOutput(errorMessage = e.localizedMessage)
        }
        return activityOutput;
    }

    private fun log(
        level:Level, msg:String, thrown:Throwable?=null, taskContext:TaskOrchestrationContext, functionContext:ExecutionContext
    ){
        if (!taskContext.getIsReplaying()) {
            if(thrown == null){
                functionContext.logger.log(level, msg);
            } else{
                functionContext.logger.log(level, msg, thrown);
            }
        }
    }

    private fun sendReportEvent(
        fileName    :String? = null,
        stepNumber  :String? = null,
        errorMessage:String? = null,
        taskContext:TaskOrchestrationContext,
        functionContext:ExecutionContext
    ){
        log(Level.INFO, "sending report event",null,taskContext,functionContext)
        val connectionString = "Endpoint=sb://dex-csv-event-namespace.servicebus.windows.net/;SharedAccessKeyName=dex-csv-event-access;SharedAccessKey=9CJdX0N5On2swY/X+7LHYAyo7379SVsvx+AEhK/tzds="
        val eventHubName = "dex-csv-reporting-events"

        //Create Reporting Event
        var reportItem:ReportingEvent = ReportingEvent(fileName, stepNumber, errorMessage)
        //Nothing can be null apparently
        if (fileName == null){
            reportItem.currentFile = "unknown"
        }    
        if (stepNumber == null){
            reportItem.stepNumber = "unknown"
        }
        if(errorMessage == null){
            reportItem.errorMessage = "No errors"
        }

        EnvironmentParam.EVENTHUB_NAME_REPORTINGEVENT.getSystemValue()
        EnvironmentParam.EVENTHUB_CONNECTION.getSystemValue()

        // create a producer client
        val producerClient = EventHubClientBuilder()
            .connectionString(connectionString, eventHubName)
            .buildProducerClient();

        // create a batch
        var eventDataBatch = producerClient.createBatch();
        
        //create message and add to batch
        val objectMapper = jacksonObjectMapper()
        val message:String = objectMapper.writeValueAsString(reportItem)
        log(Level.INFO, "event: " + message, null, taskContext, functionContext)
        val eventData = EventData(message)

        // try to add the event to the batch
        eventDataBatch.tryAdd(eventData)

        // send the event
        if (eventDataBatch.getCount() > 0) {
            producerClient.send(eventDataBatch);
        }
        producerClient.close();
    }
}
