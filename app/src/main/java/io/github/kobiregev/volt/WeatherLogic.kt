package io.github.kobiregev.volt

import java.net.URLEncoder
import java.util.Locale

/** A saved weather location. [lat] and [lon] are decimal strings with 2 decimals. */
data class WeatherLoc(val city: String, val lat: String, val lon: String)

/** Pure weather settings logic: URLs, labels and validation. No Android types. */
object WeatherLogic {
    const val MIN_QUERY = 2
    val INTERVALS = listOf(15, 30, 60)
    const val DEFAULT_INTERVAL = 30

    /** Geocoding search URL, or null when the trimmed text is shorter than [MIN_QUERY]. */
    fun geocodeUrl(query: String): String? {
        val q = query.trim()
        if (q.length < MIN_QUERY) return null
        return "https://geocoding-api.open-meteo.com/v1/search?name=" +
            URLEncoder.encode(q, "UTF-8") + "&count=5&language=en&format=json"
    }

    fun forecastUrl(lat: String, lon: String, unit: String): String {
        val u = if (normalizeUnit(unit) == "f") "&temperature_unit=fahrenheit" else ""
        return "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code&timezone=auto$u"
    }

    /** "admin1, country" with missing or blank parts skipped. */
    fun resultLine(admin1: String?, country: String?): String =
        listOfNotNull(admin1, country).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")

    fun coord(v: Double): String = String.format(Locale.US, "%.2f", v)

    /** Null when the coordinates are missing or out of range. A blank city gets a generic name. */
    fun location(city: String?, lat: String?, lon: String?): WeatherLoc? {
        val la = lat?.trim()?.toDoubleOrNull() ?: return null
        val lo = lon?.trim()?.toDoubleOrNull() ?: return null
        if (la !in -90.0..90.0 || lo !in -180.0..180.0) return null
        return WeatherLoc(city?.trim().orEmpty().ifEmpty { "Your city" }, coord(la), coord(lo))
    }

    fun normalizeUnit(u: String?): String = if (u == "f") "f" else "c"

    fun nextUnit(u: String): String = if (normalizeUnit(u) == "c") "f" else "c"

    fun unitLabel(u: String): String = if (normalizeUnit(u) == "f") "°F" else "°C"

    fun normalizeInterval(m: Int): Int = if (m in INTERVALS) m else DEFAULT_INTERVAL

    fun nextInterval(m: Int): Int = INTERVALS[(INTERVALS.indexOf(normalizeInterval(m)) + 1) % INTERVALS.size]

    fun intervalLabel(m: Int): String = if (normalizeInterval(m) == 60) "1 hour" else "${normalizeInterval(m)} min"

    /** Identifies what a cached reading belongs to. */
    fun key(loc: WeatherLoc, unit: String): String = "${loc.lat},${loc.lon},${normalizeUnit(unit)}"
}
