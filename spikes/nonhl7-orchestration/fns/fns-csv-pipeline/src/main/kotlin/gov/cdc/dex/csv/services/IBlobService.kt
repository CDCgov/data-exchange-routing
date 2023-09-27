package gov.cdc.dex.csv.services

import com.azure.storage.blob.models.BlobProperties

import java.io.InputStream
import java.io.OutputStream

interface IBlobService{
    fun exists(blobUrl:String):Boolean
    fun getProperties(blobUrl:String):BlobProperties
    fun openDownloadStream(blobUrl:String):InputStream
    fun openUploadStream(blobUrl:String):OutputStream
}