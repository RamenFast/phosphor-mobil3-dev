package dev.phosphor.mobil3.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

/**
 * Settings: one flat scroll of headed groups (design/REDESIGN.md §2, §4).
 * No index, no pages, no expanders. The row family below is shared by every sheet.
 */
internal enum class SettingsGroup(val title: String, val glyph: SettingsGlyph) {
    SOUND("SOUND & VIEW", SettingsGlyph.Signal),
    SCREEN("SCREEN", SettingsGlyph.Display),
    PIP("PiP & BACKGROUND", SettingsGlyph.Hud),
    SOURCES("SOURCES", SettingsGlyph.Remote),
    SETUPS("SETUPS", SettingsGlyph.File),
    ABOUT("ABOUT", SettingsGlyph.About),
    DEVELOPER("DEVELOPER", SettingsGlyph.Performance),
    ;

    companion object {
        /** Visible groups in their fixed order; DEVELOPER only after the unlock. */
        fun visible(developer: Boolean): List<SettingsGroup> =
            entries.filter { it != DEVELOPER || developer }
    }
}

/** Text roles made readable (4.5:1) on this sheet's surface; hairlines keep their weight. */
internal fun Palette.sheetText(): Palette = copy(
    ink = readableColor(ink), ink2 = readableColor(ink2), muted = readableColor(muted), accent = readableColor(accent),
)

/** Group heading: glyph + tracked CAPS eyebrow. Hierarchy, never a box. */
@Composable
internal fun GroupHeading(title: String, p: Palette, glyph: SettingsGlyph? = null, first: Boolean = false,
    modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(top = if (first) 4.dp else 28.dp, bottom = 8.dp)
            .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        glyph?.let {
            SettingsGlyphIcon(it, p, 14.dp)
            Spacer(Modifier.width(8.dp))
        }
        Mono(title, p.ink2, Type.eyebrow, letterSpacing = 0.08.em)
    }
}

/** The one mark for "on" or "chosen": a filled square in a hairline square. Never color alone. */
@Composable
internal fun ChosenMark(on: Boolean, enabled: Boolean, p: Palette, size: androidx.compose.ui.unit.Dp = 20.dp) {
    Box(
        Modifier.size(size).border(Dim.hairline, if (on && enabled) p.accent else p.lineStrong),
        contentAlignment = Alignment.Center,
    ) {
        if (on) Box(Modifier.size(size / 2).background(if (enabled) p.accent else p.ink2))
    }
}

@Composable
internal fun RowText(label: String, hint: String?, p: Palette, modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = p.ink) {
    Column(modifier) {
        Mono(label, color, Type.label)
        if (hint != null) {
            Spacer(Modifier.height(2.dp))
            Prose(hint, p.ink2, size = Type.hint, maxLines = 2)
        }
    }
}

@Composable
internal fun RowDivider(p: Palette) = Box(Modifier.fillMaxWidth().height(Dim.hairline).background(p.line))

private val RowMin = 56.dp
private val RowPadV = 12.dp

@Composable
internal fun SettingToggle(label: String, on: Boolean, p: Palette, hint: String? = null,
    enabled: Boolean = true, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = RowMin)
            .toggleable(on, enabled = enabled, role = Role.Switch) { onToggle(it) }
            .semantics { stateDescription = if (on) "on" else "off" }
            .alpha(if (enabled) 1f else 0.45f)
            .padding(vertical = RowPadV),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowText(label, hint, p, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Mono(if (on) "on" else "off", if (on && enabled) p.accent else p.ink2, Type.value)
        Spacer(Modifier.width(12.dp))
        ChosenMark(on, enabled, p)
    }
    RowDivider(p)
}

/**
 * Choice cells stack when any label would not fit on one line in its cell.
 * Widths are measured, never guessed from fontScale, so no word ever breaks.
 */
internal fun choicesStack(availablePx: Float, gapPx: Float, cellChromePx: Float, labelWidthsPx: List<Float>): Boolean {
    if (labelWidthsPx.isEmpty()) return false
    val cell = (availablePx - gapPx * (labelWidthsPx.size - 1)) / labelWidthsPx.size - cellChromePx
    return labelWidthsPx.any { it > cell }
}

