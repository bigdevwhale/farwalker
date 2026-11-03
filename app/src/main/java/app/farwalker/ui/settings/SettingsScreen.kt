package app.farwalker.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import app.farwalker.BuildConfig
import app.farwalker.R
import app.farwalker.container
import app.farwalker.data.strideFor
import app.farwalker.domain.Town
import app.farwalker.ui.Home
import app.farwalker.ui.components.Btn
import app.farwalker.ui.components.CommunityLinks
import app.farwalker.ui.components.Field
import app.farwalker.ui.components.PaperDialog
import app.farwalker.ui.components.Rule
import app.farwalker.ui.components.Sheet
import app.farwalker.ui.theme.C
import app.farwalker.ui.theme.T
import app.farwalker.work.HealthSteps
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(home: Home, onBack: () -> Unit) {
    val c = LocalContext.current.container
    val s = home.settings
    var stride by remember { mutableFloatStateOf(s.stride.toFloat()) }
    var height by remember { mutableFloatStateOf(s.height.toFloat()) }
    var restart by remember { mutableStateOf(false) }
    Sheet(stringResource(R.string.s_title), onBack = onBack) {
        Text(stringResource(R.string.s_height), style = T.Heading)
        Text(
            if (height > 0) stringResource(R.string.s_height_v, height.roundToInt(), strideFor(height.roundToInt())) else stringResource(R.string.s_height_none),
            style = T.Body,
        )
        Slider(
            if (height > 0) height else 173f, { height = it; stride = strideFor(it.roundToInt()).toFloat() }, valueRange = 120f..215f,
            onValueChangeFinished = { c.write { settings.update { it.copy(height = height.roundToInt(), stride = strideFor(height.roundToInt())) } } },
            colors = SliderDefaults.colors(thumbColor = C.red, activeTrackColor = C.red, inactiveTrackColor = C.paper2),
        )
        Rule(Modifier.padding(vertical = 14.dp))

        Text(stringResource(R.string.s_stride), style = T.Heading)
        Text(stringResource(R.string.s_stride_v, stride.roundToInt()), style = T.Body)
        Slider(
            stride, { stride = it }, valueRange = 45f..110f,
            onValueChangeFinished = { c.write { settings.update { it.copy(stride = stride.roundToInt()) } } },
            colors = SliderDefaults.colors(thumbColor = C.red, activeTrackColor = C.red, inactiveTrackColor = C.paper2),
        )
        Text(stringResource(R.string.s_stride_hint), style = T.Small)
        Rule(Modifier.padding(vertical = 14.dp))

        Text(stringResource(R.string.s_source), style = T.Heading)
        StepSource(s.healthFrom >= 0)
        Rule(Modifier.padding(vertical = 14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.s_notify), style = T.Heading)
                Text(stringResource(R.string.s_notify_hint), style = T.Small)
            }
            Switch(
                s.notify, { on -> c.write { settings.update { it.copy(notify = on) } } },
                colors = SwitchDefaults.colors(checkedTrackColor = C.red, checkedThumbColor = C.paper, uncheckedTrackColor = C.paper2, uncheckedBorderColor = C.ink, uncheckedThumbColor = C.ink),
            )
        }
        Rule(Modifier.padding(vertical = 14.dp))

        Text(stringResource(R.string.s_language), style = T.Heading)
        val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            for ((tag, label) in listOf("" to stringResource(R.string.s_lang_system), "ru" to "Русский", "en" to "English")) {
                Btn(label, primary = current == tag) {
                    AppCompatDelegate.setApplicationLocales(if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag))
                }
            }
        }
        Rule(Modifier.padding(vertical = 14.dp))

        Text(stringResource(R.string.s_restart), style = T.Heading, modifier = Modifier.clickable { restart = true })
        Text(stringResource(R.string.s_restart_hint), style = T.Small)
        Rule(Modifier.padding(vertical = 14.dp))

        if (BuildConfig.DEBUG || BuildConfig.DEMO) {
            Text(stringResource(R.string.s_steps), style = T.Heading)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                for (n in listOf(1_000, 10_000, 100_000)) Btn("+" + home.words.group(n.toLong()), primary = false) { c.write { trip.addSteps(n) } }
            }
            Rule(Modifier.padding(vertical = 14.dp))
        }
        Text(stringResource(R.string.community), style = T.Heading)
        CommunityLinks()
        Rule(Modifier.padding(vertical = 14.dp))
        Text(stringResource(R.string.s_credits), style = T.Small)
        Text("v" + BuildConfig.VERSION_NAME, style = T.Small)
        Spacer(Modifier.height(24.dp))
    }
    if (restart) Restart(home, onDone = { restart = false; onBack() }, onCancel = { restart = false })
}

