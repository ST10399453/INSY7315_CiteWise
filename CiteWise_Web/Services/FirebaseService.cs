using CiteWise_Web.Models;
using Newtonsoft.Json;
using System.Text;

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
            return JsonConvert.DeserializeObject<FirebaseAuthResponse>(result);
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

    }
}
