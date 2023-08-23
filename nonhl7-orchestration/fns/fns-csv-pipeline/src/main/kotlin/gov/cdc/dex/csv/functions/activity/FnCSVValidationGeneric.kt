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

class FnCSVValidationGenericEntry{
    @FunctionName("FnCSVValidationGeneric")
    fun process(
        @DurableActivityTrigger(name = "input") input: ActivityInput, 
        context: ExecutionContext 
    ):ActivityOutput{
        val blobConnectionString = EnvironmentParam.INGEST_BLOB_CONNECTION.getSystemValue()
        val blobService = AzureBlobServiceImpl(blobConnectionString)

        return FnCSVValidationGeneric().process(input, context, blobService)
    }
}

class FnCSVValidationGeneric {

    fun process(input: ActivityInput, context: ExecutionContext, blobService:IBlobService): ActivityOutput { 
                
        context.logger.log(Level.INFO,"Running CSV Validator (Generic) for input $input");

        val sourceUrl = input.common.params.currentFileUrl

        if(sourceUrl.isNullOrBlank()){
            return ActivityOutput(errorMessage = "No source URL provided!")
        }

        if(!sourceUrl.endsWith(".csv")){
            return ActivityOutput(errorMessage = "File is not a .csv!")
        }

        if(!blobService.exists(sourceUrl)){
            return ActivityOutput(errorMessage = "File does not exist!")
        }
        
        return ActivityOutput()
    }
}