package dev.phosphor.mobil3.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import dev.phosphor.mobil3.settings.appearance.AppearanceCharacter
import dev.phosphor.mobil3.settings.appearance.AppearanceColors
import dev.phosphor.mobil3.settings.appearance.AppearanceMotion
import dev.phosphor.mobil3.settings.appearance.AppearanceValue

/** Exact original fields, including absence versus explicit sentinel values. */
internal data class LegacyAppearanceInput(
    val room: String? = null,
    val character: Int? = null,
    val motion: Int? = null,
    val radius: Int? = null,
    val designators: Int? = null,
)

internal data class LegacyAppearanceResolution(
    val original: LegacyAppearanceInput,
    val resolvedId: String,
    val label: String,
    val value: AppearanceValue,
) {
    val usedUnknownRoomFallback: Boolean get() = original.room != null && original.room != resolvedId
}

/** Authored appearance boundary. No preferences, live signal or native setters are read here. */
internal object AppearancePalette {
    fun legacy(input: LegacyAppearanceInput): LegacyAppearanceResolution {
        val palette = paletteById(input.room ?: "amoled")
        val overrides = StyleOverride(
            character = input.character?.takeIf { it >= 0 }?.let { ChromeCharacter.entries.getOrNull(it) },
            motion = input.motion?.takeIf { it >= 0 }?.let { MotionFeel.entries.getOrNull(it) },
            radiusDp = input.radius?.takeIf { it >= 0 },
            designators = when (input.designators) { 1 -> true; 0 -> false; else -> null },
        )
        return LegacyAppearanceResolution(input, palette.id, palette.label, authored(palette, palette.style.overridden(overrides)))
    }

    /** Kept private so a transient withBeam palette cannot become a saved authored value. */
    private fun authored(p: Palette, s: RoomStyle): AppearanceValue {
        fun rgb(c: Color) = c.toArgb() and 0xffffff
        return AppearanceValue(
            colors = AppearanceColors(rgb(p.plane), rgb(p.surface), rgb(p.surface2),
                rgb(p.ink), rgb(p.ink2), rgb(p.muted), p.line.toArgb(), p.lineStrong.toArgb(),
                rgb(p.accent), rgb(p.onAccent), rgb(p.stone), rgb(p.stoneHi), rgb(p.stoneLo)),
            dark = p.dark, accentFollowsBeam = p.accentFollowsBeam,
            character = when (s.character) {
                ChromeCharacter.Carved -> AppearanceCharacter.CARVED
                ChromeCharacter.Engraved -> AppearanceCharacter.ENGRAVED
                ChromeCharacter.Annotated -> AppearanceCharacter.ANNOTATED
                ChromeCharacter.Glass -> AppearanceCharacter.GLASS
            },
            motion = when (s.motion) {
                MotionFeel.Eased -> AppearanceMotion.EASED
                MotionFeel.Cut -> AppearanceMotion.CUT
                MotionFeel.Detented -> AppearanceMotion.DETENTED
                MotionFeel.Springy -> AppearanceMotion.SPRINGY
            },
            durationScale = s.durationScale, densityScale = s.densityScale,
            radiusDp = s.cornerRadius.value.toInt(), monoProse = s.monoProse,
            designators = s.designators, panelAlphaScale = s.panelAlphaScale,
        )
    }

    fun palette(value: AppearanceValue, id: String, label: String): Palette = with(value.colors) {
        fun rgb(v: Int) = Color(v or 0xff000000.toInt())
        Palette(id, label, value.dark, rgb(plane), rgb(surface), rgb(surface2), rgb(ink), rgb(ink2), rgb(muted),
            Color(line), Color(lineStrong), rgb(accent), rgb(onAccent), rgb(stone), rgb(stoneHi), rgb(stoneLo), value.accentFollowsBeam)
    }

    fun style(value: AppearanceValue): RoomStyle = RoomStyle(
        character = when (value.character) {
            AppearanceCharacter.CARVED -> ChromeCharacter.Carved
            AppearanceCharacter.ENGRAVED -> ChromeCharacter.Engraved
            AppearanceCharacter.ANNOTATED -> ChromeCharacter.Annotated
            AppearanceCharacter.GLASS -> ChromeCharacter.Glass
        },
        motion = when (value.motion) {
            AppearanceMotion.EASED -> MotionFeel.Eased
            AppearanceMotion.CUT -> MotionFeel.Cut
            AppearanceMotion.DETENTED -> MotionFeel.Detented
            AppearanceMotion.SPRINGY -> MotionFeel.Springy
        },
        durationScale = value.durationScale, densityScale = value.densityScale,
        cornerRadius = value.radiusDp.dp, monoProse = value.monoProse,
        designators = value.designators, panelAlphaScale = value.panelAlphaScale,
    )
}
