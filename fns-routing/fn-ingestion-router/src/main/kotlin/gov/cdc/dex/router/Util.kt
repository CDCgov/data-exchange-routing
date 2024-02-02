package gov.cdc.dex.router

import java.text.SimpleDateFormat
import java.util.*

private const val ISO8601 = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"

val resourceAsText: String.() -> String? = { object {}.javaClass.getResource(this)?.readText()}
fun Date.asISO8601(): String = SimpleDateFormat(ISO8601).format(this)

fun pipe(context:RouteContext, vararg fn: (RouteContext)->Unit) {
    fn.forEach { f -> if ( context.errors.isEmpty()) { f(context)} }
}

