using Microsoft.AspNetCore.Mvc;

namespace CiteWise_Web.Controllers
{
    public class ConsultantController : Controller
    {
        public IActionResult ConsultantDashboard()
        {
            return View();
        }
    }
}
