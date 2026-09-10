package dev.phosphor.mobil3.ui

import androidx.compose.ui.graphics.toArgb

import android.content.res.Configuration
import android.os.SystemClock
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.phosphor.mobil3.PhosphorNative
import dev.phosphor.mobil3.AcceptanceTrace
import kotlinx.coroutines.delay

/** Layout handles stay at this boundary. The arbiter sees only root-space numbers. */
internal class StageGeometry {
    var root: LayoutCoordinates? = null
    var stage: LayoutCoordinates? = null
    val bounds = StageChromeBounds()
    class Measurement { var coordinates: LayoutCoordinates? = null }
    private val cards = arrayOfNulls<Measurement>(StageChromeBounds.Card.entries.size)

    fun rootBounds(): Rect? = root?.takeIf { it.isAttached }?.let {
        Rect(0f, 0f, it.size.width.toFloat(), it.size.height.toFloat())
    }

    fun position(coordinates: LayoutCoordinates?, point: Offset): Offset? {
        val reference = root?.takeIf { it.isAttached } ?: return null
        val source = coordinates?.takeIf { it.isAttached } ?: return null
        return reference.localPositionOf(source, point)
    }

    fun mount(card: StageChromeBounds.Card, measurement: Measurement) {
        cards[card.ordinal] = measurement
        bounds.mount(card, measurement, SystemClock.uptimeMillis())
    }

    fun sample(card: StageChromeBounds.Card, measurement: Measurement, now: Long) {
        val coordinates = measurement.coordinates?.takeIf { it.isAttached } ?: return
        val w = coordinates.size.width.toFloat()
        val h = coordinates.size.height.toFloat()
        val corners = listOf(Offset.Zero, Offset(w, 0f), Offset(w, h), Offset(0f, h))
            .map { position(coordinates, it) ?: return }
        bounds.sample(card, measurement, Rect(
            corners.minOf { it.x }, corners.minOf { it.y },
            corners.maxOf { it.x }, corners.maxOf { it.y },
        ), now)
    }

    fun refresh(now: Long) {
        StageChromeBounds.Card.entries.forEach { card ->
            cards[card.ordinal]?.let { sample(card, it, now) }
        }
    }

    fun dismiss(card: StageChromeBounds.Card, measurement: Measurement) {
        val now = SystemClock.uptimeMillis()
        sample(card, measurement, now)
        bounds.dismiss(card, measurement, now)
        measurement.coordinates = null
        if (cards[card.ordinal] === measurement) cards[card.ordinal] = null
    }
}

internal val LocalStageGeometry = compositionLocalOf<StageGeometry?> { null }

/** Samples layer-only motion during its existing transition, including final placement. */
@Composable
internal fun Modifier.stageChromeBounds(
    card: StageChromeBounds.Card,
    moving: Boolean,
    motionValue: Any? = null,
): Modifier {
    val geometry = LocalStageGeometry.current ?: return this
    val measurement = remember(geometry, card) { StageGeometry.Measurement() }
    val currentMoving by rememberUpdatedState(moving)
    DisposableEffect(geometry, measurement) {
        geometry.mount(card, measurement)
        onDispose { geometry.dismiss(card, measurement) }
    }
    SideEffect { geometry.bounds.motion(card, measurement, moving, SystemClock.uptimeMillis()) }
    LaunchedEffect(geometry, measurement, moving, motionValue) {
        do {
            withFrameNanos { geometry.sample(card, measurement, SystemClock.uptimeMillis()) }
        } while (currentMoving)
        // Frame callbacks precede layout. One more frame reads the final applied layer.
        withFrameNanos { geometry.sample(card, measurement, SystemClock.uptimeMillis()) }
    }
    return onGloballyPositioned {
        measurement.coordinates = it
        geometry.sample(card, measurement, SystemClock.uptimeMillis())
    }
}

