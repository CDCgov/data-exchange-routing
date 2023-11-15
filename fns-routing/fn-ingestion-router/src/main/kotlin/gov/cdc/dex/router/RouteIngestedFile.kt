package gov.cdc.dex.router

import com.azure.storage.blob.BlobServiceClientBuilder
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.microsoft.azure.functions.ExecutionContext
import com.microsoft.azure.functions.annotation.*
import java.net.URI

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
            cardinality = Cardinality.MANY)
        messages: List<String>,
        context:ExecutionContext
    ) {
        context.logger.info("DEX::RouteIngestedFile ${messages.size} messages")

        var cosmosDBClient:CosmosDBClient? = null
        var countProcessed = 0
        try {
            cosmosDBClient =  CosmosDBClient()

            messages.forEach {msg: String ->
                val routeContext = RouteContext(msg, cosmosDBClient, context.logger)
                pipe(routeContext,
                    ::parseMessage,
                    ::getSourceStorageConfig,
                    ::getSourceBlobConfig,
                    ::getDestinationRoutes,
                    ::routeSourceBlobToDestination
                )
                countProcessed +=  if (routeContext.error == null) 1 else 0
            }
            context.logger.info("DEX::RouteIngestedFile $countProcessed of ${messages.size} processed")
        } catch (e: Exception) {
            context.logger.severe(e.message)
        }
        finally {
            cosmosDBClient?.let {CosmosDBClient.close()}
        }
    }
    /* Parses the event message and extracts storage account name
       container name and file name for the source blob
    */
    fun parseMessage(context:RouteContext) {
        with (context) {
            val eventContent = gson.fromJson(message, Array<EventSchema>::class.java).first()

            val uri = URI(eventContent.data.url)
            val host = uri.host
            val path = uri.path.substringAfter("/")
            val containerName = path.substringBefore("/")

            sourceStorageAccount = host.substringBefore(".blob.core.windows.net")
            sourceContainerName = path.substringBefore("/")
            sourceFileName = path.substringAfter("$containerName/")
        }
    }
    /* Retrieves the connection string for the source storage account from CosmosDB
     */
    private fun getSourceStorageConfig(context:RouteContext) {
        with (context ) {
            val config = cosmosDBClient.readStorageConfig(sourceStorageAccount)
            if (config != null) {
                sourceStorageConfig = config
            } else {
                error = "No routing configuration found for $sourceStorageAccount"
                logger.severe(error )
            }
        }
    }
    /* Creates service client for the source blob and retrieves blob's metadata
     */
    fun getSourceBlobConfig(context:RouteContext) {
        with (context ) {
            val sourceServiceClient =
                BlobServiceClientBuilder().connectionString(sourceStorageConfig.connectionString).buildClient()
            val sourceContainerClient = sourceServiceClient.getBlobContainerClient(sourceContainerName)
            sourceBlob = sourceContainerClient.getBlobClient(sourceFileName)

            sourceMetadata = sourceBlob.properties.metadata
            destinationId = sourceMetadata.getOrDefault("meta_destination_id", "?")
            event = sourceMetadata.getOrDefault("meta_ext_event", "?")
        }
    }
    /* Retrieves the destination routes from CosmosDB
       using destinationId and event from the source blob metadata
     */
    private fun getDestinationRoutes(context:RouteContext) {
        with (context) {
            val config = context.cosmosDBClient.readRouteConfig("$destinationId-$event")
            if ( config != null) {
                routingConfig = config
            }
            else {
                error = "No routing configuration found for $destinationId-$event"
                logger.severe(error )
            }
        }
    }
    /*  Creates destination blob for each route using connection string, container and
        folder name from the route configuration.
        Streams the source blob and updates metadata
     */
    fun routeSourceBlobToDestination(context:RouteContext) {
        with (context) {
            val destinationFileName = sourceFileName.split("/").last()
            for( route in routingConfig.routes) {
                val destinationBlobName =
                    "${route.destinationFolder}/${destinationFileName}"
                val destinationServiceClient =
                    BlobServiceClientBuilder().connectionString(route.destinationConnectionString).buildClient()
                val destinationContainerClient =
                    destinationServiceClient.getBlobContainerClient(route.destinationContainer)
                val destinationBlob =
                    destinationContainerClient.getBlobClient(destinationBlobName)

                val sourceBlobInputStream = sourceBlob.openInputStream()
                destinationBlob.upload(sourceBlobInputStream, sourceBlob.properties.blobSize, true)
                sourceBlobInputStream.close()

                sourceMetadata["system_provider"] = "DEX-ROUTING"
                destinationBlob.setMetadata(sourceMetadata)
            }
        }
    }
}



