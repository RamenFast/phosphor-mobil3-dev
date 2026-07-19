package dev.phosphor.mobil3.ui

import android.content.res.Configuration
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.phosphor.mobil3.PhosphorNative
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

// What the chrome can ask the host to do. Keeps Compose free of Android service plumbing.
interface ScopeActions {
    fun togglePlay()
    fun openFile()
    fun startMic()
    fun startCapture()
    fun stopLive()
    fun captureConsentNeeded(): Boolean
    fun next()
    fun prev()
    fun seekTo(ms: Long)
    fun startRemote()
    fun setMode(index: Int)
    fun setBeam(index: Int)
    fun setFps(value: Int)
    fun setOversample(n: Int)
    fun setRoom(room: Palette)
    fun setFocus(focus: Float)
    fun setCustomBeam(colors: List<androidx.compose.ui.graphics.Color>, count: Int)
    fun setBeamCycle(seconds: Float, perTrack: Boolean)
    fun setBeamEnergy(e: Float)
    fun setGlow(g: Float)
    fun setGrid(on: Boolean)
    fun setGainAuto(on: Boolean)
    fun setHudMode(mode: Int)
    fun setFullscreen(on: Boolean)
    fun openCaptureMetadataSettings()
    fun isScopeRotationLocked(): Boolean
    fun setScopeRotationLocked(locked: Boolean)
    fun isUiPlacementLocked(): Boolean
    fun lockedUiLandscape(): Boolean
    fun setUiPlacementLocked(locked: Boolean)
    fun setRemoteLatencyMode(mode: Int)
    fun setRemoteNetworkMode(mode: Int)
    fun remoteHosts(): List<Pair<String, Pair<String, Int>>>
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
    fun volumeFrac(): Float
    fun setVolume(frac: Float)
    fun makeSurface(): SurfaceView
}

