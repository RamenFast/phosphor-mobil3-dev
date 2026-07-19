package dev.phosphor.mobil3.ui

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import dev.phosphor.mobil3.R

// The desktop's exact faces (phosphor-app/assets/fonts), one product identity:
// JetBrains Mono for ALL data; IBM Plex Sans for prose (consent copy, gentle notes).
val MonoFace = FontFamily(Font(R.font.jetbrains_mono))
val ProseFace = FontFamily(Font(R.font.ibm_plex_sans))
val ProseMediumFace = FontFamily(Font(R.font.ibm_plex_sans_medium, FontWeight.Medium))

// Type scale — data is mono, sized by tier; prose reads in Plex.
object Type {
    val dataXs = 10.sp   // swatch labels, footnotes
    val dataSm = 11.sp   // section headings, band annotations
    val data = 12.5.sp   // the working size: rows, keys, readouts
    val dataLg = 14.sp   // sheet rows, titles in the console
    val dataXl = 16.5.sp // stone glyphs, big readouts
    val prose = 13.sp    // humanist reading face
}

// The one text primitive for data. Sharp, mono, ellipsized.
@Composable
fun Mono(
    text: String,
    color: Color,
    size: TextUnit = Type.data,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    letterSpacing: TextUnit = TextUnit.Unspecified,
) {
    BasicText(
        text = text,
        style = TextStyle(
            color = color, fontFamily = MonoFace, fontSize = size,
            letterSpacing = letterSpacing,
        ),
        maxLines = maxLines, overflow = TextOverflow.Ellipsis, modifier = modifier,
    )
}

// The one text primitive for prose (gentle notes, consent copy). In Annotated
// rooms (the service bench) prose renders in the mono face — service manuals
// are set on a line printer; the humanist voice returns in every other room.
@Composable
fun Prose(
    text: String,
    color: Color,
    size: TextUnit = Type.prose,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    medium: Boolean = false,
) {
    val mono = LocalRoomStyle.current.monoProse
    BasicText(
        text = text,
        style = TextStyle(
            color = color,
            fontFamily = if (mono) MonoFace else if (medium) ProseMediumFace else ProseFace,
            fontSize = size, lineHeight = size * 1.45,
        ),
        maxLines = maxLines, overflow = TextOverflow.Ellipsis, modifier = modifier,
    )
}
