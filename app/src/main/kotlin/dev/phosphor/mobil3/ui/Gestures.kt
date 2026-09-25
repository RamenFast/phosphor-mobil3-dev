package dev.phosphor.mobil3.ui

import dev.phosphor.mobil3.AcceptanceTrace

import android.view.View
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

interface PullGestureHost {
    fun begin()
    fun dragBy(upwardDeltaPx: Float)
    fun release(verticalVelocityPxPerSecond: Float)
    fun cancel()
}

// The console's one vertical gesture: a deliberate upward pull opens SETTINGS.
// In ordinary motion it becomes finger-tracked as soon as vertical intent wins; in
// reduced motion it retains the prior threshold door and lets SheetHost fade in.
// It observes without consuming until vertical travel clearly wins, so child taps
// and the seek rule's horizontal scrub keep their existing ownership.
@Composable
fun Modifier.playBarSwipeUp(
    onReducedSwipeUp: () -> Unit,
    pullHost: PullGestureHost,
): Modifier {
  val style = LocalRoomStyle.current
  val reduced = LocalReducedMotion.current
  val currentReducedSwipeUp by rememberUpdatedState(onReducedSwipeUp)
  return pointerInput(pullHost, style.motion, reduced) {
    val threshold = maxOf(48.dp.toPx(), viewConfiguration.touchSlop * 3f)
    awaitEachGesture {
        val first = awaitFirstDown(requireUnconsumed = false)
        val origin = first.position
        val velocity = VelocityTracker().apply {
            addPosition(first.uptimeMillis, first.position)
        }
        var fired = false
        var pullActive = false
        var multiTouch = false
        var finishedNormally = false
        try {
            while (true) {
                val event = awaitPointerEvent()
                event.changes.firstOrNull()?.let {
                    velocity.addPosition(it.uptimeMillis, it.position)
                }
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) {
                    finishedNormally = true
                    break
                }
                if (pressed.size != 1) {
                    multiTouch = true
                    if (pullActive) {
                        pullHost.cancel()
                        pullActive = false
                    }
                    continue
                }
                if (multiTouch) continue
                if (!fired) {
                    val change = pressed.first()
                    val travel = change.position - origin
                    if (reduced) {
                        if (travel.y < -threshold && abs(travel.y) > abs(travel.x) * 1.35f) {
                            fired = true
                            pressed.forEach { it.consume() }
                            currentReducedSwipeUp()
                        }
                    } else if (!pullActive &&
                        travel.y < -viewConfiguration.touchSlop &&
                        abs(travel.y) > abs(travel.x) * 1.35f
                    ) {
                        pullActive = true
                        pullHost.begin()
                        pullHost.dragBy(
                            (-travel.y - viewConfiguration.touchSlop).coerceAtLeast(0f)
                        )
                        pressed.forEach { it.consume() }
                    } else if (pullActive && change.positionChanged()) {
                        pullHost.dragBy(-(change.position.y - change.previousPosition.y))
                        pressed.forEach { it.consume() }
                    }
                } else {
                    if (fired || pullActive) pressed.forEach { it.consume() }
                }
            }
        } finally {
            if (pullActive) {
                if (finishedNormally) pullHost.release(velocity.calculateVelocity().y)
                else pullHost.cancel()
            }
        }
    }
  }
}

/**
 * S9 owns tap and upward pull in one detector, consuming only once the pull wins.
 * Initial-pass consumption prevents the enclosing play-bar door from opening SETTINGS.
 */
