package app.farwalker.domain

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class QuestKind { DAY_STEPS, WEEK_KM, WEEK_DAYS }

/**
 * A task: [target] to reach by the end of its day or week, [progress] made so far, [reward] coins when
 * done. [key] names it in the journey: the kind and the day (or the Monday) it belongs to.
 */
class Quest(val kind: QuestKind, val key: String, val target: Int, val progress: Double, val reward: Int, val paid: Boolean) {
    val done: Boolean get() = progress >= target
}

/**
 * Daily and weekly tasks, sized by the walker's own recent days, so a light walker and a long one both
 * get a task that can be done with a little more than usual. A task belongs to its day or week and is
 * paid once, as soon as it's done: one for today's steps, one for the week's km, one for the days of
 * the week the daily task was met.
 */
object Quests {
    const val DAY_REWARD = 2
    const val WEEK_KM_REWARD = 10
    const val WEEK_DAYS_REWARD = 7
    /** Days of a week to meet the daily task on. */
    const val WEEK_DAYS = 5
    private const val DEFAULT_STEPS = 6_000
    private const val DEFAULT_KM = 35

    /** What the task named by [key] pays: its kind is the key's first letter. */
    fun rewardOf(key: String): Int = when (key.firstOrNull()) {
        'd' -> DAY_REWARD
        'w' -> WEEK_KM_REWARD
        else -> WEEK_DAYS_REWARD
    }

    /** The Monday of the week [day] falls in (epoch day 0 was a Thursday). */
    fun weekStart(day: Long): Long = day - Math.floorMod(day + 3, 7L)

    /** The day's steps to walk: a tenth over the usual of the two weeks before it, in round 500s. */
    fun dayTarget(j: Journey, day: Long): Int {
        val recent = (1..14).mapNotNull { j.days[day - it] }.filter { it > 500 }.sorted()
        if (recent.size < 3) return DEFAULT_STEPS
        val usual = recent[recent.size / 2]
        return (((usual * 1.1) / 500).roundToInt() * 500).coerceIn(3_000, 25_000)
    }

    /** The week's km to walk: a tenth over the usual week of the four before it, in round 5s. */
    fun weekTarget(j: Journey, start: Long): Int {
        val days = (1..28).mapNotNull { j.dayKm[start - it] }
        if (days.size < 7) return DEFAULT_KM
        val week = days.sum() / days.size * 7
        return (((week * 1.1) / 5).roundToInt() * 5).coerceIn(15, 250)
    }

    /** Days of the week the daily task is to be met on: fewer if the journey began partway through it. */
    private fun weekDays(j: Journey, start: Long): Int = min(WEEK_DAYS, (start + 7 - max(start, j.start)).toInt().coerceAtLeast(1))

    /** The task of the day [day], and the two of its week. */
    fun of(j: Journey, day: Long): List<Quest> {
        val start = weekStart(day)
        val dKey = "d$day"
        val kmKey = "w$start"
        val daysKey = "s$start"
        val met = (start until start + 7).count { d -> d >= j.start && (j.days[d] ?: 0) >= dayTarget(j, d) }
        return listOf(
            Quest(QuestKind.DAY_STEPS, dKey, dayTarget(j, day), (j.days[day] ?: 0).toDouble(), DAY_REWARD, dKey in j.quests),
            Quest(QuestKind.WEEK_KM, kmKey, weekTarget(j, start), (start until start + 7).sumOf { j.dayKm[it] ?: 0.0 }, WEEK_KM_REWARD, kmKey in j.quests),
            Quest(QuestKind.WEEK_DAYS, daysKey, weekDays(j, start), met.toDouble(), WEEK_DAYS_REWARD, daysKey in j.quests),
        )
    }

    /**
     * The tasks done but not yet paid: today's and this week's, and yesterday's and last week's too,
     * for the steps a phone or a watch hands over late.
     */
    fun due(j: Journey, today: Long): List<Quest> {
        val days = listOf(today - 1, today).filter { it >= j.start }
        val weeks = listOf(weekStart(today) - 7, weekStart(today)).filter { it + 6 >= j.start }
        val all = days.map { d -> of(j, d)[0] } + weeks.flatMap { w -> of(j, w).drop(1) }
        return all.filter { it.done && !it.paid }.distinctBy { it.key }
    }
}
