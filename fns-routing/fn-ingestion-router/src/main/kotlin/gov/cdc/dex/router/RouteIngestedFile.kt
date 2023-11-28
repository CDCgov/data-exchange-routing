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
        lateinit var storageAccountCache : Map<String, StorageConfig>
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
        context.logger.info("DEX::RouteIngestedFile --> ${messages.size} messages")

        var cosmosDBClient:CosmosDBClient? = null
        var countProcessed = 0
        try {
            cosmosDBClient =  CosmosDBClient()

            // cache the storage accounts
            storageAccountCache = cosmosDBClient.readStorageConfig()
            if ( storageAccountCache.isEmpty() ) {
                context.logger.severe("DEX::RouteIngested --> File No storage accounts configured")
                return
            }
            storageAccountCache.forEach { (key, value)->
                context.logger.info("DEX:::::::$key, ${value.storage_account}")
            }

            messages.forEach {msg: String ->
                val routeContext = RouteContext(msg, cosmosDBClient, context.logger)
                pipe(routeContext,
                    ::parseMessage,
                    ::getSourceStorageConfig,
                    ::getSourceBlobConfig,
                    ::getDestinationRoutes,
                    ::routeSourceBlobToDestination
                )
                countProcessed +=  if (routeContext.errors.isEmpty()) 1 else 0
            }
            context.logger.info("DEX::RouteIngestedFile --> $countProcessed of ${messages.size} processed")
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
            val config =  storageAccountCache[sourceStorageAccount]
            if ( config == null) {
                logError(this,  "No routing configuration found for $sourceStorageAccount")
            }
            else {
                sourceStorageConfig = config
            }
        }
    }

    /* Creates service client for the source blob and retrieves blob's metadata
     */
    fun getSourceBlobConfig(context:RouteContext) {
        with (context ) {
            val sourceServiceClient =
                BlobServiceClientBuilder().connectionString(sourceStorageConfig.connection_string).buildClient()
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
            val config = cosmosDBClient.readRouteConfig("$destinationId-$event")
            if ( config != null ) {
                routingConfig = config
                // check routes for valid storage account
                for (route in routingConfig.routes) {
                    val cachedAccount = storageAccountCache[route.destination_storage_account]
                    if (cachedAccount != null) {
                        route.destination_connection_string = cachedAccount.connection_string
                    } else {
                        logError(this, "No storage connection string found for ${route.destination_storage_account}")
                    }
                }
            }
            else {
                logError(this,  "No routing configuration found for $destinationId-$event")
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
                val destinationServiceClient =
                    BlobServiceClientBuilder().connectionString(route.destination_connection_string).buildClient()

                val destinationContainerClient =
                    destinationServiceClient.getBlobContainerClient(route.destination_container)

                val destinationBlobName = "${route.destination_folder}/${destinationFileName}"
                val destinationBlob =
                    destinationContainerClient.getBlobClient(destinationBlobName)

                val sourceBlobInputStream = sourceBlob.openInputStream()
                destinationBlob.upload(sourceBlobInputStream, sourceBlob.properties.blobSize, true)
                sourceBlobInputStream.close()

                sourceMetadata["system_provider"] = "DEX-ROUTING"
                route.metadata?.let {
                    it.entries.forEach {(key,value) ->
                        sourceMetadata[key] =  value
                    }
                }
                destinationBlob.setMetadata(sourceMetadata)
            }
        }
    }
    private fun logError(context:RouteContext, error:String) {
        with (context) {
            errors += error
            logger.severe(error)
        }
    }
}



