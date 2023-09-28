package gov.cdc.dex.csv.dtos

data class OrchestratorInput(
    val config          : OrchestratorConfiguration,
    val initialParams   : ActivityParams
)

data class OrchestratorConfiguration (
    val steps                   : List<OrchestratorStep>,
    val globalErrorFunction     : FunctionDefinition
)

data class OrchestratorStep (
    val stepNumber              : String,
    val functionToRun           : FunctionDefinition,
    val customErrorFunction     : FunctionDefinition? = null,
    val fanOutAfter             : Boolean = false,
    val fanInBefore             : Boolean = false,
    val fanInFailIfAnyFail      : Boolean = true
)

data class FunctionDefinition(
    val functionName            : String,
    val functionConfiguration   : Any? = null
)

data class OrchestratorOutput (
    val outputParams    : ActivityParams,
    val errorMessage    : String? = null,
    val failedStep      : String? = null
)

data class RecursiveOrchestratorInput (
    val input       : OrchestratorInput,
    val enterIndex  : Int,
    val branchIndex : String
)
data class RecursiveOrchestratorOutput (
    val output      : OrchestratorOutput,
    val leaveIndex  : Int
)