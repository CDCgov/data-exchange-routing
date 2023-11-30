
# Router Function for the DEX Pipeline

This README describes settings on dev environment and it is subject to change as the FN implementation continue to evolve.  

	
## Details:


## dex-routing cosmos db
## route-config container:
``` json
{
    "destination_id: "dex-hl7",
    "event": "hl7ingress",
    "destination_id_event": "dex-hl7-hl7ingress",
    "routes": [
        {
            "destination_storage_account": "...",
            "destinationContainer": "...",
            "destinationFolder": "..."
        },
        ...
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