using System.Collections.Generic;
namespace IngestionRouter.Models;

public class RouteConfig
{
    public string FileType { get; set; }
    public Dictionary<string,string> StagingLocations { get; set; }
}