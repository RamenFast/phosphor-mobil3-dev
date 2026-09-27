package dev.phosphor.mobil3

import android.Manifest
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.provider.Settings
import android.view.OrientationEventListener
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import dev.phosphor.mobil3.settings.SettingsArchive
import dev.phosphor.mobil3.settings.appearance.*
import dev.phosphor.mobil3.settings.instrument.*
import dev.phosphor.mobil3.ui.AppearanceMigration
import dev.phosphor.mobil3.ui.AppearancePalette
import dev.phosphor.mobil3.ui.AutoFramePreference
import dev.phosphor.mobil3.ui.AutoFrameSave
import dev.phosphor.mobil3.ui.GainScale
import dev.phosphor.mobil3.ui.LightCycleGuard
import dev.phosphor.mobil3.ui.LightRgb
import dev.phosphor.mobil3.ui.LightSettings
import dev.phosphor.mobil3.ui.Palette
import dev.phosphor.mobil3.ui.PhosphorScreen
import dev.phosphor.mobil3.ui.RotationDetent
import dev.phosphor.mobil3.ui.ScopeActions
import dev.phosphor.mobil3.ui.ScopeUiState
import dev.phosphor.mobil3.ui.applyPresetLight
import dev.phosphor.mobil3.ui.overridden
import dev.phosphor.mobil3.ui.readReducedMotion
import dev.phosphor.mobil3.ui.rollModeExcluding
import dev.phosphor.mobil3.ui.style
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

// Compose chrome overlays the scope SurfaceView. The loaded deck owns transport through one
// MediaController so the console, notification, and lock screen remain consistent.
class MainActivity : ComponentActivity(), ScopeActions {

    private lateinit var ui: ScopeUiState
    private val mic = MicCaptureService
    private var micUiRevision = 0L
    private var micRefreshAt = 0L
    private var mixStartPending = false
    private var lastMixAttempt = -1L
    private var bluetoothForMix = false
    private val micChanged: () -> Unit = { if (::ui.isInitialized && !activityDestroyed) refreshMicrophone() }
    private var taskRevision = -1L
    private var activityRevision = -1L
    private var activityDestroyed = false
    private var activityStarted = false
    private var activityResumed = false
    private var activityFocused = false
    private var scopeSurface: SurfaceView? = null
    private var surfaceHost: SurfaceHost? = null
    private var hudWasPresenting = false
    private var hudConsentOpen = false
    private val hudChanged: () -> Unit = {
        if (::ui.isInitialized && !activityDestroyed) {
            ui.floatingHudActive = FloatingHudService.active
            ui.floatingHudStatus = FloatingHudService.status
            ui.presentationVisible = activityStarted && !FloatingHudService.presenting
            applyBrightnessPin()
            tick.removeCallbacks(uiTick)
            if (ui.presentationVisible) tick.post(uiTick)
            updateOrientationSensor()
            updatePictureInPictureParams()
            val justShown = FloatingHudService.presenting && !hudWasPresenting
            hudWasPresenting = FloatingHudService.presenting
            if (justShown && activityStarted) moveTaskToBack(true)
        }
    }
    private val controllerBinding = ActivityControllerBinding()
    private fun taskIsCurrent(): Boolean = !activityDestroyed &&
        BackgroundLifecycle.policy.accepts(taskRevision) && BackgroundLifecycle.policy.acceptsActivity(activityRevision)

    private fun startSourceService(intent: Intent) {
        if (taskIsCurrent()) startService(BackgroundLifecycle.stamp(intent, taskRevision)
            .putExtra(BackgroundLifecycle.EXTRA_ACTIVITY_REVISION, activityRevision))
    }

    private fun startCaptureService(intent: Intent) {
        if (taskIsCurrent()) startForegroundService(BackgroundLifecycle.stamp(intent, taskRevision)
            .putExtra(BackgroundLifecycle.EXTRA_ACTIVITY_REVISION, activityRevision))
    }
    private var micReleaseRevision: Long? = null
    private var captureStatusSequence = 0L
    private var pendingCaptureConsent: Long? = null
    private val micHandoff = MicHandoffPolicy(
        start = { done ->
            if (micRequestIsCurrent()) {
                applyLocalGainPolicy()
                mic.request(this, null, CaptureMixSettings.read(prefs().all),
                    { micRequestIsCurrent() && micStartEligible() }, done)
            } else {
                micHandoffCancel()
            }
        },
        publish = { error ->
            ui.live = error == null
            ui.sourceLabel = if (error == null) "mic" else "no source"
            if (error != null) Toast.makeText(this, error, Toast.LENGTH_LONG).show()
        },
    )
    private var pendingAudioPermission = AudioPermissionPurpose.NONE
    private var controller: MediaController? = null
    private var reduced by mutableStateOf(false)
    private var gainValue = 1.8332275f
    private var autoFrameScale = AutoFramePreference.DEFAULT
    private var lastRandomTrackTitle: String? = null
    private var scopeRotationLockState by mutableStateOf(true)
    private var uiPlacementLockState by mutableStateOf(false)
    private var lockedUiLandscape by mutableStateOf(false)
    private var lockedScopeOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var lockedUiOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    // Route gravity to the beam, individual elements, or the whole chrome for the four lock combinations.
    private var gravityListener: android.hardware.SensorEventListener? = null
    private var pendingFreshStartup = false
    companion object {
        private val autoFrameSave = AutoFrameSave()
        @Volatile private var processStartupConsumed = false
    }
    private var sourceSelection = 0L
    private var signalSelected = SignalKind.UNKNOWN
    private var signalDenied: String? = null
    private var signalCaptureStatus: CaptureService.CaptureStatus? = null
    private var signalResumed = false
    private var signalFocused = false
    private val signalRefresh = SignalRefreshOwner()
    private val signalNative = SignalNativeObservation()
    private var lastSensorDeg = OrientationEventListener.ORIENTATION_UNKNOWN

