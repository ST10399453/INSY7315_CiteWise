using System.ComponentModel.DataAnnotations;

namespace CiteWise_Web.Models.Account
{
    public class LoginModel
    {
        [Required]
        [EmailAddress(ErrorMessage = "Please enter a valid email address.")]
        public string Email { get; set; }

        [Required]
        [DataType(DataType.Password)]
        public string Password { get; set; }
    }
}
