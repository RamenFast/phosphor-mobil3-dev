package dev.phosphor.mobil3.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.phosphor.mobil3.settings.appearance.AppearancePresentationPolicy

// Each room defines control character, motion, density, and panel treatment.
// Glass is the only built-in room that rounds controls.

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

/** Current flattened style controls send only the selected field. */
fun RoomStyle.nextCharacter() = StyleOverride(character = ChromeCharacter.entries[(character.ordinal + 1) % ChromeCharacter.entries.size])
fun RoomStyle.nextMotion() = StyleOverride(motion = MotionFeel.entries[(motion.ordinal + 1) % MotionFeel.entries.size])
fun RoomStyle.nextCorners(): StyleOverride {
    val values = listOf(0, 8, 12)
    return StyleOverride(radiusDp = values[(values.indexOf(cornerRadius.value.toInt()) + 1) % values.size])
}
fun RoomStyle.nextLabels() = StyleOverride(designators = !designators)

fun RoomStyle.choices() = StyleOverride(character, motion, cornerRadius.value.toInt(), designators)

fun RoomStyle.space(base: Dp): Dp = AppearancePresentationPolicy.spacing(base.value, densityScale).dp

// FEEL selects its coupled defaults. Explicit controls win. Null follows that base.
@Immutable
data class StyleOverride(
    val character: ChromeCharacter? = null,
    val motion: MotionFeel? = null,
    val radiusDp: Int? = null,
    val designators: Boolean? = null,
)

fun RoomStyle.overridden(o: StyleOverride): RoomStyle {
    val base = when (o.character) {
        ChromeCharacter.Carved -> CarvedStyle
        ChromeCharacter.Engraved -> VoidStyle
        ChromeCharacter.Annotated -> BenchStyle
        ChromeCharacter.Glass -> GlassStyle
        null -> this
    }
    return base.copy(
        motion = o.motion ?: base.motion,
        durationScale = when (o.motion) {
            MotionFeel.Cut -> VoidStyle.durationScale
            null -> base.durationScale
            else -> CarvedStyle.durationScale
        },
        cornerRadius = o.radiusDp?.dp ?: base.cornerRadius,
        designators = o.designators ?: base.designators,
    )
}

fun StyleOverride.nextCharacter(): StyleOverride {
    val values = listOf(null) + ChromeCharacter.entries
    return copy(character = values[(values.indexOf(character) + 1) % values.size])
}

fun StyleOverride.nextMotion(): StyleOverride {
    val values = listOf(null) + MotionFeel.entries
    return copy(motion = values[(values.indexOf(motion) + 1) % values.size])
}

fun StyleOverride.nextCorners(): StyleOverride {
    val values = listOf(null, 0, 8, 12)
    return copy(radiusDp = values[(values.indexOf(radiusDp) + 1) % values.size])
}

fun StyleOverride.nextLabels(): StyleOverride {
    val values = listOf(null, true, false)
    return copy(designators = values[(values.indexOf(designators) + 1) % values.size])
}
