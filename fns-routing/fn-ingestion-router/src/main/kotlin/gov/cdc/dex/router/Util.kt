package gov.cdc.dex.router

val resourceAsText: String.() -> String? = { object {}.javaClass.getResource(this)?.readText()}
