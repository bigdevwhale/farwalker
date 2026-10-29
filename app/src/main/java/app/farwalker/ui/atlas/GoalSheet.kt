package app.farwalker.ui.atlas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.farwalker.R
import app.farwalker.container
import app.farwalker.domain.Geo
import app.farwalker.domain.Leg
import app.farwalker.domain.Town
import app.farwalker.ui.Home
import app.farwalker.ui.components.Btn
import app.farwalker.ui.components.Field
import app.farwalker.ui.components.Rule
import app.farwalker.ui.components.Sheet
import app.farwalker.ui.theme.C
import app.farwalker.ui.theme.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil

/** What the way to a goal would be: its length, the sea in it, and the countries it passes. */
private class Preview(val town: Town, val legs: List<Leg>?, val km: Double, val sea: Double, val countries: List<String>)

@Composable
fun GoalSheet(home: Home, onClose: () -> Unit, onSet: () -> Unit) {
    val c = LocalContext.current.container
    val w = home.words
    val world = home.world
    val j = home.j
    val focus = LocalFocusManager.current
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<Town?>(null) }
    var preview by remember { mutableStateOf<Preview?>(null) }
    val results = remember(query) { world.search(query) }
    val here = world.town(j.town)
    // Empty search: the nearest capitals of states not yet stamped, and the nearest ends of the earth.
    val near = remember(j.town, j.stamps.size) {
        world.countries.values.filter { it.state && it.iso !in j.stamps }
            .mapNotNull { world.capitalOf(it.iso) }
            .sortedBy { Geo.km(here.lat, here.lon, it.lat, it.lon) }.take(6)
    }
    val capes = remember(j.town, j.capes.size) {
        world.towns.filter { it.cape && it.id !in j.capes }.sortedBy { Geo.km(here.lat, here.lon, it.lat, it.lon) }.take(5)
    }
    LaunchedEffect(picked) {
        val t = picked ?: return@LaunchedEffect
        preview = null
        preview = withContext(Dispatchers.Default) {
            val start = if (j.at > 0) home.travel.next(j) ?: j.town else j.town
            val legs = home.travel.router.route(start, t.id)
            var km = 0.0
            var sea = 0.0
            val isos = LinkedHashSet<String>()
            legs?.forEach { leg ->
                val e = world.edge(leg.edge)
                km += e.km
                if (e.sea) sea += e.km
                val cross = if (leg.forward) e.cross.map { it.iso } else e.cross.map { it.iso }.reversed()
                isos += cross
                isos += world.town(home.travel.router.to(leg)).iso
            }
            isos -= j.iso
            Preview(t, legs, km, sea, isos.toList())
        }
    }

    Sheet(stringResource(R.string.goal_title), onBack = onClose) {
        Field(query, { query = it; picked = null }, stringResource(R.string.goal_hint))
        Spacer(Modifier.height(10.dp))
        val p = preview
        val chosen = picked
        if (chosen != null) {
            Text(w.town(chosen.id), style = T.Title)
            Text(w.country(chosen.iso), style = T.Italic)
            Spacer(Modifier.height(6.dp))
            when {
                p == null -> Text("…", style = T.Sub)
                p.legs == null -> Text(stringResource(R.string.goal_none), style = T.Body)
                else -> {
                    Text(
                        stringResource(R.string.goal_way, w.km(p.km)) + if (p.sea > 0) ", " + stringResource(R.string.goal_sea, w.km(p.sea)) else "",
                        style = T.Body,
                    )
                    val foot = p.km - p.sea
                    Text(stringResource(R.string.goal_days, w.days(ceil(foot / home.pace).toInt().coerceAtLeast(1))), style = T.Sub)
                    if (p.countries.isNotEmpty()) {
                        Text(stringResource(R.string.goal_countries, p.countries.joinToString(", ") { w.country(it) }), style = T.Sub)
                    }
                    Spacer(Modifier.height(10.dp))
                    Btn(stringResource(R.string.goal_go), Modifier.fillMaxWidth()) {
                        c.scope.launch { c.trip.aim(chosen.id) }
                        onSet()
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Rule()
        }
        if (query.length >= 2) {
            for (t in results) TownRow(home, t) { picked = t; focus.clearFocus() }
        } else {
            Section(stringResource(R.string.goal_near))
            for (t in near) TownRow(home, t) { picked = t }
            Section(stringResource(R.string.goal_capes))
            for (t in capes) TownRow(home, t) { picked = t }
            if (j.dest >= 0) {
                Spacer(Modifier.height(16.dp))
                Btn(stringResource(R.string.goal_stop), Modifier.fillMaxWidth(), primary = false) {
                    c.scope.launch { c.trip.halt() }
                    onClose()
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Section(title: String) {
    Text(title.uppercase(), style = T.Eyebrow, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
}

@Composable
private fun TownRow(home: Home, t: Town, onPick: () -> Unit) {
    val w = home.words
    val here = home.world.town(home.j.town)
    Row(Modifier.fillMaxWidth().clickable(onClick = onPick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(w.town(t.id), style = T.Heading.copy(color = if (t.iso in home.j.stamps) C.ink else C.red))
            Text(w.country(t.iso), style = T.Small)
        }
        Text("≈ " + stringResource(R.string.km_n, w.km(Geo.km(here.lat, here.lon, t.lat, t.lon))), style = T.Small)
    }
}
