## Steps to run the function

1. Add the following in the local.settings.json file
```
{
  "IsEncrypted": false,
  "Values": {
    "ServiceBusConnectionString": "<SERVICE_BUS_CONNECTION_STRING>",
    "AzureWebJobsStorage": "<BLOB_STORAGE_CONNECTION_STRING>",
    "FUNCTIONS_WORKER_RUNTIME": "java"
  }
} 
```

2. Go to the `Run and Debug (Ctrl + Shift + D)` option in vs code.
3. Click on `Run`.
4. It will start debugging the project in the terminal.
5. Once its built and ready go to the `Azure (Shift + Alt + A)` tab on left hand side pane of vs code.
6. In the `Workspace` section, expand the `Local Project` and `Functions`.
7. Right click on `InternalRouter` and click on `Execute Function Now...`.
8. This will open a text box at the top add the following Batch Message to it and hit `enter`:
```
[{
	"topic": "/subscriptions/01f9b1b1-a130-4031-ba25-71771007d3ca/resourceGroups/ocio-ede-dev-moderate-hl7-rg/providers/Microsoft.Storage/storageAccounts/tfedemessagestoragedev",
	"subject": "/blobServices/default/containers/hl7ingress/blobs/unitTests/BatchedMessage.doNotDelete.txt",
	"eventType": "Microsoft.Storage.BlobCreated",
	"id": "7273cdb4-901e-000e-2db6-7e858d06cb75",
	"data": {
		"api": "PutBlob",
		"clientRequestId": "8df0affa-6a06-4bd7-bea5-9ff48233652c",
		"requestId": "7273cdb4-901e-000e-2db6-7e858d000000",
		"eTag": "0x8DB4CCDDFF72FDE",
		"contentType": "application/octet-stream",
		"contentLength": "6416",
		"blobType": "BlockBlob",
		"url": "https://tfedemessagestoragedev.blob.core.windows.net/hl7ingress/unitTests/BatchedMessage.doNotDelete.txt",
		"sequencer": "0000000000000000000000000001d01a00000000000031f0",
		"identity": "$superuser"

	},
	"dataVersion": "",
	"metadataVersion": "1",
	"eventTime": "2023-05-04T18:32:11.2658945Z",
	"claimCheckToken": "xyz"
}]
```
9. This should give an output similar to this:
```
Executing 'Functions.InternalRouter' (Reason='This function was programmatically called via the host APIs.', Id=0c260498-ee22-4b2c-98e7-162355aec373)
[2023-06-02T14:13:54.554Z] [
  {
    "topic": "/subscriptions/01f9b1b1-a130-4031-ba25-71771007d3ca/resourceGroups/ocio-ede-dev-moderate-hl7-rg/providers/Microsoft.Storage/storageAccounts/tfedemessagestoragedev",
    "subject": "/blobServices/default/containers/hl7ingress/blobs/unitTests/BatchedMessage.doNotDelete.txt",
    "eventType": "Microsoft.Storage.BlobCreated",
    "id": "7273cdb4-901e-000e-2db6-7e858d06cb75",
    "data": {
      "api": "PutBlob",
      "clientRequestId": "8df0affa-6a06-4bd7-bea5-9ff48233652c",
      "requestId": "7273cdb4-901e-000e-2db6-7e858d000000",
      "eTag": "0x8DB4CCDDFF72FDE",
      "contentType": "application/octet-stream",
      "contentLength": "6416",
      "blobType": "BlockBlob",
      "url": "https://tfedemessagestoragedev.blob.core.windows.net/hl7ingress/unitTests/BatchedMessage.doNotDelete.txt",
      "sequencer": "0000000000000000000000000001d01a00000000000031f0",
      "identity": "$superuser"
    },
    "dataVersion": "",
    "metadataVersion": "1",
    "eventTime": "2023-05-04T18:32:11.2658945Z",
    "claimCheckToken": "xyz"
  }
]
[2023-06-02T14:13:54.575Z] /subscriptions/01f9b1b1-a130-4031-ba25-71771007d3ca/resourceGroups/ocio-ede-dev-moderate-hl7-rg/providers/Microsoft.Storage/storageAccounts/tfedemessagestoragedev
[2023-06-02T14:13:54.581Z] added claimCheckToken
[2023-06-02T14:13:54.581Z] {"topic":"/subscriptions/01f9b1b1-a130-4031-ba25-71771007d3ca/resourceGroups/ocio-ede-dev-moderate-hl7-rg/providers/Microsoft.Storage/storageAccounts/tfedemessagestoragedev","subject":"/blobServices/default/containers/hl7ingress/blobs/unitTests/BatchedMessage.doNotDelete.txt","eventType":"Microsoft.Storage.BlobCreated","id":"7273cdb4-901e-000e-2db6-7e858d06cb75","data":{"api":"PutBlob","clientRequestId":"8df0affa-6a06-4bd7-bea5-9ff48233652c","requestId":"7273cdb4-901e-000e-2db6-7e858d000000","eTag":"0x8DB4CCDDFF72FDE","contentType":"application/octet-stream","contentLength":"6416","blobType":"BlockBlob","url":"https://tfedemessagestoragedev.blob.core.windows.net/hl7ingress/unitTests/BatchedMessage.doNotDelete.txt","sequencer":"0000000000000000000000000001d01a00000000000031f0","identity":"$superuser"},"dataVersion":"","metadataVersion":"1","eventTime":"2023-05-04T18:32:11.2658945Z","claimCheckToken":"Added claimCheckToken"}     
[2023-06-02T14:13:55.210Z] {
[2023-06-02T14:13:55.211Z]   "args": {},
[2023-06-02T14:13:55.212Z]   "data": "",
[2023-06-02T14:13:55.214Z]   "files": {},
[2023-06-02T14:13:55.215Z]   "form": {
[2023-06-02T14:13:55.216Z]     "[{\"topic\":\"/subscriptions/01f9b1b1-a130-4031-ba25-71771007d3ca/resourceGroups/ocio-ede-dev-moderate-hl7-rg/providers/Microsoft.Storage/storageAccounts/tfedemessagestoragedev\",\"subject\":\"/blobServices/default/containers/hl7ingress/blobs/unitTests/BatchedMessage.doNotDelete.txt\",\"eventType\":\"Microsoft.Storage.BlobCreated\",\"id\":\"7273cdb4-901e-000e-2db6-7e858d06cb75\",\"data\":{\"api\":\"PutBlob\",\"clientRequestId\":\"8df0affa-6a06-4bd7-bea5-9ff48233652c\",\"requestId\":\"7273cdb4-901e-000e-2db6-7e858d000000\",\"eTag\":\"0x8DB4CCDDFF72FDE\",\"contentType\":\"application/octet-stream\",\"contentLength\":\"6416\",\"blobType\":\"BlockBlob\",\"url\":\"https://tfedemessagestoragedev.blob.core.windows.net/hl7ingress/unitTests/BatchedMessage.doNotDelete.txt\",\"sequencer\":\"0000000000000000000000000001d01a00000000000031f0\",\"identity\":\"$superuser\"},\"dataVersion\":\"\",\"metadataVersion\":\"1\",\"eventTime\":\"2023-05-04T18:32:11.2658945Z\",\"claimCheckToken\":\"Added claimCheckToken\"}]": ""
[2023-06-02T14:13:55.217Z] Function "InternalRouter" (Id: 0c260498-ee22-4b2c-98e7-162355aec373) invoked by Java Worker
[2023-06-02T14:13:55.219Z]   },
[2023-06-02T14:13:55.221Z]   "headers": {
[2023-06-02T14:13:55.223Z]     "x-forwarded-proto": "https",
[2023-06-02T14:13:55.224Z]     "x-forwarded-port": "443",
[2023-06-02T14:13:55.226Z]     "host": "postman-echo.com",
[2023-06-02T14:13:55.227Z]     "x-amzn-trace-id": "Root=1-6479f91f-237496b46c244d6a673021c2",
[2023-06-02T14:13:55.228Z]     "content-length": "938",
[2023-06-02T14:13:55.231Z]     "content-type": "application/x-www-form-urlencoded",
[2023-06-02T14:13:55.233Z]     "cache-control": "no-cache",
[2023-06-02T14:13:55.237Z]     "pragma": "no-cache",
[2023-06-02T14:13:55.239Z]     "user-agent": "Java/17.0.7",
[2023-06-02T14:13:55.241Z]     "accept": "text/html, image/gif, image/jpeg, *; q=.2, */*; q=.2"
[2023-06-02T14:13:55.242Z]   },
[2023-06-02T14:13:55.242Z]   "json": {
[2023-06-02T14:13:55.244Z]     "[{\"topic\":\"/subscriptions/01f9b1b1-a130-4031-ba25-71771007d3ca/resourceGroups/ocio-ede-dev-moderate-hl7-rg/providers/Microsoft.Storage/storageAccounts/tfedemessagestoragedev\",\"subject\":\"/blobServices/default/containers/hl7ingress/blobs/unitTests/BatchedMessage.doNotDelete.txt\",\"eventType\":\"Microsoft.Storage.BlobCreated\",\"id\":\"7273cdb4-901e-000e-2db6-7e858d06cb75\",\"data\":{\"api\":\"PutBlob\",\"clientRequestId\":\"8df0affa-6a06-4bd7-bea5-9ff48233652c\",\"requestId\":\"7273cdb4-901e-000e-2db6-7e858d000000\",\"eTag\":\"0x8DB4CCDDFF72FDE\",\"contentType\":\"application/octet-stream\",\"contentLength\":\"6416\",\"blobType\":\"BlockBlob\",\"url\":\"https://tfedemessagestoragedev.blob.core.windows.net/hl7ingress/unitTests/BatchedMessage.doNotDelete.txt\",\"sequencer\":\"0000000000000000000000000001d01a00000000000031f0\",\"identity\":\"$superuser\"},\"dataVersion\":\"\",\"metadataVersion\":\"1\",\"eventTime\":\"2023-05-04T18:32:11.2658945Z\",\"claimCheckToken\":\"Added claimCheckToken\"}]": ""
[2023-06-02T14:13:55.247Z]   },
[2023-06-02T14:13:55.249Z]   "url": "https://postman-echo.com/post"
[2023-06-02T14:13:55.250Z] }

```
