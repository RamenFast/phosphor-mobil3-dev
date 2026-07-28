package dev.phosphor.mobil3.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.phosphor.mobil3.store.PhosphorStateListener
import dev.phosphor.mobil3.store.PhosphorStateStore
import dev.phosphor.mobil3.store.PhosphorStoreHealthListener

// One place both the activity and the chrome read/write. No god-object: these are the few
// genuinely-shared display facts, each with a single writer path.
class ScopeUiState(store: PhosphorStateStore) {
    private var causalSnapshot by mutableStateOf(store.snapshot)
    var hudControlWritable by mutableStateOf(true)
        private set
    var hudControlFix by mutableStateOf("")
        private set
    var hudControlStatus by mutableStateOf("")
    private val causalStateListener = PhosphorStateListener { causalSnapshot = it }
    private val causalHealthListener = PhosphorStoreHealthListener { health ->
        hudControlWritable = health.writable
        hudControlFix = health.fix?.fix.orEmpty()
    }

    init {
        causalSnapshot = store.addListener(causalStateListener)
        val health = store.addHealthListener(causalHealthListener)
        hudControlWritable = health.writable
        hudControlFix = health.fix?.fix.orEmpty()
    }

    var room by mutableStateOf(BlossomDark)
    var modeIndex by mutableStateOf(0)
    var randomModeArmed by mutableStateOf(false)
    var randomBanModes by mutableStateOf(setOf<Int>()) // faces ⚄ must never land on
    var beamIndex by mutableStateOf(0)
    var fpsValue by mutableStateOf(0) // engine convention: 0 = panel vsync
    var oversample by mutableStateOf(1) // beam integration multiplier

