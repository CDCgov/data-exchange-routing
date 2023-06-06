import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.*
import java.io.*
import java.util.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class TestStep2 {
    companion object {
        val gson: Gson = GsonBuilder().serializeNulls().create()
    }
    @FunctionName("HttpTrigger-Step2")
    fun run(
            @HttpTrigger(
                    name = "req",
                    methods = [HttpMethod.POST],
                    authLevel = AuthorizationLevel.ANONYMOUS) request: HttpRequestMessage<Optional<String>>,
            context: ExecutionContext): HttpResponseMessage {

        context.logger.info("HTTP trigger processed a ${request.httpMethod.name} request.")

        val hl7Token : String?
        hl7Token = request.body?.get().toString()
        val structuredMessage = gson.fromJson(
            hl7Token, HL7Token::class.java
        )
        context.logger.info("in Step 2")

         return request
                .createResponseBuilder(HttpStatus.OK)
                .body("test 2")
                .build()
    }
}