using System.ComponentModel.DataAnnotations;

namespace CiteWise_Web.Models.ServiceRequest
{
    public class ServiceRequest
    {
        public string SelectedService { get; set; }

        public string AdditionalInfo { get; set; }

        public IFormFile Documents { get; set; }

        public string Urgency { get; set; }

        public DateTime? Deadline { get; set; }
    }
}
