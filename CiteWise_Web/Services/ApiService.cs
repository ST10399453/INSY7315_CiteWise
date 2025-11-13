using CiteWise_Web.Models.ServiceRequest;
using CiteWise_Web.Models.Resources;
using Microsoft.Extensions.Configuration;
using Newtonsoft.Json;
using System.Net.Http.Headers;
using System.Net.Http.Json;

namespace CiteWise_Web.Services
{
    public class ApiService
    {
        private readonly HttpClient _client;

        public ApiService(IConfiguration config, IHttpClientFactory httpClientFactory)
        {
            _client = httpClientFactory.CreateClient();
            _client.BaseAddress = new Uri(config["Api:BaseUrl"]);
            _client.Timeout = TimeSpan.FromSeconds(20);
        }

        // ---------- Internal helper (timeout-safe) ----------
        private async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request)
        {
            try
            {
                return await _client.SendAsync(request);
            }
            catch (TaskCanceledException ex)
            {
                throw new Exception("Request timed out or was cancelled.", ex);
            }
        }

        private void SetBearer(string token)
        {
            _client.DefaultRequestHeaders.Authorization =
                new AuthenticationHeaderValue("Bearer", token);
        }

        // ====================================================
        // Existing "Requests" API (kept intact)
        // ====================================================

        public async Task<HttpResponseMessage> GetRequestAsync(string firebaseToken)
        {
            SetBearer(firebaseToken);
            var req = new HttpRequestMessage(HttpMethod.Get, "requests");
            return await SendAsync(req);
        }

        public async Task<HttpResponseMessage> SelfAssignRequestAsync(string requestId, string firebaseToken)
        {
            SetBearer(firebaseToken);
            var req = new HttpRequestMessage(HttpMethod.Post, $"requests/{requestId}/self-assign")
            {
                Content = JsonContent.Create(new { })
            };
            return await SendAsync(req);
        }

        public async Task<HttpResponseMessage> AssignRequestToConsultantAsync(
            string requestId,
            string consultantId,
            string firebaseToken,
            string? deadline = null)
        {
            SetBearer(firebaseToken);

            var body = new Dictionary<string, object>
            {
                { "consultantId", consultantId }
            };
            if (!string.IsNullOrEmpty(deadline))
                body["deadline"] = deadline;

            var req = new HttpRequestMessage(HttpMethod.Post, $"requests/{requestId}/assign")
            {
                Content = JsonContent.Create(body)
            };
            return await SendAsync(req);
        }

        public async Task<HttpResponseMessage> GetUnassignedRequestsAsync(string firebaseToken)
        {
            SetBearer(firebaseToken);

            // Fetch all requests (backend supports ?userId= to filter; blank gets all user-filtered items)
            var resp = await _client.GetAsync("requests?userId=");
            if (!resp.IsSuccessStatusCode) return resp;

            // Filter unassigned client-side (ConsultantId is null/empty)
            var json = await resp.Content.ReadAsStringAsync();
            var all = JsonConvert.DeserializeObject<List<ServiceRequestItem>>(json) ?? new();

            var unassigned = all.Where(r => string.IsNullOrEmpty(r.ConsultantId)).ToList();
            var filtered = new HttpResponseMessage(resp.StatusCode)
            {
                Content = new StringContent(JsonConvert.SerializeObject(unassigned))
            };
            return filtered;
        }

        public async Task<HttpResponseMessage> CreateRequestAsync(
            MultipartFormDataContent formData,
            string firebaseToken)
        {
            SetBearer(firebaseToken);
            var req = new HttpRequestMessage(HttpMethod.Post, "requests")
            {
                Content = formData
            };
            return await SendAsync(req);
        }

        public async Task<HttpResponseMessage> GetMyRequestsAsync(
            string firebaseToken,
            string? status = null,
            string? sort = "date",
            string? dir = "desc")
        {
            SetBearer(firebaseToken);

            var query = System.Web.HttpUtility.ParseQueryString(string.Empty);
            if (!string.IsNullOrWhiteSpace(status)) query["status"] = status;
            if (!string.IsNullOrWhiteSpace(sort)) query["sort"] = sort;
            if (!string.IsNullOrWhiteSpace(dir)) query["dir"] = dir;

            var qs = query.ToString();
            var url = string.IsNullOrEmpty(qs) ? "requests" : $"requests?{qs}";

            var req = new HttpRequestMessage(HttpMethod.Get, url);
            return await SendAsync(req);
        }

