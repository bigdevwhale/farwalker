package app.farwalker.domain

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** A notable place by the road: a Wikipedia article near where the traveller is. */
data class Roadmark(
    val title: String,
    val text: String,
    val thumb: String?,
    val url: String,
    val lat: Double,
    val lon: Double,
)

/**
 * What stands by the road right now, and where it was looked up ([fromLat], [fromLon]), so a new one is asked for only
 * after [Roadside.EVERY_KM]. [seen] keeps the titles already shown here, so «another» brings a new one.
 */
data class RoadsideNow(val sight: Roadmark?, val fromLat: Double, val fromLon: Double, val ru: Boolean, val at: Long, val seen: List<String>)

object Roadside {
    /** A new sight after this many kilometres along the way. */
    const val EVERY_KM = 10.0
    /** Wikipedia's geosearch reaches 10 km at most. */
    private const val RADIUS_M = 10_000

    fun stale(now: RoadsideNow?, lat: Double, lon: Double, ru: Boolean): Boolean =
        now == null || now.ru != ru || Geo.km(lat, lon, now.fromLat, now.fromLon) >= EVERY_KM

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

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /**
     * The nearest article around [lat], [lon] that isn't in [skip] and has a few words to say; null when the place is
     * empty (open sea, the steppe) or the network isn't there. Only the coordinates are sent.
     */
    fun near(lat: Double, lon: Double, ru: Boolean, skip: Set<String>): Roadmark? {
        val lang = if (ru) "ru" else "en"
        val url = "https://$lang.wikipedia.org/w/api.php?action=query&format=json&formatversion=2" +
            "&generator=geosearch&ggscoord=${"%.5f".format(java.util.Locale.ROOT, lat)}%7C${"%.5f".format(java.util.Locale.ROOT, lon)}" +
            "&ggsradius=$RADIUS_M&ggslimit=30&ggsnamespace=0" +
            "&prop=extracts%7Cpageimages%7Ccoordinates&exintro=1&explaintext=1&exsentences=3&exlimit=30" +
            "&piprop=thumbnail&pithumbsize=320&pilimit=30&colimit=30"
        val pages = JSONObject(get(url)).optJSONObject("query")?.optJSONArray("pages") ?: return null
        return pick(pages, lat, lon, lang, skip)
    }

    /** Parses a geosearch answer: the nearest page with a real text, list pages and disambiguations left out. */
    fun pick(pages: JSONArray, lat: Double, lon: Double, lang: String, skip: Set<String>): Roadmark? {
        val out = ArrayList<Pair<Double, Roadmark>>()
        for (i in 0 until pages.length()) {
            val p = pages.getJSONObject(i)
            val title = p.optString("title")
            if (title.isBlank() || title in skip) continue
            val text = p.optString("extract").trim()
            if (text.length < 60) continue
            if (listOf("Список", "List of").any { title.startsWith(it) }) continue
            val co = p.optJSONArray("coordinates")?.optJSONObject(0)
            val plat = co?.optDouble("lat") ?: lat
            val plon = co?.optDouble("lon") ?: lon
            val thumb = p.optJSONObject("thumbnail")?.optString("source")?.takeIf { it.isNotBlank() }
            val sight = Roadmark(title, text, thumb, "https://$lang.wikipedia.org/wiki/${enc(title.replace(' ', '_'))}", plat, plon)
            // A place with a picture usually has more to it than a bare street: it goes first, as if 3 km nearer.
            out += Geo.km(lat, lon, plat, plon) - (if (thumb != null) 3.0 else 0.0) to sight
        }
        return out.minByOrNull { it.first }?.second
    }

    // ——— kept in a small file, next to the journey ———

    fun load(f: File): RoadsideNow? = runCatching {
        if (!f.exists()) return null
        val o = JSONObject(f.readText())
        val s = o.optJSONObject("sight")?.let {
            Roadmark(it.getString("title"), it.getString("text"), it.optString("thumb").takeIf { t -> t.isNotBlank() }, it.getString("url"), it.getDouble("lat"), it.getDouble("lon"))
        }
        val seen = o.optJSONArray("seen")?.let { a -> List(a.length()) { a.getString(it) } }.orEmpty()
        RoadsideNow(s, o.getDouble("fromLat"), o.getDouble("fromLon"), o.optBoolean("ru", true), o.optLong("at"), seen)
    }.getOrNull()

    fun save(f: File, r: RoadsideNow) {
        val o = JSONObject()
            .put("fromLat", r.fromLat).put("fromLon", r.fromLon).put("ru", r.ru).put("at", r.at)
            .put("seen", JSONArray(r.seen.takeLast(60)))
        r.sight?.let { s ->
            o.put("sight", JSONObject().put("title", s.title).put("text", s.text).put("thumb", s.thumb ?: "").put("url", s.url).put("lat", s.lat).put("lon", s.lon))
        }
        runCatching { f.writeText(o.toString()) }
    }
}
