package gov.cdc.dex.csv.dtos

data class ReportingEvent (
    var currentFile     : String? = null,
    var stepNumber      : String? = null,
    var errorMessage    : String? = null,
    var id              : String? = null
)