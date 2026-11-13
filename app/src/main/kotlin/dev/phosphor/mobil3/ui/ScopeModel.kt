package dev.phosphor.mobil3.ui

import androidx.compose.ui.graphics.Color
import kotlin.random.Random

// The faces this app ships. `engine` is the engine's own mode index (phosphor-dsp Mode
// order), and it is the value stored in prefs and sent to the renderer, so the list can
// change without renumbering anyone's saved settings.
data class ScopeMode(val engine: Int, val label: String, val tag: String)

val ScopeModes = listOf(
    ScopeMode(0, "xy · scope art", "xy"),
    ScopeMode(1, "xy · goniometer", "xy45"),
    ScopeMode(6, "waveform", "wave"),
    ScopeMode(7, "ring", "ring"),
    ScopeMode(8, "spectrum", "spec"),
)

const val DefaultScopeMode = 1

val ScopeModeIds: List<Int> = ScopeModes.map { it.engine }

fun scopeMode(engine: Int): ScopeMode? = ScopeModes.firstOrNull { it.engine == engine }

// A stored mode from an older build may name a face that no longer ships.
fun keptModeOrDefault(engine: Int): Int = if (engine in ScopeModeIds) engine else DefaultScopeMode

// Stored bans: only shipped faces count, and at least two faces must stay in play.
fun keptBans(stored: Collection<Int>): Set<Int> =
    stored.filter { it in ScopeModeIds }.toSet()
        .let { if (ScopeModes.size - it.size < 2) emptySet() else it }

// Swipe stepping walks the shipped list and wraps.
fun stepMode(current: Int, delta: Int): Int {
    val at = ScopeModeIds.indexOf(keptModeOrDefault(current))
    return ScopeModeIds[Math.floorMod(at + delta, ScopeModeIds.size)]
}

// A RANDOM roll is always a shipped face, never the face already on glass, and never
// a banned face. If bans ever starve the pool (belt-and-braces; the sheet guard keeps ≥2
// faces in play), they are ignored rather than freezing the die.
fun rollModeExcluding(currentMode: Int, banned: Set<Int> = emptySet()): Int {
    val current = keptModeOrDefault(currentMode)
    val pool = ScopeModeIds.filter { it != current && it !in banned }
        .ifEmpty { ScopeModeIds.filter { it != current } }
    return pool.random()
}

data class BeamSwatch(val label: String, val color: Color)

val BeamColors = listOf(
    BeamSwatch("P7 Green", Color(0xFF6BFF8C)),
    BeamSwatch("Amber", Color(0xFFFF9E1F)),
    BeamSwatch("Ice Blue", Color(0xFF59BFFF)),
    BeamSwatch("White", Color(0xFFEFF4FF)),
    BeamSwatch("Vaporwave", Color(0xFFFF6BD6)),
    BeamSwatch("Red Phosphor", Color(0xFFFF4B4B)),
    BeamSwatch("Ultraviolet", Color(0xFFB18CFF)),
    BeamSwatch("Solar Gold", Color(0xFFFFD24B)),
    BeamSwatch("Cyan Tube", Color(0xFF4BE6E6)),
)

// FPS options. Android's compositor vsync-locks every app surface to the panel refresh
// (120 Hz on the S25) — no app can *present* faster. So the caps are real, and "uncapped"
// removes the software limiter (renders flat out for benchmarking/heat; still shown at 120).
data class FpsOption(val label: String, val value: Int)

val FpsOptions = listOf(
    FpsOption("60", 60),
    FpsOption("90", 90),
    FpsOption("120 · panel max", 0),
    FpsOption("uncapped", -1),
)

// Honest note shown under the frame-rate row.
const val FpsNote = "The S25 panel presents at 120 Hz max (Android composites at vsync). " +
    "Uncapped renders flat out but still shows at 120."

// Beam reconstruction quality. The panel still presents at 120 Hz; the desktop DSP's
// streaming polyphase stage reconstructs the contiguous 48 kHz tap before one beam deposit.
data class RateOption(val label: String, val oversample: Int)

val BeamRates = listOf(
    RateOption("120 · 48 kHz", 1),
    RateOption("240 · 96 kHz", 2),
    RateOption("480 · 192 kHz", 4),
)

const val BeamRateNote = "Reconstructs more points inside each contiguous audio window; " +
    "the panel remains 120 Hz. Higher costs battery."
