package app.farwalker

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import app.farwalker.data.DemoSeed
import app.farwalker.data.SettingsStore
import app.farwalker.data.Trip
import app.farwalker.domain.Geo
import app.farwalker.domain.Travel
import app.farwalker.domain.Weather
import app.farwalker.domain.World
import app.farwalker.work.Alarms
import app.farwalker.work.StepSensor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AppContainer(context: Context) {
    val app = context.applicationContext
    val settings = SettingsStore(app)
    val world: World by lazy {
        val a = app.assets
        World.load(a.open("world/countries.tsv"), a.open("world/towns.tsv"), a.open("world/edges.tsv"), a.open("world/shapes.bin"))
    }
    val travel by lazy { Travel(world) }
    val trip by lazy { Trip(File(app.filesDir, "journey.json"), travel, stride = { settings.now().stride / 100.0 }) }
    val sensor = StepSensor(app)
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

    /** Reads the step counter and moves the journey on. */
    suspend fun sync() {
        val v = sensor.read()
        if (v != null) trip.reading(v) else trip.tick()
    }
}

class FarwalkerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
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
