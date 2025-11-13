using System.ComponentModel.DataAnnotations;

namespace CiteWise_Web.Models.Account
{
    public class RegisterModel //Change to RegistrationModel
    {
        //[Required]
        [Display(Name = "First Name")]
        public string firstName { get; set; }

        [Required]
        [Display(Name = "Surname")]
        public string surname { get; set; }

        [Required]
        [Display(Name = "Email")]
        [EmailAddress(ErrorMessage = "Please enter a valid email address.")]
        public string email { get; set; }

        [Required]
        [DataType(DataType.Password)]
        [Display(Name = "Password")]
        [MinLength(8, ErrorMessage = "Password must be at least 8 characters long")]
        public string Password { get; set; }

        [Required]
        [DataType(DataType.Password)]
        [Compare("Password", ErrorMessage = "Passwords do not match")]
        [Display(Name = "Confirm Password")]

        public string ConfirmPassword { get; set; }

    }
}
