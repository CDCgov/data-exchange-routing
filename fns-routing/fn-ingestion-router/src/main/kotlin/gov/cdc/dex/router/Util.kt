package gov.cdc.dex.router

import java.text.SimpleDateFormat
import java.util.*

private const val ISO8601 = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"

val resourceAsText: String.() -> String? = { object {}.javaClass.getResource(this)?.readText()}
fun Date.asISO8601(): String = SimpleDateFormat(ISO8601).format(this)

fun pipe(context:RouteContext, vararg fn: (RouteContext)->Unit) {
    fn.forEach { f -> if ( context.errors.isEmpty()) { f(context)} }
}

/**
* This function reties the action block as many times as defined by the first
* param times.
* Each retry can be after a given factor in delayFactorSec.
* Say, if delayFactorSec it is not specified, it will always wait the same amount of time (delayMillis)
* If delayFactorSec is 1 (one second), then each retry will be one second slower.
*/
fun <T> retry(
    times: Int,
    delayMillis: Long = 1000,
    delayFactorSec: Int = 0,
    logger: (s:String) ->Unit,
    action: () -> T?): T? {

    val timeToSleep = delayMillis+(delayFactorSec*1000)
    repeat(times) { r ->
        logger("Retrying ${r+1}")
        try {
            val result = action()
            if (result != null) {
                return result
            }
            logger("Result came back null")
        } catch (e: Exception) {
            logger("Exception caught: ${e.message}")
        }
        Thread.sleep(timeToSleep)
    }
    return null
}
