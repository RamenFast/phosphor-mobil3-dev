package dev.phosphor.mobil3.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// One place both the activity and the chrome read/write. No god-object: these are the few
// genuinely-shared display facts, each with a single writer path.
class ScopeUiState {
    var room by mutableStateOf(BlossomDark)
    var modeIndex by mutableStateOf(0)
    var beamIndex by mutableStateOf(0)
    var fpsValue by mutableStateOf(0) // engine convention: 0 = panel vsync
    var oversample by mutableStateOf(1) // beam integration multiplier

    var sourceLabel by mutableStateOf("no source")
    var remote by mutableStateOf(false) // remote (Tailscale) source active
    var live by mutableStateOf(false) // capture or mic actively feeding the beam
    var playing by mutableStateOf(false)
    var trackTitle by mutableStateOf<String?>(null)
    var trackArtist by mutableStateOf<String?>(null)

    // Seek rule (console): live position from the controller when the deck is seekable.
    var seekable by mutableStateOf(false)
    var positionMs by mutableStateOf(0L)
    var durationMs by mutableStateOf(0L)

    // The instrument readouts.
    var gain by mutableStateOf(1.0f)
    var autoGain by mutableStateOf(false)
    var noSignal by mutableStateOf(false) // resting beam is up on an active source

    // Custom light (LIGHT sheet): 0 slots = presets active.
    var customColors by mutableStateOf(
        listOf(
            androidx.compose.ui.graphics.Color(0xFF6BFF8C),
            androidx.compose.ui.graphics.Color(0xFF35BFFF),
            androidx.compose.ui.graphics.Color(0xFFFF4CE1),
        )
    )
    var customCount by mutableStateOf(0)
    var cycleSeconds by mutableStateOf(3.0f)
    var cyclePerTrack by mutableStateOf(false)

    val modeLabel: String get() = ModeLabels.getOrElse(modeIndex) { "?" }
    val modeTag: String get() = ModeTags.getOrElse(modeIndex) { "?" }
    val mode3d: Boolean get() = modeIndex == 4 || modeIndex == 5 // attractor, helix
}
