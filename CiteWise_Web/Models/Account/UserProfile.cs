using Newtonsoft.Json;

namespace CiteWise_Web.Models.Account
{
    public class UserProfile
    {
        public string uid { get; set; }
        public string firstName { get; set; }
        public string surname { get; set; }
        public string email { get; set; }
        public string role { get; set; } = "Pending"; // until onboarding
                                                      //public long CreatedAt { get; set; } = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
        public bool? isApproved { get; set; }

        [JsonProperty("createdAt")]
        [JsonConverter(typeof(FlexibleDateTimeConverter))]
        public DateTime createdAt { get; set; } = DateTime.UtcNow;

        // Common Fields
        public string language { get; set; }

        // Student-specific fields
        public string institution { get; set; }
        public string fieldOfStudy { get; set; }


        // Consultant-specific fields
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
