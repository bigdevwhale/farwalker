package app.farwalker.ui

import android.content.Context
import app.farwalker.R
import app.farwalker.domain.Continent
import app.farwalker.domain.Medal
import app.farwalker.domain.MedalKind
import app.farwalker.domain.Medals
import app.farwalker.domain.Quest
import app.farwalker.domain.QuestKind
import app.farwalker.domain.Quests
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

    fun sight(key: String): String = world.sight(key)?.name(ru) ?: key

    /** A medal's name: "1 000 km on foot", "10 countries", "Round the earth". */
    fun medal(m: Medal): String {
        val n = m.target
        val all = when (m.kind) {
            MedalKind.SIGHTS -> n == world.sights.size
            MedalKind.CAPES -> n == world.towns.count { it.cape }
            MedalKind.PARTS -> n == Continent.entries.size
            else -> false
        }
        val res = r.resources
        return when {
            m.kind == MedalKind.ROUND -> r.getString(R.string.medal_round)
            all && m.kind == MedalKind.SIGHTS -> r.getString(R.string.medal_all_sights)
            all && m.kind == MedalKind.CAPES -> r.getString(R.string.medal_all_capes)
            all && m.kind == MedalKind.PARTS -> r.getString(R.string.medal_all_parts)
            m.kind == MedalKind.KM -> r.getString(R.string.medal_km, group(n.toLong()))
            m.kind == MedalKind.DAY -> r.getString(R.string.medal_day, group(n.toLong()))
            m.kind == MedalKind.SEA -> r.getString(R.string.medal_sea, group(n.toLong()))
            m.kind == MedalKind.COUNTRIES -> res.getQuantityString(R.plurals.medal_countries, n, group(n.toLong()))
            m.kind == MedalKind.SIGHTS -> res.getQuantityString(R.plurals.medal_sights, n, group(n.toLong()))
            m.kind == MedalKind.CAPES -> res.getQuantityString(R.plurals.medal_capes, n, group(n.toLong()))
            m.kind == MedalKind.PARTS -> res.getQuantityString(R.plurals.medal_parts, n, group(n.toLong()))
            else -> res.getQuantityString(R.plurals.medal_tasks, n, group(n.toLong()))
        }
    }

    fun medal(key: String): String = Medals.of(world, key)?.let { medal(it) } ?: key

    /** What a task asks: "Walk 7 500 steps today". */
    fun quest(q: Quest): String = when (q.kind) {
        QuestKind.DAY_STEPS -> r.getString(R.string.q_day, group(q.target.toLong()))
        QuestKind.WEEK_KM -> r.getString(R.string.q_week_km, group(q.target.toLong()))
        QuestKind.WEEK_DAYS -> r.resources.getQuantityString(R.plurals.q_week_days, q.target, q.target)
    }

    /** A task done, by its key, in a few words: "the day's steps". */
    fun questShort(key: String): String = r.getString(
        when (key.firstOrNull()) {
            'd' -> R.string.q_day_short
            'w' -> R.string.q_week_km_short
            else -> R.string.q_week_days_short
        },
    )

    /** A task done, by its key, as the journal tells it: what it was and what it paid. */
    private fun questDone(key: String): String = questShort(key) + ", +" + coins(Quests.rewardOf(key))

    private fun ref(n: Note): String = when (n.news) {
        News.SIGHT -> sight(n.ref)
        News.MEDAL -> medal(n.ref)
        News.QUEST -> questDone(n.ref)
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
            News.SIGHT -> R.string.n_sight_t
            News.MEDAL -> R.string.n_medal_t
            News.QUEST -> R.string.n_quest_t
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
            News.SIGHT -> R.string.n_sight
            News.MEDAL -> R.string.n_medal
            News.QUEST -> R.string.n_quest
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
            News.SIGHT -> R.string.j_sight
            News.MEDAL -> R.string.j_medal
            News.QUEST -> R.string.j_quest
        },
        ref(n),
    )
}
