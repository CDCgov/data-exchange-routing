# HL7 Onboarding tool


## Objective
When onboarding a data stream that has to go through the HL7 Validation 
pipeline, there are several routing configurations that needs to be in place:
One input route and several, up to 6, output routing.

Input:
* 1 route for moving hl7 files delivered to route ingress to the Hl7 ingress folder.

OUtput:
* 1 route for the Debatcher report
* 1 route for the redaction report
* 1 route for the validation report
* 1 route for the HL7-Json transformation
* 1 route for the Lake of Segments transformation
* 1 route for binary data extracted from HL7 messages

This tool facilitates the creation of all those routing configurations in one go.

To use the tool, you need Python installed on your local and access to the Azure CosmosDB
you're saving the data to.

## Step 1 -
* Update the resource group and cosmos account on cosmosDBAuth.sh.
* Execute that script to set the Account Key and Account URL to be used.

## Step 2 -
Execute the loadHL7Routes.py passing the following parameters in this order:

1. destination_id: The destination_id this datastream is associated with.
2. event: The event this datastream is associated with. 
3. hl7_storage_acct: The name of the HL7 Storage account the data needs to be moved to (Input route)
4. program_storage_acct: The name of the storage account the OUTPUT will be moved to.
5. program_container: The container name on Program_storage_acct where the OUTPUT will be moved to.

All those parameters are required.

### P.S.: 
This tool does not configure the Cosmos Table storage-account . Due to the sensitive nature
of the information on that table, that still needs to be configured manually.