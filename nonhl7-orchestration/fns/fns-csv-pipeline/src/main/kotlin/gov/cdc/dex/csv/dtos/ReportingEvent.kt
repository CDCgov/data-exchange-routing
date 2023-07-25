package gov.cdc.dex.csv.dtos

data class ReportingEvent (
    val currentFile     : String? = null,
    val stepNumber      : String? = null,
    val errorMessage    : String? = null,
)