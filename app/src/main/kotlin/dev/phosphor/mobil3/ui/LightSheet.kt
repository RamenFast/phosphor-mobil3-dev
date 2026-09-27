package dev.phosphor.mobil3.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
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
    onDismiss: () -> Unit,
) {
    var editSlot by remember { mutableIntStateOf(-1) }
    DisposableEffect(Unit) { onDispose { state.lightPending = null } }
    val light = state.light
    val p = p.sheetText()
    if (editSlot !in light.slots.indices) editSlot = -1
    CompositionLocalProvider(LocalSettingsControlAccess provides true) {
    SheetHost(p, "LIGHT", reduced, { state.lightPending = null; onDismiss() }, glyph = SettingsGlyph.BeamColor) {
        Column(Modifier.verticalScroll(rememberScrollState(), overscrollEffect = null)) {
            if (state.lightError.isNotEmpty()) SettingNote(state.lightError, p)

            // COLORS: the S25 grid of real colour, first thing.
            GroupHeading("COLORS", p, first = true)
            BeamColors.chunked(3).forEachIndexed { row, swatches ->
                if (row > 0) Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    swatches.forEachIndexed { column, swatch ->
                        val index = row * 3 + column
                        PresetSwatch(swatch, LightChoices.presetWorn(light, index, state.lightTemporary), p,
                            Modifier.weight(1f)) {
                            editSlot = -1
                            onPickPreset(index)
                        }
                    }
                }
            }

            LightChoices.ownerLine(light, state.lightTemporary)?.let { SettingNote(it, p) }

            // SAVED: six squares and `+`. Tap = wear, long-press = edit.
            GroupHeading("SAVED", p)
            val cells = light.slots.size + if (light.slots.size < 6) 1 else 0
            (0 until cells).chunked(4).forEachIndexed { row, indices ->
                if (row > 0) Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    indices.forEach { index ->
                        if (index < light.slots.size) {
                            SavedSwatch(index, light.slots[index], LightChoices.savedWorn(light, index, state.lightTemporary),
                                editSlot == index, p, Modifier.weight(1f),
                                onTap = { editSlot = -1; onLightChange(LightChoices.tapSaved(light, index)) },
                                onEdit = { editSlot = if (editSlot == index) -1 else index },
                                onDelete = { editSlot = -1; onDeleteSlot(index) },
                            )
                        } else {
                            AddSwatch(p, Modifier.weight(1f)) {
                                editSlot = light.slots.size
                                val live = runCatching { dev.phosphor.mobil3.PhosphorNative.beamColorNow() }.getOrNull()
                                onLightChange(LightChoices.addCurrent(light, state.lightTemporary,
                                    live?.let(LightChoices::rgbOf)))
                            }
                        }
                    }
                    repeat(4 - indices.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (editSlot >= 0) {
                val rgb = light.slots[editSlot]
                SavedColorEditor(editSlot, rgb, p,
                    onColor = { onLightChange(light.edit(editSlot, it)) },
                    onDelete = { val i = editSlot; editSlot = -1; onDeleteSlot(i) },
                    onDone = { editSlot = -1 },
                )
            }

            // CYCLE: whenever it has an effect (≥2 saved, or generated colour on).
            if (LightChoices.cycleVisible(light)) {
                GroupHeading("CYCLE", p)
                val words = mapOf(LightChoices.Cycle.OFF to "off", LightChoices.Cycle.TIMER to "timer",
                    LightChoices.Cycle.TRACK to "each track")
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    ChoiceCells(LightChoices.cycleOptions(light).map { it to words.getValue(it) },
                        LightChoices.cycle(light), p) { onLightChange(LightChoices.setCycle(light, it)) }
                }
                RowDivider(p)
                val pending = state.lightPending
                if (pending != null) {
                    SettingNote("fast color changes can trigger seizures", p)
                    KeyRow {
                        SheetKey("keep safe", p, active = true) { state.lightPending = null }
                        SheetKey("allow faster", p) {
                            ackEpilepsy()
                            if (epilepsyAcknowledged()) onLightChange(pending)
                        }
                    }
                    RowDivider(p)
                }
                if (LightChoices.cycle(light) == LightChoices.Cycle.TIMER) {
                    if (light.randomInterval) {
                        SettingRange("every", LightTime.toSlider(light.intervalMin), LightTime.toSlider(light.intervalMax),
                            0f, 1f, p, { LightTime.words(LightTime.fromSlider(it)) }, step = LightTime::stepOnRail) { lo, hi ->
                            val min = LightTime.fromSlider(lo)
                            val max = LightTime.fromSlider(hi).coerceAtLeast(min)
                            onLightChange(light.copy(intervalMin = min, intervalMax = max))
                        }
                    } else {
                        SettingSlider("every", LightTime.toSlider(light.seconds), 0f, 1f, p,
                            { LightTime.words(LightTime.fromSlider(it)) }, step = LightTime::stepOnRail) {
                            onLightChange(light.copy(seconds = LightTime.fromSlider(it)))
                        }
                    }
                    SettingToggle("random timing", light.randomInterval, p) {
                        onLightChange(light.copy(randomInterval = it))
                    }
                }
                if (!light.generatedAuto && LightChoices.cycle(light) != LightChoices.Cycle.OFF) {
                    SettingChoice("order", listOf(false to "saved", true to "shuffled"), light.shuffle, p) {
                        onLightChange(light.copy(shuffle = it))
                    }
                }
            }

            // RANDOM: one roll now, or generated colour that keeps changing.
            GroupHeading("RANDOM", p)
            KeyRow { SheetKey("⚄ roll", p, description = "roll a random color") { editSlot = -1; onRoll() } }
            SettingToggle("auto color", light.generatedAuto, p, hint = "a new color every step") {
                onLightChange(light.copy(generatedAuto = it))
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    }
}

/** A preset block of real colour with its name. Chosen = accent rim + corner mark. */
@Composable
private fun PresetSwatch(swatch: BeamSwatch, worn: Boolean, p: Palette, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .semantics(mergeDescendants = true) {
                contentDescription = swatch.label
                selected = worn
                role = Role.RadioButton
            }
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ColorBlock(swatch.color, worn, p, Modifier.fillMaxWidth().height(44.dp))
        Mono(swatch.label, if (worn) p.accent else p.ink2, Type.eyebrow,
            Modifier.padding(top = 4.dp, bottom = 2.dp), maxLines = 2)
    }
}

@Composable
private fun ColorBlock(color: Color, worn: Boolean, p: Palette, modifier: Modifier) {
    Box(
        modifier
            .border(if (worn) 2.dp else Dim.hairline, if (worn) p.accent else p.line)
            .padding(if (worn) 3.dp else 1.dp)
            .background(color),
        contentAlignment = Alignment.TopEnd,
    ) {
        // The mark carries "chosen" without relying on hue.
        if (worn) Box(Modifier.padding(4.dp).size(10.dp).background(p.plane).padding(2.dp).background(p.accent))
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SavedSwatch(
    index: Int, rgb: LightRgb, worn: Boolean, editing: Boolean, p: Palette, modifier: Modifier,
    onTap: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit,
) {
    val color = Color(rgb.red, rgb.green, rgb.blue)
    val name = "saved ${index + 1} · ${ColorWords.name(rgb.red, rgb.green, rgb.blue)}"
    Box(
        modifier.height(52.dp)
            .semantics {
                contentDescription = name
                selected = worn
                if (editing) stateDescription = "editing"
                customActions = listOf(
                    CustomAccessibilityAction("edit") { onEdit(); true },
                    CustomAccessibilityAction("delete") { onDelete(); true },
                )
            }
            .combinedClickable(onClick = onTap, onLongClick = onEdit, onLongClickLabel = "edit"),
    ) {
        ColorBlock(color, worn || editing, p, Modifier.fillMaxWidth().height(52.dp))
    }
}

@Composable
private fun AddSwatch(p: Palette, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(52.dp).border(Dim.hairline, p.lineStrong)
            .semantics { contentDescription = "save the current color" }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Mono("+", p.ink2, Type.dataXl) }
}

/** HSV square for the finger, three range sliders for keys and TalkBack, one hex readout. */
@Composable
private fun SavedColorEditor(index: Int, rgb: LightRgb, p: Palette,
    onColor: (LightRgb) -> Unit, onDelete: () -> Unit, onDone: () -> Unit) {
    val color = Color(rgb.red, rgb.green, rgb.blue)
    val hsv = remember(rgb) {
        FloatArray(3).also {
            android.graphics.Color.colorToHSV(android.graphics.Color.argb(255,
                (rgb.red * 255).toInt(), (rgb.green * 255).toInt(), (rgb.blue * 255).toInt()), it)
        }
    }
    fun emit(h: Float, s: Float, v: Float) {
        val c = Color(android.graphics.Color.HSVToColor(floatArrayOf(h.coerceIn(0f, 360f), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))))
        onColor(LightRgb(c.red.coerceIn(0f, 1f), c.green.coerceIn(0f, 1f), c.blue.coerceIn(0f, 1f)))
    }
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Mono("saved ${index + 1}", p.ink, Type.label, Modifier.weight(1f))
            Mono("#%02x%02x%02x".format((rgb.red * 255).toInt(), (rgb.green * 255).toInt(), (rgb.blue * 255).toInt()),
                p.ink2, Type.value)
        }
        Spacer(Modifier.height(10.dp))
        HsvSquare(color, p) { onColor(LightRgb(it.red.coerceIn(0f, 1f), it.green.coerceIn(0f, 1f), it.blue.coerceIn(0f, 1f))) }
        SliderRow("hue", hsv[0], 0f, 360f, p, { "%.0f°".format(it) }) { emit(it, hsv[1], hsv[2]) }
        SliderRow("saturation", hsv[1], 0f, 1f, p, { "%.0f %%".format(it * 100) }) { emit(hsv[0], it, hsv[2]) }
        SliderRow("brightness", hsv[2], 0f, 1f, p, { "%.0f %%".format(it * 100) }) { emit(hsv[0], hsv[1], it) }
        KeyRow {
            SheetKey("done", p, active = true, onClick = onDone)
            SheetKey("delete", p, onClick = onDelete)
        }
        RowDivider(p)
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
