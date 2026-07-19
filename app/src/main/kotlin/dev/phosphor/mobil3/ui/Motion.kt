package dev.phosphor.mobil3.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.compositionLocalOf

// The house motion table: 80–200 ms, eased, purposeful; 240 ms is reserved for the one
// deliberately longer move (a room change). Reduced-motion turns everything into cuts.
object Motion {
    const val press = 80       // stone sink, key feedback
    const val summon = 120     // console fade-in
    const val settle = 160     // console fade-out + 8px settle-down
    const val sheet = 200      // sheet travel, decelerate, no bounce
    const val room = 240       // whole-chrome room crossfade

    val decelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Departure curve — a dismissed sheet ACCELERATES away (motion shows intent). */
    val accelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
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
