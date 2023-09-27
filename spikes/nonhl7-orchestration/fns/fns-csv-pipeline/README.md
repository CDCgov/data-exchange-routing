CSV pipeline functions project: Internal Router, Orchestrator, Decompressor, Validator(s), Transformer(s), Reporting, etc.

# Summary
This pipeline uses [Durable Functions](https://learn.microsoft.com/en-us/azure/azure-functions/durable/durable-functions-overview) to efficiently link all the code together. 
The currently implemented functions and their [types](https://learn.microsoft.com/en-us/azure/azure-functions/durable/durable-functions-types-features-overview) are as follows:
* [Internal Router](#internal-router) - Entity Function - the entry point for the pipeline
* [Orchestrator](#orchestrator) - Orchestrator Function - the main workhorse of the pipeline
* [Decompressor](#decompressor) - Activitiy Function - unzips the ingested file, if applicable
* [Generic Validator](#generic-validator) - Activity Function - validates properties of the file without opening it, such as file name
* [Store Reporting Event](#store-reporting-event) - Entity Function - consumes reporting events and adds them to cosmosDB

# Internal Router
**Type**: Entity<br>
**Code**: [FnRouter.kt](src/main/kotlin/gov/cdc/dex/csv/functions/FnRouter.kt)

The Internal Router is the entry point for the pipeline. It is triggered via Event Hub, reads the message and grabs the associated orchestrator configuration, and then triggers the [Orchestrator](#orchestrator).

## Event Specification
The router assumes that the event is triggered when a blob is put in the ingestion container. As such, the following fields are needed in the event message:
* eventType - must equal "Microsoft.Storage.BlobCreated"
* id - used for audit purposes to link everything together
* data.url - location of ingested file

## File Metadata
In order to find the correct configuration, the router reads metadata from the ingested file. This metadata is required to now which CDC program the file is associated with. The following fields are required:
* messageType - the CDC program, disease, or similar identifier (eg, "Routine Immunization" or "COVID-ELR")
* messageVersion - if there are multiple versions for this type, which version is this file

# Orchestrator
**Type**: Orchestrator<br>
**Code**: [FnOrchestrator.kt](src/main/kotlin/gov/cdc/dex/csv/functions/FnOrchestrator.kt)

The Orchestrator is the main workhorse for the pipeline.
It executes each Activity function, splits processes into multiple threads, and handles any errors.
It feeds the parameters into the functions and maintains the state of the pipeline.
All of this heavily relies on the [Durable Function](https://learn.microsoft.com/en-us/azure/azure-functions/durable/durable-functions-orchestrations) infrastructure.

## Fan Out / Fan In
The orchestrator is designed to handle [fan out / fan in](https://learn.microsoft.com/en-us/azure/azure-functions/durable/durable-functions-overview#fan-in-out) patterns.
In order to accomodate an arbitrary amount of nested fanning, this is handled by recursively calling sub-orchestration.
Whenever a fan-out is triggered, a new instance of the orchestrator is called, passing in the current step to run as well as that thread's specific parameters.
Each sub-orchestrator runs until the fan-in step is triggered (or end of the pipeline if there is no fan-in step).
The orchestrator layer above waits for each sub-orchestrator to finish before calling the fan-in step.

## Configuration Layout
The configuration for the Orchestrator is the list of functions to be ran. It is specified in JSON with the following schema:
```
{
	"$schema": "https://json-schema.org/draft/2020-12/schema",
	"title": "OrchestratorConfiguration",
	"description": "the configuration for the orchestrator",
	"type": "object",
	"properties": {
		"steps": {
			"description": "list of functions to be ran by the orchestrator",
			"type": "array",
			"items": {
				"description": "a step in the orchestrator",
				"type": "object",
				"properties": {
					"stepNumber": {
						"description": "unique identifier for the step",
						"type": "string"
					}, 
					"functionToRun": {
						"description": "configuration for the function to run for this step",
						"$ref": "~FunctionConfiguration"
					}, 
					"customErrorFunction": {
						"description": "if the functionToRun fails, run this function before the globalErrorFunction",
						"$ref": "~FunctionConfiguration"
					}, 
					"fanOutAfter": {
						"description": "whether to fan out after this step is ran",
						"type": "boolean",
						"default": false
					}, 
					"fanInBefore": {
						"description": "whether to fan in before this step is ran",
						"type": "boolean",
						"default": false
					}, 
					"fanInFailIfAnyFail": {
						"description": "if any of the threads have failed when try to fan in, fail the pipeline as a whole",
						"type": "boolean",
						"default": true
					}
				},
				"required": ["stepNumber", "functionToRun"]
			}
		},
		"globalErrorFunction": {
			"description": "if any functions fail in the pipeline, run this function",
			"$ref": "~FunctionConfiguration"
		}
	},
	"required": ["steps"]
}
```
where `~FunctionConfiguration` refers to the schema
```
{
	"$schema": "https://json-schema.org/draft/2020-12/schema",
	"title": "FunctionConfiguration",
	"description": "the configuration for an activity function to be called",
	"type": "object",
	"properties":{
		"functionName": {
			"description": "identifier for the Activity function",
			"type": "string"
		}, 
		"functionConfiguration": {
			"description": "configuration objects as needed by the function",
			"type": "object"
		}
	},
	"required": ["functionName"]
}
```
### Example Configurations
Basic configuration, running only generic validation and nothing else
```
{
	"steps":[
		{
			"stepNumber": "1",
			"functionToRun": {
				"functionName": "FnCSVValidationGeneric"
			}
		}
	]
}
```
A more thorough configuration, running the decompressor and validations (note that the schema validation and reporting functions are not yet defined)
```
{
	"steps":[
		{
			"stepNumber": "1",
			"functionToRun": {
				"functionName": "DexCsvDecompressor"
			},
			"fanOutAfter": true
		},
		{
			"stepNumber": "2",
			"functionToRun": {
				"functionName": "FnCSVValidationGeneric"
			}
		},
		{
			"stepNumber": "3",
			"functionToRun": {
				"functionName": "DexCsvSchemaValidator",
				"functionConfiguration": {
					"schemaLocation": "url/to/schema"
				}
			}
		},
		{
			"stepNumber": "4",
			"functionToRun": {
				"functionName": "DexCsvReportGenerator",
				"functionConfiguration": {
					"success": true
				}
			},
			"fanInBefore": true
		}
	],
	"globalErrorFunction": {
		"functionName": "DexCsvReportGenerator",
		"functionConfiguration": {
			"success": false
		}
	}
}
```
## Activity Function Input Parameters
The orchestrator passes data to the Activity Functions using a task hub (similar to events).
The messages are written in JSON, and this pipeline enforces the following structure:
```
{
	"$schema": "https://json-schema.org/draft/2020-12/schema",
	"title": "ActivityInput",
	"description": "the input passed into an Activity function",
	"type": "object",
	"properties":{
		"config": {
			"description": "configuration objects as needed by the function",
			"type": "object"
		}, 
		"common": {
			"description": "the common parameters passed into every function",
			"type": "object",
			"properties": {
				"stepNumber":{
					"description": "a combination of the step number specified in the orchestrator configuration and the index of which fan-out branch is being ran",
					"type": "string"
				},
				"params":{
					"description": "the parameters associated with this run passed into the function",
					"$ref": "~ActivityParams"
				},
				"fanInParams":{
					"description": "if this is a fan-in step, each branch will have its own associated parameters",
					"type": "array",
					"items": {
						"description": "the parameters associated with a single branch",
						"$ref": "~ActivityParams"
					}
				}
			},
			"required": ["stepNumber", "params"]
		}
	},
	"required": ["common"]
}
```
where `~ActivityParams` refers to the schema
```
{
	"$schema": "https://json-schema.org/draft/2020-12/schema",
	"title": "ActivityParams",
	"description": "the parameters specific to this run of the pipeline",
	"type": "object",
	"properties":{
		"executionId": {
			"description": "the ID of the triggering event",
			"type": "string"
		}, 
		"originalFileUrl": {
			"description": "the URL of the file dumped into ingestion",
			"type": "string"
		}, 
		"currentFileUrl": {
			"description": "the URL of the file in its current state in the pipeline",
			"type": "string"
		}, 
		"errorMessage": {
			"description": "the error message to report",
			"type": "string"
		}
	},
	"required": []
}
```
## Activity Function Output Parameters
The activity functions pass data back to the orchestrator using the same task hub mechanism
The messages are written in JSON, and this pipeline enforces the following structure:
```
{
	"$schema": "https://json-schema.org/draft/2020-12/schema",
	"title": "ActivityOutput",
	"description": "the output passed from an Activity function",
	"type": "object",
	"properties":{
		"errorMessage": {
			"description": "details for why this step failed",
			"type": "string"
		}, 
		"updatedParams":{
			"description": "the parameters to update in the orchestrator",
			"$ref": "~ActivityParams"
		},
		"fanOutParams":{
			"description": "if this is a fan-out step, each branch will have its own associated parameters",
			"type": "array",
			"items": {
				"description": "the parameters associated with a single branch",
				"$ref": "~ActivityParams"
			}
		}
	},
	"required": []
}
```
where `~ActivityParams` refers to the schema specified in input params

# Decompressor
**Type**: Activity<br>
**Code**: [FnDecompressor.kt](src/main/kotlin/gov/cdc/dex/csv/functions/activity/FnDecompressor.kt)

This function checks if the file is a ZIP directory. 
If so, it extracts each file from the directory, recursively unzipping if need be.
Each found file (or the single original if not a ZIP) is then associated with branch for fan-out.

# Generic Validator
**Type**: Activity<br>
**Code**: [FnCSVValidationGeneric.kt](src/main/kotlin/gov/cdc/dex/csv/functions/activity/FnCSVValidationGeneric.kt)

This function validates the file is present in the container and the name ends with ".csv"

# Store Reporting Event
**Type**: Entity<br>
**Code**: [FnStoreReportingEvent.kt](src/main/kotlin/gov/cdc/dex/csv/functions/FnStoreReportingEvent.kt)

This function stores events for reporting in a CosmosDB 