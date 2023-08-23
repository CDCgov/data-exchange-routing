package hl7v2.redactor

import hl7v2.utils.*
import com.microsoft.azure.functions.annotation.*
import com.microsoft.azure.functions.*
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger
import com.microsoft.azure.functions.ExecutionContext

import java.util.*

class Function {
    @FunctionName("redactor")
    fun run(
        @DurableActivityTrigger(name = "input")
        input : HL7Token,
        context : ExecutionContext) : String {

            context.logger.info("HTTP trigger processed a ${input} request.")
            val hl7Token = input
            // TODO - Update to respond with token or step
            return "Completed"
        }
}
