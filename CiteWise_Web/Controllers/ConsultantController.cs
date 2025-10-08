using CiteWise_Web.Models.ServiceRequest;
using CiteWise_Web.Services; // Imports firebaseServices for handling database operations
using Microsoft.AspNetCore.Mvc;

namespace CiteWise_Web.Controllers
{
    public class ConsultantController : Controller
    {
        private readonly FirebaseService _firebaseService;

        //Access the firebase service 
        public ConsultantController(FirebaseService firebaseService)
        {
            _firebaseService = firebaseService;
        }

        
        public async Task<IActionResult> ConsultantDashboard()
        {

            //calls firebaseservice to get all reviews 
            var reviews = await _firebaseService.GetAllServiceReviewsAsync();

            //passes the reviews so that it can be displayed 
            return View(reviews);
        }

        
        public async Task<IActionResult> RetrieveRequest()
        {
            var reviews = await _firebaseService.GetAllServiceReviewsAsync();
            return View(reviews);
        }
    }
}
