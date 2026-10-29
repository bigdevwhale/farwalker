package app.farwalker.ui.atlas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.farwalker.R
import app.farwalker.container
import app.farwalker.domain.Mode
import app.farwalker.domain.TrainOffer
import app.farwalker.domain.Journey
import app.farwalker.domain.Weather
import app.farwalker.ui.Home
import app.farwalker.ui.components.Btn
import app.farwalker.ui.components.PaperDialog
import app.farwalker.ui.components.Rule
import app.farwalker.ui.components.Stat
import app.farwalker.ui.theme.C
import app.farwalker.ui.theme.T
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.floor

@Composable
fun AtlasScreen(home: Home, camera: Camera, onGoal: () -> Unit, onSettings: () -> Unit) {
    val j = home.j
    val travel = home.travel
    var panel by remember { mutableFloatStateOf(0f) }
    val (lat, lon) = travel.position(j)
    LaunchedEffect(lat, lon, camera.follow) { if (camera.follow) camera.look(lat, lon) }
    Box(Modifier.fillMaxSize()) {
        AtlasMap(home.world, j, travel, camera, home.ru, Modifier.fillMaxSize(), focusY = 0.36f, bottomInset = panel)
        // The cartouche: the day of the journey and the land underfoot.
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.background(C.paper.copy(alpha = 0.92f)).border(1.dp, C.ink).padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(stringResource(R.string.day_n, home.day).uppercase(), style = T.Eyebrow)
                Text(home.words.country(j.iso), style = T.Heading, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RoundIcon(onClick = onSettings) { Gear() }
                if (!camera.follow) RoundIcon(onClick = { camera.follow = true }) { Target() }
                Rose()
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { panel = it.height.toFloat() }) {
            Panel(home, onGoal)
        }
    }
}

@Composable
private fun RoundIcon(onClick: () -> Unit, draw: @Composable () -> Unit) {
    Box(
        Modifier.size(40.dp).background(C.paper.copy(alpha = 0.92f), androidx.compose.foundation.shape.CircleShape)
            .border(1.dp, C.ink, androidx.compose.foundation.shape.CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { draw() }
}

@Composable
private fun Gear() {
    Canvas(Modifier.size(20.dp)) {
        val c = Offset(size.width / 2, size.height / 2)
        val s = density
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            drawLine(C.ink, c + Offset((kotlin.math.cos(a) * 5 * s).toFloat(), (kotlin.math.sin(a) * 5 * s).toFloat()),
                c + Offset((kotlin.math.cos(a) * 9 * s).toFloat(), (kotlin.math.sin(a) * 9 * s).toFloat()), 2.6f * s)
        }
        drawCircle(C.ink, 6.5f * s, c)
        drawCircle(C.paper, 3f * s, c)
    }
}

@Composable
private fun Target() {
    Canvas(Modifier.size(20.dp)) {
        val c = Offset(size.width / 2, size.height / 2)
        val s = density
        drawCircle(C.ink, 6f * s, c, style = Stroke(1.4f * s))
        drawCircle(C.red, 2.5f * s, c)
        for (d in listOf(Offset(0f, -1f), Offset(0f, 1f), Offset(-1f, 0f), Offset(1f, 0f))) {
            drawLine(C.ink, c + d * (6f * s), c + d * (10f * s), 1.4f * s)
        }
    }
}

/** A small compass rose, as on every old chart. */
@Composable
private fun Rose() {
    Canvas(Modifier.size(54.dp)) {
        val c = Offset(size.width / 2, size.height / 2)
        val s = density
        val r = size.width / 2 - 2 * s
        drawCircle(C.paper.copy(alpha = 0.8f), r, c)
        drawCircle(C.ink, r, c, style = Stroke(0.8f * s))
        drawCircle(C.ink, r - 3 * s, c, style = Stroke(0.5f * s))
        for (i in 0 until 4) {
            val main = i * 90.0
            for ((ang, len, w) in listOf(Triple(main, r - 1 * s, 4.2f * s), Triple(main + 45, r * 0.55f, 2.6f * s))) {
                val a = Math.toRadians(ang - 90)
                val tip = c + Offset((kotlin.math.cos(a) * len).toFloat(), (kotlin.math.sin(a) * len).toFloat())
                val side = Math.toRadians(ang)
                val o = Offset((kotlin.math.cos(side) * w).toFloat(), (kotlin.math.sin(side) * w).toFloat())
                val left = Path().apply { moveTo(c.x, c.y); lineTo(tip.x, tip.y); lineTo(c.x - o.x, c.y - o.y); close() }
                val right = Path().apply { moveTo(c.x, c.y); lineTo(tip.x, tip.y); lineTo(c.x + o.x, c.y + o.y); close() }
                drawPath(left, if (ang == 0.0) C.red else C.ink)
                drawPath(right, C.paper)
                drawPath(right, C.ink, style = Stroke(0.6f * s))
            }
        }
    }
}

/** The waybill under the map: where the traveller is, what today brought, and what can be done. */
@Composable
private fun Panel(home: Home, onGoal: () -> Unit) {
    val j = home.j
    val w = home.words
    val travel = home.travel
    val c = LocalContext.current.container
    val ride = j.ride
    val next = travel.next(j)
    val ship = travel.ship(j)
    val trains = travel.trains(j)
    var tickets by remember { mutableStateOf(false) }
    val here = travel.sleepTown(j)
    val zone = remember(here) { runCatching { ZoneId.of(home.world.town(here).tz) }.getOrDefault(ZoneId.systemDefault()) }
    val clock = home.now.withZoneSameInstant(zone).format(DateTimeFormatter.ofPattern("HH:mm"))
    val (lat, lon) = travel.position(j)
    val weather = home.settings.weather?.takeIf { Weather.fresh(it, lat, lon) }

    Column(Modifier.fillMaxWidth().background(C.paper)) {
        Rule()
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val continent = home.world.country(j.iso)?.continent?.name(home.ru)
            Text(listOfNotNull(w.country(j.iso), continent).joinToString(" · ").uppercase(), style = T.Eyebrow)
            val title = when {
                ride?.mode == Mode.TRAIN -> stringResource(R.string.on_train, w.town(lastStop(home, j)))
                ride?.mode == Mode.SHIP -> stringResource(R.string.at_sea, w.town(lastStop(home, j)))
                else -> w.town(here)
            }
            Text(title, style = T.Title, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val line = when {
                ride != null -> stringResource(R.string.arrives, Instant.ofEpochMilli(ride.end).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(if (home.ru) "d MMM, HH:mm" else "MMM d, HH:mm")))
                ship != null -> stringResource(R.string.sea_waits)
                next != null && j.legs.isNotEmpty() -> stringResource(R.string.next_in, w.town(next), w.km(home.world.edge(j.legs[0].edge).km - j.at))
                else -> stringResource(R.string.resting)
            }
            Text(line, style = T.Italic)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat(stringResource(R.string.today), stringResource(R.string.km_n, w.km(home.kmToday)), Modifier.weight(1f), sub = stringResource(R.string.steps_n, w.group(home.stepsToday.toLong())))
                Stat(stringResource(R.string.purse), w.coins(floor(j.coins).toInt()), Modifier.weight(1f))
                Stat(
                    stringResource(R.string.there, clock), weather?.let { w.temp(it.temp) } ?: "—", Modifier.weight(1f),
                    sub = weather?.let { w.sky(it.sky) },
                )
            }
            Text(
                if (j.dest >= 0) stringResource(R.string.goal, w.town(j.dest), w.km(travel.left(j))) else stringResource(R.string.no_goal),
                style = T.Sub, modifier = Modifier.clickable(enabled = ride == null, onClick = onGoal),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    ride != null -> Unit
                    ship != null -> {
                        val ok = j.coins >= ship.fare
                        Btn(
                            stringResource(R.string.sail_to, w.town(ship.town)), Modifier.weight(1f), enabled = ok,
                            sub = if (ok) w.coins(ship.fare) else stringResource(R.string.short_of, w.coins(ship.fare - floor(j.coins).toInt())),
                        ) { c.write { trip.board(ship) } }
                    }
                    trains.isNotEmpty() -> Btn(stringResource(R.string.tickets), Modifier.weight(1f), primary = false, sub = stringResource(R.string.ticket_train, w.group(app.farwalker.domain.Journey.TRAIN_KM.toLong()))) { tickets = true }
                    else -> Unit
                }
                if (ride == null) {
                    Btn(stringResource(if (j.dest >= 0) R.string.change_goal else R.string.where_to), if (ship == null && trains.isEmpty()) Modifier.weight(1f) else Modifier.width(150.dp), primary = j.dest < 0, onClick = onGoal)
                }
            }
        }
    }
    if (tickets) Tickets(home, trains, onClose = { tickets = false })
}

