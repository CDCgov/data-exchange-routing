package gov.cdc.dex.router

import com.azure.cosmos.ConsistencyLevel
import com.azure.cosmos.CosmosClientBuilder
import com.azure.cosmos.CosmosException
import com.azure.cosmos.models.PartitionKey
import com.azure.storage.blob.BlobClient
import com.azure.storage.blob.BlobContainerClient
import com.azure.storage.blob.BlobServiceClient
import com.azure.storage.blob.BlobServiceClientBuilder
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.net.URI
import java.time.ZoneId
import java.time.ZonedDateTime


val gson: Gson = GsonBuilder().serializeNulls().create()

data class EventSchema(
    val data : EventData
)

data class EventData(
    val url: String
)

class Destination {
    lateinit var destination_storage_account: String
    lateinit var destination_container: String
    lateinit var destination_folder: String
    var metadata: Map<String,String>? = null

    var destinationPath:String  = ""
    var sas = ""
    var connectionString = ""
    var isValid = true
}
class RouteConfig {
    var routes: Array<Destination> = arrayOf()
}

class StorageAccountConfig {
    var connection_string:String = ""
    var  sas:String = ""
}

data class RouteContext(
    val message:String,
    val routeConfigCache:MutableMap<String, RouteConfig>,
    val storageAccountCache:MutableMap<String, StorageAccountConfig>,
    val logger:java.util.logging.Logger) {

    lateinit var sourceUrl:String
    lateinit var sourceStorageAccount:String
    lateinit var sourceContainerName:String
    lateinit var sourceFileName:String
    lateinit var sourceFolderPath:String

    lateinit var sourceMetadata: MutableMap<String,String>
    lateinit var destinationId:String
    lateinit var event:String

    // TODO rework after DEX UPLOAD
    var traceId:String? = null
    var parentSpanId:String? = null
    var uploadId:String? = null
    var childSpanId:String? = null

    lateinit var routingConfig:RouteConfig

    lateinit var sourceBlob: BlobClient

    var errors = mutableListOf<String>()
}

class SourceSAConfig {
        private val containerName = System.getenv("BlobIngestContainerName")
        private val connectionString: String = System.getenv("BlobIngestConnectionString")

        private val serviceClient: BlobServiceClient = BlobServiceClientBuilder()
            .connectionString(connectionString)
            .buildClient()
        val containerClient: BlobContainerClient = serviceClient
            .getBlobContainerClient(containerName)
}

class CosmosDBConfig {
    companion object {
        private val cosmosEndpoint = System.getenv("CosmosDBConnectionString")
        private val cosmosKey = System.getenv("CosmosDBKey")
        private val cosmosRegion = System.getenv("CosmosDBRegion")
        private val databaseName = System.getenv("CosmosDBId")
        private val storageContainerName = System.getenv("CosmosDBStorageContainer")
        private val routeContainerName = System.getenv("CosmosDBRouteContainer")

        private val cosmosClient  =
            CosmosClientBuilder()
                .endpoint(cosmosEndpoint)
                .key(cosmosKey)
                .consistencyLevel(ConsistencyLevel.EVENTUAL)
                .preferredRegions(listOf(cosmosRegion))
                .directMode()
                .buildClient()
        private val database =  cosmosClient.getDatabase(databaseName)
        private val storageContainer =  database.getContainer(storageContainerName)
        private val routeContainer = database.getContainer(routeContainerName)
    }

    fun readStorageAccountConfig(account: String): StorageAccountConfig? =
        try {
            storageContainer.readItem(
                account, PartitionKey(account),
                StorageAccountConfig::class.java
            ).item
        }
        catch (ex: CosmosException) {
            null
        }

    fun readRouteConfig(destinationIdEvent: String): RouteConfig? =
        try {
            routeContainer.readItem(
                destinationIdEvent, PartitionKey(destinationIdEvent),
                RouteConfig::class.java
            ).item
        } catch (ex: CosmosException) {
            null
        }
}


/* Parses the event message and extracts storage account name
   container name and file name for the source blob
*/
fun parseMessage(context:RouteContext) {
    with (context) {
        val eventContent = gson.fromJson(message, Array<EventSchema>::class.java).first()

        sourceUrl = eventContent.data.url
        val uri = URI(sourceUrl)
        val host = uri.host
        val path = uri.path.substringAfter("/")
        val containerName = path.substringBefore("/")

        sourceStorageAccount = host.substringBefore(".blob.core.windows.net")
        sourceContainerName = path.substringBefore("/")
        sourceFileName = path.substringAfter("$containerName/")
        sourceFolderPath = sourceFileName.substringBeforeLast("/","")
    }
}

fun foldersToPath(context:RouteContext, folders:List<String>): String {
    val t= ZonedDateTime.now( ZoneId.of("US/Eastern") )
    val path = mutableListOf<String>()
    folders.forEach {
        path.add( when (it) {
            ":f" -> context.sourceFolderPath
            ":y" -> "${t.year}"
            ":m" -> "${t.monthValue}"
            ":d" -> "${t.dayOfMonth}"
            ":h" -> "${t.hour}h"
            ":mm" -> "${t.minute}m"
            else -> it
        })
    }
    return path.joinToString("/")
}





