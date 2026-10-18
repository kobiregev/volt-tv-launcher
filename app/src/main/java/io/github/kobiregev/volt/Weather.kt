package io.github.kobiregev.volt

import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

object Weather {
    const val RETRY_MS = 2 * 60 * 1000L

    class Result(val temp: Float, val code: Int)

    class GeoResult(val name: String, val admin1: String?, val country: String?, val lat: Double, val lon: Double)

    /** Blocking GET with timeouts; null on any error. Call from a background thread. */
    private fun get(url: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            if (conn.responseCode != 200) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (t: Throwable) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    /** Blocking; call from a background thread. */
    fun fetch(lat: String, lon: String, unit: String): Result? {
        val body = get(WeatherLogic.forecastUrl(lat, lon, unit)) ?: return null
        return try {
            val cur = JSONObject(body).getJSONObject("current")
            Result(cur.getDouble("temperature_2m").toFloat(), cur.getInt("weather_code"))
        } catch (t: Throwable) {
            null
        }
    }

    /** City search. Null on error, an empty list when nothing matches. Call from a background thread. */
    fun search(url: String): List<GeoResult>? {
        val body = get(url) ?: return null
        return try {
            val arr = JSONObject(body).optJSONArray("results") ?: return emptyList()
            val out = ArrayList<GeoResult>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val name = o.optString("name", "")
                if (name.isEmpty() || !o.has("latitude") || !o.has("longitude")) continue
                out.add(
                    GeoResult(
                        name,
                        o.optString("admin1", "").ifEmpty { null },
                        o.optString("country", "").ifEmpty { null },
                        o.getDouble("latitude"),
                        o.getDouble("longitude")
                    )
                )
            }
            out
        } catch (t: Throwable) {
            null
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
