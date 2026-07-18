package dev.phosphor.mobil3.ui

import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

// The gesture arbiter (UX-SPEC §1.2/§1.3, core map): each pointer sequence is classified
// ONCE and owned by exactly one verb. This layer owns drags and pinches on the stage;
// taps fall through to the tap layer beneath it (which never sees moved sequences).
//   1-finger drag  → GAIN (2D modes) / ORBIT (3D modes)
//   2-finger pinch → GAIN, coarse (2D) / DOLLY (3D)
// The mono readout ribbon etches beside the thumb; a light tick marks crossing ×1.00.

class RibbonState {
    var visible by mutableStateOf(false)
    var text by mutableStateOf("")
    var at by mutableStateOf(Offset.Zero)
    var lastTouchMs by mutableStateOf(0L)
}

interface StageGestureHost {
    fun currentGain(): Float
    fun setGainAbsolute(g: Float)
    fun orbitBy(dyaw: Float, dpitch: Float)
    fun dollyBy(delta: Float)
    fun is3d(): Boolean
    fun view(): View
}

fun Modifier.stageGestures(host: StageGestureHost, ribbon: RibbonState): Modifier =
    this.pointerInput(Unit) {
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val first = awaitFirstDown(requireUnconsumed = false)
            var mode = 0 // 0 = undecided, 1 = drag, 2 = pinch
            var gain = host.currentGain()
            var lastDist = -1f
            var origin = first.position
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break
                if (pressed.size >= 2) {
                    if (mode != 2) { mode = 2; lastDist = -1f }
                    val a = pressed[0].position
                    val b = pressed[1].position
                    val dist = (a - b).getDistance()
                    if (lastDist > 0f) {
                        val zoom = dist / lastDist
                        if (abs(zoom - 1f) > 0.001f) {
                            if (host.is3d()) {
                                host.dollyBy((1f - zoom) * 2.2f)
                            } else {
                                val old = gain
                                gain = (gain * zoom).coerceIn(0.05f, 16f)
                                host.setGainAbsolute(gain)
                                if ((old - 1f) * (gain - 1f) <= 0f && old != gain) {
                                    Haptics.light(host.view())
                                }
                                ribbon.text = "× %.2f".format(gain)
                            }
                            ribbon.at = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
                            ribbon.visible = true
                            ribbon.lastTouchMs = System.currentTimeMillis()
                        }
                    }
                    lastDist = dist
                    pressed.forEach { it.consume() }
                } else if (pressed.size == 1) {
                    val ch = pressed[0]
                    if (mode == 2) {
                        // Pinch shed a finger — retire the sequence rather than re-owning it.
                        if (ch.positionChanged()) ch.consume()
                        continue
                    }
                    val delta = ch.position - origin
                    if (mode == 0 && (abs(delta.x) > slop || abs(delta.y) > slop)) {
                        mode = 1
                        origin = ch.position
                        continue
                    }
                    if (mode == 1 && ch.positionChanged()) {
                        val d = ch.position - ch.previousPosition
                        if (host.is3d()) {
                            host.orbitBy(d.x * 0.006f, d.y * 0.006f)
                        } else {
                            val old = gain
                            gain = (gain * exp(-d.y * 0.0042f)).coerceIn(0.05f, 16f)
                            host.setGainAbsolute(gain)
                            if ((old - 1f) * (gain - 1f) <= 0f && old != gain) {
                                Haptics.light(host.view())
                            }
                            ribbon.text = "× %.2f".format(gain)
                            ribbon.at = ch.position
                            ribbon.visible = true
                            ribbon.lastTouchMs = System.currentTimeMillis()
                        }
                        ch.consume()
                    }
                }
            }
        }
    }

// The readout ribbon — a quiet mono etching beside the thumb; fades 600 ms after release.
@Composable
fun GestureRibbon(ribbon: RibbonState, p: Palette) {
    LaunchedEffect(ribbon.lastTouchMs) {
        if (ribbon.visible) {
            delay(600)
            ribbon.visible = false
        }
    }
    AnimatedVisibility(visible = ribbon.visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier.offset {
                IntOffset(
                    (ribbon.at.x + 44f).roundToInt(),
                    (ribbon.at.y - 64f).roundToInt(),
                )
            },
        ) {
            Box(
                Modifier
                    .background(p.surface.copy(alpha = 0.80f))
                    .border(Dim.hairline, p.line)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) { Mono(ribbon.text, p.ink, Type.dataLg) }
        }
    }
}
