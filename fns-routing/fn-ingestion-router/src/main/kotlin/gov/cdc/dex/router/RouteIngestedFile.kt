package gov.cdc.dex.router

import com.azure.storage.blob.BlobClientBuilder
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.fuel.httpPut
import com.microsoft.azure.functions.ExecutionContext
import com.microsoft.azure.functions.annotation.*
import java.util.*

class RouteIngestedFile {
    companion object {
        const val ROUTE_MSG = "DEX::Routing:"

        private val apiURL = System.getenv("ProcessingStatusAPIBaseURL")
/*
        private val sBusConnectionString = System.getenv("ServiceBusConnectionString")
        private val sBusQueueName = System.getenv("ServiceBusQueue")
        private val senderClient = ServiceBusClientBuilder()
            .connectionString(sBusConnectionString)
            .sender()
            .queueName(sBusQueueName)
            .buildAsyncClient()

*/
        val cosmosDBConfig = CosmosDBConfig()
        val sourceSAConfig = SourceSAConfig()
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
                    ::validateSourceBlobMeta,
                    ::validateProcessingStatusMeta,
                    ::validateDestinationRoutes,
                    ::routeSourceBlobToDestination,
                    ::sendProcessingStatus
                )
                countProcessed +=  if (routeContext.errors.isEmpty()) 1 else 0
            }
            context.logger.info("$ROUTE_MSG $countProcessed out of ${messages.size} for ${System.currentTimeMillis()-start}ms")
        } catch (e: Exception) {
            context.logger.severe("$ROUTE_MSG BAD ERROR:${e.message}")
        }
    }

    /* Validates  blob's metadata
     */
    fun validateSourceBlobMeta(context:RouteContext) =
        with (context ) {
            sourceBlob = sourceSAConfig.containerClient.getBlobClient(sourceFileName)
            sourceMetadata = sourceBlob.properties.metadata

            destinationId = sourceMetadata.getOrDefault("meta_destination_id", "")
            event = sourceMetadata.getOrDefault("meta_ext_event", "")

            if ( destinationId.isEmpty() || event.isEmpty() ) {
                logContextError(this, "Missing destination_id:${destinationId} or event:$event")
            }
        }

    private fun validateProcessingStatusMeta(context:RouteContext) =
        with (context ) {
            // TODO TODO TODO
            traceId = sourceMetadata.getOrDefault("trace_id", "")
            parentSpanId = sourceMetadata.getOrDefault("parent_span_id", "")
            uploadId = sourceMetadata.getOrDefault("meta_ext_uploadid", "")

            if (traceId.isEmpty() || uploadId.isEmpty()) {
                uploadId = UUID.randomUUID().toString()
                context.logger.info("uploadId:$uploadId")
                getTrace(this)
            }
            sendTrace(this, "start")
        }


    /* Validates the destination routes from CosmosDB
     */
    private fun validateDestinationRoutes(context:RouteContext) {
        with (context) {
            val config = getRouteConfig(this)
            if ( config.routes.isNotEmpty()) {
                routingConfig = config
                config.routes.forEach { route->
                    if ( !route.isValid) return@forEach

                    // get connection string and sas token for this route
                    val cachedAccount = getStorageConfig(this, route.destination_storage_account)
                    if (cachedAccount != null) {
                        route.isValid = true
                        route.sas = cachedAccount.sas
                        route.connectionString = cachedAccount.connection_string
                        route.destinationPath =  if (route.destination_folder == "")
                            "."
                        else
                            foldersToPath(this, route.destination_folder.split("/", "\\"))
                        if (route.destinationPath.isNotEmpty()) {
                            route.destinationPath += "/"
                        }
                    }
                    else {
                        route.isValid = false
                        logger.severe("$ROUTE_MSG ERROR: No storage account found for ${route.destination_storage_account}")
                    }
                }
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

                val destinationBlob = if (route.connectionString.isNotEmpty())
                    BlobClientBuilder()
                        .connectionString(route.connectionString)
                        .containerName(route.destination_container)
                        .blobName("${route.destinationPath}${destinationFileName}")
                        .buildClient()
                else
                    BlobClientBuilder()
                        .endpoint("https://${route.destination_storage_account}.blob.core.windows.net")
                        .sasToken(route.sas)
                        .containerName(route.destination_container)
                        .blobName("${route.destinationPath}${destinationFileName}")
                        .buildClient()

                val sourceBlobInputStream = sourceBlob.openInputStream()
                destinationBlob.upload(sourceBlobInputStream, sourceBlob.properties.blobSize, true)
                sourceBlobInputStream.close()

                sourceMetadata["system_provider"] = "DEX-ROUTING"
                sourceMetadata["trace_id"] = traceId
                sourceMetadata["parent_span_id"] = parentSpanId
                sourceMetadata["meta_ext_uploadid"] = uploadId
                route.metadata?.let {
                    it.entries.forEach {(key,value) ->
                        sourceMetadata[key] =  value
                    }
                }
                destinationBlob.setMetadata(sourceMetadata)
            }
        }
    }
    private fun sendProcessingStatus(context:RouteContext) {
        with (context) {
            routingConfig.routes.forEach {route ->
                if ( !route.isValid) return@forEach
                sendReport(this, route)
                sendTrace(this, "stop")
            }
        }

    }

    private fun logContextError(context:RouteContext, error:String) =
        with (context) {
            errors += error
            logger.severe("$ROUTE_MSG ERROR: $error")
        }

    private fun getStorageConfig(context:RouteContext, saAccount:String):StorageAccountConfig? =
        with (context) {
            var config = storageAccountCache[saAccount]
            if (config == null) {
                config = cosmosDBConfig.readStorageAccountConfig(saAccount)
                if ( config != null) {
                    storageAccountCache[saAccount] = config
                }
            }
            config
        }

    private fun getRouteConfig(context:RouteContext):RouteConfig =
        with (context) {
            val key = "$destinationId-$event"
            var config = routeConfigCache[key]
            if (config == null) {
                config = cosmosDBConfig.readRouteConfig(key)
                if ( config == null) {
                    // use an empty config to prevent
                    // reads for the same key
                    config = RouteConfig()
                    logContextError(this,  "No routing configuration found for $key")
                }
            }
            routeConfigCache[key] = config
            config
        }
    private fun getTrace(context:RouteContext) {
        with (context) {
            val url = "$apiURL/trace?uploadId=$uploadId&destinationId=$destinationId&eventType=$event"
            context.logger.info(url)
            val (_, _, result) = url
                .httpPost()
                .responseString()
            val (payload, _) = result
            context.logger.info("$payload")
            val trace = gson.fromJson(payload, Trace::class.java)
            traceId = trace.traceId
            parentSpanId  = trace.spanId
        }
    }
    private fun sendTrace(context:RouteContext, span:String) {
        with (context) {
            context.logger.info("SENDING $span")
            val url = "$apiURL/trace/addSpan/$traceId/$parentSpanId?stageName=dex-routing&spanMark=$span"
            context.logger.info(url)
            val (_, _, result) = url
                .httpPut()
                .responseString()
            val (payload, _) = result
            context.logger.info(payload)
        }
    }

    private fun sendReport(context:RouteContext, route:Destination) {
        with (context) {
            val destinationFileName = sourceFileName.split("/").last()
            val message = SchemaContent(
                fileSourceBlobUrl = sourceUrl,
                fileDestinationBlobUrl = "https://${route.destination_storage_account}.blob.core.windows.net/${route.destination_container}/${route.destinationPath}${destinationFileName}",
                result = "success"
            )
            val msg = gson.toJson(message)
            context.logger.info("SENDING REPORT")
            val url = "$apiURL/report/json/uploadId/$uploadId?stageName=dex-routing&destinationId=$destinationId&eventType=$event"
            val (_, _, result) = url
                .httpPost()
                .body(msg)
                .responseString()
            val (payload, _) = result
            context.logger.info(payload)
        }
    }
}



