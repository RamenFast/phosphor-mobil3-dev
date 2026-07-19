package dev.phosphor.mobil3.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ── RoomStyle: a room is not a palette swap — it is a PERSONALITY ────────────
// The bundle every composable consults for control character, motion feel and
// density. Absorbs the old `p.id == "amoled"` discriminators; the amber bench
// (Annotated) and liquid glass (Glass) souls ride the same rails. House
// non-negotiables (sharp corners, hairlines, mono data, Obsidian dismiss) hold
// in every character — EXCEPT Glass's cornerRadius, sanctioned by Ben's
// explicit ask and scoped to that room alone.

enum class ChromeCharacter {
    /** Carved stone — bevels, catch-light, dimensional importance (the reference). */
    Carved,
    /** The void — no fills, engraved hairline outlines, importance via accent weight. */
    Engraved,
    /** The service bench — carved base + part-number designators + leader lines. */
    Annotated,
    /** Liquid glass — translucent slabs, specular rims, room-scoped rounding. */
    Glass,
}

enum class MotionFeel {
    /** Standard decelerate/settle curves. */
    Eased,
    /** Hard, fast cuts (the void: importance without ornament). */
    Cut,
    /** Quantized steps — a rotary switch, not a spring (the bench). */
    Detented,
    /** Gentle spring settle with slight overshoot (glass — the one room that bounces). */
    Springy,
}

@Immutable
data class RoomStyle(
    val character: ChromeCharacter = ChromeCharacter.Carved,
    val motion: MotionFeel = MotionFeel.Eased,
    /** Multiplies animation durations (the void halves them). */
    val durationScale: Float = 1f,
    /** Multiplies paddings/gaps (the bench tightens one notch). */
    val densityScale: Float = 1f,
    /** Corner rounding — 0 everywhere except Glass (room-scoped sanction). */
    val cornerRadius: Dp = 0.dp,
    /** Prose renders in the mono face (service manuals are line-printer set). */
    val monoProse: Boolean = false,
    /** Part-number designators + dotted leaders (`V2 · MODE`). */
    val designators: Boolean = false,
    /** Multiplies sheet/console surface alpha (glass runs far more translucent). */
    val panelAlphaScale: Float = 1f,
)

val CarvedStyle = RoomStyle()
val VoidStyle = RoomStyle(
    character = ChromeCharacter.Engraved,
    motion = MotionFeel.Cut,
    durationScale = 0.5f,
)
val BenchStyle = RoomStyle(
    character = ChromeCharacter.Annotated,
    motion = MotionFeel.Detented,
    densityScale = 0.9f,
    monoProse = true,
    designators = true,
)
val GlassStyle = RoomStyle(
    character = ChromeCharacter.Glass,
    motion = MotionFeel.Springy,
    cornerRadius = 12.dp,
    panelAlphaScale = 0.62f,
)

private val styleById = mapOf(
    "amoled" to VoidStyle,
    "amber" to BenchStyle,
    "glass" to GlassStyle,
)

/** Every palette carries its personality; the other rooms live in the reference. */
val Palette.style: RoomStyle
    get() = styleById[id] ?: CarvedStyle

/** Provided at the PhosphorScreen root from the DISPLAYED room (crossfade-aware). */
val LocalRoomStyle = compositionLocalOf { CarvedStyle }

// ── User overrides (Ben's ask: customizable UX/UI elements) ──────────────────
// Null = match the room. Persisted; applied on top of whichever room is live.
@Immutable
data class StyleOverride(
    val character: ChromeCharacter? = null,
    val motion: MotionFeel? = null,
    val radiusDp: Int? = null,
    val designators: Boolean? = null,
)

fun RoomStyle.overridden(o: StyleOverride): RoomStyle = copy(
    character = o.character ?: character,
    motion = o.motion ?: motion,
    cornerRadius = o.radiusDp?.dp ?: cornerRadius,
    designators = o.designators ?: designators,
)
