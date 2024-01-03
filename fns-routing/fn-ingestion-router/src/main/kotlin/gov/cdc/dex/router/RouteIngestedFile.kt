package gov.cdc.dex.router

import com.azure.storage.blob.BlobClientBuilder
import com.microsoft.azure.functions.ExecutionContext
import com.microsoft.azure.functions.annotation.*

class RouteIngestedFile {
    companion object {
        const val ROUTE_MSG = "DEX::Routing:"

        val cosmosClient = CosmosDBClient()
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
        context.logger.info("$ROUTE_MSG ${messages.size} in")

        val start = System.currentTimeMillis()

        var countProcessed = 0

        // read caches
        val routeConfigCache = mutableMapOf<String, RouteConfig>()
        val storageAccountCache = mutableMapOf<String, StorageAccountConfig>()

        try {
            messages.forEach {msg ->
                val routeContext = RouteContext(msg, routeConfigCache, storageAccountCache, context.logger)
                pipe(routeContext,
                    ::parseMessage,
                    ::validateSourceStorageConfig,
                    ::validateSourceBlobMeta,
                    ::validateDestinationRoutes,
                    ::routeSourceBlobToDestination
                )
                countProcessed +=  if (routeContext.errors.isEmpty()) 1 else 0
            }
            context.logger.info("$ROUTE_MSG $countProcessed out of ${messages.size} for ${System.currentTimeMillis()-start}ms")
        } catch (e: Exception) {
            context.logger.severe("$ROUTE_MSG ERROR:${e.message}")
        }
    }

    /* Retrieves the source storage account from cache or CosmosDB
     */
    private fun validateSourceStorageConfig(context:RouteContext) {
        val config =  getStorageConfig(context, context.sourceStorageAccount)
        if ( config != null) {
            context.sourceStorageConfig = config
        }
        else {
            logContextError(context,  "No storage account configuration found for ${context.sourceStorageAccount}")
        }
    }

    /* Validates  blob's metadata
     */
    fun validateSourceBlobMeta(context:RouteContext) {
        with (context ) {
            val config =  storageAccountCache[sourceStorageAccount]
            sourceBlob = BlobClientBuilder()
                .endpoint("https://${sourceStorageAccount}.blob.core.windows.net")
                .sasToken(config?.sas)
                .containerName(sourceContainerName)
                .blobName(sourceFileName)
                .buildClient()

            sourceMetadata = sourceBlob.properties.metadata

            destinationId = sourceMetadata.getOrDefault("meta_destination_id", "")
            event = sourceMetadata.getOrDefault("meta_ext_event", "")
            if ( destinationId.isEmpty() || event.isEmpty() ) {
                logContextError(this, "Missing destination_id:${destinationId} or event:$event")
            }
        }
    }

    /* Validates the destination routes from CosmosDB
     */
    private fun validateDestinationRoutes(context:RouteContext) {
        with (context) {
            val config = getRouteConfig(this)
            if ( config != null ) {
                routingConfig = config
                config.routes.forEach { route->
                    if ( !route.isValid) return@forEach
                    // get sas token for this route
                    val cachedAccount = getStorageConfig(this, route.destination_storage_account)
                    if (cachedAccount != null) {
                        route.isValid = true
                        route.sas = cachedAccount.sas
                        route.destinationPath =  if (route.destination_folder == "")
                            "."
                        else
                            foldersToPath(this, route.destination_folder.split("/", "\\"))
                    }
                    else {
                        route.isValid = false
                        logger.severe("$ROUTE_MSG ERROR: No storage account found for ${route.destination_storage_account}")
                    }
                }
            }
            else {
                logContextError(this,  "No routing configuration found for $destinationId-$event")
            }
        }
    }

    /*  Creates destination blob for each valid route using container and
        folder name from the route configuration.
        Streams the source blob and updates metadata
     */
    fun routeSourceBlobToDestination(context:RouteContext) {
        with (context) {
            val destinationFileName = sourceFileName.split("/").last()
            routingConfig.routes.forEach {route ->
                if ( !route.isValid) return@forEach

                val destinationBlob = BlobClientBuilder()
                    .endpoint("https://${route.destination_storage_account}.blob.core.windows.net")
                    .sasToken(route.sas)
                    .containerName(route.destination_container)
                    .blobName("${route.destinationPath}/${destinationFileName}")
                    .buildClient()

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

    private fun logContextError(context:RouteContext, error:String) {
        with (context) {
            errors += error
            logger.severe("$ROUTE_MSG ERROR: $error")
        }
    }

    private fun getStorageConfig(context:RouteContext, saAccount:String):StorageAccountConfig? {
        return with (context) {
            var config = storageAccountCache[saAccount]
            if (config == null) {
                config = cosmosClient.readStorageAccountConfig(saAccount)
                if ( config != null) {
                    storageAccountCache[saAccount] = config
                }
            }
            config
        }
    }

    private fun getRouteConfig(context:RouteContext):RouteConfig? {
        return with (context) {
            val key = "$destinationId-$event"
            var config = routeConfigCache[key]
            if (config == null) {
                config = cosmosClient.readRouteConfig(key)
                if (config != null) {
                    routeConfigCache[key] = config
                }
            }
            config
        }
    }
}



