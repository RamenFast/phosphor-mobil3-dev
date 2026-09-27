package dev.phosphor.mobil3.ui

import dev.phosphor.mobil3.settings.appearance.AppearanceDocumentCodec
import dev.phosphor.mobil3.settings.appearance.AppearancePresentationPolicy
import dev.phosphor.mobil3.settings.appearance.AppearanceValue

/**
 * The floating HUD's colours (ARGB), taken from the active look so the HUD matches the app.
 * Text keeps 4.5:1 and hairlines 3:1 on the look's plane. Anything unreadable or missing
 * falls back to the neutral dark.
 */
internal data class HudLook(val plane: Int, val line: Int, val ink: Int, val ink2: Int, val accent: Int) {
    companion object {
        private const val OPAQUE = 0xff000000.toInt()
        val NEUTRAL = HudLook(
            plane = 0xff0a0a0d.toInt(), line = 0x48ffffff, ink = 0xffeceaf2.toInt(),
            ink2 = 0xffb8b4c4.toInt(), accent = 0xffff6caa.toInt(),
        )

        fun from(value: AppearanceValue?): HudLook {
            val c = value?.colors ?: return NEUTRAL
            val plane = c.plane
            fun text(rgb: Int) = AppearancePresentationPolicy.foreground(rgb or OPAQUE, plane) or OPAQUE
            return HudLook(
                plane = plane or OPAQUE,
                line = AppearancePresentationPolicy.foreground(c.lineStrong, plane, 3.0) or OPAQUE,
                ink = text(c.ink),
                ink2 = text(c.ink2),
                accent = text(c.accent),
            )
        }

        /** The stored appearance document (preference `appearance_state`), or neutral. */
        fun read(raw: Any?): HudLook =
            (raw as? String)?.let { runCatching { from(AppearanceDocumentCodec.decode(it).active) }.getOrNull() } ?: NEUTRAL
    }
}
