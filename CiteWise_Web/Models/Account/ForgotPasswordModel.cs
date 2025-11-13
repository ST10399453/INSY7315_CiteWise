using System.ComponentModel.DataAnnotations;

namespace CiteWise_Web.Models.Account
{
    public class ForgotPasswordModel
    {
        [Display(Name = "Email")]
        public string email { get; set; }
    }
}
