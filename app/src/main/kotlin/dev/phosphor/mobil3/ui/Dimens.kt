package dev.phosphor.mobil3.ui

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
