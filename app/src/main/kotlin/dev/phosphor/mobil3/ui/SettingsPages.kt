package dev.phosphor.mobil3.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/** Settings pages. The index shows the whole; each page holds one topic (design/DIRECTION.md). */
enum class SettingsPage(val title: String, val glyph: SettingsGlyph) {
    SOUND("SOUND & VIEW", SettingsGlyph.Signal),
    SCREEN("SCREEN", SettingsGlyph.Display),
    PIP("PiP & BACKGROUND", SettingsGlyph.Hud),
    SOURCES("SOURCES", SettingsGlyph.Remote),
    SETUPS("SETUPS & BACKUP", SettingsGlyph.File),
    ABOUT("ABOUT", SettingsGlyph.About),
    DEVELOPER("DEVELOPER", SettingsGlyph.Performance),
}

/** Plain-word summaries for the index. No raw multipliers beyond one short ×. */
internal object SettingsSummary {
    fun of(page: SettingsPage, s: ScopeUiState): String = when (page) {
        SettingsPage.SOUND -> if (s.autoGain) {
            "auto size · " + GainWords.multiplier(s.autoFrameScale) + if (s.viewLock) " · view locked" else ""
        } else {
            "manual size · " + GainWords.multiplier(s.manualGain) + if (s.viewLock) " · view locked" else ""
        }
        SettingsPage.SCREEN -> listOfNotNull(
            FpsOptions.firstOrNull { it.value == s.fpsValue }?.label?.substringBefore(" ·") ?: "${s.fpsValue}",
            "fps",
            if (s.pinScreenBrightness) "· kept bright" else null,
            if (s.fullscreen) "· fullscreen" else null,
        ).joinToString(" ")
        SettingsPage.PIP -> "auto PiP " + (if (s.pipAutoEnter) "on" else "off") +
            " · floating HUD " + (if (s.floatingHudEnabled) "on" else "off")
        SettingsPage.SOURCES -> "on launch: " + when (s.defaultSource) {
            "mic" -> "microphone"; "capture" -> "everything playing"; else -> "nothing"
        }
        SettingsPage.SETUPS -> "saved setups · export · import"
        SettingsPage.ABOUT -> "manual · version ${dev.phosphor.mobil3.BuildConfig.VERSION_NAME}"
        SettingsPage.DEVELOPER -> "readouts and diagnostics"
    }
}

@Composable
internal fun SettingsIndex(state: ScopeUiState, p: Palette, onOpen: (SettingsPage) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SettingsPage.entries.filter { it != SettingsPage.DEVELOPER || state.developerView }.forEach { page ->
            val summary = SettingsSummary.of(page, state)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp)
                    .clickable(role = Role.Button) { onOpen(page) }
                    .semantics { contentDescription = "${page.title}, $summary" }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.padding(start = 4.dp)) { SettingsGlyphIcon(page.glyph, p, 20.dp) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Mono(page.title, p.ink, Type.dataLg)
                    Spacer(Modifier.height(3.dp))
                    Prose(summary, p.ink2, maxLines = 2)
                }
                Mono("›", p.ink2, Type.dataXl)
            }
            Box(Modifier.fillMaxWidth().height(Dim.hairline).background(p.line))
        }
    }
}

@Composable
internal fun SettingsPageHeader(page: SettingsPage, p: Palette, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onBack)
            .semantics { contentDescription = "back to all settings" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Mono("‹", p.ink2, Type.dataXl)
        Spacer(Modifier.width(10.dp))
        Mono("all settings", p.ink2, Type.data)
    }
    Mono(page.title, p.accent, Type.dataLg,
        Modifier.padding(top = 4.dp, bottom = 8.dp).semantics { heading() })
    Box(Modifier.fillMaxWidth().height(Dim.hairline).background(p.line))
}

/** The one mark for "on" or "chosen": a filled square in a hairline square. Never color alone. */
@Composable
private fun ChosenMark(on: Boolean, enabled: Boolean, p: Palette) {
    Box(
        Modifier.size(18.dp).border(Dim.hairline, if (on && enabled) p.accent else p.lineStrong),
        contentAlignment = Alignment.Center,
    ) {
        if (on) Box(Modifier.size(10.dp).background(if (enabled) p.accent else p.ink2))
    }
}

