using CiteWise_Web.Models;
using CiteWise_Web.Services;
using Microsoft.AspNetCore.Mvc;

namespace CiteWise_Web.Controllers
{
    public class AccountController : Controller
    {
        private readonly FirebaseService _firebaseService;

        public AccountController(FirebaseService firebaseService)
        {
            _firebaseService = firebaseService;
        }

        // ----------------------
        // REGISTER
        // ----------------------
        [HttpGet]
        public IActionResult Register()
        {
            return View();
        }

        [HttpPost]
        public async Task<IActionResult> Register(UserModel model)
        {
            if (!ModelState.IsValid)
                return View(model);

            // 1. Register user in Firebase Auth
            var authResponse = await _firebaseService.RegisterUserAsync(model.Email, model.Password);

            if (authResponse == null || string.IsNullOrEmpty(authResponse.LocalId))
            {
                ModelState.AddModelError("", "Registration failed. Try again.");
                return View(model);
            }

            // 2. Save basic user profile in Firebase DB
            var profile = new UserProfile
            {
                FirstName = model.FirstName,
                Surname = model.Surname,
                Email = model.Email,
                Role = "Pending" // default until onboarding
            };

            await _firebaseService.SaveUserProfileAsync(authResponse.LocalId, authResponse.IdToken, profile);

            // 3. Redirect to Onboarding (first login)
            return RedirectToAction("SelectRole", "Onboarding", new { uid = authResponse.LocalId, token = authResponse.IdToken });

        }

        // ----------------------
        // LOGIN
        // ----------------------
        [HttpGet]
        public IActionResult Login()
        {
            return View();
        }

        [HttpPost]
        public async Task<IActionResult> Login(LoginModel model)
        {
            if (!ModelState.IsValid)
                return View(model);

            var authResponse = await _firebaseService.LoginUserAsync(model.Email, model.Password);

            if (authResponse == null || string.IsNullOrEmpty(authResponse.LocalId))
            {
                ModelState.AddModelError("", "Login failed. Check your email and password.");
                return View(model);
            }

            // ✅ Fetch profile from DB
            var profile = await _firebaseService.GetUserProfileAsync(authResponse.LocalId, authResponse.IdToken);

            if (profile == null || string.IsNullOrEmpty(profile.Role) || profile.Role == "Pending")
            {
                // No profile yet → redirect to SelectRole
                return RedirectToAction("SelectRole", "Onboarding", new { uid = authResponse.LocalId, token = authResponse.IdToken });
            }

            // ✅ Profile exists → redirect by role
            if (profile.Role == "Student")
                return RedirectToAction("StudentDashboard", "Dashboard");
            else if (profile.Role == "Consultant")
                return RedirectToAction("ConsultantDashboard", "Dashboard");

            // fallback
            return RedirectToAction("Login");
        }

    }
}
