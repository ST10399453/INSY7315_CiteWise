using CiteWise_Web.Models.Account;
using CiteWise_Web.Services;
using Microsoft.AspNetCore.Mvc;

namespace CiteWise_Web.Controllers
{
    public class OnboardingController : Controller
    {
        private readonly FirebaseService _firebaseService;

        public OnboardingController(FirebaseService firebaseService)
        {
            _firebaseService = firebaseService;
        }

        // ----------------------
        // GET Pending Approval
        // ----------------------
        [HttpGet]
        public IActionResult PendingApproval()
        {
            return View();
        }

        // ----------------------
        // GET Role Select
        // ----------------------
        [HttpGet]
        public IActionResult SelectRole(string uid, string token)
        {
            var model = new RoleSelectionModel
            {
                Uid = uid,
                IdToken = token
            };
            return View(model);
        }


        // ----------------------
        // POST Role Select
        // ----------------------
        [HttpPost]
        [ValidateAntiForgeryToken]
        public IActionResult SelectRole(RoleSelectionModel model)
        {
            if (!ModelState.IsValid)
                return View(model);

            if (model.role == "student")
                return RedirectToAction("Student", new { uid = model.Uid, token = model.IdToken });
            else if (model.role == "consultant")
                return RedirectToAction("Consultant", new { uid = model.Uid, token = model.IdToken });

            // fallback
            ModelState.AddModelError("", "Please select a role.");
            return View(model);
        }

        // ----------------------
        // GET Student Onboarding
        // ----------------------
        [HttpGet]
        public IActionResult Student(string uid, string token)
        {
            var model = new StudentOnboardingModel { Uid = uid, IdToken = token };
            return View(model);
        }

        // ----------------------
        // POST Student Onboarding
        // ----------------------
        [HttpPost]
        [ValidateAntiForgeryToken]
        public async Task<IActionResult> Student(StudentOnboardingModel model)
        {
            if (!ModelState.IsValid)
                return View(model);

            //var profile = new UserProfile
            //{
            //    Role = "Student",
            //    Language = model.Language,
            //    Institution = model.Institution,
            //    FieldOfStudy = model.FieldOfStudy
            //};

            var updates = new Dictionary<string, object>
            {
                { "role", "student" },
                //{ "Language", model.Language },
                { "institution", model.institution },
                { "fieldOfStudy", model.fieldOfStudy }
            };

            await _firebaseService.UpdateUserProfileAsync(model.Uid, model.IdToken, updates);

            var profile = await _firebaseService.GetUserProfileAsync(model.Uid, model.IdToken);

            // ✅ Set session so name appears in topbar
            HttpContext.Session.SetString("UserName", profile?.firstName ?? "User");
            HttpContext.Session.SetString("UserUid", profile?.uid ?? model.Uid);
            HttpContext.Session.SetString("UserRole", "student");
            HttpContext.Session.SetString("FirebaseToken", model.IdToken);

            return RedirectToAction("StudentDashboard", "Student");
            // student dashboard
        }

        // ----------------------
        // GET Consultant Onboarding
        // ----------------------
        [HttpGet]
        public IActionResult Consultant(string uid, string token)
        {
            var model = new ConsultantOnboardingModel { Uid = uid, IdToken = token };
            return View(model);
        }

        // ----------------------
        // POST Consultant Onboarding
        // ----------------------
        [HttpPost]
        [ValidateAntiForgeryToken]
        public async Task<IActionResult> Consultant(ConsultantOnboardingModel model)
        {
            if (!ModelState.IsValid)
                return View(model);

            //var profile = new UserProfile
            //{
            //    Role = "Consultant",
            //    Language = model.Language,
            //    Specialisation = model.Specialisation
            //};

            var updates = new Dictionary<string, object>
            {
                { "role", "consultant" },
                //{ "Language", model.Language },
                { "specialisation", model.specialisation },
                { "isApproved", false }
            };

            await _firebaseService.UpdateUserProfileAsync(model.Uid, model.IdToken, updates);

            var profile = await _firebaseService.GetUserProfileAsync(model.Uid, model.IdToken);

            HttpContext.Session.SetString("UserName", profile?.firstName ?? "User");
            HttpContext.Session.SetString("UserUid", profile?.uid ?? model.Uid);
            HttpContext.Session.SetString("UserRole", "consultant");
            HttpContext.Session.SetString("FirebaseToken", model.IdToken);

            if (profile?.isApproved == false)
            {
                return RedirectToAction("PendingApproval", "Onboarding");
            }

            return RedirectToAction("ConsultantDashboard", "Consultant");

        }
    }
}

//References 

//Microsoft, 2023. Asynchronous programming with async and await.
//[online] Avaliable at <https://learn.microsoft.com/en-us/dotnet/csharp/asynchronous-programming/?utm_source=chatgpt.com>
//Accessed 20 September 2025]