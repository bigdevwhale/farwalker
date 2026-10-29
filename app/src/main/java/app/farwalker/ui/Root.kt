package app.farwalker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.farwalker.R
import app.farwalker.container
import app.farwalker.data.Settings
import app.farwalker.domain.Journey
import app.farwalker.domain.Travel
import app.farwalker.domain.World
import app.farwalker.ru
import app.farwalker.ui.atlas.AtlasScreen
import app.farwalker.ui.atlas.Camera
import app.farwalker.ui.atlas.GoalSheet
import app.farwalker.ui.journal.JournalScreen
import app.farwalker.ui.onboarding.Onboarding
import app.farwalker.ui.passport.PassportScreen
import app.farwalker.ui.settings.SettingsScreen
import app.farwalker.ui.theme.C
import app.farwalker.ui.theme.T
import app.farwalker.work.Notes
import app.farwalker.work.WalkService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

/** Everything a screen reads, at one moment. */
@Immutable
class Home(val j: Journey, val settings: Settings, val world: World, val travel: Travel, val words: Words, val now: ZonedDateTime) {
    val ru = words.ru
    val today: Long = now.toLocalDate().toEpochDay()
    val day: Int = (today - j.start + 1).toInt().coerceAtLeast(1)
    val stepsToday: Int = j.days[today] ?: 0
    val kmToday: Double = j.dayKm[today] ?: 0.0

    /** The traveller's pace: km a day over the last two weeks walked, or five to begin with. */
    val pace: Double = run {
        val recent = (1..14).mapNotNull { j.dayKm[today - it] }.filter { it > 0.1 }
        if (recent.size >= 3) recent.average() else 5.0
    }
}

enum class Tab { ATLAS, PASSPORT, JOURNAL }

sealed interface Overlay {
    data object Goal : Overlay
    data object Settings : Overlay
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FarwalkerRoot(settings: Settings) {
    val ctx = LocalContext.current
    val c = ctx.container
    val state by c.trip.state.collectAsStateWithLifecycle()
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        c.trip.refresh()
        while (true) {
            // The weather throttles itself to once an hour; asking here also covers a first open,
            // when the journey wasn't read yet as the app came to the front.
            runCatching { c.refreshWeather() }
            delay(20_000)
            now = ZonedDateTime.now()
            c.trip.tick()
        }
    }
    // While the app is open: read the counter as it ticks, move the journey on, fetch the weather.
    LifecycleResumeEffect(Unit) {
        now = ZonedDateTime.now()
        WalkService.stop(ctx)
        val job = c.scope.launch {
            c.trip.tick()
            Notes.seen(ctx)
            launch { runCatching { c.refreshWeather() } }
            if (c.sensor.allowed) c.sensor.live().conflate().collect { v -> c.trip.reading(v); Notes.seen(ctx); delay(1000) }
        }
        // Out of sight: a quiet foreground service keeps counting until it's open again.
        onPauseOrDispose { job.cancel(); WalkService.start(ctx) }
    }
    val j = state
    if (!settings.onboarded || j == null || !j.started) {
        if (j != null || !settings.onboarded) Onboarding()
        else Box(Modifier.fillMaxSize().background(C.paper))
        return
    }
    val words = remember(ctx.ru) { Words(ctx, ctx.ru, c.world) }
    val home = Home(j, settings, c.world, c.travel, words, now)

    var tab by remember { mutableStateOf(Tab.ATLAS) }
    val camera = remember { Camera(k = 30.0).also { cam -> c.travel.position(j).let { (lat, lon) -> cam.look(lat, lon) } } }
    val overlays = remember { mutableStateListOf<Overlay>() }
    fun push(o: Overlay) = overlays.add(o)
    fun pop() { if (overlays.isNotEmpty()) overlays.removeAt(overlays.lastIndex) }
    BackHandler(enabled = overlays.isNotEmpty() || tab != Tab.ATLAS) { if (overlays.isNotEmpty()) pop() else tab = Tab.ATLAS }

