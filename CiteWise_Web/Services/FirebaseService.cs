using CiteWise_Web.Models;
using FirebaseAdmin;
using Google.Apis.Auth.OAuth2;
using Google.Apis.Util;
using CiteWise_Web.Models.Account;
using CiteWise_Web.Models.ServiceRequest;
using Newtonsoft.Json;
using System.Text;
using Google.Cloud.Firestore;


namespace CiteWise_Web.Services
{
    public class FirebaseService
    {

        private static readonly HttpClient _client = new HttpClient
        {
            Timeout = TimeSpan.FromSeconds(15)
        };
        private readonly string _apiKey;
        private readonly string _databaseUrl;

        public FirebaseService(IConfiguration configuration)
        {
            _apiKey = configuration["Firebase:ApiKey"];
            _databaseUrl = configuration["Firebase:DatabaseUrl"];
        }

        // -------------------------------
        // REGISTER USER (Email/Password)
        // -------------------------------
        public async Task<FirebaseAuthResponse> RegisterUserAsync(string email, string password)
        {
            //using var client = GetClient();

            var data = new
            {
                email,
                password,
                returnSecureToken = true
            };

            var json = JsonConvert.SerializeObject(data);

            var response = await _client.PostAsync(
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
            //using var client = GetClient();

            var data = new
            {
                email,
                password,
                returnSecureToken = true
            };

            var json = JsonConvert.SerializeObject(data);
            var response = await _client.PostAsync(
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
            //using var client = GetClient();

            var data = new
            {
                requestType = "PASSWORD_RESET",
                email
            };

            var json = JsonConvert.SerializeObject(data);

            var response = await _client.PostAsync(
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
            //using var client = GetClient();
            var json = JsonConvert.SerializeObject(profile);

            // Save or update user profile in Realtime DB
            await _client.PutAsync(
                $"{_databaseUrl}/users/{uid}.json?auth={idToken}",
                new StringContent(json, Encoding.UTF8, "application/json")
            );
        }

        // -------------------------------
        // UPDATE USER PROFILE (merge fields)
        // -------------------------------
        public async Task UpdateUserProfileAsync(string uid, string idToken, object updates)
        {
            //using var client = GetClient();
            var json = JsonConvert.SerializeObject(updates);

            // PATCH merges fields instead of replacing the whole object
            var request = new HttpRequestMessage(new HttpMethod("PATCH"), $"{_databaseUrl}/users/{uid}.json?auth={idToken}")
            {
                Content = new StringContent(json, Encoding.UTF8, "application/json")
            };

            var response = await _client.SendAsync(request);
            response.EnsureSuccessStatusCode();
        }


        // -------------------------------
        // GET USER PROFILE (merge fields)
        // -------------------------------

        public async Task<UserProfile?> GetUserProfileAsync(string uid, string idToken)
        {
            //using var client = GetClient();
            var response = await _client.GetAsync($"{_databaseUrl}/users/{uid}.json?auth={idToken}");

            if (!response.IsSuccessStatusCode)
                return null;

            var json = await response.Content.ReadAsStringAsync();
            if (string.IsNullOrWhiteSpace(json) || json == "null")
                return null;

            return JsonConvert.DeserializeObject<UserProfile>(json);
        }

        // ------------------------------
        // GET THE CONSULTANTS 
        // ------------------------------

        public async Task<List<ConsultantItem>> GetConsultantAsync()
        {

            var response = await _client.GetAsync($"{_databaseUrl}/users.json");

            if (!response.IsSuccessStatusCode)
                return new List<ConsultantItem>();

            var json = await response.Content.ReadAsStringAsync();
            if (string.IsNullOrWhiteSpace(json) || json == "null")
                return new List<ConsultantItem>();

            var allUsers = JsonConvert.DeserializeObject<Dictionary<string, dynamic>>(json);
            var consultants = new List<ConsultantItem>();

            if(allUsers != null)
            {
                foreach(var(key,user) in allUsers)
                {
                    try
                    {
                        if(user.role == "consultant")
                        {
                            consultants.Add(new ConsultantItem
                            {
                                Id = key,
                                Name = user.name,
                                Email = user.email,
                                Speciality = user.speciality,
                            });
                        }
                    }
                    catch
                    {

                    }
                }
            }

            return consultants;
        }

        // --------------------------
        // ADD A CONSULTANT AS AN ADMIN
        // --------------------------
        public async Task<bool> AddConsultantAsync(string name, string email, string password, string speciality)
        {

            var authResponse = await RegisterUserAsync(email, password);
            if (authResponse == null || string.IsNullOrEmpty(authResponse.LocalId))
                return false;

            var profile = new
            {
                name,
                email,
                role = "consultant",
                speciality
            };

            var json = JsonConvert.SerializeObject(profile);

            var response = await _client.PutAsync(
                $"{_databaseUrl}/users/{authResponse.LocalId}.json?auth={authResponse.IdToken}",
                new StringContent(json, Encoding.UTF8, "application.json"));

            return response.IsSuccessStatusCode;
        }

        // ===============================
        // GET ALL CONSULTANTS (pending + approved)
        // ===============================
        public async Task<List<ConsultantItem>> GetAllConsultantsAsync()
        {
            var response = await _client.GetAsync($"{_databaseUrl}/users.json");

            if (!response.IsSuccessStatusCode)
                return new List<ConsultantItem>();

            var json = await response.Content.ReadAsStringAsync();
            if (string.IsNullOrWhiteSpace(json) || json == "null")
                return new List<ConsultantItem>();

            var allUsers = JsonConvert.DeserializeObject<Dictionary<string, dynamic>>(json);
            var consultants = new List<ConsultantItem>();

            if (allUsers != null)
            {
                foreach (var (key, user) in allUsers)
                {
                    try
                    {
                        if (user.role == "consultant")
                        {
                            consultants.Add(new ConsultantItem
                            {
                                Id = key,
                                Name = user.firstName + " " + user.surname,
                                Email = user.email,
                                Speciality = user.specialisation,
                                IsApproved = user.isApproved == true
                            });
                        }
                    }
                    catch { }
                }
            }

            return consultants;
        }

        // ===============================
        // APPROVE CONSULTANT
        // ===============================
        public async Task<bool> ApproveConsultantAsync(string consultantId)
        {
            var updateData = new { isApproved = true };
            var json = JsonConvert.SerializeObject(updateData);

            var request = new HttpRequestMessage(
                new HttpMethod("PATCH"),
                $"{_databaseUrl}/users/{consultantId}.json"
            )
            {
                Content = new StringContent(json, Encoding.UTF8, "application/json")
            };

            var response = await _client.SendAsync(request);
            return response.IsSuccessStatusCode;
        }

        // --------------------------
        // GET THE STUDENTS
        // --------------------------
        public async Task<List<UserProfile>> GetStudentsAsync()
        {
            var response = await _client.GetAsync($"{_databaseUrl}/users.json");

            if (!response.IsSuccessStatusCode)
                return new List<UserProfile>();

            var json = await response.Content.ReadAsStringAsync();
            if (string.IsNullOrWhiteSpace(json) || json == "null")
                return new List<UserProfile>();

            var allUsers = JsonConvert.DeserializeObject<Dictionary<string, dynamic>>(json);
            var students = new List<UserProfile>();

            if (allUsers != null)
            {
                foreach (var (key, user) in allUsers)
                {
                    try
                    {
                        if (user.role == "student")
                        {
                            students.Add(new UserProfile
                            {
                                uid = key,
                                firstName = user.firstName,
                                surname = user.surname,
                                email = user.email,
                                institution = user.institution,
                                fieldOfStudy = user.fieldOfStudy
                            });
                        }
                    }
                    catch
                    {
                        // Ignore malformed entries
                    }
                }
            }

            return students;
        }


    }
}