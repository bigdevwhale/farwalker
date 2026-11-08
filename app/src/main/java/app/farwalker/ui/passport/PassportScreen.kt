package app.farwalker.ui.passport

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import app.farwalker.ui.components.Framed
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farwalker.R
import app.farwalker.domain.Continent
import app.farwalker.domain.Geo
import app.farwalker.domain.Medal
import app.farwalker.domain.MedalKind
import app.farwalker.domain.Medals
import app.farwalker.domain.Sight
import app.farwalker.domain.Mode
import app.farwalker.domain.Stamp
import app.farwalker.ui.Home
import app.farwalker.ui.components.Btn
import app.farwalker.ui.components.PaperDialog
import app.farwalker.ui.components.Rule
import app.farwalker.ui.components.Stat
import app.farwalker.ui.theme.C
import app.farwalker.ui.theme.Cormorant
import app.farwalker.ui.theme.T
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.cos
import kotlin.math.sin

/** The passport's three pages: border stamps, medals and sights. */
private enum class Page { STAMPS, MEDALS, SIGHTS }

@Composable
fun PassportScreen(home: Home, onAim: (Int) -> Unit) {
    val j = home.j
    val w = home.words
    val world = home.world
    var page by rememberSaveable { mutableStateOf(Page.STAMPS) }
    var open by remember { mutableStateOf<Stamp?>(null) }
    var openMedal by remember { mutableStateOf<Medal?>(null) }
    var openSight by remember { mutableStateOf<Sight?>(null) }
    val medals = remember(world) { Medals.all(world) }
    val (lat, lon) = home.travel.position(j)
    val seen = remember(j.sights.size) { world.sights.filter { it.key in j.sights }.sortedByDescending { j.sights[it.key] } }
    val ahead = remember(j.sights.size, j.town) { world.sights.filter { it.key !in j.sights }.sortedBy { Geo.km(lat, lon, it.lat, it.lon) } }
    val stamps = j.stamps.values.toList()
    LazyVerticalGrid(
        GridCells.Fixed(3),
        Modifier.fillMaxSize().background(C.paper).statusBarsPadding().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(span = { GridItemSpan(3) }) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.passport), style = T.Display)
                Summary(home, medals.size)
                Parts(home)
                Tabs(
                    listOf(
                        Page.STAMPS to (stringResource(R.string.p_tab_stamps) to stamps.size),
                        Page.MEDALS to (stringResource(R.string.p_tab_medals) to j.medals.size),
                        Page.SIGHTS to (stringResource(R.string.p_tab_sights) to j.sights.size),
                    ),
                    page,
                ) { page = it }
            }
        }
        when (page) {
            Page.STAMPS -> items(stamps.asReversed(), key = { it.iso }) { s ->
                Box(Modifier.fillMaxWidth().aspectRatio(1f).clickable { open = s }) {
                    StampArt(w.country(s.iso), w.shortDate(s.day), s.iso, Modifier.fillMaxSize())
                }
            }
            Page.MEDALS -> {
                val kinds = medals.groupBy { it.kind }
                for ((kind, list) in kinds) item(span = { GridItemSpan(3) }, key = "k" + kind.name) {
                    MedalRow(home, kind, list) { openMedal = it }
                }
            }
            Page.SIGHTS -> {
                item(span = { GridItemSpan(3) }) { SightsHead(home) }
                if (seen.isNotEmpty()) {
                    item(span = { GridItemSpan(3) }) { Eyebrow(stringResource(R.string.p_seen_list)) }
                    items(seen, key = { "s" + it.key }, span = { GridItemSpan(3) }) { sg -> SightRow(home, sg, lat, lon) { openSight = sg } }
                }
                item(span = { GridItemSpan(3) }) { Eyebrow(stringResource(R.string.p_ahead)) }
                items(ahead, key = { "s" + it.key }, span = { GridItemSpan(3) }) { sg -> SightRow(home, sg, lat, lon) { openSight = sg } }
            }
        }
        item(span = { GridItemSpan(3) }) { Box(Modifier.padding(16.dp)) }
    }
    openMedal?.let { m ->
        PaperDialog(onDismiss = { openMedal = null }) {
            val won = j.medals[m.key]
            Box(Modifier.fillMaxWidth().padding(horizontal = 70.dp).aspectRatio(0.8f)) { MedalArt(m, won != null, Modifier.fillMaxSize()) }
            Text(w.medal(m), style = T.Title)
            if (won != null) Text(stringResource(R.string.p_won, w.date(won)), style = T.Body)
            else {
                Progress((m.value(j) / m.target).toFloat(), false)
                Text(amount(home, m.kind, m.value(j), m.target), style = T.Body)
            }
            Btn(stringResource(R.string.close), Modifier.fillMaxWidth(), primary = false) { openMedal = null }
        }
    }
    openSight?.let { sg ->
        PaperDialog(onDismiss = { openSight = null }) {
            val at = j.sights[sg.key]
            SightPolaroid(sg, sg.name(home.ru), if (at != null) w.date(at) else stringResource(R.string.p_photo_ahead), at != null, Modifier.padding(vertical = 6.dp))
            Text(sg.name(home.ru), style = T.Title)
            Text(w.country(sg.iso) + " · " + stringResource(R.string.p_near, w.town(sg.town)), style = T.Italic)
            Text(sg.about(home.ru), style = T.Body)
            Text(
                if (at != null) stringResource(R.string.p_seen, w.date(at)) else stringResource(R.string.p_unseen, w.town(sg.town)),
                style = T.Sub.copy(color = if (at != null) C.red else C.muted),
            )
            if (sg.town != j.town && j.ride == null) {
                Btn(stringResource(R.string.p_go), Modifier.fillMaxWidth(), primary = at == null) { openSight = null; onAim(sg.town) }
            }
            Btn(stringResource(R.string.close), Modifier.fillMaxWidth(), primary = false) { openSight = null }
        }
    }
    open?.let { s ->
        PaperDialog(onDismiss = { open = null }) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 40.dp).aspectRatio(1f)) {
                StampArt(w.country(s.iso), w.shortDate(s.day), s.iso, Modifier.fillMaxSize())
            }
            Text(w.country(s.iso), style = T.Title)
            world.country(s.iso)?.let { Text(it.continent.name(home.ru), style = T.Italic) }
            Text(stringResource(R.string.p_first, w.date(s.day)), style = T.Body)
            if (s.town >= 0) Text(stringResource(R.string.p_via, w.town(s.town)), style = T.Sub)
            if (s.entries > 1) Text(stringResource(R.string.p_times, s.entries), style = T.Sub)
            Btn(stringResource(R.string.close), Modifier.fillMaxWidth(), primary = false) { open = null }
        }
    }
}

