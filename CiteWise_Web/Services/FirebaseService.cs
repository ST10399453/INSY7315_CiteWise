using CiteWise_Web.Models;
using CiteWise_Web.Models.Account;
using CiteWise_Web.Models.ServiceRequest;
using Newtonsoft.Json;
using System.Text;
using Google.Cloud.Firestore;


namespace CiteWise_Web.Services
{
    public class FirebaseService
    {
        private readonly string _apiKey;
        private readonly string _databaseUrl;

        public FirebaseService(IConfiguration configuration)
        {
            _apiKey = configuration["Firebase:ApiKey"];
            _databaseUrl = configuration["Firebase:DatabaseUrl"];
        }

        private HttpClient GetClient()
        {
            return new HttpClient();
        }

        // -------------------------------
        // REGISTER USER (Email/Password)
        // -------------------------------
        public async Task<FirebaseAuthResponse> RegisterUserAsync(string email, string password)
        {
            using var client = GetClient();

            var data = new
            {
                email,
                password,
                returnSecureToken = true
            };

            var json = JsonConvert.SerializeObject(data);

            var response = await client.PostAsync(
                $"https://identitytoolkit.googleapis.com/v1/accounts:signUp?key={_apiKey}",
                new StringContent(json, Encoding.UTF8, "application/json")
            );

            var result = await response.Content.ReadAsStringAsync();
            return JsonConvert.DeserializeObject<FirebaseAuthResponse>(result);
        }

        // -------------------------------
        // LOGIN USER (Email/Password)
        // -------------------------------
        public async Task<FirebaseAuthResponse> LoginUserAsync(string email, string password)
        {
            using var client = GetClient();

            var data = new
            {
                email,
                password,
                returnSecureToken = true
            };

            var json = JsonConvert.SerializeObject(data);
            var response = await client.PostAsync(
                $"https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={_apiKey}",
                new StringContent(json, Encoding.UTF8, "application/json")
            );

            var result = await response.Content.ReadAsStringAsync();

            // Check if Firebase returned an error
            if (result.Contains("error"))
            {
                return null; // or throw custom exception with message
            }

            return JsonConvert.DeserializeObject<FirebaseAuthResponse>(result);
        }

        //SEND PASSWORD RESET EMAIL 
        public async Task<bool> SendPasswordResetEmailAsync(string email)
        {
            using var client = GetClient();

            var data = new
            {
                requestType = "PASSWORD_RESET",
                email
            };

            var json = JsonConvert.SerializeObject(data);

            var response = await client.PostAsync(
                $"https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=AIzaSyDwPulYyuQA-CqcFCuXwY05_gxm-PZ7P1M",
                new StringContent(json, Encoding.UTF8, "application/json")
            );

            var result = await response.Content.ReadAsStringAsync();

            if (response.IsSuccessStatusCode)
            {
                return true; // Email sent successfully
            }

            throw new Exception($"Password reset failed: {result}");
        }


        // -------------------------------
        // SAVE/UPDATE USER PROFILE
        // -------------------------------
        public async Task SaveUserProfileAsync(string uid, string idToken, UserProfile profile)
        {
            using var client = GetClient();
            var json = JsonConvert.SerializeObject(profile);

            // Save or update user profile in Realtime DB
            await client.PutAsync(
                $"{_databaseUrl}/users/{uid}.json?auth={idToken}",
                new StringContent(json, Encoding.UTF8, "application/json")
            );
        }

        // -------------------------------
        // UPDATE USER PROFILE (merge fields)
        // -------------------------------
        public async Task UpdateUserProfileAsync(string uid, string idToken, object updates)
        {
            using var client = GetClient();
            var json = JsonConvert.SerializeObject(updates);

            // PATCH merges fields instead of replacing the whole object
            var request = new HttpRequestMessage(new HttpMethod("PATCH"), $"{_databaseUrl}/users/{uid}.json?auth={idToken}")
            {
                Content = new StringContent(json, Encoding.UTF8, "application/json")
            };

            var response = await client.SendAsync(request);
            response.EnsureSuccessStatusCode();
        }


        // -------------------------------
        // GET USER PROFILE (merge fields)
        // -------------------------------

        public async Task<UserProfile?> GetUserProfileAsync(string uid, string idToken)
        {
            using var client = GetClient();
            var response = await client.GetAsync($"{_databaseUrl}/users/{uid}.json?auth={idToken}");

            if (!response.IsSuccessStatusCode)
                return null;

            var json = await response.Content.ReadAsStringAsync();
            if (string.IsNullOrWhiteSpace(json) || json == "null")
                return null;

            return JsonConvert.DeserializeObject<UserProfile>(json);
        }

