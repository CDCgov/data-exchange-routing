
# Router Function for the DEX Pipeline

This README describes settings on dev environment and it is subject to change as the FN implementation continue to evolve.  

	
## Details:


## dex-routing cosmos db
## route-config container:
``` json
{
    "destinationId": "dex-hl7",
    "event": "hl7ingress",
    "destinationIdEvent": "dex-hl7-hl7ingress",
    "routes": [
        {
            "destinationStorageAccount": "...",
            "destinationContainer": "...",
            "destinationFolder": "..."
        },
        {
            "destinationStorageAccount": "...",
            "destinationContainer": "...",
            "destinationFolder": "..."
        }
    ],
}
```
## storage-account container:   
```
{
    "storageAccount": "ocioederoutingdatasadev",
    "connectionString": "..."
}
```