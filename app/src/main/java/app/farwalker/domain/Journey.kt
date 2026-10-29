package app.farwalker.domain

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

enum class Mode { WALK, TRAIN, SHIP }

/** A ticket being used: [km] in all over the next [legs] legs, [done] of them covered so far. */
class Ride(val mode: Mode, val legs: Int, val km: Double, val start: Long, var done: Double = 0.0) {
    val kmh: Double get() = if (mode == Mode.SHIP) Journey.SHIP_KMH else Journey.TRAIN_KMH
    /** When the ride ends, in epoch ms. */
    val end: Long get() = start + (km / kmh * 3_600_000).toLong()
}

/** A country's page in the passport: first come on [day] at [town], [entries] times in all. */
class Stamp(val iso: String, val day: Long, val town: Int, var entries: Int = 1)

/**
 * Where the traveller slept: in [town], or on the road from it to [next], [at] km out. [km] are the
 * day's walked kilometres, [mode] how the day ended.
 */
class Night(val day: Long, val town: Int, val iso: String, val km: Double, val mode: Mode, val next: Int = -1, val at: Double = 0.0)

/** A town reached, in order: the line the map draws behind the traveller. */
class Visit(val town: Int, val mode: Mode, val day: Long)

enum class News { START, COUNTRY, BORDER, CONTINENT, CAPE, ARRIVED, DOCKED, STATION, PORT }

/** Something worth a line in the journal (and a notification, while the app is out of sight). */
class Note(val day: Long, val news: News, val ref: String, val town: Int = -1)

/** Everything about the journey: the way ahead, the purse, the passport and the journal. */
class Journey {
    var home = -1
    /** The last town reached; the current leg starts here. */
    var town = -1
    var dest = -1
    val legs = mutableListOf<Leg>()
    /** Km covered on the current leg. */
    var at = 0.0
    /** The country underfoot. */
    var iso = ""
    var ride: Ride? = null
    var coins = 0.0
    var walked = 0.0
    var rode = 0.0
    var sailed = 0.0
    var start = -1L
    /** Steps and walked km per epoch day. */
    val days = sortedMapOf<Long, Int>()
    val dayKm = sortedMapOf<Long, Double>()
    var lastValue = -1L
    var lastAt = 0L
    var lastDay = -1L
    val stamps = linkedMapOf<String, Stamp>()
    val continents = linkedMapOf<Continent, Long>()
    val capes = linkedMapOf<Int, Long>()
    val trail = mutableListOf<Visit>()
    val nights = mutableListOf<Night>()
    val notes = mutableListOf<Note>()

    val started get() = home >= 0
    val states get() = stamps.size

    companion object {
        /** A coin per km walked; a train ticket costs a coin per [TRAIN_KM] km, a ship's passage one per [SHIP_KM]. */
        const val TRAIN_KM = 8.0
        const val SHIP_KM = 15.0
        const val TRAIN_KMH = 70.0
        const val SHIP_KMH = 28.0
        /** A town big enough to have a station. */
        const val STATION_POP = 50_000

        fun trainFare(km: Double) = ceil(km / TRAIN_KM).toInt()
        fun shipFare(km: Double) = ceil(km / SHIP_KM).toInt()
    }
}

/** A train one could take from here: as far as [town], [legs] legs and [km] km ahead, for [fare] coins. */
class TrainOffer(val town: Int, val legs: Int, val km: Double, val fare: Int)

/** The ship waiting at the port: [legs] sea legs, [km] km, to [town]. */
class ShipOffer(val town: Int, val legs: Int, val km: Double, val fare: Int)

/** The rules of the road. Changes a [Journey] in place; returns what happened worth telling. */
class Travel(val world: World) {
    val router = Router(world)

    fun leg(j: Journey): Leg? = j.legs.firstOrNull()
    fun edge(leg: Leg): Edge = world.edge(leg.edge)
    fun next(j: Journey): Int? = leg(j)?.let { router.to(it) }

    fun begin(j: Journey, home: Int, day: Long) {
        j.home = home
        j.town = home
        j.start = day
        j.lastDay = day
        j.trail += Visit(home, Mode.WALK, day)
        j.notes += Note(day, News.START, "", home)
        enter(j, world.town(home).iso, day, home)
    }