/**
 * The passport's own page: the states stamped, large, and beside them how the way was made; under
 * them the ends of the earth and the days on the road.
 */
@Composable
private fun Summary(home: Home, medals: Int) {
    val j = home.j
    val w = home.words
    val world = home.world
    val states = j.stamps.values.count { world.country(it.iso)?.state == true }
    val lands = j.stamps.size - states
    Framed(Modifier.fillMaxWidth(), pad = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.p_states).uppercase(), style = T.Eyebrow)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(states.toString(), style = T.Display.copy(color = C.red, fontFeatureSettings = "lnum"))
                    Text(" / " + world.states, style = T.Heading.copy(color = C.muted, fontFeatureSettings = "lnum"), modifier = Modifier.padding(bottom = 4.dp))
                }
                if (lands > 0) Text(w.r.resources.getQuantityString(R.plurals.p_lands_more, lands, lands), style = T.Small)
            }
            Box(Modifier.padding(horizontal = 12.dp).width(0.8.dp).height(72.dp).background(C.ink.copy(alpha = 0.4f)))
            Column(Modifier.weight(1.1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for ((label, km) in listOf(R.string.p_foot to j.walked, R.string.p_train to j.rode, R.string.p_sea to j.sailed)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(stringResource(label), style = T.Sub, modifier = Modifier.weight(1f))
                        Text(stringResource(R.string.km_n, w.km(km)), style = T.Body)
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 10.dp).height(0.6.dp).background(C.ink.copy(alpha = 0.4f)))
        Row(Modifier.fillMaxWidth()) {
            Stat(stringResource(R.string.p_days), w.days(home.day), Modifier.weight(1f))
            Stat(stringResource(R.string.p_capes_short), stringResource(R.string.p_of, j.capes.size, world.towns.count { it.cape }), Modifier.weight(1f))
            Stat(stringResource(R.string.p_medals), stringResource(R.string.p_of, j.medals.size, medals), Modifier.weight(1f))
        }
    }
}

