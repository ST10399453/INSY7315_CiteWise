using CiteWise_Web.Models.ServiceRequest;
using CiteWise_Web.Services;
using Microsoft.AspNetCore.Mvc;
using Newtonsoft.Json;
using CiteWise_Web.Models.ViewModels;
using System.Threading.Tasks;
using System.Collections.Generic;

namespace CiteWise_Web.Controllers
{
    public class ConsultantController : Controller
    {
        private readonly ApiService _apiService;

        public ConsultantController(ApiService apiService)
        {
            _apiService = apiService;
        }

        public async Task<IActionResult> ConsultantDashboard()
        {
            string role = HttpContext.Session.GetString("UserRole")?.ToLower();
            string token = HttpContext.Session.GetString("FirebaseToken");
            string consultantId = HttpContext.Session.GetString("UserUid");

            if (role != "consultant" || string.IsNullOrEmpty(token))
                return RedirectToAction("Login", "Account");

            // 🔹 Get unassigned requests
            var unassignedResponse = await _apiService.GetUnassignedRequestsAsync(token);
            var unassignedJson = await unassignedResponse.Content.ReadAsStringAsync();
            var unassigned = JsonConvert.DeserializeObject<List<RequestItem>>(unassignedJson)
                ?? new List<RequestItem>();

            // 🔹 Get requests assigned to this consultant
            var assignedResponse = await _apiService.GetAssignedRequestsAsync(token, consultantId);
            var assignedJson = await assignedResponse.Content.ReadAsStringAsync();
            var assigned = JsonConvert.DeserializeObject<List<RequestItem>>(assignedJson) 
                ?? new List<RequestItem>();

            

            var vm = new ConsultantDashboardViewModel
            {
                Unassigned = unassigned,
                Assigned = assigned
            };

            return View(vm);
        }

        [HttpPost]
        public async Task<IActionResult> AssignToMe(string id)
        {
            string token = HttpContext.Session.GetString("FirebaseToken");
            if (string.IsNullOrEmpty(token))
                return RedirectToAction("Login", "Account");

            // 🔹 Perform self-assignment
            var response = await _apiService.SelfAssignRequestAsync(id, token);

            if (response.IsSuccessStatusCode)
            {
                TempData["Message"] = "Successfully assigned request to yourself.";
            }
            else
            {
                var error = await response.Content.ReadAsStringAsync();
                TempData["Error"] = $"Failed to self-assign: {error}";
            }

            // 🔹 Always reload the dashboard so data refreshes
            return RedirectToAction("ConsultantDashboard");
        }

        public async Task<IActionResult> DownloadFile(string fileId)
        {
            string token = HttpContext.Session.GetString("FirebaseToken");

            if (string.IsNullOrEmpty(token))
                return RedirectToAction("Login", "Account");

            var url = await _apiService.GetFileDownloadUrlAsync(fileId, token);

            if (string.IsNullOrEmpty(url))
            {
                TempData["Error"] = "Download failed.";
                return RedirectToAction("ConsultantDashboard");
            }

            return Redirect(url);
        }

        public async Task<IActionResult> UploadAnnotated(string requestId, IFormFile file)
        {
            if(file == null || file.Length == 0)
            {
                TempData["Error"] = "Please Select a File before uploading";
                return RedirectToAction("ConsultantDashboard");
            }

            string token = HttpContext.Session.GetString("FirebaseToken");

            if (string.IsNullOrEmpty(token))
                return RedirectToAction("Login", "Account");

            var response = await _apiService.UploadAnnotatedFileAsync(requestId, file, token);

            if(response.IsSuccessStatusCode)
            {
                TempData["Message"] = "File uploaded Successfully";
            }
            else
            {
                var error = await response.Content.ReadAsStringAsync();
                TempData["Error"] = $"Failed to upload: {error}";
            }

            return RedirectToAction("ConsultantDashboard");


        }

        [HttpGet]
        public async Task<IActionResult> ViewRequests(string priority = "All")
        {
            var token = HttpContext.Session.GetString("FirebaseToken");
            var role = HttpContext.Session.GetString("UserRole");
            var consultantId = HttpContext.Session.GetString("UserUid");

            if (string.IsNullOrEmpty(token) || string.IsNullOrEmpty(role) || role.ToLower() != "consultant" || string.IsNullOrEmpty(consultantId))
                return RedirectToAction("Login", "Account");

            // Server-side filtered requests
            var resp = await _apiService.GetAssignedRequestsAsync(token, consultantId);

            if (!resp.IsSuccessStatusCode)
            {
                TempData["Error"] = "Failed to load requests from API.";
                return View(new List<RequestItem>());
            }

            var json = await resp.Content.ReadAsStringAsync();
            var items = JsonConvert.DeserializeObject<List<RequestItem>>(json) ?? new List<RequestItem>();

            // Optional: filter by priority
            if (priority != "All")
            {
                items = items.Where(r => string.Equals(r.Priority, priority, StringComparison.OrdinalIgnoreCase)).ToList();
            }

            return View(items);
        }


        [HttpGet]
        public async Task<IActionResult> ServiceRequestDetails(string id)
        {
            var token = HttpContext.Session.GetString("FirebaseToken");
            var role = HttpContext.Session.GetString("UserRole");

            if (string.IsNullOrEmpty(token) || string.IsNullOrEmpty(role) || role.ToLower() != "consultant")
                return RedirectToAction("Login", "Account");

            // Fetch all assigned requests
            var resp = await _apiService.GetAssignedRequestsAsync(token, HttpContext.Session.GetString("UserUid"));
            if (!resp.IsSuccessStatusCode)
                return RedirectToAction("ConsultantDashboard");

            var json = await resp.Content.ReadAsStringAsync();
            var requests = JsonConvert.DeserializeObject<List<ServiceRequestItem>>(json) ?? new List<ServiceRequestItem>();

            var request = requests.FirstOrDefault(r => r.Id == id);
            if (request == null)
                return RedirectToAction("ConsultantDashboard");

            return View(request);
        }




    }
}

