package dev.phosphor.mobil3.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

// Settings legends share the mode picker's 24-unit, single-stroke construction.
// Every enclosure is closed; only traces, needles, rays and radio waves remain
// open because their run-off is part of what the figure means.
enum class SettingsGlyph {
    Signal,
    Display,
    Performance,
    Remote,
    RoomLight,
    About,
    Room,
    BeamColor,
    Fps,
    Hud,
    Grid,
    Deck,
    Knob,
    File,
    Folder,
    Capture,
    Mic,
}

object SettingsGlyphs {
    private fun builder(name: String) = ImageVector.Builder(
        name = "PhosphorSettings.$name",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )

    private fun ImageVector.Builder.hairline(draw: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.15f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            pathBuilder = draw,
        )
    }

    val Signal: ImageVector by lazy {
        builder("Signal").apply {
            hairline {
                moveTo(2f, 12f)
                lineTo(5f, 12f)
                lineTo(7f, 6f)
                lineTo(10f, 18f)
                lineTo(13f, 8f)
                lineTo(16f, 14f)
                lineTo(19f, 12f)
                lineTo(22f, 12f)
                moveTo(3f, 9.5f)
                lineTo(3f, 14.5f)
                moveTo(21f, 9.5f)
                lineTo(21f, 14.5f)
            }
        }.build()
    }

    val Display: ImageVector by lazy {
        builder("Display").apply {
            hairline {
                moveTo(3f, 3.5f)
                lineTo(21f, 3.5f)
                lineTo(21f, 18.5f)
                lineTo(3f, 18.5f)
                close()
                moveTo(5.5f, 6f)
                lineTo(18.5f, 6f)
                lineTo(18.5f, 16f)
                lineTo(5.5f, 16f)
                close()
                moveTo(6.5f, 11.5f)
                lineTo(9f, 11.5f)
                lineTo(10.2f, 8.5f)
                lineTo(12f, 14f)
                lineTo(14f, 10f)
                lineTo(17.5f, 10f)
                moveTo(8f, 21f)
                lineTo(16f, 21f)
            }
        }.build()
    }

    val Performance: ImageVector by lazy {
        builder("Performance").apply {
            hairline {
                moveTo(12f, 3f)
                curveTo(17.1f, 3f, 21f, 6.9f, 21f, 12f)
                curveTo(21f, 17.1f, 17.1f, 21f, 12f, 21f)
                curveTo(6.9f, 21f, 3f, 17.1f, 3f, 12f)
                curveTo(3f, 6.9f, 6.9f, 3f, 12f, 3f)
                close()
                moveTo(12f, 12f)
                lineTo(17.5f, 7.5f)
                moveTo(6f, 12f)
                lineTo(8f, 12f)
                moveTo(7.8f, 7.8f)
                lineTo(9.2f, 9.2f)
                moveTo(12f, 6f)
                lineTo(12f, 8f)
                moveTo(16.2f, 16.2f)
                lineTo(17.6f, 17.6f)
            }
        }.build()
    }

    val Remote: ImageVector by lazy {
        builder("Remote").apply {
            hairline {
                moveTo(12f, 9.5f)
                lineTo(14.5f, 12f)
                lineTo(12f, 14.5f)
                lineTo(9.5f, 12f)
                close()
                moveTo(12f, 14.5f)
                lineTo(12f, 21f)
                moveTo(8f, 21f)
                lineTo(16f, 21f)
                moveTo(8.5f, 8.5f)
                curveTo(6.6f, 10.4f, 6.6f, 13.6f, 8.5f, 15.5f)
                moveTo(15.5f, 8.5f)
                curveTo(17.4f, 10.4f, 17.4f, 13.6f, 15.5f, 15.5f)
                moveTo(5.5f, 5.5f)
                curveTo(1.9f, 9.1f, 1.9f, 14.9f, 5.5f, 18.5f)
                moveTo(18.5f, 5.5f)
                curveTo(22.1f, 9.1f, 22.1f, 14.9f, 18.5f, 18.5f)
            }
        }.build()
    }

    val RoomLight: ImageVector by lazy {
        builder("RoomLight").apply {
            hairline {
                moveTo(4f, 5f)
                lineTo(20f, 5f)
                lineTo(20f, 19f)
                lineTo(4f, 19f)
                close()
                moveTo(7f, 12f)
                lineTo(9f, 12f)
                lineTo(10.5f, 8f)
                lineTo(13f, 16f)
                lineTo(15f, 10f)
                lineTo(17f, 12f)
                moveTo(12f, 2f)
                lineTo(12f, 4f)
                moveTo(12f, 20f)
                lineTo(12f, 22f)
            }
        }.build()
    }

    val About: ImageVector by lazy {
        builder("About").apply {
            hairline {
                moveTo(3f, 3f)
                lineTo(21f, 3f)
                lineTo(21f, 21f)
                lineTo(3f, 21f)
                close()
                moveTo(5.5f, 5.5f)
                lineTo(18.5f, 5.5f)
                lineTo(18.5f, 15f)
                lineTo(5.5f, 15f)
                close()
                moveTo(7f, 10.5f)
                lineTo(9f, 10.5f)
                lineTo(10.5f, 7.5f)
                lineTo(12.5f, 13f)
                lineTo(14f, 9.5f)
                lineTo(17f, 9.5f)
                moveTo(8f, 18f)
                curveTo(8f, 17.2f, 8.7f, 16.5f, 9.5f, 16.5f)
                curveTo(10.3f, 16.5f, 11f, 17.2f, 11f, 18f)
                curveTo(11f, 18.8f, 10.3f, 19.5f, 9.5f, 19.5f)
                curveTo(8.7f, 19.5f, 8f, 18.8f, 8f, 18f)
                close()
                moveTo(14f, 18f)
                curveTo(14f, 17.2f, 14.7f, 16.5f, 15.5f, 16.5f)
                curveTo(16.3f, 16.5f, 17f, 17.2f, 17f, 18f)
                curveTo(17f, 18.8f, 16.3f, 19.5f, 15.5f, 19.5f)
                curveTo(14.7f, 19.5f, 14f, 18.8f, 14f, 18f)
                close()
            }
        }.build()
    }

    val Room: ImageVector by lazy {
        builder("Room").apply {
            hairline {
                moveTo(3f, 6f)
                lineTo(12f, 2.5f)
                lineTo(21f, 6f)
                lineTo(21f, 19f)
                lineTo(12f, 22f)
                lineTo(3f, 19f)
                close()
                moveTo(12f, 2.5f)
                lineTo(12f, 22f)
                moveTo(3f, 6f)
                lineTo(12f, 9.5f)
                lineTo(21f, 6f)
            }
        }.build()
    }

    val BeamColor: ImageVector by lazy {
        builder("BeamColor").apply {
            hairline {
                moveTo(12f, 3f)
                lineTo(20f, 12f)
                lineTo(12f, 21f)
                lineTo(4f, 12f)
                close()
                moveTo(6.5f, 12f)
                lineTo(8.5f, 12f)
                lineTo(10f, 8f)
                lineTo(12.5f, 16f)
                lineTo(14.5f, 10f)
                lineTo(17.5f, 12f)
                moveTo(12f, 1f)
                lineTo(12f, 2.5f)
                moveTo(12f, 21.5f)
                lineTo(12f, 23f)
            }
        }.build()
    }

    /** Frame cadence: a sweep line broken into four equal frame ticks. */
    val Fps: ImageVector by lazy {
        builder("Fps").apply {
            hairline {
                moveTo(3f, 17f)
                lineTo(21f, 17f)
                moveTo(3f, 17f)
                lineTo(3f, 13f)
                moveTo(9f, 17f)
                lineTo(9f, 11f)
                moveTo(15f, 17f)
                lineTo(15f, 9f)
                moveTo(21f, 17f)
                lineTo(21f, 7f)
            }
        }.build()
    }

    /** The nerd HUD: a corner bracket holding three readout lines. */
    val Hud: ImageVector by lazy {
        builder("Hud").apply {
            hairline {
                moveTo(4f, 10f)
                lineTo(4f, 4f)
                lineTo(10f, 4f)
                moveTo(7f, 9f)
                lineTo(20f, 9f)
                moveTo(7f, 13f)
                lineTo(17f, 13f)
                moveTo(7f, 17f)
                lineTo(14f, 17f)
            }
        }.build()
    }

    /** The graticule itself: closed frame, 3x3 lattice. */
    val Grid: ImageVector by lazy {
        builder("Grid").apply {
            hairline {
                moveTo(4f, 4f)
                lineTo(20f, 4f)
                lineTo(20f, 20f)
                lineTo(4f, 20f)
                close()
                moveTo(9.33f, 4f)
                lineTo(9.33f, 20f)
                moveTo(14.67f, 4f)
                lineTo(14.67f, 20f)
                moveTo(4f, 9.33f)
                lineTo(20f, 9.33f)
                moveTo(4f, 14.67f)
                lineTo(20f, 14.67f)
            }
        }.build()
    }

    /** The deck: a closed transport chassis carrying two open tape reels. */
    val Deck: ImageVector by lazy {
        builder("Deck").apply {
            hairline {
                moveTo(3f, 6f)
                lineTo(21f, 6f)
                lineTo(21f, 18f)
                lineTo(3f, 18f)
                close()
                moveTo(11f, 12f)
                curveTo(11f, 13.66f, 9.66f, 15f, 8f, 15f)
                curveTo(6.34f, 15f, 5f, 13.66f, 5f, 12f)
                curveTo(5f, 10.34f, 6.34f, 9f, 8f, 9f)
                curveTo(9.66f, 9f, 11f, 10.34f, 11f, 12f)
                close()
                moveTo(19f, 12f)
                curveTo(19f, 13.66f, 17.66f, 15f, 16f, 15f)
                curveTo(14.34f, 15f, 13f, 13.66f, 13f, 12f)
                curveTo(13f, 10.34f, 14.34f, 9f, 16f, 9f)
                curveTo(17.66f, 9f, 19f, 10.34f, 19f, 12f)
                close()
            }
        }.build()
    }

    /** Settings: a front-panel knob — closed dial, pointer, travel ticks. */
    val Knob: ImageVector by lazy {
        builder("Knob").apply {
            hairline {
                moveTo(19f, 12f)
                curveTo(19f, 15.87f, 15.87f, 19f, 12f, 19f)
                curveTo(8.13f, 19f, 5f, 15.87f, 5f, 12f)
                curveTo(5f, 8.13f, 8.13f, 5f, 12f, 5f)
                curveTo(15.87f, 5f, 19f, 8.13f, 19f, 12f)
                close()
                moveTo(12f, 12f)
                lineTo(8.6f, 8.6f)
                moveTo(4f, 16.5f)
                lineTo(5.6f, 15.7f)
                moveTo(20f, 16.5f)
                lineTo(18.4f, 15.7f)
                moveTo(12f, 2.8f)
                lineTo(12f, 4.4f)
            }
        }.build()
    }

    // SOURCE picker legends: what the beam can listen to. Same 24-unit hairline law —
    // enclosures closed, waves and radio arcs open because the run-off is the meaning.
    val FileGlyph: ImageVector by lazy {
        builder("File").apply {
            hairline {
                // A page with a dog-ear, carrying its own little trace.
                moveTo(6f, 3f)
                lineTo(14f, 3f)
                lineTo(18f, 7f)
                lineTo(18f, 21f)
                lineTo(6f, 21f)
                close()
                moveTo(14f, 3f)
                lineTo(14f, 7f)
                lineTo(18f, 7f)
                moveTo(8.5f, 14.5f)
                lineTo(10f, 11.5f)
                lineTo(12f, 16f)
                lineTo(14f, 12f)
                lineTo(15.5f, 14.5f)
            }
        }.build()
    }

    val FolderGlyph: ImageVector by lazy {
        builder("Folder").apply {
            hairline {
                // The queue folder: a tab, a mouth, and the wave waiting inside.
                moveTo(3f, 6f)
                lineTo(9f, 6f)
                lineTo(11f, 8.5f)
                lineTo(21f, 8.5f)
                lineTo(21f, 19f)
                lineTo(3f, 19f)
                close()
                moveTo(6.5f, 15f)
                lineTo(8.5f, 11.5f)
                lineTo(11f, 16f)
                lineTo(13.5f, 11.5f)
                lineTo(15.5f, 15f)
                lineTo(17.5f, 13f)
            }
        }.build()
    }

    val CaptureGlyph: ImageVector by lazy {
        builder("Capture").apply {
            hairline {
                // Another app's window, radiating what it plays — the arcs stay open.
                moveTo(4f, 7f)
                lineTo(13f, 7f)
                lineTo(13f, 17f)
                lineTo(4f, 17f)
                close()
                moveTo(6f, 14f)
                lineTo(7.5f, 10.5f)
                lineTo(9.5f, 14.5f)
                lineTo(11f, 11.5f)
                moveTo(15.5f, 10f)
                lineTo(17f, 12f)
                lineTo(15.5f, 14f)
                moveTo(18f, 8f)
                lineTo(20.5f, 12f)
                lineTo(18f, 16f)
            }
        }.build()
    }

    val MicGlyph: ImageVector by lazy {
        builder("Mic").apply {
            hairline {
                // The room mic: capsule closed, cage open at the throat, feet planted.
                moveTo(10f, 4.5f)
                lineTo(14f, 4.5f)
                lineTo(14f, 12.5f)
                lineTo(10f, 12.5f)
                close()
                moveTo(7.5f, 10.5f)
                lineTo(7.5f, 13f)
                lineTo(9f, 15.5f)
                lineTo(15f, 15.5f)
                lineTo(16.5f, 13f)
                lineTo(16.5f, 10.5f)
                moveTo(12f, 15.5f)
                lineTo(12f, 19f)
                moveTo(9f, 19.5f)
                lineTo(15f, 19.5f)
            }
        }.build()
    }

    fun vector(glyph: SettingsGlyph): ImageVector = when (glyph) {
        SettingsGlyph.Deck -> Deck
        SettingsGlyph.Knob -> Knob
        SettingsGlyph.Fps -> Fps
        SettingsGlyph.Hud -> Hud
        SettingsGlyph.Grid -> Grid
        SettingsGlyph.Signal -> Signal
        SettingsGlyph.Display -> Display
        SettingsGlyph.Performance -> Performance
        SettingsGlyph.Remote -> Remote
        SettingsGlyph.RoomLight -> RoomLight
        SettingsGlyph.About -> About
        SettingsGlyph.Room -> Room
        SettingsGlyph.BeamColor -> BeamColor
        SettingsGlyph.File -> FileGlyph
        SettingsGlyph.Folder -> FolderGlyph
        SettingsGlyph.Capture -> CaptureGlyph
        SettingsGlyph.Mic -> MicGlyph
    }
}