@Composable
fun Modifier.overflowHandleGesture(
    pullHost: PullGestureHost,
    onTap: () -> Unit,
    onPressed: (Boolean) -> Unit,
): Modifier {
  val currentTap by rememberUpdatedState(onTap)
  val currentPressed by rememberUpdatedState(onPressed)
  return pointerInput(pullHost) {
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        currentPressed(true)
        val origin = first.position
        val velocity = VelocityTracker().apply {
            addPosition(first.uptimeMillis, first.position)
        }
        var pulling = false
        var disqualifiedTap = false
        var finishedNormally = false
        try {
            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                val change = event.changes.firstOrNull() ?: break
                velocity.addPosition(change.uptimeMillis, change.position)
                if (!change.pressed) {
                    finishedNormally = true
                    break
                }
                val travel = change.position - origin
                if (!pulling && travel.getDistance() > slop) disqualifiedTap = true
                if (!pulling && travel.y < -slop && abs(travel.y) > abs(travel.x) * 1.25f) {
                    pulling = true
                    pullHost.begin()
                    pullHost.dragBy((-travel.y - slop).coerceAtLeast(0f))
                    change.consume()
                } else if (pulling && change.positionChanged()) {
                    pullHost.dragBy(-(change.position.y - change.previousPosition.y))
                    change.consume()
                }
            }
        } finally {
            currentPressed(false)
            when {
                pulling && finishedNormally -> pullHost.release(velocity.calculateVelocity().y)
                pulling -> pullHost.cancel()
                finishedNormally && !disqualifiedTap -> currentTap()
            }
        }
    }
  }
}

// The seek rule is a console child, never a stage shortcut. It waits for a
// horizontal-dominant classification before publishing any scrub value; crossing
// vertical slop first rejects the whole sequence, so a settings-door pull cannot
// skip toward the end as it passes over the timeline.
@Composable
fun Modifier.consoleSeekGesture(
    durationMs: Long,
    onScrub: (Float) -> Unit,
    onCommit: (Float) -> Unit,
    onCancel: () -> Unit,
    onStart: (Float) -> Unit = {},
): Modifier {
    val currentScrub by rememberUpdatedState(onScrub)
    val currentCommit by rememberUpdatedState(onCommit)
    val currentCancel by rememberUpdatedState(onCancel)
    val currentStart by rememberUpdatedState(onStart)
    return pointerInput(durationMs) {
        if (durationMs <= 0L) return@pointerInput
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val first = awaitFirstDown(requireUnconsumed = false)
            val origin = first.position
            val geometry = SliderGeometry(size.width.toFloat(), density)
            var ownership = if (first.isConsumed) 2 else 0 // 0 undecided · 1 horizontal seek · 2 rejected
            var fraction = geometry.fractionAt(origin.x)
            var started = false
            var finishedNormally = false
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }
                    val change = event.changes.firstOrNull { it.id == first.id }
                    if (change == null || change.isConsumed || pressed.size > 1) {
                        ownership = 2
                    }
                    if (ownership == 0 && change != null) {
                        val travel = change.position - origin
                        if (abs(travel.x) > slop || abs(travel.y) > slop) {
                            ownership = if (
                                abs(travel.x) > slop &&
                                abs(travel.x) > abs(travel.y) * 1.35f
                            ) 1 else 2
                        }
                    }
                    if (pressed.isEmpty()) {
                        if (ownership != 2 && change != null) {
                            if (!started) currentStart(geometry.fractionAt(origin.x))
                            fraction = geometry.fractionAt(change.position.x)
                            change.consume()
                            finishedNormally = true
                        }
                        break
                    }
                    if (pressed.size != 1 || change == null || !change.pressed) {
                        ownership = 2
                        continue
                    }
                    if (ownership == 1) {
                        if (!started) {
                            currentStart(geometry.fractionAt(origin.x))
                            started = true
                        }
                        fraction = geometry.fractionAt(change.position.x)
                        currentScrub(fraction)
                        change.consume()
                    }
                }
            } finally {
                if (finishedNormally) currentCommit(fraction)
                else if (started) currentCancel()
            }
        }
    }
}

// The gesture arbiter (UX-SPEC §1.2/§1.3, core map): each pointer sequence is classified
// ONCE and owned by exactly one verb. This layer owns drags and pinches on the stage;
// taps fall through to the tap layer beneath it (which never sees moved sequences).
//   1-finger drag  → AUTO FRAME or manual GAIN (2D) / ORBIT (3D)
//   2-finger pinch → AUTO FRAME or manual GAIN (2D) / DOLLY (3D)
// The mono readout ribbon etches beside the thumb; a light tick marks crossing ×1.00.

