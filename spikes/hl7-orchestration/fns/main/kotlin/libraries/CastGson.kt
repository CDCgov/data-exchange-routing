package hl7v2.utils

import com.google.gson.*
// import java.util.*
// import com.google.gson.annotations.SerializedName

class CastGson{
    companion object {
        val gson: Gson = GsonBuilder().serializeNulls().create()
    }

    inline fun <reified T: Any> castToClass(jsonString: String): T?{
        return gson.fromJson(jsonString, T::class.java) 
    }

    inline fun <reified T: Any> castToClassExplicit(jsonString: String): T{
        return gson.fromJson(jsonString, T::class.java) 
    }

    inline fun <reified T: Any> castToClassAsList(jsonString: String): List<T>?{
        return gson.fromJson(jsonString, Array<T>::class.java).asList() 
    }
}