// One set, four room readings. The base geometry never forks: only the ink and
// the small piece of chrome laid around it respond to LocalRoomStyle.
@Composable
fun SettingsGlyphIcon(
    glyph: SettingsGlyph,
    p: Palette,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val character = LocalRoomStyle.current.character
    val vector = SettingsGlyphs.vector(glyph)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        if (character == ChromeCharacter.Carved) {
            // A displaced rim catches the room light without thickening the
            // actual legend stroke.
            Image(
                vector, contentDescription = null,
                modifier = Modifier.fillMaxSize().offset((-0.55).dp, (-0.55).dp),
                colorFilter = ColorFilter.tint(p.stoneHi.copy(alpha = 0.42f)),
            )
        }
        Image(
            vector, contentDescription = null, modifier = Modifier.fillMaxSize(),
            colorFilter = ColorFilter.tint(
                if (character == ChromeCharacter.Engraved) p.lineStrong else p.muted
            ),
        )
        when (character) {
            ChromeCharacter.Annotated -> Canvas(Modifier.fillMaxSize()) {
                val stroke = 0.8.dp.toPx()
                val x = this.size.width * 0.88f
                val y = this.size.height * 0.18f
                drawLine(p.accent.copy(alpha = 0.72f), Offset(x - 3.dp.toPx(), y), Offset(x, y), stroke)
                drawLine(p.accent.copy(alpha = 0.72f), Offset(x, y), Offset(x, y + 3.dp.toPx()), stroke)
            }
            ChromeCharacter.Glass -> Canvas(Modifier.fillMaxSize()) {
                // A short, hard specular corner: glass, not a friendly rounded badge.
                val stroke = 0.8.dp.toPx()
                val inset = 2.dp.toPx()
                drawLine(
                    p.stoneHi.copy(alpha = 0.82f), Offset(inset, inset),
                    Offset(this.size.width * 0.46f, inset), stroke,
                )
                drawLine(
                    p.stoneHi.copy(alpha = 0.55f), Offset(inset, inset),
                    Offset(inset, this.size.height * 0.34f), stroke,
                )
            }
            ChromeCharacter.Carved, ChromeCharacter.Engraved -> Unit
        }
    }
}

