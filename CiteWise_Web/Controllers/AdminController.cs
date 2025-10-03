using Microsoft.AspNetCore.Mvc;

namespace CiteWise_Web.Controllers
{
    public class AdminController : Controller
    {
        public IActionResult Index()
        {
            return View();
        }
    }
}
