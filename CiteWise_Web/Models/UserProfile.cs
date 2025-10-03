namespace CiteWise_Web.Models
{
    public class UserProfile
    {
        public string Uid { get; set; }
        public string FirstName { get; set; }
        public string Surname { get; set; }
        public string Email { get; set; }
        public string Role { get; set; } = "Pending"; // until onboarding
        public DateTime CreatedAt { get; set; } = DateTime.UtcNow;


        // Common Fields
        public string Language { get; set; }

        // Student-specific fields
        public string Institution { get; set; }
        public string FieldOfStudy { get; set; }


        // Consultant-specific fields
        public string Specialisation { get; set; }
    }
}
