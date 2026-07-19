package dev.phosphor.mobil3.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

// ── LIGHT v2 (spec §2.5): presets + the custom editor (1–3 slots, gradient ring
//    preview, advance mode, seconds rule) + the photosensitivity guard. ──
@Composable
fun LightSheetV2(
    state: ScopeUiState,
    p: Palette,
    reduced: Boolean,
    onPickPreset: (Int) -> Unit,
    onCustomChange: (List<Color>, Int) -> Unit,
    onCycleChange: (Float, Boolean) -> Unit,
    epilepsyAcknowledged: () -> Boolean,
    ackEpilepsy: () -> Unit,
    onDismiss: () -> Unit,
) {
    var guardCard by remember { mutableStateOf(false) }
    var pendingSeconds by remember { mutableStateOf(1.0f) }
    var editSlot by remember { mutableIntStateOf(-1) }

    SheetHost(p, "LIGHT", reduced, onDismiss, glyph = SettingsGlyph.BeamColor) {
        if (guardCard) {
            // Full-attention card: serious but warm, reading face, deliberately no haptic.
            Prose(
                "Below one second, the whole screen changes color rapidly. For some " +
                    "people with photosensitive epilepsy this can trigger seizures. " +
                    "Only continue if that's safe for you and anyone watching.",
                p.ink, modifier = Modifier.padding(bottom = Dim.gapLg),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Dim.gap)) {
                FlatKey("KEEP 1 s", p, active = true) {
                    guardCard = false
                    state.cycleSeconds = 1.0f
                    onCycleChange(1.0f, state.cyclePerTrack)
                }
                FlatKey("I understand — allow faster", p) {
                    ackEpilepsy()
                    guardCard = false
                    state.cycleSeconds = pendingSeconds
                    onCycleChange(pendingSeconds, state.cyclePerTrack)
                }
            }
            return@SheetHost
        }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SectionHeading("PRESETS", p, Modifier.padding(top = 0.dp))
            LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.height(200.dp)) {
                itemsIndexed(BeamColors) { i, sw ->
                    SwatchCell(
                        sw, active = state.customCount == 0 && i == state.beamIndex, p = p,
                    ) {
                        state.customCount = 0
                        editSlot = -1
                        onCustomChange(state.customColors, 0)
                        onPickPreset(i)
                    }
                }
            }

            // ── CUSTOM: the slot-count chips are the way in — tap 1/2/3 to leave the
            //    presets and mix your own. Slots 2/3 keep their colors while inactive
            //    (desktop law), so shrinking then re-growing the count remembers. ──
            SectionHeading("CUSTOM", p)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (n in 1..3) {
                    Box(Modifier.weight(1f)) {
                        ChipCell(
                            n.toString(),
                            active = state.customCount == n, p = p, small = true,
                        ) {
                            if (editSlot >= n) editSlot = -1
                            state.customCount = n
                            onCustomChange(state.customColors, n)
                        }
                    }
                }
            }
            if (state.customCount == 0) {
                Prose(
                    "tap a count to mix your own — 1 is a solid custom color, 2 or 3 " +
                        "builds a cycle",
                    p.muted, modifier = Modifier.padding(top = 6.dp),
                )
            } else {
                // Per-slot pickers: a tap opens the same HSV square slot 1 has always used.
                Spacer(Modifier.height(Dim.gap))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dim.gap),
                ) {
                    for (i in 0 until state.customCount) {
                        val c = state.customColors[i]
                        Box(
                            Modifier
                                .size(44.dp)
                                .background(c)
                                .border(
                                    if (editSlot == i) 2.dp else Dim.hairline,
                                    if (editSlot == i) p.accent else p.line,
                                )
                                .clickable { editSlot = if (editSlot == i) -1 else i },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Mono(
                        if (editSlot >= 0) "slot ${editSlot + 1}" else "tap a slot",
                        p.muted, Type.dataXs,
                    )
                }
                if (editSlot in 0 until state.customCount) {
                    Spacer(Modifier.height(Dim.gapLg))
                    HsvSquare(state.customColors[editSlot], p) { picked ->
                        state.customColors = state.customColors.toMutableList().also {
                            it[editSlot] = picked
                        }
                        onCustomChange(state.customColors, state.customCount)
                    }
                }
            }

            // ── CYCLE: only earns its section once two colors give the beam somewhere
            //    to walk. The ring preview loops home; LEG is one color→color leg;
            //    TIMER/TRACK choose the clock. ──
            if (state.customCount >= 2) {
                SectionHeading("CYCLE", p)
                // The gradient strip — the ring the cycle walks, looping home.
                val cols = state.customColors.take(state.customCount) +
                    state.customColors.first()
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .background(Brush.horizontalGradient(cols))
                        .border(Dim.hairline, p.line),
                )
                Spacer(Modifier.height(Dim.gapLg))
                DragRule(
                    "LEG", state.cycleSeconds, 0.1f, 60f, p, { "%.1f s".format(it) },
                ) { v ->
                    // The guard: in TIMER a sub-1 s leg strobes the whole screen, so it
                    // stops at 1 s until knowingly accepted. TRACK is exempt — one
                    // crossfade per song is not a strobe.
                    if (!state.cyclePerTrack && v < 1.0f && !epilepsyAcknowledged()) {
                        pendingSeconds = v
                        state.cycleSeconds = 1.0f
                        guardCard = true
                    } else {
                        state.cycleSeconds = v
                        onCycleChange(v, state.cyclePerTrack)
                    }
                }
                Spacer(Modifier.height(Dim.gap))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.weight(1f)) {
                        ChipCell("TIMER", active = !state.cyclePerTrack, p = p, small = true) {
                            state.cyclePerTrack = false
                            onCycleChange(state.cycleSeconds, false)
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        ChipCell("TRACK", active = state.cyclePerTrack, p = p, small = true) {
                            state.cyclePerTrack = true
                            onCycleChange(state.cycleSeconds, true)
                        }
                    }
                }
                Prose(
                    "TIMER walks the ring on its own clock — one LEG per color. TRACK " +
                        "holds a color and steps to the next when the song changes.",
                    p.muted, modifier = Modifier.padding(top = 6.dp),
                )
            }
            Spacer(Modifier.height(Dim.gap))
            Prose(
                "the beam wears it immediately — browse freely",
                p.muted, modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

// A compact HSV picker: hue rule beneath an SV square. Sharp, hairline, no chrome.
@Composable
private fun HsvSquare(current: Color, p: Palette, onPick: (Color) -> Unit) {
    val hsv = remember(current) {
        FloatArray(3).also {
            android.graphics.Color.colorToHSV(
                android.graphics.Color.argb(
                    255,
                    (current.red * 255).toInt(),
                    (current.green * 255).toInt(),
                    (current.blue * 255).toInt(),
                ),
                it,
            )
        }
    }
    var hue by remember { mutableStateOf(hsv[0]) }
    var sat by remember { mutableStateOf(hsv[1]) }
    var vall by remember { mutableStateOf(hsv[2]) }
    fun emit() = onPick(
        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, vall)))
    )
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .border(Dim.hairline, p.line)
                .drawBehind {
                    drawRect(
                        Brush.horizontalGradient(
                            listOf(Color.White, Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))),
                        )
                    )
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                    val x = sat * size.width
                    val y = (1f - vall) * size.height
                    drawCircle(Color.White, 6.dp.toPx(), Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
                }
                .pointerInput(hue) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        sat = (change.position.x / size.width).coerceIn(0f, 1f)
                        vall = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                        emit()
                    }
                }
                .pointerInput(hue) {
                    detectTapGestures { pos ->
                        sat = (pos.x / size.width).coerceIn(0f, 1f)
                        vall = 1f - (pos.y / size.height).coerceIn(0f, 1f)
                        emit()
                    }
                },
        )
        Spacer(Modifier.height(Dim.gap))
        Box(
            Modifier
                .fillMaxWidth()
                .height(18.dp)
                .border(Dim.hairline, p.line)
                .drawBehind {
                    drawRect(
                        Brush.horizontalGradient(
                            (0..12).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 30f, 1f, 1f))) },
                        )
                    )
                    val x = (hue / 360f) * size.width
                    drawRect(
                        Color.White,
                        topLeft = Offset(x - 2.dp.toPx(), 0f),
                        size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        hue = (change.position.x / size.width).coerceIn(0f, 1f) * 360f
                        emit()
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        hue = (pos.x / size.width).coerceIn(0f, 1f) * 360f
                        emit()
                    }
                },
        )
    }
}
