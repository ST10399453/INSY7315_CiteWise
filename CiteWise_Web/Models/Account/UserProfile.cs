using Newtonsoft.Json;

namespace CiteWise_Web.Models.Account
{
    public class UserProfile
    {
        public string Uid { get; set; }
        public string FirstName { get; set; }
        public string Surname { get; set; }
        public string Email { get; set; }
        public string Role { get; set; } = "Pending"; // until onboarding
                                                      //public long CreatedAt { get; set; } = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();

        [JsonProperty("createdAt")]
        [JsonConverter(typeof(FlexibleDateTimeConverter))]
        public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

        // Common Fields
        public string Language { get; set; }

        // Student-specific fields
        public string Institution { get; set; }
        public string FieldOfStudy { get; set; }


        // Consultant-specific fields
        public string Specialisation { get; set; }
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