    /**
     * Sets out for [goal]. Halfway along a road, the traveller either goes on to its end or turns back
     * to its start, whichever makes the whole way shorter. Not while riding.
     */
    fun aim(j: Journey, goal: Int): Boolean {
        if (j.ride != null) return false
        val cur = leg(j)
        if (cur == null || j.at <= 0.0) {
            val way = router.route(j.town, goal) ?: return false
            j.legs.clear(); j.legs += way; j.at = 0.0; j.dest = goal
            return true
        }
        val e = edge(cur)
        val ahead = router.route(router.to(cur), goal)
        val back = router.route(router.from(cur), goal)
        val aheadCost = ahead?.let { (e.km - j.at) + length(it) } ?: Double.MAX_VALUE
        val backCost = back?.let { j.at + length(it) } ?: Double.MAX_VALUE
        if (ahead == null && back == null) return false
        j.legs.clear()
        if (aheadCost <= backCost) {
            j.legs += cur; j.legs += ahead!!
        } else {
            // Turn round on the same road.
            val turned = Leg(cur.edge, !cur.forward)
            j.town = router.from(turned)
            j.at = e.km - j.at
            j.legs += turned; j.legs += back!!
        }
        j.dest = goal
        return true
    }

    /** Stops where the traveller stands; the current road is still walked to its end. */
    fun halt(j: Journey) {
        if (j.ride != null) return
        val cur = leg(j)
        j.legs.clear()
        if (cur != null && j.at > 0.0) j.legs += cur
        j.dest = -1
    }

    fun length(legs: List<Leg>): Double = legs.sumOf { world.edge(it.edge).km.toDouble() }

    /** The km left to the goal. */
    fun left(j: Journey): Double = length(j.legs) - j.at

    /** Walked km on [day]: a coin each, and forward along the road unless riding or waiting for a ship. */
    fun walk(j: Journey, km: Double, day: Long): List<Note> {
        if (km <= 0) return emptyList()
        j.coins += km
        j.walked += km
        j.dayKm[day] = (j.dayKm[day] ?: 0.0) + km
        if (j.ride != null) return emptyList()
        return move(j, km, day, Mode.WALK)
    }

    /** Covers [km] along the legs; on foot, stops at a port where the road meets the sea. */
    private fun move(j: Journey, km: Double, day: Long, mode: Mode): List<Note> {
        val out = mutableListOf<Note>()
        var left = km
        while (left > 1e-9) {
            val cur = leg(j) ?: break
            val e = edge(cur)
            if (e.sea && mode != Mode.SHIP) break
            if (!e.sea && mode == Mode.SHIP) break
            val step = min(left, e.km - j.at)
            out += borders(j, cur, j.at, j.at + step, day)
            j.at += step
            left -= step
            when (mode) {
                Mode.TRAIN -> j.rode += step
                Mode.SHIP -> j.sailed += step
                Mode.WALK -> Unit
            }
            if (j.at >= e.km - 1e-9) {
                j.legs.removeAt(0)
                j.at = 0.0
                out += arrive(j, router.to(cur), day, mode)
            }
        }
        return out
    }

    /** The borders between [from] and [to] km along [leg]. */
    private fun borders(j: Journey, leg: Leg, from: Double, to: Double, day: Long): List<Note> {
        val e = edge(leg)
        if (e.sea || e.cross.isEmpty()) return emptyList()
        val out = mutableListOf<Note>()
        if (leg.forward) {
            for (c in e.cross) {
                val pos = c.at * e.km
                if (pos > from && pos <= to && c.iso != j.iso) out += enter(j, c.iso, day, -1)
            }
        } else {
            // Going the other way, each border leads back into the country before it.
            val before = listOf(world.town(e.a).iso) + e.cross.map { it.iso }
            for (k in e.cross.indices.reversed()) {
                val pos = (1 - e.cross[k].at) * e.km
                val into = before[k]
                if (pos > from && pos <= to && into != j.iso) out += enter(j, into, day, -1)
            }
        }
        return out
    }

    private fun arrive(j: Journey, town: Int, day: Long, mode: Mode): List<Note> {
        val out = mutableListOf<Note>()
        j.town = town
        j.trail += Visit(town, mode, day)
        val t = world.town(town)
        if (t.iso != j.iso) out += enter(j, t.iso, day, town)
        if (t.cape && town !in j.capes) {
            j.capes[town] = day
            out += Note(day, News.CAPE, t.en, town).also { j.notes += it }
        }
        if (town == j.dest) {
            j.dest = -1
            out += Note(day, News.ARRIVED, t.en, town).also { j.notes += it }
        }
        return out
    }

