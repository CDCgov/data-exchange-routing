package gov.cdc.dex.router

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.jupiter.api.Test
import java.util.logging.Logger
import kotlin.test.assertEquals

class
FunctionTest {

    companion object {
        val gson = Gson()

        // see message.txt
        const val SOURCE_STORAGE_ACCOUNT = "ocioederoutingdatasadev"
        const val SOURCE_CONTAINER_NAME = "routeingress"
        const val SOURCE_FILE_NAME = "dd/10/COVID_ELR_BATCH-1.txt"

        // see cosmosDB
        const val ROUTE_CONFIG =
"""
{
    "destination_id": "aims-celr",
    "event": "hl7-test",
    "destination_id_event": "aims-celr-hl7-test",
    "routes": [
        {
            "destination_storage_account": "ocioederoutingdatasadev",
            "destination_container": "test-routing",
            "destination_folder": "baba/:y/:m/:d",
            "metadata": {
                "message_type": "ELR",
                "route": "COVID19_ELR",
                "reporting_jurisdiction": "unknown"
            }
        }
    ]
}
"""
        // see cosmosDB
        const val STORAGE_ACCOUNT =
"""
{
    "storage_account": "ocioederoutingdatasadev",
    "sas": "?sv=..."
}         
"""
    }

    @Test
    fun testParseMessage() {
        val messages = gson.fromJson("/message.txt".resourceAsText()!!, Array<JsonObject>::class.java)
        val context = RouteContext("[${messages[0]}]",
            mutableMapOf(),
            mutableMapOf(),
            Logger.getAnonymousLogger())
        parseMessage(context)

        assertEquals(SOURCE_STORAGE_ACCOUNT, context.sourceStorageAccount)
        assertEquals(SOURCE_CONTAINER_NAME, context.sourceContainerName)
        assertEquals(SOURCE_FILE_NAME, context.sourceFileName)
    }

    fun testRouting() {
        val storageConfig = gson.fromJson(STORAGE_ACCOUNT, StorageAccountConfig::class.java)
        val storageCache = mutableMapOf(storageConfig.storage_account to storageConfig)

        val routeConfig = gson.fromJson(ROUTE_CONFIG, RouteConfig::class.java)

        val messages = gson.fromJson("/message.txt".resourceAsText()!!, Array<JsonObject>::class.java)
        messages?.forEach { msg ->
            val context = RouteContext(
                "[$msg]",
                mutableMapOf(),
                storageCache,
                Logger.getAnonymousLogger()
            )
            with(RouteIngestedFile()) {
                parseMessage(context)
                validateSourceBlobMeta(context)

                context.routingConfig = routeConfig
                val route = routeConfig.routes[0]
                route.sas = storageConfig.sas
                route.destinationPath = if (route.destination_folder == "")
                    "."
                else
                    foldersToPath(context, route.destination_folder.split("/", "\\"))

                routeSourceBlobToDestination(context)
            }
            assertEquals(true, context.errors.isEmpty())
        }
    }
}
