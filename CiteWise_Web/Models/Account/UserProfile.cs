using Newtonsoft.Json;
using System.ComponentModel.DataAnnotations;

namespace CiteWise_Web.Models.Account
{
    public class UserProfile
    {
        [Display(Name = "Uid")]
        public string uid { get; set; }

        [Display(Name = "First Name")]
        public string firstName { get; set; }

        [Display(Name = "Surname")]
        public string surname { get; set; }

        [Display(Name = "Email")]
        public string email { get; set; }

        [Display(Name = "Role")]
        public string role { get; set; } = "Pending"; // until onboarding
                                                      //public long CreatedAt { get; set; } = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
        public bool? isApproved { get; set; }

        [JsonProperty("createdAt")]
        [JsonConverter(typeof(FlexibleDateTimeConverter))]
        public DateTime createdAt { get; set; } = DateTime.UtcNow;

        // Common Fields
        [Display(Name = "Language")]
        public string language { get; set; }

        // Student-specific fields

        [Display(Name = "Institution")]
        public string institution { get; set; }

        [Display(Name = "Field of Study")]
        public string fieldOfStudy { get; set; }


        // Consultant-specific fields
        [Display(Name = "Specialisation")]
        public string specialisation { get; set; }
    }

    // Handles both ISO date strings and Unix timestamps
    public class FlexibleDateTimeConverter : JsonConverter<DateTime>
    {
        public override DateTime ReadJson(JsonReader reader, Type objectType, DateTime existingValue, bool hasExistingValue, JsonSerializer serializer)
        {
            if (reader.Value == null)
                return DateTime.MinValue;

            // If value is a number, treat it as Unix milliseconds
            if (reader.Value is long unixMs)
                return DateTimeOffset.FromUnixTimeMilliseconds(unixMs).UtcDateTime;

            var str = reader.Value.ToString();

            if (long.TryParse(str, out var ms))
                return DateTimeOffset.FromUnixTimeMilliseconds(ms).UtcDateTime;

            if (DateTime.TryParse(str, out var dt))
                return dt.ToUniversalTime();

            return DateTime.MinValue;
        }

        public override void WriteJson(JsonWriter writer, DateTime value, JsonSerializer serializer)
        {
            writer.WriteValue(value.ToUniversalTime().ToString("o")); // Writes ISO 8601 string
        }
    }
}
