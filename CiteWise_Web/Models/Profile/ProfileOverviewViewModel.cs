using CiteWise_Web.Models.Account;
using CiteWise_Web.Models.ServiceRequest;

namespace CiteWise_Web.Models.Profile
{
    public class ProfileStatsViewModel
    {
        public int TotalRequests { get; set; }
        public int Completed { get; set; }
        public int InProgress { get; set; }
        public int Pending { get; set; }
    }

    public class ProfileOverviewViewModel
    {
        // From Firebase user profile
        public string Uid { get; set; }
        public string Name { get; set; }
        public string Email { get; set; }

        public string Institution { get; set; }
        public string FieldOfStudy { get; set; }
        public string Specialisation { get; set; }
        public string Role { get; set; }
        public string Language { get; set; }

        public string AvatarUrl { get; set; }

        // Stats based on service requests
        public ProfileStatsViewModel Stats { get; set; } = new ProfileStatsViewModel();

        // For small UI tweaks
        public bool IsAdmin { get; set; }
        public bool IsConsultant { get; set; }
        public bool IsStudent { get; set; }
    }
}
