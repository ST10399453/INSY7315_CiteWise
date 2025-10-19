//using CiteWise_Web.Models.ServiceRequest;
//using CiteWise_Web.Services;
//using Microsoft.AspNetCore.Mvc;

//namespace CiteWise_Web.Controllers
//{
//    public class RequestsController : Controller
//    {
//        private readonly FirebaseService _firebaseService;

//        public RequestsController(FirebaseService firebaseService)
//        {
//            _firebaseService = firebaseService;
//        }

//        public async Task<IActionResult> Index()
//        {
//            var requests = await _firebaseService.GetAllServiceReviewsAsync();
//            return View(requests);
//        }
//    }
//}