/** Where the steps come from: the phone's own counter, or Health Connect. */
@Composable
private fun StepSource(health: Boolean) {
    val ctx = LocalContext.current
    val c = ctx.container
    // null while asking Health Connect whether reading is still allowed.
    var allowed by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(health) { allowed = c.health.allowed() }
    // Why tapping Health Connect didn't switch to it.
    var note by remember { mutableStateOf<Int?>(null) }
    fun useHealth() = c.write {
        settings.update { it.copy(healthFrom = java.time.LocalDate.now().toEpochDay()) }
        sync()
    }
    val ask = rememberLauncherForActivityResult(HealthSteps.requestContract()) { got ->
        allowed = HealthSteps.READ in got
        if (allowed == true) { note = null; useHealth() } else note = R.string.health_denied
    }
    fun pickHealth() {
        when {
            c.health.available -> ask.launch(c.health.permissions())
            c.health.installable -> { note = R.string.health_install; runCatching { ctx.startActivity(c.health.storeIntent()) } }
            else -> note = R.string.health_none
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
        Btn(stringResource(R.string.s_source_phone), Modifier.weight(1f), primary = !health) {
            note = null
            c.write { settings.update { it.copy(healthFrom = -1) }; sync() }
        }
        Btn(stringResource(R.string.s_source_health), Modifier.weight(1f), primary = health) { pickHealth() }
    }
    val status = when {
        !health -> note ?: if (c.sensor.allowed && c.sensor.present) R.string.steps_ok else R.string.steps_off
        allowed == null -> null
        allowed == true -> R.string.health_ok
        c.health.available -> R.string.health_denied
        c.health.installable -> R.string.health_install
        else -> R.string.health_none
    }
    status?.let { Text(stringResource(it), style = T.Body) }
    if (health && allowed == false) {
        if (c.health.available) Btn(stringResource(R.string.health_allow), Modifier.fillMaxWidth().padding(top = 6.dp)) { pickHealth() }
        else if (c.health.installable) Btn(stringResource(R.string.health_get), Modifier.fillMaxWidth().padding(top = 6.dp)) { pickHealth() }
    }
    Text(stringResource(R.string.s_source_hint), style = T.Small)
}

@Composable
private fun Restart(home: Home, onDone: () -> Unit, onCancel: () -> Unit) {
    val c = LocalContext.current.container
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<Town?>(null) }
    val results = remember(query) { home.world.search(query, 6) }
    PaperDialog(onCancel) {
        Text(stringResource(R.string.s_restart), style = T.Heading)
        Text(stringResource(R.string.s_restart_hint), style = T.Small.copy(color = C.red))
        Field(query, { query = it; picked = null }, stringResource(R.string.o_home_hint))
        for (t in results) {
            Text(
                home.words.town(t.id) + ", " + home.words.country(t.iso),
                style = T.Body.copy(color = if (t == picked) C.red else C.ink),
                modifier = Modifier.fillMaxWidth().clickable { picked = t }.padding(vertical = 4.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn(stringResource(R.string.cancel), Modifier.weight(1f), primary = false, onClick = onCancel)
            Btn(stringResource(R.string.s_restart_ok), Modifier.weight(1f), enabled = picked != null) {
                val t = picked ?: return@Btn
                c.scope.launch { c.trip.restart(t.id) }
                onDone()
            }
        }
    }
}
