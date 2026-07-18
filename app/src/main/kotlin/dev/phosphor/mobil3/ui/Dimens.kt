package dev.phosphor.mobil3.ui

import androidx.compose.ui.unit.dp

// Chrome geometry tokens. Sharp corners everywhere (no radius token on purpose).
object Dim {
    val hairline = 1.dp
    val stoneKey = 52.dp       // the carved controls (three exist app-wide)
    val flatKey = 44.dp        // flat hairline keys
    val sheetPad = 16.dp
    val consolePadH = 14.dp
    val consolePadV = 10.dp
    val rowPad = 14.dp
    val gap = 8.dp
    val gapLg = 12.dp

    const val consoleAlpha = 0.86f   // trace ghosts through the console
    const val sheetAlpha = 0.94f
    const val scrimAlpha = 0.40f
    const val chromeLuminanceCap = 0.60f // burn-in: chrome never exceeds ~60% of panel max
}