/** The page markers along the passport's edge: each with how much is on it; the open one underlined in red. */
@Composable
private fun <P> Tabs(pages: List<Pair<P, Pair<String, Int>>>, current: P, onPick: (P) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            for ((p, label) in pages) {
                val on = p == current
                Column(
                    Modifier.weight(1f).clickable { onPick(p) }.padding(top = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(label.first.uppercase(), style = T.Eyebrow.copy(color = if (on) C.ink else C.faint), maxLines = 1)
                    Text(label.second.toString(), style = T.Heading.copy(color = if (on) C.red else C.faint, fontFeatureSettings = "lnum"))
                    Box(Modifier.padding(top = 4.dp).fillMaxWidth(0.7f).height(2.5.dp).background(if (on) C.red else Color.Transparent))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(0.8.dp).background(C.ink.copy(alpha = 0.5f)))
        Box(Modifier.height(6.dp))
    }
}

/** A small caption over a list. */
@Composable
private fun Eyebrow(text: String) {
    Text(text.uppercase(), style = T.Eyebrow, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
}

/** How far along, in a thin inked bar: blue on the way, red when done. */
@Composable
fun Bar(fraction: Float, done: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(6.dp)) {
        drawRect(C.paper2)
        drawRect(if (done) C.red else C.sea, size = Size(size.width * fraction.coerceIn(0f, 1f), size.height))
        drawRect(C.ink, style = Stroke(0.8f * density))
    }
}

@Composable
private fun Progress(fraction: Float, done: Boolean) = Bar(fraction, done)

/** The words for a medal kind's count: "1 259 km", "11 280 steps", "4 of 81". */
private fun current(home: Home, kind: MedalKind, v: Double): String {
    val w = home.words
    val r = w.r
    return when (kind) {
        MedalKind.KM, MedalKind.ROUND, MedalKind.SEA -> r.getString(R.string.km_n, w.km(v))
        MedalKind.DAY -> r.getString(R.string.steps_n, w.group(v.toLong()))
        MedalKind.SIGHTS -> r.getString(R.string.p_of, v.toInt(), home.world.sights.size)
        MedalKind.CAPES -> r.getString(R.string.p_of, v.toInt(), home.world.towns.count { it.cape })
        MedalKind.PARTS -> r.getString(R.string.p_of, v.toInt(), Continent.entries.size)
        MedalKind.COUNTRIES, MedalKind.TASKS -> w.group(v.toLong())
    }
}

/** How far to a medal: "1 259 / 2 500 km". */
private fun amount(home: Home, kind: MedalKind, v: Double, target: Int): String {
    val w = home.words
    val pair = w.group(v.toLong().coerceAtMost(target.toLong())) + " / " + w.group(target.toLong())
    return when (kind) {
        MedalKind.KM, MedalKind.ROUND, MedalKind.SEA -> w.r.getString(R.string.km_n, pair)
        MedalKind.DAY -> w.r.getString(R.string.steps_n, pair)
        else -> pair
    }
}

private fun kindTitle(kind: MedalKind): Int = when (kind) {
    MedalKind.KM -> R.string.m_km
    MedalKind.ROUND -> R.string.m_round
    MedalKind.COUNTRIES -> R.string.m_countries
    MedalKind.SIGHTS -> R.string.m_sights
    MedalKind.CAPES -> R.string.m_capes
    MedalKind.PARTS -> R.string.m_parts
    MedalKind.DAY -> R.string.m_day
    MedalKind.SEA -> R.string.m_sea
    MedalKind.TASKS -> R.string.m_tasks
}

/**
 * One kind of medal on a line: what is counted and how much there is, the medals of the kind in a row
 * (won ones gilt), and how far it is to the next.
 */
@Composable
private fun MedalRow(home: Home, kind: MedalKind, list: List<Medal>, onOpen: (Medal) -> Unit) {
    val j = home.j
    val v = list.first().value(j)
    val next = list.firstOrNull { it.key !in j.medals }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(stringResource(kindTitle(kind)), style = T.Heading, modifier = Modifier.weight(1f))
            Text(current(home, kind, v), style = T.Sub)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (m in list) {
                MedalArt(m, m.key in j.medals, Modifier.width(40.dp).aspectRatio(0.8f).clickable { onOpen(m) }, next = m == next)
            }
        }
        if (next != null) {
            Bar((v / next.target).toFloat(), false)
            Text(stringResource(R.string.p_next, home.words.medal(next)) + " · " + amount(home, kind, v, next.target), style = T.Small)
        } else {
            Text(stringResource(R.string.p_all_won), style = T.Small.copy(color = C.red))
        }
        Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(0.6.dp).background(C.ink.copy(alpha = 0.25f)))
    }
}

