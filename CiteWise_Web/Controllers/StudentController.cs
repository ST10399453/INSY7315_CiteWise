using Microsoft.AspNetCore.Mvc;

namespace CiteWise_Web.Controllers
{
    public class StudentController : Controller
    {
        public IActionResult StudentDashboard()
        {

            return View();
        }

        public IActionResult StudentServiceRequest()
        {
            return View();
        }
    }
}
