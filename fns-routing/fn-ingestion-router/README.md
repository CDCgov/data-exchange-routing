
# Router Function for the DEX Pipeline

This service receives HL7 and CSV data uploaded from various programs and sources and reroutes them to DEX pipeline ingress container.
	
## Details:
The Router listens to BlobCreate events in configured drop folders and processes those files.

The content of the files is not validated. 

fileconfigs.json in resources folder contains the mappings for message_type from the files' metadata and the output folders.  

Accepted message type are CASE and ELR for HL7 files and empty message type for CSV

## fileconfigs.json:
``` json
[
  {
    "FileType": "CSV",
    "MessageTypes": [""],
    "StagingLocations": {
      "DestinationContainer": "/dex-routing/CSV"
    }
  },
  {
    "FileType": "HL7",
    "MessageTypes": ["CASE","ELR"],
    "StagingLocations": {
      "DestinationContainer": "/dex-routing/HL7"
    }
  },
  {
    "FileType": "?",
    "MessageTypes": [""],
    "StagingLocations": {
      "DestinationContainer": "/dex-routing/misc"
    }
  }
]
```
