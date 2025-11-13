using System.ComponentModel.DataAnnotations;

namespace CiteWise_Web.Models.Account
{
    public class ChangePasswordViewModel
    {
        [Display(Name = "Email")]
        public string? Email { get; set; } // optional, read-only

        [Required]
        [DataType(DataType.Password)]
        [Display(Name = "Current password")]
        public string CurrentPassword { get; set; }

        [Required]
        [DataType(DataType.Password)]
        [Display(Name = "New password")]
        [MinLength(6, ErrorMessage = "Password must be at least 6 characters.")]
        public string NewPassword { get; set; }

        [Required]
        [DataType(DataType.Password)]
        [Display(Name = "Confirm new password")]
        public string ConfirmPassword { get; set; }
    }
}
