using CiteWise_Web.Utils;
using Newtonsoft.Json;
using System.ComponentModel.DataAnnotations;

namespace CiteWise_Web.Models.ServiceRequest
{
    public class ServiceRequest
    {
        [Required(ErrorMessage = "Please select the type of service you need.")]
        public string SelectedService { get; set; }

        public string? AdditionalInfo { get; set; }

        [Required(ErrorMessage = "Please enter a document name.")]
        public string DocName { get; set; }

        [Required(ErrorMessage = "Please upload your document before continuing.")]
        public IFormFile Documents { get; set; }

        [Required(ErrorMessage = "Please select an urgency level.")]
        public string Urgency { get; set; }


        [Required(ErrorMessage = "Please enter a valid deadline date.")]
        public DateTime? Deadline { get; set; }
    }

    public class RequestFile
    {
        [JsonProperty("fileId")] public string FileId { get; set; }
        [JsonProperty("originalName")] public string OriginalName { get; set; }

        
        [JsonProperty("mimeType")] public string MimeType { get; set; }
        [JsonProperty("size")] public long? Size { get; set; }
    }

    public class RequestItem
    {
        [JsonProperty("id")] public string Id { get; set; }
        [JsonProperty("userId")] public string UserId { get; set; }
        [JsonProperty("consultantId")] public string ConsultantId { get; set; }
        [JsonProperty("serviceType")] public string ServiceType { get; set; }
        [JsonProperty("description")] public string Description { get; set; }
        [JsonProperty("priority")] public string Priority { get; set; }

        [JsonProperty("deadline")]
        [JsonConverter(typeof(FlexibleTimestampConverter))]
        public DateTime? Deadline { get; set; }
        [JsonProperty("status")] public string Status { get; set; }
        [JsonProperty("documentId")] public string DocumentId { get; set; }
        [JsonProperty("customName")] public string DocName { get; set; }
        [JsonProperty("file")] public RequestFile File { get; set; }

        [JsonProperty("createdAt")]
        [JsonConverter(typeof(FlexibleTimestampConverter))]
        public DateTime? CreatedAt { get; set; }

        [JsonProperty("updatedAt")]
        [JsonConverter(typeof(FlexibleTimestampConverter))]
        public DateTime? UpdatedAt { get; set; }

        public double? QuotationAmount { get; set; }
        public int? QuotationWords { get; set; }
    }
}
