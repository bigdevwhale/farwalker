package app.farwalker

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import app.farwalker.data.DemoSeed
import app.farwalker.data.SettingsStore
import app.farwalker.data.Trip
import app.farwalker.domain.Geo
import app.farwalker.domain.Roadside
import app.farwalker.domain.RoadsideNow
import app.farwalker.domain.Travel
import app.farwalker.domain.Weather
import app.farwalker.domain.World
import app.farwalker.work.Alarms
import app.farwalker.work.HealthSteps
import app.farwalker.work.StepSensor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

class AppContainer(context: Context) {
    val app = context.applicationContext
    val settings = SettingsStore(app)
    val world: World by lazy {
        val a = app.assets
        World.load(a.open("world/countries.tsv"), a.open("world/towns.tsv"), a.open("world/edges.tsv"), a.open("world/shapes.bin"), a.open("world/sights.tsv"))
    }
    val travel by lazy { Travel(world) }
    val trip by lazy { Trip(File(app.filesDir, "journey.json"), travel, stride = { settings.now().stride / 100.0 }) }
    val sensor = StepSensor(app)
    val health = HealthSteps(app)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Fire-and-forget so a write survives the screen being closed right after tapping. */
    fun write(block: suspend AppContainer.() -> Unit) {
        scope.launch { block() }
    }

    /** Fetches the weather where the traveller is, if the last report is stale or from elsewhere. */
    suspend fun refreshWeather(force: Boolean = false) {
        val j = trip.state.value ?: return
        if (!j.started) return
        val (lat, lon) = travel.position(j)
        val s = settings.now()
        val w = s.weather
        if (!force && w != null && System.currentTimeMillis() - w.at < 3_600_000 && Geo.km(lat, lon, w.lat, w.lon) < Weather.FRESH_KM) return
        val now = runCatching { withContext(Dispatchers.IO) { Weather.fetch(lat, lon) } }.getOrNull() ?: return
        settings.update { it.copy(weather = now) }
    }

    private val roadsideFile = File(app.filesDir, "roadside.json")
    /** The notable place by the road, as last looked up. */
    val roadside = kotlinx.coroutines.flow.MutableStateFlow(Roadside.load(roadsideFile))

    /**
     * Looks up what stands by the road where the traveller is: again only after [Roadside.EVERY_KM] along the way, or
     * at once when [another] (a new place, not one already shown here).
     */
    suspend fun refreshRoadside(another: Boolean = false) {
        val j = trip.state.value ?: return
        if (!j.started) return
        val (lat, lon) = travel.position(j)
        val ru = app.ru
        val was = roadside.value
        if (!another && !Roadside.stale(was, lat, lon, ru)) return
        val seen = if (was != null && was.ru == ru && !Roadside.stale(was, lat, lon, ru)) was.seen else emptyList()
        val sight = runCatching { withContext(Dispatchers.IO) { Roadside.near(lat, lon, ru, seen.toSet()) } }
            .getOrElse { return }
        // Nothing new nearby: on «another», start the round of this place again.
        val next = if (sight == null && another && seen.isNotEmpty())
            runCatching { withContext(Dispatchers.IO) { Roadside.near(lat, lon, ru, emptySet()) } }.getOrNull()
        else sight
        val now = RoadsideNow(next, lat, lon, ru, System.currentTimeMillis(),
            (if (sight == null) emptyList() else seen) + listOfNotNull(next?.title))
        roadside.value = now
        withContext(Dispatchers.IO) { Roadside.save(roadsideFile, now) }
    }

    /** Reads the steps (the phone's counter, or Health Connect if chosen) and moves the journey on. */
    suspend fun sync() {
        val from = settings.now().healthFrom
        if (from >= 0) {
            // A week back at most: watches and bands may hand over a day's steps only when they next sync.
            val today = LocalDate.now()
            val start = maxOf(LocalDate.ofEpochDay(from), today.minusDays(HEALTH_DAYS))
            val totals = health.days(start, today)
            if (totals != null) trip.health(totals) else trip.tick()
            return
        }
        val v = sensor.read()
        if (v != null) trip.reading(v) else trip.tick()
    }

    private companion object {
        const val HEALTH_DAYS = 6L
    }
}

class FarwalkerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Load the world and fold its names for search before the first keystroke needs them.
        container.scope.launch { container.world.search("aa") }
        if (BuildConfig.DEMO) container.scope.launch { DemoSeed.seedIfEmpty(this@FarwalkerApp, container) }
        runCatching { Alarms.schedule(this) }
    }
}

val Context.container: AppContainer get() = (applicationContext as FarwalkerApp).container

/** Resources in the in-app language, which AppCompat doesn't localise outside activities before Android 13. */
fun Context.localized(): Context {
    val tags = AppCompatDelegate.getApplicationLocales()
    if (tags.isEmpty) return this
    val config = Configuration(resources.configuration)
    config.setLocale(tags[0])
    return createConfigurationContext(config)
}

/** Is this context in Russian? */
val Context.ru: Boolean get() = resources.configuration.locales[0].language == "ru"