class RibbonState {
    var visible by mutableStateOf(false)
    var text by mutableStateOf("")
    /** Size or glow rail: marker position 0..1 (bottom..top), optional tick, end words. */
    var rail by mutableStateOf<Float?>(null)
    var railTick by mutableStateOf<Float?>(null)
    var railValue by mutableStateOf<String?>(null)
    var railTop by mutableStateOf("closer")
    var railBottom by mutableStateOf("farther")
    var at by mutableStateOf(Offset.Zero)
    var lastTouchMs by mutableLongStateOf(0L)
}

interface StageGestureHost {
    fun inspecting(): Boolean = false
    fun inspect(dx: Float, dy: Float, scale: Float) {}
    fun physicalPosition(local: Offset): Offset?
    fun physicalBounds(): Rect?
    fun chromeBlocks(points: List<Offset>, now: Long): Boolean
    fun currentGain(): Float
    fun setGainAbsolute(g: Float)
    fun currentAutoFrameScale(): Float
    fun setAutoFrameScale(scale: Float)
    fun finishAutoFrameScale() {}
    /** VIEW LOCK blocks both local 2D framing meanings after other owners settle. */
    fun gainLocked(): Boolean
    fun gainAutoArmed(): Boolean
    fun autoFrameArmed(): Boolean
    fun orbitBy(dyaw: Float, dpitch: Float)
    fun dollyBy(delta: Float)
    fun is3d(): Boolean
    fun modeStep(delta: Int)
    fun currentGlow(): Float
    fun setGlowAbsolute(g: Float)
    fun bottomPullArmed(): Boolean
    fun beginBottomChromePull()
    fun dragBottomChromePull(upwardDeltaPx: Float)
    fun releaseBottomChromePull(velocityY: Float)
    fun cancelBottomChromePull()
    fun view(): View
}

