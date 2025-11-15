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

            var authResponse = await _firebaseService.RegisterUserAsync(model.email, model.Password);

            if (authResponse == null || string.IsNullOrEmpty(authResponse.LocalId))
            {
                ModelState.AddModelError("", "Registration failed. Try again.");
                return View(model);
            }

            var profile = new UserProfile
            {
                uid = authResponse.LocalId,
                firstName = model.firstName,
                surname = model.surname,
                email = model.email,
                role = "Pending"
            };

            await _firebaseService.SaveUserProfileAsync(profile.uid, authResponse.IdToken, profile);

            HttpContext.Session.SetString("UserName", model.firstName);
            HttpContext.Session.SetString("UserSurname", model.surname);
            HttpContext.Session.SetString("UserUid", authResponse.LocalId);
            HttpContext.Session.SetString("UserRole", "Pending");
            HttpContext.Session.SetString("FirebaseToken", authResponse.IdToken);

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
        [ValidateAntiForgeryToken]
        public async Task<IActionResult> Login(LoginModel model)
        {
            // If basic model validation fails (required fields / email format)
            if (!ModelState.IsValid)
            {
                return View(model);
            }

            // Try authenticate with Firebase
            var authResponse = await _firebaseService.LoginUserAsync(model.email, model.Password);

            // If Firebase returns null or no LocalId, login failed
            if (authResponse == null || string.IsNullOrEmpty(authResponse.LocalId))
            {
                // Model-level error -> shown in validation summary
                ModelState.AddModelError(string.Empty, "Login failed. Check your email and password.");
                return View(model);
            }

            // Try get profile
            var profile = await _firebaseService.GetUserProfileAsync(authResponse.LocalId, authResponse.IdToken);

            // If no profile, send user to role selection onboarding
            if (profile == null)
            {
                return RedirectToAction(
                    "SelectRole",
                    "Onboarding",
                    new { uid = authResponse.LocalId, token = authResponse.IdToken }
                );
            }

            // If role is missing or still pending, send to onboarding
            if (string.IsNullOrEmpty(profile.role) || profile.role == "Pending")
            {
                return RedirectToAction(
                    "SelectRole",
                    "Onboarding",
                    new { uid = profile.uid, token = authResponse.IdToken }
                );
            }

            // If consultant is not yet approved
            if (profile.role == "consultant" && profile.isApproved != true)
            {
                HttpContext.Session.SetString("UserName", profile.firstName ?? "User");
                HttpContext.Session.SetString("UserSurname", profile.surname ?? "");
                HttpContext.Session.SetString("UserUid", profile.uid);
                HttpContext.Session.SetString("UserRole", "consultant");
                HttpContext.Session.SetString("FirebaseToken", authResponse.IdToken);

                return RedirectToAction("PendingApproval", "Onboarding");
            }

            HttpContext.Session.SetString("UserName", profile.firstName ?? "User");
            HttpContext.Session.SetString("UserSurname", profile.surname ?? "");
            HttpContext.Session.SetString("UserUid", profile.uid);
            HttpContext.Session.SetString("UserRole", profile.role);
            HttpContext.Session.SetString("FirebaseToken", authResponse.IdToken);

            // Redirect based on role
            return profile.role switch
            {
                "student" => RedirectToAction("StudentDashboard", "Student"),
                "consultant" => RedirectToAction("ConsultantDashboard", "Consultant"),
                "admin" => RedirectToAction("AdminDashboard", "Admin"),
                _ => RedirectToAction(nameof(Login)) // Fallback
            };
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
                        uid = uid,
                        firstName = user.DisplayName?.Split(' ').FirstOrDefault() ?? "New",
                        surname = user.DisplayName?.Split(' ').Skip(1).FirstOrDefault() ?? "User",
                        email = user.Email ?? "",
                        role = "Pending" 
                   };

                    await _firebaseService.SaveUserProfileAsync(uid, req.Token, newProfile);
                    profile = newProfile;
                }

                if (!string.IsNullOrEmpty(user.PhotoUrl))
                {
                    HttpContext.Session.SetString("UserPhotoUrl", user.PhotoUrl);
                }
                else
                {
                    HttpContext.Session.Remove("UserPhotoUrl");
                }

                HttpContext.Session.SetString("UserUid", uid);
                HttpContext.Session.SetString("UserEmail", user.Email ?? "");
                HttpContext.Session.SetString("UserName", profile.firstName ?? user.DisplayName ?? "User");
                HttpContext.Session.SetString("UserRole", profile.role ?? "Pending");

                HttpContext.Session.SetString("FirebaseToken", req.Token);


                string redirectUrl = profile.role switch
                {
                    "consultant" => Url.Action("ConsultantDashboard", "Consultant")!,
                    "student" => Url.Action("StudentDashboard", "Student")!,
                    "admin" => Url.Action("AdminDashboard", "Admin")!,
                    _ => Url.Action("SelectRole", "Onboarding", new { uid = profile.uid, token = req.Token })!
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
            if (!ModelState.IsValid || string.IsNullOrEmpty(model.email))
            {
                ModelState.AddModelError("Email", "Email is required.");
                return View(model);
            }

            try
            {
                await _firebaseService.SendPasswordResetEmailAsync(model.email);

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


//References

//Brett Westwood. 2023. Setting Up Google Authentication in Firebase 9: A Step-by-Step Guide.
//[video online] Avaliable at <https://www.youtube.com/watch?v=-YA5kORugeI>
//Accessed 14 September 2025]

//firebase, 2025. Firebase Authentication [online]
//Avaliable at: <https://firebase.google.com/docs/auth#implementation_paths>
//Accessed 14 September 2025]