        public async Task<string?> GetFileDownloadUrlAsync(string fileId, string token)
        {
            SetBearer(token);
            var req = new HttpRequestMessage(HttpMethod.Get, $"documents/{fileId}/download");
            var resp = await SendAsync(req);
            if (!resp.IsSuccessStatusCode) return null;

            var json = await resp.Content.ReadAsStringAsync();
            dynamic data = JsonConvert.DeserializeObject(json);
            return (string?)data?.url;
        }

        public async Task<HttpResponseMessage> GetAssignedRequestsAsync(string token, string consultantId)
        {
            SetBearer(token);
            var req = new HttpRequestMessage(HttpMethod.Get, $"requests?consultantId={consultantId}");
            return await SendAsync(req);
        }

        // ====================================================
        // NEW: Resources API (upload/list/signed-url/delete)
        // ====================================================

        public async Task<IReadOnlyList<ResourceItem>> ListResourcesAsync(
            string firebaseToken,
            string? faculty = null,
            string visibility = "students", // "all" | "students" | "admins"
            string? q = null,
            string sort = "date",           // "alpha" | "date"
            string dir = "desc")            // "asc" | "desc"
        {
            SetBearer(firebaseToken);

            var query = System.Web.HttpUtility.ParseQueryString(string.Empty);
            if (!string.IsNullOrWhiteSpace(faculty)) query["faculty"] = faculty;
            if (!string.IsNullOrWhiteSpace(visibility)) query["visibility"] = visibility;
            if (!string.IsNullOrWhiteSpace(q)) query["q"] = q;
            if (!string.IsNullOrWhiteSpace(sort)) query["sort"] = sort;
            if (!string.IsNullOrWhiteSpace(dir)) query["dir"] = dir;

            var url = "resources";
            var qs = query.ToString();
            if (!string.IsNullOrEmpty(qs)) url += $"?{qs}";

            var resp = await _client.GetAsync(url);
            resp.EnsureSuccessStatusCode();

            var json = await resp.Content.ReadAsStringAsync();
            return JsonConvert.DeserializeObject<List<ResourceItem>>(json) ?? new();
        }

        public async Task<ResourceItem?> UploadResourceAsync(string firebaseToken, CreateResourceRequest input)
        {
            SetBearer(firebaseToken);

            if (input.File is null || input.File.Length == 0)
                throw new InvalidOperationException("File is required.");

            using var form = new MultipartFormDataContent();

            form.Add(new StringContent(input.Name ?? ""), "name");
            form.Add(new StringContent(input.Faculty ?? ""), "faculty");
            form.Add(new StringContent(input.Category ?? ""), "category");

            var stream = input.File.OpenReadStream();
            var fileContent = new StreamContent(stream);
            fileContent.Headers.ContentType =
                new MediaTypeHeaderValue(input.File.ContentType ?? "application/octet-stream");

            form.Add(fileContent, "file", input.File.FileName);

            var resp = await _client.PostAsync("resources", form);
            if (!resp.IsSuccessStatusCode)
            {
                var err = await resp.Content.ReadAsStringAsync();
                throw new Exception($"Upload failed: {resp.StatusCode} - {err}");
            }

            var body = await resp.Content.ReadAsStringAsync();
            return JsonConvert.DeserializeObject<ResourceItem>(body);
        }

        /// <summary>
        /// Obtain short-lived signed URL from Node API to open/download a resource.
        /// </summary>
        public async Task<string?> GetResourceSignedUrlAsync(
            string firebaseToken,
            string resourceId,
            string disposition = "inline",
            int? expiresSeconds = 900) // 60..7200 supported
        {
            SetBearer(firebaseToken);

            var query = System.Web.HttpUtility.ParseQueryString(string.Empty);
            if (!string.IsNullOrWhiteSpace(disposition)) query["disposition"] = disposition;
            if (expiresSeconds.HasValue) query["expires"] = expiresSeconds.Value.ToString();

            var qs = query.ToString();
            var url = string.IsNullOrEmpty(qs)
                ? $"resources/{resourceId}/download"
                : $"resources/{resourceId}/download?{qs}";

            var resp = await _client.GetAsync(url);
            if (!resp.IsSuccessStatusCode) return null;

            var json = await resp.Content.ReadAsStringAsync();
            dynamic data = JsonConvert.DeserializeObject(json);
            return (string?)data?.url;
        }

