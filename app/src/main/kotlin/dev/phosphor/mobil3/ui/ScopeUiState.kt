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

    var sourceLabel by mutableStateOf("no source")
    var playing by mutableStateOf(false)
    var trackTitle by mutableStateOf<String?>(null)
    var trackArtist by mutableStateOf<String?>(null)

    val modeLabel: String get() = ModeLabels.getOrElse(modeIndex) { "?" }
    val modeTag: String get() = ModeTags.getOrElse(modeIndex) { "?" }
}
