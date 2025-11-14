using System.ComponentModel.DataAnnotations;

namespace CiteWise_Web.Models
{
    public class ContactFormModel
    {
        [Required(ErrorMessage = "Please enter your name")]
        public string Name { get; set; }

        [Required(ErrorMessage = "Please enter your email")]
        [EmailAddress(ErrorMessage = "Please enter a valid email address")]
        public string Email { get; set; }

        [Required(ErrorMessage = "Please enter a subject")]
        public string Subject { get; set; }

        [Required(ErrorMessage = "Please enter a message")]
        public string Message { get; set; }
    }
}