// What the chrome can ask the host to do. Keeps Compose free of Android service plumbing.
interface ScopeActions : InstrumentPresetActions, AppearanceActions {
    fun toggleDisplayPause() {}
    fun resetInspection() {}
    fun setPauseBlack(black: Boolean) {}
    fun togglePlay()
    fun openFile()
    fun exportSettings()
    fun importSettings()
    fun startMic()
    fun chooseMicrophone(id: Int)
    fun confirmMicrophoneBluetooth(accept: Boolean)
    fun setIncludeMicrophone(on: Boolean)
    fun setMicrophoneMixLevel(microphone: Boolean, value: Float)
    fun retryMicrophone()
    fun stopMicrophone()
    fun startCapture()
    fun startStandardCapture() {}
    fun setRootCapture(enabled: Boolean) {}
    fun openRootManager() {}
    fun stopLive()
    fun captureConsentNeeded(): Boolean
    fun next()
    fun prev()
    fun seekTo(ms: Long)
    fun startRemote()
    fun setMode(index: Int)
    fun setRandomBanModes(modes: Set<Int>) {}
    fun setBeam(index: Int)
    fun setFps(value: Int)
    fun setOversample(n: Int)
    fun setRoom(room: Palette)
    fun setRoomStyle(overrides: StyleOverride) {}
    fun setFocus(focus: Float)
    fun setCustomBeam(colors: List<androidx.compose.ui.graphics.Color>, count: Int)
    fun setBeamCycle(seconds: Float, perTrack: Boolean)
    fun setLight(settings: LightSettings) {}
    fun deleteLightSlot(index: Int) {}
    fun rollLight() {}
    fun setBeamEnergy(e: Float)
    fun setGlow(g: Float)
    fun tapBeamRandom()
    fun setBeamRandomRange(lo: Float, hi: Float)
    fun tapGlowRandom()
    fun setGlowRandomRange(lo: Float, hi: Float)
    fun setGeomFx(kind: Int)
    fun setGeomAmount(v: Float)
    fun setGrid(on: Boolean)
    fun setGridData(on: Boolean)
    fun setGainAuto(on: Boolean)
    fun setViewLock(on: Boolean)
    fun setHudMode(mode: Int)
    fun setFullscreen(on: Boolean)
    fun setLingerBackground(on: Boolean)
    fun setControlsAlwaysVisible(on: Boolean)
    fun setPinScreenBrightness(on: Boolean)
    fun setHdrRequested(on: Boolean)
    fun setPipAutoEnter(on: Boolean)
    fun enterPictureInPicture()
    fun showFloatingHud() {}
    fun hideFloatingHud() {}
    fun setFloatingHudEnabled(on: Boolean) {}
    fun setFloatingHudTransparent(on: Boolean) {}
    fun setDoubleTapPlayback(on: Boolean)
    fun openCaptureMetadataSettings()
    fun openLink(url: String)
    fun markBestiaryFound()
    fun isScopeRotationLocked(): Boolean
    fun setScopeRotationLocked(locked: Boolean)
    fun isUiPlacementLocked(): Boolean
    fun lockedUiLandscape(): Boolean
    fun setUiPlacementLocked(locked: Boolean)
    fun setRemoteLatencyMode(mode: Int)
    fun remoteHosts(): List<Pair<String, Pair<String, Int>>>
    /**
     * Add, edit, or remove a saved relay. Returns null on success, or the store's
     * fix-bearing refusal text so the sheet can show the user exactly why it declined.
     * The empty-string [existingHost] means "add", matching the editor's null [existing].
     */
    fun saveRemoteHost(
        existingHost: String,
        existingPort: Int,
        label: String,
        host: String,
        port: String,
    ): String?
    fun removeRemoteHost(host: String, port: Int): String?
    fun startRemoteHost(label: String, host: String, port: Int)
    fun setRemoteStreams(audio: Boolean, geometry: Boolean)
    fun disconnectRemote()
    fun epilepsyAcknowledged(): Boolean
    fun ackEpilepsy()
    fun setGainAbsolute(g: Float)
    fun orbitBy(dyaw: Float, dpitch: Float)
    fun dollyBy(delta: Float)
    fun openFolder()
    fun jumpToQueue(index: Int)
    fun makeSurface(): SurfaceView
}

