package dev.phosphor.mobil3.ui

import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

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
    fun epilepsyAcknowledged(): Boolean
    fun ackEpilepsy()
    fun setGainAbsolute(g: Float)
    fun orbitBy(dyaw: Float, dpitch: Float)
    fun dollyBy(delta: Float)
    fun makeSurface(): SurfaceView
}

@Composable
fun PhosphorScreen(state: ScopeUiState, actions: ScopeActions, reduced: Boolean) {
    val p = state.room
    val view = LocalView.current
    var consoleVisible by remember { mutableStateOf(true) }
    var sheet by remember { mutableStateOf(Sheet.NONE) }
    var overflow by remember { mutableStateOf(false) }
    var focusValue by remember { mutableFloatStateOf(0.3f) }
    val ribbon = remember { RibbonState() }

    // Predictive back peels one layer at a time: popout → sheet → console → system.
    BackHandler(enabled = overflow) { overflow = false }
    BackHandler(enabled = !overflow && sheet != Sheet.NONE) { sheet = Sheet.NONE }
    BackHandler(enabled = !overflow && sheet == Sheet.NONE && consoleVisible) {
        consoleVisible = false
    }

    // Console auto-hides after 4 s of no interaction (burn-in + clean stage).
    LaunchedEffect(consoleVisible, sheet, overflow) {
        if (consoleVisible && sheet == Sheet.NONE && !overflow) {
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
            override fun setBeamEnergy(e: Float) = actions.setBeamEnergy(e)
            override fun setGlow(g: Float) = actions.setGlow(g)
            override fun setGrid(on: Boolean) = actions.setGrid(on)
            override fun openRoom() { }
            override fun openLight() { }
        }
    }

    CompositionLocalProvider(LocalReducedMotion provides reduced) {
        Box(Modifier.fillMaxSize()) {
            // Layer 0: the scope, full-bleed under everything.
            AndroidView(factory = { actions.makeSurface() }, modifier = Modifier.fillMaxSize())

            // Layer 0.5: the stage — gesture arbiter (drags/pinches) + tap layer.
            // Sits BELOW the console so console controls win hit-testing in their bounds.
            if (sheet == Sheet.NONE) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .stageGestures(
                            remember(actions, state) {
                                object : StageGestureHost {
                                    override fun currentGain() = state.gain
                                    override fun setGainAbsolute(g: Float) = actions.setGainAbsolute(g)
                                    override fun orbitBy(dyaw: Float, dpitch: Float) =
                                        actions.orbitBy(dyaw, dpitch)
                                    override fun dollyBy(delta: Float) = actions.dollyBy(delta)
                                    override fun is3d() = state.mode3d
                                    override fun view() = view
                                }
                            },
                            ribbon,
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    if (overflow) overflow = false
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
            StatusBand(state, p, reduced)

            // Layer 1b: console strip, auto-hiding, with the settle-down exit.
            AnimatedVisibility(
                visible = consoleVisible && sheet == Sheet.NONE,
                enter = fadeIn(motionSpec(reduced, Motion.summon)),
                exit = fadeOut(motionSpec(reduced, Motion.settle)) +
                    slideOutVertically(motionSpec(reduced, Motion.settle)) { it / 12 },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Console(
                    state, p, reduced,
                    onMode = { sheet = Sheet.MODE },
                    onSrc = { sheet = Sheet.SOURCE },
                    onMore = { overflow = !overflow },
                    onPlay = { actions.togglePlay() },
                    onNext = { actions.next() },
                    onPrev = { actions.prev() },
                    onSeek = { actions.seekTo(it) },
                )
            }

            // The ⋯ overflow popout (Obsidian-persistent, anchored above the console).
            if (overflow && sheet == Sheet.NONE) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 132.dp),
                ) {
                    OverflowPopout(
                        p,
                        onLight = { sheet = Sheet.LIGHT },
                        onRoom = { sheet = Sheet.ROOM },
                        onSettings = { sheet = Sheet.SETTINGS },
                        onDismiss = { overflow = false },
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
                Sheet.SETTINGS -> SettingsSheet(
                    state, p, reduced,
                    sheetActions.withSheetRouting(
                        openRoom = { sheet = Sheet.ROOM },
                        openLight = { sheet = Sheet.LIGHT },
                    ),
                    focusValue = focusValue,
                    onFocus = { focusValue = it; actions.setFocus(it) },
                ) { sheet = Sheet.NONE }
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

