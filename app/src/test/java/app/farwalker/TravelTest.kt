package app.farwalker

import app.farwalker.domain.Journey
import app.farwalker.domain.Mode
import app.farwalker.domain.News
import app.farwalker.domain.Travel
import app.farwalker.domain.World
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TravelTest {
    private val world: World by lazy {
        val dir = File("src/main/assets/world")
        World.load(File(dir, "countries.tsv").inputStream(), File(dir, "towns.tsv").inputStream(),
            File(dir, "edges.tsv").inputStream(), File(dir, "shapes.bin").inputStream())
    }
    private val travel by lazy { Travel(world) }

    private fun town(en: String, iso: String) = world.towns.first { it.en == en && it.iso == iso }.id

    @Test
    fun theWorldHangsTogether() {
        assertEquals(195, world.states)
        for (c in world.countries.values) assertTrue("${c.en} has nowhere to go", world.towns.any { it.iso == c.iso })
        // Every town can be reached from Moscow.
        val seen = BooleanArray(world.towns.size)
        val stack = ArrayDeque(listOf(town("Moscow", "RUS")))
        while (stack.isNotEmpty()) {
            val t = stack.removeLast()
            if (seen[t]) continue
            seen[t] = true
            for (e in world.links[t]) stack += world.edge(e).other(t)
        }
        assertTrue(seen.all { it })
    }

    @Test
    fun walkingFromMoscowToLisbonStampsTheWayAcrossEurope() {
        val j = Journey()
        travel.begin(j, town("Moscow", "RUS"), 0)
        assertTrue(travel.aim(j, town("Lisbon", "PRT")))
        val total = travel.left(j)
        assertTrue("$total km", total in 4000.0..6500.0)
        // Europe goes round by land: no ship on the way.
        assertTrue(j.legs.none { world.edge(it.edge).sea })
        var day = 0L
        while (j.dest >= 0 && day < 2000) {
            travel.walk(j, 8.0, day)
            travel.tick(j, 0, ++day)
        }
        assertEquals(-1, j.dest)
        assertEquals("PRT", j.iso)
        assertTrue(j.stamps.keys.containsAll(listOf("RUS", "FRA", "ESP", "PRT")))
        assertTrue(j.notes.any { it.news == News.ARRIVED })
        assertEquals(day.toInt(), j.nights.size)
        assertEquals(j.walked, j.coins, 1e-6)
    }

    @Test
    fun theSeaWaitsForAShip() {
        val j = Journey()
        travel.begin(j, town("Lisbon", "PRT"), 0)
        assertTrue(travel.aim(j, town("New York City", "USA")))
        val ship = travel.ship(j)
        assertNotNull(ship)
        // Walking doesn't cross the ocean: the coins pile up at the port.
        travel.walk(j, 300.0, 0)
        assertEquals(world.town(j.town).en, "Lisbon")
        travel.walk(j, ship!!.fare.toDouble(), 0)
        assertTrue(travel.board(j, ship, 0))
        assertFalse(travel.aim(j, town("Madrid", "ESP")))
        travel.tick(j, 3_600_000L * 24, 1)
        assertNotNull(j.ride)
        assertTrue(j.at > 0)
        travel.tick(j, 3_600_000L * 24 * 30, 30)
        assertNull(j.ride)
        assertEquals("New York City", world.town(j.town).en)
        assertTrue("USA" in j.stamps)
        assertTrue(j.notes.any { it.news == News.DOCKED })
        assertTrue(j.notes.any { it.news == News.CONTINENT })
    }

    @Test
    fun trainsLeaveFromStationsAndCostCoins() {
        val j = Journey()
        travel.begin(j, town("Moscow", "RUS"), 0)
        travel.aim(j, town("Paris", "FRA"))
        val offers = travel.trains(j)
        assertTrue(offers.isNotEmpty())
        val far = offers.last()
        assertFalse(travel.board(j, far, 0))
        travel.walk(j, far.fare.toDouble() + 1, 0)
        // From the middle of the road the ride starts where the traveller stands, and costs that much less.
        val again = travel.trains(j).first { it.town == far.town }
        assertEquals(far.km - j.walked, again.km, 1e-6)
        assertTrue(travel.board(j, again, 0))
        travel.tick(j, 3_600_000L * 1000, 1)
        assertEquals(far.town, j.town)
    }

    @Test
    fun aTrainRunsOnTheClock() {
        val j = Journey()
        travel.begin(j, town("Moscow", "RUS"), 0)
        travel.aim(j, town("Paris", "FRA"))
        j.coins = 10_000.0
        val offer = travel.trains(j).last()
        assertTrue(travel.board(j, offer, 0))
        travel.tick(j, 3_600_000L * 1000, 2)
        assertNull(j.ride)
        assertEquals(offer.town, j.town)
        assertEquals(offer.km, j.rode, 1e-6)
        assertTrue(j.trail.last().mode == Mode.TRAIN)
    }

    @Test
    fun turningBackHalfwayTakesTheShorterWay() {
        val j = Journey()
        val moscow = town("Moscow", "RUS")
        travel.begin(j, moscow, 0)
        travel.aim(j, town("Saint Petersburg", "RUS"))
        travel.walk(j, 20.0, 0)
        assertTrue(j.at > 0)
        travel.aim(j, moscow)
        assertEquals(1, j.legs.size)
        assertEquals(moscow, travel.router.to(j.legs[0]))
        travel.walk(j, 25.0, 0)
        assertEquals(moscow, j.town)
    }

    @Test
    fun searchFindsTownsInBothLanguages() {
        assertEquals("Prague", world.search("Прага").first().en)
        assertEquals("Cape Horn", world.search("горн").first { it.cape }.en)
        assertTrue(world.search("Japan").first().capital)
    }
}
