package gov.cdc.dex.router

import com.azure.core.amqp.AmqpTransportType
import com.azure.messaging.servicebus.ServiceBusClientBuilder
import com.azure.messaging.servicebus.ServiceBusMessage
import com.azure.storage.blob.BlobClientBuilder
import com.github.kittinunf.fuel.httpPut
import com.microsoft.azure.functions.ExecutionContext
import com.microsoft.azure.functions.annotation.*
import java.util.*

class RouteIngestedFile {
    companion object {
        const val ROUTE_MSG = "DEX::Routing:"

        private val apiURL = System.getenv("ProcessingStatusAPIBaseURL")
        private val sBusConnectionString = System.getenv("ServiceBusConnectionString")
        private val sBusQueueName = System.getenv("ServiceBusQueue")
        private val sBusClient by lazy {
            ServiceBusClientBuilder()
                .connectionString(sBusConnectionString)
                .transportType(AmqpTransportType.AMQP_WEB_SOCKETS)
                .sender()
                .queueName(sBusQueueName)
                .buildAsyncClient()
        }

        val cosmosDBConfig = CosmosDBConfig()
        val sourceSAConfig = SourceSAConfig()
    }

    @FunctionName("RouteIngestedFile")
       fun run (
        @QueueTrigger(
            name = "message",
            queueName = "file-drop",
            connection = "BlobIngestConnectionString")
        msg: String,
        context: ExecutionContext
    ) {
        context.logger.info("$ROUTE_MSG in")

        val start = System.currentTimeMillis()

        // read caches
        //val routeConfigCache = mutableMapOf<String, RouteConfig>()
        //val storageAccountCache = mutableMapOf<String, StorageAccountConfig>()
        try {
            val routeContext = RouteContext(msg, context.logger)
            pipe(
                routeContext,
                ::parseMessage,
                ::validateSourceBlobMeta,
                ::validateProcessingStatusMeta,
                ::validateDestinationRoutes,
                ::routeSourceBlobToDestination,
                ::sendProcessingStatus
            )
        }
        catch (e: Exception) {
            context.logger.severe("$ROUTE_MSG BLOB ERROR:${e.message}")
        }
        finally {
            context.logger.info("$ROUTE_MSG out in ${System.currentTimeMillis() - start}ms")
        }
    }

    /* Validates  blob's metadata
     */
    fun validateSourceBlobMeta(context:RouteContext) =
        with (context ) {
            sourceBlob = sourceSAConfig.containerClient.getBlobClient(sourceFileName)
            sourceMetadata = sourceBlob.properties.metadata
            lastModifiedUTC = sourceBlob.properties.lastModified.toString()

            val routeMeta = with(sourceMetadata) {
                Pair(
                    getOrDefault("data_stream_id",
                    getOrDefault("meta_destination_id", ""))
                    ,
                    getOrDefault("data_stream_route",
                    getOrDefault("meta_ext_event","" ))
                )
            }
            dataStreamId = routeMeta.first
            dataStreamRoute = routeMeta.second

            if ( dataStreamId.isEmpty() || dataStreamRoute.isEmpty() ) {
                logContextError(this, "Missing data_stream_id:${dataStreamId} or data_stream_route:$dataStreamRoute")
            }
        }

    private fun validateProcessingStatusMeta(context:RouteContext) =
        with(context) {
            traceId = sourceMetadata.getOrDefault("trace_id", "")
            parentSpanId = sourceMetadata.getOrDefault("parent_span_id", "")
            uploadId = sourceMetadata.getOrDefault("upload_id", UUID.randomUUID().toString())

            startTrace(this)
        }


