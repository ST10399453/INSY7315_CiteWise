using CiteWise_Web.Models;
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

            if (model.Role == "Student")
                return RedirectToAction("Student", new { uid = model.Uid, token = model.IdToken });
            else if (model.Role == "Consultant")
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

            var profile = new UserProfile
            {
                Role = "Student",
                Language = model.Language,
                Institution = model.Institution,
                FieldOfStudy = model.FieldOfStudy
            };

            //await _firebaseService.SaveUserProfileAsync(model.Uid, model.IdToken, profile);
            var updates = new Dictionary<string, object>
            {
                { "Role", "Student" },
                { "Language", model.Language },
                { "Institution", model.Institution },
                { "FieldOfStudy", model.FieldOfStudy }
            };

            await _firebaseService.UpdateUserProfileAsync(model.Uid, model.IdToken, updates);


            return RedirectToAction("StudentDashboard", "Dashboard"); // student dashboard
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

            var profile = new UserProfile
            {
                Role = "Consultant",
                Language = model.Language,
                Specialisation = model.Specialisation
            };

            //await _firebaseService.SaveUserProfileAsync(model.Uid, model.IdToken, profile);
            var updates = new Dictionary<string, object>
            {
                { "Role", "Consultant" },
                { "Language", model.Language },
                { "Specialisation", model.Specialisation }
            };

            await _firebaseService.UpdateUserProfileAsync(model.Uid, model.IdToken, updates);


            return RedirectToAction("ConsultantDashboard", "Dashboard");
        }
    }
}
