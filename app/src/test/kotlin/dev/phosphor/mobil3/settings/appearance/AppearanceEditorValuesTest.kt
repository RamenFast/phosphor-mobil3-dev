package dev.phosphor.mobil3.settings.appearance

import org.junit.Assert.*
import org.junit.Test

class AppearanceEditorValuesTest {
    private val base = CuratedAppearances.dark
    private fun resolve(fields: Map<String, String> = AppearanceEditorValues.colors(base),
        duration: String = "1.0", density: String = "1.0", radius: String = "0", alpha: String = "1.0") =
        AppearanceEditorValues.value(base, fields, duration, density, radius, alpha)

    @Test fun allThirteenColorInputsRoundTripExactCuratedAndAlphaValues() {
        CuratedAppearances.all.forEach { value ->
            val fields = AppearanceEditorValues.colors(value)
            assertEquals(13, fields.size)
            assertEquals(value, AppearanceEditorValues.value(value, fields, value.durationScale.toString(),
                value.densityScale.toString(), value.radiusDp.toString(), value.panelAlphaScale.toString()))
        }
    }
    @Test fun rgbAndArgbEndpointsRemainExactIncludingNegativeSignedArgb() {
        val fields = AppearanceEditorValues.colors(base) + mapOf("plane" to "000000", "ink" to "FFFFFF",
            "line" to "00000000", "lineStrong" to "FFFFFFFF")
        val value = resolve(fields)
        assertEquals(0, value.colors.plane)
        assertEquals(0xffffff, value.colors.ink)
        assertEquals(0, value.colors.line)
        assertEquals(-1, value.colors.lineStrong)
    }
    @Test fun hashAndLowercaseHexAreAcceptedWithoutChangingAuthoredColors() {
        val value = resolve(AppearanceEditorValues.colors(base) + mapOf("plane" to "#abcdef", "line" to "80abcdef"))
        assertEquals(0xabcdef, value.colors.plane)
        assertEquals(0x80abcdef.toInt(), value.colors.line)
    }
    @Test fun malformedRgbAndArgbFailRatherThanClampOrPartiallyApply() {
        listOf("", "FFFFF", "FFFFFFF", "-00001", "GGGGGG", "FFFFFF ", "0xFFFF").forEach { text ->
            assertThrows(IllegalArgumentException::class.java) {
                resolve(AppearanceEditorValues.colors(base) + ("plane" to text))
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            resolve(AppearanceEditorValues.colors(base) + ("line" to "FFFFFF"))
        }
    }
    @Test fun exactStyleEndpointsAreReachable() {
        assertEquals(.25f, resolve(duration = "0.25").durationScale)
        assertEquals(2f, resolve(duration = "2").durationScale)
        assertEquals(.85f, resolve(density = "0.85").densityScale)
        assertEquals(1.25f, resolve(density = "1.25").densityScale)
        assertEquals(64, resolve(radius = "64").radiusDp)
        assertEquals(.2f, resolve(alpha = "0.2").panelAlphaScale)
    }
    @Test fun invalidOrNonfiniteStyleInputsNeverNormalize() {
        listOf("NaN", "Infinity", "-Infinity", "0.24", "2.01", "0.24999999999", "2.00000000001", "bad").forEach { raw ->
            assertThrows(IllegalArgumentException::class.java) { resolve(duration = raw) }
        }
        assertThrows(IllegalArgumentException::class.java) { resolve(density = "0.84") }
        assertThrows(IllegalArgumentException::class.java) { resolve(alpha = "0.19") }
        assertThrows(IllegalArgumentException::class.java) { resolve(radius = "65") }
        assertThrows(IllegalArgumentException::class.java) { resolve(radius = "0.5") }
    }
    @Test fun lowContrastRemainsExactUntilExplicitReadableProposal() {
        val authored = resolve(AppearanceEditorValues.colors(base) + mapOf("ink" to "202026", "lineStrong" to "00202026"))
        assertEquals(0x202026, authored.colors.ink)
        assertTrue(AppearanceContrast.textBackplateChecks(authored).any { !it.passes })
        val corrected = AppearanceEditorValues.readable(authored)
        assertTrue(AppearanceContrast.textBackplateChecks(corrected).all { it.passes })
        assertEquals(authored.copy(colors = corrected.colors), corrected)
        assertEquals(0x202026, authored.colors.ink)
    }
    @Test fun readableProposalPreservesEveryStyleFieldAndHasBothLightAndDarkCases() {
        listOf(false, true).forEach { dark ->
            val authored = base.copy(dark = dark, accentFollowsBeam = true, character = AppearanceCharacter.GLASS,
                motion = AppearanceMotion.SPRINGY, durationScale = 2f, densityScale = 1.25f, radiusDp = 64,
                monoProse = true, designators = true, panelAlphaScale = .2f)
            val corrected = AppearanceEditorValues.readable(authored)
            assertEquals(authored.copy(colors = corrected.colors), corrected)
            assertTrue(AppearanceContrast.textBackplateChecks(corrected).all { it.passes })
        }
    }
}