    Box(Modifier.fillMaxSize().background(C.paper).imePadding()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    Tab.ATLAS -> AtlasScreen(home, camera, onGoal = { push(Overlay.Goal) }, onSettings = { push(Overlay.Settings) })
                    Tab.PASSPORT -> PassportScreen(home)
                    Tab.JOURNAL -> JournalScreen(home)
                }
            }
            if (!WindowInsets.isImeVisible) TabBar(tab) { tab = it }
        }
        when (overlays.lastOrNull()) {
            Overlay.Goal -> GoalSheet(home, onClose = { pop() }, onSet = { pop(); camera.follow = true; tab = Tab.ATLAS })
            Overlay.Settings -> SettingsScreen(home, onBack = { pop() })
            null -> Unit
        }
    }
}

@Composable
private fun TabBar(tab: Tab, onTab: (Tab) -> Unit) {
    val items = listOf(Tab.ATLAS to R.string.tab_atlas, Tab.PASSPORT to R.string.tab_passport, Tab.JOURNAL to R.string.tab_journal)
    Column(Modifier.fillMaxWidth().background(C.paper2)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.ink))
        Box(Modifier.fillMaxWidth().padding(top = 2.dp).height(0.6.dp).background(C.ink.copy(alpha = 0.5f)))
        Row(Modifier.fillMaxWidth().navigationBarsPadding()) {
            for ((t, label) in items) {
                val sel = t == tab
                Column(
                    Modifier.weight(1f).clickable { onTab(t) }.padding(top = 6.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TabIcon(t, if (sel) C.ink else C.faint, if (sel) C.red else C.faint)
                    Text(stringResource(label).uppercase(), style = T.Eyebrow.copy(color = if (sel) C.ink else C.muted))
                }
            }
        }
    }
}

/** Little engravings for the tabs: a compass rose, a passport, a journal with a ribbon. */
@Composable
private fun TabIcon(t: Tab, ink: Color, accent: Color) {
    Canvas(Modifier.size(26.dp).padding(2.dp)) {
        val w = size.width
        val s = density
        when (t) {
            Tab.ATLAS -> {
                val c = Offset(w / 2, w / 2)
                drawCircle(ink, w * 0.42f, c, style = Stroke(1.2f * s))
                val n = Path().apply { moveTo(w / 2, w * 0.05f); lineTo(w * 0.6f, w / 2); lineTo(w / 2, w * 0.95f); lineTo(w * 0.4f, w / 2); close() }
                drawPath(n, ink, style = Stroke(1f * s))
                val top = Path().apply { moveTo(w / 2, w * 0.05f); lineTo(w * 0.6f, w / 2); lineTo(w * 0.4f, w / 2); close() }
                drawPath(top, accent)
            }
            Tab.PASSPORT -> {
                drawRoundRect(ink, Offset(w * 0.18f, w * 0.06f), androidx.compose.ui.geometry.Size(w * 0.64f, w * 0.88f), androidx.compose.ui.geometry.CornerRadius(2 * s), style = Stroke(1.3f * s))
                drawCircle(accent, w * 0.16f, Offset(w / 2, w * 0.42f), style = Stroke(1.3f * s))
                drawLine(ink, Offset(w * 0.32f, w * 0.75f), Offset(w * 0.68f, w * 0.75f), 1f * s)
            }
            Tab.JOURNAL -> {
                drawRect(ink, Offset(w * 0.16f, w * 0.08f), androidx.compose.ui.geometry.Size(w * 0.66f, w * 0.84f), style = Stroke(1.3f * s))
                for (i in 0..2) drawLine(ink, Offset(w * 0.3f, w * (0.32f + i * 0.17f)), Offset(w * 0.68f, w * (0.32f + i * 0.17f)), 0.9f * s)
                val ribbon = Path().apply { moveTo(w * 0.7f, w * 0.08f); lineTo(w * 0.7f, w); lineTo(w * 0.62f, w * 0.9f); lineTo(w * 0.54f, w); lineTo(w * 0.54f, w * 0.08f); close() }
                drawPath(ribbon, accent)
            }
        }
    }
}
