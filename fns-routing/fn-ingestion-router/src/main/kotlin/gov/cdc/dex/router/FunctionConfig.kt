package gov.cdc.dex.router

import com.google.gson.annotations.SerializedName

data class RouteConfig(
        @SerializedName("FileType")         val fileType            : String,
        @SerializedName("MessageTypes")     val messageTypes        : Array<String>,
        @SerializedName("StagingLocations") val stagingLocations    : StagingLocations
)

data class StagingLocations(
        @SerializedName("DestinationContainer") val destinationContainer    : String,
)

class FunctionConfig {
    val azBlobProxy: AzureBlobProxy
    val azBlobDestProxy: AzureBlobProxy

    val blobIngestConnString = System.getenv("RouteBlobIngestConnectionString")
    val blobIngestContName = System.getenv("RouteBlobIngestContainerName")

    val blobDestinationConnString = System.getenv("RouteBlobDestinationConnectionstring")
    val blobDestinationContName = System.getenv("RouteBlobDestinationContainerName")

    init {
        azBlobProxy = AzureBlobProxy(blobIngestConnString, blobIngestContName)
        azBlobDestProxy = AzureBlobProxy(blobDestinationConnString, blobDestinationContName)
    }
}