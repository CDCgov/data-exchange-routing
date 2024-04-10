package gov.cdc.dex.router

import com.azure.core.http.rest.Response
import com.azure.core.util.Context
import com.azure.storage.blob.BlobClient
import com.azure.storage.blob.models.BlobProperties
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.*

private const val ISO8601 = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"

private const val RETRY_COUNT = 3
private val TRY_TIMEOUT: Duration = Duration.ofSeconds(2)
private val RETRY_SLEEP = 1000L

fun Date.asISO8601(): String = SimpleDateFormat(ISO8601).format(this)

fun pipe(context:RouteContext, vararg fn: (RouteContext)->Unit) {
    fn.forEach { f -> if ( context.errors.isEmpty()) { f(context)} }
}