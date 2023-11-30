package gov.cdc.dex.router

import com.azure.cosmos.*
import com.azure.cosmos.models.CosmosQueryRequestOptions
import com.azure.storage.blob.BlobClient
import com.google.gson.Gson
import com.google.gson.GsonBuilder

data class EventSchema(
    val data : EventData
)

data class EventData(
    val url: String
)

class Config {
    lateinit var destination_storage_account: String
    lateinit var destination_connection_string: String
    lateinit var destination_container: String
    lateinit var destination_folder: String
    var metadata: Map<String,String>? = null
}
class RouteConfig {
    lateinit var id: String
    lateinit var destination_id:String
    lateinit var event : String
    lateinit var destination_id_event: String
    var routes: Array<Config> = arrayOf()
}

class StorageConfig {
    lateinit var id : String
    lateinit var storage_account : String
    lateinit var connection_string: String
}

data class RouteContext(val message:String, val cosmosDBClient:CosmosDBClient, val logger:java.util.logging.Logger) {
    lateinit var sourceStorageAccount:String
    lateinit var sourceContainerName:String
    lateinit var sourceFileName:String

    lateinit var sourceMetadata: MutableMap<String,String>
    lateinit var destinationId:String
    lateinit var event:String

    lateinit var routingConfig:RouteConfig

    lateinit var sourceStorageConfig: StorageConfig
    lateinit var sourceBlob: BlobClient

    var errors = mutableListOf<String>()
}

class CosmosDBClient {
    companion object {
        val gson: Gson = GsonBuilder().serializeNulls().create()

        private val cosmosEndpoint = System.getenv("CosmosDBConnectionString")!!
        private val cosmosKey = System.getenv("CosmosDBKey")!!
        private val cosmosRegion = System.getenv("CosmosDBRegion")!!
        private val databaseName = System.getenv("CosmosDBId")

        private val cosmosClient: CosmosClient by lazy {
            CosmosClientBuilder()
                .endpoint(cosmosEndpoint)
                .key(cosmosKey)
                .consistencyLevel(ConsistencyLevel.EVENTUAL)
                .preferredRegions(listOf(cosmosRegion))
                .gatewayMode()
                .buildClient()
        }

        private val database: CosmosDatabase by lazy {
            cosmosClient.getDatabase(databaseName)
        }
        val storageContainer: CosmosContainer by lazy {
            database.getContainer(System.getenv("CosmosDBStorageContainer"))
        }
        val routeContainer: CosmosContainer by lazy {
            database.getContainer(System.getenv("CosmosDBRouteContainer"))
        }

        fun close() {
            cosmosClient.close()
        }
    }

    fun readRouteConfig(destIdEvent: String): RouteConfig? {
        val iterable = routeContainer.queryItems("SELECT * FROM c WHERE c.destination_id_event = \"${destIdEvent}\"",
            CosmosQueryRequestOptions(),
            RouteConfig::class.java)
        return if (iterable.iterator().hasNext()) {
            iterable.iterator().next()
        }
        else {
            null
        }
    }

    fun readStorageConfig(): Map<String, StorageConfig> {
        val iterable = storageContainer.queryItems("SELECT * FROM c",
            CosmosQueryRequestOptions(),
            StorageConfig::class.java)
        return if (iterable.iterator().hasNext()) {
            iterable.associateBy {it.storage_account}
        }
        else {
            mutableMapOf()
        }
    }
}

