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
        const val METADATA = "Metadata"

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
            queueName = "%StorageQueueName%",
            connection = "BlobIngestConnectionString")
        msg: String,
        context: ExecutionContext
    ) {
        context.logger.info("$ROUTE_MSG in")

        val start = System.currentTimeMillis()
        try {
            val routeContext = RouteContext(msg, context.logger)
            pipe(
                routeContext,
                ::parseMessage,
                ::validateSourceBlobMeta,
                ::validateDestinationRoutes,
                ::routeSourceBlobToDestination,
                ::sendProcessingStatus
            )
        }
        catch (e: Exception) {
            context.logger.severe("$ROUTE_MSG BLOB ERROR:${e.message}")
            if (e.message?.startsWith(METADATA) == true) {
                throw e
            }
        }
        finally {
            context.logger.info("$ROUTE_MSG out in ${System.currentTimeMillis() - start}ms")
        }
    }

    /* Validates  blob's metadata
     */
    private fun validateSourceBlobMeta(context:RouteContext) =
        with (context ) {
            sourceBlob = sourceSAConfig.containerClient.getBlobClient(sourceFileName)

            val blobProperties =  sourceBlob.properties
            sourceMetadata = blobProperties?.metadata?.mapKeys { it.key.lowercase() }?.toMutableMap() ?:mutableMapOf()
            if (sourceMetadata.isEmpty()) {
                throw Exception("$METADATA is missing or empty")
            }
            lastModifiedUTC = blobProperties?.lastModified.toString()

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

            // get the processing status metadata
            traceId = sourceMetadata.getOrDefault("trace_id", "")
            parentSpanId = sourceMetadata.getOrDefault("parent_span_id", "")
            uploadId = sourceMetadata.getOrDefault("upload_id", UUID.randomUUID().toString())

            startTrace(this)

            if ( dataStreamId.isEmpty() || dataStreamRoute.isEmpty() ) {
                // the file cannot be processed without dataStreamId or dataStreamRoute
                stopProcessing(this, "Missing data_stream_id:${dataStreamId} or data_stream_route:$dataStreamRoute for $sourceUrl")
            }
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
                    stopRouteProcessing(this, "No storage account found for ${route.destination_storage_account} for $sourceUrl")
                }
            }
            if ( config == null) {
                stopProcessing(this, "No routing config found for $dataStreamId-$dataStreamRoute for $sourceUrl")
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
    private fun routeSourceBlobToDestination(context:RouteContext) =
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
                sBusQueueName?.let {
                    val destinationFileName = sourceFileName.split("/").last()
                    val processingStatus = ProcessingSchema(
                        uploadId, dataStreamId, dataStreamRoute,
                        content = SchemaContent.successSchema(
                            sourceBlobUrl = sourceUrl,
                            destinationBlobUrl = "https://${route.destination_storage_account}.blob.core.windows.net/${route.destination_container}/${route.destinationPath}${destinationFileName}",
                        )
                    )
                    sendReport(context, processingStatus)
                }
            }
            stopTrace(this)
        }

    private fun routeDeadLetter(context:RouteContext) =
        with (context) {
            val destinationBlob = sourceSAConfig.deadLetterContainerClient.getBlobClient(sourceFileName)
            val sourceBlobInputStream = sourceBlob.openInputStream()
            destinationBlob.upload(sourceBlobInputStream, sourceBlob.properties.blobSize, true)
            sourceBlobInputStream.close()
            destinationBlob.setMetadata(sourceMetadata)
        }

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

    private fun stopRouteProcessing(context:RouteContext, error:String) {
        with (context) {
            logger.severe("$ROUTE_MSG ERROR: $error")
            routeDeadLetter(context)

            sBusQueueName?.let {
                val processingStatus = ProcessingSchema(
                    uploadId, dataStreamId, dataStreamRoute,
                    content = SchemaContent.errorSchema(
                        sourceBlobUrl = sourceUrl,
                        destinationBlobUrl = "unknown",
                        error = error
                    )
                )
                sendReport(context, processingStatus)
            }
        }
    }

    private fun stopProcessing(context:RouteContext, error:String) {
        logContextError(context, error)
        routeDeadLetter(context)

        with (context) {
            sBusQueueName?.let {
                val processingStatus = ProcessingSchema(
                    uploadId, dataStreamId, dataStreamRoute,
                    content = SchemaContent.errorSchema(
                        sourceBlobUrl = sourceUrl,
                        destinationBlobUrl = "unknown",
                        error = error
                    )
                )
                sendReport(context, processingStatus)
            }
        }
        stopTrace(context)
    }

    private fun sendReport(context:RouteContext, processingStatus:ProcessingSchema) =
        with (context) {
            val msg = gson.toJson(processingStatus)
            sBusClient.sendMessage(ServiceBusMessage(msg))
                .subscribe(
                    {}, { e ->
                        logger.severe(
                            "$ROUTE_MSG ERROR: Sending message to Service Bus: ${e.message}\n" +
                                    "upload_id: ${processingStatus.uploadId}"
                        )
                    }
                )
        }

    private fun getStorageConfig(saAccount:String):StorageAccountConfig? =
        cosmosDBConfig.readStorageAccountConfig(saAccount)

    private fun getRouteConfig(context:RouteContext):RouteConfig? =
        cosmosDBConfig.readRouteConfig("${context.dataStreamId}-${context.dataStreamRoute}")

    private fun logContextError(context:RouteContext, error:String) =
        with (context) {
            errors += error
            logger.severe("$ROUTE_MSG ERROR: $error")
        }
}