fun Modifier.stageGestures(host: StageGestureHost, ribbon: RibbonState): Modifier =
    this.pointerInput(host, ribbon) {
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val first = awaitFirstDown(requireUnconsumed = false)
            // Reserve the top band for Android's transient system-bar gesture.
            val physicalOrigin = host.physicalPosition(first.position)
            val physicalRoot = host.physicalBounds()
            if (physicalOrigin != null && physicalRoot != null &&
                physicalOrigin.y <= physicalRoot.top + Dim.topGestureBand.toPx()
            ) {
                while (true) {
                    val e = awaitPointerEvent()
                    if (e.changes.none { it.pressed }) break
                }
                return@awaitEachGesture
            }
            // 0 undecided · 1 drag · 2 pinch · 4 mode-step (fired) · 5 glow swipe
            // 6 bottom chrome door · 7 retired. Pull deltas use physical coordinates.
            var mode = 0
            var gain = host.currentGain()
            var autoFrameScale = host.currentAutoFrameScale()
            var frameEdited = false
            var glow = host.currentGlow()
            val pinchScale = StagePinchScale()
            var origin = first.position
            var twoOrigin = Offset.Zero
            var twoStartDist = 0f
            val bottomCandidate = host.bottomPullArmed() &&
                physicalOrigin != null && physicalRoot != null &&
                StageGesturePolicy.inBottomBand(physicalOrigin, physicalRoot, Dim.bottomGestureBand.toPx())
            val policy = StageGesturePolicy.Sequence()
            val firstPoints = listOfNotNull(physicalOrigin)
            policy.frame(
                firstPoints, physicalRoot.takeIf { physicalOrigin != null }, Dim.bottomGestureBand.toPx(),
                host.chromeBlocks(firstPoints, SystemClock.uptimeMillis()),
            )
            var bottomChromeActive = false
            var finishedNormally = false
            val velocity = VelocityTracker().apply {
                physicalOrigin?.let { addPosition(first.uptimeMillis, it) }
            }
            try {
              while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                event.changes.firstOrNull()?.let {
                    host.physicalPosition(it.position)?.let { position ->
                        velocity.addPosition(it.uptimeMillis, position)
                    }
                }
                if (pressed.isEmpty()) {
                    finishedNormally = true
                    break
                }
                if (pressed.size >= 2 && (bottomCandidate || mode == 6)) {
                    if (bottomChromeActive) {
                        bottomChromeActive = false
                        host.cancelBottomChromePull()
                    }
                    mode = 7
                }
                if (pressed.size == 1 && mode in 2..5) mode = 7
                if (mode == 7) {
                    pressed.filter { it.positionChanged() }.forEach { it.consume() }
                    continue
                }

                val physicalPoints = pressed.mapNotNull { host.physicalPosition(it.position) }
                val root = host.physicalBounds().takeIf { physicalPoints.size == pressed.size }
                val evaluatedAt = SystemClock.uptimeMillis()
                val scopeFrame = policy.frame(
                    physicalPoints, root, Dim.bottomGestureBand.toPx(),
                    host.chromeBlocks(physicalPoints, evaluatedAt),
                )
                AcceptanceTrace.record("gesture") {
                    "evaluated_ms=$evaluatedAt input_ms=${pressed.first().uptimeMillis} pointers=${pressed.size} " +
                        "decision=$scopeFrame gain=${host.currentGain()} frame_scale=${host.currentAutoFrameScale()} " +
                            "locked=${host.gainLocked()} mode=$mode"
                }

                // This existing door is an exception to scope rejection, not a second owner.
                if (pressed.size == 1 && bottomCandidate && physicalOrigin != null) {
                    val ch = pressed[0]
                    val position = host.physicalPosition(ch.position)
                    val previous = host.physicalPosition(ch.previousPosition)
                    if (position != null) {
                        val travel = position - physicalOrigin
                        if (mode == 0 && StageGesturePolicy.upwardPull(travel, slop)) {
                            mode = 6
                            bottomChromeActive = true
                            host.beginBottomChromePull()
                            host.dragBottomChromePull((-travel.y - slop).coerceAtLeast(0f))
                            ch.consume()
                        } else if (mode == 6 && previous != null && ch.positionChanged()) {
                            host.dragBottomChromePull(-(position.y - previous.y))
                            ch.consume()
                        } else if (mode == 0 && (
                                (abs(travel.x) > slop * 2f && abs(travel.x) > abs(travel.y) * 1.6f) ||
                                    (travel.y > slop * 2f && abs(travel.y) > abs(travel.x) * 1.6f)
                                )) {
                            mode = 7
                        }
                    }
                    if ((ch.position - first.position).getDistance() > slop) ch.consume()
                    continue
                }

                if (pressed.size >= 2 && (mode == 0 || mode == 1)) {
                    mode = 2
                    pinchScale.reset()
                    twoOrigin = (pressed[0].position + pressed[1].position) / 2f
                    twoStartDist = (pressed[0].position - pressed[1].position).getDistance()
                    glow = host.currentGlow()
                }
                if (scopeFrame != StageGesturePolicy.ScopeFrame.Apply) {
                    // Blocked travel never becomes a deferred pinch, swipe or drag.
                    gain = host.currentGain()
                    autoFrameScale = host.currentAutoFrameScale()
                    glow = host.currentGlow()
                    origin = pressed[0].position
                    if (pressed.size >= 2) {
                        twoStartDist = (pressed[0].position - pressed[1].position).getDistance()
                        pinchScale.reset(twoStartDist)
                        twoOrigin = (pressed[0].position + pressed[1].position) / 2f
                    } else pinchScale.reset()
                    if (pressed.size >= 2 || (pressed[0].position - first.position).getDistance() > slop) {
                        pressed.forEach { it.consume() }
                    }
                    continue
                }
                // The existing bottom settings door and chrome rejection have already won.
                // Inspection owns all remaining scope travel, never the live tuning verbs.
                if (host.inspecting()) {
                    val delta = pressed.map { it.position - it.previousPosition }
                        .fold(Offset.Zero) { sum, d -> sum + d } / pressed.size.toFloat()
                    val zoom = if (pressed.size >= 2) {
                        val now = (pressed[0].position - pressed[1].position).getDistance()
                        val before = (pressed[0].previousPosition - pressed[1].previousPosition).getDistance()
                        if (before > 1f) now / before else 1f
                    } else 1f
                    host.inspect(delta.x / size.width.coerceAtLeast(1), delta.y / size.height.coerceAtLeast(1), zoom)
                    pressed.filter { it.positionChanged() }.forEach { it.consume() }
                    continue
                }
                if (pressed.size >= 2) {
                    val a = pressed[0].position
                    val b = pressed[1].position
                    val dist = (a - b).getDistance()
                    val centroid = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
                    val travel = centroid - twoOrigin
                    val spread = abs(dist - twoStartDist)
                    // Classify ONCE (single-owner law): translation clearly beats spread →
                    // a two-finger SWIPE (horizontal = mode step, vertical = glow); else pinch.
                    if (mode == 2 && spread < slop * 1.5f) {
                        if (abs(travel.x) > slop * 3f && abs(travel.x) > abs(travel.y) * 1.6f) {
                            mode = 4 // mode-step swipe: one detent per gesture
                            host.modeStep(if (travel.x < 0f) 1 else -1)
                            Haptics.medium(host.view())
                            pressed.forEach { it.consume() }
                            continue
                        } else if (abs(travel.y) > slop * 3f && abs(travel.y) > abs(travel.x) * 1.6f) {
                            mode = 5 // glow swipe: continuous
                        }
                    }
                    if (mode == 4) {
                        pressed.forEach { it.consume() }
                        continue
                    }
                    if (mode == 5) {
                        val d = pressed[0].position - pressed[0].previousPosition
                        glow = (glow - d.y * 0.0011f).coerceIn(0f, 0.98f)
                        host.setGlowAbsolute(glow)
                        ribbon.text = ""
                        ribbon.rail = glow / 0.98f
                        ribbon.railTick = null
                        ribbon.railValue = null
                        ribbon.railTop = "brighter"
                        ribbon.railBottom = "dimmer"
                        ribbon.at = centroid
                        ribbon.visible = true
                        ribbon.lastTouchMs = System.currentTimeMillis()
                        pressed.forEach { it.consume() }
                        continue
                    }
                    pinchScale.sample(dist)?.let { zoom ->
                            if (host.is3d()) {
                                host.dollyBy((1f - zoom) * 2.2f)
                            } else if (host.gainLocked()) {
                                ribbon.text = when {
                                    host.autoFrameArmed() -> "view locked"
                                    host.gainAutoArmed() -> "view locked"
                                    else -> "view locked"
                                }
                                ribbon.rail = null
                            } else {
                                val adjusted = StageZoomPolicy.adjust(gain, autoFrameScale, host.autoFrameArmed(), zoom)
                                if (adjusted.owner == StageZoomOwner.AUTO_FRAME) {
                                    autoFrameScale = adjusted.value
                                    host.setAutoFrameScale(autoFrameScale)
                                    frameEdited = true
                                    ribbon.showSize(RibbonRail.frame(autoFrameScale), RibbonRail.frame(1f), autoFrameScale)
                                } else {
                                    gain = adjusted.value
                                    host.setGainAbsolute(gain)
                                    ribbon.showSize(GainWords.toSlider(gain), null, gain)
                                }
                                if (adjusted.crossedNeutral) Haptics.light(host.view())
                            }
                            ribbon.at = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
                            ribbon.visible = true
                            ribbon.lastTouchMs = System.currentTimeMillis()
                    }
                    pressed.forEach { it.consume() }
                } else if (pressed.size == 1) {
                    val ch = pressed[0]
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
                        } else if (host.gainLocked()) {
                            ribbon.text = when {
                                host.autoFrameArmed() -> "view locked"
                                host.gainAutoArmed() -> "view locked"
                                else -> "view locked"
                            }
                            ribbon.rail = null
                            ribbon.at = ch.position
                            ribbon.visible = true
                            ribbon.lastTouchMs = System.currentTimeMillis()
                        } else {
                            val adjusted = StageZoomPolicy.adjust(
                                gain, autoFrameScale, host.autoFrameArmed(), exp(-d.y * 0.0042f),
                            )
                            if (adjusted.owner == StageZoomOwner.AUTO_FRAME) {
                                autoFrameScale = adjusted.value
                                host.setAutoFrameScale(autoFrameScale)
                                frameEdited = true
                                ribbon.showSize(RibbonRail.frame(autoFrameScale), RibbonRail.frame(1f), autoFrameScale)
                            } else {
                                gain = adjusted.value
                                host.setGainAbsolute(gain)
                                ribbon.showSize(GainWords.toSlider(gain), null, gain)
                            }
                            if (adjusted.crossedNeutral) Haptics.light(host.view())
                            ribbon.at = ch.position
                            ribbon.visible = true
                            ribbon.lastTouchMs = System.currentTimeMillis()
                        }
                        ch.consume()
                    }
                }
              }
            } finally {
                if (frameEdited) host.finishAutoFrameScale()
                if (bottomChromeActive) {
                    if (finishedNormally) {
                        host.releaseBottomChromePull(velocity.calculateVelocity().y)
                    } else {
                        host.cancelBottomChromePull()
                    }
                }
            }
        }
    }

