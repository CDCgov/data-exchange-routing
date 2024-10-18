
**Note:** This repo will be archived and no longer will be updated.

## Router Function for the DEX Pipeline

This function routes blobs dropped in a source storage account to a destination storage account.   
The destination storage account is defined by the data-stream-id and data-stream-route metadata properties of the source blob.   
The details of the destination storage account are retrieved from a CosmosDB, see below the containers structure.  
The implemented architecture connects the source storage queue as Endpoint through Event Grid/System Topic/ Blob Created event type.   
The function runs when messages are added (blobs dropped in the source storage) to the configured storage queue.
   
    
## CosmosDB
Both containers for the routing configurations are using the id property as a Partition key: \id.

### routing-config container:  

The tags :y, :m :d :h are substituted with the current year, day, month and hour when the destination
folder name is constructed
``` json   
{
    "id": "[data-stream-id]-[data-stream-route]",
    "data_stream_id": "[from metadata]",
    "data_stream_route": "[from metadata]",
    "routes": [
        {
            "destination_storage_account": "[storage-account id]",
            "destination_container": "...",
            "destination_folder": "[folders/.../:y/:m/:d/:h"
        }
    ]
}
```  
### storage-account container:  

This container supports destination storage accounts defined by connection string, sas token or service principal credentials

``` json   
{
    "id": "[destination-storage-account from route-config]",
    "connection_string": "DefaultEndpointsProtocol=https;AccountName=..."
}
```
``` json
{
    "id": "[destination-storage-account from route-config]",
    "sas": "?si..."
}
```   
``` json
{
    "id": "[destination-storage-account from route-config]",
    "tenant_id": "...",
    "client_id": "...",
    "secret": "..."
}
```  
