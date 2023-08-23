package gov.cdc.dex.csv

import com.microsoft.azure.functions.ExecutionContext
import com.azure.storage.blob.models.BlobProperties

import gov.cdc.dex.csv.services.IBlobService

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.FileInputStream
import java.io.FileOutputStream

import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock;


object BlobServiceMocker {

    fun mockBlobService(parentDir:File, contentType:String = "DUMMY_CONTENT", metadata:Map<String,String> = mapOf()):IBlobService{
        val mockBlobService = Mockito.mock(IBlobService::class.java)

        Mockito.`when`(mockBlobService.exists(Mockito.anyString())).thenAnswer({doesTestFileExist(it, parentDir)})
        Mockito.`when`(mockBlobService.getProperties(Mockito.anyString())).thenAnswer({getBlobProperties(it, contentType, metadata)})
        Mockito.`when`(mockBlobService.openDownloadStream(Mockito.anyString())).thenAnswer({openInputStream(it, parentDir)})
        Mockito.`when`(mockBlobService.openUploadStream(Mockito.anyString())).thenAnswer({openOutputStream(it, parentDir)})

        return mockBlobService
    }
    

    private fun doesTestFileExist(i: InvocationOnMock, parentDir:File):Boolean{
        val relativePath:String = i.getArgument(0)
        var localFile = File(parentDir,relativePath)
        return localFile.exists()
    }

    private fun getBlobProperties(i: InvocationOnMock, contentType:String, metadata:Map<String,String>):BlobProperties{
        val relativePath:String = i.getArgument(0)

        return BlobProperties(null, null, null, -1, contentType, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, metadata, null)
    }

    private fun openInputStream(i: InvocationOnMock, parentDir:File):InputStream{
        val relativePath:String = i.getArgument(0)
        var localFile = File(parentDir,relativePath)
        return FileInputStream(localFile)
    }

    private fun openOutputStream(i: InvocationOnMock, parentDir:File):OutputStream{
        val relativePath:String = i.getArgument(0)
        var localFile = File(parentDir,relativePath)
        localFile.parentFile.mkdirs()
        return FileOutputStream(localFile)
    }
}