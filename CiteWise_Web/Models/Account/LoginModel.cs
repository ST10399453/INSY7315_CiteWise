using System.ComponentModel.DataAnnotations;

namespace CiteWise_Web.Models.Account
{
    public class LoginModel
    {
        [Required]
        [Display(Name = "Email")]
        [EmailAddress(ErrorMessage = "Please enter a valid email address.")]
        public string email { get; set; }

        [Required]
        [Display(Name = "Password")]
        [DataType(DataType.Password)]
        public string Password { get; set; }
    }
}
