package app.farwalker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.farwalker.R

/** The colours of an old engraved atlas: rag paper, iron-gall ink, a red route, blue-grey seas. */
@Immutable
object C {
    val paper = Color(0xFFEFE3C8)
    val paper2 = Color(0xFFE5D5B0)
    val paperDeep = Color(0xFFD8C49A)
    val land = Color(0xFFF3E9D2)
    val ink = Color(0xFF2B2118)
    val muted = Color(0xFF6B5A45)
    val faint = Color(0xFFA08E72)
    val red = Color(0xFF9E2B20)
    val redSoft = Color(0xFFC0705A)
    val sea = Color(0xFF4E6577)
    val seaSoft = Color(0xFF8FA0A8)
    val wash = Color(0xFFD9A85F)
    val green = Color(0xFF4F6B45)
    val violet = Color(0xFF5B3F6E)
}

/** Cormorant for the engraved titles, PT Serif to read by (both SIL OFL, both with Cyrillic). */
val Cormorant = FontFamily(
    Font(R.font.cormorant_semibold, FontWeight.SemiBold),
    Font(R.font.cormorant_bold, FontWeight.Bold),
    Font(R.font.cormorant_italic, FontWeight.Normal, FontStyle.Italic),
)
val Serif = FontFamily(
    Font(R.font.pt_serif_regular, FontWeight.Normal),
    Font(R.font.pt_serif_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.pt_serif_bold, FontWeight.Bold),
)

object T {
    val Display = TextStyle(fontFamily = Cormorant, fontWeight = FontWeight.Bold, fontSize = 38.sp, lineHeight = 40.sp, color = C.ink)
    val Title = TextStyle(fontFamily = Cormorant, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 30.sp, color = C.ink)
    val Heading = TextStyle(fontFamily = Cormorant, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 24.sp, color = C.ink)
    val Eyebrow = TextStyle(fontFamily = Cormorant, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 15.sp, letterSpacing = 0.18.em, color = C.muted)
    val Body = TextStyle(fontFamily = Serif, fontSize = 15.sp, lineHeight = 21.sp, color = C.ink)
    val Sub = TextStyle(fontFamily = Serif, fontSize = 13.sp, lineHeight = 18.sp, color = C.muted)
    val Italic = TextStyle(fontFamily = Serif, fontStyle = FontStyle.Italic, fontSize = 14.sp, lineHeight = 19.sp, color = C.muted)
    val Small = TextStyle(fontFamily = Serif, fontSize = 11.sp, lineHeight = 14.sp, color = C.muted)
    val Number = TextStyle(fontFamily = Cormorant, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 26.sp, color = C.ink)
    val Button = TextStyle(fontFamily = Cormorant, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 20.sp, letterSpacing = 0.04.em)
    val Map = TextStyle(fontFamily = Cormorant, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.22.em, color = C.ink)
    val MapTown = TextStyle(fontFamily = Cormorant, fontStyle = FontStyle.Italic, fontSize = 13.sp, color = C.ink)
}

@Composable
fun FarwalkerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = C.red, onPrimary = C.paper, background = C.paper, surface = C.paper, onSurface = C.ink,
            onBackground = C.ink, surfaceContainer = C.paper, surfaceContainerHigh = C.paper, surfaceContainerHighest = C.paper2,
            outline = C.ink, error = C.red,
        ),
        typography = MaterialTheme.typography.copy(bodyLarge = T.Body, bodyMedium = T.Body, bodySmall = T.Small, labelLarge = T.Button, titleLarge = T.Title),
        content = content,
    )
}
