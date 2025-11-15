using Newtonsoft.Json;

namespace CiteWise_Web.Models.ServiceRequest
{
    public class ServiceRequestItem
    {
        public string Id { get; set; }

        [JsonProperty("customName")]
        public string DocumentName { get; set; }

        [JsonProperty("serviceType")]
        public string ServiceType { get; set; }

        [JsonProperty("priority")]
        public string Priority { get; set; }

        [JsonProperty("status")]
        public string Status { get; set; }

        [JsonProperty("consultantId")]
        public string ConsultantId { get; set; }

        [JsonProperty("deadline")]
        public string Deadline { get; set; }

        [JsonProperty("documentId")]
        public string? DocumentId { get; set; }

        [JsonProperty("file")]
        public FileMetadata? File { get; set; }
    }
}
