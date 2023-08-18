package hl7v2.utils

import com.azure.storage.blob.*
import com.azure.storage.blob.models.*
import com.azure.core.util.BinaryData
import com.azure.core.http.rest.*


class BlobProxy(connectionStr: String, container: String) {

    private val blobServiceClient: BlobServiceClient 
    private val blobContainerClient: BlobContainerClient

    init{
        blobServiceClient =  BlobServiceClientBuilder().connectionString(connectionStr).buildClient()
        blobContainerClient = blobServiceClient.getBlobContainerClient(container)
    }

    fun getBlobContainerClient(): BlobContainerClient{
        return blobContainerClient
    }

    fun getBlobClient(blobName: String): BlobClient {
        return blobContainerClient.getBlobClient(blobName)
    }

    fun getBlobUrl(blobName: String): String {
        return blobContainerClient.getBlobClient(blobName).getBlobUrl()
    }

    fun listBlobs(): PagedIterable<BlobItem> {
        return blobContainerClient.listBlobs()
    }

    fun uploadDataToBlob(blobClient: BlobClient, blobData: String, token: HL7Token, name: String): HL7Token {
        blobClient.upload(BinaryData.fromString(blobData), true);
        token.fileName = name
        return token
    }
}