    /* Validates the destination routes from CosmosDB
     */
    private fun validateDestinationRoutes(context:RouteContext) {
        with (context) {
            val config  =  getRouteConfig(this)
            config?.routes?.forEach { route->
                // get connection string and sas token for this route
                val cachedAccount = getStorageConfig(route.destination_storage_account)
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
                } else {
                    route.isValid = false
                    logger.severe("$ROUTE_MSG ERROR: No storage account found for ${route.destination_storage_account}")
                }
            }
            if ( config == null) {
                logger.severe("$ROUTE_MSG ERROR: No routing config found for $dataStreamId-$dataStreamRoute")
            }
            else {
                routingConfig = config
            }
        }
    }

    /*  Creates destination blob for each valid route using container and
        folder name from the route configuration.
        Streams the source blob and updates metadata
     */
    fun routeSourceBlobToDestination(context:RouteContext) =
        with (context) {
            val destinationFileName = sourceFileName.split("/").last()
            routingConfig?.routes?.forEach {route ->
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
                sourceMetadata["upload_id"] = uploadId
                sourceMetadata["data_stream_id"] = dataStreamId
                sourceMetadata["data_stream_route"] = dataStreamRoute
                sourceMetadata["upload_id"] = uploadId
                if (isChildSpanInitialized) {
                    sourceMetadata["parent_span_id"] = childSpanId
                }
                route.metadata?.let {
                    it.entries.forEach {(key,value) ->
                        sourceMetadata[key] =  value
                    }
                }
                destinationBlob.setMetadata(sourceMetadata)
            }
        }

    private fun sendProcessingStatus(context:RouteContext) =
        with(context) {
            routingConfig?.routes?.forEach { route ->
                if (!route.isValid) return@forEach
                sendReport(this, route)
                stopTrace(this)
            }
        }

    private fun getStorageConfig(saAccount:String):StorageAccountConfig? =
        cosmosDBConfig.readStorageAccountConfig(saAccount)

    private fun getRouteConfig(context:RouteContext):RouteConfig? =
        cosmosDBConfig.readRouteConfig("${context.dataStreamId}-${context.dataStreamRoute}")

    private fun startTrace(context:RouteContext) =
        if (apiURL != null ) {
            with(context) {
                childSpanId = if (traceId.isNotEmpty() && parentSpanId.isNotEmpty()) {
                    val url = "$apiURL/trace/startSpan/$traceId/$parentSpanId?stageName=dex-routing"
                    val (_, _, result) = url
                        .httpPut()
                        .responseString()
                    val (payload, _) = result
                    val trace = gson.fromJson(payload, Trace::class.java)
                    trace.spanId
                } else {
                    ""
                }
            }
        }
        else {}

    private fun stopTrace(context:RouteContext) =
        if (apiURL != null ) {
            with (context) {
                if (traceId.isNotEmpty() && isChildSpanInitialized) {
                    val url = "$apiURL/trace/stopSpan/$traceId/$childSpanId"
                    url.httpPut().responseString()
                }
           }
        }
        else {}

    private fun sendReport(context:RouteContext, route:Destination) =
        if (sBusQueueName != null) {
            with (context) {
                val destinationFileName = sourceFileName.split("/").last()
                val processingStatus = ProcessingSchema(uploadId, dataStreamId, dataStreamRoute,
                    content = SchemaContent(
                        fileSourceBlobUrl = sourceUrl,
                        fileDestinationBlobUrl = "https://${route.destination_storage_account}.blob.core.windows.net/${route.destination_container}/${route.destinationPath}${destinationFileName}",
                        result = "success"
                    ))
                val msg = gson.toJson(processingStatus)
                sBusClient.sendMessage(ServiceBusMessage(msg))
                    .subscribe(
                        {}, { e -> logger.severe("$ROUTE_MSG ERROR: Sending message to Service Bus: ${e.message}\n" +
                                "upload_id: ${processingStatus.uploadId}") }
                    )
                Unit
            }
        }
        else {}

    private fun logContextError(context:RouteContext, error:String) =
        with (context) {
            errors += error
            logger.severe("$ROUTE_MSG ERROR: $error for ${context.sourceUrl}")
        }
}



