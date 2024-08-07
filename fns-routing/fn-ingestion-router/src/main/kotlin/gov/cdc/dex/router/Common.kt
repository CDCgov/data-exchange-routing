package gov.cdc.dex.router

import com.azure.core.http.policy.HttpLogDetailLevel
import com.azure.core.http.policy.HttpLogOptions
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
import com.google.gson.annotations.SerializedName
import java.net.URI
import java.util.*
import java.util.concurrent.ConcurrentHashMap

data class ConfigCache(val intervalMillis:Long) {
    private val lock = Any()

    @Volatile
    private var cacheExpire:Long = System.currentTimeMillis()+intervalMillis

    val routesCache = ConcurrentHashMap<String,  RouteConfig>()
    val storageCache = ConcurrentHashMap<String,  StorageAccountConfig>()

    fun clearIfExpired(now:Long) =
        if ( cacheExpire < now) {
            synchronized(lock) {
                if (cacheExpire < now) {
                    cacheExpire = now + intervalMillis
                    routesCache.clear()
                    storageCache.clear()
                }
                true
            }
        }
        else false
}

data class Chunk(
    val blockId: String,
    val block:ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Chunk

        return blockId == other.blockId
    }
    override fun hashCode(): Int {
        return blockId.hashCode()
    }
}

val gson: Gson = GsonBuilder().serializeNulls().create()
val regexUTC = """^(\d{4})-(\d\d)-(\d\d).(\d\d):(\d\d).*$""".toRegex()

data class EventSchema(
    val data : EventData
)

data class EventData(
    val url: String
)
data class Destination(
    @SerializedName("destination_storage_account") val destinationStorageAccount: String = "",
    @SerializedName("destination_container") val destinationContainer: String = "",
    @SerializedName("destination_folder") val destinationFolder: String = "",
    @SerializedName("metadata") val metadata: Map<String,String>? = null
) {
    var destinationPath:String  = ""

    var sas = ""
    var connectionString = ""
    var tenantId:String = ""
    var clientId:String = ""
    var secret:String = ""
    var isValid = true
}

data class RouteConfig( val id:String,
                        @SerializedName("routes")  var routes: Array<Destination> = arrayOf()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RouteConfig

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

data class StorageAccountConfig(
    @SerializedName("connection_string") val connectionString:String = "",
    @SerializedName("sas") val sas:String = "",
    @SerializedName("tenant_id") val tenantId:String = "",
    @SerializedName("client_id") val clientId:String = "",
    @SerializedName("secret") val secret:String = "")

data class RouteContext(
    val message:String,
    val logger:java.util.logging.Logger) {

    lateinit var sourceUrl:String
    lateinit var sourceStorageAccount:String
    lateinit var sourceContainerName:String
    lateinit var sourceFileName:String
    lateinit var sourceFolderPath:String
    lateinit var creationTimeUTC:String

    var blobSize:Long = 0L

    lateinit var sourceMetadata: MutableMap<String,String>
    lateinit var dataStreamId:String
    lateinit var dataStreamRoute:String

    lateinit var traceId:String
    lateinit var parentSpanId:String
    lateinit var uploadId:String

    lateinit var childSpanId:String
    val isChildSpanInitialized get() = this::childSpanId.isInitialized && childSpanId.isNotEmpty()

    var routingConfig:RouteConfig? = null

    lateinit var sourceBlob: BlobClient

    var errors = mutableListOf<String>()
}

class SourceSAConfig {
    private val containerName = System.getenv("BlobIngestContainerName")
    private val deadLetterContainerName = System.getenv("DeadLetterContainer")?:"route-deadletter"
    private val connectionString: String = System.getenv("BlobIngestConnectionString")

    private val serviceClient: BlobServiceClient = BlobServiceClientBuilder()
        .connectionString(connectionString)
        .buildClient()

    val containerClient: BlobContainerClient = serviceClient
        .getBlobContainerClient(containerName)

    val deadLetterContainerClient: BlobContainerClient = serviceClient
        .getBlobContainerClient(deadLetterContainerName)
}

class CosmosDBConfig {
    companion object {
        private val cosmosEndpoint = System.getenv("CosmosDBUri")
        private val cosmosKey = System.getenv("CosmosDBKey")
        private val cosmosRegion = System.getenv("CosmosDBRegion")?:"eastus"
        private val databaseName = System.getenv("CosmosDBId")
        private val storageContainerName = System.getenv("CosmosDBStorageContainer")
        private val routeContainerName = System.getenv("CosmosDBRouteContainer")

        private val cosmosClient = CosmosClientBuilder()
            .endpoint(cosmosEndpoint)
            .key(cosmosKey)
            .consistencyLevel(ConsistencyLevel.EVENTUAL)
            .preferredRegions(listOf(cosmosRegion))
            .gatewayMode()
            .buildClient()

        private val database = cosmosClient.getDatabase(databaseName)
        private val storageContainer = database.getContainer(storageContainerName)
        private val routeContainer = database.getContainer(routeContainerName)
    }

    fun readStorageAccountConfig(account: String): StorageAccountConfig? =
        try {
            gson.fromJson(
                storageContainer.readItem(
                    account,
                    PartitionKey(account), String::class.java
                ).item,
                StorageAccountConfig::class.java
            )
        }
        catch (ex: CosmosException) {
            null
        }

    fun readRouteConfig(destinationIdEvent: String): RouteConfig? =
        try {
            gson.fromJson(
                routeContainer.readItem(
                    destinationIdEvent,
                    PartitionKey(destinationIdEvent), String::class.java
                ).item,
                RouteConfig::class.java
            )
        } catch (ex: CosmosException) {
            null
        }
}


/* Parses the event message and extracts storage account name
   container name and file name for the source blob
*/
fun parseMessage(context:RouteContext) {
    with (context) {
        val eventContent = gson.fromJson(message, EventSchema::class.java)
        sourceUrl = eventContent.data.url

        val fileName = sourceUrl.substringAfterLast("/")
        val uri = URI(sourceUrl.substringBefore(fileName))

        val host = uri.host
        val path = uri.path.substringAfter("/")

        sourceStorageAccount = host.substringBefore(".blob.core.windows.net")
        sourceContainerName = path.substringBefore("/")
        sourceFileName = "${path.substringAfter("/", "")}$fileName"
        sourceFolderPath = sourceFileName.substringBeforeLast("/","")
        context.logger.info("${RouteIngestedFile.ROUTE_MSG} BLOB URL:$sourceUrl")

    }
}

fun foldersToPath(context:RouteContext, folders:List<String>): String {
    // TODO-DO  fix for :f and missing creationTimeUTC
    val res = regexUTC.find(context.creationTimeUTC) ?: return folders.joinToString("/")

    val (year, month, day, hour, minutes) = res.destructured
    val path = mutableListOf<String>()
    folders.forEach {
        path.add( when (it) {
            ":f" -> context.sourceFolderPath
            ":y" -> year
            ":m" -> month
            ":d" -> day
            ":h" -> hour
            ":mm" ->minutes
            else -> it
        })
    }
    return path.filter { it.isNotEmpty() }
        .joinToString("/")
}

fun getAppVersion(): String {
    val properties = Properties()
    val inputStream = ClassLoader.getSystemResourceAsStream("META-INF/maven/gov.cdc.dataexchange.router/fns-ingestion-router/pom.properties")

    if (inputStream != null) {
        properties.load(inputStream)
        return properties.getProperty("version")
    }
    return "unknown"
}





