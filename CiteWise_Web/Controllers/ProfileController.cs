using CiteWise_Web.Models.Account;
using CiteWise_Web.Models.Profile;
using CiteWise_Web.Models.ServiceRequest;
using CiteWise_Web.Services;
using Microsoft.AspNetCore.Mvc;
using Newtonsoft.Json;

namespace CiteWise_Web.Controllers
{
    public class ProfileController : Controller
    {
        private readonly FirebaseService _firebaseService;
        private readonly ApiService _apiService;

        public ProfileController(FirebaseService firebaseService, ApiService apiService)
        {
            _firebaseService = firebaseService;
            _apiService = apiService;
        }

        // =======================
        // ADMIN PROFILE
        // =======================
        [HttpGet]
        public async Task<IActionResult> Admin()
        {
            string token = HttpContext.Session.GetString("FirebaseToken");
            string uid = HttpContext.Session.GetString("UserUid");
            string role = HttpContext.Session.GetString("UserRole")?.ToLower();

            if (string.IsNullOrEmpty(token) || role != "admin")
                return RedirectToAction("Login", "Account");

            var vm = await BuildProfileViewModelAsync(uid, token, "admin");

            // Admin stats can be from all requests; here we use unassigned + assigned to get a feel.
            // Adjust this part to match your API surface.
            try
            {
                var unassignedResp = await _apiService.GetUnassignedRequestsAsync(token);
                var unassignedJson = await unassignedResp.Content.ReadAsStringAsync();
                var unassigned = JsonConvert.DeserializeObject<List<ServiceRequestItem>>(unassignedJson)
                                 ?? new List<ServiceRequestItem>();

                // If you have an "all requests" endpoint, use it here instead.
                int total = unassigned.Count;
                int completed = unassigned.Count(r => string.Equals(r.Status, "completed", StringComparison.OrdinalIgnoreCase));
                int inProgress = unassigned.Count(r => string.Equals(r.Status, "assigned", StringComparison.OrdinalIgnoreCase) ||
                                                       string.Equals(r.Status, "in_progress", StringComparison.OrdinalIgnoreCase));
                int pending = total - completed - inProgress;

                vm.Stats = new ProfileStatsViewModel
                {
                    TotalRequests = total,
                    Completed = completed,
                    InProgress = inProgress,
                    Pending = pending
                };
            }
            catch
            {
                // Leave default zeros
            }

            vm.IsAdmin = true;
            return View("AdminProfileSettings", vm);
        }

        // =======================
        // CONSULTANT PROFILE
        // =======================
        [HttpGet]
        public async Task<IActionResult> Consultant()
        {
            string token = HttpContext.Session.GetString("FirebaseToken");
            string uid = HttpContext.Session.GetString("UserUid");
            string role = HttpContext.Session.GetString("UserRole")?.ToLower();

            if (string.IsNullOrEmpty(token) || role != "consultant")
                return RedirectToAction("Login", "Account");

            var vm = await BuildProfileViewModelAsync(uid, token, "consultant");

            // Stats for consultant: use requests assigned to this consultant
            try
            {
                var assignedResp = await _apiService.GetAssignedRequestsAsync(token, uid);
                var assignedJson = await assignedResp.Content.ReadAsStringAsync();
                var assigned = JsonConvert.DeserializeObject<List<ServiceRequestItem>>(assignedJson)
                               ?? new List<ServiceRequestItem>();

                int total = assigned.Count;
                int completed = assigned.Count(r => EqualsStatus(r.Status, "completed", "complete", "done"));
                int inProgress = assigned.Count(r => EqualsStatus(r.Status, "assigned", "in_progress", "inprogress"));
                int pending = assigned.Count(r => EqualsStatus(r.Status, "pending", "submitted", "awaiting", "awaiting_review"));

                vm.Stats = new ProfileStatsViewModel
                {
                    TotalRequests = total,
                    Completed = completed,
                    InProgress = inProgress,
                    Pending = pending
                };
            }
            catch
            {
                // Leave default zeros
            }

            vm.IsConsultant = true;
            return View("ConsultantProfileSettings", vm);
        }

        // =======================
        // STUDENT PROFILE
        // =======================
        [HttpGet]
        public async Task<IActionResult> Student()
        {
            string token = HttpContext.Session.GetString("FirebaseToken");
            string uid = HttpContext.Session.GetString("UserUid");
            string role = HttpContext.Session.GetString("UserRole")?.ToLower();

            if (string.IsNullOrEmpty(token) || role != "student")
                return RedirectToAction("Login", "Account");

            var vm = await BuildProfileViewModelAsync(uid, token, "student");

            // Stats for student: re-use GetMyRequestsAsync like your StudentDashboard
            try
            {
                var resp = await _apiService.GetMyRequestsAsync(token, null);
                if (resp.IsSuccessStatusCode)
                {
                    var json = await resp.Content.ReadAsStringAsync();
                    var items = JsonConvert.DeserializeObject<List<RequestItem>>(json)
                                ?? new List<RequestItem>();

                    int total = items.Count;
                    int completed = items.Count(r => EqualsStatus(r.Status, "completed", "complete", "done"));
                    int inProgress = items.Count(r => EqualsStatus(r.Status, "assigned", "in_progress", "inprogress"));
                    int pending = items.Count(r => EqualsStatus(r.Status, "pending", "submitted", "awaiting", "awaiting_review"));

                    vm.Stats = new ProfileStatsViewModel
                    {
                        TotalRequests = total,
                        Completed = completed,
                        InProgress = inProgress,
                        Pending = pending
                    };
                }
            }
            catch
            {
                // Leave default zeros
            }

            vm.IsStudent = true;
            return View("StudentProfileSettings", vm);
        }

        // =======================
        // Helpers
        // =======================
        private async Task<ProfileOverviewViewModel> BuildProfileViewModelAsync(
            string uid,
            string idToken,
            string role)
        {
            var vm = new ProfileOverviewViewModel
            {
                Uid = uid,
                Role = FirstUpper(role),
                Language = "English" // default; Firebase may override
            };

            UserProfile? profile = null;
            try
            {
                profile = await _firebaseService.GetUserProfileAsync(uid, idToken);
            }
            catch
            {
                // ignore
            }

            if (profile != null)
            {
                var fullName = $"{profile.firstName} {profile.surname}".Trim();
                vm.Name = string.IsNullOrWhiteSpace(fullName) ? profile.email : fullName;
                vm.Email = profile.email;
                vm.Institution = profile.institution;
                vm.FieldOfStudy = profile.fieldOfStudy;
                vm.Specialisation = profile.specialisation;
                vm.Role = FirstUpper(profile.role ?? role);
                vm.Language = string.IsNullOrWhiteSpace(profile.language) ? "English" : profile.language;
                // If you store photoUrl in UserProfile, map it here.
                // vm.AvatarUrl = profile.photoUrl;
            }
            else
            {
                vm.Name = "User";
                vm.Email = "you@example.com";
            }

            return vm;
        }

        private static bool EqualsStatus(string? status, params string[] candidates)
        {
            if (string.IsNullOrWhiteSpace(status)) return false;
            return candidates.Any(c =>
                string.Equals(status, c, StringComparison.OrdinalIgnoreCase));
        }

        private static string FirstUpper(string? value)
        {
            if (string.IsNullOrWhiteSpace(value)) return string.Empty;
            value = value.Trim().ToLowerInvariant();
            return char.ToUpper(value[0]) + value[1..];
        }
    }
}
