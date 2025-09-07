using System.ComponentModel.DataAnnotations;

namespace CiteWise_Web.Models
{
    public class RoleSelectionModel
    {
        [Required]
        public string Role { get; set; } // Student or Consultant

        // Hidden Firebase info
        public string Uid { get; set; }
        public string IdToken { get; set; }
    }
}
