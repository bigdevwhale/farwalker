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

    /** Faster than anyone walks or runs for long: about 240 steps a minute. */
    const val MAX_PER_SECOND = 4

    /** Room for steps the phone held back and handed over late, which were walked before the last reading. */
    const val LATE_STEPS = 1_000

    /**
     * The steps between a reading of [lastValue] at [lastAt] and one of [value] at [at]. The counter
     * starts again from zero only when the phone boots (at [bootAt]); a smaller value without a boot
     * is a stale or glitched reading and brings no steps. No reading can bring more steps than could be
     * walked in the time since the last one, so a counter that jumps doesn't fling the traveller ahead.
     */
    fun delta(lastValue: Long, lastAt: Long, value: Long, at: Long, bootAt: Long): Long {
        val d = when {
            value >= lastValue -> value - lastValue
            bootAt > lastAt -> value
            else -> 0
        }
        if (lastAt <= 0) return d
        val most = LATE_STEPS + (at - lastAt).coerceAtLeast(0) / 1000 * MAX_PER_SECOND
        return min(d, most)
    }

    /**
     * Day totals from a health app (Health Connect) against the steps the journey already has for those
     * days: what is new on each day. A total only grows, as watches and apps hand their steps over late;
     * a total below the known one brings nothing, so steps once walked are never taken back.
     */
    fun catchUp(known: Map<Long, Int>, totals: Map<Long, Long>): Map<Long, Int> {
        val out = mutableMapOf<Long, Int>()
        for ((day, total) in totals) {
            val n = min(total, MAX_PER_DAY.toLong()) - (known[day] ?: 0)
            if (n > 0) out[day] = n.toInt()
        }
        return out
    }

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