/** How many sights are seen, and how a sight is collected. */
@Composable
private fun SightsHead(home: Home) {
    val n = home.j.sights.size
    val all = home.world.sights.size
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.p_sights_seen).uppercase(), style = T.Eyebrow)
        Text(stringResource(R.string.p_of, n, all), style = T.Title)
        Bar(n.toFloat() / all, n == all)
        Text(stringResource(R.string.p_sights_hint), style = T.Small)
    }
}

@Composable
private fun SightRow(home: Home, sg: Sight, lat: Double, lon: Double, onOpen: () -> Unit) {
    val w = home.words
    val at = home.j.sights[sg.key]
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        SightMark(at != null, Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(sg.name(home.ru), style = T.Heading.copy(color = if (at != null) C.ink else C.muted))
            Text(w.country(sg.iso) + " · " + stringResource(R.string.p_near, w.town(sg.town)), style = T.Small)
        }
        Text(
            if (at != null) w.shortDate(at) else "≈ " + stringResource(R.string.km_n, w.km(Geo.km(lat, lon, sg.lat, sg.lon))),
            style = T.Small.copy(color = if (at != null) C.red else C.muted),
        )
    }
}

/** The seven parts of the world, inked in as they're reached. */
@Composable
private fun Parts(home: Home) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (c in Continent.entries) {
            val got = c in home.j.continents
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.fillMaxWidth().aspectRatio(1f).padding(6.dp)) {
                    drawCircle(if (got) C.red else C.paper2, size.width / 2)
                    drawCircle(C.ink, size.width / 2, style = Stroke(1f * density))
                    if (got) drawCircle(C.paper, size.width / 6)
                }
                Text(c.name(home.ru), style = T.Small.copy(fontSize = 9.sp, lineHeight = 10.sp, color = if (got) C.ink else C.faint), textAlign = TextAlign.Center, maxLines = 2)
            }
        }
    }
}

private val inks = listOf(Color(0xFF9E2B20), Color(0xFF2F4A6E), Color(0xFF5B3F6E), Color(0xFF3F5E3A), Color(0xFF2B2118), Color(0xFF8A4B1C))

/**
 * A border stamp, cut from the country's code: its shape, ink and tilt are the same for the same
 * country every time, as if each border post had its own rubber stamp.
 */
