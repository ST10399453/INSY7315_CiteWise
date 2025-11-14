using CiteWise_Web.Models.Resources;
using CiteWise_Web.Models.ServiceRequest;
using CiteWise_Web.Services;
using CiteWise_Web.Utils;
using Microsoft.AspNetCore.Mvc;
using Newtonsoft.Json;

namespace CiteWise_Web.Controllers
{
    public class StudentController : Controller
    {
        private readonly ApiService _apiService;

        public StudentController(ApiService apiService)
        {
            _apiService = apiService;
        }

        [HttpGet]
        public async Task<IActionResult> StudentDashboard(string? status = null)
        {
            var role = HttpContext.Session.GetString("UserRole");
            var token = HttpContext.Session.GetString("FirebaseToken");

            if (string.IsNullOrEmpty(role) || role.ToLower() != "student" || string.IsNullOrEmpty(token))
                return RedirectToAction("Login", "Account");

            var resp = await _apiService.GetMyRequestsAsync(token, status);
            if (!resp.IsSuccessStatusCode)
            {
                var apiError = await resp.Content.ReadAsStringAsync();
                TempData["Error"] = $"Failed to load requests. API response: {apiError}";
                return View(new List<RequestItem>()); // still render view
            }

            var json = await resp.Content.ReadAsStringAsync();

            var items = new List<RequestItem>();
            try
            {
                // Normal deserialization if API returns simple primitives
                 items = JsonConvert.DeserializeObject<List<RequestItem>>(json, new FlexibleTimestampConverter());

            }
            catch (JsonSerializationException)
            {
                // Firestore-style fallback (nested _seconds fields)
                dynamic parsed = JsonConvert.DeserializeObject(json);
                foreach (var entry in parsed)
                {
                    var item = new RequestItem
                    {
                        ServiceType = entry.serviceType,
                        Description = entry.description,
                        Priority = entry.priority,
                        //Deadline = entry.deadline,
                        Deadline = entry.deadline,

                        CreatedAt = entry.createdAt?._seconds,
                        UpdatedAt = entry.updatedAt?._seconds
                    };
                    items.Add(item);
                }
            }

            return View(items);
        }

        [HttpGet]
        public IActionResult StudentServiceRequest()
        {
            var firebaseToken = HttpContext.Session.GetString("FirebaseToken");
            var role = HttpContext.Session.GetString("UserRole");

            if (string.IsNullOrEmpty(firebaseToken) || string.IsNullOrEmpty(role) || role.ToLower() != "student")
            {
                return RedirectToAction("Login", "Account");
            }

            return View();
        }

        [HttpPost]
        public async Task<IActionResult> StudentServiceRequest(ServiceRequest model)
        {
            if (!ModelState.IsValid)
            {
                return View(model);
            }

            string firebaseToken = HttpContext.Session.GetString("FirebaseToken");
            if (string.IsNullOrEmpty(firebaseToken))
            {
                return RedirectToAction("Login", "Account");
            }

            string userRole = HttpContext.Session.GetString("UserRole");
            if (userRole?.ToLower() != "student")
            {
                return RedirectToAction("Login", "Account");
            }

            // Build multipart form data
            var formData = new MultipartFormDataContent();

            // Attach the file
            if (model.Documents != null && model.Documents.Length > 0)
            {
                var streamContent = new StreamContent(model.Documents.OpenReadStream());
                streamContent.Headers.ContentType =
                    new System.Net.Http.Headers.MediaTypeHeaderValue(model.Documents.ContentType);
                formData.Add(streamContent, "file", model.Documents.FileName);
            }

            // Attach text fields
            formData.Add(new StringContent(model.DocName ?? string.Empty), "documentName");
            formData.Add(new StringContent(model.SelectedService ?? string.Empty), "serviceType");
            //formData.Add(new StringContent(model.AdditionalInfo ?? string.Empty), "description");
            formData.Add(new StringContent(string.IsNullOrWhiteSpace(model.AdditionalInfo)? "No additional description provided.": model.AdditionalInfo),"description");

            formData.Add(new StringContent(model.Urgency ?? string.Empty), "priority");

            if (model.Deadline.HasValue)
            {
                formData.Add(new StringContent(model.Deadline.Value.ToString("yyyy-MM-dd")), "deadline");
            }

            // Send to API
            HttpResponseMessage response = await _apiService.CreateRequestAsync(formData, firebaseToken);

            if (response.IsSuccessStatusCode)
            {
                TempData["RequestSubmitted"] = true;
                return RedirectToAction("Confirmation");
            }

            // Handle API error
            string apiError = await response.Content.ReadAsStringAsync();
            ModelState.AddModelError("", $"Failed to submit request. API response: {apiError}");
            return View(model);
        }

