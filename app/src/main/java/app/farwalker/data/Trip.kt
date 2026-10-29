package app.farwalker.data

import app.farwalker.domain.Journey
import app.farwalker.domain.Note
import app.farwalker.domain.ShipOffer
import app.farwalker.domain.Steps
import app.farwalker.domain.TrainOffer
import app.farwalker.domain.Travel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * The journey on disk: every change loads nothing (it's kept in memory), runs against the journey
 * under a lock, and writes it back whole. [state] gets a fresh copy after each change, so screens see it.
 */
class Trip(private val file: File, val travel: Travel, private val stride: suspend () -> Double, private val zone: () -> ZoneId = { ZoneId.systemDefault() }) {
    private val lock = Mutex()
    private var j: Journey? = null
    private val _state = MutableStateFlow<Journey?>(null)
    val state: StateFlow<Journey?> = _state

    private suspend fun journey(): Journey = j ?: withContext(Dispatchers.IO) { Store.read(file) }.also { j = it }

    suspend fun <T> change(block: (Journey) -> T): T = lock.withLock {
        val jj = journey()
        val out = block(jj)
        withContext(Dispatchers.IO) { Store.write(file, jj) }
        _state.value = Store.decode(Store.encode(jj))
        out
    }

    suspend fun refresh() {
        lock.withLock { _state.value = Store.decode(Store.encode(journey())) }
    }

    /** Reads the file again: it was written behind the journey's back (the demo's seed). */
    suspend fun reload() {
        lock.withLock { j = null; _state.value = Store.decode(Store.encode(journey())) }
    }

    private fun today(): Long = LocalDate.now(zone()).toEpochDay()

    suspend fun begin(home: Int) = change { if (!it.started) travel.begin(it, home, today()) }

    /** Starts over from a new home: everything walked so far is let go. */
    suspend fun restart(home: Int) = lock.withLock {
        val old = journey()
        val fresh = Journey()
        travel.begin(fresh, home, today())
        fresh.lastValue = old.lastValue
        fresh.lastAt = old.lastAt
        withContext(Dispatchers.IO) { Store.write(file, fresh) }
        j = fresh
        _state.value = Store.decode(Store.encode(fresh))
    }

    suspend fun aim(goal: Int) = change { travel.aim(it, goal) }
    suspend fun halt() = change { travel.halt(it) }
    suspend fun board(t: TrainOffer) = change { travel.board(it, t, System.currentTimeMillis()) }
    suspend fun board(s: ShipOffer) = change { travel.board(it, s, System.currentTimeMillis()) }

    /** The clock alone: rides move on, the night is written down. */
    suspend fun tick(): List<Note> = change { if (it.started) travel.tick(it, System.currentTimeMillis(), today()) else emptyList() }

    /**
     * A reading of the step counter: the steps since the last one are spread over the days they were
     * walked on and walked along the road.
     */
    suspend fun reading(value: Long, at: Long = System.currentTimeMillis()): List<Note> {
        val m = stride()
        return change { jj ->
            val out = mutableListOf<Note>()
            if (jj.lastValue >= 0 && jj.started) {
                val d = Steps.delta(jj.lastValue, value)
                for ((day, n) in Steps.spread(d, if (jj.lastAt > 0) jj.lastAt else at, at, zone()).toSortedMap()) {
                    jj.days[day] = (jj.days[day] ?: 0) + n
                    out += travel.walk(jj, n * m / 1000.0, day)
                }
            }
            jj.lastValue = value
            jj.lastAt = at
            if (jj.started) out += travel.tick(jj, at, today())
            out
        }
    }

    /** Steps added by hand (debug and demo builds). */
    suspend fun addSteps(n: Int): List<Note> {
        val m = stride()
        return change { jj ->
            val day = today()
            jj.days[day] = (jj.days[day] ?: 0) + n
            travel.walk(jj, n * m / 1000.0, day)
        }
    }
}
