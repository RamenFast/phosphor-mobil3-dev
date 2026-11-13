package dev.phosphor.mobil3.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlin.math.pow

// House design tokens, ported verbatim from phosphor/crates/phosphor-app/src/theme.rs
// (PALETTES[12], same order). Sharp corners, hairline frames, mono data, dimensional
// stone for the few important controls. Blossom Dark is THE default; the last four are
// the distinct-visual-system rooms — bevel city, true black, warm paper, amber CRT.
@Immutable
data class Palette(
    val id: String,
    val label: String,
    val dark: Boolean,
    val plane: Color,
    val surface: Color,
    val surface2: Color,
    val ink: Color,
    val ink2: Color,
    val muted: Color,
    val line: Color,
    val lineStrong: Color,
    val accent: Color,
    val onAccent: Color,
    val stone: Color,
    val stoneHi: Color,
    val stoneLo: Color,
    val accentFollowsBeam: Boolean,
    // The live beam's color as a chrome ink (fed by withBeam every tick, all rooms).
    // Value-indicating elements — seek progress, rule fills, kept ranges — draw with
    // this so the chrome's moving parts glow the same phosphor as the trace; the
    // room's structural accent stays its own.
    val beamAccent: Color = Color.Unspecified,
) {
    val liveAccent: Color get() = if (beamAccent == Color.Unspecified) accent else beamAccent

    // Blend a beam color into the chrome — port of theme.rs with_beam: gamma-lift,
    // then 82% toward the beam hue. Every room gets beamAccent; only
    // accent_follows_beam rooms let it take over the structural accent too.
    fun withBeam(beam: FloatArray): Palette {
        fun lift(c: Float) = (c.toDouble().pow(1.0 / 2.2).coerceIn(0.0, 1.0) * 255.0).toInt()
        fun mix(beamCh: Int, base: Int) = ((beamCh * 0.82 + base * 0.18).toInt()).coerceAtLeast(base)
        val blended = Color(
            mix(lift(beam[0]), 0x30),
            mix(lift(beam[1]), 0x40),
            mix(lift(beam[2]), 0x38),
        )
        return copy(
            beamAccent = blended,
            accent = if (accentFollowsBeam) blended else accent,
        )
    }

    // The 240 ms whole-chrome crossfade (port of theme.rs lerp_to): every color
    // slot lerps — compose's Color lerp is RGBA-aware, so the alpha-bearing
    // line/lineStrong tokens interpolate correctly — while identity fields come
    // from the DESTINATION room.
    fun lerpTo(other: Palette, t: Float): Palette {
        fun l(a: Color, b: Color) = androidx.compose.ui.graphics.lerp(a, b, t)
        return other.copy(
            plane = l(plane, other.plane),
            surface = l(surface, other.surface),
            surface2 = l(surface2, other.surface2),
            ink = l(ink, other.ink),
            ink2 = l(ink2, other.ink2),
            muted = l(muted, other.muted),
            line = l(line, other.line),
            lineStrong = l(lineStrong, other.lineStrong),
            accent = l(accent, other.accent),
            onAccent = l(onAccent, other.onAccent),
            stone = l(stone, other.stone),
            stoneHi = l(stoneHi, other.stoneHi),
            stoneLo = l(stoneLo, other.stoneLo),
        )
    }
}

/** theme.rs smoothstep — the crossfade's ease. */
fun smoothstep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private fun c(hex: Long) = Color(0xFF000000 or hex)
private fun ca(r: Int, g: Int, b: Int, a: Int) = Color(r, g, b, a)

val Blossom = Palette(
    id = "blossom", label = "Blossom Light", dark = false,
    plane = c(0xf3e6e6), surface = c(0xfcf4f3), surface2 = c(0xf6e9e9),
    ink = c(0x2b2128), ink2 = c(0x5f4f57), muted = c(0x9c8890),
    line = ca(43, 33, 40, 41), lineStrong = ca(43, 33, 40, 77),
    accent = c(0xc85a7c), onAccent = c(0xfff7f9),
    stone = c(0xe7dad9), stoneHi = c(0xfffafa), stoneLo = c(0xc9b6b8),
    accentFollowsBeam = false,
)

val Amoled = Palette(
    id = "amoled", label = "Blossom AMOLED", dark = true,
    plane = c(0x000000), surface = c(0x000000), surface2 = c(0x0d0d0d),
    ink = c(0xffffff), ink2 = c(0xc4c4c4), muted = c(0x8a8a8a),
    line = ca(255, 255, 255, 46), lineStrong = ca(255, 255, 255, 92),
    accent = c(0xff2d7e), onAccent = c(0xffffff),
    stone = c(0x141414), stoneHi = c(0x333333), stoneLo = c(0x000000),
    accentFollowsBeam = false,
)

// Liquid Glass uses translucent cool surfaces and lets the beam tint the accent.
val LiquidGlass = Palette(
    id = "glass", label = "Liquid Glass", dark = true,
    plane = c(0x05070c), surface = c(0x10131c), surface2 = c(0x181c28),
    ink = c(0xe8eefc), ink2 = c(0xaeb9d6), muted = c(0x67718c),
    line = ca(160, 190, 255, 42), lineStrong = ca(180, 205, 255, 90),
    accent = c(0x7fb8ff), onAccent = c(0x061018),
    stone = c(0x151a26), stoneHi = c(0x3a4a68), stoneLo = c(0x090b12),
    accentFollowsBeam = true,
)

// The three rooms this build ships. Ids are the stored pref values and stay stable.
val Rooms = listOf(Amoled, Blossom, LiquidGlass)

// A stored room from an older build may name a room that no longer ships.
fun paletteById(id: String): Palette = Rooms.find { it.id == id } ?: Amoled
