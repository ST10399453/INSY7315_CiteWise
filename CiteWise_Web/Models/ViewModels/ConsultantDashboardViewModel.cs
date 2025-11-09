using CiteWise_Web.Models.ServiceRequest;
using System.Collections.Generic;

namespace CiteWise_Web.Models.ViewModels
{
    public class ConsultantDashboardViewModel
    {
        public List<ServiceRequestItem> Unassigned { get; set; }

        public List<ServiceRequestItem> Assigned { get; set; }
    }
}