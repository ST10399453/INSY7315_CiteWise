using Microsoft.AspNetCore.Mvc;

namespace CiteWise_Web.Controllers
{
    public class DashboardController : Controller
    {
        public IActionResult StudentDashboard()
        {

            return View(); 
        }

        public IActionResult ConsultantDashboard()
        {
            return View();
        }
    }
}
