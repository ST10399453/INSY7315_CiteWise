using System.ComponentModel.DataAnnotations;

namespace CiteWise_Web.Models.Account
{
    public class EditProfileViewModel
    {
        [Display(Name = "First name")]
        public string? FirstName { get; set; }

        [Display(Name = "Surname")]
        public string? Surname { get; set; }

        [Display(Name = "Email")]
        public string? Email { get; set; } // readonly in UI

        [Display(Name = "Institution")]
        public string? Institution { get; set; }

        [Display(Name = "Field of study")]
        public string? FieldOfStudy { get; set; }

        [Display(Name = "Specialisation")]
        public string? Specialisation { get; set; }

        [Display(Name = "Language")]
        public string? Language { get; set; }
    }

}
