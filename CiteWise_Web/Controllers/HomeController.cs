using System.Diagnostics;
using CiteWise_Web.Models;
using Firebase.Auth;
using Firebase.Database;
using Firebase.Database.Query;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.Logging;

namespace CiteWise_Web.Controllers
{
    public class HomeController : Controller
    {
        private readonly ILogger<HomeController> _logger;
        private readonly FirebaseAuthProvider _auth;
        private readonly FirebaseClient _firebaseClient;

        public HomeController(ILogger<HomeController> logger, IConfiguration config)
        {
            _logger = logger;

            var apiKey = config["Firebase:ApiKey"];
            var databaseUrl = config["Firebase:DatabaseUrl"];

            _auth = new FirebaseAuthProvider(new FirebaseConfig(apiKey));
            _firebaseClient = new FirebaseClient(databaseUrl);
        }

        public IActionResult Index()
        {
            return View();
        }

        public IActionResult Privacy()
        {
            return View();
        }

        [ResponseCache(Duration = 0, Location = ResponseCacheLocation.None, NoStore = true)]
        public IActionResult Error()
        {
            return View(new ErrorViewModel { RequestId = Activity.Current?.Id ?? HttpContext.TraceIdentifier });
        }

        // ------------------- REGISTER -------------------
        [HttpGet]
        public IActionResult Register()
        {
            return View();
        }

        [HttpPost]
        public async Task<IActionResult> Register(UserModel userModel)
        {
            if (!ModelState.IsValid)
                return View(userModel);

            try
            {
                // Create user in Firebase Authentication
                await _auth.CreateUserWithEmailAndPasswordAsync(userModel.Email, userModel.Password);

                // Log in the new user
                var fbAuthLink = await _auth.SignInWithEmailAndPasswordAsync(userModel.Email, userModel.Password);
                string token = fbAuthLink.FirebaseToken;

                if (!string.IsNullOrEmpty(token))
                {
                    HttpContext.Session.SetString("_UserToken", token);

                    // Save additional user details to Realtime Database
                    var userId = fbAuthLink.User.LocalId;
                    await _firebaseClient.Child("users").Child(userId).PutAsync(new
                    {
                        FirstName = userModel.FirstName,
                        Surname = userModel.Surname,
                        Email = userModel.Email
                    });

                    return RedirectToAction("Index");
                }
                else
                {
                    ModelState.AddModelError(string.Empty, "Unable to log in user after registration.");
                    return View(userModel);
                }
            }
            catch (FirebaseAuthException ex)
            {
                ModelState.AddModelError(string.Empty, ex.Message);
                return View(userModel);
            }
        }

        // ------------------- SIGN IN -------------------
        [HttpGet]
        public IActionResult SignIn()
        {
            return View();
        }

        [HttpPost]
        public async Task<IActionResult> SignIn(UserModel userModel)
        {
            if (!ModelState.IsValid)
                return View(userModel);

            try
            {
                var fbAuthLink = await _auth.SignInWithEmailAndPasswordAsync(userModel.Email, userModel.Password);
                string token = fbAuthLink.FirebaseToken;

                if (!string.IsNullOrEmpty(token))
                {
                    HttpContext.Session.SetString("_UserToken", token);
                    return RedirectToAction("Index");
                }
                else
                {
                    ModelState.AddModelError(string.Empty, "Login failed. Please try again.");
                    return View(userModel);
                }
            }
            catch (FirebaseAuthException ex)
            {
                ModelState.AddModelError(string.Empty, ex.Message);
                return View(userModel);
            }
        }

        // ------------------- LOG OUT -------------------
        public IActionResult LogOut()
        {
            HttpContext.Session.Clear();
            return RedirectToAction("SignIn");
        }
    }
}
