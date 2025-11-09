namespace CiteWise_Web.Models.ServiceRequest
{
    public class ServiceRequestItem
    {
        public string Id { get; set; }
        public string DocumentName { get; set; }
        public string ServiceType { get; set; }
        public string Priority { get; set; }
        public string Status { get; set; }
        public string ConsultantId { get; set; }
        public string Deadline { get; set; }

        public string? DocumentId { get; set; }

        public FileMetadata? File { get; set; }
    }
}
