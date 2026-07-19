package dev.phosphor.mobil3.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// One place both the activity and the chrome read/write. No god-object: these are the few
// genuinely-shared display facts, each with a single writer path.
class ScopeUiState {
    var room by mutableStateOf(BlossomDark)
    var modeIndex by mutableStateOf(0)
    var randomModeArmed by mutableStateOf(false)
    var randomBanModes by mutableStateOf(setOf<Int>()) // faces ⚄ must never land on
    var beamIndex by mutableStateOf(0)
    var fpsValue by mutableStateOf(0) // engine convention: 0 = panel vsync
    var oversample by mutableStateOf(1) // beam integration multiplier

    var sourceLabel by mutableStateOf("no source")
    var remote by mutableStateOf(false) // remote (Tailscale) source active
    var remoteAudio by mutableStateOf(true) // bridge stream toggles (H frame)
    var remoteGeometry by mutableStateOf(false)
    var live by mutableStateOf(false) // capture or mic actively feeding the beam
    var captureMetadataAccess by mutableStateOf(false)
    var playing by mutableStateOf(false)
    var trackTitle by mutableStateOf<String?>(null)
    var trackArtist by mutableStateOf<String?>(null)
    var artwork by mutableStateOf<ByteArray?>(null)
    var queueTitles by mutableStateOf<List<String>>(emptyList())
    var queueIndex by mutableStateOf(0)

    // Seek rule (console): live position from the controller when the deck is seekable.
    var seekable by mutableStateOf(false)
    var positionMs by mutableStateOf(0L)
    var durationMs by mutableStateOf(0L)

    // The instrument readouts (desktop-parity ranges: gain 0.1–6, beam 1–30, glow 0–0.98).
    var gain by mutableStateOf(1.0f)
    var beamEnergy by mutableStateOf(8.0f)
    var glow by mutableStateOf(0.7f)
    var grid by mutableStateOf(true)
    // BEAM/GLOW dice: armed re-rolls inside the kept sub-range on every track change.
    var beamRandomArmed by mutableStateOf(false)
    var beamRandomLo by mutableStateOf(6.0f)   // sub-range of 1..30
    var beamRandomHi by mutableStateOf(20.0f)
    var glowRandomArmed by mutableStateOf(false)
    var glowRandomLo by mutableStateOf(0.30f)  // sub-range of 0..0.98
    var glowRandomHi by mutableStateOf(0.90f)
    // Geometry FX (MODE sheet): 0 off · 1 kaleido · 2 spin · 3 tunnel · 4 pulse.
    var geomFx by mutableStateOf(0)
    var geomAmount by mutableStateOf(0.6f)
    // The active source's AUTO-GAIN truth. Local truth comes from Rust; remote
    // truth is reconciled from the desktop K/status frame rather than invented here.
    var autoGain by mutableStateOf(false)
    var localAutoGain by mutableStateOf(false)
    var noSignal by mutableStateOf(false) // resting beam is up on an active source
    var hudMode by mutableStateOf(2) // nerd HUD: 0 on · 1 auto (console timer) · 2 off
    var hudLine by mutableStateOf("")
    var hudLine2 by mutableStateOf("") // bridge health (remote sessions only)
    var bandMode by mutableStateOf(0)  // status band: 0 on · 1 auto (console timer) · 2 off
    var fullscreen by mutableStateOf(true) // immersive (bars hidden); off shows system bars
    var uprightQuadrant by mutableStateOf(0) // UI-locked: counter-rotate icons to gravity
    var chromeQuadrant by mutableStateOf(0)  // scope-locked + UI-follow: whole chrome rotates to gravity
    var viewLock by mutableStateOf(false) // pin the current zoom: gain gestures inform only
    var latencyMode by mutableStateOf(2) // remote audio: 0 tight · 1 balanced · 2 safe
    var networkMode by mutableStateOf(0) // remote route: 0 auto · 1 Wi-Fi · 2 mobile
    var calDate by mutableStateOf("")  // last saveTuning date — the bench's CAL stamp
    // Desktop-truth band line while VISUALIZER feeds the beam (`swirl · auto · pc`);
    // null = local rendering, show the local mode/gain as always.
    var remoteScopeLine by mutableStateOf<String?>(null)
    var styleOverride by mutableStateOf(StyleOverride()) // user style knobs (ROOM sheet)
    var amoledCaptionSeen by mutableStateOf(false)       // one-time Void caption
    var bestiaryFound by mutableStateOf(false)           // the tube's secret, once kept
    var pip by mutableStateOf(false)

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

    // The picker and console share this one host-owned request path. The host rolls and
    // applies a real mode, so remote VISUALIZER control stays identical to a manual pick.
    private var randomModeRequest: (() -> Unit)? = null
    fun bindRandomModeRequest(request: () -> Unit) { randomModeRequest = request }
    fun requestRandomMode() { randomModeRequest?.invoke() }

    val modeLabel: String get() = ModeLabels.getOrElse(modeIndex) { "?" }
    val modeTag: String get() = ModeTags.getOrElse(modeIndex) { "?" }
    val mode3d: Boolean get() = modeIndex == 4 || modeIndex == 5 // attractor, helix
}
