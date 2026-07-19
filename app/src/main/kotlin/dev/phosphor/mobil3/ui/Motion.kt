package dev.phosphor.mobil3.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// The house motion table: 80–200 ms, eased, purposeful; 240 ms is reserved for the one
// deliberately longer move (a room change). Reduced-motion turns everything into cuts.
object Motion {
    const val press = 80       // stone sink, key feedback
    const val summon = 120     // console fade-in
    const val settle = 160     // console fade-out + 8px settle-down
    const val sheet = 200      // sheet travel, decelerate, no bounce
    const val room = 240       // whole-chrome room crossfade
    const val bloomSettle = 260 // P7 bloom: the eased room exhales, then the raster decays
    const val bloomDetent = 170 // service-bench return through discrete switch positions
    const val pullSettle = 200  // finger-tracked chrome completes the distance left by the hand

    val decelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Departure curve — a dismissed sheet ACCELERATES away (motion shows intent). */
    val accelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
}

/**
 * One-dimensional, finger-tracked reveal shared by the play-bar sheet door and S9 menu.
 *
 * Pointer-input scopes may only call the synchronous methods here. Animatable work is
 * serialized onto the real composition scope, preserving the restricted-scope law that
 * previously bit this project. Travel is measured in pixels so a slow pull reveals the
 * same physical amount of chrome that the finger travelled.
 */
class PullRevealState(private val scope: CoroutineScope) {
    val animation = Animatable(0f)

    private var revealPx = 0f
    private var travelPx = 1f
    private var motionJob: Job? = null
    private var tracking = false

    val progress: Float get() = animation.value.coerceIn(0f, 1f)

    fun setTravelPx(px: Float) {
        val oldTravel = travelPx
        travelPx = px.coerceAtLeast(1f)
        if (tracking) {
            revealPx = revealPx.coerceIn(0f, travelPx)
            snapTracked()
            return
        }
        // When measured geometry replaces the provisional estimate during a settle,
        // preserve the anchored end state or the current proportional progress.
        revealPx = when {
            animation.value <= 0.001f -> 0f
            animation.value >= 0.999f -> travelPx
            else -> (animation.value * oldTravel).coerceIn(0f, travelPx)
        }
    }

    fun begin(resetClosed: Boolean) {
        motionJob?.cancel()
        tracking = true
        revealPx = if (resetClosed) 0f else progress * travelPx
        snapTracked()
    }

    fun dragBy(upwardDeltaPx: Float) {
        tracking = true
        revealPx = (revealPx + upwardDeltaPx).coerceIn(0f, travelPx)
        snapTracked()
    }

    fun settleFromRelease(
        verticalVelocityPxPerSecond: Float,
        flickThresholdPxPerSecond: Float,
        style: RoomStyle,
        reduced: Boolean,
        onSettled: (Boolean) -> Unit,
    ) {
        tracking = false
        val opens = when {
            verticalVelocityPxPerSecond <= -flickThresholdPxPerSecond -> true
            verticalVelocityPxPerSecond >= flickThresholdPxPerSecond -> false
            else -> revealPx / travelPx >= 0.40f
        }
        settleTo(
            open = opens,
            initialProgressVelocity = (-verticalVelocityPxPerSecond / travelPx).coerceIn(-8f, 8f),
            style = style,
            reduced = reduced,
            onSettled = onSettled,
        )
    }

    fun settleTo(
        open: Boolean,
        style: RoomStyle,
        reduced: Boolean,
        onSettled: (Boolean) -> Unit = {},
    ) = settleTo(open, 0f, style, reduced, onSettled)

    private fun settleTo(
        open: Boolean,
        initialProgressVelocity: Float,
        style: RoomStyle,
        reduced: Boolean,
        onSettled: (Boolean) -> Unit,
    ) {
        tracking = false
        motionJob?.cancel()
        motionJob = scope.launch {
            animation.stop()
            val target = if (open) 1f else 0f
            if (reduced) {
                // Reduced motion keeps only a brief opacity transition at call sites.
                animation.animateTo(target, tween(Motion.press, easing = Motion.standard))
            } else when (style.motion) {
                MotionFeel.Cut -> animation.snapTo(target)
                MotionFeel.Detented -> animation.animateTo(
                    target,
                    tween(
                        (Motion.pullSettle * style.durationScale).toInt().coerceAtLeast(1),
                        easing = stepEasing(5),
                    ),
                )
                MotionFeel.Springy -> animation.animateTo(
                    target,
                    spring(dampingRatio = 0.72f, stiffness = 380f, visibilityThreshold = 0.002f),
                    initialVelocity = initialProgressVelocity,
                )
                MotionFeel.Eased -> animation.animateTo(
                    target,
                    tween(
                        (Motion.pullSettle * style.durationScale).toInt().coerceAtLeast(1),
                        easing = if (open) Motion.decelerate else Motion.accelerate,
                    ),
                )
            }
            revealPx = target * travelPx
            onSettled(open)
        }
    }

