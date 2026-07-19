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
    id = "blossom", label = "Blossom", dark = false,
    plane = c(0xf3e6e6), surface = c(0xfcf4f3), surface2 = c(0xf6e9e9),
    ink = c(0x2b2128), ink2 = c(0x5f4f57), muted = c(0x9c8890),
    line = ca(43, 33, 40, 41), lineStrong = ca(43, 33, 40, 77),
    accent = c(0xc85a7c), onAccent = c(0xfff7f9),
    stone = c(0xe7dad9), stoneHi = c(0xfffafa), stoneLo = c(0xc9b6b8),
    accentFollowsBeam = false,
)

val BlossomDark = Palette(
    id = "blossom_dark", label = "Blossom Dark", dark = true,
    plane = c(0x1c1016), surface = c(0x281821), surface2 = c(0x33212c),
    ink = c(0xf5eaef), ink2 = c(0xc9b0bc), muted = c(0x917986),
    line = ca(244, 233, 238, 36), lineStrong = ca(244, 233, 238, 82),
    accent = c(0xec8fac), onAccent = c(0x1a0e14),
    stone = c(0x3b2631), stoneHi = c(0x553948), stoneLo = c(0x1d1117),
    accentFollowsBeam = true,
)

val LightRoom = Palette(
    id = "light", label = "Light", dark = false,
    plane = c(0xeaeef2), surface = c(0xffffff), surface2 = c(0xf5f8fa),
    ink = c(0x0e1620), ink2 = c(0x43515e), muted = c(0x7a8894),
    line = ca(14, 22, 32, 31), lineStrong = ca(14, 22, 32, 71),
    accent = c(0x0c94a2), onAccent = c(0xffffff),
    stone = c(0xe4e9ee), stoneHi = c(0xffffff), stoneLo = c(0xc2ccd4),
    accentFollowsBeam = false,
)

val DarkRoom = Palette(
    id = "dark", label = "Dark", dark = true,
    plane = c(0x0a0810), surface = c(0x141019), surface2 = c(0x1b1522),
    ink = c(0xf0eaf0), ink2 = c(0xb3a6b3), muted = c(0x7d6f7d),
    line = ca(240, 234, 240, 31), lineStrong = ca(240, 234, 240, 66),
    accent = c(0xe78aa6), onAccent = c(0x160810),
    stone = c(0x241d29), stoneHi = c(0x332838), stoneLo = c(0x140f18),
    accentFollowsBeam = false,
)

val Chromacore = Palette(
    id = "chromacore", label = "Chromacore", dark = true,
    plane = c(0x080810), surface = c(0x0d0d16), surface2 = c(0x12121e),
    ink = c(0xe8e8f0), ink2 = c(0xa8b4c4), muted = c(0x606a7e),
    line = ca(0, 229, 255, 28), lineStrong = ca(0, 229, 255, 64),
    accent = c(0x00e5ff), onAccent = c(0x030a0e),
    stone = c(0x10181e), stoneHi = c(0x1a2a30), stoneLo = c(0x060c10),
    accentFollowsBeam = false,
)

val Basalt = Palette(
    id = "basalt", label = "Basalt", dark = true,
    plane = c(0x171719), surface = c(0x222225), surface2 = c(0x1a1a1d),
    ink = c(0xdbd7ce), ink2 = c(0x9a968e), muted = c(0x66635d),
    line = ca(0, 0, 0, 110), lineStrong = ca(0, 0, 0, 150),
    accent = c(0x9cb4c9), onAccent = c(0x101215),
    stone = c(0x2c2c30), stoneHi = c(0x4a4a50), stoneLo = c(0x0e0e10),
    accentFollowsBeam = false,
)

val Afterglow = Palette(
    id = "afterglow", label = "Afterglow", dark = true,
    plane = c(0x050607), surface = c(0x0b0d0e), surface2 = c(0x101314),
    ink = c(0xd6e0dc), ink2 = c(0x8c9c96), muted = c(0x55625d),
    line = ca(255, 255, 255, 20), lineStrong = ca(255, 255, 255, 46),
    accent = c(0x63ffb0), onAccent = c(0x020805),
    stone = c(0x121615), stoneHi = c(0x202825), stoneLo = c(0x060a08),
    accentFollowsBeam = true,
)

