using Azure.Storage.Blobs;
using Azure.Storage.Blobs.Models;
using IngestionRouter.Models;
using Microsoft.Azure.WebJobs;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging;
using Newtonsoft.Json;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Threading.Tasks;

namespace IngestionRouter;

public class RouteIngestedFile
{
    private readonly IConfiguration _configuration;
    public RouteIngestedFile(IConfiguration configuration)
    {
        _configuration = configuration;
    }

    [FunctionName("RouteIngestedFile")]
    public async Task Run([BlobTrigger("filedump/{blobname}.{blobextension}", Connection = "blobtriggerConnection")] Stream myBlob, string blobName, string blobExtension, IDictionary<string, string> metaData, ILogger log, ExecutionContext context)
    {
        log.LogInformation($"C# Blob trigger function Processed blob\n Name:{blobName}.{blobExtension} \n Size: {myBlob.Length} Bytes. Metadata: {JsonConvert.SerializeObject(metaData)}");

        //Load Config
        string configPath = Path.Combine(context.FunctionAppDirectory, "fileconfigs.json");
        string configJson = File.ReadAllText(configPath);
        if (string.IsNullOrEmpty(configJson))
        {
            log.LogError("Config file is empty, routing cannot continue");
            return;
        }
        List<RouteConfig> routeConfigs = JsonConvert.DeserializeObject<List<RouteConfig>>(configJson);

        //Get Config for this Message Type
        string messageType = metaData.ContainsKey("message_type") ? metaData["message_type"] : "?";
        RouteConfig routeConfig = routeConfigs.FirstOrDefault(x => x.MessageTypes.Contains(messageType));
        if (routeConfig is null)
        {
            log.LogError($"No routing configured for files with a {messageType} message type. Will route to misc folder.");
            routeConfig = routeConfigs.FirstOrDefault(x => x.FileType == "?");
        }

        //Define Destination
        string destinationRoute = routeConfig.StagingLocations["DestinationContainer"];
        string destinationBlobName = $"{destinationRoute}/{blobName}.{blobExtension}";
        string destinationContainerName = "routedfiles";
        BlobServiceClient blobServiceClient = new(_configuration["DestinationBlobStorageConnection"]);
        BlobContainerClient containerClient = blobServiceClient.GetBlobContainerClient(destinationContainerName);

        //Build Source reference
        BlobClient blobClient = containerClient.GetBlobClient(destinationBlobName);

        //Add Source Metadata to Destination
        BlobUploadOptions blobUploadOptions = new() { Metadata = metaData };

        //Upload the file
        await blobClient.UploadAsync(myBlob, blobUploadOptions);

        log.LogInformation($"Blob {blobName}.{blobExtension} has been routed to {destinationBlobName}");
    }
}
