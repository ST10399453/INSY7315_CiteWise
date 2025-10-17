using Google.Cloud.Firestore;

namespace CiteWise_Web.Models.ServiceRequest
{
    public class ServiceReviewModel
    {
        [FirestoreDocumentId]
        public string ReviewId { get; set; }

        [FirestoreProperty("userId")]
        public string UID { get; set; }

        [FirestoreProperty("documentId")]
        public string DocumentId { get; set; }

        [FirestoreProperty("documentName")]
        public string DocumentName { get; set; }

        [FirestoreProperty("consultantId")]
        public string ConsultantId { get; set; }      

        [FirestoreProperty("serviceType")]
        public string ServiceType { get; set; }      

        [FirestoreProperty("description")]
        public string Description { get; set; }

        [FirestoreProperty("priority")]
        public string Priority { get; set; } 

        [FirestoreProperty("deadline")]
        public Timestamp? Deadline { get; set; }

        [FirestoreProperty("createdAt")]
        public Timestamp CreatedAt { get; set; }
    }
}
