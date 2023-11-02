package gov.cdc.dex.router

import com.azure.identity.DefaultAzureCredentialBuilder
import com.azure.storage.blob.BlobServiceClientBuilder
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.microsoft.azure.functions.ExecutionContext
import com.microsoft.azure.functions.annotation.*


class RouteIngestedFile {
    companion object {
        val gson: Gson = GsonBuilder().serializeNulls().create()
    }

    @FunctionName("RouteIngestedFile")
    fun run(
            @EventHubTrigger(name = "message",
                    eventHubName = "%AzureEventHubName%",
                    consumerGroup = "%AzureEventHubConsumerGroup%",
                    connection = "AzureEventHubConnectionString",
                    cardinality = Cardinality.ONE) message: String,
            context:ExecutionContext
    ) {
        var cosmosDBClient:CosmosDBClient? = null
        try {
            context.logger.info("DEX:::RouteIngestedFile function triggered ")
            cosmosDBClient =  CosmosDBClient()

            // parse out source url
            val eventContent = gson.fromJson(message, Array<EventSchema>::class.java).first()
            val sourceUrl = eventContent.data.url
            val blob = parseBlobURI(eventContent.data.url)
            context.logger.info("FileName:: ${blob.fileName}")

            // create service client for the source blob
            val endpoint = "https://${blob.host}"
            val azureCredential = DefaultAzureCredentialBuilder().build()
            val sourceServiceClient =
                    BlobServiceClientBuilder().endpoint(endpoint).credential(azureCredential).buildClient()
            val sourceContainerClient = sourceServiceClient.getBlobContainerClient(blob.containerName)
            val sourceBlob = sourceContainerClient.getBlobClient(blob.fileName)
            context.logger.info("Source Blob URL:: " + sourceBlob.blobUrl)

            // extract destination id and event from metadata
            // retrieve routing configuration
            val sourceMetadata = sourceBlob.properties.metadata
            val destinationId = sourceMetadata.getOrDefault("meta_destination_id", "?")
            val event = sourceMetadata.getOrDefault("meta_ext_event", "?")
            val routingConfig = cosmosDBClient.readRouteConfig("$destinationId-$event")
            if ( routingConfig == null) {
                context.logger.severe("No routing configuration found for $destinationId-$event")
                return
            }

            val destinationFileName = blob.fileName.split("/").last()
            for( route in routingConfig.routes) {
                val destinationBlobName =
                        "${route.destinationFolder}/${destinationFileName}"
                val destinationEndpoint =
                        "https://${route.destinationStorageAccount}.blob.core.windows.net"
                val destinationServiceClient =
                        BlobServiceClientBuilder().endpoint(destinationEndpoint).credential(azureCredential).buildClient()
                val destinationContainerClient =
                        destinationServiceClient.getBlobContainerClient(route.destinationContainer)
                val destinationBlob =
                        destinationContainerClient.getBlobClient(destinationBlobName)
                context.logger.info("Destination Blob URL: " + destinationBlob.blobUrl)

                // stream the blob and upload it to the destination, then close the stream
                val sourceBlobInputStream = sourceBlob.openInputStream()
                destinationBlob.upload(sourceBlobInputStream, sourceBlob.properties.blobSize, true)
                sourceBlobInputStream.close()

                //update  metadata
                sourceMetadata["system_provider"] = "DEX-ROUTING"
                destinationBlob.setMetadata(sourceMetadata)
                context.logger.info("Blob $sourceUrl has been routed to $destinationBlobName")
            }
        } catch (e: Exception) {
            context.logger.severe(e.message)
        }
        finally {
            cosmosDBClient?.let {CosmosDBClient.close()}
        }
    }
}
