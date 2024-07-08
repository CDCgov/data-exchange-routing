#!/bin/bash
if ! [[ "$1" =~ ^(dev|tst|stg|prd)$ ]]; then
	echo "You must pass the targe environment as paramter: dev, tst, stg or prd"
	return
fi
env=$1
routeRG=ocio-ede-$env-moderate-routing-rg

if [[ $1 == "prd" ]]; then
	function_name="routing-$env-processor"
else
	function_name="routing-processor-$env"
fi

function="routing-processor"
base_name=az-fun-hl7-$function.zip


echo "Building Jar..."
mvn clean package -DskipTests=true -Paz-$env

echo "Zipping it:"

cd target/azure-functions/$function

zip -r ../../../$base_name *
cd ../../..

echo "Deploying Zip..."

az functionapp deployment source config-zip -g $routeRG -n $function_name --src $base_name

### Set FN_VERSION:
fn_version=$(cat pom.xml |grep -oPm1 "(?<=<version>)[^<]+")
az functionapp config appsettings set --name $function_name --resource-group $routeRG --settings FN_VERSION=$fn_version
