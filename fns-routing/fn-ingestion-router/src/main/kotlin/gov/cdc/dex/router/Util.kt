package gov.cdc.dex.router


val resourceAsText: String.() -> String? = { object {}.javaClass.getResource(this)?.readText()}

fun  pipe(context:RouteContext, vararg fn: (RouteContext)->Unit) {
    fn.forEach { f -> if ( context.errors.isEmpty()) { f(context)} }
}
