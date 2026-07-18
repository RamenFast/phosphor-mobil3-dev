package dev.phosphor.mobil3.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// House design tokens, ported verbatim from phosphor/crates/phosphor-app/src/theme.rs.
// Sharp corners, hairline frames, mono data, dimensional stone for the few important controls.
@Immutable
data class Palette(
    val id: String,
    val label: String,
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
)

private fun c(hex: Long) = Color(0xFF000000 or hex)
private fun ca(r: Int, g: Int, b: Int, a: Int) = Color(r, g, b, a)

val BlossomDark = Palette(
    id = "blossom_dark", label = "Blossom Dark",
    plane = c(0x1c1016), surface = c(0x281821), surface2 = c(0x33212c),
    ink = c(0xf5eaef), ink2 = c(0xc9b0bc), muted = c(0x917986),
    line = ca(244, 233, 238, 36), lineStrong = ca(244, 233, 238, 82),
    accent = c(0xec8fac), onAccent = c(0x1a0e14),
    stone = c(0x3b2631), stoneHi = c(0x553948), stoneLo = c(0x1d1117),
    accentFollowsBeam = true,
)

val Amoled = Palette(
    id = "amoled", label = "AMOLED",
    plane = c(0x000000), surface = c(0x000000), surface2 = c(0x0d0d0d),
    ink = c(0xffffff), ink2 = c(0xc4c4c4), muted = c(0x8a8a8a),
    line = ca(255, 255, 255, 46), lineStrong = ca(255, 255, 255, 92),
    accent = c(0xff2d7e), onAccent = c(0xffffff),
    stone = c(0x141414), stoneHi = c(0x333333), stoneLo = c(0x000000),
    accentFollowsBeam = false,
)

// The rooms shipped so far (the remaining desktop rooms port in later M5 passes).
val Rooms = listOf(BlossomDark, Amoled)
