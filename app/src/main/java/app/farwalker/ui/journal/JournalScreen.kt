package app.farwalker.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.farwalker.R
import app.farwalker.domain.News
import app.farwalker.domain.Night
import app.farwalker.domain.Note
import app.farwalker.ui.Home
import app.farwalker.ui.components.Rule
import app.farwalker.ui.passport.modeWord
import app.farwalker.ui.theme.C
import app.farwalker.ui.theme.T

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
            Column(Modifier.padding(top = 12.dp, bottom = 6.dp)) {
                Text(stringResource(R.string.journal), style = T.Display)
                Rule()
            }
        }
        items(pages, key = { it.day }) { PageRow(home, it) }
        if (j.nights.isEmpty()) item { Text(stringResource(R.string.j_empty), style = T.Italic, modifier = Modifier.padding(vertical = 12.dp)) }
        item { Box(Modifier.padding(16.dp)) }
    }
}

@Composable
private fun PageRow(home: Home, p: Page) {
    val w = home.words
    val today = p.day == home.today
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.width(84.dp)) {
            Text(if (today) stringResource(R.string.j_today) else w.shortDate(p.day), style = T.Heading.copy(color = if (today) C.red else C.ink))
            Text(stringResource(R.string.day_n, (p.day - home.j.start + 1).toInt()), style = T.Small)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            val km = if (today) home.kmToday else p.night?.km ?: (home.j.dayKm[p.day] ?: 0.0)
            p.night?.let { n ->
                val where = when {
                    modeWord(n.mode) != null -> stringResource(modeWord(n.mode)!!)
                    n.next >= 0 -> stringResource(R.string.j_road, w.town(n.town), w.town(n.next), w.km(n.at))
                    else -> w.town(n.town)
                }
                Text(stringResource(R.string.j_night, where), style = T.Body)
                Text(w.country(n.iso), style = T.Small)
            }
            if (today) Text(w.town(home.travel.sleepTown(home.j)), style = T.Body)
            for (n in p.notes) {
                val strong = n.news == News.COUNTRY || n.news == News.CONTINENT || n.news == News.CAPE || n.news == News.ARRIVED
                Text(w.line(n), style = if (strong) T.Italic.copy(color = C.red) else T.Italic)
            }
            if (km > 0.05) Text(stringResource(R.string.km_n, w.km(km)) + (home.j.days[p.day]?.let { " · " + stringResource(R.string.steps_n, w.group(it.toLong())) } ?: ""), style = T.Small)
        }
    }
    Box(Modifier.fillMaxWidth().padding(start = 96.dp).background(C.ink.copy(alpha = 0.25f)).padding(top = 0.6.dp))
}
