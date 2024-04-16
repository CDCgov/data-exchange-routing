#!/bin/bash
if ! [[ "$1" =~ ^(dev|tst|stg|prd)$ ]]; then
	echo "You must pass the targe environment as paramter: dev, tst, stg or prd"
	return
fi
env=$1
hl7RG=ocio-ede-$env-moderate-routing-rg

function=routing-${env}-processor
base_name=az-fun-hl7-$function.zip


echo "Building Jar..."
mvn clean package -DskipTests=true -Paz-$env

echo "Zipping it:"

cd target/azure-functions/$function

zip -r ../../../$base_name *
cd ../../..

echo "Deploying Zip..."

az functionapp deployment source config-zip -g $hl7RG -n $function --src $base_name