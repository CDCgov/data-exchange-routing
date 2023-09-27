package hl7v2.utils

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName

// HTTP Post Request Imports
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse


class HTTPHandler {
    private val url: String
    private var message: String
    public var client = HttpClient.newBuilder().build()

    constructor(endpoint: String, body : String){
        this.url = endpoint
        this.message = body
    }

    public fun createPostRequest(): HttpRequest{
        var request = HttpRequest.newBuilder().uri(URI.create(this.url))
        .POST(HttpRequest.BodyPublishers.ofString(this.message))
        .build()

        return request
    }

    public fun sendRequest( request : HttpRequest): HttpResponse<String> {
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        return response
    }
}