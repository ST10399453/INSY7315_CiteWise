using CiteWise_Web.Models;
using CiteWise_Web.Models.Account;
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
        public async Task<IActionResult> Register(RegisterModel model)
        {
            if (!ModelState.IsValid)
                return View(model);

            var authResponse = await _firebaseService.RegisterUserAsync(model.Email, model.Password);

            if (authResponse == null || string.IsNullOrEmpty(authResponse.LocalId))
            {
                ModelState.AddModelError("", "Registration failed. Try again.");
                return View(model);
            }

            var profile = new UserProfile
            {
                Uid = authResponse.LocalId,
                FirstName = model.FirstName,
                Surname = model.Surname,
                Email = model.Email,
                Role = "Pending"
            };

            await _firebaseService.SaveUserProfileAsync(profile.Uid, authResponse.IdToken, profile);

            return RedirectToAction("SelectRole", "Onboarding", new { uid = authResponse.LocalId, token = authResponse.IdToken });


            //optional chnage for redirecting and saving token
            ////////
            //HttpContext.Session.SetString("Uid", profile.Uid);

            //return RedirectToAction("SelectRole", "Onboarding");
            ////
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

            var profile = await _firebaseService.GetUserProfileAsync(authResponse.LocalId, authResponse.IdToken);

            if (profile == null || string.IsNullOrEmpty(profile.Role) || profile.Role == "Pending")
            {
                // No profile yet → redirect to SelectRole
                return RedirectToAction("SelectRole", "Onboarding", new { uid = authResponse.LocalId, token = authResponse.IdToken });
            }

            HttpContext.Session.SetString("UserName", profile.FirstName);
            //MIGHT NEED TO SUE THESE LATER
            //HttpContext.Session.SetString("UserEmail", profile.Email);
            //HttpContext.Session.SetString("UserRole", profile.Role);

            // ✅ Profile exists → redirect by role
            if (profile.Role == "Student")
                return RedirectToAction("StudentDashboard", "Student");
            else if (profile.Role == "Consultant")
                return RedirectToAction("ConsultantDashboard", "Consultant");

            // fallback
            return RedirectToAction("Login");
        }

        [HttpGet]
        public IActionResult ForgotPassword()
        {
            return View();
        }

        //FORGOT PASSWORD  
        [HttpPost]
        public async Task<IActionResult> ForgotPassword(string email)
        {
            if (string.IsNullOrEmpty(email))
            {
                ModelState.AddModelError("", "Email is required.");
                return View();
            }

            try
            {
                await _firebaseService.SendPasswordResetEmailAsync(email);
                ViewBag.Message = "Password reset link has been sent to your email.";
            }
            catch (Exception ex)
            {
                ModelState.AddModelError("", $"Error: {ex.Message}");
            }

            return View();
        }

        // ----------------------
        // LOGOUT
        // ----------------------
        //ADDED LOGOUT FEATUERE HERE FOR NOW, FOR TESTING
        public IActionResult Logout()
        {
            // Clear all session values
            HttpContext.Session.Clear();

            // Redirect to home (or login page)
            return RedirectToAction("Index", "Home");
        }
        //ADDED LOGOUT FEATUERE HERE FOR NOW, FOR TESTING

    }
}
