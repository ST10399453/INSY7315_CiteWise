using System.ComponentModel.DataAnnotations;

namespace CiteWise_Web.Models.Account
{
    public class ConsultantOnboardingModel
    {
        //[Required]
        //[Display(Name = "Language")]
        //public string Language { get; set; }

        [Required]
        [Display(Name = "Specialisation")]
        public string specialisation { get; set; }

        // Hidden Firebase info
        public string Uid { get; set; }
        public string IdToken { get; set; }
    }
}