        public async Task<bool> DeleteResourceAsync(string firebaseToken, string resourceId)
        {
            SetBearer(firebaseToken);
            var resp = await _client.DeleteAsync($"resources/{resourceId}");
            return resp.IsSuccessStatusCode;
        }

        public async Task<HttpResponseMessage> UploadAnnotatedFileAsync(string requestId, IFormFile file, string firebaseToken)
        {
            SetBearer(firebaseToken);

            using var form = new MultipartFormDataContent();

            var streamContent = new StreamContent(file.OpenReadStream());
            streamContent.Headers.ContentType = new MediaTypeHeaderValue(file.ContentType);

            form.Add(streamContent, "file", file.FileName);

            var request = new HttpRequestMessage(HttpMethod.Post, $"requests/{requestId}/annotated")
            {
                Content = form
            };

            return await SendAsync(request);
        }

        public async Task<string>? GetAnnotatedFileUrlAsync(string id, string token)
        {
            SetBearer(token);

            var resp = await _client.GetAsync($"requests/{id}/feedback/download");
            if (!resp.IsSuccessStatusCode)
                return null;

            var json = await resp.Content.ReadAsStringAsync();
            dynamic parsed = JsonConvert.DeserializeObject(json);
            return parsed?.url;
        }


    }
}


//using Microsoft.Extensions.Configuration;
//using Newtonsoft.Json;
//using System.Net.Http.Headers;
//using System.Threading.Tasks;
//using System.Web;

//namespace CiteWise_Web.Services
//{
//    public class ApiService
//    {
//        private readonly HttpClient _client;


//        public ApiService(IConfiguration config, IHttpClientFactory httpClientFactory)
//        {
//            _client = httpClientFactory.CreateClient();

//            _client.BaseAddress = new Uri(config["Api:BaseUrl"]);

//        }

//        public async Task<HttpResponseMessage> GetRequestAsync(string firebaseToken)
//        {
//            _client.DefaultRequestHeaders.Authorization =
//                 new AuthenticationHeaderValue("Bearer", firebaseToken);


//            return await _client.GetAsync("requests");
//        }

//        public async Task<HttpResponseMessage> SelfAssignRequestAsync(string requestId, string firebaseToken)
//        {
//            _client.DefaultRequestHeaders.Authorization =
//                new AuthenticationHeaderValue("Bearer", firebaseToken);

//            var body = new { };

//            return await _client.PostAsJsonAsync($"requests/{requestId}/self-assign", body);
//        }

//        public async Task<HttpResponseMessage> GetUnassignedRequestsAsync(string firebaseToken)
//        {
//            _client.DefaultRequestHeaders.Authorization =
//                new AuthenticationHeaderValue("Bearer", firebaseToken);

//            return await _client.GetAsync("requests?userId=");
//        }




//        public async Task<HttpResponseMessage> CreateRequestAsync(
//            MultipartFormDataContent formData,
//            string firebaseToken
//        )
//        {
//            _client.DefaultRequestHeaders.Authorization =
//                new AuthenticationHeaderValue("Bearer", firebaseToken);

//            return await _client.PostAsync("requests", formData);
//        }

//        public async Task<HttpResponseMessage> GetMyRequestsAsync(
//            string firebaseToken,
//            string? status = null,   // optional filter
//            string? sort = "date",   // "alpha" | "date"
//            string? dir = "desc")   // "asc" | "desc"
//        {
//            //_client.DefaultRequestHeaders.Authorization =
//            //    new AuthenticationHeaderValue("Bearer", firebaseToken);

//            var query = System.Web.HttpUtility.ParseQueryString(string.Empty);
//            if (!string.IsNullOrWhiteSpace(status)) query["status"] = status;
//            if (!string.IsNullOrWhiteSpace(sort)) query["sort"] = sort;
//            if (!string.IsNullOrWhiteSpace(dir)) query["dir"] = dir;

//            var qs = query.ToString();
//            var url = string.IsNullOrEmpty(qs) ? "requests" : $"requests?{qs}";
//            return await _client.GetAsync(url);
//        }

//        public async Task<string> GetFileDownloadUrlAsync(string fileId, string token)
//        {

//            _client.DefaultRequestHeaders.Authorization =
//        new AuthenticationHeaderValue("Bearer", token);

//            var response = await _client.GetAsync($"documents/{fileId}/download");


