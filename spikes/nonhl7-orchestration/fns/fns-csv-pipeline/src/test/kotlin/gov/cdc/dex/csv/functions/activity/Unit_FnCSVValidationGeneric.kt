package gov.cdc.dex.csv.functions.activity

import com.azure.storage.blob.BlobClient
import com.azure.storage.blob.models.BlobProperties
import com.azure.storage.blob.specialized.BlobInputStream

import gov.cdc.dex.csv.ContextMocker
import gov.cdc.dex.csv.dtos.ActivityInput
import gov.cdc.dex.csv.dtos.ActivityParams
import gov.cdc.dex.csv.dtos.CommonInput
import gov.cdc.dex.csv.services.IBlobService
import gov.cdc.dex.csv.BlobServiceMocker

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import kotlin.test.assertEquals

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock



internal class Unit_FnCSVValidationGeneric {
    companion object{
        private val outputParentDir = File("target/test-output/Unit_FnCSVValidationGeneric/"+LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSSSS")))
    }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //happy path
    @Test
    internal fun happyPath_singleCsv(){
        moveToOutput("test-upload.csv", "happyPath_singleCsv")
        val input = ActivityInput(common=CommonInput("happyPath_singleCsv", ActivityParams(originalFileUrl = "happyPath_singleCsv/test-upload.csv")))
        val response = FnCSVValidationGeneric().process(input, ContextMocker.mockExecutionContext(), BlobServiceMocker.mockBlobService(outputParentDir))
        Assertions.assertNull(response.updatedParams)
        Assertions.assertNull(response.errorMessage)
        Assertions.assertNull(response.fanOutParams)
    }
    
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //bad inputs
    
    @Test
    internal fun negative_url_null(){
        val input = ActivityInput(common=CommonInput("negative_url_null", ActivityParams()))
        val response = FnCSVValidationGeneric().process(input, ContextMocker.mockExecutionContext(), BlobServiceMocker.mockBlobService(outputParentDir))
        Assertions.assertEquals("No source URL provided!", response.errorMessage)
        Assertions.assertNull(response.updatedParams)
        Assertions.assertNull(response.fanOutParams)
    }

    @Test
    internal fun negative_url_empty(){
        val input = ActivityInput(common=CommonInput("negative_url_empty", ActivityParams(originalFileUrl = "")))
        val response = FnCSVValidationGeneric().process(input, ContextMocker.mockExecutionContext(),BlobServiceMocker.mockBlobService(outputParentDir))
        Assertions.assertEquals("No source URL provided!", response.errorMessage)
        Assertions.assertNull(response.updatedParams)
        Assertions.assertNull(response.fanOutParams)
    }

    @Test
    internal fun negative_url_notFound(){
        val input = ActivityInput(common=CommonInput("negative_url_notFound", ActivityParams(originalFileUrl = "some-other-file.csv")))
        val response = FnCSVValidationGeneric().process(input, ContextMocker.mockExecutionContext(), BlobServiceMocker.mockBlobService(outputParentDir))
        Assertions.assertEquals("File does not exist!", response.errorMessage)
        Assertions.assertNull(response.updatedParams)
        Assertions.assertNull(response.fanOutParams)
    }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //bad files
    
    @Test
    internal fun negative_file_notCSV(){
        moveToOutput("test-upload.xls","negative_file_notCSV")
        val input = ActivityInput(common=CommonInput("negative_file_unableToZip", ActivityParams(originalFileUrl = "negative_file_unableToZip/test-upload.xls")))
        val response = FnCSVValidationGeneric().process(input, ContextMocker.mockExecutionContext(), BlobServiceMocker.mockBlobService(outputParentDir))
        Assertions.assertEquals("File is not a .csv!", response.errorMessage)
        Assertions.assertNull(response.updatedParams)
        Assertions.assertNull(response.fanOutParams)
    }
    
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //helper functions
    
    private fun moveToOutput(fileName:String, testName:String){
        var localFileIn = File("src/test/resources/testfiles",fileName)
        var localFileOut = File(File(outputParentDir,testName),fileName)
        
        localFileOut.parentFile.mkdirs()
        localFileIn.copyTo(localFileOut)
    }
}