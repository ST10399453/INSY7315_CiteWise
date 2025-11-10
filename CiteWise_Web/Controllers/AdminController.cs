using CiteWise_Web.Models;
using CiteWise_Web.Models.ServiceRequest;
using CiteWise_Web.Services;
using Microsoft.AspNetCore.Mvc;
using Newtonsoft.Json;

namespace CiteWise_Web.Controllers
{
    public class AdminController : Controller
    {
        private readonly ApiService _apiService;

        public AdminController(ApiService apiService)
        {
            _apiService = apiService;
        }

        // Displays unassigned requests and allows assignment
        public async Task<IActionResult> AdminDashboard()
        {
            string token = HttpContext.Session.GetString("FirebaseToken");
            string role = HttpContext.Session.GetString("UserRole")?.ToLower();

            if (string.IsNullOrEmpty(token) || role != "admin")
                return RedirectToAction("Login", "Account");

            var unassignedResponse = await _apiService.GetUnassignedRequestsAsync(token);
            var unassignedJson = await unassignedResponse.Content.ReadAsStringAsync();
            var unassigned = JsonConvert.DeserializeObject<List<ServiceRequestItem>>(unassignedJson)
                             ?? new List<ServiceRequestItem>();

            // 🔹 Simulated consultant list for now (replace later with Firebase pull)
            var consultants = new List<ConsultantItem>
            {
                new ConsultantItem {Id = "consultant1UID", Name = "John Smith"},
                new ConsultantItem {Id = "consultant2UID", Name = "Sarah Lee"}
            };

            ViewBag.Consultants = consultants;

            return View(unassigned);
        }

        // Handles the assignment action
        [HttpPost]
        public async Task<IActionResult> Assign(string requestId, string consultantId)
        {
            string token = HttpContext.Session.GetString("FirebaseToken");
            if (string.IsNullOrEmpty(token))
                return RedirectToAction("Login", "Account");

            var response = await _apiService.AssignRequestToConsultantAsync(requestId, consultantId, token);

            if (response.IsSuccessStatusCode)
            {
                TempData["Message"] = "✅ Successfully assigned request!";
            }
            else
            {
                var error = await response.Content.ReadAsStringAsync();
                TempData["Error"] = $"❌ Assignment failed: {error}";
            }

            return RedirectToAction("AdminDashboard");
        }
    }
}
