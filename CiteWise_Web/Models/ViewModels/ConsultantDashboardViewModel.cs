using CiteWise_Web.Models.ServiceRequest;
using System.Collections.Generic;

namespace CiteWise_Web.Models.ViewModels
{
    public class ConsultantDashboardViewModel
    {
        public List<RequestItem> Unassigned { get; set; }

        public List<RequestItem> Assigned { get; set; }

    }
}