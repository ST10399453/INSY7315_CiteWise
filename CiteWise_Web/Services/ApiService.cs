using System.Net.Http.Headers;
using Microsoft.Extensions.Configuration;
using System.Threading.Tasks;

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

        public async Task<HttpResponseMessage> CreateRequestAsync(
            MultipartFormDataContent formData,
            string firebaseToken
        )
        {
            _client.DefaultRequestHeaders.Authorization =
                new AuthenticationHeaderValue("Bearer", firebaseToken);

            return await _client.PostAsync("requests", formData);
        }
    }
}