@Composable
internal fun <T> ChoiceCells(options: List<Pair<T, String>>, selected: T, p: Palette,
    enabled: Boolean = true, onPick: (T) -> Unit) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = TextStyle(fontFamily = MonoFace, fontSize = Type.value)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widths = options.map { (_, text) ->
            measurer.measure(text, style, maxLines = 1, softWrap = false).size.width.toFloat()
        }
        // Chrome: 8dp padding each side + the 8dp chosen mark and its 8dp gap, always reserved.
        val stacked = with(density) {
            choicesStack(maxWidth.toPx(), 8.dp.toPx(), 32.dp.toPx(), widths)
        }
        val cell: @Composable (Modifier, T, String) -> Unit = { modifier, value, text ->
            val chosen = value == selected
            Row(
                modifier.heightIn(min = 48.dp)
                    .border(Dim.hairline, if (chosen) p.accent else p.line)
                    .selectable(chosen, enabled = enabled, role = Role.RadioButton) { onPick(value) }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (stacked) Arrangement.Start else Arrangement.Center,
            ) {
                if (chosen) {
                    Box(Modifier.size(8.dp).background(p.accent))
                    Spacer(Modifier.width(8.dp))
                }
                Mono(text, if (chosen) p.accent else p.ink2, Type.value, maxLines = if (stacked) 3 else 1)
            }
        }
        if (stacked) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (value, text) -> cell(Modifier.fillMaxWidth(), value, text) }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (value, text) -> cell(Modifier.weight(1f), value, text) }
            }
        }
    }
}

@Composable
internal fun <T> SettingChoice(label: String, options: List<Pair<T, String>>, selected: T, p: Palette,
    hint: String? = null, onPick: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = RowPadV)) {
        RowText(label, hint, p, Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        ChoiceCells(options, selected, p, onPick = onPick)
    }
    RowDivider(p)
}

/** Name left, value right on one line; the 48dp lane spans the full width below. */
@Composable
internal fun SliderRow(label: String, value: Float, min: Float, max: Float, p: Palette,
    format: (Float) -> String, reset: (() -> Unit)? = null, step: ((Float, Boolean) -> Float)? = null,
    onChange: (Float) -> Unit) {
    val unit = remember { SliderGeometry(1f, 0f) }
    Column(Modifier.fillMaxWidth().padding(top = RowPadV, bottom = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Mono(label, p.ink, Type.label, Modifier.weight(1f))
            reset?.let {
                Spacer(Modifier.width(12.dp))
                SheetKey("reset", p, description = "reset $label", onClick = it)
            }
            Spacer(Modifier.width(12.dp))
            Mono(format(value), p.ink2, Type.value)
        }
        SliderLane(
            p, unit.fraction(value, min, max),
            Modifier.fillMaxWidth().settingsFocusBorder(p)
                .settingsRange(label, format(value), SettingsRangeAction(value.coerceIn(min, max), min, max, onChange)
                    .also { a -> step?.let { a.stepping(it) } }),
        ) { onChange(unit.valueAt(it, min, max)) }
    }
}

@Composable
internal fun SettingSlider(label: String, value: Float, min: Float, max: Float, p: Palette,
    format: (Float) -> String, reset: (() -> Unit)? = null, step: ((Float, Boolean) -> Float)? = null,
    onChange: (Float) -> Unit) {
    SliderRow(label, value, min, max, p, format, reset, step, onChange)
    RowDivider(p)
}

