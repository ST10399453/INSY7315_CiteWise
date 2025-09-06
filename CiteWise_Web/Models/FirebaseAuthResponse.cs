namespace CiteWise_Web.Models
{
    public class FirebaseAuthResponse
    {
        public string IdToken { get; set; }   // Used to authenticate DB requests
        public string LocalId { get; set; }   // Firebase UID
        public string Email { get; set; }
        public string RefreshToken { get; set; }
        public string ExpiresIn { get; set; }
    }
}
