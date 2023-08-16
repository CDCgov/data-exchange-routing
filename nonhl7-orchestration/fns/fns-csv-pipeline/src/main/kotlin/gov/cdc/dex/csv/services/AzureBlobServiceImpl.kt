package gov.cdc.dex.csv.services

import com.azure.storage.blob.BlobClient
import com.azure.storage.blob.BlobClientBuilder
import com.azure.storage.blob.models.BlobProperties

import java.io.InputStream
import java.io.OutputStream

class AzureBlobServiceImpl(connectionString:String) :IBlobService {
    private val connectionString = connectionString
 
    private fun getBlobClient(blobUrl:String):BlobClient{
        return BlobClientBuilder().connectionString(connectionString).endpoint(blobUrl).buildClient()
    }

    override fun exists(blobUrl: String): Boolean { 
        return getBlobClient(blobUrl).exists()
    }

    override fun getProperties(blobUrl: String): BlobProperties { 
        return getBlobClient(blobUrl).properties
    }

    override fun openDownloadStream(blobUrl: String): InputStream {
        return getBlobClient(blobUrl).openInputStream()
    }

    override fun openUploadStream(blobUrl: String): OutputStream {
        return getBlobClient(blobUrl).blockBlobClient.getBlobOutputStream()
    }

    override fun getBlobContent(blobUrl: String): String {
        return getBlobClient(blobUrl).downloadContent().toString();
    }

}