/** Two thumbs: the lane scrubs the nearest one. Each number is its own TalkBack range. */
@Composable
internal fun SettingRange(label: String, lo: Float, hi: Float, min: Float, max: Float, p: Palette,
    format: (Float) -> String, step: ((Float, Boolean) -> Float)? = null, onChange: (Float, Float) -> Unit) {
    val grab = remember { mutableIntStateOf(0) }
    val unit = remember { SliderGeometry(1f, 0f) }
    Column(Modifier.fillMaxWidth().padding(top = RowPadV, bottom = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Mono(label, p.ink, Type.label, Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Mono(format(lo), p.ink2, Type.value, Modifier.settingsFocusBorder(p)
                .settingsRange("$label lowest", format(lo), SettingsRangeAction(lo, min, hi) { onChange(it, hi) }.also { a -> step?.let { a.stepping(it) } }))
            Mono(" – ", p.ink2, Type.value)
            Mono(format(hi), p.ink2, Type.value, Modifier.settingsFocusBorder(p)
                .settingsRange("$label highest", format(hi), SettingsRangeAction(hi, lo, max) { onChange(lo, it) }.also { a -> step?.let { a.stepping(it) } }))
        }
        SliderLane(
            p, unit.fraction(lo, min, max), Modifier.fillMaxWidth(),
            highFraction = unit.fraction(hi, min, max),
            onStart = { grab.intValue = unit.nearestThumb(it, lo, hi, min, max) },
        ) {
            val moved = unit.moveThumb(grab.intValue, it, lo, hi, min, max)
            onChange(moved.first, moved.second)
        }
    }
    RowDivider(p)
}

@Composable
internal fun SettingAction(label: String, p: Palette, hint: String? = null, value: String = "›",
    enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = RowMin)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.45f)
            .padding(vertical = RowPadV),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowText(label, hint, p, Modifier.weight(1f))
        if (value.isNotEmpty()) {
            Spacer(Modifier.width(12.dp))
            Mono(value, p.ink2, if (value == "›") Type.dataXl else Type.value)
        }
    }
    RowDivider(p)
}

/** A row with a label and a pair of keys (e.g. "settings file  [export] [import]"). */
@Composable
internal fun SettingKeys(label: String, p: Palette, keys: List<Triple<String, Boolean, () -> Unit>>) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = RowMin).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Mono(label, p.ink, Type.label, Modifier.weight(1f))
        keys.forEach { (text, enabled, action) ->
            Spacer(Modifier.width(8.dp))
            SheetKey(text, p, enabled = enabled, description = "$text $label", onClick = action)
        }
    }
    RowDivider(p)
}

/** One quiet status line. Only for live state (an error, a result), never for help. */
@Composable
internal fun SettingNote(text: String, p: Palette) {
    Prose(text, p.ink2, size = Type.hint, modifier = Modifier.padding(vertical = 10.dp))
}

@Composable
internal fun SettingsBody(
    state: ScopeUiState,
    p: Palette,
    actions: SheetActions,
    setups: InstrumentPresetActions,
    focusValue: Float,
    onFocus: (Float) -> Unit,
) {
    val setupsRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(state.settingsFocus) {
        if (state.settingsFocus == SettingsGroup.SETUPS.name) {
            setupsRequester.bringIntoView()
            state.settingsFocus = null
        }
    }
    SettingsGroup.visible(state.developerView).forEachIndexed { index, group ->
        GroupHeading(group.title, p, group.glyph, first = index == 0,
            modifier = if (group == SettingsGroup.SETUPS) Modifier.bringIntoViewRequester(setupsRequester) else Modifier)
        when (group) {
            SettingsGroup.SOUND -> SoundGroup(state, p, actions, focusValue, onFocus)
            SettingsGroup.SCREEN -> ScreenGroup(state, p, actions)
            SettingsGroup.PIP -> PipGroup(state, p, actions)
            SettingsGroup.SOURCES -> SourcesGroup(state, p, actions)
            SettingsGroup.SETUPS -> SetupsGroup(state, p, actions, setups)
            SettingsGroup.ABOUT -> AboutGroup(state, p, actions)
            SettingsGroup.DEVELOPER -> DeveloperGroup(state, p, actions)
        }
    }
}

