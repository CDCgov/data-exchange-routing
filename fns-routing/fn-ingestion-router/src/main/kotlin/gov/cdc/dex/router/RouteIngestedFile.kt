package gov.cdc.dex.router

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.microsoft.azure.functions.annotation.*
import org.slf4j.LoggerFactory

private data class EventSchema(
    val eventType: String,
    val data : EventData
)

private data class EventData(
    val url: String
)

class RouteIngestedFile {
    companion object {
        const val BLOB_CREATED = "Microsoft.Storage.BlobCreated"
        val fnConfig = FunctionConfig()
        val gson: Gson = GsonBuilder().serializeNulls().create()
        private var logger = LoggerFactory.getLogger(Function::class.java.simpleName)
    }


    @FunctionName("RouteIngestedFile")
    fun run(
        @EventHubTrigger(name = "message",
            eventHubName = "%EventHubReceiveName%",
            consumerGroup = "%EventHubConsumerGroup%",
            connection = "EventHubConnectionString",
            cardinality = Cardinality.ONE)
        message: String,
    ):Int {
        logger.info("DEX::RouteIngestedFile function triggered")

        // count processed messages
        // implementation will change if cardinality is Cardinality.MANY
        // and message List<String>
        var countProcessedMsg = 0
        try {
            val event = gson.fromJson(message, Array<EventSchema>::class.java).first()
            if (event.eventType != BLOB_CREATED) {
                // no messages processed
                return countProcessedMsg
            }
            val blobName = event.data.url.substringAfter("/${fnConfig.blobIngestContName}/")
            logger.info("DEX::Reading blob: $blobName")
            val sourceBlobClient = fnConfig.azBlobProxy.getBlobClient(blobName)
            val sourceMetadata = sourceBlobClient.properties.metadata.mapKeys { it.key.lowercase() }.toMutableMap()

            // get message type from the metadata
            val messageType = sourceMetadata.getOrDefault("message_type", "?")

            // load configs
            val configJson = "/fileconfigs.json".resourceAsText()
            val routeConfigs = gson.fromJson(configJson, Array<RouteConfig>::class.java).toList()

            // find config for this message type
            var routeConfig = routeConfigs.firstOrNull { it.messageTypes.contains(messageType) }
            if (routeConfig == null) {
                logger.warn("No routing configured for files with a $messageType message type. Will route to misc folder.")
                routeConfig = routeConfigs.firstOrNull { it.fileType == "?" }
                if(routeConfig == null){
                    logger.error("Config file does not define misc folder.")
                    return countProcessedMsg
                }
            }
            // output to destination container and blob
            val destinationFileName = blobName.split("/").last()
            val destinationBlobName = "${routeConfig.stagingLocations.destinationContainer}/${destinationFileName}"

            val destClient = fnConfig.azBlobDestProxy.getBlobClient(destinationBlobName)
            val sourceBlobInputStream = sourceBlobClient.openInputStream()
            destClient.upload(sourceBlobInputStream, sourceBlobClient.properties.blobSize, true)
            sourceBlobInputStream.close()

            sourceMetadata["system_provider"] = "DEX-ROUTING"
            destClient.setMetadata(sourceMetadata)

            logger.info("DEX::Blob $blobName has been routed to $destinationBlobName")
            return ++countProcessedMsg
        } catch (e: Exception) {
            logger.error(e.message)
        }
        return countProcessedMsg
    }
}