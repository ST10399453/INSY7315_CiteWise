using CiteWise_Web.Models.ServiceRequest;
using CiteWise_Web.Services; 
using Microsoft.AspNetCore.Mvc;
using Newtonsoft.Json;

namespace CiteWise_Web.Controllers
{
    public class ConsultantController : Controller
    {
        private readonly ApiService _apiService;

        //Access the firebase service 
        public ConsultantController(ApiService apiService)
        {
            _apiService = apiService;
        }


        public async Task<IActionResult> ConsultantDashboard()
        {

            string role = HttpContext.Session.GetString("UserRole");
            string token = HttpContext.Session.GetString("FirebaseToken");
            Console.WriteLine($"Firebase token in session: {token}");
            string uid = HttpContext.Session.GetString("UserUid");

            if (role != "consultant" || string.IsNullOrEmpty(token))
                return RedirectToAction("Login", "Account");

            var response = await _apiService.GetRequestsAsync(token,role);

            if(!response.IsSuccessStatusCode)
            {
                ViewBag.Error = "Failed to load requests from API";
                return View(new List<ServiceRequestItem>());
            }

            var json = await response.Content.ReadAsStringAsync();
            ViewBag.DebugJson = json;
            var requests = JsonConvert.DeserializeObject<List<ServiceRequestItem>>(json)
                ?? new List<ServiceRequestItem>();

            return View(requests);

           
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

    }
}