/**
 * S9's dual-affordance legend: the three square points retain the familiar
 * "more" reading, while the hairline rail and restrained upward index say that
 * the same control can be physically pulled. No enclosing icon bubble: the key's
 * own border is the instrument geometry.
 */
@Composable
fun OverflowHandleGlyph(
    p: Palette,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(26.dp)) {
        val ink = if (active) p.accent else p.ink2
        val hairline = 1.dp.toPx()
        val cx = size.width / 2f
        val railY = size.height * 0.34f
        drawLine(
            ink,
            Offset(size.width * 0.24f, railY),
            Offset(size.width * 0.76f, railY),
            hairline,
            cap = StrokeCap.Butt,
        )
        drawLine(
            ink.copy(alpha = 0.82f),
            Offset(cx, railY),
            Offset(cx, size.height * 0.17f),
            hairline,
            cap = StrokeCap.Butt,
        )
        val dot = 2.2.dp.toPx()
        val dotY = size.height * 0.66f - dot / 2f
        listOf(0.31f, 0.50f, 0.69f).forEach { x ->
            drawRect(
                ink,
                topLeft = Offset(size.width * x - dot / 2f, dotY),
                size = androidx.compose.ui.geometry.Size(dot, dot),
            )
        }
    }
}

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
                        center = Offset(
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
                        center = Offset(cx, cy + i * r * 0.06f),
                    )
                }
            }
        }
    }
}

@Suppress("unused")
private val keepPathEffectImport = PathEffect.Companion
