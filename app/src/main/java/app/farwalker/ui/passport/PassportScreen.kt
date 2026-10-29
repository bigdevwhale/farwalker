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

@Composable
fun PassportScreen(home: Home) {
    val j = home.j
    val w = home.words
    val world = home.world
    var open by remember { mutableStateOf<Stamp?>(null) }
    val stamps = j.stamps.values.toList()
    val states = stamps.count { world.country(it.iso)?.state == true }
    val capes = world.towns.count { it.cape }
    LazyVerticalGrid(
        GridCells.Fixed(3),
        Modifier.fillMaxSize().background(C.paper).statusBarsPadding().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(span = { GridItemSpan(3) }) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.passport), style = T.Display)
                Rule()
                Row(Modifier.fillMaxWidth()) {
                    Stat(stringResource(R.string.p_states), stringResource(R.string.p_of, states, world.states), Modifier.weight(1f))
                    Stat(stringResource(R.string.p_lands), (stamps.size - states).toString(), Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth()) {
                    Stat(stringResource(R.string.p_parts), stringResource(R.string.p_of, j.continents.size, Continent.entries.size), Modifier.weight(1f))
                    Stat(stringResource(R.string.p_capes), stringResource(R.string.p_of, j.capes.size, capes), Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth()) {
                    Stat(stringResource(R.string.p_foot), stringResource(R.string.km_n, w.km(j.walked)), Modifier.weight(1f))
                    Stat(stringResource(R.string.p_train), stringResource(R.string.km_n, w.km(j.rode)), Modifier.weight(1f))
                    Stat(stringResource(R.string.p_sea), stringResource(R.string.km_n, w.km(j.sailed)), Modifier.weight(1f))
                }
                Parts(home)
                Text(stringResource(R.string.p_stamps).uppercase(), style = T.Eyebrow, modifier = Modifier.padding(top = 6.dp))
            }
        }
        items(stamps, key = { it.iso }) { s ->
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clickable { open = s }) {
                StampArt(w.country(s.iso), w.shortDate(s.day), s.iso, Modifier.fillMaxSize())
            }
        }
        item(span = { GridItemSpan(3) }) { Box(Modifier.padding(12.dp)) }
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

/** How the traveller got somewhere, as a word for the journal. */
fun modeWord(m: Mode): Int? = when (m) {
    Mode.TRAIN -> R.string.j_on_train
    Mode.SHIP -> R.string.j_at_sea
    Mode.WALK -> null
}
