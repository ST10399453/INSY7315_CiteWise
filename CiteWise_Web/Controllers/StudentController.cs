using CiteWise_Web.Models.ServiceRequest;
using CiteWise_Web.Services;
using Google.Cloud.Firestore;
using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.AspNetCore.Mvc;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;

namespace CiteWise_Web.Controllers
{
    public class StudentController : Controller
    {
        private readonly FirebaseService _firebaseService;

        public StudentController(FirebaseService firebaseService)
        {
            _firebaseService = firebaseService;
        }

        [HttpGet]
        public IActionResult StudentDashboard()
        {

            return View();
        }


        [HttpGet]
        public IActionResult StudentServiceRequest()
        {
            return View();
        }

        [HttpPost]
        public async Task<IActionResult> StudentServiceRequest(ServiceRequest model)
        {
            if (!ModelState.IsValid)
            {
                return View(model);
            }

            string uid = HttpContext.Session.GetString("UserUid");
            if (string.IsNullOrEmpty(uid))
            {
                return RedirectToAction("Login", "Account");
            }

            //Creates a document model from the submitted form data
            var document = new DocumentModel
            {
                UID = uid,
                DocName = model.DocName,
                MimeType = model.Documents.ContentType,
                ProjectTitle = model.ProjectTitle,
                AdditionalInfo = model.AdditionalInfo,
                Service = model.SelectedService,
                Urgency = model.Urgency,
                Deadline = model.Deadline.HasValue? Timestamp.FromDateTime(model.Deadline.Value.ToUniversalTime()): null,
                UploadedAt = Timestamp.FromDateTime(DateTime.UtcNow)
            };

            // Save document
            string docResult = await _firebaseService.SaveDocumentToFirestoreAsync(document, uid);

            var jsonObj = JsonConvert.DeserializeObject<JObject>(docResult);
            var fullPath = jsonObj?["name"]?.ToString();
            var docId = fullPath?.Split('/').Last(); 

            // Create service review thats links to the document saved
            var review = new ServiceReviewModel
            {
                UID = uid,
                DocumentId = docId,
                DocumentName = document.DocName,
                ConsultantId = null,
                ServiceType = model.SelectedService,
                ProjectTitle = model.ProjectTitle,
                Description = model.AdditionalInfo,
                Priority = model.Urgency,
                Deadline = document.Deadline,
                CreatedAt = Timestamp.FromDateTime(DateTime.UtcNow)
            };

            // Save service review
            await _firebaseService.SaveServiceReviewAsync(review);

            return RedirectToAction("StudentDashboard");
        }
    }
}
