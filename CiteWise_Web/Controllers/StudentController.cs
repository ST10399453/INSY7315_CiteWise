using CiteWise_Web.Models.ServiceRequest;
using CiteWise_Web.Services;
using Google.Cloud.Firestore;
using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.AspNetCore.Mvc;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;
using System.Data;

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
        public IActionResult StudentDashboard()
        {
            string role = HttpContext.Session.GetString("UserRole");
            if (string.IsNullOrEmpty(role) || role.ToLower() != "student")
            {
                return RedirectToAction("Login", "Account");
            }

            return View();
        }


        [HttpGet]
        public IActionResult StudentServiceRequest()
        {
            string firebaseToken = HttpContext.Session.GetString("FirebaseToken");
            string role = HttpContext.Session.GetString("UserRole");

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

            // Get Firebase token from session
            string firebaseToken = HttpContext.Session.GetString("FirebaseToken");
            if (string.IsNullOrEmpty(firebaseToken))
            {
                return RedirectToAction("Login", "Account");
            }

            string userRole = HttpContext.Session.GetString("UserRole");
            if (userRole != "student")
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
            formData.Add(new StringContent(model.DocName ?? ""), "documentName");
            formData.Add(new StringContent(model.SelectedService ?? ""), "serviceType");
            formData.Add(new StringContent(model.AdditionalInfo ?? ""), "description");
            formData.Add(new StringContent(model.Urgency ?? ""), "priority");

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

            // Get API response content for debugging
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

            // Optional: remove the flag so refresh won't show the page again
            TempData.Remove("RequestSubmitted");

            return View();
        }
    }
}
