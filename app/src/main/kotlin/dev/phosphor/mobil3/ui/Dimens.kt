package dev.phosphor.mobil3.ui

import androidx.compose.ui.unit.dp
import androidx.compose.runtime.staticCompositionLocalOf

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
    val popoutWidth = 220.dp
    val popoutPullTravel = 184.dp
    val landscapeConsoleMaxWidth = 620.dp
    val landscapeSheetMaxWidth = 600.dp
    val landscapeBandMaxWidth = 760.dp
    val bottomGestureBand = 88.dp
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
