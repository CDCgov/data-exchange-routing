package gov.cdc.dex.router

import com.google.gson.annotations.SerializedName
import java.net.URI

val resourceAsText: String.() -> String? = { object {}.javaClass.getResource(this)?.readText()}

fun parseBlobURI( url:String): BlobURI {
    val uri = URI(url)
    val host = uri.host
    val path = uri.path.substringAfter("/")
    val containerName = path.substringBefore("/")
    return BlobURI(
            host,
            storageAccount = host.substringBefore("blob.core.windows.net"),
            containerName = path.substringBefore("/"),
            fileName = path.substringAfter(containerName+"/")
    )
}
