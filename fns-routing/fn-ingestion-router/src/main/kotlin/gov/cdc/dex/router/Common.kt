package gov.cdc.dex.router

import com.azure.cosmos.ConsistencyLevel
import com.azure.cosmos.CosmosClientBuilder
import com.azure.cosmos.models.CosmosQueryRequestOptions
import com.azure.storage.blob.BlobClient
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
    var isValid = true
}
class RouteConfig {
    var routes: Array<Destination> = arrayOf()
}

class StorageAccountConfig {
    lateinit var storage_account:String
    lateinit var sas:String
}

data class RouteContext(
    val message:String,
    val routeConfigCache:MutableMap<String, RouteConfig>,
    val storageAccountCache:MutableMap<String, StorageAccountConfig>,
    val logger:java.util.logging.Logger) {

    lateinit var sourceStorageAccount:String
    lateinit var sourceContainerName:String
    lateinit var sourceFileName:String
    lateinit var sourceFolderPath:String

    lateinit var sourceMetadata: MutableMap<String,String>
    lateinit var destinationId:String
    lateinit var event:String

    lateinit var routingConfig:RouteConfig

    lateinit var sourceStorageConfig: StorageAccountConfig
    lateinit var sourceBlob: BlobClient

    var errors = mutableListOf<String>()
}

class CosmosDBClient {
    companion object {
        private val cosmosEndpoint = System.getenv("CosmosDBConnectionString")
        private val cosmosKey = System.getenv("CosmosDBKey")
        private val cosmosRegion = System.getenv("CosmosDBRegion")
        private val databaseName = System.getenv("CosmosDBId")
        private val storageContainerName = System.getenv("CosmosDBStorageContainer")
        private val routeContainerName = System.getenv("CosmosDBRouteContainer")

        private val cosmosClient  by lazy  {
            CosmosClientBuilder()
                .endpoint(cosmosEndpoint)
                .key(cosmosKey)
                .consistencyLevel(ConsistencyLevel.EVENTUAL)
                .preferredRegions(listOf(cosmosRegion))
                .directMode()
                .buildClient()
        }

        private val database by lazy { cosmosClient.getDatabase(databaseName) }
        private val storageContainer by lazy { database.getContainer(storageContainerName) }
        private val routeContainer by lazy { database.getContainer(routeContainerName) }
    }

    fun readRouteConfig(destIdEvent: String): RouteConfig? {
        val iterator = routeContainer.queryItems(
            "SELECT * FROM c WHERE c.destination_id_event = \"${destIdEvent}\"",
            CosmosQueryRequestOptions(),
            RouteConfig::class.java
        ).iterator()
        return if (iterator.hasNext()) iterator.next() else  null
    }

    fun readStorageAccountConfig(storageAccount: String): StorageAccountConfig? {
        val iterator = storageContainer.queryItems(
            "SELECT * FROM c WHERE c.storage_account = \"${storageAccount}\"",
            CosmosQueryRequestOptions(),
            StorageAccountConfig::class.java
        ).iterator()
        return if (iterator.hasNext()) iterator.next() else null
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





