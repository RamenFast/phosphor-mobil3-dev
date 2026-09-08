package dev.phosphor.mobil3.ui

import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.semantics.*
import androidx.compose.ui.input.key.*

internal fun applyGuardedCycle(
    state: ScopeUiState,
    seconds: Float,
    perTrack: Boolean,
    acknowledged: Boolean,
    publish: (Float, Boolean) -> Unit,
    warn: (Float) -> Unit,
) {
    val result = LightCycleGuard.evaluate(state.light.copy(seconds = seconds, perTrack = perTrack), acknowledged)
    state.light = result.safe
    publish(result.safe.seconds, result.safe.perTrack)
    result.pending?.let { warn(it.seconds) }
}

@Composable
fun LightSheetV2(
    state: ScopeUiState, p: Palette, reduced: Boolean,
    onPickPreset: (Int) -> Unit,
    onLightChange: (LightSettings) -> Unit,
    onDeleteSlot: (Int) -> Unit,
    onRoll: () -> Unit,
    epilepsyAcknowledged: () -> Boolean,
    ackEpilepsy: () -> Unit,
    onRecallInstrument: () -> Unit,
    onDismiss: () -> Unit,
) {
    var editSlot by remember { mutableIntStateOf(-1) }
    DisposableEffect(Unit) { onDispose { state.lightPending = null } }
    val light = state.light
    SheetHost(p, "LIGHT", reduced, { state.lightPending = null; onDismiss() }, glyph = SettingsGlyph.BeamColor) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            LightKey("RECALL INSTRUMENT", p, action = onRecallInstrument)
            Prose("Recall a complete saved setup, not only its beam color. Opening does not apply it.", p.muted)
            val pending = state.lightPending
            if (pending != null) {
                Prose("Below one second, full-screen color changes can trigger photosensitive seizures. " +
                    "Safe timing is already active. Only allow faster timing if it is safe for everyone watching.", p.ink)
                LightKey("KEEP SAFE", p) { state.lightPending = null }
                LightKey("I understand: allow faster", p) {
                    ackEpilepsy()
                    if (epilepsyAcknowledged()) onLightChange(pending)
                }
            }
            if (state.lightError.isNotEmpty()) Prose(state.lightError, p.ink)
            val owner = when {
                state.lightTemporary -> "Temporary manual roll. The next light edit restores your setup."
                light.generatedAuto -> "Automatic generated color owns the beam. Saved colors remain stored."
                light.selectedMask != 0 -> "Saved slots: ${light.selected.joinToString { (it + 1).toString() }}"
                else -> "Preset: ${BeamColors[light.preset].label}"
            }
            Prose(owner, p.ink)
            SectionHeading("PRESETS", p)
            BeamColors.forEachIndexed { index, swatch ->
                LightKey(swatch.label, p, !light.generatedAuto && light.selectedMask == 0 && light.preset == index) {
                    editSlot = -1
                    onPickPreset(index)
                }
            }
            SectionHeading("SAVED COLORS ${light.slots.size}/6", p)
            light.slots.forEachIndexed { index, rgb ->
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(24.dp).background(Color(rgb.red, rgb.green, rgb.blue)).border(Dim.hairline, p.line))
                        Mono("  SLOT ${index + 1}", p.ink, Type.data)
                    }
                    LightKey("Select slot ${index + 1}", p, index in light.selected, toggle = true) {
                        onLightChange(light.toggle(index))
                    }
                    LightKey("Edit slot ${index + 1}", p, editSlot == index) {
                        editSlot = if (editSlot == index) -1 else index
                    }
                    LightKey("Delete slot ${index + 1}", p) {
                        editSlot = -1
                        onDeleteSlot(index)
                    }
                    if (editSlot == index) {
                        HsvSquare(Color(rgb.red, rgb.green, rgb.blue), p) {
                            onLightChange(light.edit(index, LightRgb(it.red, it.green, it.blue)))
                        }
                        LightRule("Slot ${index + 1} red", rgb.red, 0f, 1f, p) { onLightChange(light.edit(index, rgb.copy(red = it))) }
                        LightRule("Slot ${index + 1} green", rgb.green, 0f, 1f, p) { onLightChange(light.edit(index, rgb.copy(green = it))) }
                        LightRule("Slot ${index + 1} blue", rgb.blue, 0f, 1f, p) { onLightChange(light.edit(index, rgb.copy(blue = it))) }
                    }
                }
            }
            if (light.slots.size < 6) LightKey("ADD CURRENT PRESET COLOR", p) {
                val color = BeamColors[light.preset].color
                editSlot = light.slots.size
                onLightChange(light.add(LightRgb(color.red, color.green, color.blue)))
            }
            if (light.slots.isEmpty()) Prose("No saved colors. Add stores the current preset and selects it.", p.muted)
            SectionHeading("RANDOM COLOR", p)
            LightKey("ROLL NOW", p) { onRoll() }
            LightKey("Automatic generated color", p, light.generatedAuto, toggle = true) {
                onLightChange(light.copy(generatedAuto = !light.generatedAuto))
            }
            if (!light.generatedAuto) {
                LightKey("Le random order", p, light.shuffle, toggle = true) { onLightChange(light.copy(shuffle = !light.shuffle)) }
                Prose("Each selected slot appears once per shuffle bag. Identical saved colors can look unchanged.", p.muted)
            } else Prose("Saved order is inactive and retained while automatic color owns the beam.", p.muted)
            SectionHeading("CYCLE", p)
            LightKey("TIMER", p, !light.perTrack) { onLightChange(light.copy(perTrack = false)) }
            LightKey("TRACK", p, light.perTrack) { onLightChange(light.copy(perTrack = true)) }
            if (light.perTrack) Prose("TRACK holds one color and steps once when the track changes. Interval values are retained.", p.muted)
            else {
                LightKey("Random interval", p, light.randomInterval, toggle = true) {
                    onLightChange(light.copy(randomInterval = !light.randomInterval))
                }
                if (light.randomInterval) {
                    LightRule("Minimum seconds", light.intervalMin, 0.1f, 60f, p) {
                        onLightChange(light.copy(intervalMin = it, intervalMax = light.intervalMax.coerceAtLeast(it)))
                    }
                    LightRule("Maximum seconds", light.intervalMax, 0.1f, 60f, p) {
                        onLightChange(light.copy(intervalMax = it, intervalMin = light.intervalMin.coerceAtMost(it)))
                    }
                } else LightRule("LEG seconds", light.seconds, 0.1f, 60f, p) { onLightChange(light.copy(seconds = it)) }
            }
            if (state.displayPaused) Prose("HOLD keeps its pixels. These edits affect the next live image.", p.muted)
        }
    }
}

