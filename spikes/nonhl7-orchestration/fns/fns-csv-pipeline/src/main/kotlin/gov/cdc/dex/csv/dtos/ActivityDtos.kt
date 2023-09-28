package gov.cdc.dex.csv.dtos

data class ActivityInput (
    val config  : Any? = null,
    val common  : CommonInput
)

data class CommonInput (
    val stepNumber  : String,
    val params      : ActivityParams,
    val fanInParams : List<ActivityParams>? = null
)

data class ActivityOutput (
    val updatedParams   : ActivityParams? = null,
    val errorMessage    : String? = null,
    val fanOutParams    : List<ActivityParams>? = null
)

data class ActivityParams (
    val executionId         : String? = null,
    var originalFileUrl     : String? = null,
    var currentFileUrl      : String? = null,
    var errorMessage        : String? = null
)