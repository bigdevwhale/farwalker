package app.farwalker.ui.passport

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farwalker.domain.Sight
import app.farwalker.ui.theme.C
import app.farwalker.ui.theme.T

/** The photo file of a sight in assets/sights, named as tools/sight_photos.py names it. */
fun sightPhoto(sg: Sight): String = "sights/" + sg.en.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_') + ".jpg"

/**
 * A sight as an instant photo (#9): in colour, with its name and the day written under it, once the
 * traveller has been there; faded and grey, «still ahead», until then.
 */
@Composable
fun SightPolaroid(sg: Sight, name: String, caption: String, seen: Boolean, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val bmp = remember(sg.key) { runCatching { ctx.assets.open(sightPhoto(sg)).use { BitmapFactory.decodeStream(it) } }.getOrNull() } ?: return
    val tilt = remember(sg.key) { ((sg.key.hashCode() % 5) - 2) * 0.9f }
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth(0.78f).rotate(tilt).shadow(6.dp).background(Color(0xFFFBF8F1))
                .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 6.dp),
        ) {
            Image(
                bmp.asImageBitmap(), name, Modifier.fillMaxWidth().aspectRatio(1f).alpha(if (seen) 1f else 0.55f),
                contentScale = ContentScale.Crop,
                colorFilter = if (seen) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
            )
            Text(name, style = T.Italic.copy(fontSize = 17.sp, color = C.ink), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), maxLines = 1)
            Text(caption, style = T.Small.copy(color = if (seen) C.red else C.muted), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}
