import sys

from azure.cosmos import CosmosClient

import os
URL = os.environ['ACCOUNT_URI']
KEY = os.environ['ACCOUNT_KEY']
client = CosmosClient(URL, credential=KEY)

DATABASE_NAME = 'dex-routing'
database = client.get_database_client(DATABASE_NAME)
CONTAINER_NAME = 'route-config'
container = database.get_container_client(CONTAINER_NAME)

if len(sys.argv) < 6:
    print("Pass appropriate params: <destination> <event> <hl7_storage_acct> <program_storage_acct", "program_container")
    exit()

dest_id = sys.argv[1]
event = sys.argv[2]
hl7SA = sys.argv[3]
programSA = sys.argv[4]
programContainer = sys.argv[5]

container.upsert_item({
    "id": dest_id + '-' + event,
    "destination_id": dest_id,
    "event": event,
    "routes": [
        {
            "destination_storage_account": hl7SA,
            "destination_container": "hl7ingress",
            "destination_folder": "dex-routing",
            "metadata": {
                "reporting_jurisdiction": "unknown"
            }
        }]
})

hl7Outputs = ["recdeb", "redaction_report", "validation_report", "hl7_json", "lake_segs", "binary"]

for item in hl7Outputs:
    container.upsert_item({
        "id": dest_id + '-' + item,
        "destination_id": dest_id,
        "event": item,
        "routes": [
            {
                "destination_storage_account": programSA,
                "destination_container": programContainer,
                "destination_folder": "hl7_" + item + "/:y/:m/:d/:/h",
                "metadata": {
                    "reporting_jurisdiction": "unknown"
                }
            }]
    })