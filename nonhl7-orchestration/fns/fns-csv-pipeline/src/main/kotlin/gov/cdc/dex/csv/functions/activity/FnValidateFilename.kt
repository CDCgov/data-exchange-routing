package gov.cdc.dex.csv.functions.activity

import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger;

import gov.cdc.dex.csv.dtos.ActivityInput
import gov.cdc.dex.csv.dtos.ActivityOutput
import gov.cdc.dex.csv.services.IBlobService
import gov.cdc.dex.csv.services.AzureBlobServiceImpl
import gov.cdc.dex.csv.constants.EnvironmentParam

import java.util.logging.Level

class FnValidateFilenameEntry{
    @FunctionName("FnValidateFilename")
    fun process(
        @DurableActivityTrigger(name = "input") input: ActivityInput, 
        context: ExecutionContext 
    ):ActivityOutput{
        val blobConnectionString = EnvironmentParam.INGEST_BLOB_CONNECTION.getSystemValue()
        val blobService = AzureBlobServiceImpl(blobConnectionString)

        return FnValidateFilename().process(input, context, blobService)
    }
}

class FnValidateFilename {

    fun process(input: ActivityInput, context: ExecutionContext, blobService:IBlobService): ActivityOutput { 
                
        context.logger.log(Level.INFO,"Running CSV Filename Validator for input $input");

        val sourceUrl = input.common.params.originalFileUrl
        if(sourceUrl.isNullOrBlank()){
            return ActivityOutput(errorMessage = "No source URL provided!")
        }
        //check for file existence
        if(!blobService.exists(sourceUrl)){
            return ActivityOutput(errorMessage = "File missing in Azure! $sourceUrl")
        }

        val path = input.common.params.pathInZip ?: sourceUrl

        if(!path.endsWith(".csv")){
            return ActivityOutput(errorMessage = "File is not a .csv!")
        }

        return ActivityOutput()
    }
}