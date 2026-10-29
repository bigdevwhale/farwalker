package app.farwalker.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.farwalker.R
import app.farwalker.container
import app.farwalker.domain.Journey
import app.farwalker.domain.Town
import app.farwalker.ru
import app.farwalker.ui.Words
import app.farwalker.ui.atlas.AtlasMap
import app.farwalker.ui.atlas.Camera
import app.farwalker.ui.components.Btn
import app.farwalker.ui.components.Field
import app.farwalker.ui.components.Rule
import app.farwalker.ui.theme.C
import app.farwalker.ui.theme.T
import kotlinx.coroutines.launch

/** Three pages: what this is, where the journey starts, and the step counter. */
@Composable
fun Onboarding() {
    val ctx = LocalContext.current
    val c = ctx.container
    val words = remember { Words(ctx, ctx.ru, c.world) }
    var page by remember { mutableIntStateOf(0) }
    var home by remember { mutableStateOf<Town?>(null) }
    val camera = remember { Camera(lon = 15.0, lat = 25.0, k = 1.5) }
    // A traveller standing at the chosen home (or at Greenwich, before there is one), for the map.
    val preview = remember(home) {
        Journey().also { j ->
            val t = home ?: c.world.towns.first { it.en == "London" }
            c.travel.begin(j, t.id, 0)
        }
    }
    LaunchedEffect(home) {
        home?.let { camera.k = 9.0; camera.look(it.lat, it.lon) }
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    Column(Modifier.fillMaxSize().background(C.paper).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Box(Modifier.fillMaxWidth().weight(if (page == 1) 0.35f else 0.5f)) {
            AtlasMap(c.world, preview, c.travel, camera, words.ru, Modifier.fillMaxSize(), focusY = 0.5f, interactive = false)
        }
        Rule()
        Column(
            Modifier.fillMaxWidth().weight(if (page == 1) 0.65f else 0.5f).padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (page) {
                0 -> {
                    Text(stringResource(R.string.o_title), style = T.Display)
                    Text(stringResource(R.string.o_text), style = T.Body)
                    Btn(stringResource(R.string.o_next), Modifier.fillMaxWidth()) { page = 1 }
                }
                1 -> {
                    var query by remember { mutableStateOf("") }
                    val results = remember(query) { c.world.search(query, 8) }
                    Text(stringResource(R.string.o_home), style = T.Title)
                    Field(query, { query = it }, stringResource(R.string.o_home_hint))
                    for (t in results) {
                        Text(
                            words.town(t.id) + ", " + words.country(t.iso),
                            style = T.Body.copy(color = if (t.id == home?.id) C.red else C.ink),
                            modifier = Modifier.fillMaxWidth().clickable { home = t }.padding(vertical = 6.dp),
                        )
                    }
                    Btn(stringResource(R.string.o_next), Modifier.fillMaxWidth(), enabled = home != null) { page = 2 }
                }
                else -> {
                    Text(stringResource(R.string.o_steps), style = T.Title)
                    Text(stringResource(R.string.o_steps_text), style = T.Body)
                    if (!c.sensor.present) Text(stringResource(R.string.o_no_sensor), style = T.Italic)
                    val perms = buildList {
                        if (Build.VERSION.SDK_INT >= 29) add(Manifest.permission.ACTIVITY_RECOGNITION)
                        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    if (perms.isNotEmpty() && !c.sensor.allowed) {
                        Btn(stringResource(R.string.o_allow), Modifier.fillMaxWidth(), primary = false) { ask.launch(perms.toTypedArray()) }
                    }
                    Btn(stringResource(R.string.o_start), Modifier.fillMaxWidth()) {
                        val t = home ?: return@Btn
                        c.scope.launch {
                            c.trip.begin(t.id)
                            c.settings.update { it.copy(onboarded = true) }
                        }
                    }
                }
            }
        }
    }
}
