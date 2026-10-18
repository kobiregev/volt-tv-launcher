package io.github.kobiregev.volt

import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

object Weather {
    // Set weather.city / weather.lat / weather.lon in local.properties (see README).
    val CITY: String = BuildConfig.WEATHER_CITY
    private val LAT: String = BuildConfig.WEATHER_LAT
    private val LON: String = BuildConfig.WEATHER_LON
    val enabled: Boolean get() = LAT.isNotBlank() && LON.isNotBlank()
    const val MAX_AGE_MS = 30 * 60 * 1000L
    const val RETRY_MS = 2 * 60 * 1000L

    class Result(val temp: Float, val code: Int)

    /** Blocking; call from a background thread. */
    fun fetch(): Result? {
        if (!enabled) return null
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(
                "https://api.open-meteo.com/v1/forecast?latitude=$LAT&longitude=$LON" +
                    "&current=temperature_2m,weather_code&timezone=auto"
            )
            conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val cur = JSONObject(body).getJSONObject("current")
            Result(cur.getDouble("temperature_2m").toFloat(), cur.getInt("weather_code"))
        } catch (t: Throwable) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    fun condition(code: Int): String = WeatherCodes.condition(code)

    fun icon(code: Int): Int = when (code) {
        0 -> R.drawable.wx_clear
        1, 2 -> R.drawable.wx_partly
        3 -> R.drawable.wx_cloudy
        45, 48 -> R.drawable.wx_fog
        in 51..67, in 80..82 -> R.drawable.wx_rain
        in 71..77, 85, 86 -> R.drawable.wx_snow
        95, 96, 99 -> R.drawable.wx_storm
        else -> R.drawable.wx_cloudy
    }
}
