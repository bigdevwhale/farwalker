package app.farwalker.ui.journal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.farwalker.R
import app.farwalker.domain.Medals
import app.farwalker.domain.News
import app.farwalker.domain.Night
import app.farwalker.domain.Note
import app.farwalker.domain.Quest
import app.farwalker.domain.QuestKind
import app.farwalker.domain.Quests
import app.farwalker.ui.Home
import app.farwalker.ui.components.Framed
import app.farwalker.ui.components.Rule
import app.farwalker.ui.passport.Bar
import app.farwalker.ui.passport.MedalArt
import app.farwalker.ui.passport.modeWord
import app.farwalker.ui.theme.C
import app.farwalker.ui.theme.T
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** One page of the journal: a day, where it ended, and what happened on it. */
private class Page(val day: Long, val night: Night?, val notes: List<Note>)

@Composable
fun JournalScreen(home: Home) {
    val j = home.j
    val pages = remember(j.nights.size, j.notes.size, home.today) {
        val notes = j.notes.groupBy { it.day }
        val nights = j.nights.associateBy { it.day }
        val days = (notes.keys + nights.keys + home.today).distinct().sortedDescending()
        days.map { Page(it, nights[it], notes[it].orEmpty()) }
    }
    LazyColumn(Modifier.fillMaxSize().background(C.paper).statusBarsPadding().padding(horizontal = 16.dp)) {
        item {
            Column(Modifier.padding(top = 12.dp, bottom = 10.dp)) {
                Text(stringResource(R.string.journal), style = T.Display)
            }
        }
        item { Week(home) }
        item { Rule(Modifier.padding(top = 14.dp)) }
        items(pages, key = { it.day }) { PageRow(home, it) }
        if (j.nights.isEmpty()) item { Text(stringResource(R.string.j_empty), style = T.Italic, modifier = Modifier.padding(vertical = 12.dp)) }
        item { Box(Modifier.padding(16.dp)) }
    }
}

/** News that is the day's own: a new country, a part of the world, a cape, a sight, the goal. */
private val STRONG = setOf(News.COUNTRY, News.CONTINENT, News.CAPE, News.ARRIVED, News.SIGHT)

