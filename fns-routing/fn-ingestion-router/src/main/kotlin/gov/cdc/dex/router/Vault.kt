import com.azure.identity.DefaultAzureCredentialBuilder
import com.azure.security.keyvault.secrets.SecretClient
import com.azure.security.keyvault.secrets.SecretClientBuilder
import com.microsoft.azure.functions.annotation.*
import com.microsoft.azure.functions.ExecutionContext
import com.microsoft.azure.functions.HttpMethod
import com.microsoft.azure.functions.HttpRequestMessage
import com.microsoft.azure.functions.HttpResponseMessage
import com.microsoft.azure.functions.HttpStatus
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.core.type.TypeReference
import java.util.*

class KeyVaultFunction {

    private fun getSecretClient(): SecretClient? {
        val keyVaultUrl = System.getenv("DEX_VAULT_URL") ?: return null 
        return SecretClientBuilder()
            .vaultUrl(keyVaultUrl)
            .credential(DefaultAzureCredentialBuilder().build())
            .buildClient()
    }

    @FunctionName("writeToKeyVault")
    fun writeToKeyVault(
        @HttpTrigger(
            name = "req",
            methods = [HttpMethod.POST],
            authLevel = AuthorizationLevel.FUNCTION
        ) request: HttpRequestMessage<Optional<String>>,
        context: ExecutionContext
    ): HttpResponseMessage {
        val body = request.body.orElse(null)
        if (body == null) {
        return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body("Request body is missing")
        .build()
        }

        val objectMapper = jacksonObjectMapper()
        val data: Map<String, String> = try {
            objectMapper.readValue(body, object : TypeReference<Map<String, String>>() {})
        } catch (e: Exception) {
            context.logger.warning("Error parsing request body: ${e.message}")
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body("Error parsing request body: ${e.message}")
            .build()
        }

        val secretName = data["secretName"]
        val secretValue = data["secretValue"]
        if (secretName.isNullOrEmpty() || secretValue.isNullOrEmpty()) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                .body("Please include a valid 'secretName' and 'secretValue' in the request body")
                .build()
        }

        val secretClient = getSecretClient()
        return if (secretClient != null) {
            try {
                secretClient.setSecret(secretName, secretValue)
                context.logger.info("Secret '$secretName' updated successfully.")
                request.createResponseBuilder(HttpStatus.OK)
                    .body("Secret '$secretName' updated successfully.")
                    .build()
            } catch (e: Exception) {
            context.logger.severe("Failed to write secret to Key Vault: ${e.message}")
            request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to write secret to Key Vault: ${e.message}")
                .build()
            }
        } else {
            context.logger.severe("Failed to initialize SecretClient. Check KEY_VAULT_URL environment variable.")
            request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to initialize SecretClient")
                .build()
        }
    }


    @FunctionName("readFromKeyVault")
    fun readFromKeyVault(
        @HttpTrigger(
            name = "req",
            methods = [HttpMethod.GET],
            authLevel = AuthorizationLevel.FUNCTION
        ) request: HttpRequestMessage<String?>,
        context: ExecutionContext
    ): HttpResponseMessage {
        val secretName = request.queryParameters["secretName"]

        if (secretName.isNullOrEmpty()) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                .body("Please pass a secret name on the query string")
                .build()
        }

        context.logger.info("Attempting to read secret: $secretName from Key Vault.")

        try {
            val secretClient = getSecretClient()
            context.logger.info("SecretClient initialized successfully, proceeding to get secret.")
            val secret = secretClient!!.getSecret(secretName)
            context.logger.info("Secret retrieved successfully: Name: ${secret.name}, Value: ${secret.value}")

            return request.createResponseBuilder(HttpStatus.OK)
                .body("Secret: ${secret.name}, Value: ${secret.value}")
                .build()
        } catch (e: Exception) {
            context.logger.severe("Failed to read secret from Key Vault: ${e.message}")
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to read secret from Key Vault")
                .build()
        }
    }
}