@Composable
private fun SoundGroup(state: ScopeUiState, p: Palette, actions: SheetActions, focusValue: Float, onFocus: (Float) -> Unit) {
    val remoteGeometry = state.remote && state.remoteGeometry
    SettingToggle(if (remoteGeometry) "desktop auto size" else "auto size", state.autoGain, p,
        hint = "keeps sound in view") { actions.setGainAuto(it) }
    if (state.autoGain && !remoteGeometry) {
        SettingSlider("size", RibbonRail.frame(state.autoFrameScale), 0f, 1f, p,
            { GainWords.multiplier(RibbonRail.frameAt(it)) },
            reset = if (state.autoFrameScale != AutoFramePreference.DEFAULT) {
                { actions.resetAutoFrameScale() }
            } else null,
        ) { actions.setAutoFrameScale(RibbonRail.frameAt(it)) }
    } else {
        SettingSlider("size", GainWords.toSlider(if (remoteGeometry) state.gain else state.manualGain), 0f, 1f, p,
            { GainWords.multiplier(GainWords.fromSlider(it)) },
        ) { actions.setGainAbsolute(GainWords.fromSlider(it)) }
    }
    if (state.autoFrameSaveStatus.contains("failed")) {
        SettingAction("size not saved", p, value = "retry") { actions.finishAutoFrameScale() }
    }
    SettingToggle("view lock", state.viewLock, p, hint = "stops pinch and drag zoom") { actions.setViewLock(it) }
    SettingSlider("focus", focusValue, 0.3f, 3.0f, p, { "%.1f px".format(it) }, onChange = onFocus)
    SettingSlider("beam", state.beamEnergy, 1.0f, 30.0f, p, { "×%.0f".format(it) }) { actions.setBeamEnergy(it) }
    SettingSlider("glow", state.glow, 0.0f, 0.98f, p, { "%.0f %%".format(it * 100) }) { actions.setGlow(it) }
    SettingToggle("vary beam per track", state.beamRandomArmed, p) { actions.tapBeamRandom() }
    if (state.beamRandomArmed) {
        SettingRange("beam range", state.beamRandomLo, state.beamRandomHi, 1.0f, 30.0f, p,
            { "×%.0f".format(it) }) { lo, hi -> actions.setBeamRandomRange(lo, hi) }
    }
    SettingToggle("vary glow per track", state.glowRandomArmed, p) { actions.tapGlowRandom() }
    if (state.glowRandomArmed) {
        SettingRange("glow range", state.glowRandomLo, state.glowRandomHi, 0.0f, 0.98f, p,
            { "%.0f %%".format(it * 100) }) { lo, hi -> actions.setGlowRandomRange(lo, hi) }
    }
}

@Composable
private fun ScreenGroup(state: ScopeUiState, p: Palette, actions: SheetActions) {
    SettingChoice("frame rate", FpsOptions.map { it.value to if (it.value < 0) "max" else it.label.substringBefore(" ·") },
        state.fpsValue, p) { actions.setFps(it) }
    SettingChoice("fps line", listOf(0 to "on", 1 to "with keys", 2 to "off"), state.hudMode, p) {
        actions.setHudMode(it)
    }
    SettingToggle("keep screen bright", state.pinScreenBrightness, p,
        hint = state.brightnessPinError.ifBlank { null }) { actions.setPinScreenBrightness(it) }
    SettingToggle("HDR", state.hdrRequested, p, hint = "brighter beam on HDR screens") { actions.setHdrRequested(it) }
    SettingToggle("fullscreen", state.fullscreen, p) { actions.setFullscreen(it) }
    SettingToggle("keys always visible", state.controlsAlwaysVisible, p) { actions.setControlsAlwaysVisible(it) }
    SettingToggle("double tap to play", state.doubleTapPlayback, p) { actions.setDoubleTapPlayback(it) }
    SettingChoice("when paused", listOf(false to "hold frame", true to "black"), state.pauseBlack, p) {
        actions.setPauseBlack(it)
    }
    val rotationHint = if (state.systemRotationLocked) "Android auto-rotate is off" else null
    SettingToggle("lock scope rotation", actions.isScopeRotationLocked(), p, hint = rotationHint,
        enabled = !state.systemRotationLocked) { actions.setScopeRotationLocked(it) }
    SettingToggle("lock key placement", actions.isUiPlacementLocked(), p, hint = rotationHint,
        enabled = !state.systemRotationLocked) { actions.setUiPlacementLocked(it) }
}

