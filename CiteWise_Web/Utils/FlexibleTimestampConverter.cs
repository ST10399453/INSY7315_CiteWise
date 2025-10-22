using System;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;

namespace CiteWise_Web.Utils
{
    /// <summary>
    /// Converts Firestore timestamps ({"_seconds":..., "_nanoseconds":...}),
    /// unix epoch numbers (ms or s), or ISO8601 strings into DateTime? (UTC).
    /// </summary>
    public sealed class FlexibleTimestampConverter : JsonConverter<DateTime?>
    {
        public override DateTime? ReadJson(JsonReader reader, Type objectType, DateTime? existingValue, bool hasExistingValue, JsonSerializer serializer)
        {
            if (reader.TokenType == JsonToken.Null) return null;

            // Object? -> Firestore { _seconds, _nanoseconds }
            if (reader.TokenType == JsonToken.StartObject)
            {
                var obj = JObject.Load(reader);
                var secsToken = obj["_seconds"];
                if (secsToken != null && secsToken.Type == JTokenType.Integer)
                {
                    long seconds = secsToken.Value<long>();
                    long nanos = obj["_nanoseconds"]?.Value<long>() ?? 0L;

                    // nanos -> ticks (1 tick = 100 ns)
                    long extraTicks = nanos / 100;
                    var dto = DateTimeOffset.FromUnixTimeSeconds(seconds).AddTicks(extraTicks);
                    return dto.UtcDateTime;
                }
                return null; // unexpected object shape
            }

            // Integer? -> treat as epoch (auto-detect ms vs s)
            if (reader.TokenType == JsonToken.Integer)
            {
                long value = Convert.ToInt64(reader.Value);
                // Heuristic: values > 10^12 are milliseconds
                if (value > 1_000_000_000_000)
                    return DateTimeOffset.FromUnixTimeMilliseconds(value).UtcDateTime;
                else
                    return DateTimeOffset.FromUnixTimeSeconds(value).UtcDateTime;
            }

            // String? -> ISO or numeric text
            if (reader.TokenType == JsonToken.String)
            {
                var s = (string?)reader.Value;
                if (string.IsNullOrWhiteSpace(s)) return null;

                if (DateTimeOffset.TryParse(s, out var dto))
                    return dto.UtcDateTime;

                if (long.TryParse(s, out var num))
                {
                    if (num > 1_000_000_000_000)
                        return DateTimeOffset.FromUnixTimeMilliseconds(num).UtcDateTime;
                    else
                        return DateTimeOffset.FromUnixTimeSeconds(num).UtcDateTime;
                }
            }

            // Fallback: skip/consume token
            JToken.ReadFrom(reader);
            return null;
        }

        public override void WriteJson(JsonWriter writer, DateTime? value, JsonSerializer serializer)
        {
            if (value == null) { writer.WriteNull(); return; }
            
            writer.WriteValue(((DateTime)value).ToUniversalTime().ToString("o"));
        }
    }
}
