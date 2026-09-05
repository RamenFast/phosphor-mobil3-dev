package dev.phosphor.mobil3.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Shared screen state for the activity and transient Compose chrome. */
class ScopeUiState {
    var room by mutableStateOf(BlossomDark)
    var modeIndex by mutableIntStateOf(0)
    var randomModeArmed by mutableStateOf(false)
    var randomBanModes by mutableStateOf(setOf<Int>())
    var beamIndex by mutableIntStateOf(0)
    var fpsValue by mutableIntStateOf(0)
    var oversample by mutableIntStateOf(1)

    var sourceLabel by mutableStateOf("no source")
    var captureStatus by mutableStateOf("")
    var captureFix by mutableStateOf("")
    var remote by mutableStateOf(false)
    var remoteFailure by mutableStateOf("")
    var remoteAudio by mutableStateOf(true)
    var remoteGeometry by mutableStateOf(false)
    var live by mutableStateOf(false)
    var captureMetadataAccess by mutableStateOf(false)
    var captureCanPlay by mutableStateOf(false)
    var captureCanNext by mutableStateOf(false)
    var captureCanPrevious by mutableStateOf(false)
    var playing by mutableStateOf(false)
    var trackTitle by mutableStateOf<String?>(null)
    var trackArtist by mutableStateOf<String?>(null)
    var artwork by mutableStateOf<ByteArray?>(null)
    var queueTitles by mutableStateOf<List<String>>(emptyList())
    var queueIndex by mutableIntStateOf(0)

    var seekable by mutableStateOf(false)
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)

    var gain by mutableFloatStateOf(1.0f)
    var beamEnergy by mutableFloatStateOf(8.0f)
    var glow by mutableFloatStateOf(0.7f)
    var grid by mutableStateOf(true)
    var beamRandomArmed by mutableStateOf(false)
    var beamRandomLo by mutableFloatStateOf(6.0f)
    var beamRandomHi by mutableFloatStateOf(20.0f)
    var glowRandomArmed by mutableStateOf(false)
    var glowRandomLo by mutableFloatStateOf(0.30f)
    var glowRandomHi by mutableFloatStateOf(0.90f)
    var geomFx by mutableIntStateOf(0)
    var geomAmount by mutableFloatStateOf(0.6f)
    var autoGain by mutableStateOf(false)
    var localAutoGain by mutableStateOf(false)
    var noSignal by mutableStateOf(false)
    var hudMode by mutableIntStateOf(2)
    var hudControlStatus by mutableStateOf("")
    var hudLine by mutableStateOf("")
    var hudLine2 by mutableStateOf("")
    var bandMode by mutableIntStateOf(0)
    var fullscreen by mutableStateOf(true)
    var lingerBackground by mutableStateOf(false)
    var uprightQuadrant by mutableIntStateOf(0)
    var chromeQuadrant by mutableIntStateOf(0)
    var viewLock by mutableStateOf(false)
    var latencyMode by mutableIntStateOf(2)
    var calDate by mutableStateOf("")
    var remoteScopeLine by mutableStateOf<String?>(null)
    var styleOverride by mutableStateOf(StyleOverride())
    var amoledCaptionSeen by mutableStateOf(false)
    var bestiaryFound by mutableStateOf(false)
    var pip by mutableStateOf(false)
    var settingsTransferStatus by mutableStateOf("")

    var customColors by mutableStateOf(
        listOf(
            androidx.compose.ui.graphics.Color(0xFF6BFF8C),
            androidx.compose.ui.graphics.Color(0xFF35BFFF),
            androidx.compose.ui.graphics.Color(0xFFFF4CE1),
        )
    )
    var customCount by mutableIntStateOf(0)
    var cycleSeconds by mutableFloatStateOf(3.0f)
    var cyclePerTrack by mutableStateOf(false)

    private var randomModeRequest: (() -> Unit)? = null
    fun bindRandomModeRequest(request: () -> Unit) { randomModeRequest = request }
    fun requestRandomMode() { randomModeRequest?.invoke() }

    val modeLabel: String get() = ModeLabels.getOrElse(modeIndex) { "?" }
    val modeTag: String get() = ModeTags.getOrElse(modeIndex) { "?" }
    val mode3d: Boolean get() = modeIndex == 4 || modeIndex == 5
}

fun nextHudMode(current: Int): Int = ((if (current in 0..2) current else 2) + 1) % 3
