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
}
