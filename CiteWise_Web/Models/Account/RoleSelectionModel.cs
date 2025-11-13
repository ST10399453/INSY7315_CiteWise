using System.ComponentModel.DataAnnotations;

namespace CiteWise_Web.Models.Account
{
    public class RoleSelectionModel
    {
        [Required]
        [Display(Name = "Role")]
        public string role { get; set; } // Student or Consultant

        // Hidden Firebase info
        public string Uid { get; set; }
        public string IdToken { get; set; }
    }
}