//            if (!response.IsSuccessStatusCode)
//                return null;

//            var json = await response.Content.ReadAsStringAsync();


//            dynamic data = JsonConvert.DeserializeObject(json);

//            return (string)data.url;
//        }

//        public async Task<HttpResponseMessage> GetAssignedRequestsAsync(string token, string consultantId)
//        {
//            _client.DefaultRequestHeaders.Authorization =
//                new AuthenticationHeaderValue("Bearer", token);

//            return await _client.GetAsync($"requests?consultantId={consultantId}");
//        }


//    }
//}


//POSSIBLE FIX?????
//______________________

//using Microsoft.Extensions.Configuration;
//using Newtonsoft.Json;
//using System.Net.Http.Headers;
//using System.Threading.Tasks;
//using System.Web;

//namespace CiteWise_Web.Services
//{
//    public class ApiService
//    {
//        private readonly HttpClient _client;

//        public ApiService(IConfiguration config, IHttpClientFactory httpClientFactory)
//        {
//            _client = httpClientFactory.CreateClient();
//            _client.BaseAddress = new Uri(config["Api:BaseUrl"]);
//            _client.Timeout = TimeSpan.FromSeconds(15); // prevents long hangs
//        }

//        private async Task<HttpResponseMessage> SendRequestAsync(HttpRequestMessage request)
//        {
//            try
//            {
//                return await _client.SendAsync(request);
//            }
//            catch (TaskCanceledException)
//            {
//                throw new Exception("Request timed out. Backend is slow or unreachable.");
//            }
//        }

//        public async Task<HttpResponseMessage> GetRequestAsync(string firebaseToken)
//        {
//            var request = new HttpRequestMessage(HttpMethod.Get, "requests");
//            request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", firebaseToken);
//            return await SendRequestAsync(request);
//        }

//        public async Task<HttpResponseMessage> SelfAssignRequestAsync(string requestId, string firebaseToken)
//        {
//            var request = new HttpRequestMessage(HttpMethod.Post, $"requests/{requestId}/self-assign")
//            {
//                Content = JsonContent.Create(new { })
//            };
//            request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", firebaseToken);
//            return await SendRequestAsync(request);
//        }

//        public async Task<HttpResponseMessage> GetUnassignedRequestsAsync(string firebaseToken)
//        {
//            var request = new HttpRequestMessage(HttpMethod.Get, "requests?userId=");
//            request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", firebaseToken);
//            return await SendRequestAsync(request);
//        }

//        public async Task<HttpResponseMessage> CreateRequestAsync(
//            MultipartFormDataContent formData,
//            string firebaseToken
//        )
//        {
//            var request = new HttpRequestMessage(HttpMethod.Post, "requests")
//            {
//                Content = formData
//            };
//            request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", firebaseToken);
//            return await SendRequestAsync(request);
//        }

//        public async Task<HttpResponseMessage> GetMyRequestsAsync(
//            string firebaseToken,
//            string? status = null,
//            string? sort = "date",
//            string? dir = "desc")
//        {
//            var query = HttpUtility.ParseQueryString(string.Empty);
//            if (!string.IsNullOrWhiteSpace(status)) query["status"] = status;
//            if (!string.IsNullOrWhiteSpace(sort)) query["sort"] = sort;
//            if (!string.IsNullOrWhiteSpace(dir)) query["dir"] = dir;

//            var qs = query.ToString();
//            var url = string.IsNullOrEmpty(qs) ? "requests" : $"requests?{qs}";

//            var request = new HttpRequestMessage(HttpMethod.Get, url);
//            request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", firebaseToken);
//            return await SendRequestAsync(request);
//        }

//        public async Task<string> GetFileDownloadUrlAsync(string fileId, string token)
//        {
//            var request = new HttpRequestMessage(HttpMethod.Get, $"documents/{fileId}/download");
//            request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);

//            var response = await SendRequestAsync(request);
//            if (!response.IsSuccessStatusCode) return null;

//            var json = await response.Content.ReadAsStringAsync();
//            dynamic data = JsonConvert.DeserializeObject(json);
//            return (string)data.url;
//        }

//        public async Task<HttpResponseMessage> GetAssignedRequestsAsync(string token, string consultantId)
//        {
//            var request = new HttpRequestMessage(HttpMethod.Get, $"requests?consultantId={consultantId}");
//            request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);
//            return await SendRequestAsync(request);
//        }
//    }
//}
