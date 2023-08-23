package gov.cdc.dex.csv.functions

import com.microsoft.azure.functions.ExecutionContext
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.BlobTrigger
import com.microsoft.azure.functions.annotation.BindingName

import com.fasterxml.jackson.databind.ObjectMapper
import redis.clients.jedis.Jedis
import redis.clients.jedis.DefaultJedisClientConfig


import gov.cdc.dex.csv.constants.EnvironmentParam

import java.util.logging.Level

class FnCacheUpdater {
    private var redisHost = ""
    private var redisPort = 0
    private var redisPassword = ""

    @FunctionName("FnCacheUpdater")
    fun FnCacheUpdater(
        @BlobTrigger(name = "cacheData", path = "configurations/cache/{fileName}", dataType = "binary", connection = "BlobConnection") blob: ByteArray,
        @BindingName("fileName") fileName: String,
        context: ExecutionContext
    ) {
        context.logger.info("FnCacheUpdater triggered for $fileName")

        redisHost = EnvironmentParam.REDIS_CACHE_URL.getSystemValue()
        redisPort = EnvironmentParam.REDIS_CACHE_PORT.getSystemValue().toIntOrNull() ?:0
        redisPassword = EnvironmentParam.REDIS_CACHE_PASS.getSystemValue()

        //Connect to Redis Cache
        var jedis = Jedis(redisHost, redisPort, DefaultJedisClientConfig.builder()
                .password(redisPassword)
                .ssl(true)
                .build())

        try {
            

            //Create/update key-value pair
            jedis.set(fileName, blob.toString(Charsets.UTF_8))

            context.logger.info("Key/Value updated for key: $fileName")
        } catch (e: Exception) {
            context.logger.log(Level.SEVERE, "Error updating key: $fileName - error is ${e.message}")
        } finally {
            jedis.close()
        }
    }
}