    var sourceLabel by mutableStateOf("no source")
    var captureStatus by mutableStateOf("")
    var captureFix by mutableStateOf("")
    var remote by mutableStateOf(false) // remote (Tailscale) source active
    // The last relay failure, carrying the engine's own fix text. Both the engine and the
    // relay protocol guarantee a fix on every error; before this field existed the fix was
    // built and then discarded, so the user saw a bare failure with no remedy.
    var remoteFailure by mutableStateOf("")
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
    // Phase 04: HUD is a strict Compose projection of the causal store. There is no
    // mutable UI-side HUD copy and no direct persistence path here.
    val hudMode: Int
        get() = when (causalSnapshot.effective.displayHud) {
            "on" -> 0
            "auto" -> 1
            "off" -> 2
            else -> error("unsupported causal display HUD mode ${causalSnapshot.effective.displayHud}")
        }
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
    var settingsTransferStatus by mutableStateOf("")

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

data class HudQuickAction(
    val enabled: Boolean,
    val requestedMode: Int,
    val labelMode: Int,
)

fun hudQuickAction(hudControlWritable: Boolean, causalHudMode: Int): HudQuickAction =
    HudQuickAction(
        enabled = hudControlWritable,
        requestedMode = if (hudControlWritable) (causalHudMode + 1) % 3 else causalHudMode,
        labelMode = causalHudMode,
    )

const val PENDING_HUD_SETTINGS_IMPORT_KEY: String = "__phosphor_private.pending_settings_import_hud"

data class PendingHudMarkerRead(
    val raw: String?,
    val wrongType: Boolean,
    val status: String? = null,
)

fun pendingHudMarkerRead(allPreferences: Map<String, Any?>): PendingHudMarkerRead {
    if (!allPreferences.containsKey(PENDING_HUD_SETTINGS_IMPORT_KEY)) {
        return PendingHudMarkerRead(raw = null, wrongType = false)
    }
    val value = allPreferences[PENDING_HUD_SETTINGS_IMPORT_KEY]
    val raw = value as? String
    return if (raw != null) {
        PendingHudMarkerRead(raw = raw, wrongType = false)
    } else {
        PendingHudMarkerRead(
            raw = null,
            wrongType = true,
            status = "pending HUD import marker has unsupported type · import a verified settings archive to replace it",
        )
    }
}

data class PendingHudSettingsImport(
    val contentSha256: String,
    val hudMode: Int,
    val operationId: String,
) {
    init {
        require(contentSha256.matches(Regex("[0-9a-f]{64}"))) { "content SHA-256 must be lowercase hexadecimal" }
        require(hudMode in 0..2) { "HUD mode must be on, auto, or off" }
        require(operationId.matches(Regex("[A-Za-z0-9._-]{1,128}"))) { "operation id contains unsupported characters" }
    }

    val idempotencyKey: String
        get() = "settings-import:$contentSha256:hud:$operationId"

    fun encode(): String = "v1:$contentSha256:$hudMode:$operationId"

    companion object {
        fun decode(raw: String?): PendingHudSettingsImport? {
            val parts = raw?.split(':', limit = 4) ?: return null
            if (parts.size != 4 || parts[0] != "v1") return null
            return runCatching {
                PendingHudSettingsImport(parts[1], parts[2].toInt(), parts[3])
            }.getOrNull()
        }
    }
}

fun settingsImportHudOperation(
    pendingRaw: String?,
    contentSha256: String,
    hudMode: Int,
    newOperationId: String,
): PendingHudSettingsImport {
    val pending = PendingHudSettingsImport.decode(pendingRaw)
    return if (pending?.contentSha256 == contentSha256 && pending.hudMode == hudMode) {
        pending
    } else {
        PendingHudSettingsImport(contentSha256, hudMode, newOperationId)
    }
}

fun settingsImportAppliedCount(importedValueCount: Int, hudResultAccepted: Boolean): Int =
    if (hudResultAccepted) importedValueCount else (importedValueCount - 1).coerceAtLeast(0)

data class PendingHudResumeDecision(
    val shouldDispatch: Boolean,
    val attempted: Boolean,
    val status: String? = null,
)

fun pendingHudResumeDecision(
    rawMarker: String?,
    lifecycleStarted: Boolean,
    activityFinishing: Boolean,
    activityDestroyed: Boolean,
): PendingHudResumeDecision {
    if (rawMarker == null) return PendingHudResumeDecision(shouldDispatch = false, attempted = false)
    val pending = PendingHudSettingsImport.decode(rawMarker)
        ?: return PendingHudResumeDecision(
            shouldDispatch = false,
            attempted = true,
            status = "pending HUD import is malformed · import a verified settings archive to replace it",
        )
    if (!lifecycleStarted || activityFinishing || activityDestroyed) {
        return PendingHudResumeDecision(
            shouldDispatch = false,
            attempted = false,
            status = "pending HUD import preserved for the next foreground Activity",
        )
    }
    return PendingHudResumeDecision(
        shouldDispatch = true,
        attempted = true,
        status = "resume:${pending.idempotencyKey}",
    )
}

data class PendingHudMarkerWakeDecision(
    val shouldWake: Boolean,
    val keepResumeAttempted: Boolean,
    val reconcileImportedPreferences: Boolean,
    val suppressedOwnInFlightMarker: Boolean = false,
)

fun pendingHudMarkerWakeDecision(
    changedKey: String?,
    rawMarker: String?,
    localInFlightRawMarker: String? = null,
    lifecycleStarted: Boolean,
    activityFinishing: Boolean,
    activityDestroyed: Boolean,
): PendingHudMarkerWakeDecision {
    if (changedKey != PENDING_HUD_SETTINGS_IMPORT_KEY || rawMarker == null) {
        return PendingHudMarkerWakeDecision(
            shouldWake = false,
            keepResumeAttempted = false,
            reconcileImportedPreferences = false,
        )
    }
    if (rawMarker == localInFlightRawMarker) {
        return PendingHudMarkerWakeDecision(
            shouldWake = false,
            keepResumeAttempted = false,
            reconcileImportedPreferences = false,
            suppressedOwnInFlightMarker = true,
        )
    }
    val resume = pendingHudResumeDecision(
        rawMarker = rawMarker,
        lifecycleStarted = lifecycleStarted,
        activityFinishing = activityFinishing,
        activityDestroyed = activityDestroyed,
    )
    return PendingHudMarkerWakeDecision(
        shouldWake = resume.shouldDispatch,
        keepResumeAttempted = resume.attempted,
        reconcileImportedPreferences = resume.shouldDispatch,
    )
}

data class PreferenceValueSnapshot(
    val present: Boolean,
    val value: Any?,
)

object PendingHudMarkerTransaction {
    val lock = Any()
}

fun preferenceValueSnapshots(
    allPreferences: Map<String, Any?>,
    keys: Set<String>,
): Map<String, PreferenceValueSnapshot> = keys.associateWith { key ->
    PreferenceValueSnapshot(
        present = allPreferences.containsKey(key),
        value = allPreferences[key],
    )
}

data class PendingHudCleanupResult(
    val markerAfterCleanup: String?,
    val cleared: Boolean,
    val attemptedRestore: Boolean,
    val honestStatus: String,
)

fun pendingHudCleanupResult(
    expectedRaw: String,
    currentRaw: String?,
    observedRawAfterCleanup: String?,
    observedWrongTypeAfterCleanup: Boolean = false,
    removeCommitted: Boolean,
    restoreCommitted: Boolean,
): PendingHudCleanupResult {
    if (currentRaw != expectedRaw) {
        return PendingHudCleanupResult(
            markerAfterCleanup = observedRawAfterCleanup,
            cleared = false,
            attemptedRestore = false,
            honestStatus = if (observedWrongTypeAfterCleanup) {
                "pending receipt cleanup skipped; marker has unsupported type"
            } else {
                "pending receipt cleanup skipped; marker changed before cleanup"
            },
        )
    }
    if (removeCommitted && observedRawAfterCleanup == null && !observedWrongTypeAfterCleanup) {
        return PendingHudCleanupResult(
            markerAfterCleanup = null,
            cleared = true,
            attemptedRestore = false,
            honestStatus = "pending receipt cleanup complete",
        )
    }
    return PendingHudCleanupResult(
        markerAfterCleanup = observedRawAfterCleanup,
        cleared = false,
        attemptedRestore = !removeCommitted,
        honestStatus = when {
            observedWrongTypeAfterCleanup -> "pending receipt cleanup left an unsupported marker type; import a verified settings archive to replace it"
            removeCommitted -> "pending receipt cleanup reported committed but marker remains; retry after storage is writable"
            restoreCommitted -> "pending receipt cleanup failed; retry after storage is writable"
            else -> "pending receipt cleanup failed and marker restore was not committed; retry after storage is writable"
        },
    )
}

fun hudImportStatusWithCleanup(
    hudStatus: String,
    cleanup: PendingHudCleanupResult,
): String = if (cleanup.cleared) hudStatus else "$hudStatus · ${cleanup.honestStatus}"

fun settingsExportPreferences(
    allPreferences: Map<String, Any?>,
    causalHudMode: Int,
    causalStoreWritable: Boolean,
): Map<String, Any?> = buildMap {
    allPreferences.forEach { (key, value) ->
        if (key != "hud_mode") put(key, value)
    }
    if (causalStoreWritable) put("hud_mode", causalHudMode)
}