@Composable
private fun PipGroup(state: ScopeUiState, p: Palette, actions: SheetActions) {
    SettingToggle("auto PiP", state.pipAutoEnter, p, hint = "small scope when you leave") {
        actions.setPipAutoEnter(it)
    }
    SettingToggle("floating HUD", state.floatingHudEnabled, p, hint = "small scope over other apps") {
        actions.setFloatingHudEnabled(it)
    }
    if (state.floatingHudEnabled) {
        SettingChoice("HUD background", listOf(false to "solid", true to "clear"),
            state.floatingHudTransparent, p) { actions.setFloatingHudTransparent(it) }
        SettingAction("floating HUD now", p, value = if (state.floatingHudActive) "hide" else "show") {
            if (state.floatingHudActive) actions.hideFloatingHud() else actions.showFloatingHud()
        }
    }
    SettingToggle("keep playing in background", state.lingerBackground, p,
        hint = "after you swipe Phosphor away") { actions.setLingerBackground(it) }
}

@Composable
private fun SourcesGroup(state: ScopeUiState, p: Palette, actions: SheetActions) {
    SettingChoice("on launch", listOf("none" to "nothing", "mic" to "microphone", "capture" to "everything playing"),
        state.defaultSource, p) { actions.setDefaultSource(it) }
    SettingToggle("ask for permission at launch", state.automaticPermissionPopup, p) {
        actions.setAutomaticPermissionPopup(it)
    }
    val hasRelay = remember(state.remote, state.sourceLabel) { actions.remoteHosts().isNotEmpty() }
    if (hasRelay) {
        SettingChoice("relay latency", listOf(0 to "tight", 1 to "balanced", 2 to "safe"), state.latencyMode, p) {
            actions.setRemoteLatencyMode(it)
        }
    }
}

@Composable
private fun AboutGroup(state: ScopeUiState, p: Palette, actions: SheetActions) {
    SettingAction("manual", p) { actions.openManual() }
    // Seven taps on the version open the developer view (engineering readouts).
    var versionTaps by remember { mutableIntStateOf(0) }
    SettingAction("version", p,
        value = dev.phosphor.mobil3.BuildConfig.VERSION_NAME + if (state.developerView) " · dev" else "") {
        versionTaps += 1
        if (versionTaps >= 7) {
            versionTaps = 0
            actions.setDeveloperView(!state.developerView)
        }
    }
    if (LocalRoomStyle.current.designators && state.calDate.isNotBlank()) {
        SettingNote("CAL · ${state.calDate}   S/N 003" + if (state.bestiaryFound) "   ☂ holding" else "", p)
    }
}

@Composable
private fun DeveloperGroup(state: ScopeUiState, p: Palette, actions: SheetActions) {
    SignalCheckEntry(state, p)
    RowDivider(p)
    SettingChoice("status band", listOf(0 to "on", 1 to "auto", 2 to "off"), state.bandMode, p) {
        state.bandMode = it
    }
    SettingChoice("beam rate", BeamRates.map { it.oversample to it.label.substringBefore(" ·") },
        state.oversample, p) { actions.setOversample(it) }
    SettingToggle("grid data", state.gridData, p) { actions.setGridData(it) }
    SettingAction(if (state.displayPaused) "return display to live" else "pause display only", p, value = "") {
        actions.toggleDisplayPause()
    }
    if (state.displayPaused) SettingAction("reset inspection", p, value = "") { actions.resetInspection() }
    SettingNote("HDR: " + state.hdrStatus, p)
    if (state.floatingHudStatus.isNotBlank()) SettingNote("HUD: " + state.floatingHudStatus, p)
    if (state.hudControlStatus.isNotBlank()) SettingNote(state.hudControlStatus, p)
    GroupHeading("APPEARANCE VALUES", p)
    AppearanceEditor(state, actions)
}