@Composable
fun StampArt(name: String, date: String, iso: String, modifier: Modifier = Modifier) {
    val text = rememberTextMeasurer()
    val h = iso.hashCode().let { if (it < 0) -it else it }
    val ink = inks[h % inks.size]
    val shape = (h / 7) % 5
    val tilt = ((h / 37) % 17 - 8).toFloat()
    Canvas(modifier.padding(4.dp)) {
        val s = density
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2 - 4 * s
        val col = ink.copy(alpha = 0.85f)
        rotate(tilt, c) {
            when (shape) {
                0 -> {
                    drawCircle(col, r, c, style = Stroke(2.2f * s))
                    drawCircle(col, r - 4 * s, c, style = Stroke(0.9f * s))
                }
                1 -> {
                    val inset = r * 0.12f
                    drawRoundRect(col, Offset(c.x - r, c.y - r + inset * 2), Size(2 * r, 2 * r - inset * 4), CornerRadius(6 * s), style = Stroke(2.2f * s))
                    drawRoundRect(col, Offset(c.x - r + 4 * s, c.y - r + inset * 2 + 4 * s), Size(2 * r - 8 * s, 2 * r - inset * 4 - 8 * s), CornerRadius(3 * s), style = Stroke(0.9f * s))
                }
                2 -> {
                    drawOval(col, Offset(c.x - r, c.y - r * 0.72f), Size(2 * r, 1.44f * r), style = Stroke(2.2f * s))
                    drawOval(col, Offset(c.x - r + 4 * s, c.y - r * 0.72f + 4 * s), Size(2 * r - 8 * s, 1.44f * r - 8 * s), style = Stroke(0.9f * s))
                }
                3 -> polygon(col, c, r, 8, s)
                else -> polygon(col, c, r, 6, s)
            }
            val nameStyle = TextStyle(fontFamily = Cormorant, fontWeight = FontWeight.Bold, color = col, textAlign = TextAlign.Center,
                fontSize = (if (name.length > 12) 10 else if (name.length > 8) 12 else 14).sp, lineHeight = 13.sp)
            val m = text.measure(name.uppercase(), nameStyle, constraints = androidx.compose.ui.unit.Constraints(maxWidth = (r * 1.6f).toInt()))
            drawText(m, topLeft = Offset(c.x - m.size.width / 2f, c.y - m.size.height / 2f - 5 * s))
            val d = text.measure(date, TextStyle(fontFamily = Cormorant, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = col))
            drawText(d, topLeft = Offset(c.x - d.size.width / 2f, c.y + m.size.height / 2f - 2 * s))
            // Little stars either side of the date, and gaps where the ink didn't take.
            drawCircle(col, 1.4f * s, Offset(c.x - d.size.width / 2f - 6 * s, c.y + m.size.height / 2f + d.size.height / 2f - 2 * s))
            drawCircle(col, 1.4f * s, Offset(c.x + d.size.width / 2f + 6 * s, c.y + m.size.height / 2f + d.size.height / 2f - 2 * s))
        }
        worn(h, s)
    }
}

