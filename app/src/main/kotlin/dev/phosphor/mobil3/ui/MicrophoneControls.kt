package dev.phosphor.mobil3.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun MicrophoneMixControls(state: ScopeUiState, p: Palette, actions: SheetActions) {
    StoneToggle("INCLUDE MIC · visualization only", state.includeMicrophone, p) {
        actions.setIncludeMicrophone(!state.includeMicrophone)
    }
    if (state.includeMicrophone) {
        MixRule("PLAYBACK LEVEL", state.playbackMixLevel, p) { actions.setMicrophoneMixLevel(false, it) }
        MixRule("MIC LEVEL", state.microphoneMixLevel, p) { actions.setMicrophoneMixLevel(true, it) }
        Prose("Levels affect only the beam. The combined signal is normalized to prevent clipping. Android keeps control of speaker sound.", p.muted)
    }
}

@Composable
internal fun MicrophoneInputControls(state: ScopeUiState, p: Palette, actions: SheetActions) {
    if (state.microphoneInputs.isEmpty()) Prose("No supported microphone input is available. Connect an input and retry.", p.muted)
    state.microphoneInputs.forEach { input ->
        SheetRow(input.label, p, checked = state.selectedMicrophone == input.id, glyph = SettingsGlyph.Mic) {
            actions.chooseMicrophone(input.id)
        }
    }
    Prose(state.microphoneStatus, p.muted)
    if (state.micBluetoothExplain) {
        Prose("Bluetooth microphone input can reduce playback quality or change the output route. Phosphor releases its temporary route when the microphone stops. Continue?", p.ink)
        SheetRow("continue with Bluetooth microphone", p) { actions.confirmMicrophoneBluetooth(true) }
        SheetRow("not now", p) { actions.confirmMicrophoneBluetooth(false) }
    }
    SheetRow("retry selected microphone", p) { actions.retryMicrophone() }
    if (state.microphoneActive) SheetRow("stop microphone", p) { actions.stopMicrophone() }
}

@Composable
private fun MixRule(label: String, value: Float, p: Palette, change: (Float) -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .settingsRange(label, "${(value * 100).toInt()} percent", SettingsRangeAction(value, 0f, 1f, change))) {
        DragRule(label, value, 0f, 1f, p, onChange = change)
    }
}