    private fun snapTracked() {
        val target = (revealPx / travelPx).coerceIn(0f, 1f)
        motionJob?.cancel()
        motionJob = scope.launch {
            animation.stop()
            animation.snapTo(target)
        }
    }
}

/**
 * Shared bottom-pull state for the stage and every sheet scroll host.
 *
 * Positive values are the resisted pull. A Springy release may cross slightly below zero;
 * the sheet uses that signed value for its physical rebound while [enginePull] turns the
 * negative lobe into a much smaller secondary beam breath. The renderer still owns the
 * actual P7 flash/glow accumulation and decay -- this state only drives new beam energy.
 */
class BloomPullState(private val scope: CoroutineScope) {
    val animation = Animatable(0f)

    private var rawPullPx = 0f
    private var lastResistancePx = 160f
    private var releaseJob: Job? = null

    val visualPull: Float get() = animation.value

    // begin/dragBy are called from restricted pointer-input scopes, so they cannot
    // suspend — the raw-pull math stays synchronous and only the Animatable calls hop
    // onto the state's (sequential, main) scope.
    fun begin(resistancePx: Float) {
        releaseJob?.cancel()
        lastResistancePx = resistancePx.coerceAtLeast(1f)
        val held = animation.value.coerceIn(0f, 0.94f)
        rawPullPx = lastResistancePx * held / (1f - held).coerceAtLeast(0.06f)
        scope.launch { animation.stop() }
    }

    fun dragBy(upwardDeltaPx: Float, resistancePx: Float, feel: MotionFeel) {
        lastResistancePx = resistancePx.coerceAtLeast(1f) * when (feel) {
            MotionFeel.Detented -> 1.42f // stiff rotary mechanism
            MotionFeel.Springy -> 0.72f  // glass yields sooner
            MotionFeel.Eased -> 1f
            MotionFeel.Cut -> 1.08f
        }
        rawPullPx = (rawPullPx + upwardDeltaPx).coerceIn(0f, lastResistancePx * 8f)
        val resisted = rawPullPx / (rawPullPx + lastResistancePx)
        val roomValue = if (feel == MotionFeel.Detented) {
            // Seven physical switch positions, continuously selected by pull distance.
            (resisted * 7f).roundToInt() / 7f
        } else resisted
        scope.launch { animation.snapTo(roomValue.coerceIn(0f, 1f)) }
    }

    fun release(feel: MotionFeel, reduced: Boolean) {
        releaseJob?.cancel()
        releaseJob = scope.launch {
            rawPullPx = 0f
            if (reduced || feel == MotionFeel.Cut) {
                animation.snapTo(0f)
                return@launch
            }
            when (feel) {
                MotionFeel.Detented -> animation.animateTo(
                    0f,
                    tween(Motion.bloomDetent, easing = stepEasing(7)),
                )
                MotionFeel.Springy -> animation.animateTo(
                    0f,
                    spring(dampingRatio = 0.48f, stiffness = 300f, visibilityThreshold = 0.002f),
                )
                MotionFeel.Eased -> animation.animateTo(
                    0f,
                    tween(Motion.bloomSettle, easing = Motion.decelerate),
                )
                MotionFeel.Cut -> Unit // handled above
            }
        }
    }

    /** Engine command value: direct while held; a restrained rebound only in glass. */
    fun enginePull(feel: MotionFeel): Float {
        val v = animation.value
        return if (v >= 0f) v.coerceAtMost(1f)
        else if (feel == MotionFeel.Springy) (abs(v) * 0.20f).coerceAtMost(0.14f)
        else 0f
    }
}

val LocalBloomPull = compositionLocalOf<BloomPullState?> { null }

/** Quantized step easing — the bench's rotary-switch feel. Ends exactly at 1. */
fun stepEasing(steps: Int = 5): Easing = Easing { t ->
    (kotlin.math.floor(t * steps) / (steps - 1f)).coerceIn(0f, 1f)
}

/** motionSpec that also honors the room's MotionFeel (duration scale + curve family). */
fun <T> styleSpec(
    reduced: Boolean,
    style: RoomStyle,
    durationMs: Int,
    easing: Easing = Motion.standard,
): FiniteAnimationSpec<T> {
    if (reduced) return snap()
    val ms = (durationMs * style.durationScale).toInt().coerceAtLeast(1)
    return when (style.motion) {
        MotionFeel.Detented -> tween(ms, easing = stepEasing())
        else -> tween(ms, easing = easing)
    }
}

// True when Android's remove-animations accessibility setting is on.
fun readReducedMotion(context: Context): Boolean =
    Settings.Global.getFloat(
        context.contentResolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f
    ) == 0f || Settings.Global.getFloat(
        context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
    ) == 0f

val LocalReducedMotion = compositionLocalOf { false }

// An animation spec that honors reduced-motion (hard cut when on).
fun <T> motionSpec(
    reduced: Boolean,
    durationMs: Int,
    easing: Easing = Motion.standard,
): FiniteAnimationSpec<T> = if (reduced) snap() else tween(durationMs, easing = easing)
