package dev.phosphor.mobil3.ui

import androidx.compose.ui.graphics.Color

// Mirrors the engine's mode order (phosphor-dsp Mode) and beam presets (phosphor-beam).
val ModeLabels = listOf(
    "xy · scope art", "xy · goniometer", "xy · swirl", "xy · dots",
    "attractor", "time helix", "waveform", "ring",
    "spectrum", "spectrum · radial", "spectrum · tunnel",
)

// Short tags for the status band.
val ModeTags = listOf(
    "xy", "xy45", "swirl", "dots", "3d", "helix", "wave", "ring", "spec", "radial", "tunnel",
)

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

// Beam integration rate — the genuine "beyond 120" lever. Each displayed frame integrates N
// sub-steps of the beam (dt-correct decay), so the trace is smoother and more recent even
// though the panel shows 120. Values are the effective integration rate.
data class RateOption(val label: String, val oversample: Int)

val BeamRates = listOf(
    RateOption("120", 1),
    RateOption("240", 2),
    RateOption("480", 4),
)

const val BeamRateNote = "Integrates the beam at this rate per 120 Hz frame — a denser, " +
    "more-recent trace (the real 'beyond 120'). Higher costs battery."

