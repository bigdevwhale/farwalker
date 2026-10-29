package app.farwalker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.farwalker.ui.theme.C
import app.farwalker.ui.theme.T

/** The double rule of an old title page: a heavy line and a hairline under it. */
@Composable
fun Rule(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.2.dp).background(C.ink))
        Box(Modifier.fillMaxWidth().padding(top = 2.dp).height(0.6.dp).background(C.ink.copy(alpha = 0.6f)))
    }
}

/** A sheet of paper framed in two ink lines. */
@Composable
fun Framed(modifier: Modifier = Modifier, pad: Dp = 14.dp, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.background(C.paper).border(1.2.dp, C.ink).padding(3.dp).border(0.6.dp, C.ink.copy(alpha = 0.6f))) {
        Column(Modifier.padding(pad), content = content)
    }
}

/** A button cut like a label: red with paper letters, or paper with ink. */
@Composable
fun Btn(text: String, modifier: Modifier = Modifier, primary: Boolean = true, enabled: Boolean = true, sub: String? = null, onClick: () -> Unit) {
    val bg = when {
        !enabled -> C.paper2
        primary -> C.red
        else -> C.paper
    }
    val fg = when {
        !enabled -> C.faint
        primary -> C.paper
        else -> C.ink
    }
    Column(
        modifier
            .background(bg)
            .border(1.dp, if (enabled) C.ink else C.faint)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = T.Button.copy(color = fg), textAlign = TextAlign.Center)
        if (sub != null) Text(sub, style = T.Small.copy(color = fg.copy(alpha = 0.85f)), textAlign = TextAlign.Center)
    }
}

/** A page of its own over the tabs: a title with a back arrow, and scrolling content. */
@Composable
fun Sheet(title: String, onBack: () -> Unit, scroll: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(C.paper).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("←", style = T.Title, modifier = Modifier.clickable(onClick = onBack).padding(horizontal = 12.dp, vertical = 4.dp))
            Text(title, style = T.Title)
        }
        Rule(Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        val m = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp)
        Column(if (scroll) m.verticalScroll(rememberScrollState()) else m, content = content)
    }
}

@Composable
fun Field(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().background(C.land).border(1.dp, C.ink).padding(horizontal = 12.dp, vertical = 10.dp)) {
        if (value.isEmpty()) Text(hint, style = T.Body.copy(color = C.faint))
        BasicTextField(value, onChange, textStyle = T.Body, singleLine = true, cursorBrush = SolidColor(C.red), modifier = Modifier.fillMaxWidth())
    }
}

/** A small framed dialog on paper. */
@Composable
fun PaperDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Framed(Modifier.fillMaxWidth(), pad = 16.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}

/** A label above a value: "PURSE / 214 coins". */
@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null) {
    Column(modifier) {
        Text(label.uppercase(), style = T.Eyebrow)
        Text(value, style = T.Heading)
        if (sub != null) Text(sub, style = T.Small)
    }
}
