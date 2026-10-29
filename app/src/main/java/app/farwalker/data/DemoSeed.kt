package app.farwalker.data

import android.content.Context
import app.farwalker.AppContainer
import app.farwalker.domain.Journey
import java.io.File
import java.time.LocalDate
import kotlin.random.Random

/**
 * The demo build's journey: five months walked by the real rules from Moscow, by way of Helsinki and
 * Stockholm (two ferries), with a train now and then, across Europe to the end of it at Cabo da Roca and on to Morocco.
 */
object DemoSeed {
    private val ROUTE = listOf("Saint Petersburg" to "RUS", "Helsinki" to "FIN", "Stockholm" to "SWE", "Copenhagen" to "DNK", "Paris" to "FRA", "Lisbon" to "PRT", "Cabo da Roca" to "PRT", "Rabat" to "MAR", "Marrakesh" to "MAR")

    suspend fun seedIfEmpty(context: Context, c: AppContainer) {
        val file = File(context.filesDir, "journey.json")
        if (file.exists()) return
        val world = c.world
        val travel = c.travel
        fun town(en: String, iso: String) = world.towns.first { it.en == en && it.iso == iso }.id
        val rnd = Random(7)
        val today = LocalDate.now().toEpochDay()
        val start = today - 160
        val j = Journey()
        travel.begin(j, town("Moscow", "RUS"), start)
        var goal = 0
        travel.aim(j, town(ROUTE[0].first, ROUTE[0].second))
        for (d in start until today) {
            val steps = (5_500 + rnd.nextInt(7_500)) * (if (rnd.nextInt(7) == 0) 2 else 1)
            j.days[d] = steps
            travel.walk(j, steps * 0.72 / 1000.0, d)
            val noon = d * 86_400_000L + 12 * 3_600_000L
            travel.ship(j)?.let { s -> if (j.coins >= s.fare) travel.board(j, s, noon) }
            if (d % 10 == 1L) travel.trains(j).lastOrNull { it.km < 450 && it.fare <= j.coins }?.let { travel.board(j, it, noon) }
            travel.tick(j, (d + 1) * 86_400_000L, d + 1)
            if (j.dest < 0 && j.ride == null && goal + 1 < ROUTE.size) {
                goal++
                travel.aim(j, town(ROUTE[goal].first, ROUTE[goal].second))
            }
        }
        // Today is under way too.
        j.days[today] = 6_840
        travel.walk(j, 6_840 * 0.72 / 1000.0, today)
        c.settings.update { it.copy(onboarded = true, told = j.notes.size) }
        Store.write(file, j)
        c.trip.reload()
    }
}