private fun DrawScope.polygon(col: Color, c: Offset, r: Float, n: Int, s: Float) {
    for ((rr, w) in listOf(r to 2.2f * s, r - 4 * s to 0.9f * s)) {
        val p = Path()
        for (i in 0 until n) {
            val a = Math.PI * 2 * i / n + Math.PI / n
            val x = c.x + (cos(a) * rr).toFloat()
            val y = c.y + (sin(a) * rr).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        drawPath(p, col, style = Stroke(w))
    }
}

/** Specks of bare paper over the ink, the same for the same stamp. */
private fun DrawScope.worn(seed: Int, s: Float) {
    var x = seed.toLong()
    repeat(26) {
        x = (x * 1103515245 + 12345) and 0x7fffffff
        val px = (x % 1000) / 1000f * size.width
        x = (x * 1103515245 + 12345) and 0x7fffffff
        val py = (x % 1000) / 1000f * size.height
        drawCircle(C.paper, (0.6f + (x % 3) * 0.5f) * s, Offset(px, py))
    }
}

/** A medal on a ribbon: gilt and inked once won, a faint outline until then. */
@Composable
fun MedalArt(m: Medal, won: Boolean, modifier: Modifier = Modifier, next: Boolean = false) {
    val text = rememberTextMeasurer()
    val label = when {
        m.kind == MedalKind.ROUND -> "40k"
        m.target >= 1000 -> (if (m.target % 1000 == 0) (m.target / 1000).toString() else String.format(java.util.Locale.US, "%.1f", m.target / 1000.0)) + "k"
        else -> m.target.toString()
    }
    val ink = if (won) C.ink else C.faint
    Canvas(modifier.padding(2.dp)) {
        val s = density * (size.width / (90f * density)).coerceIn(0.45f, 1f)
        val r = size.width * 0.38f
        val c = Offset(size.width / 2, size.height - r - 2 * s)
        // The ribbon: two tails crossing behind the medal.
        val tail = Path().apply {
            moveTo(c.x - r * 0.75f, 0f); lineTo(c.x - r * 0.05f, 0f); lineTo(c.x + r * 0.35f, c.y - r * 0.6f); lineTo(c.x - r * 0.25f, c.y - r * 0.4f); close()
        }
        val tail2 = Path().apply {
            moveTo(c.x + r * 0.75f, 0f); lineTo(c.x + r * 0.05f, 0f); lineTo(c.x - r * 0.35f, c.y - r * 0.6f); lineTo(c.x + r * 0.25f, c.y - r * 0.4f); close()
        }
        drawPath(tail, if (won) C.red else C.paper2)
        drawPath(tail2, if (won) C.sea else C.paper2)
        drawPath(tail, ink, style = Stroke(0.9f * s))
        drawPath(tail2, ink, style = Stroke(0.9f * s))
        drawCircle(if (won) C.wash else C.paper2, r, c)
        drawCircle(if (next) C.red else ink, r, c, style = Stroke((if (next) 2.2f else 1.6f) * s))
        drawCircle(ink, r - 3.5f * s, c, style = Stroke(0.7f * s))
        // Little rays round the rim, as stamped into the metal.
        for (i in 0 until 24) {
            val a = Math.PI * 2 * i / 24
            val p1 = c + Offset((cos(a) * (r - 3.5f * s)).toFloat(), (sin(a) * (r - 3.5f * s)).toFloat())
            val p2 = c + Offset((cos(a) * (r - 6f * s)).toFloat(), (sin(a) * (r - 6f * s)).toFloat())
            drawLine(ink.copy(alpha = 0.6f), p1, p2, 0.7f * s)
        }
        val style = TextStyle(fontFamily = Cormorant, fontWeight = FontWeight.Bold, color = if (won) C.ink else C.faint,
            fontSize = (r * (if (label.length > 3) 0.42f else 0.55f)).toSp())
        val t = text.measure(label, style)
        drawText(t, topLeft = Offset(c.x - t.size.width / 2f, c.y - t.size.height / 2f))
    }
}

/** A sight's mark: a small pointed star, red once seen. */
@Composable
fun SightMark(seen: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2 - density
        val p = Path()
        for (i in 0 until 10) {
            val a = Math.PI * 2 * i / 10 - Math.PI / 2
            val rr = if (i % 2 == 0) r else r * 0.45f
            val x = c.x + (cos(a) * rr).toFloat()
            val y = c.y + (sin(a) * rr).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        drawPath(p, if (seen) C.red else C.paper2)
        drawPath(p, if (seen) C.ink else C.faint, style = Stroke(1f * density))
    }
}

/** How the traveller got somewhere, as a word for the journal. */
fun modeWord(m: Mode): Int? = when (m) {
    Mode.TRAIN -> R.string.j_on_train
    Mode.SHIP -> R.string.j_at_sea
    Mode.WALK -> null
}
