using CiteWise_Web.Models;
using CiteWise_Web.Models.ServiceRequest;
using CiteWise_Web.Models.Resources;
using CiteWise_Web.Services;
using Microsoft.AspNetCore.Mvc;
using Newtonsoft.Json;

namespace CiteWise_Web.Controllers
{
    public class AdminController : Controller
    {
        private readonly ApiService _apiService;
        private readonly FirebaseService _firebaseService;

        public AdminController(ApiService apiService, FirebaseService firebaseService)
        {
            _apiService = apiService;
            _firebaseService = firebaseService;
        }

        // =======================
        // REQUESTS: Unassigned + Assign
        // =======================
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

            // Simulated consultant list (replace with Firebase)
            var consultants = await _firebaseService.GetConsultantAsync();


            ViewBag.Consultants = consultants;

            return View(unassigned);
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public async Task<IActionResult> Assign(string requestId, string consultantId)
        {
            string token = HttpContext.Session.GetString("FirebaseToken");
            if (string.IsNullOrEmpty(token))
                return RedirectToAction("Login", "Account");

            var response = await _apiService.AssignRequestToConsultantAsync(requestId, consultantId, token);

            if (response.IsSuccessStatusCode)
                TempData["Message"] = "✅ Successfully assigned request!";
            else
            {
                var error = await response.Content.ReadAsStringAsync();
                TempData["Error"] = $"❌ Assignment failed: {error}";
            }

            return RedirectToAction("AdminDashboard");
        }

        // =======================
        // RESOURCES: List, Upload, Download(Signed URL), Delete
        // =======================
        public async Task<IActionResult> Resources(string? faculty = null, string? q = null, string sort = "date", string dir = "desc")
        {
            string token = HttpContext.Session.GetString("FirebaseToken");
            string role = HttpContext.Session.GetString("UserRole")?.ToLower();

            if (string.IsNullOrEmpty(token) || role != "admin")
                return RedirectToAction("Login", "Account");

            var items = await _apiService.ListResourcesAsync(
                token,
                faculty: faculty,
                visibility: "all", // admin can see all
                q: q,
                sort: sort,
                dir: dir
            );

            ViewBag.Faculty = faculty;
            ViewBag.Query = q;
            ViewBag.Sort = sort;
            ViewBag.Dir = dir;

            return View(items);
        }

        public IActionResult AddResources()
        {
            string role = HttpContext.Session.GetString("UserRole")?.ToLower();
            if (role != "admin")
                return RedirectToAction("Login", "Account");

            return View(); // returns AddResources.cshtml
        }


        [HttpPost]
        [ValidateAntiForgeryToken]
        public async Task<IActionResult> UploadResource(CreateResourceRequest model)
        {
            string token = HttpContext.Session.GetString("FirebaseToken");
            string role = HttpContext.Session.GetString("UserRole")?.ToLower();
            if (string.IsNullOrEmpty(token) || role != "admin")
                return RedirectToAction("Login", "Account");

            try
            {
                var created = await _apiService.UploadResourceAsync(token, model);
                TempData["Message"] = $"Uploaded “{created?.Name ?? model.Name}”.";
            }
            catch (Exception ex)
            {
                TempData["Error"] = $"Upload failed: {ex.Message}";
            }

            return RedirectToAction("Resources");
        }

        [HttpGet]
        public async Task<IActionResult> DownloadResource(string id, string disposition = "inline")
        {
            string token = HttpContext.Session.GetString("FirebaseToken");
            if (string.IsNullOrEmpty(token))
                return RedirectToAction("Login", "Account");

            var url = await _apiService.GetResourceSignedUrlAsync(token, id, disposition);
            if (string.IsNullOrEmpty(url))
            {
                TempData["Error"] = "Could not generate download URL.";
                return RedirectToAction("Resources");
            }

            // Redirect browser to signed URL (Cloudflare R2)
            return Redirect(url);
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public async Task<IActionResult> DeleteResource(string id)
        {
            string token = HttpContext.Session.GetString("FirebaseToken");
            string role = HttpContext.Session.GetString("UserRole")?.ToLower();
            if (string.IsNullOrEmpty(token) || role != "admin")
                return RedirectToAction("Login", "Account");

            var ok = await _apiService.DeleteResourceAsync(token, id);
            TempData[ok ? "Message" : "Error"] = ok ? "Resource deleted." : "Delete failed.";

            return RedirectToAction("Resources");
        }
    }
}