@Composable
private fun PageRow(home: Home, p: Page) {
    val w = home.words
    val j = home.j
    val today = p.day == home.today
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.width(76.dp)) {
            Text(if (today) stringResource(R.string.j_today) else w.shortDate(p.day), style = T.Heading.copy(color = if (today) C.red else C.ink))
            Text(stringResource(R.string.day_n, (p.day - j.start + 1).toInt()), style = T.Small)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            // Where the day ended, and under it the land and what was walked.
            val where = when {
                today -> w.town(home.travel.sleepTown(j))
                p.night == null -> null
                modeWord(p.night.mode) != null -> stringResource(R.string.j_night, stringResource(modeWord(p.night.mode)!!))
                p.night.next >= 0 -> stringResource(R.string.j_night, stringResource(R.string.j_road, w.town(p.night.town), w.town(p.night.next), w.km(p.night.at)))
                else -> stringResource(R.string.j_night, w.town(p.night.town))
            }
            where?.let { Text(it, style = T.Body) }
            val km = if (today) home.kmToday else p.night?.km ?: (j.dayKm[p.day] ?: 0.0)
            val steps = j.days[p.day] ?: 0
            val facts = listOfNotNull(
                (if (today) j.iso else p.night?.iso)?.let { w.country(it) },
                if (km > 0.05) stringResource(R.string.km_n, w.km(km)) else null,
                if (steps > 0) stringResource(R.string.steps_n, w.group(steps.toLong())) else null,
            )
            if (facts.isNotEmpty()) Text(facts.joinToString(" · "), style = T.Small)
            // The news: the day's own in red, each on its line; medals and tasks gathered on one line each.
            val (medals, rest) = p.notes.partition { it.news == News.MEDAL }
            val (quests, others) = rest.partition { it.news == News.QUEST }
            for (n in others) {
                Text(w.line(n), style = if (n.news in STRONG) T.Italic.copy(color = C.red) else T.Italic)
            }
            if (quests.isNotEmpty()) {
                val paid = quests.sumOf { Quests.rewardOf(it.ref) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Check(true, Modifier.size(14.dp))
                    Text(
                        stringResource(R.string.j_quests, quests.joinToString(", ") { w.questShort(it.ref) }, w.coins(paid)),
                        style = T.Italic, modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            if (medals.isNotEmpty()) {
                Row(verticalAlignment = Alignment.Top) {
                    Medals.of(home.world, medals.first().ref)?.let { MedalArt(it, true, Modifier.width(18.dp).height(22.dp)) }
                    Text(
                        stringResource(
                            R.string.j_medals,
                            medals.take(3).joinToString(", ") { w.medal(it.ref) } +
                                if (medals.size > 3) " " + stringResource(R.string.j_more, medals.size - 3) else "",
                        ),
                        style = T.Italic.copy(color = C.red), modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
    Box(Modifier.fillMaxWidth().padding(start = 88.dp).height(0.6.dp).background(C.ink.copy(alpha = 0.25f)))
}

/**
 * The week at a glance: a bar for each day's steps from Monday, with a tick at the day's task, red
 * where it was met; then the day's task and the week's two, each with how far along it is and what it pays.
 */
@Composable
private fun Week(home: Home) {
    val j = home.j
    val w = home.words
    val quests = Quests.of(j, home.today)
    val start = Quests.weekStart(home.today)
    val days = (start until start + 7).toList()
    val steps = days.map { j.days[it] ?: 0 }
    val targets = days.map { Quests.dayTarget(j, it) }
    val met = days.indices.count { days[it] >= j.start && steps[it] >= targets[it] }
    val km = days.sumOf { j.dayKm[it] ?: 0.0 }
    val locale = if (home.ru) Locale.forLanguageTag("ru") else Locale.ENGLISH
    Framed(Modifier.fillMaxWidth(), pad = 12.dp) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(stringResource(R.string.tasks_week).uppercase(), style = T.Eyebrow, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.j_week_sum, w.km(km), met, quests[2].target), style = T.Small)
        }
        val most = targets.maxOrNull() ?: 1
        val top = (steps.maxOrNull() ?: 0).toFloat().coerceIn(most * 1.3f, most * 2.5f)
        Canvas(Modifier.fillMaxWidth().height(84.dp).padding(top = 10.dp)) {
            val s = density
            val slot = size.width / 7
            val bw = slot * 0.5f
            drawLine(C.ink.copy(alpha = 0.5f), Offset(0f, size.height), Offset(size.width, size.height), 0.8f * s)
            for (i in 0 until 7) {
                val x = slot * i + (slot - bw) / 2
                val d = days[i]
                // Days to come, and days before the journey began, are only pencilled in.
                val future = d > home.today || d < j.start
                val cut = steps[i] > top
                val h = size.height * minOf(steps[i].toFloat(), top) / top
                val done = steps[i] >= targets[i] && d >= j.start
                if (!future && h > 0) {
                    drawRect(if (done) C.red else C.sea.copy(alpha = 0.8f), Offset(x, size.height - h), Size(bw, h))
                    drawRect(C.ink, Offset(x, size.height - h), Size(bw, h), style = Stroke(0.8f * s))
                    // Taller than the chart: two strokes across the top, as an engraver breaks a long column.
                    if (cut) for (k in 0..1) {
                        val y = 5 * s + k * 4 * s
                        drawLine(C.paper, Offset(x - 2 * s, y + 2 * s), Offset(x + bw + 2 * s, y - 2 * s), 2.2f * s)
                    }
                }
                // The day's task, as a tick across its bar.
                val ty = size.height - size.height * targets[i] / top
                drawLine(
                    if (future) C.faint else C.ink, Offset(x - 3 * s, ty), Offset(x + bw + 3 * s, ty), 1.2f * s,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3 * s, 2 * s)),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 3.dp)) {
            for (d in days) {
                val name = LocalDate.ofEpochDay(d).dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
                val isToday = d == home.today
                Text(
                    name, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = T.Small.copy(color = if (isToday) C.red else if (d > home.today || d < j.start) C.faint else C.muted, fontWeight = if (isToday) FontWeight.Bold else null),
                )
            }
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 10.dp).height(0.6.dp).background(C.ink.copy(alpha = 0.4f)))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (q in quests) TaskRow(home, q)
        }
    }
}

/** A task: done or not, what it asks, how far along, and what it pays. */
@Composable
private fun TaskRow(home: Home, q: Quest) {
    val w = home.words
    Row(verticalAlignment = Alignment.Top) {
        Check(q.done, Modifier.padding(top = 2.dp).size(18.dp))
        Column(Modifier.weight(1f).padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(w.quest(q), style = T.Body.copy(color = if (q.done) C.muted else C.ink))
            Bar((q.progress / q.target).toFloat(), q.done)
            val shown = when (q.kind) {
                QuestKind.DAY_STEPS -> w.group(q.progress.toLong()) + " / " + w.group(q.target.toLong())
                QuestKind.WEEK_KM -> stringResource(R.string.km_n, w.km(q.progress) + " / " + w.group(q.target.toLong()))
                QuestKind.WEEK_DAYS -> q.progress.toInt().toString() + " / " + q.target
            }
            Text(shown, style = T.Small)
        }
        Text(
            stringResource(R.string.task_reward, w.coins(q.reward)),
            style = T.Sub.copy(color = if (q.done) C.red else C.muted), modifier = Modifier.padding(start = 10.dp),
        )
    }
}

/** A round box, ticked in red when done. */
@Composable
private fun Check(done: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = density
        val r = size.minDimension / 2 - s
        val c = Offset(size.width / 2, size.height / 2)
        if (done) drawCircle(C.red, r, c)
        drawCircle(if (done) C.ink else C.faint, r, c, style = Stroke(1.2f * s))
        if (done) {
            val tick = Path().apply {
                moveTo(c.x - r * 0.45f, c.y + r * 0.02f); lineTo(c.x - r * 0.1f, c.y + r * 0.38f); lineTo(c.x + r * 0.5f, c.y - r * 0.35f)
            }
            drawPath(tick, C.paper, style = Stroke(1.8f * s))
        }
    }
}