    private fun enter(j: Journey, iso: String, day: Long, town: Int): List<Note> {
        j.iso = iso
        val c = world.country(iso) ?: return emptyList()
        val out = mutableListOf<Note>()
        if (c.continent !in j.continents) {
            j.continents[c.continent] = day
            if (j.continents.size > 1) out += Note(day, News.CONTINENT, c.continent.name, town).also { j.notes += it }
        }
        val s = j.stamps[iso]
        if (s == null) {
            j.stamps[iso] = Stamp(iso, day, if (town >= 0) town else nextTown(j), 1)
            if (j.stamps.size > 1) out += Note(day, News.COUNTRY, iso, town).also { j.notes += it }
        } else {
            s.entries++
            out += Note(day, News.BORDER, iso, town).also { j.notes += it }
        }
        return out
    }

    /** The town a border stamp is credited to: the next one on the road. */
    private fun nextTown(j: Journey): Int = next(j) ?: j.town

    /**
     * Rides from here to each station ahead, up to the next sea, with the fare. They can be taken from
     * anywhere on the road: the ride starts where the traveller stands.
     */
    fun trains(j: Journey): List<TrainOffer> {
        if (j.ride != null || j.legs.isEmpty() || world.town(j.town).iso == "ATA") return emptyList()
        val out = mutableListOf<TrainOffer>()
        var km = -j.at
        for ((i, leg) in j.legs.withIndex()) {
            val e = edge(leg)
            if (e.sea) break
            val t = world.town(router.to(leg))
            if (t.iso == "ATA") break
            km += e.km
            if (station(t.id) || i == j.legs.lastIndex) out += TrainOffer(t.id, i + 1, km, Journey.trainFare(km))
        }
        return out
    }

    fun station(town: Int): Boolean = world.town(town).let { (it.pop >= Journey.STATION_POP || it.capital) && it.iso != "ATA" }

    /** The ship from this port, when the way goes on by sea. */
    fun ship(j: Journey): ShipOffer? {
        if (j.ride != null || j.at > 0.0) return null
        var km = 0.0
        var n = 0
        var to = -1
        for (leg in j.legs) {
            val e = edge(leg)
            if (!e.sea) break
            km += e.km; n++; to = router.to(leg)
        }
        if (n == 0) return null
        return ShipOffer(to, n, km, Journey.shipFare(km))
    }

    fun board(j: Journey, offer: TrainOffer, now: Long): Boolean {
        if (j.coins < offer.fare || trains(j).none { it.legs == offer.legs }) return false
        j.coins -= offer.fare
        j.ride = Ride(Mode.TRAIN, offer.legs, offer.km, now)
        return true
    }

    fun board(j: Journey, offer: ShipOffer, now: Long): Boolean {
        if (j.coins < offer.fare || ship(j)?.legs != offer.legs) return false
        j.coins -= offer.fare
        j.ride = Ride(Mode.SHIP, offer.legs, offer.km, now)
        return true
    }

    /** The clock moves rides along and closes the day: call often. */
    fun tick(j: Journey, now: Long, day: Long): List<Note> {
        val out = mutableListOf<Note>()
        val ride = j.ride
        if (ride != null) {
            val should = min(ride.km, max(0.0, (now - ride.start) / 3_600_000.0 * ride.kmh))
            val delta = should - ride.done
            // A ride's km are exactly its legs', so it never runs past its last stop.
            if (delta > 0) {
                out += move(j, delta, day, ride.mode)
                ride.done = should
            }
            if (ride.done >= ride.km - 1e-6) {
                j.ride = null
                val news = if (ride.mode == Mode.SHIP) News.DOCKED else News.STATION
                out += Note(day, news, world.town(j.town).en, j.town).also { j.notes += it }
            }
        }
        if (j.lastDay in 0 until day) {
            val last = j.lastDay
            val on = if (j.at > 0) next(j) ?: -1 else -1
            j.nights += Night(last, j.town, j.iso, j.dayKm[last] ?: 0.0, j.ride?.mode ?: Mode.WALK, on, if (on >= 0) j.at else 0.0)
            j.lastDay = day
        }
        return out
    }

    /** The town nearest the traveller: the one behind until halfway, then the one ahead. */
    fun sleepTown(j: Journey): Int {
        val cur = leg(j) ?: return j.town
        return if (j.at < edge(cur).km / 2.0) j.town else router.to(cur)
    }

    /** Where the traveller is, lat and lon. */
    fun position(j: Journey): Pair<Double, Double> {
        val a = world.town(j.town)
        val cur = leg(j) ?: return a.lat to a.lon
        val b = world.town(router.to(cur))
        val f = (j.at / edge(cur).km).coerceIn(0.0, 1.0)
        val blon = Geo.near(a.lon, b.lon)
        return a.lat + (b.lat - a.lat) * f to Geo.wrap(a.lon + (blon - a.lon) * f)
    }
}
