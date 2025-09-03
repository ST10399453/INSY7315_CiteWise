using System.ComponentModel.DataAnnotations;

namespace CiteWise_Web.Models
{
    public class UserModel
    {

        [Required]
        [EmailAddress]

        public string Email { get; set; }

        [Required]
        public string Password { get; set; }

        [Required]
        [Display(Name = "First Name")]
        public string FirstName { get; set; }

        [Required]
        [Display(Name = "Surname")]
        public string Surname { get; set; }

    }
}
