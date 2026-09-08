package dev.phosphor.mobil3.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Shared screen state for the activity and transient Compose chrome. */
class ScopeUiState {
    var room by mutableStateOf(Amoled)
    var modeIndex by mutableIntStateOf(1)
    var randomModeArmed by mutableStateOf(false)
    var randomBanModes by mutableStateOf(setOf<Int>())
    var beamIndex by mutableIntStateOf(7)
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
    var displayPaused by mutableStateOf(false)
    var pauseBlack by mutableStateOf(false)
    var heldFrameAvailable by mutableStateOf(false)
    var pauseSourceLive by mutableStateOf(false)
    val pauseLabel: String get() = PauseDisplayPolicy.status(
        displayPaused, pauseBlack, heldFrameAvailable, pauseSourceLive,
    )

    var trackTitle by mutableStateOf<String?>(null)
    var trackArtist by mutableStateOf<String?>(null)
    var artwork by mutableStateOf<ByteArray?>(null)
    var queueTitles by mutableStateOf<List<String>>(emptyList())
    var queueIndex by mutableIntStateOf(0)

    var seekable by mutableStateOf(false)
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)

    var gain by mutableFloatStateOf(1.8332275f)
    var beamEnergy by mutableFloatStateOf(8.0f)
    var glow by mutableFloatStateOf(0.7f)
    var grid by mutableStateOf(false)
    var gridData by mutableStateOf(GridData.DEFAULT)
    var gridReading by mutableStateOf<GridData.Reading?>(null)
    var beamRandomArmed by mutableStateOf(false)
    var beamRandomLo by mutableFloatStateOf(6.0f)
    var beamRandomHi by mutableFloatStateOf(20.0f)
    var glowRandomArmed by mutableStateOf(false)
    var glowRandomLo by mutableFloatStateOf(0.30f)
    var glowRandomHi by mutableFloatStateOf(0.90f)
    var geomFx by mutableIntStateOf(0)
    var geomAmount by mutableFloatStateOf(0.6f)
    var autoGain by mutableStateOf(true)
    var localAutoGain by mutableStateOf(true)
    var noSignal by mutableStateOf(false)
    var hudMode by mutableIntStateOf(1)
    var hudControlStatus by mutableStateOf("")
    var hudLine by mutableStateOf("")
    var hudLine2 by mutableStateOf("")
    var bandMode by mutableIntStateOf(1)
    var fullscreen by mutableStateOf(true)
    var lingerBackground by mutableStateOf(false)
    var doubleTapPlayback by mutableStateOf(true)
    var pipAutoEnter by mutableStateOf(dev.phosphor.mobil3.PictureInPicturePolicy.DEFAULT)
    private var keepControls by mutableStateOf(ControlsVisibilityPolicy.DEFAULT)
    var controlsVisibilityRevision by mutableLongStateOf(0L)
        private set
    var controlsAlwaysVisible: Boolean
        get() = keepControls
        set(value) {
            if (value != keepControls) {
                keepControls = value
                controlsVisibilityRevision++
            }
        }
    var systemRotationLocked by mutableStateOf(true)
    var rotationPresentation by mutableStateOf(RotationDetent.Presentation())
    val uprightQuadrant: Int get() = rotationPresentation.uprightQuadrant
    val chromeQuadrant: Int get() = rotationPresentation.chromeQuadrant
    var viewLock by mutableStateOf(false)
    var latencyMode by mutableIntStateOf(2)
    var calDate by mutableStateOf("")
    var remoteScopeLine by mutableStateOf<String?>(null)
    var styleOverride by mutableStateOf(StyleOverride())
    var amoledCaptionSeen by mutableStateOf(false)
var bestiaryFound by mutableStateOf(false)
    var rootCaptureEnabled by mutableStateOf(false)
    var rootCaptureBusy by mutableStateOf(false)
    var rootCaptureStatus by mutableStateOf("")
    var captureRoot by mutableStateOf(false)
    var pip by mutableStateOf(false)
    var presentationVisible by mutableStateOf(true)
    var floatingHudEnabled by mutableStateOf(false)
    var floatingHudTransparent by mutableStateOf(false)
    var floatingHudActive by mutableStateOf(false)
    var floatingHudStatus by mutableStateOf("HUD off")
    var showSourcePicker by mutableStateOf(false)
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

/** Absolute raw-channel values from scopeStats, independent of display gain and HUD visibility. */
object GridData {
    const val KEY = "grid_data"
    const val DEFAULT = false
    data class Reading(val left: Double, val right: Double, val leftDbfs: Double?, val rightDbfs: Double?)

    fun needsStats(hudMode: Int, enabled: Boolean) = hudMode != 2 || enabled

    fun read(stats: org.json.JSONObject?): Reading? {
        val data = stats?.optJSONObject(KEY) ?: return null
        fun rawPeak(key: String) = data.optDouble(key, Double.NaN).takeIf { it.isFinite() && it >= 0.0 }
        val left = rawPeak("left") ?: return null
        val right = rawPeak("right") ?: return null
        fun dbfs(key: String) = data.optDouble(key, Double.NaN).takeIf { it.isFinite() }
        val leftDbfs = dbfs("left_dbfs")
        val rightDbfs = dbfs("right_dbfs")
        if ((left > 0.0 && leftDbfs == null) || (right > 0.0 && rightDbfs == null)) return null
        return Reading(left, right, leftDbfs, rightDbfs)
    }

    fun line(reading: Reading?, left: Boolean): String {
        val channel = if (left) "L" else "R"
        if (reading == null) return "$channel · no data"
        val rawPeak = if (left) reading.left else reading.right
        val dbfs = if (left) reading.leftDbfs else reading.rightDbfs
        val db = if (rawPeak == 0.0) "−∞" else String.format(java.util.Locale.ROOT, "%.1f", dbfs)
        return String.format(java.util.Locale.ROOT, "%s · %.3f · %s dBFS", channel, rawPeak, db)
    }
}

fun nextHudMode(current: Int): Int = ((if (current in 0..2) current else 2) + 1) % 3
