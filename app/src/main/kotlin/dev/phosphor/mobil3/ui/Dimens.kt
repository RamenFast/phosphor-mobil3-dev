package dev.phosphor.mobil3.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

// Chrome geometry tokens. Sharp corners everywhere (no radius token on purpose).
object Dim {
    val hairline = 1.dp
    val stoneKey = 52.dp       // the carved controls (three exist app-wide)
    val flatKey = 44.dp        // flat hairline keys
    val cardMarginH = 12.dp    // chrome floats clear of the display's side glass
    val cardMarginBottom = 10.dp
    val sheetPad = 16.dp
    val consolePadH = 14.dp
    val consolePadV = 10.dp
    val rowPad = 14.dp
    val gap = 8.dp
    val gapLg = 12.dp
    val popoutGap = 8.dp         // measured from the console card's outer top edge
    val popoutWidth = 180.dp     // hugs the 2×2 nav grid + quick-toggle row
    val popoutPad = 8.dp         // inner card padding — dense but still breathing
    val popoutGridGap = 6.dp     // between the four destination cells
    val popoutPullTravel = 184.dp
    val landscapeConsoleMaxWidth = 620.dp
    val landscapeSheetMaxWidth = 600.dp
    val landscapeBandMaxWidth = 760.dp
    val bottomGestureBand = 88.dp
    // Top band belongs to Android's transient-bars swipe — never gain/orbit.
    val topGestureBand = 56.dp
    val sheetDismissDistance = 72.dp
    val chromeFlickVelocity = 920.dp // dp per second; converted at the gesture boundary

    // A tall sheet narrows only at its top edge, like a page pinched into a
    // binding. The border follows this geometry; there is no elevation/shadow.
    val sheetCurlInset = 20.dp
    val sheetCurlDepth = 14.dp
    const val sheetCurlStartFraction = 0.72f
    const val sheetCurlFullFraction = 0.94f

    const val consoleAlpha = 0.86f   // trace ghosts through the console
    const val sheetAlpha = 0.94f
    const val scrimAlpha = 0.40f
    const val chromeLuminanceCap = 0.60f // burn-in: chrome never exceeds ~60% of panel max
}

// The responsive chrome profile may deliberately differ from the current display
// orientation when UI PLACEMENT is locked. Text itself is never rotated.
val LocalChromeLandscape = staticCompositionLocalOf { false }

// Device-upright quadrant while UI PLACEMENT is locked: key labels and glyphs
// counter-rotate so they read from the edge the user is actually viewing from.
val LocalUiUpright = staticCompositionLocalOf { 0 }

// ── The upright primitive (Ben's ask #1) ──────────────────────────────────────
// ONE correct quadrant rotation. The naive `graphicsLayer { rotationZ = q*-90 }`
// rotated a cell's pixels WITHOUT re-measuring, so at 90°/270° a label clipped and
// misaligned against its un-rotated bounds. This remeasures the child with SWAPPED
// constraints on odd quadrants and reports the rotated bounding box as its own
// size — the cell occupies the space it actually needs. The layer rotation lives
// INSIDE the custom layout, so Compose still transforms pointer input: hit-testing
// stays correct. Square-ish cells (glyphs, short key labels) rotate as units; long
// prose must NOT be wrapped — it would read sideways (and unbounded cross-axis
// constraints would mis-measure it after the swap).
// q is CCW quadrants (rotationZ negative), matching PhosphorNative.setViewRotation.
fun Modifier.uprightRotate(quadrant: Int): Modifier {
    val q = ((quadrant % 4) + 4) % 4
    if (q == 0) return this
    return layout { measurable, constraints ->
        val swap = q % 2 != 0
        val childConstraints = if (swap) {
            Constraints(
                minWidth = constraints.minHeight,
                maxWidth = constraints.maxHeight,
                minHeight = constraints.minWidth,
                maxHeight = constraints.maxWidth,
            )
        } else constraints
        val placeable = measurable.measure(childConstraints)
        val boxW = if (swap) placeable.height else placeable.width
        val boxH = if (swap) placeable.width else placeable.height
        layout(boxW, boxH) {
            // Centre the child in the rotated bounding box, then spin about that
            // shared centre: a ±90° turn maps a (w×h) child exactly onto (h×w).
            placeable.placeWithLayer(
                x = (boxW - placeable.width) / 2,
                y = (boxH - placeable.height) / 2,
            ) {
                rotationZ = q * -90f
            }
        }
    }
}

// Element-upright wrapper: reads LocalUiUpright and rotates its content to the
// viewing edge (UI PLACEMENT locked). Container-scale callers pass their own
// quadrant to uprightRotate directly instead.
@Composable
fun UprightCell(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.uprightRotate(LocalUiUpright.current),
        contentAlignment = Alignment.Center,
    ) { content() }
}
