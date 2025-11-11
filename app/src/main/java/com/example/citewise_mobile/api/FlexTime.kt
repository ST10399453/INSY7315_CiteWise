package com.example.citewise_mobile.api

import com.google.gson.*
import java.io.Serializable
import java.lang.reflect.Type
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

data class FlexTime(val epochMillis: Long?) : Serializable {
    companion object {
        fun fromMillis(ms: Long?): FlexTime? = ms?.let { FlexTime(it) }
    }
}

fun Long?.toFlex(): FlexTime? = this?.let { FlexTime(it) }

class FlexTimeAdapter : JsonDeserializer<FlexTime> {

    private val isoFormats = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd"
    ).map { pattern ->
        SimpleDateFormat(pattern, Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): FlexTime {
        return try {
            when {
                json.isJsonNull -> FlexTime(null)

                // Firestore object: {"_seconds":..., "_nanoseconds":...}
                json.isJsonObject -> {
                    val obj = json.asJsonObject
                    if (obj.has("_seconds")) {
                        val sec = obj.get("_seconds")?.asLong ?: 0L
                        val nsec = obj.get("_nanoseconds")?.asLong ?: 0L
                        FlexTime(sec * 1_000L + nsec / 1_000_000L)
                    } else {
                        FlexTime(null)
                    }
                }

                // Raw number (millis)
                json.isJsonPrimitive && json.asJsonPrimitive.isNumber ->
                    FlexTime(json.asLong)

                // String: try ISO, then numeric string
                json.isJsonPrimitive && json.asJsonPrimitive.isString -> {
                    val s = json.asString
                    parseIsoToMillis(s)?.let { FlexTime(it) }
                        ?: FlexTime(s.toLongOrNull())
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
                fmt.parse(s)?.let { return it.time }
            } catch (_: ParseException) {
            }
        }
        return null
    }
}

// Extension to display in dd/MM/yyyy
fun FlexTime?.toUiDate(): String =
    this?.epochMillis?.let { ms ->
        val df = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        df.format(Date(ms))
    } ?: "—"
