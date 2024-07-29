package gov.cdc.dex.router

import com.azure.core.amqp.AmqpTransportType
import com.azure.identity.ClientSecretCredentialBuilder
import com.azure.identity.ManagedIdentityCredentialBuilder
import com.azure.messaging.servicebus.ServiceBusClientBuilder
import com.azure.messaging.servicebus.ServiceBusMessage
import com.azure.security.keyvault.secrets.SecretClient
import com.azure.security.keyvault.secrets.SecretClientBuilder
import com.azure.storage.blob.BlobClient
import com.azure.storage.blob.BlobClientBuilder
import com.azure.storage.blob.models.BlobRange
import com.azure.storage.blob.models.BlobRequestConditions
import com.azure.storage.blob.specialized.BlockBlobClient
import com.microsoft.azure.functions.ExecutionContext
import com.microsoft.azure.functions.annotation.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.produce
import java.io.ByteArrayInputStream
import java.util.*
import java.util.concurrent.atomic.AtomicLong

class RouteIngestedFile {
    companion object {
        const val ROUTE_MSG = "DEX::Routing:"

        // processing status
        private val sBusConnectionString = System.getenv("ServiceBusConnectionString")
        private val sBusQueueName = System.getenv("ServiceBusQueue")
        private const val sbBusTopicName = "processing-status-cosmos-db-report-sink-topics"
        private val sBusClient by lazy {
            ServiceBusClientBuilder()
                .connectionString(sBusConnectionString)
                .transportType(AmqpTransportType.AMQP_WEB_SOCKETS)
                .sender()
                .topicName(sbBusTopicName)
                .buildAsyncClient()
        }

        // cosmos db config
        private val cosmosDBConfig = CosmosDBConfig()

        // source storage account config
        private val sourceSAConfig = SourceSAConfig()

        // read cache for cosmos db configurations
        val cache = ConfigCache(
            (System.getenv("CosmosDBCacheExpireHr")?:"24").toLong()*60*60*1000
        )

        // settings for big blobs copy, blob is considered big, if above this size
        val bigBlobSize = (System.getenv("BigBlobSizeMiB")?:"50").toInt()*1024*1024
        val blobChunkSize = (System.getenv("BlobChunkSizeMiB")?:"2").toLong()*1024*1024

        // key vault
        private val vaultUrl = System.getenv("KeyVaultUrl")?:""
        private val  managedIdentityClientId = System.getenv("ManagedIdentityClientId")?:""
        private val vSecretClient: SecretClient? =
            if ( vaultUrl.isNotEmpty() && managedIdentityClientId.isNotEmpty()) {
                SecretClientBuilder()
                    .vaultUrl(vaultUrl)
                    .credential(
                        ManagedIdentityCredentialBuilder()
                            .clientId(managedIdentityClientId)
                            .build()
                    )
                    .buildClient()
            }
            else {
                null
            }
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
        cache.clearIfExpired(start)

        context.logger.info("$ROUTE_MSG BIG BLOB SIZE: ${bigBlobSize}")
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
            throw e
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
                throw Exception("Metadata is missing or empty")
            }
            blobSize = blobProperties?.blobSize ?:0L

            val dexIngestDateTime = sourceMetadata["dex_ingest_datetime"]
            creationTimeUTC = dexIngestDateTime?:blobProperties?.creationTime?.toString() ?: ""

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

