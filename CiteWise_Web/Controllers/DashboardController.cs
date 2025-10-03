using CiteWise_Web.Services;
using Microsoft.AspNetCore.Mvc;

namespace CiteWise_Web.Controllers
{
    public class DashboardController : Controller
    {

        private readonly FirebaseService _firebaseService;

        public DashboardController(FirebaseService firebaseService)
        {
            _firebaseService = firebaseService;
        }

        public IActionResult StudentDashboard()
        {

            return View(); 
        }

        public IActionResult StudentServiceRequest()
        {
            return View();
        }


        public IActionResult ConsultantDashboard()
        {
            return View();
        }
    }
}
