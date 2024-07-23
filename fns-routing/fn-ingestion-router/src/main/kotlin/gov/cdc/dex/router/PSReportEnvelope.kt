package gov.cdc.dex.router
import com.google.gson.annotations.SerializedName

const val SCHEMA_VERSION = "1.0.0"
const val CONTENT_MIME = "application/json"

data class BlobFileCopy(
        @SerializedName("file_source_blob_url") val srcUrl: String,
        @SerializedName("file_destination_blob_url") val destUrl: String?,
        @SerializedName("timestamp") val timestamp: String?
) : ReportContent (
        contentSchemaName = "blob-file-copy",
        contentSchemaVersion = "1.0.0",
)

data class PSReportEnvelope
(
        @SerializedName("upload_id") val uploadId: String,
        @SerializedName("user_id") val userId: String?,
        @SerializedName("data_stream_id") val dataStreamId: String,
        @SerializedName("data_stream_route") val dataStreamRoute: String,
        @SerializedName("jurisdiction") val jurisdiction: String?,
        @SerializedName("sender_id") val senderId: String,
        @SerializedName("data_producer_id") val dataProducerId: String?,
        @SerializedName("dex_ingest_timestamp") val dexIngestTimestamp: String,
        @SerializedName("message_metadata") val messageMetadata: Any?,
        @SerializedName("stage_info") val stageInfo: StageInfo,
        @SerializedName("tags") val tags: Map<String, String>? = null,
        @SerializedName("data") val data: Map<String, String>? = null,
        @SerializedName("content") var content: ReportContent
) {
    @SerializedName("report_schema_version") val reportSchemaVersion: String = SCHEMA_VERSION
    @SerializedName("content_type") val contentType: String = CONTENT_MIME
}

data class StageInfo(
        @SerializedName("service") val service: String = "Routing",
        @SerializedName("stage") val stage: String = "dex-routing",
        @SerializedName("version")  val version: String = getAppVersion(),
        @SerializedName("status") val status: StageStatus,
        @SerializedName("issues")  val issues: List<Issue>?,
        @SerializedName("start_processing_time") val startProcessingTime: String?,
        @SerializedName("end_processing_time") val endProcessingTime: String?
)

enum class StageStatus { SUCCESS, FAILURE }

data class Issue(
        @SerializedName("level") val level: IssueLevel,
        @SerializedName("message") val message: String
)

enum class IssueLevel { WARNING, ERROR }

abstract class ReportContent(
        @SerializedName("content_schema_name") val contentSchemaName: String,
        @SerializedName("content_schema_version") val contentSchemaVersion: String
)

