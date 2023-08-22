CSV pipeline functions project: Internal Router, Orchestrator, Decompressor, Validator(s), Transformer(s), Reporting, etc.

# Summary
This pipeline uses [Durable Functions](https://learn.microsoft.com/en-us/azure/azure-functions/durable/durable-functions-overview) to efficiently link all the code together. 
The currently implemented functions and their [types](https://learn.microsoft.com/en-us/azure/azure-functions/durable/durable-functions-types-features-overview) are as follows:
* [Internal Router](#internal-router) - Entity Function - the entry point for the pipeline
* [Orchestrator](#orchestrator) - Orchestrator Function - the main workhorse of the pipeline
* [Decompressor](#decompressor) - Activitiy Function - peeks into a zipped file
* [Filename Validator](#filename-validator) - Activity Function - validates properties of the file without opening it, such as file name
* [Schema Validator](#schema-validator) - Activity Function - validates the contents of the file against the associated schema

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
				"functionName": "FnValidateFilename"
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
				"functionName": "FnValidateFilename"
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
		"pathInZip": {
			"description": "the path within the zip file to the specific file for this branch",
			"type": "string"
		}, 
		"validationErrors": {
			"description": "the error messages reported by the schema validator",
			"type": "array",
			"items": {
				"description": "a single error message",
				"type": "object",
				"properties":{
					"message":{
						"description": "the message given by the schema validator",
						"type": "string"
					},
					"lineNumber":{
						"description": "the line in the file which has the error (1-based), or -1 if applies to the file as a whole"
						"type": "integer"
					},
					"columnIndex":{
						"description": "the column in the line which has the error (0-based), or -1 if applies to the row as a whole"
						"type": "integer"
					}
				},
				"required":["message","lineNumber","columnIndex"]
			}
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
**Configuration Params**: none

This function checks if the file is a ZIP directory. 
If so, it peeks through the directory, recursively peeking into nested ZIP if need be.
Each found file (or the single original if not a ZIP) is then associated with branch for fan-out.

>**NOTE** the changes to just peek in the zip instead of unzipping are untested. The unit tests were not able to be updated before the handoff.
>Since no use cases want unzipped files, there is "no need" to have the files physically unzipped (there might be performance concerns, but we did not have a chance to test these out).
>As such, there is an idea of just peeking to grab the file names here, and then in the validation function itself stream the ZIP to this file and then only read in the contents of that file.
>Again, there are a lot of performance considerations that have not been tested with either this approach or the original approach.

# Filename Validator
**Type**: Activity<br>
**Code**: [FnValidateFilename.kt](src/main/kotlin/gov/cdc/dex/csv/functions/activity/FnValidateFilename.kt)
**Configuration Params**: none

This function validates the file is present in the container and the name ends with ".csv"

# Schema Validator
**Type**: Activity<br>
**Code**: [FnValidateFilename.kt](src/main/kotlin/gov/cdc/dex/csv/functions/activity/FnValidateFilename.kt)
**Configuration Params**:
* schemaUrl - location of the schema file to use
* relaxedHeader - whether to relax header requirements (ordering, whitespace, etc)

This function uses [Digital Preservation's CSV validation tool](https://github.com/digital-preservation/csv-validator) to validate the contents of the CSV file.
The associated format for the schema is detailed [here](http://digital-preservation.github.io/csv-schema/csv-schema-1.2.html).

>**NOTE** This function is completely untested. Handoff occurred before even unit tests were created.
>Since no use cases want unzipped files, there is "no need" to have the files physically unzipped (there might be performance concerns, but we did not have a chance to test these out).
>As such, there is an idea of just peeking to grab the file names in the decompressor, and then in this function stream the ZIP to the single file and then only read in the contents of that file.
>Again, there are a lot of performance considerations that have not been tested with either this approach or the original approach.

## Multithreading
Digital Preservation does not offer multithreading out-of-the-box. As such, this function manually chunks the file and runs multiple threads in parallel.

## No-Schema Implementation
Digital Preservation requires a schema to run any kind of validation on the file.
In order to enforce that each row has the same number of columns, the number of columns must be specified in the schema.
To support use cases that don't provide a schema, we generate a schema on-the-fly.
This is done by reading the header line and counting the columns, then creating the most basic of schemas possible.

## Schema Massaging
Digital Preservation is strict with its schema.
Columns must be in the order specified in the schema, headers must not have trailing or leading whitespace, there can't be any extra columns not specified in the schema, and other limitations.
Some use cases want a more lax standard.
As such, the idea is pass in a "relaxedHeader" configuration boolean to this function, which if enabled the code will massage the schema a little bit before streaming in the file for validation.
>**NOTE** This was implemented in the proof-of-concept for Digital Preservation, but has not yet been implemented here.
