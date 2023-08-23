Details of the proof-of-concept architecture created in an Azure instance outside of CDC

# Blob Storage
A basic storage account with blob storage containers.
In the production pipeline this would be integrated with routing and not be its own storage account.
Config details in [storage.json](storage.json)

# Event Hub
A hub to handle events, both for kicking off the pipeline and for reporting.
Config details for the namespace in [eventhub.json](eventhub.json)

## Ingest 
Event subscription was created for the blob storage such that when a blob is created in "ingest" container, it triggers an event.
Config details in [eventhub-ingest.json](eventhub-ingest.json)

## Reporting 
For each action the orchestrator performs, it sends an event to be reported.
Config details in [eventhub-reporting.json](eventhub-reporting.json)

# Function App
With the current Durable Function design, all of the functions must be in the same function app.
One acception to this is the reporting function, but this was not fully developed yet at time of handoff.
Config details in [function-app.json](function-app.json)

## Configuration > Application Settings
The following are required to be set within the function app:
* BaseConfigUrl - URL to the blob container containing the orchestrator config files
* BlobConnection - connection string for the blob container containing the ingested file
* EventHubConnection - connection string for the event hub
* EventHubName_Ingest - name of the ingest hub (in our case, "dex-csv-ingest-eventhub")
* EventHubName_ReportingEvent - name of the reporting hub (in our case, "dex-csv-reporting-events")
