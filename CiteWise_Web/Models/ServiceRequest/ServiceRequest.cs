using System.ComponentModel.DataAnnotations;

namespace CiteWise_Web.Models.ServiceRequest
{
    public class ServiceRequest
    {
        [Required]
        public string SelectedService { get; set; }

        public string? AdditionalInfo { get; set; }

        [Required]
        public IFormFile Documents { get; set; }

        [Required]
        public string Urgency { get; set; }

        [Required]
        public DateTime? Deadline { get; set; }
    }
}
