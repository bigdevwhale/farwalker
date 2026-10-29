package app.farwalker.ui

import android.content.Context
import app.farwalker.R
import app.farwalker.domain.Continent
import app.farwalker.domain.News
import app.farwalker.domain.Note
import app.farwalker.domain.Sky
import app.farwalker.domain.World
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** The app's words, in the language chosen: numbers, names, dates and the lines of the journal. */
class Words(val r: Context, val ru: Boolean, val world: World) {
    private val locale = if (ru) Locale.forLanguageTag("ru") else Locale.ENGLISH

    /** Whole km, grouped: "1 234". Under ten, with one decimal: "4.2". */
    fun km(v: Double): String =
        if (v < 10 && v > 0) String.format(locale, "%.1f", v) else group(v.roundToLong())

    fun group(n: Long): String = String.format(locale, "%,d", n).replace(',', ' ').replace(' ', ' ')

    fun coins(n: Int): String = r.resources.getQuantityString(R.plurals.coins, n, group(n.toLong()))
    fun days(n: Int): String = r.resources.getQuantityString(R.plurals.days, n, group(n.toLong()))

    fun town(id: Int): String = world.town(id).name(ru)
    fun country(iso: String): String = world.country(iso)?.name(ru) ?: iso

    fun date(day: Long): String =
        LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern(if (ru) "d MMMM yyyy" else "d MMMM yyyy", locale))

    fun shortDate(day: Long): String = LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("dd.MM.yy", locale))

    fun sky(s: Sky): String = r.getString(
        when (s) {
            Sky.CLEAR -> R.string.sky_clear
            Sky.CLOUDY -> R.string.sky_cloudy
            Sky.OVERCAST -> R.string.sky_overcast
            Sky.FOG -> R.string.sky_fog
            Sky.DRIZZLE -> R.string.sky_drizzle
            Sky.RAIN -> R.string.sky_rain
            Sky.STORM -> R.string.sky_storm
            Sky.SNOW -> R.string.sky_snow
            Sky.SLEET -> R.string.sky_sleet
        },
    )

    fun temp(t: Double): String = (if (t > 0.5) "+" else "") + t.roundToInt() + "°"

    private fun ref(n: Note): String = when (n.news) {
        News.COUNTRY, News.BORDER -> country(n.ref)
        News.CONTINENT -> Continent.entries.firstOrNull { it.name == n.ref }?.name(ru) ?: n.ref
        else -> if (n.town >= 0) town(n.town) else n.ref
    }

    fun noteTitle(n: Note): String = r.getString(
        when (n.news) {
            News.COUNTRY, News.BORDER -> R.string.n_country_t
            News.CONTINENT -> R.string.n_continent_t
            News.CAPE -> R.string.n_cape_t
            News.ARRIVED, News.START -> R.string.n_arrived_t
            News.DOCKED, News.PORT -> R.string.n_docked_t
            News.STATION -> R.string.n_station_t
        },
    )

    fun note(n: Note): String = r.getString(
        when (n.news) {
            News.COUNTRY, News.BORDER -> R.string.n_country
            News.CONTINENT -> R.string.n_continent
            News.CAPE -> R.string.n_cape
            News.ARRIVED, News.START -> R.string.n_arrived
            News.DOCKED, News.PORT -> R.string.n_docked
            News.STATION -> R.string.n_station
        },
        ref(n),
    )

    /** The note as a line of the journal. */
    fun line(n: Note): String = r.getString(
        when (n.news) {
            News.START -> R.string.j_start
            News.COUNTRY -> R.string.j_country
            News.BORDER -> R.string.j_border
            News.CONTINENT -> R.string.j_continent
            News.CAPE -> R.string.j_cape
            News.ARRIVED -> R.string.j_arrived
            News.DOCKED, News.PORT -> R.string.j_docked
            News.STATION -> R.string.j_station
        },
        ref(n),
    )
}
