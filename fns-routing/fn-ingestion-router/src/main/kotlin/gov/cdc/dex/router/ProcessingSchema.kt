package gov.cdc.dex.router

import com.google.gson.annotations.SerializedName
import java.util.*

data class Trace(@SerializedName("trace_id") val traceId:String,
                 @SerializedName("span_id") val spanId:String)

data class SchemaContent(
    @SerializedName("file_source_blob_url")
    val fileSourceBlobUrl:String,

    @SerializedName("file_destination_blob_url")
    val fileDestinationBlobUrl:String,

    @SerializedName("result")
    val result:String,

    @SerializedName("error_description")
    val errorDescription:String? = null,

    @SerializedName("schema_version")
    val schemaVersion:String = "0.0.1",
) {
    companion object {
       fun errorSchema(sourceBlobUrl:String,destinationBlobUrl:String,error:String) = SchemaContent(
           fileSourceBlobUrl=sourceBlobUrl,
           fileDestinationBlobUrl=destinationBlobUrl,
           errorDescription = error,
           result="error"
       )
        fun successSchema(sourceBlobUrl:String,destinationBlobUrl:String) = SchemaContent(
            fileSourceBlobUrl=sourceBlobUrl,
            fileDestinationBlobUrl=destinationBlobUrl,
            result="success"
        )
    }

    @SerializedName("schema_name")
    val schemaName:String = "dex-file-copy"

    @SerializedName("timestamp")
    val timestamp:String = Date().asISO8601()
}

data class ProcessingSchema (
    @SerializedName("upload_id")
    val uploadId: String?,

    @SerializedName("destination_id")
    val destinationId: String?,

    @SerializedName("event_type")
    val eventType: String?,

    @SerializedName("content")
    var content: SchemaContent
) {
    @SerializedName("stage_name")
    val stageName = "dex-routing"

    @SerializedName("content_type")
    val contentType: String = "json"
}
