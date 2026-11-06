package app.farwalker

import app.farwalker.domain.Steps
import org.junit.Assert.assertEquals
import org.junit.Test

class StepsTest {
    @Test
    fun healthTotalsBringOnlyWhatIsNew() {
        val known = mapOf(10L to 3_000, 11L to 500)
        val totals = mapOf(10L to 4_200L, 11L to 400L, 12L to 1_000L, 13L to 0L, 14L to 1_000_000L)
        // Day 10 brings the steps beyond the known; day 11 has fewer, so nothing is taken back.
        assertEquals(mapOf(10L to 1_200, 12L to 1_000, 14L to Steps.MAX_PER_DAY), Steps.catchUp(known, totals))
    }

    @Test
    fun counterReadings() {
        val boot = 1_000_000_000L
        val t = boot + 3_600_000L * 24 * 5
        val min15 = 15 * 60_000L
        // An ordinary walk.
        assertEquals(1500L, Steps.delta(10_000, t, 11_500, t + min15, boot))
        // A stale reading a little behind the last one brings nothing.
        assertEquals(0L, Steps.delta(28_600, t, 28_590, t + 1_000, boot))
        // A real reboot: the counter started again from zero.
        assertEquals(300L, Steps.delta(28_600, t, 300, t + min15, t + 60_000))
        // The counter glitches to zero and back: the jump is held to what can be walked meanwhile.
        assertEquals(0L, Steps.delta(28_600, t, 0, t + 1_000, boot))
        assertEquals(Steps.LATE_STEPS + 2L * Steps.MAX_PER_SECOND, Steps.delta(0, t + 1_000, 28_600, t + 3_000, boot))
        // After a long sleep a whole day's walk still arrives in one reading.
        assertEquals(12_000L, Steps.delta(10_000, t, 22_000, t + 3_600_000L * 10, boot))
    }
}
