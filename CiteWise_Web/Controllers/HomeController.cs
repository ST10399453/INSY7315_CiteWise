using CiteWise_Web.Models;
using Firebase.Auth;
using Firebase.Database;
using Firebase.Database.Query;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;
using System.Diagnostics;
using System.Net;
using System.Net.Mail;

namespace CiteWise_Web.Controllers
{
    public class HomeController : Controller
    {
        private readonly EmailSettings _emailSettings;

        public HomeController(IOptions<EmailSettings> emailSettings)
        {
            _emailSettings = emailSettings.Value;
        }

        public IActionResult Index()
        {
            return View();
        }

        public IActionResult Services()
        {
            ViewData["Title"] = "Services";
            return View();
        }

        [HttpGet]
        public IActionResult ContactUs()
        {
            var model = new ContactFormModel();
            return View(model);
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public IActionResult ContactUs(ContactFormModel model)
        {
            if (!ModelState.IsValid)
            {
                return View(model);
            }

            try
            {
                var mail = new MailMessage
                {
                    From = new MailAddress(_emailSettings.SenderEmail, _emailSettings.SenderName),
                    Subject = model.Subject,
                    Body = $@"
                    <p><strong>Name:</strong> {model.Name}</p>
                    <p><strong>Email:</strong> {model.Email}</p>
                    <p><strong>Message:</strong></p>
                    <p>{model.Message}</p>",
                    IsBodyHtml = true
                };

                // Send to your support inbox (can be same as SenderEmail)
                mail.To.Add(_emailSettings.SenderEmail);

                using (var smtp = new SmtpClient("smtp.gmail.com", 587))
                {
                    smtp.Credentials = new NetworkCredential(
                        _emailSettings.SmtpUsername,
                        _emailSettings.SmtpPassword);

                    smtp.EnableSsl = true;
                    smtp.Send(mail);
                }

                TempData["SuccessMessage"] = "Thank you for reaching out. We’ll get back to you soon.";
                return RedirectToAction(nameof(ContactUs));
            }
            catch (Exception ex)
            {
                // In production, log ex
                TempData["ErrorMessage"] = "Sorry, we couldn’t send your message. Please try again later.";
                return RedirectToAction(nameof(ContactUs));
            }
        }

        public IActionResult AboutUs()
        {
            ViewData["Title"] = "About Us";
            return View();
        }

        [ResponseCache(Duration = 0, Location = ResponseCacheLocation.None, NoStore = true)]
        public IActionResult Error()
        {
            return View(new ErrorViewModel { RequestId = Activity.Current?.Id ?? HttpContext.TraceIdentifier });
        }
    }
}
