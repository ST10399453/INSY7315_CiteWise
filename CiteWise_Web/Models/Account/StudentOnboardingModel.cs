using System.ComponentModel.DataAnnotations;

namespace CiteWise_Web.Models.Account
{
    public class StudentOnboardingModel
    {
        //[Required]
        //[Display(Name = "Language")]
        //public string Language { get; set; }

        [Required]
        [Display(Name = "Institution")]
        public string Institution { get; set; }

        [Required]
        [Display(Name = "Field of Study")]
        public string FieldOfStudy { get; set; }

        // Hidden Firebase info
        public string Uid { get; set; }
        public string IdToken { get; set; }

    }
}