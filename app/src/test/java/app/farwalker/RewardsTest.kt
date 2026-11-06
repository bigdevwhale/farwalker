package app.farwalker

import app.farwalker.domain.Journey
import app.farwalker.domain.Medals
import app.farwalker.domain.News
import app.farwalker.domain.QuestKind
import app.farwalker.domain.Quests
import app.farwalker.domain.Travel
import app.farwalker.domain.Visit
import app.farwalker.domain.Mode
import app.farwalker.domain.World
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate

/** Sights, medals and the daily and weekly tasks. */
class RewardsTest {
    private val world: World by lazy {
        val dir = File("src/main/assets/world")
        World.load(File(dir, "countries.tsv").inputStream(), File(dir, "towns.tsv").inputStream(),
            File(dir, "edges.tsv").inputStream(), File(dir, "shapes.bin").inputStream(), File(dir, "sights.tsv").inputStream())
    }
    private val travel by lazy { Travel(world) }

    private fun town(en: String, iso: String) = world.towns.first { it.en == en && it.iso == iso }.id

    @Test
    fun everySightHasATownInItsOwnCountry() {
        assertTrue(world.sights.size >= 70)
        assertEquals(world.sights.size, world.sights.map { it.key }.toSet().size)
        for (s in world.sights) {
            val t = world.town(s.town)
            assertEquals(s.en, s.iso, t.iso)
            assertTrue(s.en, s.aboutEn.isNotBlank() && s.aboutRu.isNotBlank() && s.ru.isNotBlank())
        }
        assertEquals("Paris", world.town(world.sight("Eiffel Tower")!!.town).en)
    }

    @Test
    fun reachingASightsTownCollectsItOnce() {
        val j = Journey()
        travel.begin(j, town("Rouen", "FRA"), 0)
        travel.settle(j, 0)
        assertTrue(travel.aim(j, town("Paris", "FRA")))
        var day = 0L
        while (j.dest >= 0 && day < 100) { travel.walk(j, 10.0, day); travel.tick(j, 0, ++day) }
        assertEquals(day - 1, j.sights["Eiffel Tower"])
        assertEquals(1, j.notes.count { it.news == News.SIGHT && it.ref == "Eiffel Tower" })
    }

    @Test
    fun anOlderJourneyGetsItsSightsAndMedalsQuietly() {
        val j = Journey()
        travel.begin(j, town("Rouen", "FRA"), 0)
        j.trail += Visit(town("Paris", "FRA"), Mode.WALK, 3)
        j.walked = 150.0
        val notes = j.notes.size
        travel.settle(j, 10)
        assertEquals(3L, j.sights["Eiffel Tower"])
        assertTrue("km10" in j.medals && "km100" in j.medals && "km500" !in j.medals)
        assertEquals(notes, j.notes.size)
        // Afterwards a new medal is news.
        j.walked = 600.0
        travel.settle(j, 11)
        assertTrue(j.notes.any { it.news == News.MEDAL && it.ref == "km500" })
        assertTrue(Medals.all(world).all { it.key.isNotBlank() })
    }

    @Test
    fun weeksStartOnMonday() {
        for (d in 20_000L..20_014L) {
            assertEquals(DayOfWeek.MONDAY, LocalDate.ofEpochDay(Quests.weekStart(d)).dayOfWeek)
            assertTrue(d - Quests.weekStart(d) in 0..6)
        }
    }

    @Test
    fun theDaysTaskIsSizedByRecentDays() {
        val j = Journey()
        val today = 20_010L
        travel.begin(j, town("Rouen", "FRA"), today - 30)
        assertEquals(6_000, Quests.dayTarget(j, today))
        for (d in today - 14 until today) j.days[d] = 8_000
        // A tenth over the usual 8 000, in round 500s.
        assertEquals(9_000, Quests.dayTarget(j, today))
    }

    @Test
    fun aTaskDoneIsPaidOnce() {
        val j = Journey()
        val today = 20_010L
        travel.begin(j, town("Rouen", "FRA"), today)
        travel.settle(j, today)
        val coins = j.coins
        j.days[today] = 7_000
        val first = travel.settle(j, today)
        assertTrue(first.any { it.news == News.QUEST && it.ref == "d$today" })
        assertEquals(coins + Quests.DAY_REWARD, j.coins, 1e-9)
        travel.settle(j, today)
        assertEquals(coins + Quests.DAY_REWARD, j.coins, 1e-9)
        val q = Quests.of(j, today)
        assertTrue(q[0].kind == QuestKind.DAY_STEPS && q[0].done && q[0].paid)
        // Begun partway through the week, the days task asks only for the days left in it.
        assertTrue(q[2].target <= 7 - (today - Quests.weekStart(today)).toInt())
        assertFalse(q[1].done)
    }
}
