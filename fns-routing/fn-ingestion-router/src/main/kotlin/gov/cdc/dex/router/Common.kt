package gov.cdc.dex.router

import com.azure.cosmos.*
import com.azure.cosmos.models.CosmosQueryRequestOptions

data class EventSchema(
        val data    : EventData
)

data class EventData(
        val url     : String
)

class Config {
    var destinationStorageAccount: String? = null
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

data class BlobURI(
        val host:String,
        val storageAccount:String,
        val containerName:String,
        val fileName:String
)

class CosmosDBClient {
    companion object {
        private val cosmosEndpoint = System.getenv("CosmosDBConnectionString")!!
        private val cosmosKey = System.getenv("CosmosDBKey")!!
        private val cosmosRegion = System.getenv("CosmosDBRegion")!!
        private val databaseName = System.getenv("CosmosDBId")
        private val containerName = System.getenv("CosmosDBContainer")

        private val cosmosClient: CosmosClient by lazy {
            CosmosClientBuilder()
                    .endpoint(cosmosEndpoint)
                    .key(cosmosKey)
                    .consistencyLevel(ConsistencyLevel.EVENTUAL)
                    .preferredRegions(listOf(cosmosRegion))
                    .directMode()
                    .buildClient()
        }

        private val database: CosmosDatabase by lazy {
            cosmosClient.getDatabase(databaseName)
        }
        val container: CosmosContainer by lazy {
            database.getContainer(containerName)
        }

        fun close() {
            cosmosClient.close()
        }
    }

    fun readRouteConfig(destIdEvent: String): RouteConfig? {
        val iterable = container.queryItems(
                "SELECT * FROM c WHERE c.destinationIdEvent = \"${destIdEvent}\"",
                CosmosQueryRequestOptions(),
                RouteConfig::class.java)

        return if (iterable.iterator().hasNext()) {
            iterable.iterator().next()
        }
        else {
            null
        }
    }
}

