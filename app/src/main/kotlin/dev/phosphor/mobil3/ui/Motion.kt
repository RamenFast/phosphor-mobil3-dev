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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.compositionLocalOf
import dev.phosphor.mobil3.settings.appearance.AppearanceMotion
import dev.phosphor.mobil3.settings.appearance.AppearanceMotionPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// The house motion table: 80–200 ms, eased, purposeful; 240 ms is reserved for the one
// deliberately longer move (a room change). Reduced-motion turns everything into cuts.
object Motion {
    const val press = 80       // stone sink, key feedback
    const val summon = 120     // console fade-in
    const val settle = 160     // console fade-out + 8px settle-down
    const val sheet = 200      // sheet travel, decelerate, no bounce
    const val room = 240       // whole-chrome room crossfade
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
        MotionFeel.Cut -> snap()
        MotionFeel.Detented -> tween(ms, easing = stepEasing())
        MotionFeel.Springy -> spring(dampingRatio = 0.85f, stiffness = 500f)
        MotionFeel.Eased -> tween(ms, easing = easing)
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

/** Composed visible glyphs animate only a real state change, never an idle clock. */
@Composable
internal fun stateGlyphFloat(target: Float, label: String): Float {
    val style = LocalRoomStyle.current
    val motion = AppearanceMotion.entries[style.motion.ordinal]
    if (!AppearanceMotionPolicy.stateChange(true, true, true, LocalReducedMotion.current, motion)) return target
    val value by animateFloatAsState(target,
        tween((Motion.settle * style.durationScale).toInt().coerceIn(80, 200),
            easing = if (style.motion == MotionFeel.Detented) stepEasing() else Motion.standard), label = label)
    return value
}

// An animation spec that honors reduced-motion (hard cut when on).
fun <T> motionSpec(
    reduced: Boolean,
    durationMs: Int,
    easing: Easing = Motion.standard,
): FiniteAnimationSpec<T> = if (reduced) snap() else tween(durationMs, easing = easing)
