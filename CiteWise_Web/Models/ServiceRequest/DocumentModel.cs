using Google.Cloud.Firestore;

namespace CiteWise_Web.Models.ServiceRequest
{
    public class DocumentModel
    {
        [FirestoreDocumentId]
        public string DocumentId { get; set; }

        [FirestoreProperty("uid")]
        public string UID { get; set; }

        [FirestoreProperty("projectitle")]
        public string ProjectTitle { get; set; }

        [FirestoreProperty("service")]
        public string Service { get; set; }

        [FirestoreProperty("additionalInfo")]
        public string AdditionalInfo { get; set; }

        [FirestoreProperty("docName")]
        public string DocName { get; set; }

        [FirestoreProperty("mimeType")]
        public string MimeType { get; set; }

        [FirestoreProperty("urgency")]
        public string Urgency { get; set; } 

        [FirestoreProperty("deadline")]
        public Timestamp? Deadline { get; set; }

        [FirestoreProperty("uploadedAt")]
        public Timestamp UploadedAt { get; set; }

        [FirestoreProperty("status")]
        public string Status { get; set; } = "submitted";
    }
}
