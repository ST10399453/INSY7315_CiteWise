namespace CiteWise_Web.Models
{
    public class EmailSettings
    {
        public string Provider { get; set; } // "Gmail" or "Outlook"
        public string SenderEmail { get; set; }
        public string SenderName { get; set; }
        public string SmtpUsername { get; set; }
        public string SmtpPassword { get; set; }
    }
}