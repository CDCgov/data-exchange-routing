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
    var destination_connection_string: String? = null
    var destination_container: String? = null
    var destination_folder: String? = null
}
 class RouteConfig {
     var id: String? = null
     var destination_id: String? = null
     var event : String? = null
     var destination_id_event: String? = null
     var routes: Array<Config> = arrayOf()
 }

class StorageConfig {
    var id : String? = null
    var storage_account : String? = null
    var connection_string: String? = null
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

    var error:String? = null
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
        return runQuery(
            routeContainer,
            "SELECT * FROM c WHERE c.destination_id_event = \"${destIdEvent}\"",
            RouteConfig::class.java)
    }

    fun readStorageConfig(account: String): StorageConfig? {
        return runQuery(
            storageContainer,
            "SELECT * FROM c WHERE c.storage_account = \"${account}\"",
            StorageConfig::class.java)
    }
    private fun <T> runQuery(
        container:CosmosContainer,
        query: String,
        classType: Class<T>): T? {

        val iterable = container.queryItems(query,
            CosmosQueryRequestOptions(),
            classType)

        return if (iterable.iterator().hasNext()) {
            iterable.iterator().next()
        }
        else {
            null
        }
    }
}

