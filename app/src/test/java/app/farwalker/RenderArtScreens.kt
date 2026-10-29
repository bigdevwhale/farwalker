package app.farwalker

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import app.farwalker.data.DemoSeed
import app.farwalker.domain.Sky
import app.farwalker.domain.WeatherNow
import app.farwalker.ui.Home
import app.farwalker.ui.Words
import app.farwalker.ui.atlas.AtlasScreen
import app.farwalker.ui.atlas.Camera
import app.farwalker.ui.atlas.GoalSheet
import app.farwalker.ui.journal.JournalScreen
import app.farwalker.ui.onboarding.Onboarding
import app.farwalker.ui.passport.PassportScreen
import app.farwalker.ui.theme.C
import app.farwalker.ui.theme.FarwalkerTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.ZonedDateTime

/** Every main screen over the demo journey, drawn to PNGs (run with `-Prender`), in both languages. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ScreensBase {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    abstract val lang: String
    private val out by lazy { File(System.getProperty("render.out") ?: "build/art", "screens-$lang").apply { mkdirs() } }

    private fun home(): Home = runBlocking {
        val app = ApplicationProvider.getApplicationContext<FarwalkerApp>()
        val c = app.container
        DemoSeed.seedIfEmpty(app, c)
        c.trip.reload()
        val j = c.trip.state.value!!
        val (lat, lon) = c.travel.position(j)
        val s = c.settings.now().copy(weather = WeatherNow(Sky.CLOUDY, 14.0, 3.0, System.currentTimeMillis(), lat, lon))
        Home(j, s, c.world, c.travel, Words(app, app.ru, c.world), ZonedDateTime.now().withHour(16).withMinute(20))
    }

    private fun shot(name: String, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent { FarwalkerTheme { Box(Modifier.fillMaxSize().background(C.paper)) { content() } } }
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(500)
        rule.runOnUiThread {
            val view = rule.activity.window.decorView
            val image = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(image))
            File(out, "$name.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun camera(h: Home, k: Double) = Camera(k = k).also { cam -> h.travel.position(h.j).let { (lat, lon) -> cam.look(lat, lon) } }

    @Test fun welcome() = shot("0-welcome") { Onboarding() }
    @Test fun atlas() = home().let { h -> shot("1-atlas") { AtlasScreen(h, camera(h, 30.0), {}, {}) } }
    @Test fun atlasFar() = home().let { h -> shot("2-atlas-far") { AtlasScreen(h, camera(h, 3.0), {}, {}) } }
    @Test fun atlasNear() = home().let { h -> shot("3-atlas-near") { AtlasScreen(h, camera(h, 90.0), {}, {}) } }
    @Test fun passport() = home().let { h -> shot("4-passport") { PassportScreen(h) } }
    @Test fun journal() = home().let { h -> shot("5-journal") { JournalScreen(h) } }
    @Test fun goal() = home().let { h -> shot("6-goal") { GoalSheet(h, {}, {}) } }
}

@Config(sdk = [34], qualifiers = "en-w411dp-h891dp-xxhdpi")
class RenderArtScreensEn : ScreensBase() { override val lang = "en" }

@Config(sdk = [34], qualifiers = "ru-w411dp-h891dp-xxhdpi")
class RenderArtScreensRu : ScreensBase() { override val lang = "ru" }
