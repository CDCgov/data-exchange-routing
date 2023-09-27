Use the ARMTemplateForFactory and ARMTemplateParametersForFactory file to deploy code in azure data factory .

Use the DatabaseProjectdex-rs-config-db.sqlproj file for config database deployment.

Update below tables in configdb database for new source, target trigger and object details.

ConfigTrigger: Contains details about trigger name of the application.​

ConfigSource: Contains details about sources from where we are going to pull data.​​

Configtarget: Contains details about where to write files.​​

ConfigObject: Contains details about objects from which we have to pull data.

Enable Triggers in ADF to copy the data from source to target locations.git