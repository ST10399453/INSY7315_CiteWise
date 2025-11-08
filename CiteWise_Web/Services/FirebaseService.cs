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
    }
}