val Stonework95 = Palette(
    id = "stonework95", label = "Stonework 95", dark = false,
    plane = c(0xc8c5bd), surface = c(0xd9d6ce), surface2 = c(0xc3c0b8),
    ink = c(0x1a1a1f), ink2 = c(0x45454d), muted = c(0x6e6e76),
    line = ca(26, 26, 31, 64), lineStrong = ca(26, 26, 31, 115),
    accent = c(0x20328c), onAccent = c(0xf2f2f7),
    stone = c(0xd4d0c8), stoneHi = c(0xfffffb), stoneLo = c(0x86837c),
    accentFollowsBeam = false,
)

val Amoled = Palette(
    id = "amoled", label = "AMOLED", dark = true,
    plane = c(0x000000), surface = c(0x000000), surface2 = c(0x0d0d0d),
    ink = c(0xffffff), ink2 = c(0xc4c4c4), muted = c(0x8a8a8a),
    line = ca(255, 255, 255, 46), lineStrong = ca(255, 255, 255, 92),
    accent = c(0xff2d7e), onAccent = c(0xffffff),
    stone = c(0x141414), stoneHi = c(0x333333), stoneLo = c(0x000000),
    accentFollowsBeam = false,
)

val Paper = Palette(
    id = "paper", label = "Paper", dark = false,
    plane = c(0xf2ecdf), surface = c(0xfaf6ec), surface2 = c(0xece5d6),
    ink = c(0x2e2820), ink2 = c(0x5c5244), muted = c(0x8f8472),
    line = ca(46, 40, 32, 46), lineStrong = ca(46, 40, 32, 92),
    accent = c(0xc33d2e), onAccent = c(0xfdf9f2),
    stone = c(0xe8e0d0), stoneHi = c(0xfffdf6), stoneLo = c(0xc0b5a0),
    accentFollowsBeam = false,
)

val CrtAmber = Palette(
    id = "amber", label = "CRT Amber", dark = true,
    plane = c(0x0e0802), surface = c(0x170e04), surface2 = c(0x201406),
    ink = c(0xffc966), ink2 = c(0xc99642), muted = c(0x8a662e),
    line = ca(255, 176, 0, 38), lineStrong = ca(255, 176, 0, 84),
    accent = c(0xffb000), onAccent = c(0x1a0f00),
    stone = c(0x241708), stoneHi = c(0x452d10), stoneLo = c(0x080501),
    accentFollowsBeam = false,
)

// Liquid Glass (Ben's ask, mobile-first — desktop backport is a future ask):
// the instrument dissolved into translucency. Glacial ink over cool near-black;
// the beam refracts through the accent (follows_beam — glass bends light).
val LiquidGlass = Palette(
    id = "glass", label = "Liquid Glass", dark = true,
    plane = c(0x05070c), surface = c(0x10131c), surface2 = c(0x181c28),
    ink = c(0xe8eefc), ink2 = c(0xaeb9d6), muted = c(0x67718c),
    line = ca(160, 190, 255, 42), lineStrong = ca(180, 205, 255, 90),
    accent = c(0x7fb8ff), onAccent = c(0x061018),
    stone = c(0x151a26), stoneHi = c(0x3a4a68), stoneLo = c(0x090b12),
    accentFollowsBeam = true,
)

// The model that built v4 signs the guestbook: a storyteller's room. 🐢
val Fable = Palette(
    id = "fable", label = "Fable", dark = true,
    plane = c(0x0a1411), surface = c(0x111e1a), surface2 = c(0x172822),
    ink = c(0xeaf2ec), ink2 = c(0xadc4b8), muted = c(0x6e877b),
    line = ca(158, 232, 200, 34), lineStrong = ca(158, 232, 200, 72),
    accent = c(0xeac279), onAccent = c(0x141a10),
    stone = c(0x1c2e27), stoneHi = c(0x2e463b), stoneLo = c(0x0c1612),
    accentFollowsBeam = false,
)

// Menu order = desktop menu order (12 verbatim) + the mobile-first 13th.
val Rooms = listOf(
    Blossom, BlossomDark, LightRoom, DarkRoom, Chromacore, Basalt,
    Afterglow, Stonework95, Amoled, Paper, CrtAmber, Fable, LiquidGlass,
)

fun paletteById(id: String): Palette = Rooms.find { it.id == id } ?: BlossomDark
