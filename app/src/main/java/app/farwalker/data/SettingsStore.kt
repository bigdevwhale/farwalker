package app.farwalker.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.farwalker.domain.Sky
import app.farwalker.domain.WeatherNow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

data class Settings(
    val onboarded: Boolean = false,
    /** The length of a step, in cm: steps times this is the km walked. */
    val stride: Int = 72,
    /** The walker's height in cm, if told: the stride is worked out from it. 0 is not told. */
    val height: Int = 0,
    /** The weather where the traveller is, as last fetched. */
    val weather: WeatherNow? = null,
    /** News of the road (a new country, a ship in port) while the app is out of sight. */
    val notify: Boolean = true,
    /** How many of the journal's notes have been told already. */
    val told: Int = 0,
)

/** A walking step is about this share of the walker's height. */
fun strideFor(height: Int): Int = Math.round(height * 0.415).toInt()

class SettingsStore(context: Context) {
    private val store = context.applicationContext.dataStore

    val settings: Flow<Settings> = store.data.map { it.read() }

    private fun Preferences.read(): Settings {
        val sky = Sky.of(this[SKY])
        return Settings(
            onboarded = this[ONBOARDED] ?: false,
            stride = this[STRIDE] ?: 72,
            height = this[HEIGHT] ?: 0,
            weather = if (sky != null) WeatherNow(sky, this[TEMP] ?: 0.0, this[WIND] ?: 0.0, this[WEATHER_AT] ?: 0, this[W_LAT] ?: 0.0, this[W_LON] ?: 0.0) else null,
            notify = this[NOTIFY] ?: true,
            told = this[TOLD] ?: 0,
        )
    }

    suspend fun now(): Settings = settings.first()

    suspend fun update(change: (Settings) -> Settings) {
        store.edit {
            val s = change(it.read())
            it[ONBOARDED] = s.onboarded
            it[STRIDE] = s.stride
            it[HEIGHT] = s.height
            val w = s.weather
            if (w != null) {
                it[SKY] = w.sky.name; it[TEMP] = w.temp; it[WIND] = w.wind; it[WEATHER_AT] = w.at; it[W_LAT] = w.lat; it[W_LON] = w.lon
            } else {
                it.remove(SKY); it.remove(TEMP); it.remove(WIND); it.remove(WEATHER_AT); it.remove(W_LAT); it.remove(W_LON)
            }
            it[NOTIFY] = s.notify
            it[TOLD] = s.told
        }
    }

    private companion object {
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val STRIDE = intPreferencesKey("stride")
        val HEIGHT = intPreferencesKey("height")
        val SKY = stringPreferencesKey("sky")
        val TEMP = doublePreferencesKey("temp")
        val WIND = doublePreferencesKey("wind")
        val WEATHER_AT = longPreferencesKey("weather_at")
        val W_LAT = doublePreferencesKey("w_lat")
        val W_LON = doublePreferencesKey("w_lon")
        val NOTIFY = booleanPreferencesKey("notify")
        val TOLD = intPreferencesKey("told")
    }
}
