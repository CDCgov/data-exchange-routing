RES_GROUP=ocio-ede-dev-moderate-routing-rg
ACCT_NAME=ocio-ede-dev-routing-db-cosmos-account

export ACCOUNT_URI=$(az cosmosdb show --resource-group $RES_GROUP --name $ACCT_NAME --query documentEndpoint --output tsv)
export ACCOUNT_KEY=$(az cosmosdb list-keys --resource-group $RES_GROUP --name $ACCT_NAME --query primaryMasterKey --output tsv)