package gov.cdc.dex.router

import com.azure.cosmos.*
import com.azure.cosmos.models.CosmosQueryRequestOptions
import com.azure.storage.blob.BlobClient

data class EventSchema(
    val data    : EventData
)

data class EventData(
    val url     : String
)

class Config {
    var destinationConnectionString: String? = null
    var destinationContainer: String? = null
    var destinationFolder: String? = null
}

class RouteConfig {
    var id: String? = null
    var destinationId: String? = null
    var event: String? = null
    var destinationIdEvent:  String? = null
    var routes: Array<Config> = arrayOf()
}

class StorageConfig {
    var id: String? = null
    var storageAccount: String? = null
    var connectionString: String? = null
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
            "SELECT * FROM c WHERE c.destinationIdEvent = \"${destIdEvent}\"",
            RouteConfig::class.java)
    }

    fun readStorageConfig(account: String): StorageConfig? {
        return runQuery(
            storageContainer,
            "SELECT * FROM c WHERE c.storageAccount = \"${account}\"",
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

