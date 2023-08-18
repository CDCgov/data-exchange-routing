package gov.cdc.dex.csv.constants

enum class EnvironmentParam(val paramKey:String){
    INGEST_BLOB_CONNECTION("BlobConnection"),
    CONFIG_BLOB_CONNECTION("BlobConnection"),
    BASE_CONFIG_URL("BaseConfigUrl"),
    REDIS_CACHE_PASS("RedisCachePass"),
    REDIS_CACHE_PORT("RedisCachePort"),
    REDIS_CACHE_URL("RedisCacheURL"),
    VALIDATION_NUMBER_ACTIVE_THREADS("NumActiveThreads"),
    VALIDATION_NUMBER_PENDING_THREADS("NumPendingThreads"),
    VALIDATION_BATCH_SIZE("BatchSize");

    fun getSystemValue():String{
        val value = System.getenv(paramKey) 
        if(value == null){
            throw IllegalArgumentException("$paramKey Environment variable not defined")
        }
        return value
    }
}