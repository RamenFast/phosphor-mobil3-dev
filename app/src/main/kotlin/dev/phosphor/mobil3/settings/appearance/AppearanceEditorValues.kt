package dev.phosphor.mobil3.settings.appearance

import java.util.Locale
import java.math.BigDecimal

/** Exact draft inputs. They are not persistence state or a sampled Palette. */
internal object AppearanceEditorValues {
    val colorNames = listOf("plane", "surface", "surface2", "ink", "ink2", "muted", "line", "lineStrong",
        "accent", "onAccent", "stone", "stoneHi", "stoneLo")
    fun colors(value: AppearanceValue): Map<String, String> = with(value.colors) {
        val values = listOf(plane, surface, surface2, ink, ink2, muted, line, lineStrong, accent, onAccent, stone, stoneHi, stoneLo)
        colorNames.zip(values).associate { (key, color) -> key to String.format(Locale.ROOT,
            if (key == "line" || key == "lineStrong") "%08X" else "%06X", color) }
    }
    fun value(base: AppearanceValue, fields: Map<String, String>, duration: String, density: String,
        radius: String, alpha: String): AppearanceValue {
        fun number(raw: String, min: String, max: String, label: String): Float {
            val decimal = BigDecimal(raw)
            require(decimal >= BigDecimal(min) && decimal <= BigDecimal(max)) { "$label needs $min to $max" }
            return decimal.toFloat()
        }
        val colors = colorNames.map { key ->
            val count = if (key == "line" || key == "lineStrong") 8 else 6
            val raw = fields.getValue(key).removePrefix("#")
            require(raw.length == count && raw.all { it in "0123456789abcdefABCDEF" }) { "$key needs exactly $count hexadecimal digits" }
            raw.toLong(16).toInt()
        }
        return base.copy(colors = AppearanceColors(colors[0], colors[1], colors[2], colors[3], colors[4],
            colors[5], colors[6], colors[7], colors[8], colors[9], colors[10], colors[11], colors[12]),
            durationScale = number(duration, "0.25", "2.0", "Duration scale"),
            densityScale = number(density, "0.85", "1.25", "Density scale"), radiusDp = radius.toInt(),
            panelAlphaScale = number(alpha, "0.2", "1.0", "Panel opacity"))
    }

    /** Explicit correction proposal. The editor must not publish or save it automatically. */
    fun readable(value: AppearanceValue): AppearanceValue = value.copy(
        colors = if (value.dark) CuratedAppearances.dark.colors else CuratedAppearances.light.colors,
    )
}
