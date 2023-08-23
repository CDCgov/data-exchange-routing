package gov.cdc.dex.router.dtos


data class EventSchema(
    val data    : EventData
)

data class EventData(
    val url     : String
)