/** Where the current ride ends. */
private fun lastStop(home: Home, j: Journey): Int {
    val r = j.ride ?: return j.town
    var km = r.km - r.done + j.at
    for (leg in j.legs) {
        km -= home.world.edge(leg.edge).km
        if (km <= 1e-6) return home.travel.router.to(leg)
    }
    return j.legs.lastOrNull()?.let { home.travel.router.to(it) } ?: j.town
}

/** The timetable: a handful of stations ahead, the nearest, the farthest, and some between. */
@Composable
private fun Tickets(home: Home, offers: List<TrainOffer>, onClose: () -> Unit) {
    val c = LocalContext.current.container
    val w = home.words
    val shown = remember(offers) {
        if (offers.size <= 7) offers else (0 until 7).map { offers[it * (offers.size - 1) / 6] }.distinct()
    }
    PaperDialog(onClose) {
        Text(stringResource(R.string.tickets), style = T.Title)
        Text(stringResource(R.string.ticket_train, w.group(app.farwalker.domain.Journey.TRAIN_KM.toLong())), style = T.Italic)
        Rule()
        for (o in shown) {
            val ok = home.j.coins >= o.fare
            Row(
                Modifier.fillMaxWidth().clickable(enabled = ok) { c.write { trip.board(o) }; onClose() }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(w.town(o.town), style = T.Heading.copy(color = if (ok) C.ink else C.faint))
                    Text(stringResource(R.string.km_n, w.km(o.km)), style = T.Small)
                }
                Text(w.coins(o.fare), style = T.Body.copy(color = if (ok) C.red else C.faint))
            }
        }
        Spacer(Modifier.height(4.dp))
        Btn(stringResource(R.string.close), Modifier.fillMaxWidth(), primary = false, onClick = onClose)
    }
}
