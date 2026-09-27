package dev.phosphor.mobil3.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The "reading the scope" figures: what left/right sound draws in the XY faces.
 * Pure point sets in −1..1 (x = left, y = right), drawn statically with the beam look.
 */
internal object ManualFigures {
    enum class Kind(val caption: String, val description: String) {
        MONO("mono", "Mono: left and right are the same, so the beam draws one straight diagonal line."),
        STEREO("wide stereo", "Wide stereo: left and right differ, so the beam fills an open cloud around the centre."),
        ANTI("out of phase", "Out of phase: right is the left turned upside down, so the line leans the other way."),
        CIRCLE("a quarter turn", "Two equal tones a quarter turn apart draw a circle."),
        KNOT("three to two", "Two tones in a three to two ratio draw a Lissajous knot."),
    }

    const val POINTS = 480

    fun points(kind: Kind, n: Int = POINTS): List<Pair<Float, Float>> = (0 until n).map { i ->
        val t = 2.0 * PI * i / n
        val (x, y) = when (kind) {
            Kind.MONO -> sin(3 * t).let { 0.85 * it to 0.85 * it }
            Kind.ANTI -> sin(3 * t).let { 0.85 * it to -0.85 * it }
            Kind.CIRCLE -> 0.85 * sin(t) to 0.85 * cos(t)
            Kind.KNOT -> 0.85 * sin(3 * t) to 0.85 * sin(2 * t + PI / 4)
            // Deterministic "music": shared low tone plus different partials per side.
            Kind.STEREO -> {
                val common = 0.45 * sin(2 * t)
                (common + 0.30 * sin(7 * t + 0.4) + 0.15 * sin(19 * t)) to
                    (common + 0.30 * sin(11 * t + 1.9) + 0.15 * sin(23 * t + 0.7))
            }
        }
        x.toFloat().coerceIn(-1f, 1f) to y.toFloat().coerceIn(-1f, 1f)
    }
}

/** A row of small static scope faces. No animation, so reduced motion needs nothing extra. */
@Composable
internal fun ScopeFigures(p: Palette, kinds: List<ManualFigures.Kind> = ManualFigures.Kind.entries) {
    kinds.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { kind -> ScopeFigure(kind, p, Modifier.weight(1f)) }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun ScopeFigure(kind: ManualFigures.Kind, p: Palette, modifier: Modifier) {
    val points = remember(kind) { ManualFigures.points(kind) }
    val beam = p.liveAccent
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = kind.description }) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f).background(Color.Black).border(Dim.hairline, p.line)) {
            val half = size.minDimension / 2f
            val centre = Offset(size.width / 2f, size.height / 2f)
            // Faint graticule: the two axes of the tube.
            drawLine(p.line, Offset(centre.x, 0f), Offset(centre.x, size.height), 1f)
            drawLine(p.line, Offset(0f, centre.y), Offset(size.width, centre.y), 1f)
            val path = Path()
            points.forEachIndexed { i, (x, y) ->
                // Right channel up: screen y grows downwards.
                val o = Offset(centre.x + x * half * 0.9f, centre.y - y * half * 0.9f)
                if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            path.close()
            // Glow, then the sharp beam: the phosphor look without motion.
            drawPath(path, beam.copy(alpha = 0.18f), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, beam, style = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Mono(kind.caption, p.ink2, Type.value, Modifier.padding(top = 4.dp), maxLines = 2)
    }
}
