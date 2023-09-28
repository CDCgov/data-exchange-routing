package hl7v2.utils

import com.google.gson.annotations.SerializedName

data class HL7Message(
        @SerializedName("topic") var topic: String? = null,
        @SerializedName("subject") var subject: String? = null,
        @SerializedName("eventType") var eventType: String? = null,
        @SerializedName("id") var id: String? = null,
        @SerializedName("data") var data: Data? = Data(),
        @SerializedName("dataVersion") var dataVersion: String? = null,
        @SerializedName("metadataVersion") var metadataVersion: String? = null,
        @SerializedName("eventTime") var eventTime: String? = null
)

data class Data(
        @SerializedName("api") var api: String? = null,
        @SerializedName("clientRequestId") var clientRequestId: String? = null,
        @SerializedName("requestId") var requestId: String? = null,
        @SerializedName("eTag") var eTag: String? = null,
        @SerializedName("contentType") var contentType: String? = null,
        @SerializedName("contentLength") var contentLength: String? = null,
        @SerializedName("blobType") var blobType: String? = null,
        @SerializedName("url") var url: String? = null,
        @SerializedName("sequencer") var sequencer: String? = null,
        @SerializedName("identity") var identity: String? = null
)
