using System.Threading.Tasks;
using CiteWise_Web.Models.Account;
using CiteWise_Web.Models.Profile;
using CiteWise_Web.Services;
using Microsoft.AspNetCore.Mvc;

namespace CiteWise_Web.Controllers
{
    public class AccountSettingsController : Controller
    {
        private readonly FirebaseService _firebaseService;

        public AccountSettingsController(FirebaseService firebaseService)
        {
            _firebaseService = firebaseService;
        }

        // ---------------------
        // EDIT PROFILE
        // ---------------------

        [HttpGet]
        public async Task<IActionResult> EditProfile()
        {
            string token = HttpContext.Session.GetString("FirebaseToken");
            string uid = HttpContext.Session.GetString("UserUid");

            if (string.IsNullOrEmpty(token) || string.IsNullOrEmpty(uid))
                return RedirectToAction("Login", "Account");

            var profile = await _firebaseService.GetUserProfileAsync(uid, token);

            var vm = new EditProfileViewModel();

            if (profile != null)
            {
                vm.FirstName = profile.firstName;
                vm.Surname = profile.surname;
                vm.Email = profile.email;          // read-only in form
                vm.Language = profile.language;
                vm.Institution = profile.institution;
                vm.FieldOfStudy = profile.fieldOfStudy;
                vm.Specialisation = profile.specialisation;
            }

            return View(vm);
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public async Task<IActionResult> EditProfile(EditProfileViewModel model)
        {
            if (!ModelState.IsValid)
                return View(model);

            string token = HttpContext.Session.GetString("FirebaseToken");
            string uid = HttpContext.Session.GetString("UserUid");

            if (string.IsNullOrEmpty(token) || string.IsNullOrEmpty(uid))
                return RedirectToAction("Login", "Account");

            var updates = new
            {
                firstName = model.FirstName,
                surname = model.Surname,
                language = model.Language,
                institution = model.Institution,
                fieldOfStudy = model.FieldOfStudy,
                specialisation = model.Specialisation
            };

            try
            {
                await _firebaseService.UpdateUserProfileAsync(uid, token, updates);

                // Keep session name in sync for navbar / profile, if you use it there
                HttpContext.Session.SetString("UserName", model.FirstName ?? "");
                HttpContext.Session.SetString("UserSurname", model.Surname ?? "");

                TempData["Message"] = "Profile updated successfully.";
                return RedirectToAction(nameof(EditProfile));
            }
            catch (Exception ex)
            {
                ModelState.AddModelError(string.Empty, $"Failed to update profile: {ex.Message}");
                return View(model);
            }
        }

        // ---------------------
        // CHANGE PASSWORD
        // ---------------------

        [HttpGet]
        public IActionResult ChangePassword()
        {
            var vm = new ChangePasswordViewModel();
            // if you want to show email
            vm.Email = HttpContext.Session.GetString("UserEmail");
            return View(vm);
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public async Task<IActionResult> ChangePassword(ChangePasswordViewModel model)
        {
            if (!ModelState.IsValid)
                return View(model);

            if (model.NewPassword != model.ConfirmPassword)
            {
                ModelState.AddModelError(string.Empty, "New password and confirmation do not match.");
                return View(model);
            }

            string idToken = HttpContext.Session.GetString("FirebaseToken");
            if (string.IsNullOrEmpty(idToken))
                return RedirectToAction("Login", "Account");

            try
            {
                var ok = await _firebaseService.ChangePasswordAsync(idToken, model.NewPassword);
                if (!ok)
                {
                    ModelState.AddModelError(string.Empty, "Unable to change password. Please try again.");
                    return View(model);
                }

                TempData["Message"] = "Password changed successfully.";
                return RedirectToAction(nameof(ChangePassword));
            }
            catch (Exception ex)
            {
                ModelState.AddModelError(string.Empty, $"Password change failed: {ex.Message}");
                return View(model);
            }
        }
    }
}
