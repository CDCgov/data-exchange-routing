package hl7v2.utils
import com.google.gson.annotations.SerializedName

data class HL7Token(
    @SerializedName("fileType") var fileType: String?,
    @SerializedName("fileName") var fileName: String?,
    @SerializedName("pipelineProcess") var pipelineProcess: List<FunctionStep>
)

data class FunctionStep (
    var functionName: String?,
    var referenceStorage: String,
    var functionURI: String,
    var result: String
)