        [HttpGet]
        public IActionResult Confirmation()
        {
            if (TempData["RequestSubmitted"] == null)
            {
                // User didn't submit a request — redirect to dashboard
                return RedirectToAction("StudentDashboard");
            }

            TempData.Remove("RequestSubmitted");

            return View();
        }


        [HttpGet]
        public async Task<IActionResult> ViewRequests(string priority = "All")
        {
            var token = HttpContext.Session.GetString("FirebaseToken");
            var role = HttpContext.Session.GetString("UserRole");

            if (string.IsNullOrEmpty(token) || string.IsNullOrEmpty(role) || role.ToLower() != "student")
                return RedirectToAction("Login", "Account");

            var resp = await _apiService.GetMyRequestsAsync(token, null); // fetch all requests
            if (!resp.IsSuccessStatusCode)
            {
                TempData["Error"] = "Failed to load requests from API.";
                return View(new List<RequestItem>());
            }

            var json = await resp.Content.ReadAsStringAsync();
            var items = JsonConvert.DeserializeObject<List<RequestItem>>(json) ?? new List<RequestItem>();

            if (priority != "All")
            {
                items = items.Where(r => string.Equals(r.Priority, priority, StringComparison.OrdinalIgnoreCase)).ToList();
            }

            return View(items);
        }


        [HttpGet]
        public async Task<IActionResult> Resources(string? faculty = null, string? q = null, string sort = "date", string dir = "desc")
        {
            var role = HttpContext.Session.GetString("UserRole");
            var token = HttpContext.Session.GetString("FirebaseToken");

            if (string.IsNullOrEmpty(token) || string.IsNullOrEmpty(role) || role.ToLower() != "student")
                return RedirectToAction("Login", "Account");

            IReadOnlyList<ResourceItem> items;
            try
            {
                items = await _apiService.ListResourcesAsync(
                    token,
                    faculty: faculty,
                    visibility: "students", // only resources visible to students
                    q: q,
                    sort: sort,
                    dir: dir
                );
            }
            catch (Exception ex)
            {
                TempData["Error"] = $"Failed to load resources: {ex.Message}";
                items = new List<ResourceItem>();
            }

            ViewBag.Faculty = faculty;
            ViewBag.Query = q;
            ViewBag.Sort = sort;
            ViewBag.Dir = dir;

            return View(items);
        }

        [HttpGet]
        public async Task<IActionResult> DownloadResource(string id)
        {
            var token = HttpContext.Session.GetString("FirebaseToken");
            if (string.IsNullOrEmpty(token))
                return RedirectToAction("Login", "Account");

            var url = await _apiService.GetResourceSignedUrlAsync(token, id, "inline");
            if (string.IsNullOrEmpty(url))
            {
                TempData["Error"] = "Could not generate download URL.";
                return RedirectToAction("Resources");
            }

            return Redirect(url); // redirect student to the signed URL
        }

        [HttpGet]
        public async Task<IActionResult> DownloadFeedback(string requestId)
        {
            var token = HttpContext.Session.GetString("FirebaseToken");

            if (string.IsNullOrEmpty(token))
                return RedirectToAction("Login", "Account");

            var url = await _apiService.GetAnnotatedFileUrlAsync(requestId, token);

            if (string.IsNullOrEmpty(url))
            {
                TempData["Error"] = "No file available";
                return RedirectToAction("StudentDashboard");
            }

            return Redirect(url);
        }
    }
}
