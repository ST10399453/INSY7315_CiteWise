using CiteWise_Web.Models;
using CiteWise_Web.Models.Account;
using CiteWise_Web.Services;
using FirebaseAdmin.Auth;
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
                return RedirectToAction("SelectRole", "Onboarding", new { uid = authResponse.LocalId, token = authResponse.IdToken });
            }

            HttpContext.Session.SetString("UserName", profile.FirstName);
            HttpContext.Session.SetString("UserUid", profile.Uid);
            HttpContext.Session.SetString("UserRole", profile.Role);

            if (profile.Role == "student")
                return RedirectToAction("StudentDashboard", "Student");
            else if (profile.Role == "consultant")
                return RedirectToAction("ConsultantDashboard", "Consultant");

            return RedirectToAction("Login");
        }

        public async Task<IActionResult> GoogleLogin([FromBody] TokenRequest req)
        {

            try
            {
                FirebaseToken decodedToken = await FirebaseAuth.DefaultInstance.VerifyIdTokenAsync(req.Token);
                string uid = decodedToken.Uid;

                var user = await FirebaseAuth.DefaultInstance.GetUserAsync(decodedToken.Uid);

                var profile = await _firebaseService.GetUserProfileAsync(uid, req.Token);

                if(profile == null)
                {
                    var newProfile = new UserProfile
                    {
                        Uid = uid,
                        FirstName = user.DisplayName?.Split(' ').FirstOrDefault() ?? "New",
                        Surname = user.DisplayName?.Split(' ').Skip(1).FirstOrDefault() ?? "User",
                        Email = user.Email ?? "",
                        Role = "Pending" 
                   };

                    await _firebaseService.SaveUserProfileAsync(uid, req.Token, newProfile);
                    profile = newProfile;
                }

                HttpContext.Session.SetString("UserUid", uid);
                HttpContext.Session.SetString("UserEmail", user.Email ?? "");
                HttpContext.Session.SetString("UserName", profile.FirstName ?? user.DisplayName ?? "User");
                HttpContext.Session.SetString("UserRole", profile.Role ?? "Pending");


                string redirectUrl = profile.Role switch
                {
                    "Consultant" => Url.Action("ConsultantDashboard", "Consultant")!,
                    "Student" => Url.Action("StudentDashboard", "Student")!,
                    _ => Url.Action("SelectRole", "Onboarding", new { uid = profile.Uid, token = req.Token })!
                };


                return Ok(new { success = true, redirectUrl });
            }
            catch (Exception ex)
            {
                return Unauthorized(new { success = false, message = ex.Message });
            }
        }

       

        // ----------------------
        // FORGOT PASSWORD
        // ----------------------
        [HttpGet]
        public IActionResult ForgotPassword()
        {
            return View();
        }

        [HttpPost]
        public async Task<IActionResult> ForgotPassword(ForgotPasswordModel model)
        {
            if (!ModelState.IsValid || string.IsNullOrEmpty(model.Email))
            {
                ModelState.AddModelError("Email", "Email is required.");
                return View(model);
            }

            try
            {
                await _firebaseService.SendPasswordResetEmailAsync(model.Email);

                ViewBag.SuccessMessage = "Password reset link sent successfully!";

            }
            catch (Exception ex)
                
            {
                ModelState.AddModelError("Email", $"Error sending reset link: {ex.Message}");
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