        // -------------------------------
        // SAVE DOCUMENT TO FIRESTORE
        // -------------------------------

        public async Task<string> SaveDocumentToFirestoreAsync(DocumentModel doc, string idToken)
        {
            using var client = new HttpClient();

            var firestoreDoc = new
            {
                fields = new
                {
                    uid = new { stringValue = doc.UID },
                    docName = new { stringValue = doc.DocName },
                    mimeType = new { stringValue = doc.MimeType },
                    additionalInfo = new { stringValue = doc.AdditionalInfo ?? "" },
                    service = new { stringValue = doc.Service ?? "" },
                    urgency = new { stringValue = doc.Urgency ?? "" },
                    deadline = doc.Deadline.HasValue
            ? new { timestampValue = doc.Deadline.Value.ToDateTime().ToString("o") }
            : null,
                    uploadedAt = new { timestampValue = doc.UploadedAt.ToDateTime().ToString("o") },
                    status = new { stringValue = doc.Status }
                }
            };


            var json = JsonConvert.SerializeObject(firestoreDoc);

            var response = await client.PostAsync(
                $"https://firestore.googleapis.com/v1/projects/budgetapp-fbcbf/databases/(default)/documents/Documents",
                new StringContent(json, Encoding.UTF8, "application/json"));

            response.EnsureSuccessStatusCode();
            var result = await response.Content.ReadAsStringAsync();
            return result;
        }


        // -------------------------------
        // SAVE SERVICE INFO TO FIRESTORE
        // -------------------------------
        public async Task SaveServiceReviewAsync(ServiceReviewModel review)
        {
            using var client = new HttpClient();

            var firestoreDoc = new
            {
                fields = new
                {
                    userId = new { stringValue = review.UID },
                    documentId = new { stringValue = review.DocumentId },
                    consultantId = new { stringValue = review.ConsultantId ?? "" },
                    serviceType = new { stringValue = review.ServiceType ?? "" },
                    description = new { stringValue = review.Description ?? "" },
                    priority = new { stringValue = review.Priority ?? "" },
                    deadline = review.Deadline != null? new { timestampValue = review.Deadline.Value.ToDateTime().ToString("o") }: null,
                    createdAt = new { timestampValue = review.CreatedAt.ToDateTime().ToString("o") }
                }
            };

            var json = JsonConvert.SerializeObject(firestoreDoc);

            var response = await client.PostAsync(
                $"https://firestore.googleapis.com/v1/projects/budgetapp-fbcbf/databases/(default)/documents/ServiceReviews",
                new StringContent(json, Encoding.UTF8, "application/json")
            );

            response.EnsureSuccessStatusCode();
        }

        // ----- GET ALL SERVICE REVIEWS ------------------
        // GET ALL SERVICE REVIEWS
        public async Task<List<ServiceReviewModel>> GetAllServiceReviewsAsync()
        {
            using var client = new HttpClient();
            var response = await client.GetAsync(
                "https://firestore.googleapis.com/v1/projects/budgetapp-fbcbf/databases/(default)/documents/ServiceReviews"
            );

            response.EnsureSuccessStatusCode();
            var json = await response.Content.ReadAsStringAsync();

            var result = new List<ServiceReviewModel>();

            dynamic firestoreResponse = JsonConvert.DeserializeObject(json);

            if (firestoreResponse.documents != null)
            {
                foreach (var doc in firestoreResponse.documents)
                {
                    var fields = doc.fields;

                   
                    var nameParts = doc.name.ToString().Split('/');
                    var reviewId = nameParts[nameParts.Length - 1];

                    // Convert timestamps to Firestore Timestamp
                    Timestamp? deadline = null;
                    if (fields.deadline != null && fields.deadline.timestampValue != null)
                    {
                        deadline = Timestamp.FromDateTime(DateTime.Parse(fields.deadline.timestampValue.ToString()).ToUniversalTime());
                    }

                    Timestamp createdAt = Timestamp.FromDateTime(DateTime.Parse(fields.createdAt.timestampValue.ToString()).ToUniversalTime());

                    var review = new ServiceReviewModel
                    {
                        ReviewId = reviewId,
                        UID = fields.userId?.stringValue,
                        DocumentId = fields.documentId?.stringValue,
                        ConsultantId = fields.consultantId?.stringValue,
                        ServiceType = fields.serviceType?.stringValue,
                        Description = fields.description?.stringValue,
                        Priority = fields.priority?.stringValue,
                        Deadline = deadline,
                        CreatedAt = createdAt
                    };

                    result.Add(review);
                }
            }

            return result;
        }
    }
}