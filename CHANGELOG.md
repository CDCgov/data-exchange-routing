# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).


### [0.0.50] 2024-07-24
	- Updated Processing Report Schema to new consolidated version and sending report to topics
	- Updated docker file to implement multistage to build code + create image
	- Modified cosmos access to Gateway mode to overcome issues with AKS not able to access CosmosDB.

### [0.0.49] 2024-07-10
	- Minor changes to Pom.xml to support deployment of both K8s and serverless
	- Updated tool to register HL7 routes on cosmosdb
	- Ability to extract secrets from KeyVault


### [0.0.48] 2024-06-26
	- Added Dockerfile to routing function

### [0.0.47] 2024-06-12
	- Renamed attributes of SP configuration to spell out their names

### [0.0.46] 2025-05-29
	 - Implemented Service Principal authentication against storage accounts.

### [0.0.44] 2024-05-16
	- Caching cosmosDB config
	- Improved copying of large files
 	- Added dex_ingest_datetime metadata support.

### [0.0.43] - 2024-04-17

	- Created bash scripts to deploy Azure Functions with Zip deploy instead of maven plugin.

### [0.0.42] 2024-04-03
	- Added retries to loading blob with metadata.!


### [0.0.41] - 2024-03-20
	- Switched Routing Fn to read events from Storage queue instead of Event hubs (remove duplication)
	- Added retries to read metadata out of blob files (Azure SDK bug?)
	- Added feature to dead letter files that can't be routed.

### [0.0.40] - 2024-03-06	
	- Added Health check endpoint to routing function
	- Implemented Metadata V2 
	- Fixed a bug to allow files with spaces on their names to be processed
        - Implemented better error handling to not reject the entire batch for a single message with errors.

### [0.0.39] - 2024-02-21
	- Created a tool that configures all routes needed for HL7 pipeline (1 in, 6 out)

### [0.0.38] -2024-02-07
	 - Integrated with Processing status for Reports and Traces
	 - Load tested on TST with 500,000 messages


### [0.0.37] 2024-01-24

	- Refactor cosmos DB Partition

### [0.0.36] - 2024-01-10
	- Finished linking CI/Cd
 	- Implemented configuration on Cosmos DB.
  	- Enhanced Routing to support both Connection String and SAS tokens to authenticate against Storage Account.
   	- Performed code tune up to improve performance - caching configs, etc.
    	= Added ability to configure partitioning of files into subfolders
    

### [0.0.32] - 2023-11-15

	- Routing files to appropriate storage containers based on configuration saved in Cosmos DB
	- Created Terraform Scripts to migrate Routing Azure Resources to its own Resource group (out of HL7 resource group).

### [0.0.31] 2023-11-01

  - Created first draft of Metadata and reviewed internally. (Reviewing with wider audience next sprint)
  - Updated Routing function to use storage connection strings.
  - Updated Terraform and deployed routing Azure resources for TST environment.
  - Requested two Resource Groups to isolate routing resources in DEV and TST
  - Agreement with Upload on how to integrate the two systems:
  	  -  DEX Routing will create a storage account where Upload will copy any routable files into.

### [0.0.30] - 2023-10-18
  - No Code Changes
  - Creating analysis for bringing the spike work into production grade.

### [0.0.29] - 2023-09-20

  - Refactored repository to organize modules into spikes, tools, deprecated
  - Refactored code to abide by DEX standards
  - Added Unit tests
