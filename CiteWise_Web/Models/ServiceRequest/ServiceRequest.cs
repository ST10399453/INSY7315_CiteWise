using System.ComponentModel.DataAnnotations;

namespace CiteWise_Web.Models.ServiceRequest
{
    public class ServiceRequest
    {
        [Required(ErrorMessage = "Please select the type of service you need.")]
        public string SelectedService { get; set; }

        public string? AdditionalInfo { get; set; }

        [Required(ErrorMessage = "Please upload your document before continuing.")]
        public IFormFile Documents { get; set; }

        [Required(ErrorMessage = "Please select an urgency level.")]
        public string Urgency { get; set; }


        [Required(ErrorMessage = "Please enter a valid deadline date.")]
        public DateTime? Deadline { get; set; }
    }
}
