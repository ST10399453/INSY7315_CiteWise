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

            var consultants = await _firebaseService.GetAllConsultantsAsync();
            var students = await _firebaseService.GetStudentsAsync();

            // 👇 Split consultants into pending and approved
            ViewBag.Pending = consultants.Where(c => !c.IsApproved).ToList();
            ViewBag.Approved = consultants.Where(c => c.IsApproved).ToList();
            ViewBag.Consultants = consultants;
            ViewBag.Students = students;

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

        // =======================
        // MANAGE CONSULTANTS PAGE
        // =======================
        public async Task<IActionResult> ManageConsultants()
        {
            string token = HttpContext.Session.GetString("FirebaseToken");
            string role = HttpContext.Session.GetString("UserRole")?.ToLower();

            if (string.IsNullOrEmpty(token) || role != "admin")
                return RedirectToAction("Login", "Account");

            // Get all consultants
            var consultants = await _firebaseService.GetAllConsultantsAsync();

            ViewBag.Pending = consultants.Where(c => !c.IsApproved).ToList();
            ViewBag.Approved = consultants.Where(c => c.IsApproved).ToList();
            ViewBag.Consultants = consultants;

            // 👇 Fetch unassigned service requests
            var unassignedResponse = await _apiService.GetUnassignedRequestsAsync(token);
            var unassignedJson = await unassignedResponse.Content.ReadAsStringAsync();
            var unassigned = JsonConvert.DeserializeObject<List<ServiceRequestItem>>(unassignedJson)
                             ?? new List<ServiceRequestItem>();

            return View(unassigned); // 👈 Pass unassigned requests as model
        }


        [HttpPost]
        [ValidateAntiForgeryToken]
        public async Task<IActionResult> ApproveConsultant(string id)
        {
            string role = HttpContext.Session.GetString("UserRole")?.ToLower();
            if (role != "admin")
                return RedirectToAction("Login", "Account");

            bool ok = await _firebaseService.ApproveConsultantAsync(id);
            TempData[ok ? "Message" : "Error"] = ok ? "✅ Consultant approved!" : "❌ Approval failed.";

            return RedirectToAction("ManageConsultants");
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public async Task<IActionResult> RejectConsultant(string id)
        {
            string role = HttpContext.Session.GetString("UserRole")?.ToLower();
            if (role != "admin")
                return RedirectToAction("Login", "Account");

            bool ok = await _firebaseService.DeleteUserAsync(id);

            TempData[ok ? "Message" : "Error"] = ok
                ? "❌ Consultant rejected and account deleted."
                : "⚠️ Failed to reject consultant.";

            return RedirectToAction("AdminDashboard");
        }


    }
}
