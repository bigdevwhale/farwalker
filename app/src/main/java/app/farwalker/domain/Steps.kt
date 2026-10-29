package app.farwalker.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.min

/**
 * Turns readings of the phone's step counter (which counts from the last reboot) into steps per day.
 * Between two readings the steps are spread over the days in between by time, so a long gap while
 * the phone kept the app asleep still lands roughly on the right days.
 */
object Steps {
    /** A day can't plausibly hold more than this; anything above is a counter glitch. */
    const val MAX_PER_DAY = 70_000

    /** The steps since [lastValue] at [lastAt]; a smaller counter means the phone rebooted. */
    fun delta(lastValue: Long, value: Long): Long = if (value >= lastValue) value - lastValue else value

    fun spread(steps: Long, from: Long, to: Long, zone: ZoneId = ZoneId.systemDefault()): Map<Long, Int> {
        if (steps <= 0) return emptyMap()
        val a = Instant.ofEpochMilli(min(from, to)).atZone(zone)
        val b = Instant.ofEpochMilli(to).atZone(zone)
        val first = a.toLocalDate()
        val last = b.toLocalDate()
        val days = (last.toEpochDay() - first.toEpochDay() + 1).coerceAtLeast(1)
        val cap = MAX_PER_DAY.toLong() * days
        val total = min(steps, cap)
        if (first == last) return mapOf(last.toEpochDay() to total.toInt())
        val span = (to - from).toDouble().coerceAtLeast(1.0)
        val out = mutableMapOf<Long, Int>()
        var given = 0L
        var d: LocalDate = first
        while (!d.isAfter(last)) {
            val s = maxOf(from, d.atStartOfDay(zone).toInstant().toEpochMilli())
            val e = minOf(to, d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
            val share = if (d == last) total - given else (total * ((e - s) / span)).toLong()
            if (share > 0) out[d.toEpochDay()] = share.toInt()
            given += share
            d = d.plusDays(1)
        }
        return out
    }
}
