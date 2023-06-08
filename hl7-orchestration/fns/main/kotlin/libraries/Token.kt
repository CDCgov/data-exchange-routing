import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName

data class Token(
        @SerializedName("UTF_BOM") val UTF_BOM: String = "\uFEFF",
        @SerializedName("STATUS_SUCCESS") val STATUS_SUCCESS: String = "SUCCESS",
        @SerializedName("STATUS_ERROR") val STATUS_ERROR: String = "ERROR",
        @SerializedName("MSH_9_PATH") val MSH_9_PATH: String = "MSH-9",
        @SerializedName("MSH_21_1_1_PATH") val MSH_21_1_1_PATH: String = "MSH-21[1].1",
        @SerializedName("MSH_21_2_1_PATH") val MSH_21_2_1_PATH: String = "MSH-21[2].1",
        @SerializedName("MSH_21_3_1_PATH") val MSH_21_3_1_PATH: String = "MSH-21[3].1",
        @SerializedName("EVENT_CODE_PATH") val EVENT_CODE_PATH: String = "OBR-31.1",
        @SerializedName("JURISDICTION_CODE_PATH")
        val JURISDICTION_CODE_PATH: String = "OBX[@3.1='77968-6']-5.1",
        @SerializedName("ALT_JURISDICTION_CODE_PATH")
        val ALT_JURISDICTION_CODE_PATH: String = "OBX[@3.1='NOT116']-5.1",
        val PIPELINE_PROCESS: List<FunctionStep>,
)
/*
The token has to answer the following questions:
1. What am I looking at? File type?
2. Where can I get it? Filename + Filetype = Ability to retrieve blob
3. Now that I know what I am looking at, what are the steps to complete it?
   For each step, what unique properties do I need to provide?
   For each step, what unique hl7 properties do I need to provide?
4. Are there any additional properties I need to know for this message?
 */

data class HL7Token(
        @SerializedName("fileType") val fileType: String,
        @SerializedName("fileName") val fileName: String,
        @SerializedName("pipelineProcess") val pipelineProcess: List<FunctionStep>     
)

data class FunctionStep(
        var functionName: String?,
        var referenceStorage: String,
        var functionURI: String,
        var result: String
)

data class StepHL7Properties(
        var field: String,
        var value: String
)

data class StepFnProperties(
        var field: String,
        var value: String
)