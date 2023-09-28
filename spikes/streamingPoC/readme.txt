zip file for Event hubs is the ARM template that creates the azure resources for Event hub namespace and covid eventhub and flu eventhub for routing


the main Event hub is the streaming-eventhub. For PoC the events are sent directly to this event hub using the Azure Portal (Generate-data) 
menu item

ASA will trigger when events flow throgh the main event hub and it will run the query (see query txt file) and it routes the 
events to Covid/Flu event hubs

each individual event hub (covid/flu) has an attached ASA that persists the events to storage containters as blobs. RBAC controls can 
be attached to these storage containers. 


Sample messages sent are: 
the main 