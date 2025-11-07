using Microsoft.Extensions.Configuration;
using System.Net.Http.Headers;
using System.Threading.Tasks;
using System.Web;

namespace CiteWise_Web.Services
{
    public class ApiService
    {
        private readonly HttpClient _client;
        
//dasdsadasds
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

            return await _client.PostAsync($"requests/{requestId}/self-assign", null);
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
    }
}