@Composable
fun PhosphorScreen(state: ScopeUiState, actions: ScopeActions, reduced: Boolean) {
    // ── The 240 ms whole-chrome crossfade (spec §2.6): switching rooms lerps
    // every palette slot from what was ON SCREEN to the destination; the
    // discrete RoomStyle flips at the midpoint. Beam-breathing updates (same
    // room id) pass straight through un-animated.
    val target = state.room
    var fromRoom by remember { mutableStateOf(target) }
    var lastShown by remember { mutableStateOf(target) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(target.id) {
        if (lastShown.id != target.id) {
            fromRoom = lastShown
            if (reduced) {
                fade.snapTo(1f)
            } else {
                fade.snapTo(0f)
                fade.animateTo(1f, tween(Motion.room, easing = LinearEasing))
            }
        }
    }
    val t = fade.value
    val p = if (t >= 1f) target else fromRoom.lerpTo(target, smoothstep(t))
    SideEffect { lastShown = p }
    val style = (if (t >= 0.5f) target else fromRoom).style.overridden(state.styleOverride)
    val view = LocalView.current
    val density = LocalDensity.current
    val actualLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val chromeLandscape =
        if (actions.isUiPlacementLocked()) actions.lockedUiLandscape() else actualLandscape
    var consoleVisible by remember { mutableStateOf(true) }
    var sheet by remember { mutableStateOf(Sheet.NONE) }
    var overflowComposed by remember { mutableStateOf(false) }
    var overflowTargetOpen by remember { mutableStateOf(false) }
    var overflowPendingSheet by remember { mutableStateOf(Sheet.NONE) }
    var settingsPullActive by remember { mutableStateOf(false) }
    var bottomEdgePullActive by remember { mutableStateOf(false) }
    var rootHeightPx by remember { mutableStateOf(0) }
    var consoleHeightPx by remember { mutableStateOf(0) }
    var focusValue by remember { mutableFloatStateOf(0.3f) }
    val ribbon = remember { RibbonState() }
    val bloomScope = rememberCoroutineScope()
    val bloom = remember(bloomScope) { BloomPullState(bloomScope) }
    val settingsReveal = remember(bloomScope) { PullRevealState(bloomScope) }
    val overflowReveal = remember(bloomScope) { PullRevealState(bloomScope) }
    val overflowGestureActive = remember { mutableStateOf(false) }
    val currentStyle = rememberUpdatedState(style)
    val currentReduced = rememberUpdatedState(reduced)
    val flickVelocityPx = with(density) { Dim.chromeFlickVelocity.toPx() }
    val popoutTravelPx = with(density) { Dim.popoutPullTravel.toPx() }

    // The Compose side continuously commands NEW beam energy. The renderer deposits that
    // energy into its real flash/glow textures, so release naturally leaves the P7 layers
    // to decay instead of fading a chrome overlay.
    LaunchedEffect(bloom, style.motion) {
        snapshotFlow { bloom.enginePull(style.motion) }
            .distinctUntilChanged()
            .collect { PhosphorNative.setBloomPull(it) }
    }
    DisposableEffect(bloom) {
        onDispose { PhosphorNative.setBloomPull(0f) }
    }

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
    BackHandler(enabled = !overflowComposed && sheet == Sheet.NONE && consoleVisible) {
        consoleVisible = false
    }

    // Console auto-hides after 4 s of no interaction (burn-in + clean stage).
    LaunchedEffect(consoleVisible, sheet, overflowComposed) {
        if (consoleVisible && sheet == Sheet.NONE && !overflowComposed) {
            delay(4000)
            consoleVisible = false
        }
    }

    val sheetActions = remember(actions) {
        object : SheetActions {
            override fun openFile() = actions.openFile()
            override fun startMic() = actions.startMic()
            override fun startCapture() = actions.startCapture()
            override fun startRemote() = actions.startRemote()
            override fun stopLive() = actions.stopLive()
            override fun captureConsentNeeded() = actions.captureConsentNeeded()
            override fun setFps(value: Int) = actions.setFps(value)
            override fun setOversample(n: Int) = actions.setOversample(n)
            override fun setGainAbsolute(g: Float) = actions.setGainAbsolute(g)
            override fun setGainAuto(on: Boolean) = actions.setGainAuto(on)
            override fun setBeamEnergy(e: Float) = actions.setBeamEnergy(e)
            override fun setGlow(g: Float) = actions.setGlow(g)
            override fun setGrid(on: Boolean) = actions.setGrid(on)
            override fun setHudMode(mode: Int) = actions.setHudMode(mode)
            override fun setFullscreen(on: Boolean) = actions.setFullscreen(on)
            override fun openCaptureMetadataSettings() = actions.openCaptureMetadataSettings()
            override fun isScopeRotationLocked() = actions.isScopeRotationLocked()
            override fun setScopeRotationLocked(locked: Boolean) =
                actions.setScopeRotationLocked(locked)
            override fun isUiPlacementLocked() = actions.isUiPlacementLocked()
            override fun setUiPlacementLocked(locked: Boolean) =
                actions.setUiPlacementLocked(locked)
            override fun setRemoteLatencyMode(mode: Int) = actions.setRemoteLatencyMode(mode)
            override fun setRemoteNetworkMode(mode: Int) = actions.setRemoteNetworkMode(mode)
            override fun openRoom() { }
            override fun openLight() { }
            override fun remoteHosts() = actions.remoteHosts()
            override fun startRemoteHost(label: String, host: String, port: Int) =
                actions.startRemoteHost(label, host, port)
            override fun setRemoteStreams(audio: Boolean, geometry: Boolean) =
                actions.setRemoteStreams(audio, geometry)
            override fun disconnectRemote() = actions.disconnectRemote()
            override fun openFolder() = actions.openFolder()
        }
    }

    // Stable gesture hosts survive the recomposition triggered by beginning a pull.
    // Their dynamic room/reduced values come through remembered state holders.
    val settingsPullHost = remember(settingsReveal) {
        object : PullGestureHost {
            private var ignored = false

            override fun begin() {
                ignored = currentReduced.value || sheet != Sheet.NONE || overflowComposed ||
                    overflowGestureActive.value
                if (ignored) return
                settingsReveal.setTravelPx(
                    if (rootHeightPx > 0) rootHeightPx * 0.82f
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
        LocalBloomPull provides bloom,
        LocalChromeLandscape provides chromeLandscape,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { rootHeightPx = it.height }
        ) {
            // Layer 0: the scope, full-bleed under everything.
            AndroidView(factory = { actions.makeSurface() }, modifier = Modifier.fillMaxSize())

            // PiP is pure scope — zero chrome (spec §3).
            if (state.pip) return@Box

            // Layer 0.5: the stage — gesture arbiter (drags/pinches) + tap layer.
            // Sits BELOW the console so console controls win hit-testing in their bounds.
            if (sheet == Sheet.NONE || bottomEdgePullActive) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .stageGestures(
                            remember(actions, state, bloom, style.motion, reduced) {
                                object : StageGestureHost {
                                    private var edgeTravelPx = 0f
                                    private var settingsHandedOff = false

                                    override fun currentGain() = state.gain
                                    override fun setGainAbsolute(g: Float) = actions.setGainAbsolute(g)
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
                                    // bottom band may summon it (with the same bloom); gain/orbit
                                    // keep every other clearly classified stage drag.
                                    override fun bottomPullArmed() =
                                        !consoleVisible && !overflowComposed
                                    override fun beginBottomChromePull(resistancePx: Float) {
                                        edgeTravelPx = 0f
                                        settingsHandedOff = false
                                        bottomEdgePullActive = true
                                        consoleVisible = true
                                        bloom.begin(resistancePx)
                                    }
                                    override fun dragBottomChromePull(
                                        upwardDeltaPx: Float,
                                        resistancePx: Float,
                                    ) {
                                        val delta = upwardDeltaPx.coerceAtLeast(-edgeTravelPx)
                                        edgeTravelPx = (edgeTravelPx + delta).coerceAtLeast(0f)
                                        bloom.dragBy(delta, resistancePx, style.motion)
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
                                        bloom.release(style.motion, reduced)
                                        if (settingsHandedOff && !currentReduced.value) {
                                            settingsPullHost.release(velocityY)
                                        }
                                    }
                                    override fun cancelBottomChromePull() {
                                        bottomEdgePullActive = false
                                        bloom.release(style.motion, reduced)
                                        if (settingsHandedOff && !currentReduced.value) {
                                            settingsPullHost.cancel()
                                        }
                                        settingsPullActive = false
                                        sheet = Sheet.NONE
                                        consoleVisible = false
                                    }
                                    override fun view() = view
                                }
                            },
                            ribbon,
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    if (overflowComposed) closeOverflow(Sheet.NONE)
                                    else consoleVisible = !consoleVisible
                                },
                                onDoubleTap = { actions.togglePlay() },
                            )
                        },
                )
            }

            // The gesture readout ribbon rides above the stage.
            GestureRibbon(ribbon, p)

            // Layer 1a: read-only status band.
            // Band visibility (Ben's ask): on = always · auto = rides the
            // console's timer · off = pure scope. Default on.
            if (state.bandMode == 0 || (state.bandMode == 1 && consoleVisible)) {
                StatusBand(
                    state, p, reduced,
                    hudVisible = state.hudMode == 0 ||
                        (state.hudMode == 1 && consoleVisible),
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
                ) { BenchPost(state, p) }
            }

            // Layer 1b: console strip, auto-hiding, with the settle-down exit.
            AnimatedVisibility(
                visible = consoleVisible && (sheet == Sheet.NONE || settingsPullActive),
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
                )
            }

            // The ⋯ overflow popout (Obsidian-persistent, anchored above the console).
            if (overflowComposed && sheet == Sheet.NONE) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(
                            bottom = with(density) { consoleHeightPx.toDp() } + Dim.popoutGap
                        ),
                ) {
                    OverflowPopout(
                        p,
                        reduced = reduced,
                        reveal = overflowReveal,
                        onDeck = { closeOverflow(Sheet.DECK) },
                        onLight = { closeOverflow(Sheet.LIGHT) },
                        onRoom = { closeOverflow(Sheet.ROOM) },
                        onSettings = { closeOverflow(Sheet.SETTINGS) },
                    )
                }
            }

            // Layer 2: sheets.
            when (sheet) {
                Sheet.SOURCE -> SourceSheet(state, p, reduced, sheetActions) { sheet = Sheet.NONE }
                Sheet.MODE -> ModeSheet(state, p, reduced, onPick = { actions.setMode(it) }) {
                    sheet = Sheet.NONE
                }
                Sheet.LIGHT -> LightSheetV2(
                    state, p, reduced,
                    onPickPreset = { actions.setBeam(it) },
                    onCustomChange = { colors, count -> actions.setCustomBeam(colors, count) },
                    onCycleChange = { secs, perTrack -> actions.setBeamCycle(secs, perTrack) },
                    epilepsyAcknowledged = { actions.epilepsyAcknowledged() },
                    ackEpilepsy = { actions.ackEpilepsy() },
                ) { sheet = Sheet.NONE }
                Sheet.ROOM -> RoomSheet(state, p, reduced, onPick = { actions.setRoom(it) }) {
                    sheet = Sheet.NONE
                }
                Sheet.DECK -> DeckSheet(
                    state, p, reduced,
                    onPlay = { actions.togglePlay() },
                    onNext = { actions.next() },
                    onPrev = { actions.prev() },
                    onSeek = { actions.seekTo(it) },
                    onJump = { actions.jumpToQueue(it) },
                    volumeFrac = { actions.volumeFrac() },
                    onVolume = { actions.setVolume(it) },
                    onOpenFolder = { actions.openFolder() },
                ) { sheet = Sheet.NONE }
                Sheet.SETTINGS -> SettingsSheet(
                    state, p, reduced,
                    sheetActions.withSheetRouting(
                        openRoom = { sheet = Sheet.ROOM },
                        openLight = { sheet = Sheet.LIGHT },
                    ),
                    focusValue = focusValue,
                    onFocus = { focusValue = it; actions.setFocus(it) },
                    entryReveal = if (settingsPullActive) settingsReveal else null,
                ) {
                    settingsPullActive = false
                    sheet = Sheet.NONE
                }
                Sheet.NONE -> {}
            }
        }
    }
}

// Route the settings sheet's room/light links back into the sheet state machine.
private fun SheetActions.withSheetRouting(
    openRoom: () -> Unit,
    openLight: () -> Unit,
): SheetActions {
    val base = this
    return object : SheetActions by base {
        override fun openRoom() = openRoom()
        override fun openLight() = openLight()
    }
}
