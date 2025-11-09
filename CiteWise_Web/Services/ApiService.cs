using Microsoft.Extensions.Configuration;
using Newtonsoft.Json;
using System.Net.Http.Headers;
using System.Threading.Tasks;
using System.Web;

namespace CiteWise_Web.Services
{
    public class ApiService
    {
        private readonly HttpClient _client;


        public ApiService(IConfiguration config, IHttpClientFactory httpClientFactory)
        {
            _client = httpClientFactory.CreateClient();

            _client.BaseAddress = new Uri(config["Api:BaseUrl"]);

        }

        public async Task<HttpResponseMessage> GetRequestAsync(string firebaseToken)
        {
            _client.DefaultRequestHeaders.Authorization =
                 new AuthenticationHeaderValue("Bearer", firebaseToken);


            return await _client.GetAsync("requests");
        }

        public async Task<HttpResponseMessage> SelfAssignRequestAsync(string requestId, string firebaseToken)
        {
            _client.DefaultRequestHeaders.Authorization =
                new AuthenticationHeaderValue("Bearer", firebaseToken);

            var body = new { };

            return await _client.PostAsJsonAsync($"requests/{requestId}/self-assign", body);
        }

        public async Task<HttpResponseMessage> GetUnassignedRequestsAsync(string firebaseToken)
        {
            _client.DefaultRequestHeaders.Authorization =
                new AuthenticationHeaderValue("Bearer", firebaseToken);

            return await _client.GetAsync("requests?userId=");
        }




        public async Task<HttpResponseMessage> CreateRequestAsync(
            MultipartFormDataContent formData,
            string firebaseToken
        )
        {
            _client.DefaultRequestHeaders.Authorization =
                new AuthenticationHeaderValue("Bearer", firebaseToken);

            return await _client.PostAsync("requests", formData);
        }

        public async Task<HttpResponseMessage> GetMyRequestsAsync(
            string firebaseToken,
            string? status = null,   // optional filter
            string? sort = "date",   // "alpha" | "date"
            string? dir = "desc")   // "asc" | "desc"
        {
            _client.DefaultRequestHeaders.Authorization =
                new AuthenticationHeaderValue("Bearer", firebaseToken);

            var query = System.Web.HttpUtility.ParseQueryString(string.Empty);
            if (!string.IsNullOrWhiteSpace(status)) query["status"] = status;
            if (!string.IsNullOrWhiteSpace(sort)) query["sort"] = sort;
            if (!string.IsNullOrWhiteSpace(dir)) query["dir"] = dir;

            var qs = query.ToString();
            var url = string.IsNullOrEmpty(qs) ? "requests" : $"requests?{qs}";
            return await _client.GetAsync(url);
        }

        public async Task<string> GetFileDownloadUrlAsync(string fileId, string token)
        {

            _client.DefaultRequestHeaders.Authorization =
        new AuthenticationHeaderValue("Bearer", token);

            var response = await _client.GetAsync($"documents/{fileId}/download");


            if (!response.IsSuccessStatusCode)
                return null;

            var json = await response.Content.ReadAsStringAsync();


            dynamic data = JsonConvert.DeserializeObject(json);

            return (string)data.url;
        }

        public async Task<HttpResponseMessage> GetAssignedRequestsAsync(string token, string consultantId)
        {
            _client.DefaultRequestHeaders.Authorization =
                new AuthenticationHeaderValue("Bearer", token);

            return await _client.GetAsync($"requests?consultantId={consultantId}");
        }


    }
}