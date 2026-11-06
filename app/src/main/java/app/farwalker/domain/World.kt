package app.farwalker.domain

import java.io.DataInputStream
import java.io.InputStream
import java.text.Normalizer
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** A country or land with its own outline. [state] is a UN member or observer; the rest are lands. */
class Country(
    val iso: String,
    val en: String,
    val ru: String,
    val continent: Continent,
    val state: Boolean,
    val labelLat: Double,
    val labelLon: Double,
    /** Square degrees of its biggest piece: decides from which zoom its name is written. */
    val area: Double,
) {
    fun name(ru: Boolean) = if (ru) this.ru else en
}

enum class Continent(val en: String, val ru: String) {
    EUROPE("Europe", "Европа"),
    ASIA("Asia", "Азия"),
    AFRICA("Africa", "Африка"),
    NORTH_AMERICA("North America", "Северная Америка"),
    SOUTH_AMERICA("South America", "Южная Америка"),
    OCEANIA("Oceania", "Океания"),
    ANTARCTICA("Antarctica", "Антарктида");

    fun name(ru: Boolean) = if (ru) this.ru else en

    companion object {
        fun of(s: String) = when (s) {
            "Europe" -> EUROPE
            "Asia" -> ASIA
            "Africa" -> AFRICA
            "North America" -> NORTH_AMERICA
            "South America" -> SOUTH_AMERICA
            "Antarctica" -> ANTARCTICA
            // Natural Earth's "Seven seas (open ocean)" are islands scattered over the oceans: Oceania is the nearest home.
            else -> OCEANIA
        }
    }
}

class Town(
    val id: Int,
    val en: String,
    val ru: String,
    val iso: String,
    val lat: Double,
    val lon: Double,
    val pop: Int,
    val tz: String,
    val wikidata: String,
    flags: String,
) {
    val capital = 'C' in flags
    val port = 'P' in flags
    val station = 'S' in flags
    /** One of the ends of the earth. */
    val cape = 'X' in flags
    /** A landing on an island the data has no town for. */
    val landing = 'L' in flags
    fun name(ru: Boolean) = if (ru) this.ru else en
}

/**
 * A sight worth the walk: collected on reaching [town], the town of its own country nearest to it,
 * since the roads run between towns. [about] is a line on what it is.
 */
class Sight(
    val id: Int,
    val en: String,
    val ru: String,
    val iso: String,
    val lat: Double,
    val lon: Double,
    val aboutEn: String,
    val aboutRu: String,
    val town: Int,
) {
    /** What the journey keeps it by: the English name, which stays put when the list grows. */
    val key: String get() = en
    fun name(ru: Boolean) = if (ru) this.ru else en
    fun about(ru: Boolean) = if (ru) aboutRu else aboutEn
}

/** Where along a road the country underfoot changes, going from its first town to its second. */
class Cross(val at: Float, val iso: String)

/** A road ([sea] false) or a sea lane between two towns. */
class Edge(val id: Int, val a: Int, val b: Int, val km: Int, val sea: Boolean, val cross: List<Cross>) {
    fun other(t: Int) = if (t == a) b else a
}

/** One outline: lon, lat pairs. */
class Ring(val points: FloatArray, val minLon: Float, val maxLon: Float, val minLat: Float, val maxLat: Float)

