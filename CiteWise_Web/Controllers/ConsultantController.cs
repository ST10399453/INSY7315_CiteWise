using CiteWise_Web.Models.ServiceRequest;
using CiteWise_Web.Services;
using Microsoft.AspNetCore.Mvc;
using Newtonsoft.Json;
using CiteWise_Web.Models.ViewModels;
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


            var unassignedResponse = await _apiService.GetUnassignedRequestsAsync(token);
            var unassignedJson = await unassignedResponse.Content.ReadAsStringAsync();
            var unassigned = JsonConvert.DeserializeObject<List<ServiceRequestItem>>(unassignedJson)
                ?? new List<ServiceRequestItem>();

            var assignedResponse = await _apiService.GetAssignedRequestsAsync(token, consultantId);
            var assignedJson = await assignedResponse.Content.ReadAsStringAsync();
            var assigned = JsonConvert.DeserializeObject<List<ServiceRequestItem>>(assignedJson)
                ?? new List<ServiceRequestItem>();



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

            var response = await _apiService.SelfAssignRequestAsync(id, token);

            if (response.IsSuccessStatusCode)
            {
                TempData["Message"] = "Successfully assigned request to yourself";
            }
            else
            {
                var error = await response.Content.ReadAsStringAsync();
                TempData["Error"] = $"Failed to self-assign: {error}";
            }

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

    }
}