    /**
     * The cardinal the chrome is currently committed to, or -1 before the first reading.
     *
     * Paired with the two tolerances below to give rotation a detent: once an
     * orientation is taken it holds through a wide sloppy range, and only a decisive
     * turn close to the next cardinal takes it away. A single symmetric window instead
     * flipped the chrome the instant the phone crossed 45°, which is what made rotation
     * feel twitchy.
     */
    private var committedCardinal = RotationDetent.NONE
    private var rotationAuthorityNeedsRouting = true
    private var captureStatusReceiverRegistered = false
    private val tick = Handler(Looper.getMainLooper())
    private val persistGain = Runnable {
        persistAutomaticGain()
    }
    private val persistAutoFrame = Runnable { finishAutoFrameScale() }
    private val captureStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == CaptureService.ACTION_STATUS) {
                applyCaptureStatus(CaptureService.statusFrom(intent))
            } else if (intent?.action == PlaybackService.ACTION_LOCAL_SOURCE_CHANGED) {
                applyLocalSourcePublication(intent.getLongExtra(PlaybackService.EXTRA_SOURCE_REVISION, -1))
            }
            reassertSourceWake()
        }
    }

    private enum class AudioPermissionPurpose {
        NONE,
        MICROPHONE,
        MIX_MICROPHONE,
        PLAYBACK_CAPTURE,
    }

    private val openFileLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (taskIsCurrent()) uri?.let { loadUri(it) }
        }

    private val overlaySettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            hudConsentOpen = false
            ui.floatingHudStatus = if (Settings.canDrawOverlays(this)) "Overlay access allowed. Tap SHOW FLOATING HUD" else "Overlay access not granted. HUD remains off"
            updatePictureInPictureParams()
        }

    // The service owns traversal, staging and open. Binder carries only the tree identity.
    private val openFolderLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (!taskIsCurrent()) return@registerForActivityResult
            uri ?: return@registerForActivityResult
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                selectSource(SignalKind.LOCAL)
                applyLocalGainPolicy()
                startSourceService(Intent(this, PlaybackService::class.java)
                    .setAction(PlaybackService.ACTION_OPEN_TREE)
                    .putExtra(PlaybackService.EXTRA_TREE_URI, uri.toString())
                    .putExtra(PlaybackService.EXTRA_QUEUE_START, 0))
            }.onFailure {
                Toast.makeText(this, "Folder access failed, choose a readable tree", Toast.LENGTH_LONG).show()
            }
        }

    private val createSettingsArchive =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri ?: return@registerForActivityResult
            saveTuning()
            ui.settingsTransferStatus = "exporting settings…"
            Thread {
                runCatching {
                    val result = SettingsArchive.export(
                        sourcePackage = packageName,
                        sourceVersion = BuildConfig.VERSION_NAME,
                        sourceDistribution = if (BuildConfig.DEBUG) "debug" else "release",
                        exportedAt = java.time.Instant.now().toString(),
                        allPreferences = prefs().all,
                    )
                    val output = contentResolver.openOutputStream(uri, "wt")
                        ?: error("Android did not provide a writable document")
                    output.bufferedWriter(Charsets.UTF_8).use { it.write(result.json) }
                    "exported ${result.exportedKeys.size} settings · ${result.contentSha256.take(12)}"
                }.onSuccess { status ->
                    runOnUiThread { ui.settingsTransferStatus = status }
                }.onFailure { error ->
                    runOnUiThread {
                        ui.settingsTransferStatus = "export failed · ${error.message ?: "choose another document"}"
                    }
                }
            }.start()
        }

    private val settingsWriteOwner = dev.phosphor.mobil3.settings.SettingsWriteOwner()
    private var appearanceWorkflow: AppearanceWorkflow? = null
    private var pendingAppearanceImport: AppearanceWorkflow.Ticket? = null
    private var instrumentWorkflow: InstrumentWorkflow? = null
    private var pendingSettingsImport: InstrumentWorkflow.SettingsImport? = null
    private lateinit var instrumentStore: InstrumentPresetStore
    private val instrumentDocuments = InstrumentDocumentOwner()
    private var instrumentExport: Pair<InstrumentDocumentOwner.Ticket, String>? = null
    private var instrumentImportRevision: InstrumentDocumentOwner.Ticket? = null
    private var instrumentSource: String? = null

    private fun captureInstrument() = InstrumentSetup(
        ui.modeIndex, ui.randomModeArmed, ui.randomBanModes.sorted(), ui.geomFx, ui.geomAmount,
        gainValue, ui.localAutoGain, ui.focus, ui.beamEnergy, ui.glow,
        ui.beamRandomArmed, ui.beamRandomLo, ui.beamRandomHi,
        ui.glowRandomArmed, ui.glowRandomLo, ui.glowRandomHi,
        ui.grid, ui.gridData, ui.oversample, ui.light,
    )

    private fun publishInstrument(setup: InstrumentSetup) {
        tick.removeCallbacks(persistGain)
        ui.modeIndex = setup.mode
        ui.randomModeArmed = setup.randomModeArmed
        ui.randomBanModes = setup.randomBanModes.toSet()
        ui.geomFx = setup.geomFx
        ui.geomAmount = setup.geomAmount
        gainValue = setup.gain
        ui.gain = setup.gain
        ui.manualGain = setup.gain
        ui.localAutoGain = setup.autoGain
        ui.autoGain = setup.autoGain
        ui.focus = setup.focus
        ui.beamEnergy = setup.beamEnergy
        ui.glow = setup.glow
        ui.beamRandomArmed = setup.beamRandomArmed
        ui.beamRandomLo = setup.beamRandomMin
        ui.beamRandomHi = setup.beamRandomMax
        ui.glowRandomArmed = setup.glowRandomArmed
        ui.glowRandomLo = setup.glowRandomMin
        ui.glowRandomHi = setup.glowRandomMax
        ui.grid = setup.grid
        ui.gridData = setup.gridData
        ui.gridReading = null
        ui.oversample = setup.oversample
        ui.light = setup.light
        ui.beamIndex = setup.light.preset
        ui.lightTemporary = false
        ui.lightPending = null
        ui.lightError = ""
    }

    private fun persistInstrument(setup: InstrumentSetup): InstrumentWorkflow.PersistenceFailure? {
        if (appearanceWorkflow?.uncertain == true) return InstrumentWorkflow.PersistenceFailure(
            "Appearance recovery must finish first. Open APPEARANCE and repair or retry the saved appearance.", false)
        return settingsWriteOwner.write {
        val values = setup.preferenceValues()
        val prior = preferenceValueSnapshots(prefs().all, values.keys)
        val editor = prefs().edit()
        values.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
            }
        }
        settingsWriteOwner.commit({ editor.commit() }, { true }, { restorePreferenceSnapshots(prior) })?.let {
            appearanceWorkflow?.persistenceFailed(it)
            InstrumentWorkflow.PersistenceFailure(it.message(), it.restored)
        }
        }
    }

    private fun initializeAppearance() {
        appearanceWorkflow = AppearanceWorkflow(
            AppearancePreferences(settingsWriteOwner, { prefs().all },
                { encoded -> prefs().edit().putString(AppearanceMigration.KEY, encoded).commit() },
                { before -> restorePreferenceSnapshots(preferenceValueSnapshots(before, setOf(AppearanceMigration.KEY))) }),
            AppearanceMigration::initial,
            failed = { failure -> reportTuningWriteFailure(failure) },
            changed = ::refreshAppearance,
            sharedBlocked = { instrumentWorkflow?.storageUncertain == true },
        )
        appearanceWorkflow?.load()
    }

    private fun refreshAppearance() {
        val owner = appearanceWorkflow ?: return
        ui.appearanceDocument = owner.committed
        ui.appearanceValue = owner.effective
        ui.appearanceRevision = owner.revision
        ui.appearanceSummary = owner.summary
        ui.appearanceStatus = owner.status
        ui.appearancePreview = owner.preview != null
        ui.appearanceBlocked = owner.blocked
        ui.appearanceRepairRequired = owner.unavailable
        ui.appearanceRecoveryRequired = owner.uncertain
        owner.effective?.let { value ->
            val document = owner.committed
            val id = document?.activeId?.ifBlank { "appearance:custom" } ?: "appearance:custom"
            val palette = AppearancePalette.palette(value, id, owner.summary)
            baseRoom = palette
            ui.room = palette
            ui.appearanceStyle = AppearancePalette.style(value)
            ui.styleOverride = dev.phosphor.mobil3.ui.StyleOverride()
        }
    }

    override fun previewAppearance(value: AppearanceValue) { appearanceWorkflow?.preview(value) }
    override fun applyAppearance(value: AppearanceValue, id: String) { appearanceWorkflow?.apply(value, id) }
    override fun selectAppearance(id: String) { appearanceWorkflow?.select(id) }
    override fun cancelAppearancePreview() { appearanceWorkflow?.cancel() }
    override fun saveAppearance(name: String, value: AppearanceValue, id: String?) { appearanceWorkflow?.save(name, value, id) }
    override fun renameAppearance(id: String, name: String) { appearanceWorkflow?.rename(id, name) }
    override fun deleteAppearance(id: String) { appearanceWorkflow?.delete(id) }
    override fun resetAppearance() { appearanceWorkflow?.reset() }
    override fun repairAppearance() { appearanceWorkflow?.replace(AppearanceDocument.of()) }
    override fun recoverAppearance() {
        if (appearanceWorkflow?.recover() == true) instrumentWorkflow?.retryPersistence()
    }

    private fun initializeInstruments() {
        val storage = getSharedPreferences(InstrumentPresetCollection.PREFERENCES_FILE, MODE_PRIVATE)
        instrumentStore = InstrumentPresetStore(
            read = { storage.getString(InstrumentPresetCollection.COLLECTION_KEY, null) },
            write = { value ->
                val editor = storage.edit()
                if (value == null) editor.remove(InstrumentPresetCollection.COLLECTION_KEY)
                else editor.putString(InstrumentPresetCollection.COLLECTION_KEY, value)
                editor.commit()
            },
        )
        instrumentStore.load()
        ui.instrumentCollection = instrumentStore.collection
        ui.instrumentStatus = instrumentStore.error
        instrumentWorkflow = InstrumentWorkflow(
            native = object : InstrumentWorkflow.Native {
                override fun request(setup: InstrumentSetup) = PhosphorNative.requestInstrument(InstrumentPresetCodec.encodeSetup(setup))
                override fun cancel(id: Long) = PhosphorNative.cancelInstrument(id)
                override fun release(id: Long) { check(PhosphorNative.releaseInstrument(id)) { "Native receipt release failed" } }
            },
            snapshot = ::captureInstrument, publish = ::publishInstrument, persist = ::persistInstrument,
            canApply = { taskIsCurrent() && !(ui.remote && ui.remoteGeometry) },
            acknowledged = ::epilepsyAcknowledged,
            waitOffMain = { id, done ->
                Thread({
                    val result = try { PhosphorNative.awaitInstrument(id) }
                        catch (_: Exception) { runCatching { PhosphorNative.cancelInstrument(id) }.getOrDefault(4) }
                    tick.post { if (!activityDestroyed) done(result) }
                }, "instrument-await-$id").start()
            },
            changed = ::refreshInstrumentState,
        )
    }

    private fun refreshInstrumentState() {
        val owner = instrumentWorkflow ?: return
        refreshAppearance()
        ui.instrumentPending = owner.pending
        ui.instrumentUndo = owner.undoSetup != null
        ui.instrumentRapid = owner.rapidReview != null
        ui.instrumentUnsaved = owner.unsaved && !owner.uncertain
        ui.instrumentRestoreRequired = owner.restoreRequired
        ui.instrumentSourceRequired = owner.sourceControlsRequired
        ui.instrumentApplyStatus = owner.status
        ui.instrumentRecall = owner.association?.let {
            "${it.name} · ${if (owner.modified) "modified" else "recalled"}"
        } ?: "Local authored setup"
    }

    private fun instrumentEdit(block: () -> Unit) = instrumentValueEdit(Unit, block)

    private fun <T> instrumentValueEdit(fallback: T, block: () -> T): T {
        val owner = instrumentWorkflow
        if (owner == null) return block()
        owner.settle("Apply superseded by a later tuning edit.")
        if (owner.editsBlocked) {
            refreshInstrumentState()
            ui.showInstrumentPresets = true
            return fallback
        }
        return owner.edit(block)
    }

    private fun instrumentFailure(error: Throwable) {
        ui.instrumentStatus = if (error is InstrumentPresetException) "${error.message}. ${error.fix}."
            else "Instrument operation failed. ${error.message ?: "Reopen the browser and retry."}"
    }

    private fun instrumentRecord(key: String): InstrumentWorkflow.Association {
        CuratedInstrumentPresets.all.find { "curated:${it.name}" == key }?.let {
            return InstrumentWorkflow.Association(key, it.name, it.setup)
        }
        val record = checkNotNull(instrumentStore.collection) { "Saved collection is unavailable" }.record(key)
        return InstrumentWorkflow.Association(record.id, record.name, record.setup)
    }

    private fun changeInstruments(success: String, change: (InstrumentPresetCollection) -> InstrumentPresetCollection) {
        if (!taskIsCurrent()) return
        instrumentWorkflow?.settle("Saved record changed. Pending apply cancelled.")
        val saved = settingsWriteOwner.write { instrumentStore.change(change) }
        ui.instrumentCollection = instrumentStore.collection
        ui.instrumentStatus = if (saved) success else instrumentStore.error
        if (saved) instrumentStore.collection?.let { instrumentWorkflow?.refreshAssociation(it) }
    }

    override fun openInstrumentPresets() {
        if (!taskIsCurrent()) return
        instrumentStore.load()
        ui.instrumentCollection = instrumentStore.collection
        ui.instrumentStatus = instrumentStore.error
        ui.showInstrumentPresets = true
        refreshInstrumentState()
    }

    override fun applyInstrument(key: String) {
        runCatching { instrumentRecord(key).let { instrumentWorkflow?.apply(it.setup, it) } }.onFailure(::instrumentFailure)
    }
    override fun saveInstrument(name: String) {
        runCatching { instrumentEdit {
            val setup = captureInstrument()
            changeInstruments("Saved local authored setup. Tuning unchanged.") { it.create(name, setup) }
        } }.onFailure(::instrumentFailure)
    }
    override fun updateInstrument(id: String) {
        runCatching { instrumentEdit {
            val setup = captureInstrument()
            changeInstruments("Updated saved setup. Tuning unchanged.") { it.update(id, setup) }
        } }.onFailure(::instrumentFailure)
    }
    override fun renameInstrument(id: String, name: String) = changeInstruments("Renamed preset.") { it.rename(id, name) }
    override fun duplicateInstrument(key: String, name: String) {
        runCatching { val setup = instrumentRecord(key).setup
            changeInstruments("Saved duplicate. Tuning unchanged.") { it.create(name, setup) }
        }.onFailure(::instrumentFailure)
    }
    override fun deleteInstrument(id: String) = changeInstruments("Deleted record. Tuning unchanged.") { it.delete(id) }
    override fun undoInstrument() { instrumentWorkflow?.undo() }
    override fun cancelInstrumentApply() { instrumentWorkflow?.settle() }
    override fun retryInstrumentSave() {
        if (appearanceWorkflow?.recover() != false) instrumentWorkflow?.retryPersistence()
    }
    override fun keepInstrumentSafe() { instrumentWorkflow?.keepSafe() }
    override fun allowInstrumentRapid() {
        ackEpilepsy()
        instrumentWorkflow?.allowRapid()
    }
    override fun instrumentSourceControls() { ui.showSourcePicker = true }

    private fun instrumentDocumentWork(ticket: InstrumentDocumentOwner.Ticket, name: String, block: () -> Unit) {
        runCatching { Thread(block, name).start() }.onFailure {
            instrumentDocuments.finish(ticket)
            if (!activityDestroyed) {
                ui.instrumentDocumentBusy = false
                instrumentFailure(it)
            }
        }
    }

    private val openInstrumentDocument =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val revision = instrumentImportRevision
            instrumentImportRevision = null
            if (revision == null) return@registerForActivityResult
            if (!taskIsCurrent()) instrumentDocuments.cancel()
            if (!instrumentDocuments.picked(revision)) {
                ui.instrumentDocumentBusy = instrumentDocuments.busy
                return@registerForActivityResult
            }
            if (uri == null) {
                instrumentDocuments.finish(revision)
                ui.instrumentDocumentBusy = false
                ui.instrumentStatus = "Import cancelled. Collection unchanged."
                return@registerForActivityResult
            }
            instrumentDocumentWork(revision, "instrument-document-read") {
                val decoded = runCatching {
                    val stream = contentResolver.openInputStream(uri) ?: error("Choose a readable instrument document")
                    stream.use(InstrumentDocuments::read)
                }
                tick.post {
                    val accepted = instrumentDocuments.finish(revision)
                    if (!activityDestroyed) ui.instrumentDocumentBusy = instrumentDocuments.busy
                    if (accepted && taskIsCurrent()) {
                        decoded.onSuccess { incoming ->
                            val base = instrumentStore.collection
                            if (base == null) ui.instrumentStatus = "Saved collection unavailable. Recover it before importing."
                            else {
                                ui.instrumentPreview = InstrumentImportPreview(base, incoming)
                                ui.instrumentChoices = emptyMap()
                                ui.instrumentStatus = "Preview checked. Choose an action for every record. Nothing has been imported or applied."
                            }
                        }.onFailure(::instrumentFailure)
                    }
                }
            }
        }

    private val createInstrumentDocument =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            val export = instrumentExport
            instrumentExport = null
            if (export == null) return@registerForActivityResult
            if (!taskIsCurrent()) instrumentDocuments.cancel()
            if (!instrumentDocuments.picked(export.first)) {
                ui.instrumentDocumentBusy = instrumentDocuments.busy
                return@registerForActivityResult
            }
            if (uri == null) {
                instrumentDocuments.finish(export.first)
                ui.instrumentDocumentBusy = false
                ui.instrumentStatus = "Export cancelled. No document written."
                return@registerForActivityResult
            }
            instrumentDocumentWork(export.first, "instrument-document-write") {
                val result = runCatching {
                    val output = contentResolver.openOutputStream(uri, "wt") ?: error("Choose a writable document")
                    output.use { it.write(export.second.toByteArray(Charsets.UTF_8)) }
                }
                tick.post {
                    val accepted = instrumentDocuments.finish(export.first)
                    if (!activityDestroyed) ui.instrumentDocumentBusy = instrumentDocuments.busy
                    if (accepted && taskIsCurrent()) {
                        ui.instrumentStatus = if (result.isSuccess) "Instrument presets exported. Tuning unchanged."
                            else "Export failed. The selected document may be incomplete. Choose another document and retry."
                    }
                }
            }
        }

    override fun importInstrumentPresets() {
        if (!taskIsCurrent() || ui.instrumentDocumentBusy || instrumentImportRevision != null || instrumentExport != null) return
        if (instrumentStore.collection == null) return
        ui.instrumentPreview = null
        ui.instrumentChoices = emptyMap()
        instrumentImportRevision = instrumentDocuments.begin() ?: return
        ui.instrumentDocumentBusy = true
        ui.instrumentStatus = "Choose an Instrument presets document for an inert preview."
        runCatching { openInstrumentDocument.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }
            .onFailure {
                instrumentImportRevision?.let(instrumentDocuments::finish)
                instrumentImportRevision = null
                ui.instrumentDocumentBusy = false
                instrumentFailure(it)
            }
    }

    override fun exportInstrumentPresets(id: String?) {
        if (!taskIsCurrent() || ui.instrumentDocumentBusy || instrumentImportRevision != null || instrumentExport != null) return
        runCatching {
            val collection = checkNotNull(instrumentStore.collection) { "Recover saved collection before exporting" }
            val json = if (id == null) InstrumentPresetCodec.encode(collection) else InstrumentPresetCodec.exportRecord(collection, id)
            instrumentExport = (instrumentDocuments.begin() ?: return) to json
            ui.instrumentDocumentBusy = true
            ui.instrumentStatus = "Choose where to export Instrument presets."
            createInstrumentDocument.launch("Instrument presets.phospresets")
        }.onFailure {
            instrumentExport?.first?.let(instrumentDocuments::finish)
            instrumentExport = null
            ui.instrumentDocumentBusy = false
            instrumentFailure(it)
        }
    }

    override fun chooseInstrumentImport(id: String, choice: InstrumentImportChoice) {
        val preview = ui.instrumentPreview ?: return
        if (preview.incoming.records.none { it.id == id }) return
        ui.instrumentChoices = ui.instrumentChoices + (id to choice)
    }
    override fun commitInstrumentImport() {
        val preview = ui.instrumentPreview ?: return
        val choices = ui.instrumentChoices
        changeInstruments("Imported saved records. No tuning applied.") { it.resolveImport(preview, choices) }
        if (instrumentStore.error.isEmpty()) {
            ui.instrumentPreview = null
            ui.instrumentChoices = emptyMap()
        }
    }
    override fun cancelInstrumentImport() {
        instrumentDocuments.cancel()
        ui.instrumentPreview = null
        ui.instrumentChoices = emptyMap()
        ui.instrumentDocumentBusy = instrumentDocuments.busy
        ui.instrumentStatus = if (instrumentDocuments.busy)
            "Cancelled. Waiting for the document provider to return before another operation. An export already writing may leave a document."
            else "Preview cancelled. Collection and tuning unchanged."
    }

    private fun reportImportFailure(error: Throwable) {
        ui.settingsTransferStatus = when (error) {
            is SettingsArchive.ArchiveException -> "${error.error} · ${error.fix}"
            else -> "import failed · ${error.message ?: "choose another archive"}"
        }
    }

    private fun reportTuningWriteFailure(failure: dev.phosphor.mobil3.settings.SettingsWriteOwner.Failure): String {
        if (!failure.restored) tick.removeCallbacks(persistGain)
        appearanceWorkflow?.persistenceFailed(failure)
        val message = if (failure.restored) failure.message() else
            "${failure.stage} failed and rollback could not be confirmed. Open INSTRUMENT PRESETS and use RETRY SAVE CURRENT."
        instrumentWorkflow?.persistenceFailed(InstrumentWorkflow.PersistenceFailure(message, failure.restored))
        return message
    }

    private fun staleSettingsImport() {
        ui.settingsTransferStatus = "Settings changed while the document was open. Nothing imported. Choose the archive again to review current settings."
    }

    private fun acceptSettingsArchive(decoded: SettingsArchive.ImportResult) {
        if (isFinishing || isDestroyed) return
        instrumentWorkflow?.settle("Apply superseded by settings import.")
        if (instrumentWorkflow?.editsBlocked == true) {
            ui.settingsTransferStatus = "Instrument state is uncertain. Recover it in INSTRUMENT PRESETS before importing settings."
            return
        }
        instrumentWorkflow?.keepSafe()
        runCatching {
            settingsWriteOwner.write {
                val merged = AppearanceMigration.merge(SettingsArchive.merge(decoded, prefs().all), prefs().all)
                val guard = if (merged.keys.any(LightSettings.keys::contains)) {
                    LightCycleGuard.evaluate(LightSettings.read(prefs().all + merged), epilepsyAcknowledged())
                } else null
                val imported = decoded.copy(values = if (guard == null) merged else
                    merged.filterKeys { it !in LightSettings.keys } + guard.safe.values())
                val priorValues = preferenceValueSnapshots(prefs().all, imported.values.keys)
                val editor = prefs().edit()
                imported.values.forEach { (key, value) ->
                    when (value) {
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Float -> editor.putFloat(key, value)
                        is String -> editor.putString(key, value)
                        else -> error("unsupported imported preference type for $key")
                    }
                }
                settingsWriteOwner.commit(
                    commit = { editor.commit() },
                    publish = { guard == null || publishNativeLight(guard.safe) },
                    rollback = { restorePreferenceSnapshots(priorValues) },
                )?.let { error(reportTuningWriteFailure(it)) }
                if (guard != null && prefs().contains("custom_count")) prefs().edit().remove("custom_count").commit()
                Triple(imported, guard?.pending, guard != null)
            }
        }.onSuccess { (imported, pendingLight, lightPublished) ->
            // Same main-thread callback, before another event can publish a later revision.
            if (AppearanceMigration.KEY in imported.values) {
                appearanceWorkflow?.imported(checkNotNull(AppearanceMigration.stored(imported.values)))
            }
            if (AutoFramePreference.KEY in imported.values) autoFrameSave.restored()
            restoreTuning(lightPublished)
            if (ForegroundBrightnessPolicy.KEY in imported.values) ui.brightnessPinError = ""
            instrumentWorkflow?.externalRestoreSaved()
            refreshInstrumentState()
            ui.lightPending = pendingLight
            applyScopeRotationPreference()
            applyImmersive()
            updatePictureInPictureParams()
            ui.settingsTransferStatus = buildString {
                if (pendingLight != null) append("Rapid timing kept safe. Open LIGHT to review. ")
                append("imported ${imported.values.size}")
                append(" from ")
                append(imported.sourceVersion)
                if (imported.skippedKeys.isNotEmpty()) append(" · skipped ${imported.skippedKeys.size} newer fields")
            }
        }.onFailure(::reportImportFailure)
    }

    private val openSettingsArchive =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val ticket = pendingSettingsImport ?: return@registerForActivityResult
            val appearanceTicket = pendingAppearanceImport
            pendingSettingsImport = null
            pendingAppearanceImport = null
            val owner = instrumentWorkflow ?: return@registerForActivityResult
            if (uri == null) {
                owner.cancelSettingsImport(ticket)
                ui.settingsTransferStatus = "Import cancelled. Settings unchanged."
                return@registerForActivityResult
            }
            if (appearanceTicket == null || appearanceWorkflow?.accepts(appearanceTicket) != true ||
                !owner.settingsImportPicked(ticket)) {
                owner.cancelSettingsImport(ticket)
                staleSettingsImport()
                return@registerForActivityResult
            }
            ui.settingsTransferStatus = "checking settings…"
            runCatching { Thread {
                runCatching {
                    val input = contentResolver.openInputStream(uri)
                        ?: error("Android did not provide a readable document")
                    val text = input.use { stream ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(16 * 1024)
                        var total = 0
                        while (true) {
                            val count = stream.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > SettingsArchive.MAX_BYTES) {
                                throw SettingsArchive.ArchiveException(
                                    "archive_too_large",
                                    "Settings archive exceeds ${SettingsArchive.MAX_BYTES} bytes",
                                    "Choose an original .phossettings export",
                                )
                            }
                            output.write(buffer, 0, count)
                        }
                        output.toString(Charsets.UTF_8.name())
                    }
                    SettingsArchive.decode(text)
                }.onSuccess { decoded ->
                    runOnUiThread {
                        if (isFinishing || isDestroyed) {
                            owner.cancelSettingsImport(ticket)
                            return@runOnUiThread
                        }
                        runCatching {
                            if (appearanceWorkflow?.accepts(appearanceTicket) != true) {
                                owner.cancelSettingsImport(ticket)
                                false
                            } else owner.finishSettingsImport(ticket) { acceptSettingsArchive(decoded) }
                        }.onSuccess { accepted -> if (!accepted) staleSettingsImport() }
                            .onFailure(::reportImportFailure)
                    }
                }.onFailure { error ->
                    runOnUiThread {
                        owner.cancelSettingsImport(ticket)
                        if (!isFinishing && !isDestroyed) reportImportFailure(error)
                    }
                }
            }.start() }.onFailure {
                owner.cancelSettingsImport(ticket)
                reportImportFailure(it)
            }
        }

    private val captureConsent =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val selection = pendingCaptureConsent
            pendingCaptureConsent = null
            if (!taskIsCurrent() || selection == null || selection != sourceSelection) return@registerForActivityResult
            val data = result.data
            if (result.resultCode != android.app.Activity.RESULT_OK || data == null) {
                applyCaptureStatus(
                    CaptureService.CaptureStatus.permissionNeeded(
                        "capture permission not granted",
                        "Choose everything playing and approve Android's capture prompt",
                    )
                )
                return@registerForActivityResult
            }
            withSourcesReleased {
                applyLocalGainPolicy()
                applyCaptureStatus(CaptureService.CaptureStatus.starting())
                mixStartPending = CaptureMixSettings.read(prefs().all).include
                runCatching {
                    startCaptureService(
                        Intent(this, CaptureService::class.java).putExtra(CaptureService.EXTRA_RESULT, data)
                    )
                }.onFailure {
                    applyCaptureStatus(CaptureService.CaptureStatus.error(
                        "capture service could not start",
                        "Return to Phosphor and approve Android's foreground capture prompt again",
                    ))
                }
            }
        }

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!taskIsCurrent()) return@registerForActivityResult
            val purpose = pendingAudioPermission
            pendingAudioPermission = AudioPermissionPurpose.NONE
            if (!granted) {
                if (purpose != AudioPermissionPurpose.NONE) signalDenied = "Microphone permission not granted"
                if (purpose == AudioPermissionPurpose.PLAYBACK_CAPTURE) {
                    applyCaptureStatus(
                        CaptureService.CaptureStatus.permissionNeeded(
                            "microphone permission not granted",
                            "Grant microphone access so Android can provide playback audio",
                        )
                    )
                }
                return@registerForActivityResult
            }
            when (purpose) {
                AudioPermissionPurpose.MICROPHONE -> startMic()
                AudioPermissionPurpose.MIX_MICROPHONE -> startMixMicrophone()
                AudioPermissionPurpose.PLAYBACK_CAPTURE -> launchCaptureConsent()
                AudioPermissionPurpose.NONE -> Unit
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingFreshStartup = StartupCoordinatorPolicy.processFreshLaunch(processStartupConsumed)
        processStartupConsumed = true
        taskRevision = BackgroundLifecycle.policy.enterActivity(taskId)
        activityRevision = BackgroundLifecycle.policy.activityRevision
        ui = ScopeUiState()
        FloatingHudService.observe(hudChanged)
        MicCaptureService.observe(micChanged)
        enableEdgeToEdge()
        // Visible flags mirror actual source owners. Idle chrome must remain sleep-eligible.
        reassertSourceWake()
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        reduced = readReducedMotion(this)
        PhosphorNative.setReducedMotion(reduced)
        ui.bindRandomModeRequest(::armAndRollRandomMode)
        initializeInstruments()
        restoreTuning()
        initializeAppearance()
        refreshCaptureMetadataAccess()
        applyScopeRotationPreference()
        // The scope starts immersive; an edge swipe can reveal system bars temporarily.
        applyImmersive()
        setContent { PhosphorScreen(ui, this, reduced) }
        // Post after layout so picture-in-picture receives a valid source rectangle.
        window.decorView.post { updatePictureInPictureParams() }
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        reduced = readReducedMotion(this)
        PhosphorNative.setReducedMotion(reduced)
        signalResumed = true
        activityResumed = true
        refreshMicrophone()
        maybeStartMixMicrophone()
        applyBrightnessPin()
        if (FloatingHudService.active) FloatingHudService.hide(this)
        refreshRotationAuthority(force = true)
        refreshCaptureMetadataAccess()
        if (taskIsCurrent() && pendingFreshStartup) {
            pendingFreshStartup = false
            maybeStartConfiguredDefault()
        }
    }

    override fun onPause() {
        activityResumed = false
        applyBrightnessPin()
        appearanceWorkflow?.cancel()
        signalResumed = false
        super.onPause()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        val leaving = ui.pip && !isInPictureInPictureMode
        ui.pip = isInPictureInPictureMode
        applyBrightnessPin()
        if (ui.pip) appearanceWorkflow?.cancel()
        tick.removeCallbacks(uiTick)
        if (PictureInPicturePolicy.shouldRebindSurface(leaving, activityStarted)) {
            SurfaceHost.rebindActivity()
            ui.presentationVisible = true
        }
        if (!ui.pip && activityStarted && ui.presentationVisible) tick.post(uiTick)
        updateOrientationSensor()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyBrightnessPin()
        refreshRotationAuthority(force = true)
        // The SurfaceView is still full-bleed and receives its new buffer dimensions
        // through surfaceChanged. Only PiP's advertised frame needs explicit refresh.
        updatePictureInPictureParams()
    }

    private fun updatePictureInPictureParams() {
        setPictureInPictureParams(pictureInPictureParams())
    }

    private fun pictureInPictureParams(): android.app.PictureInPictureParams {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val sourceRectHint = Rect()
        val hasSourceRectHint = window.decorView.getGlobalVisibleRect(sourceRectHint)
        val builder = android.app.PictureInPictureParams.Builder()
            .setAspectRatio(
                if (landscape) android.util.Rational(16, 9)
                else android.util.Rational(9, 16)
            )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(!hudConsentOpen && !FloatingHudService.active && PictureInPicturePolicy.platformAutoEnter(Build.VERSION.SDK_INT, ui.pipAutoEnter))
        }
        if (hasSourceRectHint) builder.setSourceRectHint(sourceRectHint)
        return builder.build()
    }

    override fun onUserLeaveHint() {
        if (!hudConsentOpen && !FloatingHudService.active && PictureInPicturePolicy.enterOnLeave(Build.VERSION.SDK_INT, ui.pipAutoEnter, isInPictureInPictureMode)) {
            enterPictureInPictureMode(pictureInPictureParams())
        }
        super.onUserLeaveHint()
    }

    override fun enterPictureInPicture() {
        if (!hudConsentOpen && !FloatingHudService.active && PictureInPicturePolicy.enterManually(isInPictureInPictureMode)) {
            enterPictureInPictureMode(pictureInPictureParams())
        }
    }

    override fun onStart() {
        super.onStart()
        activityStarted = true
        SurfaceHost.activityVisible(true)
        hudChanged()
        reassertSourceWake()
        val bindingRevision = controllerBinding.start()
        if (!captureStatusReceiverRegistered) {
            ContextCompat.registerReceiver(
                this,
                captureStatusReceiver,
                IntentFilter(CaptureService.ACTION_STATUS).apply { addAction(PlaybackService.ACTION_LOCAL_SOURCE_CHANGED) },
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            captureStatusReceiverRegistered = true
        }
        applyCaptureStatus(CaptureService.currentStatus())
        PlaybackService.localSourcePublication.current.let {
            if (it.source == LocalSourcePublication.Source.NONE ||
                it.source == LocalSourcePublication.Source.RELEASED_READERS) applyLocalSourcePublication(it.revision)
        }
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token)
            .setListener(object : MediaController.Listener {
                override fun onExtrasChanged(current: MediaController, extras: Bundle) {
                    if (taskIsCurrent() && controllerBinding.accepts(bindingRevision) && controller === current) {
                        ui.playing = sessionPlaying(current)
                        AcceptanceTrace.record("capture_observed_ui") {
                            "state=${extras.getInt(CaptureMirrorPolicy.OBSERVED_STATE)} ui=${ui.playing}"
                        }
                    }
                }
            }).buildAsync()
        future.addListener({
            if (!taskIsCurrent() || !controllerBinding.accepts(bindingRevision)) {
                runCatching { future.get().release() }
                return@addListener
            }
            val connected = runCatching { future.get() }.getOrElse {
                android.util.Log.w("PhosphorPlayback", "Media session unavailable, reopen Phosphor to reconnect", it)
                return@addListener
            }
            controller = connected.also { c ->
                c.addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) {
                        reassertSourceWake()
                        AcceptanceTrace.record("activity_player") {
                            "capture=${c.mediaMetadata.extras?.getString("source") == "capture"} " +
                                "state=${c.playbackState} ready=${c.playWhenReady} playing=${c.isPlaying} ui=${ui.playing}"
                        }
                    }
                    override fun onIsPlayingChanged(isPlaying: Boolean) { ui.playing = sessionPlaying(c) }
                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                        if (c.mediaMetadata.extras?.getString("source") == "capture") ui.playing = sessionPlaying(c)
                    }
                    override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
                        syncCaptureCommands(c)
                    }
                    // The relay's failures arrive here carrying the engine's fix text.
                    // Keeping the message means the REMOTE sheet can show a remedy instead
                    // of a dead end; a recovered link clears it.
                    override fun onPlayerErrorChanged(error: PlaybackException?) {
                        if (ui.remote) ui.remoteFailure = error?.message.orEmpty()
                    }
                    override fun onTimelineChanged(
                        t: androidx.media3.common.Timeline, reason: Int,
                    ) {
                        syncQueue(c)
                        syncSessionFace(c)
                        ui.playing = sessionPlaying(c)
                    }
                    override fun onMediaItemTransition(
                        item: androidx.media3.common.MediaItem?, reason: Int,
                    ) = syncQueue(c)
                    override fun onMediaMetadataChanged(m: MediaMetadata) {
                        syncSessionFace(c, m)
                        ui.playing = sessionPlaying(c)
                        // Track boundary: advance a per-song light cycle (engine ignores
                        // it unless per-track cycling is active).
                        surfaceHost?.metadataChanged(c)
                    }
                })
                // Initial sync: the world may have moved while the Activity slept
                // (earbud skips with the screen off) — mirror the session's truth now,
                // not just on the next change event.
                ui.playing = sessionPlaying(c)
                syncSessionFace(c)
                surfaceHost?.metadataChanged(c)
            }
        }, MoreExecutors.directExecutor())
        tick.removeCallbacks(uiTick)
        if (ui.presentationVisible) tick.post(uiTick)
    }

    override fun onStop() {
        appearanceWorkflow?.cancel()
        instrumentWorkflow?.settle("Activity stopped. Pending apply cancelled.")
        activityStarted = false
        activityResumed = false
        applyBrightnessPin()
        ui.presentationVisible = false
        updateOrientationSensor()
        SurfaceHost.activityVisible(false)
        reassertSourceWake()
        controllerBinding.cancel()
        finishAutoFrameScale()
        saveTuning()
        tick.removeCallbacks(uiTick)
        if (captureStatusReceiverRegistered) {
            unregisterReceiver(captureStatusReceiver)
            captureStatusReceiverRegistered = false
        }
        controller?.release()
        controller = null
        super.onStop()
    }

    private fun sessionPlaying(player: MediaController): Boolean = CaptureMirrorPolicy.displayedPlaying(
        capture = player.mediaMetadata.extras?.getString("source") == "capture",
        isPlaying = player.isPlaying,
        observedState = player.sessionExtras.getInt(CaptureMirrorPolicy.OBSERVED_STATE),
    )

    override fun onDestroy() {
        appearanceWorkflow?.close()
        instrumentWorkflow?.close()
        instrumentDocuments.close()
        tick.removeCallbacks(persistGain)
        tick.removeCallbacks(persistAutoFrame)
        activityDestroyed = true
        activityResumed = false
        applyBrightnessPin()
        FloatingHudService.unobserve(hudChanged)
        surfaceHost?.close()
        surfaceHost = null
        val retiredGravityListener = gravityListener
        gravityListener = null
        retiredGravityListener?.let {
            getSystemService(android.hardware.SensorManager::class.java)?.unregisterListener(it)
        }
        MicCaptureService.unobserve(micChanged)
        activityStarted = false
        reassertSourceWake()
        scopeSurface = null
        controllerBinding.cancel()
        selectSource()
        BackgroundLifecycle.policy.leaveActivity(activityRevision)
        super.onDestroy()
    }

    // One gentle heartbeat for display facts Compose can't observe directly:
    // seek position, resting-beam flag and breathing accent.
    private var baseRoom: Palette? = null
    private var lastRxBytes = 0L
    private val uiTick = object : Runnable {
        override fun run() {
            if (!activityStarted || !ui.presentationVisible || ui.pip || activityDestroyed) return
            refreshRotationAuthority()
            reassertSourceWake()
            refreshRootState()
            if (android.os.SystemClock.elapsedRealtime() - micRefreshAt >= 500) refreshMicrophone()
            controller?.let { c ->
                val dur = c.duration
                ui.seekable = !ui.remote && dur > 0 &&
                    c.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                ui.durationMs = if (dur > 0) dur else 0L
                ui.positionMs = c.currentPosition.coerceAtLeast(0L)
            }
            ui.noSignal = PhosphorNative.scopeSilent()
            ui.hdrStatus = runCatching { PhosphorNative.hdrObservation() }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: ui.hdrStatus
            val rs = if (ui.remote) runCatching {
                org.json.JSONObject(PhosphorNative.remoteStatus())
            }.getOrNull() else null
            val remoteScope = rs?.optJSONObject("scope")
            val remoteGain = remoteScope?.optJSONObject("gain")
            // Show measured renderer and relay gain rather than saved preference values.
            if (!ui.remoteGeometry && instrumentWorkflow?.pending != true) ui.gain = PhosphorNative.gainNow()
            if (ui.remote && ui.remoteGeometry) {
                remoteGain?.let { ui.autoGain = it.optBoolean("auto", false) }
            } else {
                ui.autoGain = ui.localAutoGain
            }
            // When the desktop supplies geometry, show its measured mode and gain.
            ui.remoteScopeLine = if (ui.remote && ui.remoteGeometry) {
                remoteScope?.let { sc ->
                    val mode = sc.optString("mode", "—")
                    val g = sc.optJSONObject("gain")
                    when {
                        g == null -> "$mode · pc"
                        g.optBoolean("auto") ->
                            "%s · auto ×%.2f · pc".format(mode, g.optDouble("effective", 0.0))
                        else -> "%s · ×%.2f · pc".format(mode, g.optDouble("effective", 0.0))
                    }
                }
            } else null
            refreshDisplayPause()
            val stats = if (dev.phosphor.mobil3.ui.GridData.needsStats(ui.hudMode, ui.gridData)) {
                runCatching {
                    org.json.JSONObject(PhosphorNative.scopeStats())
                }.getOrNull()
            } else null
            ui.gridReading = dev.phosphor.mobil3.ui.GridData.read(stats)
            AcceptanceTrace.record("framing_sample") {
                val raw = ui.gridReading
                "source_revision=$sourceSelection auto=${ui.autoGain} frame_scale=$autoFrameScale " +
                    "effective_gain=${ui.gain} raw_left=${raw?.left} raw_right=${raw?.right} " +
                    "mode=${ui.modeIndex} remote_geometry=${ui.remoteGeometry}"
            }
            if (signalRefresh.take(android.os.SystemClock.elapsedRealtime(), ui.signalCheckVisible,
                    signalResumed && activityStarted && !activityDestroyed, signalFocused,
                    ui.presentationVisible && !hudConsentOpen, ui.pip)) refreshSignalCheck()
            if (ui.hudMode != 2) {
                val rx = rs?.optLong("rx_bytes") ?: 0L
                val mbps = if (lastRxBytes in 1 until rx) {
                    (rx - lastRxBytes) * 8f * 2f / 1_000_000f // 500 ms tick → per-second
                } else 0f
                lastRxBytes = rx
                ui.hudLine = buildString {
                    append("%.1f fps".format(stats?.optDouble("fps") ?: 0.0))
                    append(" · ${stats?.optInt("segs") ?: 0} segs")
                    if (ui.remote) append(" · %.1f Mb/s".format(mbps))
                }
                // Bridge health (the hardening made visible): live buffer depth,
                // catch-up skips, channel drops, and the leak counter that must
                // stay zero. All real numbers from the session's atomics.
                ui.hudLine2 = if (rs != null) buildString {
                    append("bridge · buf ${rs.optInt("audio_buf_ms")} ms")
                    append(" · tgt ${rs.optInt("audio_target_ms")} ms")
                    append(" · und ${rs.optInt("audio_underruns")}")
                    append(" · skip ${rs.optInt("audio_skips")}")
                    val skipMs = rs.optInt("audio_skip_ms")
                    if (skipMs > 0) append(" (${skipMs} ms)")
                    append(" · drop ${rs.optInt("a_drops")}")
                    val leaked = rs.optInt("leaked_threads")
                    if (leaked > 0) append(" · LEAK $leaked")
                } else ""
            }
            // Moving chrome accents follow the measured beam color; structural accents opt in per room.
            val base = baseRoom ?: ui.room
            run {
                val rgb = PhosphorNative.beamColorNow()
                val beam = floatArrayOf(
                    ((rgb shr 16) and 0xff) / 255f,
                    ((rgb shr 8) and 0xff) / 255f,
                    (rgb and 0xff) / 255f,
                )
                // beamColorNow is already gamma-encoded; withBeam lifts linear — feed it
                // the linearized value so the lift round-trips.
                fun lin(v: Float) = Math.pow(v.toDouble(), 2.2).toFloat()
                ui.room = base.withBeam(floatArrayOf(lin(beam[0]), lin(beam[1]), lin(beam[2])))
            }
            tick.postDelayed(this, 500)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(FloatingHudService.RETURN, false)) {
            FloatingHudService.hide(this)
            ui.showSourcePicker = intent.getBooleanExtra(FloatingHudService.SOURCE, false)
            return
        }
        selectSource()
        taskRevision = BackgroundLifecycle.policy.enterActivity(taskId)
        activityRevision = BackgroundLifecycle.policy.activityRevision
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.getBooleanExtra(FloatingHudService.RETURN, false)) {
            FloatingHudService.hide(this)
            ui.showSourcePicker = intent.getBooleanExtra(FloatingHudService.SOURCE, false)
            return
        }
        intent.getStringExtra("open")?.let { openDeck(it) }
        if (intent.getBooleanExtra("capture", false)) startCapture()
        if (intent.getBooleanExtra("remote", false)) startRemote()
    }

    // Direct documents use the same serial staging and validation path as tree entries.
    private fun loadUri(uri: Uri) {
        selectSource(SignalKind.LOCAL)
        runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        applyLocalGainPolicy()
        startSourceService(Intent(this, PlaybackService::class.java)
            .setAction(PlaybackService.ACTION_OPEN_DOCUMENT)
            .putExtra(PlaybackService.EXTRA_DOCUMENT_URI, uri.toString()))
    }

    // Mirror the session's timeline into the deck sheet's queue rows (ghost items from
    // the remote deck are filtered by their reserved ids).
    private fun syncQueue(c: MediaController) {
        val n = c.mediaItemCount
        val titles = mutableListOf<String>()
        var remoteGhosts = false
        for (i in 0 until n) {
            val item = c.getMediaItemAt(i)
            if (item.mediaId.startsWith("remote:")) { remoteGhosts = true; break }
            titles += item.mediaMetadata.title?.toString() ?: "track ${i + 1}"
        }
        ui.queueTitles = if (remoteGhosts) emptyList() else titles
        ui.queueIndex = c.currentMediaItemIndex.coerceAtLeast(0)
    }

    /** The one MediaSession is the source of truth for every visible now-playing face. */
    private fun syncSessionFace(c: MediaController, metadata: MediaMetadata = c.mediaMetadata) {
        val nextInstrumentSource = metadata.extras?.getString("source")
            ?: c.currentMediaItem?.mediaId?.takeIf { it.startsWith("q") }?.let { "local" }
        if (instrumentSource != nextInstrumentSource) {
            instrumentWorkflow?.settle("Source capability changed. Pending apply cancelled.")
            instrumentSource = nextInstrumentSource
        }
        syncCaptureCommands(c)
        acceptTrackTitle(metadata.title?.toString())
        ui.trackArtist = metadata.artist?.toString()
        ui.artwork = metadata.artworkData?.takeIf { it.isNotEmpty() }
        val source = metadata.extras?.getString("source")
            ?: c.currentMediaItem?.mediaId?.takeIf { it.startsWith("q") }?.let { "local" }
        ui.remote = source == "remote"
        when (source) {
            "local" -> {
                ui.sourceLabel = "deck"
                ui.live = false
                ui.remote = false
            }
            "capture" -> {
                ui.sourceLabel = "capture"
                ui.live = true
            }
            "remote" -> {
                ui.live = false
                val conn = metadata.extras?.getString("conn")
                val host = metadata.extras?.getString("host") ?: "remote"
                ui.sourceLabel = when (conn) {
                    "CONNECTING" -> "remote · connecting…"
                    // A welcome frame alone is not a live media link.
                    "GREETED" -> "remote · waiting for audio"
                    // The relay says it is sending silence. Without this the user sees a
                    // dark scope and cannot tell whether the desktop is quiet or the link
                    // is broken.
                    "SILENT" -> "remote · $host · no sound"
                    // Frozen socket, not a dropped one. Acceptance H-04 forbids showing a
                    // frozen live trace, and this is what stops that happening.
                    "STALLED" -> "remote · signal stalled"
                    "LOST" -> "remote · reconnecting…"
                    "FAILED" -> "remote · unreachable"
                    else -> "remote · $host"
                }
            }
            else -> if (ui.sourceLabel == "capture" && c.mediaItemCount == 0) {
                // Capture ended (including a system MediaProjection stop): clear the
                // entire face now. No old title or art may survive into "no source".
                ui.sourceLabel = "no source"
                ui.live = false
            }
        }
    }

    private fun syncCaptureCommands(player: Player) {
        val capture = player.mediaMetadata.extras?.getString("source") == "capture"
        ui.captureCanPlay = capture && player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)
        ui.captureCanNext = capture && player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        ui.captureCanPrevious = capture && player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
    }

    private fun openDeck(path: String) {
        selectSource(SignalKind.LOCAL)
        applyLocalGainPolicy()
        startSourceService(Intent(this, PlaybackService::class.java).putExtra(PlaybackService.EXTRA_OPEN, path))
    }

    private fun micHandoffCancel() {
        micHandoff.cancel()
        mic.cancelStart()
        micReleaseRevision = null
    }

    /** Observation only. Existing owners and one already-acquired display meter are the authorities. */
    private fun refreshSignalCheck() {
        val selection = sourceSelection
        val publication = PlaybackService.localSourcePublication.current.revision
        val now = android.os.SystemClock.elapsedRealtime()
        val capture = CaptureService.signalObservation()
        val microphone = mic.signalObservation()
        val playback = PlaybackService.signalObservation()
        val native = runCatching { org.json.JSONObject(PhosphorNative.signalObservation()) }.getOrNull()
        val selected = when {
            signalSelected != SignalKind.UNKNOWN -> signalSelected
            ui.remote -> SignalKind.RELAY
            ui.sourceLabel == "mic" -> SignalKind.MIC
            ui.sourceLabel.startsWith("capture") -> if (ui.captureRoot) SignalKind.ROOT else SignalKind.CAPTURE
            PlaybackService.ownsLocal() -> SignalKind.LOCAL
            else -> SignalKind.NONE
        }
        val currentCapture = capture?.takeIf { it.contributing || it.kind == selected }
        val currentMic = microphone?.takeIf { it.contributing || selected == SignalKind.MIC }
        var input = when (selected) {
            SignalKind.MIC -> currentMic ?: currentCapture
            SignalKind.CAPTURE, SignalKind.ROOT -> currentCapture ?: currentMic
            SignalKind.LOCAL -> signalNative.local(native, playback, now)
            SignalKind.RELAY -> signalNative.relay(native, playback, now)
            else -> currentMic?.takeIf { it.contributing } ?: currentCapture?.takeIf { it.contributing }
        }
        val localCapture = signalCaptureStatus?.takeIf { it.ownerId == 0L && selected in setOf(SignalKind.CAPTURE, SignalKind.ROOT) }
        if (localCapture != null) input = SignalInput(selected, 0, life = when (localCapture.state) {
            CaptureService.STATE_ERROR -> SignalLife.FAILED
            CaptureService.STATE_PERMISSION_NEEDED -> SignalLife.PERMISSION
            else -> SignalLife.STARTING
        }, reason = localCapture.message)
        signalDenied?.let { input = SignalInput(selected, 0, life = SignalLife.PERMISSION, reason = it) }
        val ownerCurrent = when (input?.kind) {
            SignalKind.MIC -> input?.owner == 0L || mic.signalObservation()?.session == input?.session
            SignalKind.CAPTURE, SignalKind.ROOT -> input?.owner == 0L || CaptureService.signalObservation()?.let {
                it.owner == input?.owner && it.session == input?.session
            } == true
            else -> true
        }
        val current = selection == sourceSelection && PlaybackService.localSourcePublication.accepts(publication) && ownerCurrent
        val detail = signalNative.details(native, input, playback, now).toMutableList()
        detail += "Selection revision" to "$selection · service source revision $publication"
        CaptureService.mixDescription()?.let { detail += "Visualization mixer" to it }
        if (selected == SignalKind.CAPTURE && microphone != null) {
            detail += "Microphone contribution" to if (microphone.contributing) "Included" else "Unavailable or off · playback remains independent"
            detail += "Microphone health" to SignalPresentation.primary(SignalKind.MIC, microphone, now)
            detail += "Microphone route / device" to MicCaptureService.status()
            microphone.descriptor.format?.let { detail += "Microphone client format" to it.label() }
            microphone.descriptor.route?.let { detail += "Microphone actual route" to it }
        }
        if (selected == SignalKind.MIC) detail += "Microphone route / device" to MicCaptureService.status()
        detail += "Scope tap peak" to "Unavailable · the existing display tap has no owner and measurement-age receipt. No second tap is consumed."
        ui.signalCheck = SignalPresentation.present(selected, input, now,
            SignalDisplay(ui.displayPaused, ui.pauseBlack, ui.heldFrameAvailable, ui.displayPresentPending),
            current, pendingCaptureConsent == selection || pendingAudioPermission != AudioPermissionPurpose.NONE, detail)
    }

    private fun selectSource(signalKind: SignalKind = SignalKind.UNKNOWN): Long {
        signalSelected = signalKind
        signalDenied = null
        signalCaptureStatus = null
        mixStartPending = false
        ++micUiRevision
        ui.micBluetoothExplain = false
        instrumentWorkflow?.settle("Source changed. Pending apply cancelled.")
        micHandoffCancel()
        pendingAudioPermission = AudioPermissionPurpose.NONE
        pendingCaptureConsent = null
        return ++sourceSelection
    }

    private fun micRequestIsCurrent(): Boolean = taskIsCurrent() && !isDestroyed &&
        micReleaseRevision?.let { PlaybackService.localSourcePublication.accepts(it) } == true

    private fun withSourcesReleased(
        selection: Long = selectSource(),
        micRequest: String? = null,
        start: () -> Unit,
    ) {
        if (!taskIsCurrent()) return
        val reply = object : ResultReceiver(tick) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                val revision = resultData?.getLong(PlaybackService.EXTRA_SOURCE_REVISION, -1) ?: -1
                if (resultCode == 0 && taskIsCurrent() && selection == sourceSelection && !isDestroyed &&
                    PlaybackService.localSourcePublication.accepts(revision)) {
                    if (micRequest != null) micReleaseRevision = revision
                    start()
                }
            }
        }
        startSourceService(Intent(this, PlaybackService::class.java)
            .setAction(PlaybackService.ACTION_RELEASE_LOCAL)
            .putExtra(CaptureService.EXTRA_MIC_REQUEST, micRequest)
            .putExtra(PlaybackService.EXTRA_RELEASE_REPLY, reply))
    }

    // ---- ScopeActions ----
    override fun showFloatingHud() {
        val access = Settings.canDrawOverlays(this)
        val locked = getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked
        val microphone = (mic.ownsSource() && !mic.established()) || micHandoff.isPending ||
            pendingAudioPermission in setOf(AudioPermissionPurpose.MICROPHONE, AudioPermissionPurpose.MIX_MICROPHONE)
        val refusal = HudPolicy.refusal(true, activityStarted && taskIsCurrent() && !isInPictureInPictureMode,
            access, locked, microphone)
        if (refusal != null) {
            ui.floatingHudStatus = refusal
            if (!access && activityStarted && taskIsCurrent() && !locked && !microphone && !isInPictureInPictureMode) {
                hudConsentOpen = true
                updatePictureInPictureParams()
                runCatching { overlaySettingsLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri())) }
                    .onFailure {
                        hudConsentOpen = false
                        updatePictureInPictureParams()
                        ui.floatingHudStatus = "Overlay settings unavailable. Open Android settings for Phosphor"
                    }
            }
            return
        }
        setFloatingHudEnabled(true)
        FloatingHudService.show(this)
    }

    override fun hideFloatingHud() { FloatingHudService.hide(this) }

    override fun setFloatingHudEnabled(on: Boolean) {
        prefs().edit { putBoolean(HudPolicy.ENABLED, on) }
        ui.floatingHudEnabled = on
        if (!on) FloatingHudService.hide(this)
    }

    override fun setFloatingHudTransparent(on: Boolean) {
        prefs().edit { putString(HudPolicy.BACKGROUND, if (on) "TRANSPARENT" else "SOLID") }
        ui.floatingHudTransparent = on
        ui.floatingHudStatus = "${if (on) "TRANSPARENT" else "SOLID"} requested for next Show"
    }

    override fun makeSurface(): SurfaceView {
        val host = SurfaceHost(this, status = { ui.floatingHudStatus = it },
            failed = { android.widget.Toast.makeText(this, ui.floatingHudStatus, android.widget.Toast.LENGTH_LONG).show() },
            presented = { controller?.let { surfaceHost?.metadataChanged(it) } })
        surfaceHost?.close()
        surfaceHost = host
        SurfaceHost.activity(host)
        return host.view.apply {
            scopeSurface?.keepScreenOn = false
            scopeSurface = this
            applyBrightnessPin()
        }
    }

    private fun brightnessPinActive(): Boolean = ::ui.isInitialized && ForegroundBrightnessPolicy.active(
        ui.pinScreenBrightness, ForegroundBrightnessPolicy.WindowState(
            started = activityStarted, resumed = activityResumed, focused = activityFocused,
            current = taskIsCurrent(), destroyed = activityDestroyed,
            pip = ui.pip || isInPictureInPictureMode, hud = FloatingHudService.presenting,
        ),
    )

    private fun applyBrightnessPin() {
        if (!::ui.isInitialized) return
        val active = brightnessPinActive()
        val requested = ForegroundBrightnessPolicy.brightness(active)
        val attributes = window.attributes
        if (attributes.screenBrightness != requested) {
            attributes.screenBrightness = requested
            window.attributes = attributes
        }
        ui.brightnessPinActive = active
        reassertSourceWake()
    }

    override fun setPinScreenBrightness(on: Boolean) {
        if (!taskIsCurrent()) return
        ui.pinScreenBrightness = on
        applyBrightnessPin()
        val saved = runCatching { prefs().edit().putBoolean(ForegroundBrightnessPolicy.KEY, on).commit() }.getOrDefault(false)
        ui.brightnessPinError = if (saved) "" else
            "Brightness choice applies now but saving failed. Toggle it again to retry before closing Phosphor."
    }

    override fun setDefaultSource(kind: String) {
        if (!taskIsCurrent()) return
        val value = kind.takeIf { it in StartupCoordinatorPolicy.allowed } ?: "none"
        ui.defaultSource = value
        runCatching { prefs().edit().putString(StartupCoordinatorPolicy.DEFAULT, value).commit() }
        if (taskIsCurrent()) runtimePrefs().edit { putBoolean(StartupCoordinatorPolicy.CONFIRMED, value != "none") }
    }

    override fun setAutomaticPermissionPopup(on: Boolean) {
        if (!taskIsCurrent()) return
        ui.automaticPermissionPopup = on
        runCatching { prefs().edit().putBoolean(StartupCoordinatorPolicy.POPUP, on).commit() }
    }

    override fun setHdrRequested(on: Boolean) {
        if (!taskIsCurrent()) return
        ui.hdrRequested = on
        ui.hdrStatus = HdrPresentationPolicy.reason(on, false, android.os.Build.VERSION.SDK_INT, false)
        PhosphorNative.setHdrRequested(on, android.os.Build.VERSION.SDK_INT)
        runCatching { prefs().edit().putBoolean(HdrPresentationPolicy.KEY, on).commit() }
    }

    private fun reassertSourceWake() {
        val sourceAwake = SourceWakePolicy.visible(
            started = activityStarted && !activityDestroyed,
            sourceLive = MicCaptureService.hasLiveWakeSource() || PlaybackService.hasLiveWakeSource() || CaptureService.hasLiveWakeSource(),
        )
        val awake = ForegroundBrightnessPolicy.awake(sourceAwake, brightnessPinActive())
        scopeSurface?.keepScreenOn = awake
        if (awake) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // The transport law, unified: ALL transport goes through the one MediaController —
    // the session's player routes to the local deck or the bridge. Notification, lock
    // screen, earbuds and the console are therefore the same code path.
    private fun refreshDisplayPause() {
        val state = PhosphorNative.displayPauseState()
        ui.displayPaused = state and 1 != 0
        ui.displayPresentPending = state and 8 != 0
        ui.pauseBlack = state and 2 != 0
        ui.heldFrameAvailable = state and 4 != 0
        ui.pauseSourceLive = ui.playing || mic.isRecording() ||
            (CaptureService.ownsCapture() && CaptureService.currentStatus().live)
    }
    override fun toggleDisplayPause() {
        PhosphorNative.setDisplayPaused(PhosphorNative.displayPauseState() and 1 == 0)
        refreshDisplayPause()
    }
    override fun resetInspection() { PhosphorNative.inspectHeld(0f, 0f, 1f, true) }
    override fun setPauseBlack(black: Boolean) {
        prefs().edit { putString("pause_display", if (black) "BLACK" else "HOLD") }
        PhosphorNative.setPauseBlack(black)
        refreshDisplayPause()
    }
    override fun togglePlay() {
        if (dev.phosphor.mobil3.ui.PauseDisplayPolicy.displayOnly(ui.live, ui.captureCanPlay)) {
            toggleDisplayPause(); return
        }

        val c = controller
        if (c != null) {
            val playing = if (c.mediaMetadata.extras?.getString("source") == "capture") sessionPlaying(c) else c.playWhenReady
            if (playing) c.pause() else c.play()
        }
        else ui.playing = PhosphorNative.deckToggle() // no session yet (nothing loaded)
    }

    override fun next() { controller?.seekToNext() }

    override fun prev() { controller?.seekToPrevious() }

    override fun seekTo(ms: Long) {
        if (!ui.remote) controller?.seekTo(ms)
    }

    override fun startRemote() {
        val first = remoteHosts().firstOrNull() ?: return
        startRemoteHost(first.first, first.second.first, first.second.second)
    }

    // The relay hosts the phone knows. Seeded once from BuildConfig (a build-machine
    // fact, never source) and thereafter owned by the user, so the Play distribution,
    // which compiles an empty seed, can still reach a desktop the user runs.
    private val remoteHostStore by lazy {
        RemoteHostStore(this, "")
    }

    override fun remoteHosts(): List<Pair<String, Pair<String, Int>>> =
        remoteHostStore.hosts().map { it.label to (it.host to it.port) }

    override fun saveRemoteHost(
        existingHost: String,
        existingPort: Int,
        label: String,
        host: String,
        port: String,
    ): String? {
        // The port arrives as raw text because the field is a text field. Refuse it here
        // in the same fix-bearing shape the store uses, so the sheet has one error path.
        val parsedPort = port.trim().toIntOrNull()
            ?: return "Enter a port number from 1 through 65535."
        val outcome = if (existingHost.isEmpty()) {
            remoteHostStore.add(label, host, parsedPort)
        } else {
            remoteHostStore.update(existingHost, existingPort, label, host, parsedPort)
        }
        return when (outcome) {
            is RemoteHostOutcome.Saved -> null
            is RemoteHostOutcome.Refused -> "${outcome.message} ${outcome.fix}"
            is RemoteHostOutcome.Failed -> "${outcome.message} ${outcome.fix}"
        }
    }

    override fun removeRemoteHost(host: String, port: Int): String? =
        when (val outcome = remoteHostStore.remove(host, port)) {
            is RemoteHostOutcome.Saved -> null
            is RemoteHostOutcome.Refused -> "${outcome.message} ${outcome.fix}"
            is RemoteHostOutcome.Failed -> "${outcome.message} ${outcome.fix}"
        }

    override fun startRemoteHost(label: String, host: String, port: Int) {
        selectSource(SignalKind.RELAY)
        persistAutomaticGain()
        ui.sourceLabel = "remote · connecting…" // honest immediately (kills the race)
        ui.remoteFailure = "" // a fresh attempt clears the previous failure's fix
        ui.remote = true
        startSourceService(
            Intent(this, PlaybackService::class.java)
                .setAction(PlaybackService.ACTION_REMOTE_CONNECT)
                .putExtra(PlaybackService.EXTRA_HOST, host)
                .putExtra(PlaybackService.EXTRA_PORT, port)
                .putExtra(PlaybackService.EXTRA_LABEL, label)
        )
    }

    override fun setRemoteStreams(audio: Boolean, geometry: Boolean) {
        instrumentWorkflow?.settle("Remote stream capability changed. Pending apply cancelled.")
        PhosphorNative.remoteSetStreams(audio, geometry)
        ui.remoteAudio = audio
        ui.remoteGeometry = geometry
    }

    override fun disconnectRemote() {
        instrumentWorkflow?.settle("Remote source disconnected. Pending apply cancelled.")
        selectSource()
        startSourceService(
            Intent(this, PlaybackService::class.java)
                .setAction(PlaybackService.ACTION_REMOTE_DISCONNECT)
        )
        ui.remote = false
        ui.sourceLabel = "no source"
        applyLocalGainPolicy()
    }

    override fun openFile() {
        selectSource(signalSelected)
        openFileLauncher.launch(arrayOf("audio/*"))
    }

    override fun exportSettings() = createSettingsArchive.launch(
        "phosphor-settings-${BuildConfig.VERSION_NAME}.phossettings"
    )

    override fun importSettings() {
        val owner = instrumentWorkflow ?: return
        val ticket = owner.beginSettingsImport()
        if (ticket == null) {
            ui.settingsTransferStatus = if (owner.editsBlocked)
                "Instrument state is uncertain. Recover it in INSTRUMENT PRESETS before importing settings."
            else "A settings document is still open or being read. Wait for it before another import."
            return
        }
        pendingSettingsImport = ticket
        pendingAppearanceImport = appearanceWorkflow?.ticket()
        runCatching {
            openSettingsArchive.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
        }.onFailure {
            pendingSettingsImport = null
            pendingAppearanceImport = null
            owner.cancelSettingsImport(ticket)
            reportImportFailure(it)
        }
    }

    private fun maybeStartConfiguredDefault() {
        val surviving = mic.established() || CaptureService.hasLiveWakeSource() || PlaybackService.hasLiveWakeSource()
        val confirmed = runtimePrefs().getBoolean(StartupCoordinatorPolicy.CONFIRMED, false)
        if (!StartupCoordinatorPolicy.shouldAutoStart(ui.defaultSource, ui.live, surviving, confirmed)) return
        when (ui.defaultSource) {
            "mic" -> {
                val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (StartupCoordinatorPolicy.promptFreeMic(granted) || ui.automaticPermissionPopup) startMic()
                else ui.microphoneStatus = "Default microphone is waiting. Open SRC to grant and start."
            }
            "capture" -> {
                if (ui.automaticPermissionPopup) startCapture()
                else ui.captureStatus = "Default capture is waiting. Open SRC to approve Android consent."
            }
        }
    }

    override fun startMic() {
        if (!micStartEligible()) { ui.microphoneStatus = "Start microphone from the visible app"; return }
        if (mic.established() && mic.standalone()) { ui.live = true; ui.sourceLabel = "mic"; return }
        val selection = selectSource(SignalKind.MIC)
        if (!explainMicrophone(false)) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            val request = UUID.randomUUID().toString()
            ui.live = false
            micHandoff.request(request, CaptureService.currentStatus().sequence)
            withSourcesReleased(selection, request) {
                // Re-read the observation too: the receiver may have been stopped by Android UI.
                observeMicCaptureStatus(CaptureService.currentStatus())
                micHandoff.sourcesReleased(request)
            }
        }
        else {
            pendingAudioPermission = AudioPermissionPurpose.MICROPHONE
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun micStartEligible(): Boolean = taskIsCurrent() && activityStarted && !isInPictureInPictureMode &&
        !getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked

    private fun refreshMicrophone() {
        micRefreshAt = android.os.SystemClock.elapsedRealtime()
        ui.microphoneInputs = MicrophoneRoutes.choices(this)
        val key = runtimePrefs().getString(MicrophoneRoutePolicy.SELECTED, null)
        ui.selectedMicrophone = MicrophoneRoutePolicy.select(ui.microphoneInputs, key)?.id ?: -1
        ui.microphoneStatus = if (ui.selectedMicrophone < 0) "Selected microphone unavailable. Connect it or choose an input" else MicCaptureService.status()
        ui.microphoneActive = mic.established()
        if (mic.standalone() && mic.established() && !micHandoff.isPending) { ui.sourceLabel = "mic"; ui.live = true }
        if (ui.sourceLabel == "mic" && !mic.isRecording() && !micHandoff.isPending) {
            ui.live = false
            if (taskIsCurrent()) runtimePrefs().edit { putString("last_source", "none") }
        }
        if (activityStarted) reassertSourceWake()
    }

    private fun explainMicrophone(forMix: Boolean): Boolean {
        val selected = runCatching { MicrophoneRoutes.selected(this) }.getOrNull()
        if (selected == null) { ui.microphoneStatus = "Selected microphone unavailable. Connect it or choose an input"; return false }
        if (MicrophoneRoutes.choice(selected).bluetooth && !runtimePrefs().getBoolean(MicrophoneRoutePolicy.BLUETOOTH_ACK, false)) {
            bluetoothForMix = forMix; ui.micBluetoothExplain = true; return false
        }
        return true
    }

    override fun confirmMicrophoneBluetooth(accept: Boolean) {
        if (!ui.micBluetoothExplain || !micStartEligible()) return
        ui.micBluetoothExplain = false
        if (!accept) { ui.microphoneStatus = "Bluetooth microphone not started"; return }
        runtimePrefs().edit { putBoolean(MicrophoneRoutePolicy.BLUETOOTH_ACK, true) }
        if (bluetoothForMix) startMixMicrophone() else startMic()
    }

    override fun chooseMicrophone(id: Int) = selectMicrophone(id, startAfter = false)

    /** SRC's one tap on an idle input: choose it, then start it through the existing start path. */
    override fun chooseAndStartMicrophone(id: Int) = selectMicrophone(id, startAfter = true)

    private fun selectMicrophone(id: Int, startAfter: Boolean) {
        if (!micStartEligible()) return
        val choice = MicrophoneRoutes.choices(this).singleOrNull { it.id == id } ?: run {
            ui.microphoneStatus = "Input disconnected. Refresh the input list"; return
        }
        val wasStandalone = mic.standalone()
        val wasMixing = CaptureService.mixSession() != null && ui.includeMicrophone
        val revision = ++micUiRevision
        runtimePrefs().edit { putString(MicrophoneRoutePolicy.SELECTED, choice.key) }
        refreshMicrophone()
        mic.stopForLocal(revision) { returned, error, _ -> tick.post {
            if (returned != micUiRevision || !micStartEligible()) return@post
            if (error != null) ui.microphoneStatus = error
            else if (wasStandalone || (startAfter && !wasMixing)) startMic()
            else if (wasMixing) startMixMicrophone()
        } }
    }

    override fun setIncludeMicrophone(on: Boolean) {
        if (!taskIsCurrent()) return
        if (!saveMixPreferences(CaptureMixSettings(on, ui.playbackMixLevel, ui.microphoneMixLevel))) return
        if (on) startMixMicrophone() else {
            ++micUiRevision
            if (!mic.standalone()) mic.stop()
            ui.micBluetoothExplain = false
        }
    }

    override fun setMicrophoneMixLevel(microphone: Boolean, value: Float) {
        if (!taskIsCurrent() || !value.isFinite() || value !in 0f..1f) return
        val settings = CaptureMixSettings(ui.includeMicrophone, if (microphone) ui.playbackMixLevel else value,
            if (microphone) value else ui.microphoneMixLevel)
        if (saveMixPreferences(settings)) CaptureService.mixSession()?.settings(settings)
    }

    private fun saveMixPreferences(settings: CaptureMixSettings): Boolean {
        val saved = settingsWriteOwner.write { prefs().edit().putBoolean(CaptureMixSettings.INCLUDE, settings.include)
            .putFloat(CaptureMixSettings.PLAYBACK, settings.playback).putFloat(CaptureMixSettings.MICROPHONE, settings.microphone).commit() }
        if (!saved) { ui.microphoneStatus = "Mix settings could not be saved. Retry"; return false }
        ui.includeMicrophone = settings.include; ui.playbackMixLevel = settings.playback; ui.microphoneMixLevel = settings.microphone
        return true
    }

    override fun retryMicrophone() {
        if (!micStartEligible()) return
        val forMix = CaptureService.mixSession() != null && ui.includeMicrophone
        val revision = ++micUiRevision
        mic.stopForLocal(revision) { _, error, _ -> tick.post {
            if (revision != micUiRevision || !micStartEligible()) return@post
            if (error != null) ui.microphoneStatus = error
            else if (forMix) startMixMicrophone() else startMic()
        } }
    }

    override fun stopMicrophone() { ++micUiRevision; mic.stop(); ui.micBluetoothExplain = false }

    private fun maybeStartMixMicrophone() {
        val target = CaptureService.mixSession() ?: return
        if (!mixStartPending || lastMixAttempt == target.id || !activityResumed || !micStartEligible()) return
        mixStartPending = false; lastMixAttempt = target.id
        startMixMicrophone()
    }

    private fun startMixMicrophone() {
        if (!micStartEligible()) { ui.microphoneStatus = "Return to the visible app to include microphone"; return }
        val target = CaptureService.mixSession() ?: run { ui.microphoneStatus = "Choose everything playing, then include microphone"; return }
        if (!ui.includeMicrophone || mic.established()) return
        if (!explainMicrophone(true)) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingAudioPermission = AudioPermissionPurpose.MIX_MICROPHONE
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        val revision = ++micUiRevision
        mic.request(this, target, CaptureMixSettings.read(prefs().all),
            { revision == micUiRevision && micStartEligible() && CaptureService.mixSession() === target && ui.includeMicrophone }) { error ->
            if (revision == micUiRevision && taskIsCurrent()) { ui.microphoneStatus = error ?: "Microphone included"; refreshMicrophone() }
        }
    }

    override fun startCapture() = startCaptureBackend(explicitStandard = false)

    override fun startStandardCapture() = startCaptureBackend(explicitStandard = true)

    private fun startCaptureBackend(explicitStandard: Boolean) {
        if (!taskIsCurrent()) return
        val backend = if (!explicitStandard && RootCaptureSettings.enabled(this)) CaptureBackend.ROOT else CaptureBackend.STANDARD
        val alreadyCapturing = CaptureService.ownsCapture() && CaptureService.currentStatus().backend == backend && !micHandoff.isPending
        val selection = selectSource(if (backend == CaptureBackend.ROOT) SignalKind.ROOT else SignalKind.CAPTURE)
        if (alreadyCapturing) return
        if (backend == CaptureBackend.ROOT) {
            withSourcesReleased(selection = selection) {
                if (!RootCaptureSettings.enabled(this)) return@withSourcesReleased
                applyLocalGainPolicy()
                applyCaptureStatus(CaptureService.CaptureStatus(CaptureService.STATE_STARTING,
                    "root capture starting", RootCapturePolicy.CAPABILITY, false, backend = CaptureBackend.ROOT))
                runCatching { startCaptureService(Intent(this, RootCaptureService::class.java)) }.onFailure {
                    applyCaptureStatus(CaptureService.CaptureStatus.error("root capture could not start", RootCaptureSettings.fix(it.message)).copy(backend = CaptureBackend.ROOT))
                }
            }
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pendingAudioPermission = AudioPermissionPurpose.PLAYBACK_CAPTURE
            ui.captureStatus = "microphone permission needed for playback capture"
            ui.captureFix = "Grant microphone access, then approve Android's capture prompt"
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        launchCaptureConsent()
    }

    private fun launchCaptureConsent() {
        pendingCaptureConsent = sourceSelection
        markConsentSeen()
        ui.captureStatus = "waiting for Android capture permission"
        ui.captureFix = "Approve the prompt to connect playback audio"
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        captureConsent.launch(screenCaptureIntent(mpm))
    }

    private fun screenCaptureIntent(manager: MediaProjectionManager): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            fullDisplayCaptureIntent(manager)
        } else {
            // Android 10 through 13 only offer full-display projection.
            manager.createScreenCaptureIntent()
        }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun fullDisplayCaptureIntent(manager: MediaProjectionManager): Intent =
        manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())

    override fun setRootCapture(enabled: Boolean) {
        if (!taskIsCurrent()) return
        if (!RootCapturePolicy.PRODUCT_AVAILABLE) {
            Toast.makeText(this, RootCapturePolicy.DEFERRED, Toast.LENGTH_LONG).show()
            return
        }
        selectSource()
        if (enabled) RootCaptureSettings.enable(this) else RootCaptureSettings.disable(this)
        refreshRootState()
    }

    override fun openRootManager() {
        if (!RootCapturePolicy.PRODUCT_AVAILABLE) {
            Toast.makeText(this, RootCapturePolicy.DEFERRED, Toast.LENGTH_LONG).show()
            return
        }
        runCatching {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage("me.weishu.kernelsu"))
        }.onFailure {
            Toast.makeText(this, "Open your installed KernelSU manager and verify Phosphor's existing grant and Default/inherited profile", Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshRootState() {
        ui.rootCaptureEnabled = RootCaptureSettings.enabled(this)
        ui.rootCaptureBusy = RootCaptureSettings.busy
        val status = CaptureService.currentStatus()
        ui.rootCaptureStatus = if (status.backend == CaptureBackend.ROOT && status.message.isNotBlank()) {
            "${status.message} · ${status.fix}"
        } else if (ui.rootCaptureEnabled && RootCaptureSettings.message.startsWith("Off")) {
            "Enabled · ${RootCapturePolicy.CAPABILITY} · authorization checked at capture start"
        } else RootCaptureSettings.message
    }

    override fun stopLive() {
        withSourcesReleased {
            PhosphorNative.setRingActive(false)
            ui.live = false
            if (ui.sourceLabel.startsWith("capture") || ui.sourceLabel == "mic") {
                ui.sourceLabel = "no source"
                acceptTrackTitle(null)
                ui.trackArtist = null
                ui.artwork = null
            }
            ui.captureStatus = ""
            ui.captureFix = ""
        }
    }

    private fun applyCaptureStatus(status: CaptureService.CaptureStatus) {
        if (!taskIsCurrent()) return
        if (status.sequence > 0) {
            if (status.sequence <= captureStatusSequence) return
            captureStatusSequence = status.sequence
        }
        val captureActive = status.state == CaptureService.STATE_STARTING || status.state == CaptureService.STATE_FLOWING
        if (captureActive != ui.sourceLabel.startsWith("capture")) {
            instrumentWorkflow?.settle("Capture source changed. Pending apply cancelled.")
        }
        if (micHandoff.isPending) {
            observeMicCaptureStatus(status)
            return
        }
        signalCaptureStatus = status
        val wasCapture = ui.sourceLabel.startsWith("capture")
        ui.captureRoot = status.backend == CaptureBackend.ROOT
        ui.captureStatus = status.message
        ui.captureFix = status.fix
        when (status.state) {
            CaptureService.STATE_STARTING -> {
                ui.sourceLabel = "capture · starting…"
                ui.live = false
            }
            CaptureService.STATE_FLOWING -> {
                ui.sourceLabel = "capture"
                ui.live = true
                maybeStartMixMicrophone()
            }
            else -> if (wasCapture) {
                ui.sourceLabel = "no source"
                ui.live = false
                acceptTrackTitle(null)
                ui.trackArtist = null
                ui.artwork = null
            }
        }
    }

    private fun observeMicCaptureStatus(status: CaptureService.CaptureStatus) {
        if (micReleaseRevision != null && !micRequestIsCurrent()) {
            micHandoffCancel()
            return
        }
        micHandoff.captureStatus(
            sequence = status.sequence,
            idle = status.state == CaptureService.STATE_IDLE,
            requestId = status.micRequest,
            error = status.fix.takeIf { status.state == CaptureService.STATE_ERROR },
        )
    }

    private fun applyLocalSourcePublication(revision: Long) {
        val publication = PlaybackService.localSourcePublication
        if (!publication.accepts(revision)) return
        instrumentWorkflow?.settle("Local source changed. Pending apply cancelled.")
        when (publication.current.source) {
            LocalSourcePublication.Source.LOCAL -> {
                ui.sourceLabel = "deck"
                ui.live = false
                ui.remote = false
            }
            LocalSourcePublication.Source.NONE, LocalSourcePublication.Source.RELEASED_READERS -> {
                if (publication.current.source == LocalSourcePublication.Source.RELEASED_READERS &&
                    !publication.current.clearsReaderFace(ui.sourceLabel)) return
                ui.sourceLabel = "no source"
                ui.live = false
                ui.remote = false
                acceptTrackTitle(null)
                ui.trackArtist = null
                ui.artwork = null
            }
            LocalSourcePublication.Source.OTHER -> Unit
        }
    }

    // Runtime consent state is separate from portable instrument settings.
    private fun prefs() = getSharedPreferences(PhosphorApplication.PREFERENCES_NAME, MODE_PRIVATE)
    private fun runtimePrefs() = getSharedPreferences(PhosphorApplication.RUNTIME_PREFERENCES_NAME, MODE_PRIVATE)

    // ── Tuning persistence: the scope remembers its knobs across launches. ──
    private fun persistAutomaticGain() {
        if (instrumentWorkflow?.automaticPersistenceAllowed == false) return
        prefs().edit { putFloat("gain", gainValue) }
    }

    private fun saveTuning() {
        instrumentWorkflow?.settle("Saving current tuning. Pending apply cancelled.")
        // Do not silently make a failed preset durable during lifecycle autosave.
        val preserved = if (instrumentWorkflow?.automaticPersistenceAllowed == false) {
            preferenceValueSnapshots(prefs().all, CuratedInstrumentPresets.cleanXy.setup.preferenceValues().keys)
        } else emptyMap()
        prefs().edit {
            putInt("mode", ui.modeIndex)
            putBoolean("random_mode_armed", ui.randomModeArmed)
            putString("random_ban_modes", ui.randomBanModes.sorted().joinToString(","))
            // Light has its own validated, synchronous snapshot save.
            putInt("fps", ui.fpsValue)
            putInt("oversample", ui.oversample)
            // AUTO framing breathes ui.gain; the manual landing remains the saved knob.
            putFloat("gain", gainValue)
            if (autoFrameSave.pending == null) putFloat(AutoFramePreference.KEY, autoFrameScale)
            putFloat("beam_energy", ui.beamEnergy)
            putFloat("glow", ui.glow)
            putBoolean("beam_random_armed", ui.beamRandomArmed)
            putString("beam_random_range", "${ui.beamRandomLo},${ui.beamRandomHi}")
            putBoolean("glow_random_armed", ui.glowRandomArmed)
            putString("glow_random_range", "${ui.glowRandomLo},${ui.glowRandomHi}")
            putInt("geom_fx", ui.geomFx)
            putFloat("geom_amount", ui.geomAmount)
            putBoolean("grid", ui.grid)
            putBoolean(dev.phosphor.mobil3.ui.GridData.KEY, ui.gridData)
            putFloat("focus", ui.focus)
            // Relay auto-gain is display truth, not authority for an absent local preference.
            putBoolean("auto_gain", prefs().getBoolean("auto_gain", true))
            putInt("hud_mode", ui.hudMode)
            putInt("band_mode", ui.bandMode)
            putBoolean("fullscreen", ui.fullscreen)
            putBoolean("linger_background", ui.lingerBackground)
            putBoolean("view_lock", ui.viewLock)
            // Do not rewrite light here after a failed restore or pending guard.
            putBoolean("double_tap_playback", ui.doubleTapPlayback)
            putBoolean(dev.phosphor.mobil3.ui.ControlsVisibilityPolicy.KEY, ui.controlsAlwaysVisible)
            putBoolean(PictureInPicturePolicy.KEY, ui.pipAutoEnter)
            putBoolean("scope_rotation_locked", scopeRotationLockState)
            putInt("scope_locked_orientation", lockedScopeOrientation)
            putBoolean("ui_placement_locked", uiPlacementLockState)
            putBoolean("ui_locked_landscape", lockedUiLandscape)
            putInt("remote_latency_mode", ui.latencyMode)
            putBoolean("amoled_seen", ui.amoledCaptionSeen)
            restoreSnapshots(preserved)
        }
        runtimePrefs().edit {
            putString("random_track_title", lastRandomTrackTitle)
            // The remembered input and calibration date are device runtime metadata.
            if (taskIsCurrent()) putString(
                "last_source",
                runtimeInputSource(ui, mic.isRecording()),
            )
            putString(
                "cal_date",
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    .format(java.util.Date()),
            )
        }
    }

    private fun restoreTuning(lightPublished: Boolean = false) {
        val p = prefs()
        ui.pinScreenBrightness = ForegroundBrightnessPolicy.requested(p.all)
        applyBrightnessPin()
        ui.hdrRequested = HdrPresentationPolicy.requested(p.all)
        ui.hdrStatus = HdrPresentationPolicy.reason(ui.hdrRequested, false, android.os.Build.VERSION.SDK_INT, false)
        PhosphorNative.setHdrRequested(ui.hdrRequested, android.os.Build.VERSION.SDK_INT)
        ui.defaultSource = StartupCoordinatorPolicy.defaultOf(p.all)
        ui.automaticPermissionPopup = StartupCoordinatorPolicy.popup(p.all)
        PhosphorNative.setPauseBlack(dev.phosphor.mobil3.ui.PauseDisplayPolicy.black(p.all))
        refreshDisplayPause()
        val hud = HudPolicy.read(p.all)
        ui.floatingHudEnabled = hud.enabled
        ui.floatingHudTransparent = hud.background == HudPolicy.Background.TRANSPARENT
        ui.lingerBackground = BackgroundLifecyclePolicy.linger(p.all)
        CaptureMixSettings.read(p.all).let {
            ui.includeMicrophone = it.include; ui.playbackMixLevel = it.playback; ui.microphoneMixLevel = it.microphone
        }
        ui.doubleTapPlayback = p.getBoolean("double_tap_playback", true)
        ui.controlsAlwaysVisible = dev.phosphor.mobil3.ui.ControlsVisibilityPolicy.alwaysVisible(p.all)
        ui.pipAutoEnter = PictureInPicturePolicy.autoEnter(p.all)
        updatePictureInPictureParams()
        ui.modeIndex = p.getInt("mode", 1).also { PhosphorNative.setMode(it) }
        ui.randomModeArmed = p.getBoolean("random_mode_armed", false)
        lastRandomTrackTitle = runtimePrefs().getString("random_track_title", null)
        ui.randomBanModes = (p.getString("random_ban_modes", "") ?: "")
            .split(",").mapNotNull { it.toIntOrNull() }
            .filter { it in dev.phosphor.mobil3.ui.ModeLabels.indices }.toSet()
            .let { if (dev.phosphor.mobil3.ui.ModeLabels.size - it.size < 2) emptySet() else it }
        ui.beamIndex = p.getInt("beam", 7)
        ui.fpsValue = p.getInt("fps", 0).also { PhosphorNative.setTargetFps(it) }
        ui.oversample = p.getInt("oversample", 1).also { PhosphorNative.setOversample(it) }
        gainValue = p.getFloat("gain", 1.8332275f)
        ui.gain = gainValue
        ui.manualGain = gainValue
        PhosphorNative.setGain(gainValue)
        tick.removeCallbacks(persistAutoFrame)
        autoFrameScale = autoFrameSave.pending ?: AutoFramePreference.read(p.all)
        ui.autoFrameSaveStatus = if (autoFrameSave.pending == null) "" else
            "Framing save failed. Free storage, then retry."
        ui.autoFrameScale = autoFrameScale
        PhosphorNative.setAutoFrameScale(autoFrameScale)
        val autoGain = p.getBoolean("auto_gain", true)
        PhosphorNative.setGainAuto(autoGain)
        ui.autoGain = autoGain
        ui.localAutoGain = autoGain
        ui.beamEnergy = p.getFloat("beam_energy", 8f).also { PhosphorNative.setBeamEnergy(it) }
        ui.glow = p.getFloat("glow", 0.7f).also { PhosphorNative.setGlow(it) }
        // The dice: restore range + armed state; never roll on restore — the last landed
        // BEAM/GLOW values above are the truth until the next track boundary.
        fun range(key: String, min: Float, max: Float, dLo: Float, dHi: Float): Pair<Float, Float> {
            val parts = (p.getString(key, "") ?: "").split(",")
            if (parts.size != 2) return dLo to dHi
            val lo = parts[0].toFloatOrNull() ?: return dLo to dHi
            val hi = parts[1].toFloatOrNull() ?: return dLo to dHi
            if (!lo.isFinite() || !hi.isFinite() || lo !in min..max || hi !in lo..max) return dLo to dHi
            return lo to hi
        }
        range("beam_random_range", 1f, 30f, 6f, 20f).let { (lo, hi) ->
            ui.beamRandomLo = lo; ui.beamRandomHi = hi
        }
        range("glow_random_range", 0f, 0.98f, 0.30f, 0.90f).let { (lo, hi) ->
            ui.glowRandomLo = lo; ui.glowRandomHi = hi
        }
        ui.beamRandomArmed = p.getBoolean("beam_random_armed", false)
        ui.glowRandomArmed = p.getBoolean("glow_random_armed", false)
        ui.bestiaryFound = p.getBoolean("bestiary_found", false)
        refreshRootState()
        ui.geomFx = p.getInt("geom_fx", 0).coerceIn(0, 4).also { PhosphorNative.setGeomFx(it) }
        ui.geomAmount = p.getFloat("geom_amount", 0.6f).coerceIn(0f, 1f)
            .also { PhosphorNative.setGeomAmount(it) }
        ui.grid = p.getBoolean("grid", false).also { PhosphorNative.setGrid(it) }
        ui.gridData = p.getBoolean(dev.phosphor.mobil3.ui.GridData.KEY, dev.phosphor.mobil3.ui.GridData.DEFAULT)
        ui.gridReading = null
        ui.focus = p.getFloat("focus", 0.3f).also { PhosphorNative.setFocus(it) }
        ui.hudMode = p.getInt("hud_mode", 1).coerceIn(0, 2)
        ui.bandMode = p.getInt("band_mode", 1)
        ui.fullscreen = p.getBoolean("fullscreen", true)
        ui.viewLock = p.getBoolean("view_lock", false)
        ui.developerView = p.getBoolean("developer_view", false)
        scopeRotationLockState = p.getBoolean("scope_rotation_locked", true)
        lockedScopeOrientation = p.getInt(
            "scope_locked_orientation",
            if (scopeRotationLockState) exactCurrentOrientation() else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
        )
        uiPlacementLockState = p.getBoolean("ui_placement_locked", false)
        lockedUiOrientation = p.getInt(
            "ui_locked_orientation", ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
        )
        updateOrientationSensor()
        lockedUiLandscape = p.getBoolean(
            "ui_locked_landscape",
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
        )
        ui.latencyMode = p.getInt("remote_latency_mode", 2).coerceIn(0, 2)
            .also { PhosphorNative.remoteSetLatencyMode(it) }
        ui.calDate = runtimePrefs().getString("cal_date", "") ?: ""
        ui.amoledCaptionSeen = p.getBoolean("amoled_seen", false)
        if (appearanceWorkflow?.committed == null) {
        val legacy = p.all
        ui.styleOverride = dev.phosphor.mobil3.ui.StyleOverride(
            character = (legacy["ov_char"] as? Int)?.takeIf { it >= 0 }
                ?.let { dev.phosphor.mobil3.ui.ChromeCharacter.entries.getOrNull(it) },
            motion = (legacy["ov_motion"] as? Int)?.takeIf { it >= 0 }
                ?.let { dev.phosphor.mobil3.ui.MotionFeel.entries.getOrNull(it) },
            radiusDp = (legacy["ov_radius"] as? Int)?.takeIf { it >= 0 },
            designators = when (legacy["ov_desig"] as? Int) {
                1 -> true; 0 -> false; else -> null
            },
        )
        dev.phosphor.mobil3.ui.paletteById(legacy["room"] as? String ?: "amoled")
            .let { baseRoom = it; ui.room = it }
        ui.appearanceStyle = ui.room.style.overridden(ui.styleOverride)
        } else refreshAppearance()
        runCatching { LightSettings.read(p.all) }.onSuccess {
            if (lightPublished) {
                ui.light = it
                ui.beamIndex = it.preset
                ui.lightTemporary = false
                ui.lightError = ""
            } else if (!applyLight(it)) markLightRestoreUnconfirmed(it)
        }.onFailure {
            ui.lightError = "Stored light settings could not be decoded. ${it.message}. Recover the displayed setup or reopen after restoring valid saved settings."
            if (!lightPublished) markLightRestoreUnconfirmed(ui.light)
        }

    }

    private fun markLightRestoreUnconfirmed(target: LightSettings) {
        ui.light = LightCycleGuard.evaluate(target, epilepsyAcknowledged()).safe
        ui.beamIndex = ui.light.preset
        ui.lightError += " Displayed light values are restore targets, not confirmed active tuning."
        instrumentWorkflow?.restoreUnconfirmed(captureInstrument())
    }
    override fun captureConsentNeeded(): Boolean = !RootCaptureSettings.enabled(this) && !runtimePrefs().getBoolean("consent_seen", false)
    private fun markConsentSeen() {
        if (taskIsCurrent()) runtimePrefs().edit { putBoolean("consent_seen", true) }
    }

    override fun setLingerBackground(on: Boolean) {
        ui.lingerBackground = on
        prefs().edit { putBoolean(BackgroundLifecyclePolicy.LINGER_KEY, on) }
    }

    override fun setDeveloperView(on: Boolean) {
        ui.developerView = on
        prefs().edit { putBoolean("developer_view", on) }
    }

    override fun setViewLock(on: Boolean) {
        ui.viewLock = on
        prefs().edit { putBoolean("view_lock", on) }
    }

    override fun setDoubleTapPlayback(on: Boolean) {
        ui.doubleTapPlayback = on
        prefs().edit { putBoolean("double_tap_playback", on) }
    }

    override fun setControlsAlwaysVisible(on: Boolean) {
        ui.controlsAlwaysVisible = on
        prefs().edit { putBoolean(dev.phosphor.mobil3.ui.ControlsVisibilityPolicy.KEY, on) }
    }

    override fun setPipAutoEnter(on: Boolean) {
        ui.pipAutoEnter = on
        prefs().edit { putBoolean(PictureInPicturePolicy.KEY, on) }
        updatePictureInPictureParams()
    }

    override fun openCaptureMetadataSettings() {
        val component = ComponentName(this, CaptureNotificationListenerService::class.java)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            return
        }
        val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                component.flattenToString(),
            )
        runCatching { startActivity(detail) }.onFailure {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    override fun markBestiaryFound() {
        ui.bestiaryFound = true
        prefs().edit { putBoolean("bestiary_found", true) } // found is forever
    }

    override fun openLink(url: String) {
        // Cards leave through the user's own browser — the app renders no web content.
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        }.onFailure {
            android.app.AlertDialog.Builder(this)
                .setTitle("Browser could not open")
                .setMessage("Enable a browser in Android Settings > Apps. Open this URL in that browser:\n\n$url")
                .setPositiveButton("CLOSE", null)
                .show()
        }
    }

    private fun refreshCaptureMetadataAccess() {
        val component = ComponentName(this, CaptureNotificationListenerService::class.java)
        ui.captureMetadataAccess = getSystemService(NotificationManager::class.java)
            .isNotificationListenerAccessGranted(component)
    }

    private fun acceptTrackTitle(title: String?) {
        ui.trackTitle = title
        if (title != null && title != lastRandomTrackTitle) {
            instrumentEdit {
                lastRandomTrackTitle = title
                if (ui.randomModeArmed) rollRandomMode()
                if (ui.beamRandomArmed) applyBeamEnergy(rollIn(ui.beamRandomLo, ui.beamRandomHi))
                if (ui.glowRandomArmed) applyGlow(rollIn(ui.glowRandomLo, ui.glowRandomHi))
            }
        }
    }

    private fun armAndRollRandomMode() = instrumentEdit {
        ui.randomModeArmed = true
        rollRandomMode()
    }

    private fun rollRandomMode() = applyMode(rollModeExcluding(ui.modeIndex, ui.randomBanModes))

    private fun applyMode(index: Int) {
        PhosphorNative.setMode(index); ui.modeIndex = index
        // Remote geometry uses the desktop renderer's mode names.
        if (ui.remote && ui.remoteGeometry) {
            PhosphorNative.remoteScopeCtl("mode", dev.phosphor.mobil3.ui.ModeTags[index])
        }
    }
    override fun setMode(index: Int) = instrumentEdit {
        ui.randomModeArmed = false
        applyMode(index)
    }
    override fun setRandomBanModes(modes: Set<Int>) = instrumentEdit {
        if (modes.all { it in 0..10 } && modes.size <= 9) ui.randomBanModes = modes
    }
    override fun setBeam(index: Int) = instrumentEdit {
        applyPresetLight(ui.light, index, { applyLight(it) }) {
            if (ui.remote && ui.remoteGeometry) {
                PhosphorNative.remoteScopeCtl("theme", dev.phosphor.mobil3.ui.BeamColors[index].label)
            }
        }
    }

    override fun setFps(value: Int) { PhosphorNative.setTargetFps(value); ui.fpsValue = value }
    override fun setOversample(n: Int) = instrumentEdit { PhosphorNative.setOversample(n); ui.oversample = n }
    override fun setRoom(room: Palette) {
        val owner = appearanceWorkflow ?: return
        if (owner.blocked) return
        val value = AppearancePalette.legacy(dev.phosphor.mobil3.ui.LegacyAppearanceInput(room.id)).value
        val id = "legacy:${room.id}".takeIf { key -> owner.committed?.legacy?.any { it.id == key } == true } ?: ""
        owner.apply(value, id)
    }

    override fun setRoomStyle(overrides: dev.phosphor.mobil3.ui.StyleOverride) {
        val owner = appearanceWorkflow ?: return
        if (owner.blocked) return
        val current = owner.committed ?: return
        val value = AppearancePalette.editCurrentStyle(owner.effective ?: return, overrides)
        owner.apply(value, current.activeId)
    }
    override fun setFocus(focus: Float) = instrumentEdit { ui.focus = focus; PhosphorNative.setFocus(focus) }

    override fun setGainAbsolute(g: Float) = instrumentEdit {
        gainValue = GainScale.clamp(g)
        PhosphorNative.setGain(gainValue)
        ui.gain = gainValue
        ui.manualGain = gainValue
        ui.autoGain = false
        ui.localAutoGain = false
        prefs().edit { putBoolean("auto_gain", false) }
        tick.removeCallbacks(persistGain)
        tick.postDelayed(persistGain, 250)
        // Pinch drives the DESKTOP's gain while it renders the beam (throttled —
        // the gesture fires per-frame; the scope only needs ~10 Hz).
        if (ui.remote) {
            val now = android.os.SystemClock.uptimeMillis()
            if (now - lastRemoteGainMs > 100) {
                lastRemoteGainMs = now
                PhosphorNative.remoteScopeCtl("gain", String.format(java.util.Locale.US, "%.2f", gainValue))
            }
        }
    }
    private var lastRemoteGainMs = 0L

    override fun setAutoFrameScale(scale: Float) {
        if (!taskIsCurrent() || activityDestroyed) return
        autoFrameScale = AutoFramePreference.normalize(scale)
        autoFrameSave.stage(autoFrameScale)
        ui.autoFrameSaveStatus = "Saving framing…"
        ui.autoFrameScale = autoFrameScale
        PhosphorNative.setAutoFrameScale(autoFrameScale)
        tick.removeCallbacks(persistAutoFrame)
        tick.postDelayed(persistAutoFrame, 250)
        AcceptanceTrace.record("auto_frame") {
            "scale=$autoFrameScale effective_gain=${PhosphorNative.gainNow()}"
        }
    }

    override fun finishAutoFrameScale() {
        tick.removeCallbacks(persistAutoFrame)
        if (activityDestroyed || !taskIsCurrent()) return
        val saved = autoFrameSave.flush { value ->
            prefs().edit().putFloat(AutoFramePreference.KEY, value).commit()
        }
        ui.autoFrameSaveStatus = if (saved) "" else "Framing save failed. Free storage, then retry."
        if (!saved) android.widget.Toast.makeText(this, ui.autoFrameSaveStatus, android.widget.Toast.LENGTH_LONG).show()
    }

    override fun resetAutoFrameScale() {
        setAutoFrameScale(AutoFramePreference.DEFAULT)
        finishAutoFrameScale()
    }

    override fun setGainAuto(on: Boolean) = instrumentEdit {
        if (!on && ui.localAutoGain && !ui.remoteGeometry) {
            // One size scale: leaving AUTO keeps what the user sees as the manual size.
            gainValue = GainScale.clamp(PhosphorNative.gainNow())
            ui.gain = gainValue
            ui.manualGain = gainValue
            PhosphorNative.setGain(gainValue)
            tick.removeCallbacks(persistGain)
            tick.post(persistGain)
        }
        prefs().edit { putBoolean("auto_gain", on) }
        // Keep the local renderer ready for local/captured remote audio, while a
        // remote source also receives the desktop's existing typed gain verb.
        PhosphorNative.setGainAuto(on)
        ui.localAutoGain = on
        ui.autoGain = on
        if (ui.remote) {
            PhosphorNative.remoteScopeCtl(
                "gain",
                if (on) "auto" else String.format(java.util.Locale.US, "%.2f", gainValue),
            )
        }
    }

    override fun setHudMode(mode: Int) {
        val safeMode = mode.coerceIn(0, 2)
        if (prefs().edit().putInt("hud_mode", safeMode).commit()) {
            ui.hudMode = safeMode
            ui.hudControlStatus = ""
        } else {
            ui.hudControlStatus = "HUD change could not be saved · retry after storage is available"
        }
    }

    private data class PreferenceValueSnapshot(val present: Boolean, val value: Any?)

    private fun preferenceValueSnapshots(
        allPreferences: Map<String, Any?>,
        keys: Set<String>,
    ): Map<String, PreferenceValueSnapshot> = keys.associateWith { key ->
        PreferenceValueSnapshot(
            present = allPreferences.containsKey(key),
            value = allPreferences[key],
        )
    }

    private fun restorePreferenceSnapshots(
        snapshots: Map<String, PreferenceValueSnapshot>,
    ): Boolean {
        val editor = prefs().edit()
        editor.restoreSnapshots(snapshots)
        return editor.commit()
    }

    private fun android.content.SharedPreferences.Editor.restoreSnapshots(
        snapshots: Map<String, PreferenceValueSnapshot>,
    ) {
        val editor = this
        snapshots.forEach { (key, snapshot) ->
            if (!snapshot.present) {
                editor.remove(key)
                return@forEach
            }
            when (val value = snapshot.value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Float -> editor.putFloat(key, value)
                is Long -> editor.putLong(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> {
                    val strings = value.filterIsInstance<String>().toSet()
                    if (strings.size == value.size) editor.putStringSet(key, strings) else editor.remove(key)
                }
                null -> editor.remove(key)
                else -> editor.remove(key)
            }
        }
    }

    override fun setRemoteLatencyMode(mode: Int) {
        ui.latencyMode = mode.coerceIn(0, 2)
        prefs().edit { putInt("remote_latency_mode", ui.latencyMode) }
        PhosphorNative.remoteSetLatencyMode(ui.latencyMode)
    }

    private fun applyLocalGainPolicy() {
        instrumentWorkflow?.settle("Source gain policy changed. Pending apply cancelled.")
        val on = if (instrumentWorkflow?.unsaved == true) ui.localAutoGain else prefs().getBoolean("auto_gain", true)
        PhosphorNative.setGain(gainValue) // restores the remembered manual landing
        PhosphorNative.setAutoFrameScale(autoFrameScale)
        PhosphorNative.setGainAuto(on)
        ui.gain = gainValue
        ui.manualGain = gainValue
        ui.autoGain = on
        ui.localAutoGain = on
        refreshInstrumentState()
    }

    private fun applyImmersive() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            if (ui.fullscreen) {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    override fun setFullscreen(on: Boolean) {
        ui.fullscreen = on
        prefs().edit { putBoolean("fullscreen", on) }
        applyImmersive()
    }

    override fun isScopeRotationLocked(): Boolean = scopeRotationLockState

    override fun setScopeRotationLocked(locked: Boolean) {
        if (!rotationAllowed()) return
        if (scopeRotationLockState == locked) return
        scopeRotationLockState = locked
        if (locked) lockedScopeOrientation = exactCurrentOrientation()
        prefs().edit {
            putBoolean("scope_rotation_locked", locked)
            putInt("scope_locked_orientation", lockedScopeOrientation)
        }
        applyScopeRotationPreference()
        // The sensor must run for scope-locked + UI-follow (chrome-to-gravity) too.
        updateOrientationSensor()
    }

    override fun isUiPlacementLocked(): Boolean = uiPlacementLockState
    override fun lockedUiLandscape(): Boolean = lockedUiLandscape

    override fun setUiPlacementLocked(locked: Boolean) {
        if (!rotationAllowed()) return
        if (uiPlacementLockState == locked) return
        if (locked) {
            lockedUiLandscape =
                resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            lockedUiOrientation = exactCurrentOrientation()
        }
        uiPlacementLockState = locked
        applyScopeRotationPreference()
        updateOrientationSensor()
        prefs().edit {
            putBoolean("ui_placement_locked", locked)
            putBoolean("ui_locked_landscape", lockedUiLandscape)
            putInt("ui_locked_orientation", lockedUiOrientation)
        }
    }

    /** Refresh before mutations, including callbacks retained before Android locked rotation. */
    private fun rotationAllowed(): Boolean {
        if (!taskIsCurrent()) return false
        val locked = runCatching {
            android.provider.Settings.System.getInt(
                contentResolver,
                android.provider.Settings.System.ACCELEROMETER_ROTATION,
                0,
            ) != 1
        }.getOrDefault(true)
        if (ui.systemRotationLocked != locked) rotationAuthorityNeedsRouting = true
        ui.systemRotationLocked = locked
        if (locked) {
            // LOCKED holds Android's observed current orientation, not a saved cardinal.
            if (requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_LOCKED) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
            }
        }
        return !locked
    }

    private fun refreshRotationAuthority(force: Boolean = false) {
        if (!rotationAllowed()) return
        if (rotationAuthorityNeedsRouting || force) {
            rotationAuthorityNeedsRouting = false
            applyScopeRotationPreference()
            routeOrientation(force = true)
        }
    }

    private fun applyScopeRotationPreference() {
        if (!rotationAllowed()) return
        requestedOrientation = if (scopeRotationLockState) {
            if (lockedScopeOrientation in setOf(
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                    ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                    ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
                )
            ) lockedScopeOrientation else exactCurrentOrientation()
        } else if (uiPlacementLockState) {
            // UI PLACEMENT locked with scope free: the Activity never rotates — the
            // BEAM follows gravity instead (see the orientation sensor). Chrome
            // physically cannot move; scope content stays upright.
            if (lockedUiOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
                lockedUiOrientation else exactCurrentOrientation()
        } else {
            // NO LOCK — the detent owns rotation here, not Android. Returning
            // SCREEN_ORIENTATION_UNSPECIFIED at this point is what kept undoing the
            // detent: this runs on resume, on config change and after settings imports,
            // so whatever the detent had pinned was overwritten moments later and
            // Android's twitchy sensor logic took the wheel again.
            applyDetentedOrientation()
            return
        }
    }

    // Runs ALWAYS, not only under a lock. The detent governs plain rotation too: with
    // no lock the Activity used to be SCREEN_ORIENTATION_UNSPECIFIED, which hands the
    // decision to Android's own (very twitchy) sensor logic, so the detent was dead code
    // for anyone who had not turned a lock on. Ben reported
    // rotation "still super sensitive" for exactly this reason.
    private fun updateOrientationSensor() {
        if (!activityStarted || !ui.presentationVisible || ui.pip) {
            gravityListener?.let { getSystemService(android.hardware.SensorManager::class.java)?.unregisterListener(it) }
            gravityListener = null
            return
        }
        if (!taskIsCurrent()) return
        run {
            if (gravityListener == null) {
                val sm = getSystemService(android.hardware.SensorManager::class.java)
                val accel = sm?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
                if (accel != null) {
                    gravityListener = object : android.hardware.SensorEventListener {
                        private var gx = 0f; private var gy = 0f; private var gz = 0f
                        override fun onAccuracyChanged(s: android.hardware.Sensor?, a: Int) {}
                        override fun onSensorChanged(e: android.hardware.SensorEvent) {
                            if (!taskIsCurrent() || gravityListener !== this) return
                            // Low-pass to gravity, then two gates before any quadrant
                            // moves: (1) FLATNESS — a phone within ~20° of lying flat has
                            // no meaningful "up"; a desk phone must never rotate its
                            // chrome (Ben: "rotations are messed up"). (2) A DETENT —
                            // see below.
                            gx = 0.8f * gx + 0.2f * e.values[0]
                            gy = 0.8f * gy + 0.2f * e.values[1]
                            gz = 0.8f * gz + 0.2f * e.values[2]
                            val horiz = kotlin.math.sqrt(gx * gx + gy * gy)
                            if (horiz < 3.4f) return // flatter than ~20° tilt: hold
                            val degrees = ((Math.toDegrees(
                                kotlin.math.atan2(-gx.toDouble(), gy.toDouble())
                            ) + 360.0) % 360.0).toInt()
                            // The detent (Ben: "you have to really rotate it and then
                            // it's set"). Not a timer — a timer makes a correct turn feel
                            // laggy. The rule is asymmetric instead: keeping the current
                            // orientation is easy, taking a new one needs a real turn.
                            // Lives in RotationDetent so it can be tested on the host.
                            if (!RotationDetent.shouldCommit(committedCardinal, degrees)) return
                            val previousCardinal = committedCardinal
                            committedCardinal = RotationDetent.nearestCardinal(degrees)
                            lastSensorDeg = degrees
                            // Route when the COMMITTED ORIENTATION changes, not when the
                            // raw angle does. Gating on the raw degree meant a phone held
                            // steady at one angle stopped routing entirely (the reading
                            // repeats), while a phone jittering by a degree routed
                            // constantly. The orientation is the thing that matters.
                            if (previousCardinal != committedCardinal) routeOrientation()
                        }
                    }.also {
                        android.util.Log.i("PhosphorRotation", "gravity sensor registered")
                        sm.registerListener(
                            it, accel, android.hardware.SensorManager.SENSOR_DELAY_UI
                        )
                    }
                }
            }
            // A mode toggle re-routes the last known gravity now: the sensor only fires
            // on CHANGE, so a stationary phone would otherwise keep the prior mode's fields.
            routeOrientation(force = true)
        }
    }

    // q is the counter-clockwise quadrant from the pinned display to gravity-up.
    private fun routeOrientation(force: Boolean = false) {
        if (!rotationAllowed()) return
        val next = RotationDetent.presentation(
            systemRotationLocked = ui.systemRotationLocked,
            current = ui.rotationPresentation,
            scopeLocked = scopeRotationLockState,
            uiLocked = uiPlacementLockState,
            cardinal = committedCardinal,
            displayQuadrant = currentDisplayRotation(),
        )
        if (next != ui.rotationPresentation || force) {
            ui.rotationPresentation = next
            PhosphorNative.setViewRotation(next.beamQuadrant)
        }
        // The free Activity follows the committed gravity cardinal, not a display delta.
        if (!scopeRotationLockState && !uiPlacementLockState) {
            applyDetentedOrientation()
        }
    }

    /**
     * Pin the Activity to the orientation the detent has committed to.
     *
     * Respecting the user's OS rotation lock matters here: if they have locked their
     * phone to portrait system-wide, an app that rotates anyway is broken, however good
     * its detent is.
     */
    private fun applyDetentedOrientation() {
        if (!rotationAllowed()) return
        if (scopeRotationLockState || uiPlacementLockState) return
        val target = when (RotationDetent.screenTarget(committedCardinal)) {
            RotationDetent.ScreenTarget.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            RotationDetent.ScreenTarget.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            RotationDetent.ScreenTarget.REVERSE_PORTRAIT ->
                ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            RotationDetent.ScreenTarget.REVERSE_LANDSCAPE ->
                ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            RotationDetent.ScreenTarget.UNSPECIFIED ->
                return // No credible gravity yet. Keep the current orientation.
        }
        if (requestedOrientation != target) {
            // Keep the detent decision observable during physical rotation tests.
            android.util.Log.i(
                "PhosphorRotation",
                "detent commit=$committedCardinal deg=$lastSensorDeg -> $target",
            )
            requestedOrientation = target
        }
    }

    private fun exactCurrentOrientation(): Int {
        val rotation = currentDisplayRotation()
        return when (resources.configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE ->
                if (rotation == Surface.ROTATION_270)
                    ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            Configuration.ORIENTATION_PORTRAIT ->
                if (rotation == Surface.ROTATION_180)
                    ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    private fun currentDisplayRotation(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }

    // Android can reveal system bars when focus returns, so restore the selected immersive state.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        signalFocused = hasFocus
        activityFocused = hasFocus
        applyBrightnessPin()
        if (hasFocus) {
            refreshRotationAuthority(force = true)
            applyImmersive()
            reassertSourceWake()
        }
    }

    override fun orbitBy(dyaw: Float, dpitch: Float) = PhosphorNative.orbitBy(dyaw, dpitch)
    override fun dollyBy(delta: Float) = PhosphorNative.dollyBy(delta)

    private fun android.content.SharedPreferences.Editor.putLight(light: LightSettings) {
        light.values().forEach { (key, value) ->
            when (value) {
                is Boolean -> putBoolean(key, value)
                is Int -> putInt(key, value)
                is Float -> putFloat(key, value)
                is String -> putString(key, value)
            }
        }
    }

    private fun publishNativeLight(safe: LightSettings, deletedSlot: Int = -1): Boolean =
        PhosphorNative.setLight(safe.slots.flatMap { it.components() }.toFloatArray(),
            safe.selectedMask, safe.preset, safe.seconds, safe.perTrack, safe.generatedAuto,
            safe.shuffle, safe.randomInterval, safe.intervalMin, safe.intervalMax, deletedSlot)

    override fun setLight(settings: LightSettings) { instrumentEdit { applyLight(settings) } }
    override fun deleteLightSlot(index: Int) = instrumentEdit {
        if (index in ui.light.slots.indices) applyLight(ui.light.delete(index), index)
    }

    private fun applyLight(settings: LightSettings, deletedSlot: Int = -1): Boolean = instrumentValueEdit(false) { settingsWriteOwner.write {
        val guarded = LightCycleGuard.evaluate(settings, epilepsyAcknowledged())
        val safe = guarded.safe
        val prior = preferenceValueSnapshots(prefs().all, safe.values().keys)
        val editor = prefs().edit()
        editor.putLight(safe)
        val failure = settingsWriteOwner.commit(
            commit = { editor.commit() },
            publish = { publishNativeLight(safe, deletedSlot) },
            rollback = { restorePreferenceSnapshots(prior) },
        )
        if (failure != null) {
            ui.lightError = reportTuningWriteFailure(failure)
            return@write false
        }
        // Copy-before-remove. A failed cleanup leaves a harmless rollback-readable key.
        if (prefs().contains("custom_count")) prefs().edit().remove("custom_count").commit()
        ui.light = safe
        ui.beamIndex = safe.preset
        ui.lightPending = guarded.pending
        ui.lightTemporary = false
        ui.lightError = ""
        true
    } }

    override fun rollLight() = instrumentEdit {
        if (PhosphorNative.rollLight()) ui.lightTemporary = !ui.light.generatedAuto
        else ui.lightError = "Renderer could not roll a color. Reopen Phosphor and try again."
    }

    override fun setCustomBeam(colors: List<androidx.compose.ui.graphics.Color>, count: Int) = instrumentEdit {
        setLight(ui.light.copy(slots = colors.map { LightRgb(it.red, it.green, it.blue) }, selectedMask = (1 shl count) - 1))
    }

    override fun setBeamCycle(seconds: Float, perTrack: Boolean) = instrumentEdit {
        setLight(ui.light.copy(seconds = seconds, perTrack = perTrack))
    }

    // Photosensitivity acceptance persists forever, as on desktop.
    override fun epilepsyAcknowledged(): Boolean = runtimePrefs().getBoolean("epilepsy_ack", false)
    override fun ackEpilepsy() { runtimePrefs().edit { putBoolean("epilepsy_ack", true) } }

    // Keep mobile tuning limits aligned with the desktop engine.
    private fun applyBeamEnergy(e: Float) { PhosphorNative.setBeamEnergy(e); ui.beamEnergy = e.coerceIn(1f, 30f) }
    private fun applyGlow(g: Float) { PhosphorNative.setGlow(g); ui.glow = g.coerceIn(0f, 0.98f) }
    private fun rollIn(lo: Float, hi: Float) = lo + kotlin.random.Random.nextFloat() * (hi - lo)

    // Manual drag of a rule is a takeover: it disarms that die, exactly like picking a
    // mode disarms the mode-⚄.
    override fun setBeamEnergy(e: Float) = instrumentEdit { ui.beamRandomArmed = false; applyBeamEnergy(e) }
    override fun setGlow(g: Float) = instrumentEdit { ui.glowRandomArmed = false; applyGlow(g) }

    // The checkbox is a true toggle: check = arm + roll now; uncheck = disarm, the last
    // rolled value simply stays on the rule.
    override fun tapBeamRandom() = instrumentEdit {
        ui.beamRandomArmed = !ui.beamRandomArmed
        if (ui.beamRandomArmed) applyBeamEnergy(rollIn(ui.beamRandomLo, ui.beamRandomHi))
    }
    override fun tapGlowRandom() = instrumentEdit {
        ui.glowRandomArmed = !ui.glowRandomArmed
        if (ui.glowRandomArmed) applyGlow(rollIn(ui.glowRandomLo, ui.glowRandomHi))
    }
    override fun setBeamRandomRange(lo: Float, hi: Float) = instrumentEdit {
        ui.beamRandomLo = lo.coerceIn(1f, 30f)
        ui.beamRandomHi = hi.coerceIn(ui.beamRandomLo, 30f)
    }
    override fun setGlowRandomRange(lo: Float, hi: Float) = instrumentEdit {
        ui.glowRandomLo = lo.coerceIn(0f, 0.98f)
        ui.glowRandomHi = hi.coerceIn(ui.glowRandomLo, 0.98f)
    }
    override fun setGeomFx(kind: Int) = instrumentEdit { ui.geomFx = kind.coerceIn(0, 4); PhosphorNative.setGeomFx(ui.geomFx) }
    override fun setGeomAmount(v: Float) = instrumentEdit { ui.geomAmount = v.coerceIn(0f, 1f); PhosphorNative.setGeomAmount(ui.geomAmount) }
    override fun setGrid(on: Boolean) = instrumentEdit { PhosphorNative.setGrid(on); ui.grid = on }
    override fun setGridData(on: Boolean) = instrumentEdit {
        ui.gridData = on
        ui.gridReading = null
        prefs().edit { putBoolean(dev.phosphor.mobil3.ui.GridData.KEY, on) }
    }

    // ── Deck sheet verbs ──
    override fun openFolder() {
        selectSource(signalSelected)
        openFolderLauncher.launch(null)
    }
    override fun jumpToQueue(index: Int) { controller?.seekTo(index, 0) }

}

/** Failure-only publication. Generic recorder stop must not clear a replacement source face. */
internal fun publishMicReaderFailure(ui: ScopeUiState, isCurrent: () -> Boolean, report: () -> Unit) {
    if (!isCurrent() || !ui.live || ui.sourceLabel != "mic") return
    ui.live = false
    ui.sourceLabel = "no source"
    report()
}

internal fun runtimeInputSource(ui: ScopeUiState, micRecording: Boolean): String = when {
    ui.live && ui.sourceLabel == "capture" -> "capture"
    ui.live && ui.sourceLabel == "mic" && micRecording -> "mic"
    else -> "none"
}