            if ( dataStreamId.isEmpty() || dataStreamRoute.isEmpty() ) {
                // the file cannot be processed without dataStreamId or dataStreamRoute
                stopProcessing(this, "Missing data_stream_id:${dataStreamId} or data_stream_route:$dataStreamRoute for $sourceUrl")
            }
        }

    /* Validates the destination routes from CosmosDB
     */
    private fun validateDestinationRoutes(context:RouteContext) {
        with (context) {
            val config  =  getRouteConfig( dataStreamId, dataStreamRoute)
            config?.routes?.forEach { route->
                // get connection string and sas token for this route
                val cachedAccount = getStorageConfig(route.destinationStorageAccount)
                if (cachedAccount != null) {
                    route.isValid = true
                    route.sas = cachedAccount.sas
                    route.connectionString = cachedAccount.connectionString
                    route.tenantId = cachedAccount.tenantId
                    route.clientId = cachedAccount.clientId
                    route.secret = cachedAccount.secret

                    route.destinationPath =  if (route.destinationFolder == "")
                        "."
                    else
                        foldersToPath(this, route.destinationFolder.split("/", "\\"))

                    if (route.destinationPath.isNotEmpty()) {
                        route.destinationPath += "/"
                    }
                } else {
                    route.isValid = false
                    stopRouteProcessing(this, "No storage account found for ${route.destinationStorageAccount} for $sourceUrl")
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

                sourceMetadata["system_provider"] = "DEX-ROUTING"
                sourceMetadata["upload_id"] = uploadId
                sourceMetadata["data_stream_id"] = dataStreamId
                sourceMetadata["data_stream_route"] = dataStreamRoute
                sourceMetadata["upload_id"] = uploadId
                sourceMetadata["dex_ingest_datetime"] = creationTimeUTC
                if (isChildSpanInitialized) {
                    sourceMetadata["parent_span_id"] = childSpanId
                }
                route.metadata?.let {
                    it.entries.forEach {(key,value) ->
                        sourceMetadata[key] =  value
                    }
                }

                val destinationBlob = if (
                    route.tenantId.isNotEmpty() &&
                    route.clientId.isNotEmpty() &&
                    route.secret.isNotEmpty()) {

                    BlobClientBuilder()
                        .endpoint("https://${route.destinationStorageAccount}.blob.core.windows.net")
                        .credential(
                            ClientSecretCredentialBuilder()
                                .tenantId(route.tenantId)
                                .clientId(route.clientId)
                                .clientSecret(route.secret)
                                .build()
                        )
                        .containerName(route.destinationContainer)
                        .blobName("${route.destinationPath}${destinationFileName}")
                        .buildClient()
                }
                else if (route.connectionString.isNotEmpty())
                    BlobClientBuilder()
                        .connectionString(route.connectionString)
                        .containerName(route.destinationContainer)
                        .blobName("${route.destinationPath}${destinationFileName}")
                        .buildClient()
                else  if (route.sas.isNotEmpty()) {
                    BlobClientBuilder()
                        .endpoint("https://${route.destinationStorageAccount}.blob.core.windows.net")
                        .sasToken(route.sas)
                        .containerName(route.destinationContainer)
                        .blobName("${route.destinationPath}${destinationFileName}")
                        .buildClient()
                }
                else {
                    throw Exception("Misconfigured storage-account")
                }

                logger.info("BLOB SIZE:$blobSize) VS BIG BLOB SIZE: ${bigBlobSize}")

                if (blobSize <= bigBlobSize) {
                    sourceBlob.openInputStream().use { stream->
                        destinationBlob.upload(stream, true)
                        destinationBlob.setMetadata(sourceMetadata)
                    }
                }
                else {
                    val destinationBlockBlob = destinationBlob.blockBlobClient
                    destinationBlockBlob.deleteIfExists()

                    val start = System.currentTimeMillis()
                    runBlocking {
                        launch {
                            copyBlob( context.logger, sourceBlob, blobSize, destinationBlockBlob, sourceMetadata)
                        }
                    }
                    logger.info("copied in ${System.currentTimeMillis() - start}ms")
                }
            }
        }

    private fun sendProcessingStatus(context:RouteContext) =
        with(context) {
            routingConfig?.routes?.forEach { route ->
                if (!route.isValid) return@forEach
                sBusQueueName?.let {
                    val destinationFileName = sourceFileName.split("/").last()
                    val userId = sourceMetadata["user_id"]
                    val senderId = sourceMetadata.getOrDefault("sender_id", "dex-routing")
                    val dataProducer = sourceMetadata["data_producer_id"]
                    val stageInfo = StageInfo(
                        status = StageStatus.SUCCESS,
                        issues = null,
                        startProcessingTime = creationTimeUTC,
                        endProcessingTime = creationTimeUTC
                    )
                    val blobCopyReport = BlobFileCopy(
                        srcUrl = sourceUrl,
                        destUrl = "https://${route.destinationStorageAccount}.blob.core.windows.net/${route.destinationContainer}/${route.destinationPath}${destinationFileName}",
                        timestamp = creationTimeUTC
                    )
                    val psReportEnvelope = PSReportEnvelope(
                        uploadId = uploadId,
                        userId = userId,
                        dataStreamId = dataStreamId,
                        dataStreamRoute = dataStreamRoute,
                        jurisdiction = null,
                        senderId = senderId,
                        dataProducerId = dataProducer,
                        dexIngestTimestamp = creationTimeUTC,
                        messageMetadata = null,
                        stageInfo = stageInfo,
                        content = blobCopyReport
                    )
                    sendReport(context, psReportEnvelope)
                }
            }
        }

    private fun routeDeadLetter(context:RouteContext) =
        with (context) {
            val destinationBlob = sourceSAConfig.deadLetterContainerClient.getBlobClient(sourceFileName)
            if (blobSize <= bigBlobSize) {
                sourceBlob.openInputStream().use { stream->
                    destinationBlob.upload(stream, true)
                    destinationBlob.setMetadata(sourceMetadata)
                }
            }
            else {
                val destinationBlockBlob = destinationBlob.blockBlobClient
                destinationBlockBlob.deleteIfExists()

                val start = System.currentTimeMillis()
                runBlocking {
                    launch {
                        copyBlob( context.logger, sourceBlob, blobSize, destinationBlockBlob, sourceMetadata)
                    }
                }
                logger.info("copied in ${System.currentTimeMillis() - start}ms")
            }
        }

    private fun stopRouteProcessing(context:RouteContext, error:String) {
        with (context) {
            logger.severe("$ROUTE_MSG ERROR: $error")
            routeDeadLetter(context)

            sBusQueueName?.let {
                val userId = sourceMetadata["user_id"]
                val senderId = sourceMetadata.getOrDefault("sender_id", "dex-routing")
                val dataProducer = sourceMetadata["data_producer_id"]
                val issues = listOf(Issue(IssueLevel.ERROR, error))
                val stageInfo = StageInfo(
                    status = StageStatus.FAILURE,
                    issues = issues,
                    startProcessingTime = creationTimeUTC,
                    endProcessingTime = creationTimeUTC
                )
                val blobCopyReport = BlobFileCopy(
                    srcUrl = sourceUrl,
                    destUrl = null,
                    timestamp = creationTimeUTC
                )
                val psReportEnvelope = PSReportEnvelope(
                    uploadId = uploadId,
                    userId = userId,
                    dataStreamId = dataStreamId,
                    dataStreamRoute = dataStreamRoute,
                    jurisdiction = null,
                    senderId = senderId,
                    dataProducerId = dataProducer,
                    dexIngestTimestamp = creationTimeUTC,
                    messageMetadata = null,
                    stageInfo = stageInfo,
                    content = blobCopyReport
                )
                sendReport(context, psReportEnvelope)
            }
        }
    }

    private fun stopProcessing(context:RouteContext, error:String) {
        logContextError(context, error)
        routeDeadLetter(context)

        with (context) {
            sBusQueueName?.let {
                val userId = sourceMetadata["user_id"]
                val senderId = sourceMetadata.getOrDefault("sender_id", "dex-routing")
                val dataProducer = sourceMetadata["data_producer_id"]
                val issues = listOf(Issue(IssueLevel.ERROR, error))
                val stageInfo = StageInfo(
                    status = StageStatus.FAILURE,
                    issues = issues,
                    startProcessingTime = creationTimeUTC,
                    endProcessingTime = creationTimeUTC
                )
                val blobCopyReport = BlobFileCopy(
                    srcUrl = sourceUrl,
                    destUrl = null,
                    timestamp = creationTimeUTC
                )
                val psReportEnvelope = PSReportEnvelope(
                    uploadId = uploadId,
                    userId = userId,
                    dataStreamId = dataStreamId,
                    dataStreamRoute = dataStreamRoute,
                    jurisdiction = null,
                    senderId = senderId,
                    dataProducerId = dataProducer,
                    dexIngestTimestamp = creationTimeUTC,
                    messageMetadata = null,
                    stageInfo = stageInfo,
                    content = blobCopyReport
                )
                sendReport(context, psReportEnvelope)
            }
        }
    }

    private fun sendReport(context:RouteContext, psReportEnvelope:PSReportEnvelope) =
        with (context) {
            val msg = gson.toJson(psReportEnvelope)
            sBusClient.sendMessage(ServiceBusMessage(msg))
                .subscribe(
                    {
                        logger.info(
                            "$ROUTE_MSG SUCCESS: Message sent to Service Bus successfully.\n" +
                                    "upload_id: ${psReportEnvelope.uploadId}\n" +
                                    "ps report: $msg"
                        )
                    },
                    { e ->
                        logger.severe(
                            "$ROUTE_MSG ERROR: Sending message to Service Bus: ${e.message}\n" +
                                    "upload_id: ${psReportEnvelope.uploadId}"
                        )
                    }
                )
        }

    private fun getStorageConfig(saAccount:String):StorageAccountConfig? {
        var config = cache.storageCache[saAccount]
        if (config == null ||
            ((config.tenantId.isEmpty() ||
                    config.clientId.isEmpty() ||
                    config.secret.isEmpty()) &&
                    config.connectionString.isEmpty() &&
                    config.sas.isEmpty())) {

            config = if (vSecretClient != null) {
                gson.fromJson(vSecretClient.getSecret(saAccount).value, StorageAccountConfig::class.java)
            }
            else {
                cosmosDBConfig.readStorageAccountConfig(saAccount)
            }
            if ( config != null) {
                cache.storageCache[saAccount] = config
            }
        }
        return config
    }

    private fun getRouteConfig(dataStreamId:String, dataStreamRoute:String):RouteConfig?  {
        val key = "${dataStreamId}-${dataStreamRoute}"
        var config = cache.routesCache[key]
        if (config == null) {
            config = cosmosDBConfig.readRouteConfig(key)
            if (config != null) {
                cache.routesCache[key] = config
            }
        }
        return config
    }

    private fun logContextError(context:RouteContext, error:String) =
        with (context) {
            errors += error
            logger.severe("$ROUTE_MSG ERROR: $error")
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun copyBlob(logger:java.util.logging.Logger, sourceBlob: BlobClient, blobSize: Long, destinationBlob: BlockBlobClient, metadata:MutableMap<String,String>) {
        logger.info("IN COPY BLOB")
        withContext(Dispatchers.IO) {
            val totalBytesRead = AtomicLong(0L)
            val totalBytesSent = AtomicLong(0L)
            val corCount = 10

            // one for each coroutine
            val blockIdList = (0..corCount).map {
                mutableListOf<Pair<Int, String>>()
            }

            val channel = produce {
                repeat(corCount) {
                    launch {
                        var bytesRead: Int
                        var start = it*blobChunkSize
                        var index = it
                        val buffer = ByteArray(blobChunkSize.toInt())
                        while (start<blobSize) {
                            val range = BlobRange(start, blobChunkSize)

                            sourceBlob.openInputStream(range, BlobRequestConditions()).use { stream ->
                                bytesRead = stream.read(buffer)
                            }
                            if (bytesRead > 0) {
                                totalBytesRead.addAndGet(bytesRead.toLong())

                                // block id for this block
                                val blockId = Base64.getEncoder()
                                    .encodeToString(UUID.randomUUID().toString().toByteArray())
                                blockIdList[it].add(Pair(index, blockId))

                                // next offset for reading
                                start += corCount * blobChunkSize

                                // next index for the block id
                                // needs it to order all blocks
                                index += corCount
                                send(Chunk(blockId, buffer.copyOf(bytesRead)))
                            }
                            else {
                                break
                            }
                        }
                    }
                }
            }

            val jobs = mutableListOf<Job>()
            for(chunk in channel) {
                val job = launch {
                    totalBytesSent.addAndGet(chunk.block.size.toLong())
                    // send the chunk
                    ByteArrayInputStream(chunk.block).use { stream ->
                        destinationBlob.stageBlock(chunk.blockId, stream, chunk.block.size.toLong())
                    }
                }
                jobs.add(job)
            }
            jobs.forEach { it.join() }

            logger.info("TOTAL BYTES READ:${totalBytesRead.get()}")
            logger.info("TOTAL BYTES SENT:${totalBytesSent.get()}")

            // marge individual lists
            val idListAll = mutableListOf<Pair<Int, String>>()
            blockIdList.forEach {
                idListAll.addAll(it)
            }

            // sort by index and extract the block ids
            val idList = idListAll
                .sortedBy {(index,_)->index}
                .map {(_,blockId)-> blockId}

            destinationBlob.commitBlockList(idList)
            destinationBlob.setMetadata(metadata)
        }
    }
}