@Composable
private fun RowText(label: String, hint: String?, p: Palette, modifier: Modifier) {
    Column(modifier) {
        Mono(label, p.ink, Type.dataLg)
        if (hint != null) {
            Spacer(Modifier.height(2.dp))
            Prose(hint, p.ink2, maxLines = 2)
        }
    }
}

@Composable
private fun Divider(p: Palette) = Box(Modifier.fillMaxWidth().height(Dim.hairline).background(p.line))

@Composable
internal fun SettingToggle(label: String, on: Boolean, p: Palette, hint: String? = null,
    enabled: Boolean = true, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .toggleable(on, enabled = enabled, role = Role.Switch) { onToggle(it) }
            .semantics { stateDescription = if (on) "on" else "off" }
            .alpha(if (enabled) 1f else 0.45f)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowText(label, hint, p, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Mono(if (on) "on" else "off", if (on && enabled) p.accent else p.ink2, Type.data)
        Spacer(Modifier.width(10.dp))
        ChosenMark(on, enabled, p)
    }
    Divider(p)
}

@Composable
internal fun <T> SettingChoice(label: String, options: List<Pair<T, String>>, selected: T, p: Palette,
    hint: String? = null, onPick: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        RowText(label, hint, p, Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        // Large text stacks the choices so no word ever breaks mid-way.
        val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
        val stacked = fontScale >= 1.3f || (options.size >= 4 && fontScale > 1.15f)
        val cell: @Composable (Modifier, T, String) -> Unit = { modifier, value, text ->
            val chosen = value == selected
            Row(
                modifier.heightIn(min = 48.dp)
                    .border(Dim.hairline, if (chosen) p.accent else p.line)
                    .selectable(chosen, role = Role.RadioButton) { onPick(value) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (stacked) Arrangement.Start else Arrangement.Center,
            ) {
                if (chosen) {
                    Box(Modifier.size(7.dp).background(p.accent))
                    Spacer(Modifier.width(6.dp))
                }
                Mono(text, if (chosen) p.accent else p.ink2, Type.data, maxLines = if (stacked) 3 else 2)
            }
        }
        if (stacked) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                options.forEach { (value, text) -> cell(Modifier.fillMaxWidth(), value, text) }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                options.forEach { (value, text) -> cell(Modifier.weight(1f), value, text) }
            }
        }
    }
    Divider(p)
}

@Composable
internal fun SettingSlider(label: String, value: Float, min: Float, max: Float, p: Palette,
    format: (Float) -> String, hint: String? = null, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        DragRule(label, value, min, max, p, format, onChange)
        if (hint != null) Prose(hint, p.ink2, maxLines = 2, modifier = Modifier.padding(bottom = 6.dp))
    }
    Divider(p)
}

@Composable
internal fun SettingAction(label: String, p: Palette, hint: String? = null, value: String = "›",
    onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowText(label, hint, p, Modifier.weight(1f))
        Mono(value, p.ink2, if (value == "›") Type.dataXl else Type.data)
    }
    Divider(p)
}

