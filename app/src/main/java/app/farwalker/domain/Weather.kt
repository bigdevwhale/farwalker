package app.farwalker.domain

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

enum class Sky { CLEAR, CLOUDY, OVERCAST, FOG, DRIZZLE, RAIN, STORM, SNOW, SLEET;

    val rain get() = this == DRIZZLE || this == RAIN || this == STORM || this == SLEET
    val snow get() = this == SNOW || this == SLEET
    val grey get() = this != CLEAR && this != CLOUDY

    companion object {
        /** The WMO weather code Open-Meteo reports. */
        fun wmo(code: Int): Sky = when (code) {
            0, 1 -> CLEAR
            2 -> CLOUDY
            3 -> OVERCAST
            45, 48 -> FOG
            51, 53, 55, 56, 57 -> DRIZZLE
            61, 63, 65, 80, 81, 82 -> RAIN
            66, 67 -> SLEET
            71, 73, 75, 77, 85, 86 -> SNOW
            95, 96, 99 -> STORM
            else -> CLOUDY
        }

        fun of(s: String?) = entries.firstOrNull { it.name == s }
    }
}

/** The weather where the traveller is, as last fetched: [lat], [lon] is where it was asked for. */
data class WeatherNow(val sky: Sky, val temp: Double, val wind: Double, val at: Long, val lat: Double = 0.0, val lon: Double = 0.0)

object Weather {
    /** A report is good for three hours, and for 60 km around where it was asked for. */
    const val FRESH_MS = 3 * 60 * 60 * 1000L
    const val FRESH_KM = 60.0

    fun fresh(w: WeatherNow?, lat: Double, lon: Double, now: Long = System.currentTimeMillis()): Boolean =
        w != null && now - w.at < FRESH_MS && Geo.km(lat, lon, w.lat, w.lon) < FRESH_KM

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 6000
        c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "Farwalker/1.0 github.com/bigdevwhale/farwalker")
        try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    /**
     * The current weather at a place: from Open-Meteo, or from MET Norway when Open-Meteo can't be
     * reached (some networks block it). No key; nothing but the coordinates is sent.
     */
    fun fetch(lat: Double, lon: Double, now: Long = System.currentTimeMillis()): WeatherNow =
        (runCatching { openMeteo(lat, lon, now) }.getOrElse { metNo(lat, lon, now) }).copy(lat = lat, lon = lon)

    private fun openMeteo(lat: Double, lon: Double, now: Long): WeatherNow {
        val j = JSONObject(get("https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f&current=temperature_2m,weather_code,wind_speed_10m".format(java.util.Locale.US, lat, lon)))
        val cur = j.getJSONObject("current")
        return WeatherNow(Sky.wmo(cur.getInt("weather_code")), cur.getDouble("temperature_2m"), cur.optDouble("wind_speed_10m", 0.0), now)
    }

    private fun metNo(lat: Double, lon: Double, now: Long): WeatherNow {
        val j = JSONObject(get("https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%.3f&lon=%.3f".format(java.util.Locale.US, lat, lon)))
        val first = j.getJSONObject("properties").getJSONArray("timeseries").getJSONObject(0).getJSONObject("data")
        val d = first.getJSONObject("instant").getJSONObject("details")
        val symbol = first.optJSONObject("next_1_hours")?.optJSONObject("summary")?.optString("symbol_code")
            ?: first.optJSONObject("next_6_hours")?.optJSONObject("summary")?.optString("symbol_code") ?: "cloudy"
        return WeatherNow(symbol(symbol), d.getDouble("air_temperature"), d.optDouble("wind_speed", 0.0) * 3.6, now)
    }

    /** MET Norway's symbol codes ("lightrainshowers_day", "fog"…) as skies. */
    fun symbol(code: String): Sky {
        val c = code.substringBefore('_')
        return when {
            "thunder" in c -> Sky.STORM
            "sleet" in c -> Sky.SLEET
            "snow" in c -> Sky.SNOW
            c.startsWith("light") && "rain" in c -> Sky.DRIZZLE
            "rain" in c -> Sky.RAIN
            c == "fog" -> Sky.FOG
            c == "cloudy" -> Sky.OVERCAST
            c == "partlycloudy" -> Sky.CLOUDY
            else -> Sky.CLEAR
        }
    }
}
