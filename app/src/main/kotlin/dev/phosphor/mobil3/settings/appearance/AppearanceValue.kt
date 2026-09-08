package dev.phosphor.mobil3.settings.appearance

import kotlin.math.pow

/** Authored sRGB values. Transient beam tint is deliberately absent. */
data class AppearanceColors(
    val plane: Int, val surface: Int, val surface2: Int,
    val ink: Int, val ink2: Int, val muted: Int,
    val line: Int, val lineStrong: Int,
    val accent: Int, val onAccent: Int,
    val stone: Int, val stoneHi: Int, val stoneLo: Int,
) {
    init {
        require(listOf(plane, surface, surface2, ink, ink2, muted, accent, onAccent,
            stone, stoneHi, stoneLo).all { it in 0..0xffffff }) { "Appearance RGB must be 0..16777215" }
    }
}

enum class AppearanceCharacter { CARVED, ENGRAVED, ANNOTATED, GLASS }
enum class AppearanceMotion { EASED, CUT, DETENTED, SPRINGY }

data class AppearanceValue(
    val colors: AppearanceColors,
    val dark: Boolean,
    val accentFollowsBeam: Boolean,
    val character: AppearanceCharacter,
    val motion: AppearanceMotion,
    val durationScale: Float = 1f,
    val densityScale: Float = 1f,
    val radiusDp: Int = 0,
    val monoProse: Boolean = false,
    val designators: Boolean = false,
    val panelAlphaScale: Float = 1f,
) {
    init {
        require(durationScale.isFinite() && durationScale in .25f..2f) { "Appearance duration must be 0.25..2" }
        require(densityScale.isFinite() && densityScale in .85f..1.25f) { "Appearance density must be 0.85..1.25" }
        require(radiusDp in 0..64) { "Appearance radius must be 0..64 dp" }
        require(panelAlphaScale.isFinite() && panelAlphaScale in .2f..1f) { "Appearance opacity must be 0.2..1" }
    }
}

/** New curated entries do not replace the separate, lossless legacy migration. */
object CuratedAppearances {
    val light = AppearanceValue(
        AppearanceColors(0xf2ecdf, 0xfaf6ec, 0xece5d6, 0x2e2820, 0x5c5244, 0x615847,
            0x402e2820, 0xff7a6e5e.toInt(), 0x8e2d23, 0xfffaf4, 0xe8e0d0, 0xfffdf6, 0xc0b5a0),
        dark = false, accentFollowsBeam = false, character = AppearanceCharacter.ANNOTATED,
        motion = AppearanceMotion.EASED, monoProse = true, designators = true,
    )
    val dark = AppearanceValue(
        AppearanceColors(0x141418, 0x202026, 0x292931, 0xf2eff6, 0xc3bdcf, 0xaaa1b5,
            0x405d566a, 0xff93899f.toInt(), 0xd99ac9, 0x1a101c, 0x2b2631, 0x4a4253, 0x100d15),
        dark = true, accentFollowsBeam = false, character = AppearanceCharacter.CARVED,
        motion = AppearanceMotion.EASED,
    )
    val glass = AppearanceValue(
        AppearanceColors(0x05070c, 0x10131c, 0x181c28, 0xf4f6ff, 0xccd4e8, 0xb2bed4,
            0x606d829f, 0xffa3b2d0.toInt(), 0xaacdff, 0x061018, 0x151a26, 0x3a4a68, 0x090b12),
        dark = true, accentFollowsBeam = true, character = AppearanceCharacter.GLASS,
        motion = AppearanceMotion.EASED, panelAlphaScale = .62f,
    )
    val amoled = AppearanceValue(
        AppearanceColors(0, 0, 0, 0xffffff, 0xc4c4c4, 0xaaaaaa,
            0x2effffff, 0xff999999.toInt(), 0xff6caa, 0x100008, 0, 0x333333, 0),
        dark = true, accentFollowsBeam = false, character = AppearanceCharacter.ENGRAVED,
        motion = AppearanceMotion.CUT, durationScale = .5f,
    )
    val all: List<AppearanceValue> get() = listOf(light, dark, glass, amoled)
}

/** Contrast describes presentation, never mutates an authored color. */
object AppearanceContrast {
    data class Check(val role: String, val ratio: Double, val minimum: Double) {
        val passes: Boolean get() = ratio >= minimum
    }

    fun ratio(foreground: Int, background: Int): Double {
        require(foreground in 0..0xffffff && background in 0..0xffffff)
        val a = luminance(foreground)
        val b = luminance(background)
        return (maxOf(a, b) + .05) / (minOf(a, b) + .05)
    }

    fun over(argb: Int, opaqueBackground: Int): Int {
        require(opaqueBackground in 0..0xffffff)
        val alpha = (argb ushr 24) / 255.0
        fun channel(shift: Int): Int {
            val front = (argb ushr shift) and 255
            val back = (opaqueBackground ushr shift) and 255
            return (front * alpha + back * (1 - alpha) + .5).toInt().coerceIn(0, 255)
        }
        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    /** Opaque text-backplate checks. Dynamic Glass/beam composites need their actual background. */
    fun textBackplateChecks(value: AppearanceValue): List<Check> = with(value.colors) {
        listOf(
            Check("text / plane", ratio(ink, plane), 4.5),
            Check("text / surface", ratio(ink, surface), 4.5),
            Check("text / secondary surface", ratio(ink, surface2), 4.5),
            Check("secondary text / surface", ratio(ink2, surface), 4.5),
            Check("context / surface", ratio(muted, surface), 4.5),
            Check("accent / surface", ratio(accent, surface), 4.5),
            Check("text / accent", ratio(onAccent, accent), 4.5),
            Check("essential boundary / surface", ratio(over(lineStrong, surface), surface), 3.0),
        )
    }

    private fun luminance(rgb: Int): Double {
        fun linear(shift: Int): Double {
            val v = ((rgb ushr shift) and 255) / 255.0
            return if (v <= .04045) v / 12.92 else ((v + .055) / 1.055).pow(2.4)
        }
        return .2126 * linear(16) + .7152 * linear(8) + .0722 * linear(0)
    }
}

object AppearanceMotionPolicy {
    fun stateChange(activityVisible: Boolean, surfaceVisible: Boolean, componentVisible: Boolean,
        reduced: Boolean, motion: AppearanceMotion): Boolean =
        activityVisible && surfaceVisible && componentVisible && !reduced && motion != AppearanceMotion.CUT

    fun progress(activityVisible: Boolean, surfaceVisible: Boolean, componentVisible: Boolean,
        reduced: Boolean, motion: AppearanceMotion, workInProgress: Boolean): Boolean =
        workInProgress && stateChange(activityVisible, surfaceVisible, componentVisible, reduced, motion)
}