class World(
    val countries: Map<String, Country>,
    val towns: List<Town>,
    val edges: List<Edge>,
    val shapes: Map<String, List<Ring>>,
    sightRows: List<List<String>> = emptyList(),
) {
    /** The sights, each tied to the nearest town of its country that a road reaches. */
    val sights: List<Sight> = sightRows.mapIndexed { i, f ->
        val lat = f[3].toDouble()
        val lon = f[4].toDouble()
        val near = towns.filter { it.iso == f[2] && !it.cape }.minByOrNull { Geo.km(lat, lon, it.lat, it.lon) }
            ?: towns.filter { !it.cape }.minBy { Geo.km(lat, lon, it.lat, it.lon) }
        Sight(i, f[0], f[1], f[2], lat, lon, f[5], f[6], near.id)
    }

    /** The sights collected on reaching each town. */
    val sightsAt: Map<Int, List<Sight>> = sights.groupBy { it.town }

    fun sight(key: String): Sight? = sights.firstOrNull { it.key == key }

    /** The edges at each town. */
    val links: Array<IntArray> = run {
        val lists = Array(towns.size) { mutableListOf<Int>() }
        for (e in edges) { lists[e.a] += e.id; lists[e.b] += e.id }
        Array(towns.size) { lists[it].toIntArray() }
    }

    val states: Int = countries.values.count { it.state }

    /** The towns in the order the map names them: capitals first, then by size. */
    val ranked: List<Town> by lazy { towns.sortedWith(compareBy({ !it.capital }, { -it.pop })) }

    fun country(iso: String): Country? = countries[iso]
    fun town(id: Int): Town = towns[id]
    fun edge(id: Int): Edge = edges[id]

    /** The towns of a country, biggest first. */
    fun townsOf(iso: String): List<Town> = towns.filter { it.iso == iso }.sortedByDescending { it.pop }

    /** The town a country's traveller would make for: its capital, or its biggest town. */
    fun capitalOf(iso: String): Town? = townsOf(iso).let { l -> l.firstOrNull { it.capital } ?: l.firstOrNull() }

    fun nearest(lat: Double, lon: Double): Town = towns.minBy { Geo.km(lat, lon, it.lat, it.lon) }

    /** The names as search compares them, folded once: English and Russian of each town in turn. */
    private val folded: Array<String> by lazy { Array(towns.size * 2) { fold(if (it % 2 == 0) towns[it / 2].en else towns[it / 2].ru) } }
    private val foldedCountries by lazy { countries.values.map { Triple(it, fold(it.en), fold(it.ru)) } }
    private val capitals: Map<String, Town> by lazy { countries.keys.mapNotNull { iso -> capitalOf(iso)?.let { iso to it } }.toMap() }

    /** Towns, capes and countries (as their capitals) matching what was typed, best first. */
    fun search(query: String, limit: Int = 30): List<Town> {
        val q = fold(query)
        if (q.length < 2) return emptyList()
        val hits = mutableListOf<Pair<Int, Town>>()
        for (t in towns) {
            val a = folded[t.id * 2]
            val b = folded[t.id * 2 + 1]
            val rank = when {
                a == q || b == q -> 0
                a.startsWith(q) || b.startsWith(q) -> 1
                q in a || q in b -> 3
                else -> continue
            }
            hits += (rank * 10 - if (t.capital || t.cape) 5 else 0) to t
        }
        for ((c, a, b) in foldedCountries) {
            if (a.startsWith(q) || b.startsWith(q)) capitals[c.iso]?.let { hits += -1 to it }
        }
        return hits.sortedWith(compareBy({ it.first }, { -it.second.pop })).map { it.second }.distinctBy { it.id }.take(limit)
    }

    companion object {
        private val marks = Regex("\\p{Mn}+")
        fun fold(s: String): String =
            Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(marks, "").replace('ё', 'е')

        fun load(countries: InputStream, towns: InputStream, edges: InputStream, shapes: InputStream, sights: InputStream? = null): World {
            val cs = countries.bufferedReader().readLines().filter { it.isNotBlank() }.map { l ->
                val f = l.split('\t')
                Country(f[0], f[1], f[2], Continent.of(f[3]), f[4] == "S", f[5].toDouble(), f[6].toDouble(), f[7].toDouble())
            }.associateBy { it.iso }
            val ts = towns.bufferedReader().readLines().filter { it.isNotBlank() }.mapIndexed { i, l ->
                val f = l.split('\t')
                Town(i, f[0], f[1], f[2], f[3].toDouble(), f[4].toDouble(), f[5].toInt(), f[6], f[7], f[8])
            }
            val es = edges.bufferedReader().readLines().filter { it.isNotBlank() }.mapIndexed { i, l ->
                val f = l.split('\t')
                val cross = if (f.size > 4 && f[4].isNotEmpty()) f[4].split(',').map {
                    Cross(it.substringBefore(':').toFloat(), it.substringAfter(':'))
                } else emptyList()
                Edge(i, f[0].toInt(), f[1].toInt(), f[2].toInt(), f[3] == "S", cross)
            }
            val sh = mutableMapOf<String, List<Ring>>()
            DataInputStream(shapes.buffered()).use { d ->
                repeat(d.readUnsignedShort()) {
                    val iso = String(ByteArray(3).also { d.readFully(it) }, Charsets.US_ASCII)
                    sh[iso] = List(d.readUnsignedShort()) {
                        val n = d.readUnsignedShort()
                        val p = FloatArray(n * 2)
                        var x0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
                        for (k in 0 until n) {
                            val x = d.readShort() / 100f
                            val y = d.readShort() / 100f
                            p[2 * k] = x; p[2 * k + 1] = y
                            x0 = min(x0, x); x1 = maxOf(x1, x); y0 = min(y0, y); y1 = maxOf(y1, y)
                        }
                        Ring(p, x0, x1, y0, y1)
                    }
                }
            }
            val ss = sights?.bufferedReader()?.readLines()?.filter { it.isNotBlank() }?.map { it.split('	') }.orEmpty()
            return World(cs, ts, es, sh, ss)
        }
    }
}

object Geo {
    fun km(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = p2 - p1
        val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 6371.0 * 2 * asin(min(1.0, sqrt(a)))
    }

    /** The longitude of [to] moved by a whole turn if that brings it nearer [from]: roads across the date line. */
    fun near(from: Double, to: Double): Double = when {
        to - from > 180 -> to - 360
        from - to > 180 -> to + 360
        else -> to
    }

    fun wrap(lon: Double): Double = ((lon + 180) % 360 + 360) % 360 - 180
}