@Composable
fun PhosphorScreen(state: ScopeUiState, actions: ScopeActions, reduced: Boolean) {
    val settingsPresentation = rememberSettingsPresentationState()
    if (!state.presentationVisible) return
    if (state.pip) {
        AndroidView(factory = { actions.makeSurface() }, modifier = Modifier.fillMaxSize())
        return
    }
    // Only authored revisions start a finite transition. The measured beam tick stays immediate.
    val target = state.room
    var fromRoom by remember { mutableStateOf(target) }
    var lastShown by remember { mutableStateOf(target) }
    val fade = remember { Animatable(1f) }
    var lastRevision by remember { mutableStateOf(state.appearanceRevision) }
    LaunchedEffect(state.appearanceRevision, reduced) {
        if (lastRevision != state.appearanceRevision) {
            fromRoom = lastShown
            if (reduced || state.appearanceStyle.motion == MotionFeel.Cut) {
                fade.snapTo(1f)
                lastRevision = state.appearanceRevision
            } else {
                fade.snapTo(0f)
                lastRevision = state.appearanceRevision
                fade.animateTo(1f, tween((Motion.room * state.appearanceStyle.durationScale).toInt().coerceIn(80, 200), easing = LinearEasing))
            }
        } else if (reduced) fade.snapTo(1f)
    }
    val t = fade.value
    // Until the effect owns this revision, do not let SideEffect record the new target as already shown.
    val p = if (lastRevision != state.appearanceRevision) lastShown
        else if (t >= 1f) target else fromRoom.lerpTo(target, smoothstep(t))
    SideEffect { lastShown = p }
    val style = state.appearanceStyle
    val view = LocalView.current
    val density = LocalDensity.current
    val actualLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Saved choices may change during import while Android holds the presentation.
    val rotation = state.rotationPresentation
    val uiLocked = rotation.uiPlacementLocked
    val chromeQuadrant = rotation.chromeQuadrant
    // Android may ignore an orientation request. Layout follows the observed frame.
    val chromeLandscape = RotationDetent.chromeLandscape(rotation, actualLandscape)
    var consoleVisible by remember { mutableStateOf(true) }
    val consoleShown = ControlsVisibilityPolicy.visible(consoleVisible, state.controlsAlwaysVisible)
    LaunchedEffect(state.controlsAlwaysVisible) {
        if (state.controlsAlwaysVisible) consoleVisible = true
    }
    var sheet by remember { mutableStateOf(Sheet.NONE) }
    LaunchedEffect(state.showSourcePicker) {
        if (state.showSourcePicker) { sheet = Sheet.SOURCE; state.showSourcePicker = false }
    }
    LaunchedEffect(state.showInstrumentPresets) {
        if (state.showInstrumentPresets) { sheet = Sheet.INSTRUMENT; state.showInstrumentPresets = false }
    }
    var manualFrom by remember { mutableStateOf(Sheet.SETTINGS) } // where MANUAL returns to
    var overflowComposed by remember { mutableStateOf(false) }
    var overflowTargetOpen by remember { mutableStateOf(false) }
    var overflowPendingSheet by remember { mutableStateOf(Sheet.NONE) }
    var settingsPullActive by remember { mutableStateOf(false) }
    var bottomEdgePullActive by remember { mutableStateOf(false) }
    var rootHeightPx by remember { mutableIntStateOf(0) }
    // Width matters as well as height now: a locked-landscape sheet slides in sideways,
    // so its pull travel is measured across the screen rather than up it.
    var rootWidthPx by remember { mutableIntStateOf(0) }
    var consoleHeightPx by remember { mutableIntStateOf(0) }
    var statusBandHeightPx by remember { mutableIntStateOf(0) }
    val ribbon = remember { RibbonState() }
    val stageGeometry = remember { StageGeometry() }
    val currentActions by rememberUpdatedState(actions)
    val revealScope = rememberCoroutineScope()
    val settingsReveal = remember(revealScope) { PullRevealState(revealScope) }
    val overflowReveal = remember(revealScope) { PullRevealState(revealScope) }
    val overflowGestureActive = remember { mutableStateOf(false) }
    val currentStyle = rememberUpdatedState(style)
    val currentReduced = rememberUpdatedState(reduced)
    val currentChromeLandscape = rememberUpdatedState(chromeLandscape)
    val currentUiLocked = rememberUpdatedState(uiLocked)
    val flickVelocityPx = with(density) { Dim.chromeFlickVelocity.toPx() }
    val popoutTravelPx = with(density) { Dim.popoutPullTravel.toPx() }

    val finishOverflowClosed = {
        overflowComposed = false
        overflowTargetOpen = false
        val next = overflowPendingSheet
        overflowPendingSheet = Sheet.NONE
        if (next != Sheet.NONE) sheet = next
    }
    val closeOverflow: (Sheet) -> Unit = { next ->
        overflowPendingSheet = next
        overflowTargetOpen = false
        overflowReveal.settleTo(false, style, reduced) { open ->
            if (!open) finishOverflowClosed()
        }
    }
    val openOverflow = {
        if (sheet == Sheet.NONE) {
            overflowPendingSheet = Sheet.NONE
            overflowComposed = true
            overflowTargetOpen = true
            overflowReveal.setTravelPx(popoutTravelPx)
            overflowReveal.settleTo(true, style, reduced)
        }
    }

    // Predictive back peels one layer at a time: popout → sheet → console → system.
    BackHandler(enabled = overflowComposed) { closeOverflow(Sheet.NONE) }
    BackHandler(enabled = !overflowComposed && sheet != Sheet.NONE) { sheet = Sheet.NONE }
    BackHandler(enabled = !overflowComposed && sheet == Sheet.NONE && consoleShown && !state.controlsAlwaysVisible) {
        consoleVisible = ControlsVisibilityPolicy.afterHide(state.controlsAlwaysVisible)
    }

    // Console auto-hides after 4 s of no interaction (burn-in + clean stage).
    LaunchedEffect(consoleShown, sheet, overflowComposed, state.controlsVisibilityRevision) {
        val revision = state.controlsVisibilityRevision
        if (consoleShown && sheet == Sheet.NONE && !overflowComposed && !state.controlsAlwaysVisible) {
            delay(4000)
            if (ControlsVisibilityPolicy.timeoutCanHide(
                    state.controlsAlwaysVisible, revision, state.controlsVisibilityRevision,
                )) consoleVisible = ControlsVisibilityPolicy.afterHide(state.controlsAlwaysVisible)
        }
    }

    val sheetActions = remember(actions) {
        object : SheetActions {
            override fun previewAppearance(value: dev.phosphor.mobil3.settings.appearance.AppearanceValue) = actions.previewAppearance(value)
            override fun applyAppearance(value: dev.phosphor.mobil3.settings.appearance.AppearanceValue, id: String) = actions.applyAppearance(value, id)
            override fun selectAppearance(id: String) = actions.selectAppearance(id)
            override fun cancelAppearancePreview() = actions.cancelAppearancePreview()
            override fun saveAppearance(name: String, value: dev.phosphor.mobil3.settings.appearance.AppearanceValue, id: String?) = actions.saveAppearance(name, value, id)
            override fun renameAppearance(id: String, name: String) = actions.renameAppearance(id, name)
            override fun deleteAppearance(id: String) = actions.deleteAppearance(id)
            override fun resetAppearance() = actions.resetAppearance()
            override fun repairAppearance() = actions.repairAppearance()
            override fun recoverAppearance() = actions.recoverAppearance()
            override fun openFile() = actions.openFile()
            override fun exportSettings() = actions.exportSettings()
            override fun importSettings() = actions.importSettings()
            override fun startMic() = actions.startMic()
            override fun chooseMicrophone(id: Int) = actions.chooseMicrophone(id)
            override fun confirmMicrophoneBluetooth(accept: Boolean) = actions.confirmMicrophoneBluetooth(accept)
            override fun setIncludeMicrophone(on: Boolean) = actions.setIncludeMicrophone(on)
            override fun setMicrophoneMixLevel(microphone: Boolean, value: Float) = actions.setMicrophoneMixLevel(microphone, value)
            override fun retryMicrophone() = actions.retryMicrophone()
            override fun stopMicrophone() = actions.stopMicrophone()
            override fun startCapture() = actions.startCapture()
            override fun startStandardCapture() = actions.startStandardCapture()
            override fun setRootCapture(enabled: Boolean) = actions.setRootCapture(enabled)
            override fun openRootManager() = actions.openRootManager()
            override fun startRemote() = actions.startRemote()
            override fun stopLive() = actions.stopLive()
            override fun captureConsentNeeded() = actions.captureConsentNeeded()
            override fun setFps(value: Int) = actions.setFps(value)
            override fun setOversample(n: Int) = actions.setOversample(n)
            override fun setGainAbsolute(g: Float) = actions.setGainAbsolute(g)
            override fun setGainAuto(on: Boolean) = actions.setGainAuto(on)
            override fun setViewLock(on: Boolean) = actions.setViewLock(on)
            override fun setBeamEnergy(e: Float) = actions.setBeamEnergy(e)
            override fun setGlow(g: Float) = actions.setGlow(g)
            override fun tapBeamRandom() = actions.tapBeamRandom()
            override fun setBeamRandomRange(lo: Float, hi: Float) =
                actions.setBeamRandomRange(lo, hi)
            override fun tapGlowRandom() = actions.tapGlowRandom()
            override fun setGlowRandomRange(lo: Float, hi: Float) =
                actions.setGlowRandomRange(lo, hi)
            override fun setGrid(on: Boolean) = actions.setGrid(on)
            override fun setGridData(on: Boolean) = actions.setGridData(on)
            override fun setHudMode(mode: Int) = actions.setHudMode(mode)
            override fun setFullscreen(on: Boolean) = actions.setFullscreen(on)
            override fun setLingerBackground(on: Boolean) = actions.setLingerBackground(on)
            override fun setControlsAlwaysVisible(on: Boolean) = actions.setControlsAlwaysVisible(on)
            override fun setPinScreenBrightness(on: Boolean) = actions.setPinScreenBrightness(on)
            override fun setHdrRequested(on: Boolean) = actions.setHdrRequested(on)
            override fun setPipAutoEnter(on: Boolean) = actions.setPipAutoEnter(on)
            override fun enterPictureInPicture() = actions.enterPictureInPicture()
            override fun showFloatingHud() = actions.showFloatingHud()
            override fun hideFloatingHud() = actions.hideFloatingHud()
            override fun toggleDisplayPause() = actions.toggleDisplayPause()
            override fun resetInspection() = actions.resetInspection()
            override fun setPauseBlack(black: Boolean) = actions.setPauseBlack(black)
            override fun setFloatingHudEnabled(on: Boolean) = actions.setFloatingHudEnabled(on)
            override fun setFloatingHudTransparent(on: Boolean) = actions.setFloatingHudTransparent(on)
            override fun setDoubleTapPlayback(on: Boolean) = actions.setDoubleTapPlayback(on)
            override fun openCaptureMetadataSettings() = actions.openCaptureMetadataSettings()
            override fun isScopeRotationLocked() = actions.isScopeRotationLocked()
            override fun setScopeRotationLocked(locked: Boolean) =
                actions.setScopeRotationLocked(locked)
            override fun isUiPlacementLocked() = actions.isUiPlacementLocked()
            override fun setUiPlacementLocked(locked: Boolean) =
                actions.setUiPlacementLocked(locked)
            override fun setRemoteLatencyMode(mode: Int) = actions.setRemoteLatencyMode(mode)
            override fun openRoom() { }
            override fun openLight() { }
            override fun openManual() { }
            override fun openLink(url: String) = actions.openLink(url)
            override fun remoteHosts() = actions.remoteHosts()
            override fun saveRemoteHost(
                existingHost: String,
                existingPort: Int,
                label: String,
                host: String,
                port: String,
            ) = actions.saveRemoteHost(existingHost, existingPort, label, host, port)
            override fun removeRemoteHost(host: String, port: Int) =
                actions.removeRemoteHost(host, port)
            override fun startRemoteHost(label: String, host: String, port: Int) =
                actions.startRemoteHost(label, host, port)
            override fun setRemoteStreams(audio: Boolean, geometry: Boolean) =
                actions.setRemoteStreams(audio, geometry)
            override fun disconnectRemote() = actions.disconnectRemote()
            override fun openFolder() = actions.openFolder()
            override fun jumpToQueue(index: Int) = actions.jumpToQueue(index)
        }
    }

    // Stable gesture hosts survive the recomposition triggered by beginning a pull.
    // Their current style, motion setting and applied frame use updated state holders.
    val settingsPullHost = remember(settingsReveal) {
        object : PullGestureHost {
            private var ignored = false

            override fun begin() {
                ignored = currentReduced.value || sheet != Sheet.NONE || overflowComposed ||
                    overflowGestureActive.value
                if (ignored) return
                settingsReveal.setTravelPx(
                    // Locked landscape slides the card in sideways, so the travel is a
                    // width. Using height there made the drag feel like it had to cover
                    // the whole screen before the card appeared.
                    if (currentChromeLandscape.value && currentUiLocked.value) {
                        if (rootWidthPx > 0) rootWidthPx * 0.82f
                        else with(density) { 560.dp.toPx() }
                    } else if (rootHeightPx > 0) rootHeightPx * 0.82f
                    else with(density) { 560.dp.toPx() }
                )
                settingsReveal.begin(resetClosed = true)
                settingsPullActive = true
                sheet = Sheet.SETTINGS
            }

            override fun dragBy(upwardDeltaPx: Float) {
                if (!ignored) settingsReveal.dragBy(upwardDeltaPx)
            }

            override fun release(verticalVelocityPxPerSecond: Float) {
                if (ignored) return
                settingsReveal.settleFromRelease(
                    verticalVelocityPxPerSecond,
                    flickVelocityPx,
                    currentStyle.value,
                    currentReduced.value,
                ) { open ->
                    settingsPullActive = false
                    if (!open) sheet = Sheet.NONE
                }
            }

            override fun cancel() {
                if (ignored) return
                settingsReveal.settleTo(
                    false, currentStyle.value, currentReduced.value,
                ) {
                    settingsPullActive = false
                    sheet = Sheet.NONE
                }
            }
        }
    }
    val overflowPullHost = remember(overflowReveal) {
        object : PullGestureHost {
            private var ignored = false

            override fun begin() {
                ignored = sheet != Sheet.NONE
                if (ignored) return
                overflowGestureActive.value = true
                val startsClosed = !overflowComposed
                overflowPendingSheet = Sheet.NONE
                overflowComposed = true
                overflowTargetOpen = true
                overflowReveal.setTravelPx(popoutTravelPx)
                overflowReveal.begin(resetClosed = startsClosed)
            }

            override fun dragBy(upwardDeltaPx: Float) {
                if (!ignored) overflowReveal.dragBy(upwardDeltaPx)
            }

            override fun release(verticalVelocityPxPerSecond: Float) {
                overflowGestureActive.value = false
                if (ignored) return
                overflowReveal.settleFromRelease(
                    verticalVelocityPxPerSecond,
                    flickVelocityPx,
                    currentStyle.value,
                    currentReduced.value,
                ) { open ->
                    overflowTargetOpen = open
                    if (!open) finishOverflowClosed()
                }
            }

            override fun cancel() {
                overflowGestureActive.value = false
                if (ignored) return
                overflowTargetOpen = false
                overflowReveal.settleTo(
                    false, currentStyle.value, currentReduced.value,
                ) { open -> if (!open) finishOverflowClosed() }
            }
        }
    }

    CompositionLocalProvider(
        LocalReducedMotion provides reduced,
        LocalRoomStyle provides style,
        LocalChromeLandscape provides chromeLandscape,
        LocalUiPlacementLocked provides uiLocked,
        LocalUiUpright provides state.uprightQuadrant,
        LocalStageGeometry provides stageGeometry,
    ) {
        Box(Modifier.fillMaxSize().onGloballyPositioned { stageGeometry.root = it }) {
            // Layer 0: the scope, full-bleed under everything. NEVER rotated by the
            // chrome container — the SurfaceView owns its own beam-rotation verb.
            AndroidView(factory = { actions.makeSurface() }, modifier = Modifier.fillMaxSize())

            // Picture-in-picture shows only the scope.
            if (state.pip) return@Box

          // The chrome overlay — band, console, popout, sheets, ribbon, stage — as one
          // unit. In the NEW mode it rotates to gravity around the pinned scope; the
          // custom layout swaps constraints on odd quadrants so the chrome lays out in
          // the transposed frame and pointer input stays correctly transformed. rootHeightPx
          // is measured INSIDE the container, so the settings pull travels in chrome space.
          CompositionLocalProvider(LocalChromeInsetQuadrant provides chromeQuadrant) {
          Box(Modifier.fillMaxSize().uprightRotate(chromeQuadrant)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { rootHeightPx = it.height; rootWidthPx = it.width }
            ) {
            // Layer 0.5: the stage — gesture arbiter (drags/pinches) + tap layer.
            // Sits BELOW the console so console controls win hit-testing in their bounds.
            if (sheet == Sheet.NONE || bottomEdgePullActive) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { stageGeometry.stage = it }
                        .stageGestures(
                            remember(actions, state, style.motion, reduced, state.displayPaused, state.pauseBlack) {
                                object : StageGestureHost {
                                    private var edgeTravelPx = 0f
                                    private var settingsHandedOff = false

                                    override fun physicalPosition(local: Offset) =
                                        stageGeometry.position(stageGeometry.stage, local)
                                    override fun physicalBounds() = stageGeometry.rootBounds()
                                    override fun chromeBlocks(points: List<Offset>, now: Long): Boolean {
                                        stageGeometry.refresh(now)
                                        return stageGeometry.bounds.blocks(
                                            points, with(density) { StageGesturePolicy.MARGIN_DP.dp.toPx() }, now,
                                        ).also { blocked ->
                                            AcceptanceTrace.record("chrome") {
                                                "evaluated_ms=$now blocked=$blocked ${stageGeometry.bounds.observation()}"
                                            }
                                        }
                                    }

                                    override fun inspecting() = state.displayPaused
                                    override fun inspect(dx: Float, dy: Float, scale: Float) =
                                        PhosphorNative.inspectHeld(dx, dy, scale, false)
                                    override fun currentGain() = state.gain
                                    override fun setGainAbsolute(g: Float) = actions.setGainAbsolute(g)
                                    // Only the explicit VIEW LOCK refuses gestures. A pinch
                                    // while AUTO-GAIN is armed is a manual takeover — the
                                    // setGainAbsolute path below disarms auto, same as the
                                    // Auto-gain owns the viewport and blocks manual gain gestures.
                                    override fun gainLocked() = state.viewLock
                                    override fun gainAutoArmed() = state.autoGain
                                    override fun orbitBy(dyaw: Float, dpitch: Float) =
                                        actions.orbitBy(dyaw, dpitch)
                                    override fun dollyBy(delta: Float) = actions.dollyBy(delta)
                                    override fun is3d() = state.mode3d
                                    override fun modeStep(delta: Int) =
                                        actions.setMode((state.modeIndex + delta + 11) % 11)
                                    override fun currentGlow() = state.glow
                                    override fun setGlowAbsolute(g: Float) = actions.setGlow(g)
                                    // The console owns its upward swipe while visible. Once it
                                    // settles away, only a one-finger pull born in the physical
                                    // bottom band may summon it; gain/orbit
                                    // keep every other clearly classified stage drag.
                                    override fun bottomPullArmed() =
                                        !ControlsVisibilityPolicy.visible(consoleVisible, state.controlsAlwaysVisible) && !overflowComposed
                                    override fun beginBottomChromePull() {
                                        edgeTravelPx = 0f
                                        settingsHandedOff = false
                                        bottomEdgePullActive = true
                                        consoleVisible = true
                                    }
                                    override fun dragBottomChromePull(
                                        upwardDeltaPx: Float,
                                    ) {
                                        val delta = upwardDeltaPx.coerceAtLeast(-edgeTravelPx)
                                        edgeTravelPx = (edgeTravelPx + delta).coerceAtLeast(0f)
                                        val handoffAt = maxOf(
                                            consoleHeightPx * 0.72f,
                                            with(density) { 96.dp.toPx() },
                                        )
                                        if (!settingsHandedOff && edgeTravelPx >= handoffAt) {
                                            settingsHandedOff = true
                                            if (currentReduced.value) {
                                                sheet = Sheet.SETTINGS
                                            } else {
                                                settingsPullHost.begin()
                                                settingsPullHost.dragBy(edgeTravelPx - handoffAt)
                                            }
                                        } else if (settingsHandedOff && !currentReduced.value) {
                                            settingsPullHost.dragBy(delta)
                                        }
                                    }
                                    override fun releaseBottomChromePull(velocityY: Float) {
                                        bottomEdgePullActive = false
                                        if (settingsHandedOff && !currentReduced.value) {
                                            settingsPullHost.release(velocityY)
                                        }
                                    }
                                    override fun cancelBottomChromePull() {
                                        bottomEdgePullActive = false
                                        if (settingsHandedOff && !currentReduced.value) {
                                            settingsPullHost.cancel()
                                        }
                                        settingsPullActive = false
                                        sheet = Sheet.NONE
                                        consoleVisible = ControlsVisibilityPolicy.afterHide(state.controlsAlwaysVisible)
                                    }
                                    override fun view() = view
                                }
                            },
                            ribbon,
                        )
                        .pointerInput(state.doubleTapPlayback) {
                            detectTapGestures(
                                onTap = {
                                    if (overflowComposed) closeOverflow(Sheet.NONE)
                                    else consoleVisible = ControlsVisibilityPolicy.afterTap(consoleVisible, state.controlsAlwaysVisible)
                                },
                                onDoubleTap = if (state.doubleTapPlayback) {
                                    { currentActions.togglePlay() }
                                } else null,
                            )
                        },
                )
            }

            val bandShown = state.bandMode == 0 || (state.bandMode == 1 && consoleShown)
            // The gesture readout ribbon rides above the stage.
            GestureRibbon(ribbon, p)
            if (sheet == Sheet.NONE && !overflowComposed && !state.pip &&
                (state.noSignal || state.sourceLabel == "no source" || (!state.live && state.captureStatus.isNotBlank()) || state.remoteFailure.isNotBlank())) {
                val signalTop = StageReadability.signalTopDp(bandShown, with(density) { statusBandHeightPx.toDp().value }).dp
                val plotPalette = p.copy(ink = androidx.compose.ui.graphics.Color(
                    StageReadability.plotInk(p.ink.toArgb() and 0xffffff) or 0xff000000.toInt()))
                Box(Modifier.align(Alignment.TopCenter).padding(top = signalTop)) {
                    SignalCheckAction("SIGNAL CHECK", plotPalette) { sheet = Sheet.SIGNAL_CHECK }
                }
            }
            if (state.displayPaused || state.displayPresentPending) {
                val pausePalette = p.readableOn(p.plane)
                Prose(state.pauseLabel, pausePalette.muted,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp)
                        .background(pausePalette.plane).padding(horizontal = 8.dp, vertical = 4.dp))
            }

            // Layer 1a: read-only status band.
            // Band visibility: on is persistent, auto follows the console timer, and off hides it.
            if (bandShown) {
                StatusBand(
                    state, p, reduced,
                    hudVisible = state.hudMode == 0 ||
                        (state.hudMode == 1 && consoleShown),
                    chromeVisible = sheet == Sheet.NONE && !overflowComposed,
                    onHeightChanged = { statusBandHeightPx = it },
                )
            }

            // The service-bench POST rides the warm-up (Annotated rooms only).
            if (style.designators) {
                Box(
                    Modifier.align(Alignment.BottomStart)
                        .windowInsetsPadding(chromeSafeDrawingInsets(18.dp, 18.dp))
                        .padding(
                            start = 18.dp,
                            bottom = if (chromeLandscape) 18.dp else 140.dp,
                        )
                ) { BenchPost(state, p, reduced, chromeVisible = sheet == Sheet.NONE && !overflowComposed) }
            }

            // Layer 1b: console strip, auto-hiding, with the settle-down exit.
            AnimatedVisibility(
                visible = consoleShown && (sheet == Sheet.NONE || settingsPullActive),
                enter = fadeIn(motionSpec(reduced, Motion.summon)),
                exit = fadeOut(motionSpec(reduced, Motion.settle)) +
                    slideOutVertically(motionSpec(reduced, Motion.settle)) { it / 12 },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Console(
                    state, p, reduced,
                    onMode = { sheet = Sheet.MODE },
                    onSrc = { sheet = Sheet.SOURCE },
                    onMore = {
                        if (overflowComposed && overflowTargetOpen) {
                            closeOverflow(Sheet.NONE)
                        } else openOverflow()
                    },
                    onPlay = { actions.togglePlay() },
                    onNext = { actions.next() },
                    onPrev = { actions.prev() },
                    onSeek = { actions.seekTo(it) },
                    onSettingsSwipe = {
                        if (!overflowComposed) sheet = Sheet.SETTINGS
                    },
                    settingsPullHost = settingsPullHost,
                    moreActive = overflowComposed,
                    overflowPullHost = overflowPullHost,
                    onHeightChanged = { consoleHeightPx = it },
                    chromeMoving = transition.isRunning,
                    chromeVisible = consoleShown && (sheet == Sheet.NONE || settingsPullActive),
                )
            }

            // The ⋯ overflow popout (Obsidian-persistent, anchored above the console).
            if (overflowComposed && sheet == Sheet.NONE) {
                val physicalInsets = chromeSafeDrawingInsets(0.dp, 0.dp, appliedQuadrant = 0)
                val direction = LocalLayoutDirection.current
                val insets = OverflowPopoutPolicy.rotatedInsets(
                    listOf(
                        physicalInsets.getLeft(density, direction), physicalInsets.getTop(density),
                        physicalInsets.getRight(density, direction), physicalInsets.getBottom(density),
                    ),
                    chromeQuadrant,
                )
                val consoleHeight = with(density) { consoleHeightPx.toDp() }
                BoxWithConstraints(
                    Modifier
                        .align(Alignment.BottomCenter)
                        // Console height already includes its bottom inset and margin.
                        .windowInsetsPadding(WindowInsets(left = insets[0], top = insets[1], right = insets[2])),
                ) {
                  val viewportHeight = OverflowPopoutPolicy.viewportHeight(
                      maxHeight.value, consoleHeight.value, Dim.popoutGap.value,
                  ).dp
                  Box(Modifier.padding(bottom = consoleHeight + Dim.popoutGap)) {
                    OverflowPopout(
                        p,
                        state = state,
                        reduced = reduced,
                        reveal = overflowReveal,
                        maxHeight = viewportHeight,
                        onPictureInPicture = { actions.enterPictureInPicture() },
                        onPipAutoEnter = { actions.setPipAutoEnter(!state.pipAutoEnter) },
                        onLight = { closeOverflow(Sheet.LIGHT) },
                        onRoom = { closeOverflow(Sheet.ROOM) },
                        onSettings = { closeOverflow(Sheet.SETTINGS) },
                        onFps = {
                            val order = FpsOptions.map { it.value }
                            val next = order[(order.indexOf(state.fpsValue) + 1) % order.size]
                            sheetActions.setFps(next)
                        },
                        onHud = {
                            sheetActions.setHudMode(nextHudMode(state.hudMode))
                        },
                        onGrid = { sheetActions.setGrid(!state.grid) },
                        onRequestClose = { closeOverflow(Sheet.NONE) },
                    )
                  }
                }
            }

            // Layer 2: sheets. Under UI PLACEMENT lock a sideways-held phone presents
            // the WHOLE sheet rotated to the viewer on a swapped-dimension canvas —
            // which is exactly the landscape layout (Ben: "portrait view again instead
            // of the side view"). The upright primitive re-measures with swapped
            // constraints. Settings keeps one vertical section order in either profile.
            val sheetQuadrant = if (uiLocked) state.uprightQuadrant else 0
            val sheetLandscape = chromeLandscape != (sheetQuadrant % 2 != 0)
            CompositionLocalProvider(
                LocalChromeLandscape provides sheetLandscape,
                LocalChromeInsetQuadrant provides (chromeQuadrant + sheetQuadrant),
                LocalUiPlacementLocked provides uiLocked,
                LocalSheetEntryQuadrant provides sheetQuadrant,
                LocalUiUpright provides 0, // content is already facing the viewer
            ) {
                Box(Modifier.uprightRotate(sheetQuadrant)) {
                    when (sheet) {
                Sheet.SOURCE -> SourceSheet(
                    state, p, reduced,
                    sheetActions.withSheetRouting(
                        openManual = { manualFrom = Sheet.SOURCE; sheet = Sheet.MANUAL },
                    ),
                ) { sheet = Sheet.NONE }
                Sheet.MODE -> ModeSheet(
                    state, p, reduced,
                    onPick = { actions.setMode(it) },
                    onGeomFx = { actions.setGeomFx(it) },
                    onGeomAmount = { actions.setGeomAmount(it) },
                    onBanModes = actions::setRandomBanModes,
                ) {
                    sheet = Sheet.NONE
                }
                Sheet.LIGHT -> LightSheetV2(
                    state, p, reduced,
                    onPickPreset = { actions.setBeam(it) },
                    onLightChange = { actions.setLight(it) },
                    onDeleteSlot = { actions.deleteLightSlot(it) },
                    onRoll = { actions.rollLight() },
                    epilepsyAcknowledged = { actions.epilepsyAcknowledged() },
                    ackEpilepsy = { actions.ackEpilepsy() },
                    onRecallInstrument = { actions.openInstrumentPresets(); sheet = Sheet.INSTRUMENT },
                ) { sheet = Sheet.NONE }
                Sheet.INSTRUMENT -> InstrumentPresetSheet(state, p, reduced, actions) { sheet = Sheet.NONE }
                Sheet.SIGNAL_CHECK -> SignalCheckSheet(state, p, reduced) { sheet = Sheet.NONE }
                Sheet.ROOM -> RoomSheet(state, p, reduced, onPick = { actions.setRoom(it) },
                    onStyle = actions::setRoomStyle) {
                    sheet = Sheet.NONE
                }
                Sheet.SETTINGS -> SettingsSheet(
                    state, p, reduced,
                    sheetActions.withSheetRouting(
                        openRoom = { sheet = Sheet.ROOM },
                        openLight = { sheet = Sheet.LIGHT },
                        openInstrument = { actions.openInstrumentPresets(); sheet = Sheet.INSTRUMENT },
                        openManual = { manualFrom = Sheet.SETTINGS; sheet = Sheet.MANUAL },
                    ),
                    focusValue = state.focus,
                    onFocus = actions::setFocus,
                    presentation = settingsPresentation,
                    entryReveal = if (settingsPullActive) settingsReveal else null,
                ) {
                    settingsPullActive = false
                    sheet = Sheet.NONE
                }
                Sheet.MANUAL -> ManualSheet(
                    p, reduced,
                    bestiaryFound = state.bestiaryFound,
                    onBestiaryFound = { actions.markBestiaryFound() },
                    onOpenLink = { actions.openLink(it) },
                ) { sheet = manualFrom }
                Sheet.NONE -> {}
                    }
                }
            }
            } // inner chrome box (rootHeightPx / chrome-space layout)
          } // chrome container — rotates as a unit in the NEW mode
          } // physical insets use the applied chrome frame
        } // outer box — holds the never-rotated scope SurfaceView
    }
}

// Route the settings sheet's room/light/manual links back into the sheet state machine.
private fun SheetActions.withSheetRouting(
    openRoom: () -> Unit = {},
    openLight: () -> Unit = {},
    openInstrument: () -> Unit = {},
    openManual: () -> Unit = {},
): SheetActions {
    val base = this
    return object : SheetActions by base {
        override fun openRoom() = openRoom()
        override fun openLight() = openLight()
        override fun openInstrument() = openInstrument()
        override fun openManual() = openManual()
    }
}
