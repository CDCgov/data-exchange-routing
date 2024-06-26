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
    print("Pass appropriate params: <data_stream_id> <route> <hl7_storage_acct> <program_storage_acct", "program_container")
    exit()

dest_id = sys.argv[1]
event = sys.argv[2]
hl7SA = sys.argv[3]
programSA = sys.argv[4]
programContainer = sys.argv[5]

container.upsert_item({
    "id": dest_id + '-' + event,
    "data_stream_id": dest_id,
    "data_stream_route": event,
    "routes": [
        {
            "destination_storage_account": hl7SA,
            "destination_container": "hl7ingress",
            "destination_folder": "dex-routing"
        }]
})

if (event.startswith('hl7')):
    hl7Outputs = ["recdeb", "redacted", "validation_report", "json", "lake_seg", "binary"]

    for item in hl7Outputs:
        container.upsert_item({
            "id": dest_id + '-hl7_out_' + item,
            "data_stream_id": dest_id,
            "data_stream_route": "hl7_out_" + item,
            "routes": [
                {
                    "destination_storage_account": programSA,
                    "destination_container": programContainer,
                    "destination_folder": "hl7_out_" + item + "/:y/:m/:d/"
                }]
        })