package com.example.citewise_mobile.api

import com.google.gson.*
import java.lang.reflect.Type
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

/**
 * FlexTime stores a timestamp as epochMillis, parsed from:
 *  - ISO-8601 strings, e.g. "2025-10-12T00:00:00Z" (or with .SSS)
 *  - Firestore-like objects: { "_seconds": 1760479200, "_nanoseconds": 0 }
 */
data class FlexTime(val epochMillis: Long?)

/** Gson adapter that can read both ISO strings and Firestore {_seconds,_nanoseconds} objects. */
class FlexTimeAdapter : JsonDeserializer<FlexTime> {

    private val isoFormats = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd" // fallback: date-only
    ).map { pattern ->
        SimpleDateFormat(pattern, Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): FlexTime {
        return try {
            when {
                json.isJsonNull -> FlexTime(null)
                json.isJsonPrimitive && json.asJsonPrimitive.isString -> {
                    val s = json.asString
                    FlexTime(parseIsoToMillis(s))
                }
                json.isJsonObject -> {
                    val obj = json.asJsonObject
                    if (obj.has("_seconds")) {
                        val sec = obj.get("_seconds").asLong
                        val nsec = obj.get("_nanoseconds")?.asLong ?: 0L
                        val ms = sec * 1000L + nsec / 1_000_000L
                        FlexTime(ms)
                    } else {
                        // Unknown object shape → don't crash
                        FlexTime(null)
                    }
                }
                else -> FlexTime(null)
            }
        } catch (_: Exception) {
            FlexTime(null)
        }
    }

    private fun parseIsoToMillis(s: String): Long? {
        for (fmt in isoFormats) {
            try {
                return fmt.parse(s)?.time
            } catch (_: ParseException) { /* try next */ }
        }
        return null
    }
}

/** UI helper: format as local yyyy-MM-dd or "—" if null. */
fun FlexTime?.toUiDate(): String =
    this?.epochMillis?.let { ms ->
        val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        df.format(Date(ms))
    } ?: "—"
