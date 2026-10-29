package app.farwalker.domain

import java.util.PriorityQueue

/** One stretch of the way: an edge, walked from its first town ([forward]) or from its second. */
data class Leg(val edge: Int, val forward: Boolean)

class Router(private val world: World) {
    fun from(leg: Leg): Int = world.edge(leg.edge).let { if (leg.forward) it.a else it.b }
    fun to(leg: Leg): Int = world.edge(leg.edge).let { if (leg.forward) it.b else it.a }

    /**
     * The way from one town to another. Roads count their length; a sea lane counts less than its
     * length (the ship does the work) plus a fixed price for going aboard, so the way keeps to land
     * where a road goes round and takes the ship where the sea is in the way.
     */
    fun route(start: Int, goal: Int): List<Leg>? {
        if (start == goal) return emptyList()
        val n = world.towns.size
        val dist = DoubleArray(n) { Double.MAX_VALUE }
        val via = IntArray(n) { -1 }
        dist[start] = 0.0
        val queue = PriorityQueue<Pair<Double, Int>>(compareBy { it.first })
        queue += 0.0 to start
        while (queue.isNotEmpty()) {
            val (d, t) = queue.poll()!!
            if (d > dist[t]) continue
            if (t == goal) break
            for (eid in world.links[t]) {
                val e = world.edge(eid)
                val o = e.other(t)
                val nd = d + cost(e)
                if (nd < dist[o]) {
                    dist[o] = nd
                    via[o] = eid
                    queue += nd to o
                }
            }
        }
        if (via[goal] < 0) return null
        val legs = ArrayDeque<Leg>()
        var t = goal
        while (t != start) {
            val e = world.edge(via[t])
            val from = e.other(t)
            legs.addFirst(Leg(e.id, e.a == from))
            t = from
        }
        return legs.toList()
    }

    private fun cost(e: Edge): Double = if (e.sea) e.km * SEA + BOARD else e.km.toDouble()

    companion object {
        const val SEA = 0.6
        const val BOARD = 400.0
    }
}
