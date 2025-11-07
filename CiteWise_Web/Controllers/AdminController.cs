using Microsoft.AspNetCore.Mvc;

namespace CiteWise_Web.Controllers
{
    public class AdminController : Controller
    {
        public IActionResult Index()
        {
            return View();
        }

        public IActionResult AdminDashboard()
        {
            return View();
        }

        public IActionResult ManageConsultants()
        {
            return View();
        }

        public IActionResult Resources()
        {
            return View();
        }
    }
}
