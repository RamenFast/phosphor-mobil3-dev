package dev.phosphor.mobil3.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

// Engraved mode glyphs — tiny etched vectors of each mode's characteristic figure.
// Static, ink_2, hairline stroke: an instrument's front-panel legends, not icons.
@Composable
fun ModeGlyph(modeIndex: Int, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val r = w * 0.38f
        val stroke = Stroke(width = 1.2.dp.toPx())
        fun poly(points: List<Pair<Float, Float>>, close: Boolean = false) {
            val p = Path()
            points.forEachIndexed { i, (x, y) -> if (i == 0) p.moveTo(x, y) else p.lineTo(x, y) }
            if (close) p.close()
            drawPath(p, color, style = stroke)
        }
        fun curve(n: Int, f: (Float) -> Pair<Float, Float>) =
            poly((0..n).map { f(it / n.toFloat()) })
        when (modeIndex) {
            0 -> curve(64) { t -> // xy — 3:2 lissajous
                val a = t * (2f * Math.PI.toFloat())
                Pair(cx + r * sin(3f * a + 1.57f), cy + r * sin(2f * a))
            }
            1 -> curve(64) { t -> // xy45 — the same figure, turned on its shoulder
                val a = t * (2f * Math.PI.toFloat())
                val x = r * sin(3f * a + 1.57f)
                val y = r * sin(2f * a)
                Pair(cx + (x - y) * 0.707f, cy + (x + y) * 0.707f)
            }
            2 -> curve(72) { t -> // swirl — a spiral falling inward
                val a = t * 6f * Math.PI.toFloat()
                val rr = r * (1f - t * 0.85f)
                Pair(cx + rr * cos(a), cy + rr * sin(a))
            }
            3 -> { // dots — the figure sampled, not traced
                for (i in 0 until 10) {
                    val a = i / 10f * (2f * Math.PI.toFloat())
                    drawCircle(
                        color, radius = 1.2.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(
                            cx + r * sin(2f * a + 1.57f), cy + r * sin(3f * a),
                        ),
                    )
                }
            }
            4 -> curve(80) { t -> // attractor — two-lobed takens butterfly
                val a = t * (2f * Math.PI.toFloat())
                Pair(
                    cx + r * sin(a) * cos(a * 2f),
                    cy + r * 0.85f * sin(a * 2f) * 0.9f - r * 0.05f * sin(a * 5f),
                )
            }
            5 -> curve(72) { t -> // helix — the spring of time
                val a = t * 4f * Math.PI.toFloat()
                Pair(cx - r + t * 2f * r, cy + r * 0.62f * sin(a))
            }
            6 -> curve(48) { t -> // waveform
                Pair(cx - r + t * 2f * r, cy - r * 0.55f * sin(t * 4f * Math.PI.toFloat()))
            }
            7 -> curve(72) { t -> // ring oscillogram — a circle that carries the wave
                val a = t * (2f * Math.PI.toFloat())
                val rr = r * (0.82f + 0.14f * sin(a * 7f))
                Pair(cx + rr * cos(a), cy + rr * sin(a))
            }
            8 -> { // spectrum — bars
                val heights = listOf(0.35f, 0.7f, 1f, 0.55f, 0.8f, 0.4f)
                val bw = (w * 0.76f) / heights.size
                heights.forEachIndexed { i, hh ->
                    val x = w * 0.12f + i * bw + bw * 0.18f
                    poly(listOf(Pair(x, cy + r), Pair(x, cy + r - 2f * r * hh * 0.9f)))
                }
            }
            9 -> { // radial — bars around the circle
                for (i in 0 until 12) {
                    val a = i / 12f * (2f * Math.PI.toFloat())
                    val len = r * (0.45f + 0.5f * ((i * 7) % 5) / 5f)
                    poly(
                        listOf(
                            Pair(cx + r * 0.42f * cos(a), cy + r * 0.42f * sin(a)),
                            Pair(cx + (r * 0.42f + len * 0.55f) * cos(a), cy + (r * 0.42f + len * 0.55f) * sin(a)),
                        )
                    )
                }
            }
            else -> { // tunnel — rings racing away
                for (i in 0 until 4) {
                    val rr = r * (1f - i * 0.24f)
                    drawCircle(
                        color, radius = rr, style = stroke,
                        center = androidx.compose.ui.geometry.Offset(cx, cy + i * r * 0.06f),
                    )
                }
            }
        }
    }
}

@Suppress("unused")
private val keepPathEffectImport = PathEffect.Companion
