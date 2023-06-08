package hl7v2.redactor

import com.google.gson.annotations.SerializedName

import java.util.*

import com.azure.identity.*
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.*

import com.azure.storage.blob.*
import com.azure.storage.blob.models.*
import com.azure.storage.blob.specialized.*
import com.azure.storage.blob.BlobClient
import com.azure.storage.blob.BlobServiceClient
import com.azure.storage.blob.BlobServiceClientBuilder
import com.azure.storage.blob.BlobContainerClient
import com.azure.storage.blob.BlobContainerClientBuilder
import com.azure.storage.blob.models.BlobProperties
import com.azure.storage.blob.models.ListBlobsOptions

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonElement

class Function {
    companion object {
        val gson: Gson = GsonBuilder().serializeNulls().create()
    }
    @FunctionName("poc-redactor")
    fun run(
            @HttpTrigger(
                    name = "req",
                    methods = [HttpMethod.POST],
                    authLevel = AuthorizationLevel.ANONYMOUS) request: HttpRequestMessage<Optional<String>>,
            context: ExecutionContext): HttpResponseMessage {

        context.logger.info("HTTP trigger processed a ${request.httpMethod.name} request.")

        val query = request.queryParameters["token"]
        val body: String = request.body.orElse(query)

        if(body == null){
			return request
				.createResponseBuilder(HttpStatus.BAD_REQUEST)
				.body("Orchestration token is required the request body")
				.build()
        }
        var hl7Token : String
        hl7Token = request.body?.get().toString()
        // Retrieve Token
        var messageInfo = gson.fromJson(
            hl7Token, HL7Token::class.java
        )
        
        return request
			.createResponseBuilder(HttpStatus.OK)
			.body(messageInfo)
			.build()
    }
}

data class HL7Token(
        @SerializedName("fileType") val fileType: String?,
        @SerializedName("fileName") val fileName: String?,
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