internal object RibbonRail {
    private val lo = kotlin.math.ln(AutoFramePreference.MIN)
    private val hi = kotlin.math.ln(AutoFramePreference.MAX)
    fun frame(scale: Float): Float =
        ((kotlin.math.ln(AutoFramePreference.normalize(scale)) - lo) / (hi - lo)).coerceIn(0f, 1f)
}

internal fun RibbonState.showSize(position: Float, tick: Float?, multiplier: Float) {
    text = ""
    rail = position
    railTick = tick
    railValue = GainWords.multiplier(multiplier)
    railTop = "closer"
    railBottom = "farther"
}

// Gesture feedback: a thin rail at the screen edge (size or glow), or a short word. No numbers.
// Fades 600 ms after release.
@Composable
fun GestureRibbon(ribbon: RibbonState, p: Palette) {
    LaunchedEffect(ribbon.lastTouchMs) {
        if (ribbon.visible) {
            delay(600)
            ribbon.visible = false
        }
    }
    AnimatedVisibility(visible = ribbon.visible, enter = fadeIn(), exit = fadeOut()) {
        val rail = ribbon.rail
        if (rail != null) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(chromeSafeDrawingInsets(16.dp, 16.dp))) {
                Column(
                    Modifier.align(Alignment.CenterEnd).padding(end = 14.dp)
                        .semantics(mergeDescendants = true) {
                            contentDescription = "${ribbon.railTop} to ${ribbon.railBottom}"
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Mono(ribbon.railTop, p.ink2, Type.dataSm)
                    Box(Modifier.padding(vertical = 8.dp).width(24.dp).height(240.dp)) {
                        Box(Modifier.align(Alignment.Center).width(Dim.hairline).fillMaxHeight().background(p.line))
                        ribbon.railTick?.let { tick ->
                            Box(Modifier.align(Alignment.TopCenter)
                                .offset(y = (240.dp - 1.dp) * (1f - tick))
                                .width(12.dp).height(1.dp).background(p.ink2))
                        }
                        Box(Modifier.align(Alignment.TopCenter)
                            .offset(y = (240.dp - 8.dp) * (1f - rail))
                            .size(width = 20.dp, height = 8.dp).background(p.accent))
                    }
                    ribbon.railValue?.let { Mono(it, p.ink, Type.data, Modifier.padding(top = 6.dp)) }
                    Mono(ribbon.railBottom, p.ink2, Type.dataSm)
                }
            }
        } else {
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
}
