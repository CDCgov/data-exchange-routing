package gov.cdc.dex.router

import com.google.gson.Gson
import org.junit.jupiter.api.Test
import java.util.logging.Logger
import kotlin.test.assertEquals

class FunctionTest {

    companion object {
        val gson = Gson()

        const val SOURCE_STORAGE_ACCOUNT = "ocioederoutingdatasadev"
        const val SOURCE_CONTAINER_NAME = "routeingress"
        const val SOURCE_FILE_NAME = "unit-test/UNIT-TEST-DO-NOT-DELETE.txt"

        const val ROUTE_CONFIG =
        """
        """
        const val STORAGE_ACCOUNT =
        """    
        """
    }

    @Test
    fun testParseMessage() {
        val context = RouteContext("/message.txt".resourceAsText()!!, CosmosDBClient(), Logger.getAnonymousLogger())
        RouteIngestedFile().parseMessage(context)

        assertEquals(SOURCE_STORAGE_ACCOUNT, context.sourceStorageAccount)
        assertEquals(SOURCE_CONTAINER_NAME, context.sourceContainerName)
        assertEquals(SOURCE_FILE_NAME, context.sourceFileName)
    }
    fun testRouting() {
        // on azure it comes from cosmosdb
        val routeConfig = gson.fromJson(ROUTE_CONFIG, RouteConfig::class.java )

        // on azure it comes from cosmosdb
        val storageConfig = gson.fromJson(STORAGE_ACCOUNT, StorageConfig::class.java )

        val context = RouteContext("/message.txt".resourceAsText()!!, CosmosDBClient(), Logger.getAnonymousLogger())
        with(RouteIngestedFile()) {
            parseMessage(context)

            context.sourceStorageConfig = storageConfig
            getSourceBlobConfig(context)

            context.routingConfig = routeConfig
            routeSourceBlobToDestination(context)
        }
        assertEquals(null, context.error)
    }
}