@Composable
private fun LightKey(label: String, p: Palette, active: Boolean = false, toggle: Boolean = false, action: () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .border(if (active) 2.dp else Dim.hairline, if (active) p.accent else p.line)
        .semantics { if (toggle) stateDescription = if (active) "On" else "Off" else selected = active }
        .clickable(role = if (toggle) Role.Checkbox else Role.Button, onClick = action)
        .padding(12.dp), contentAlignment = Alignment.CenterStart) {
        Mono(label, if (active) p.accent else p.ink, Type.data)
    }
}

@Composable
private fun LightRule(label: String, value: Float, min: Float, max: Float, p: Palette, change: (Float) -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics(mergeDescendants = true) {
        contentDescription = label
        progressBarRangeInfo = ProgressBarRangeInfo(value, min..max)
        setProgress { change(it.coerceIn(min, max)); true }
    }.onKeyEvent {
        if (it.type != KeyEventType.KeyDown) false else when (it.key) {
            Key.DirectionLeft, Key.DirectionDown -> { change((value - (max - min) / 100f).coerceIn(min, max)); true }
            Key.DirectionRight, Key.DirectionUp -> { change((value + (max - min) / 100f).coerceIn(min, max)); true }
            else -> false
        }
    }.focusable()) { DragRule(label, value, min, max, p, onChange = change) }
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
    var hue by remember(current) { mutableFloatStateOf(hsv[0]) }
    var sat by remember(current) { mutableFloatStateOf(hsv[1]) }
    var vall by remember(current) { mutableFloatStateOf(hsv[2]) }
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
                .height(48.dp)
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