@Composable
internal fun SettingNote(text: String, p: Palette) {
    Prose(text, p.ink2, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
internal fun SettingsPageBody(
    page: SettingsPage,
    state: ScopeUiState,
    p: Palette,
    actions: SheetActions,
    focusValue: Float,
    onFocus: (Float) -> Unit,
) {
    when (page) {
        SettingsPage.SOUND -> {
            val remoteGeometry = state.remote && state.remoteGeometry
            SettingToggle(if (remoteGeometry) "DESKTOP AUTO" else "AUTO SIZE", state.autoGain, p,
                hint = "keeps sound comfortably in view") { actions.setGainAuto(it) }
            if (state.autoGain && !remoteGeometry) {
                SettingSlider("SIZE", RibbonRail.frame(state.autoFrameScale), 0f, 1f, p,
                    { GainWords.multiplier(RibbonRail.frameAt(it)) },
                    hint = "closer or farther around AUTO",
                ) { actions.setAutoFrameScale(RibbonRail.frameAt(it)) }
                if (state.autoFrameScale != AutoFramePreference.DEFAULT) {
                    SettingAction("RESET SIZE", p, value = "×1") { actions.resetAutoFrameScale() }
                }
            } else {
                SettingSlider("SIZE", GainWords.toSlider(if (remoteGeometry) state.gain else state.manualGain), 0f, 1f, p,
                    { GainWords.multiplier(GainWords.fromSlider(it)) },
                ) { actions.setGainAbsolute(GainWords.fromSlider(it)) }
            }
            if (state.autoFrameSaveStatus.contains("failed")) {
                SettingAction("SIZE NOT SAVED · RETRY", p, hint = state.autoFrameSaveStatus, value = "retry") {
                    actions.finishAutoFrameScale()
                }
            }
            SettingToggle("VIEW LOCK", state.viewLock, p, hint = "stops pinch and drag zoom") { actions.setViewLock(it) }
            SettingSlider("FOCUS", focusValue, 0.3f, 3.0f, p, { "%.1f px".format(it) }, onChange = onFocus)
            SettingSlider("BEAM", state.beamEnergy, 1.0f, 30.0f, p, { "×%.0f".format(it) }) { actions.setBeamEnergy(it) }
            SettingSlider("GLOW", state.glow, 0.0f, 0.98f, p, { "%.0f %%".format(it * 100) }) { actions.setGlow(it) }
            SettingToggle("VARY BEAM PER TRACK", state.beamRandomArmed, p) { actions.tapBeamRandom() }
            if (state.beamRandomArmed) {
                RangeDragRule("BEAM RANGE", state.beamRandomLo, state.beamRandomHi, 1.0f, 30.0f, p,
                    armed = true, format = { "×%.0f".format(it) },
                ) { lo, hi -> actions.setBeamRandomRange(lo, hi) }
                Divider(p)
            }
            SettingToggle("VARY GLOW PER TRACK", state.glowRandomArmed, p) { actions.tapGlowRandom() }
            if (state.glowRandomArmed) {
                RangeDragRule("GLOW RANGE", state.glowRandomLo, state.glowRandomHi, 0.0f, 0.98f, p,
                    armed = true, format = { "%.0f %%".format(it * 100) },
                ) { lo, hi -> actions.setGlowRandomRange(lo, hi) }
                Divider(p)
            }
        }
        SettingsPage.SCREEN -> {
            SettingChoice("FRAME RATE", FpsOptions.map { it.value to if (it.value < 0) "max" else it.label.substringBefore(" ·") },
                state.fpsValue, p) { actions.setFps(it) }
            SettingChoice("FPS LINE", listOf(0 to "on", 1 to "with keys", 2 to "off"), state.hudMode, p) {
                actions.setHudMode(it)
            }
            SettingToggle("KEEP SCREEN BRIGHT", state.pinScreenBrightness, p,
                hint = "while Phosphor is open") { actions.setPinScreenBrightness(it) }
            if (state.brightnessPinError.isNotBlank()) SettingNote(state.brightnessPinError, p)
            SettingToggle("HDR", state.hdrRequested, p, hint = "brighter beam on HDR screens") { actions.setHdrRequested(it) }
            SettingToggle("FULLSCREEN", state.fullscreen, p) { actions.setFullscreen(it) }
            SettingToggle("KEYS ALWAYS VISIBLE", state.controlsAlwaysVisible, p) { actions.setControlsAlwaysVisible(it) }
            SettingToggle("DOUBLE TAP TO PLAY", state.doubleTapPlayback, p) { actions.setDoubleTapPlayback(it) }
            SettingChoice("WHEN PAUSED", listOf(false to "hold frame", true to "black"), state.pauseBlack, p) {
                actions.setPauseBlack(it)
            }
            SettingToggle("LOCK SCOPE ROTATION", actions.isScopeRotationLocked(), p,
                enabled = !state.systemRotationLocked) { actions.setScopeRotationLocked(it) }
            SettingToggle("LOCK KEY PLACEMENT", actions.isUiPlacementLocked(), p,
                enabled = !state.systemRotationLocked) { actions.setUiPlacementLocked(it) }
            if (state.systemRotationLocked) SettingNote("Android auto-rotate is off, so rotation stays put.", p)
        }
        SettingsPage.PIP -> {
            SettingToggle("AUTO PiP", state.pipAutoEnter, p, hint = "small scope when you leave the app") {
                actions.setPipAutoEnter(it)
            }
            SettingToggle("FLOATING HUD", state.floatingHudEnabled, p, hint = "small scope over other apps") {
                actions.setFloatingHudEnabled(it)
            }
            if (state.floatingHudEnabled) {
                SettingChoice("HUD BACKGROUND", listOf(false to "solid", true to "clear"),
                    state.floatingHudTransparent, p) { actions.setFloatingHudTransparent(it) }
                SettingAction(if (state.floatingHudActive) "HIDE FLOATING HUD" else "SHOW FLOATING HUD", p,
                    value = if (state.floatingHudActive) "hide" else "show") {
                    if (state.floatingHudActive) actions.hideFloatingHud() else actions.showFloatingHud()
                }
            }
            SettingToggle("KEEP PLAYING IN BACKGROUND", state.lingerBackground, p,
                hint = "after you swipe Phosphor away") { actions.setLingerBackground(it) }
        }
        SettingsPage.SOURCES -> {
            SettingChoice("ON LAUNCH", listOf("none" to "nothing", "mic" to "microphone", "capture" to "everything playing"),
                state.defaultSource, p) { actions.setDefaultSource(it) }
            SettingToggle("ASK FOR PERMISSION AT LAUNCH", state.automaticPermissionPopup, p) {
                actions.setAutomaticPermissionPopup(it)
            }
            SettingChoice("RELAY LATENCY", listOf(0 to "tight", 1 to "balanced", 2 to "safe"), state.latencyMode, p,
                hint = "for desktop audio over Tailscale") { actions.setRemoteLatencyMode(it) }
        }
        SettingsPage.SETUPS -> {
            SettingAction("SAVED SETUPS", p, hint = "recall or save a whole look and tuning") { actions.openInstrument() }
            SettingAction("EXPORT SETTINGS", p, value = "export") { actions.exportSettings() }
            SettingAction("IMPORT SETTINGS", p, value = "import") { actions.importSettings() }
            if (state.settingsTransferStatus.isNotBlank()) SettingNote(state.settingsTransferStatus, p)
        }
        SettingsPage.ABOUT -> {
            SettingNote("Phosphor draws sound as light: a CRT oscilloscope in your pocket. GPL-3.0.", p)
            SettingAction("MANUAL", p, hint = "how it all works") { actions.openManual() }
            // Seven taps on the version open the developer view (engineering readouts).
            var versionTaps by remember { mutableIntStateOf(0) }
            SettingAction("VERSION", p,
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
        SettingsPage.DEVELOPER -> {
            SignalCheckEntry(state, p)
            Divider(p)
            SettingChoice("STATUS BAND", listOf(0 to "on", 1 to "auto", 2 to "off"), state.bandMode, p) {
                state.bandMode = it
            }
            SettingChoice("BEAM RATE", BeamRates.map { it.oversample to it.label.substringBefore(" ·") },
                state.oversample, p) { actions.setOversample(it) }
            SettingToggle("GRID DATA", state.gridData, p) { actions.setGridData(it) }
            SettingAction(if (state.displayPaused) "RETURN DISPLAY TO LIVE" else "PAUSE DISPLAY ONLY", p, value = "") {
                actions.toggleDisplayPause()
            }
            if (state.displayPaused) SettingAction("RESET INSPECTION", p, value = "") { actions.resetInspection() }
            SettingNote("HDR: " + state.hdrStatus, p)
            if (state.floatingHudStatus.isNotBlank()) SettingNote("HUD: " + state.floatingHudStatus, p)
            if (state.hudControlStatus.isNotBlank()) SettingNote(state.hudControlStatus, p)
            Mono("APPEARANCE VALUES", p.ink2, Type.dataSm, Modifier.padding(top = 12.dp, bottom = 4.dp))
            AppearanceEditor(state, actions)
        }
    }
}
