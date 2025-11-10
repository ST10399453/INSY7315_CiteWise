using CiteWise_Web.Utils;
using Newtonsoft.Json;
using System;

namespace CiteWise_Web.Models.Resources
{
    public class ResourceItem
    {
        public string Id { get; set; } = "";
        public string Name { get; set; } = "";
        public string Faculty { get; set; } = "";
        public string Category { get; set; } = ""; // WRITING_GUIDE | TEMPLATE | AI_USAGE
        public string MimeType { get; set; } = "";
        public long Size { get; set; }

        [JsonProperty("updatedAt")]
        [JsonConverter(typeof(FlexibleTimestampConverter))]
        public DateTime? UpdatedAt { get; set; }

        [JsonProperty("createdAt")]
        [JsonConverter(typeof(FlexibleTimestampConverter))]
        public DateTime? CreatedAt { get; set; }

        public string? CreatedBy { get; set; }
    }
    // Convenience for upload
    public class CreateResourceRequest
    {
        public string Name { get; set; } = "";
        public string Faculty { get; set; } = "";
        public string Category { get; set; } = ""; // WRITING_GUIDE | TEMPLATE | AI_USAGE
        public IFormFile File { get; set; } = default!;
    }
}