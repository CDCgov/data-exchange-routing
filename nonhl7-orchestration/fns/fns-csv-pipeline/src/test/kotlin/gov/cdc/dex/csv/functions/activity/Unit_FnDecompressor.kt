package gov.cdc.dex.csv.functions.activity

import com.azure.storage.blob.BlobClient
import com.azure.storage.blob.models.BlobProperties
import com.azure.storage.blob.specialized.BlobInputStream
import com.microsoft.azure.functions.ExecutionContext

import gov.cdc.dex.csv.ContextMocker
import gov.cdc.dex.csv.dtos.ActivityInput
import gov.cdc.dex.csv.dtos.ActivityParams
import gov.cdc.dex.csv.dtos.CommonInput
import gov.cdc.dex.csv.dtos.ActivityOutput
import gov.cdc.dex.csv.services.IBlobService
import gov.cdc.dex.csv.BlobServiceMocker

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

import kotlin.test.assertEquals

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock



internal class Unit_FnDecompressor {
    companion object{
        private val parentDir = File("target/test-output/Unit_FnDecompressor/"+LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSSSS")))
    }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        //happy path
    @Test
    internal fun happyPath_singleCsv(){
        val inputUrl = moveToIngest("test-upload.csv","happyPath_singleCsv")
        val input = ActivityInput(common=CommonInput("happyPath_singleCsv", ActivityParams(executionId="happyPath_singleCsv", originalFileUrl = inputUrl)))
        val response = process(input, BlobServiceMocker.mockBlobService(parentDir, ))
        Assertions.assertNull(response.updatedParams)
        Assertions.assertNull(response.errorMessage)

        val fanOut = response.fanOutParams
        Assertions.assertNotNull(fanOut)
        Assertions.assertEquals(1, fanOut!!.size, "Invalid number of fan out files")
        Assertions.assertEquals(ActivityParams(executionId="happyPath_singleCsv", originalFileUrl = "ingest/happyPath_singleCsv/test-upload.csv", currentFileUrl = "ingest/happyPath_singleCsv/test-upload.csv"), fanOut[0])
    }
    
    @Test
    internal fun happyPath_zip(){
        val inputUrl = moveToIngest("test-upload-zip.zip","happyPath_zip")
        val input = ActivityInput(common=CommonInput("happyPath_zip", ActivityParams(executionId="happyPath_zip", originalFileUrl = inputUrl)))
        val response = process(input, BlobServiceMocker.mockBlobService(parentDir, "application/zip"))
        Assertions.assertNull(response.updatedParams)
        Assertions.assertNull(response.errorMessage)

        val fanOut = response.fanOutParams
        Assertions.assertNotNull(fanOut)
        Assertions.assertEquals(4, fanOut!!.size, "Invalid number of fan out files")
        
        val okFileNameList = mutableListOf(
            "processed/happyPath_zip/happyPath_zip/test-upload-zip-decompressed/test-upload-zip/test-upload-1.csv",
            "processed/happyPath_zip/happyPath_zip/test-upload-zip-decompressed/test-upload-zip/test-upload-2.csv",
            "processed/happyPath_zip/happyPath_zip/test-upload-zip-decompressed/test-upload-zip/test-upload-3/test-upload.csv",
            "processed/happyPath_zip/happyPath_zip/test-upload-zip-decompressed/test-upload-zip/test-upload-inner/test-upload-4.csv"
        )

        for(param in fanOut){
            Assertions.assertEquals("ingest/happyPath_zip/test-upload-zip.zip", param.originalFileUrl)
            val okFileName = param.currentFileUrl
            Assertions.assertNotNull(okFileName)
            Assertions.assertTrue(File(parentDir, okFileName).exists(), "file not in processed! $okFileName")
            Assertions.assertTrue(okFileNameList.remove(okFileName), "bad processed URL, potentially a duplicate")
        }

        Assertions.assertTrue(okFileNameList.isEmpty(), "some URLs not in messages $okFileNameList")
    }
    
    @Test
    internal fun happyPath_bigZip(){
        //TODO need to supply own zip
        val inputUrl = moveToIngest("test-upload-big-zip.zip","happyPath_bigZip")
        val input = ActivityInput(common=CommonInput("happyPath_bigZip", ActivityParams(executionId="happyPath_bigZip", originalFileUrl = inputUrl)))
        val response = process(input, BlobServiceMocker.mockBlobService(parentDir, "application/zip"))
        Assertions.assertNull(response.updatedParams)
        Assertions.assertNull(response.errorMessage)

        val fanOut = response.fanOutParams
        Assertions.assertNotNull(fanOut)
        Assertions.assertEquals(3, fanOut!!.size, "Invalid number of fan out files")
        System.out.println(fanOut)
        
        val okFileNameList = mutableListOf(
            "processed/happyPath_bigZip/happyPath_bigZip/test-upload-big-zip-decompressed/test-upload-big-zip/test-upload-big-1.csv",
            "processed/happyPath_bigZip/happyPath_bigZip/test-upload-big-zip-decompressed/test-upload-big-zip/test-upload-big-2.csv",
            "processed/happyPath_bigZip/happyPath_bigZip/test-upload-big-zip-decompressed/test-upload-big-zip/test-upload-big-3.csv",
        )

        for(param in fanOut){
            Assertions.assertEquals("ingest/happyPath_bigZip/test-upload-big-zip.zip", param.originalFileUrl)
            val okFileName = param.currentFileUrl
            Assertions.assertNotNull(okFileName)
            Assertions.assertTrue(File(parentDir, okFileName).exists(), "file not in processed! $okFileName")
            Assertions.assertTrue(okFileNameList.remove(okFileName), "bad processed URL, potentially a duplicate")
        }

        Assertions.assertTrue(okFileNameList.isEmpty(), "some URLs not in messages $okFileNameList")
    }

    
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //bad inputs
    
    @Test
    internal fun negative_url_null(){
        val input = ActivityInput(common=CommonInput("negative_url_null", ActivityParams()))
        val response = process(input, BlobServiceMocker.mockBlobService(parentDir))
        Assertions.assertEquals("No source URL provided!", response.errorMessage)
        Assertions.assertNull(response.updatedParams)
        Assertions.assertNull(response.fanOutParams)
    }

    @Test
    internal fun negative_url_empty(){
        val input = ActivityInput(common=CommonInput("negative_url_empty", ActivityParams(executionId="negative_url_empty", originalFileUrl = "")))
        val response = process(input, BlobServiceMocker.mockBlobService(parentDir))
        Assertions.assertEquals("No source URL provided!", response.errorMessage)
        Assertions.assertNull(response.updatedParams)
        Assertions.assertNull(response.fanOutParams)
    }

    @Test
    internal fun negative_url_notFound(){
        val input = ActivityInput(common=CommonInput("negative_url_notFound", ActivityParams(executionId="negative_url_notFound", originalFileUrl = "some-other-file.csv")))
        val response = process(input, BlobServiceMocker.mockBlobService(parentDir))
        Assertions.assertEquals("File missing in Azure! some-other-file.csv", response.errorMessage)
        Assertions.assertNull(response.updatedParams)
        Assertions.assertNull(response.fanOutParams)
    }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //bad files
    
    @Test
    internal fun negative_file_unableToZip(){
        val inputUrl = moveToIngest("test-upload.csv","negative_file_unableToZip")
        val input = ActivityInput(common=CommonInput("negative_file_unableToZip", ActivityParams(executionId="negative_file_unableToZip", originalFileUrl = inputUrl)))
        val response = process(input, BlobServiceMocker.mockBlobService(parentDir, "application/zip"))
        Assertions.assertEquals("Zipped file was empty: ingest/negative_file_unableToZip/test-upload.csv", response.errorMessage)
        Assertions.assertNull(response.updatedParams)
        Assertions.assertNull(response.fanOutParams)
    }
    
    @Test
    internal fun negative_file_emptyZip(){
        val inputUrl = moveToIngest("test-empty.zip","negative_file_emptyZip")
        val input = ActivityInput(common=CommonInput("negative_file_emptyZip", ActivityParams(executionId="negative_file_emptyZip", originalFileUrl = inputUrl)))
        val response = process(input, BlobServiceMocker.mockBlobService(parentDir, "application/zip"))
        Assertions.assertEquals("Zipped file was empty: ingest/negative_file_emptyZip/test-empty.zip", response.errorMessage)
        Assertions.assertNull(response.updatedParams)
        Assertions.assertNull(response.fanOutParams)
    }
    
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //helper functions

    
    fun process(input: ActivityInput, blobService:IBlobService):ActivityOutput{
        val startTime = LocalDateTime.now()
        val response = FnDecompressor().process(input, ContextMocker.mockExecutionContext(), blobService)
        val endTime = LocalDateTime.now()
        val timeDiff = ChronoUnit.MILLIS.between(startTime, endTime)
        
        println("\nTest ${input.common.stepNumber} took $timeDiff millis");
        return response
    }
    
    private fun moveToIngest(fileName:String, testName:String):String{
        var localFileIn = File("src/test/resources/testfiles",fileName)
        var localFileOut = File(File(File(parentDir, "ingest"),testName),fileName)
        
        localFileOut.parentFile.mkdirs()
        localFileIn.copyTo(localFileOut)

        return "ingest/$testName/$fileName"
    }
}