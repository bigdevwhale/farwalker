package app.farwalker.domain

enum class MedalKind { KM, ROUND, COUNTRIES, SIGHTS, CAPES, PARTS, DAY, SEA, TASKS }

/** A medal: won once [value] of the journey reaches [target]. */
class Medal(val kind: MedalKind, val target: Int, val value: (Journey) -> Double) {
    val key: String get() = "${kind.name.lowercase()}$target"
}

/** The milestones of the road: distances, countries, sights, capes, a long day, the sea, tasks done. */
object Medals {
    /** Round the earth at the equator, km. */
    const val EARTH_KM = 40_075

    @Volatile private var cached: Pair<World, List<Medal>>? = null

    fun all(world: World): List<Medal> {
        cached?.let { (w, l) -> if (w === world) return l }
        val sights = world.sights.size
        val capes = world.towns.count { it.cape }
        val l = buildList {
            for (n in listOf(10, 100, 500, 1_000, 2_500, 5_000, 10_000, 20_000)) add(Medal(MedalKind.KM, n) { it.walked })
            add(Medal(MedalKind.ROUND, EARTH_KM) { it.walked + it.rode + it.sailed })
            for (n in listOf(2, 5, 10, 25, 50, 100, 150, 200)) add(Medal(MedalKind.COUNTRIES, n) { it.stamps.size.toDouble() })
            for (n in listOf(1, 5, 10, 25, 50, sights).filter { it in 1..sights }.distinct()) add(Medal(MedalKind.SIGHTS, n) { it.sights.size.toDouble() })
            for (n in listOf(1, 5, 10, capes).filter { it in 1..capes }.distinct()) add(Medal(MedalKind.CAPES, n) { it.capes.size.toDouble() })
            for (n in listOf(2, 4, 7)) add(Medal(MedalKind.PARTS, n) { it.continents.size.toDouble() })
            for (n in listOf(10_000, 20_000, 30_000, 50_000)) add(Medal(MedalKind.DAY, n) { j -> (j.days.values.maxOrNull() ?: 0).toDouble() })
            for (n in listOf(500, 5_000, 20_000)) add(Medal(MedalKind.SEA, n) { it.sailed })
            for (n in listOf(1, 10, 50, 100, 365)) add(Medal(MedalKind.TASKS, n) { it.quests.size.toDouble() })
        }
        cached = world to l
        return l
    }

    fun of(world: World, key: String): Medal? = all(world).firstOrNull { it.key == key }
}
