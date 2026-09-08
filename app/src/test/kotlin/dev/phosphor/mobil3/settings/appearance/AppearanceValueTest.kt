package dev.phosphor.mobil3.settings.appearance

import org.junit.Assert.*
import org.junit.Test

class AppearanceValueTest {
    private val base = CuratedAppearances.amoled

    @Test fun allCuratedOpaqueTextRolesAndEssentialBoundariesMeetTheirFloors() {
        CuratedAppearances.all.forEach { value ->
            AppearanceContrast.textBackplateChecks(value).forEach { check ->
                assertTrue("${value.character} ${check.role}: ${check.ratio}", check.passes)
            }
        }
    }

    @Test fun amoledKeepsBlackAndCuratedShapesDoNotInheritLegacyGlassRounding() {
        assertEquals(0, base.colors.plane)
        assertEquals(0, base.colors.surface)
        assertEquals(0, base.colors.surface2)
        assertEquals(0, base.colors.stone)
        assertEquals(AppearanceMotion.CUT, base.motion)
        CuratedAppearances.all.forEach { assertEquals(0, it.radiusDp) }
        assertEquals(.62f, CuratedAppearances.glass.panelAlphaScale)
        assertEquals(AppearanceMotion.EASED, CuratedAppearances.glass.motion)
    }

    @Test fun contrastKnownEndpointsAndAlphaCompositesAreIndependentOfStoredValue() {
        assertEquals(21.0, AppearanceContrast.ratio(0xffffff, 0), 1e-12)
        assertEquals(1.0, AppearanceContrast.ratio(0x123456, 0x123456), 1e-12)
        assertEquals(0xffffff, AppearanceContrast.over(0xffffffff.toInt(), 0))
        assertEquals(0x123456, AppearanceContrast.over(0x00abcdef, 0x123456))
        assertEquals(0x808080, AppearanceContrast.over(0x80ffffff.toInt(), 0))
        assertEquals(AppearanceContrast.ratio(0xff0055, 0x203050),
            AppearanceContrast.ratio(0x203050, 0xff0055), 1e-12)
    }

    @Test fun unsafeCustomContrastIsReportedWithoutMutatingAuthoredColors() {
        val bad = base.copy(colors = base.colors.copy(ink = 0, muted = 0, accent = 0))
        val before = bad.copy()
        assertTrue(AppearanceContrast.textBackplateChecks(bad).any { !it.passes })
        assertEquals(before, bad)
        assertEquals(0, bad.colors.ink)
    }

    @Test fun rgbFieldsAreValidatedButArgbSeparatorsPreserveAllBits() {
        assertThrows(IllegalArgumentException::class.java) { base.colors.copy(plane = -1) }
        assertThrows(IllegalArgumentException::class.java) { base.colors.copy(ink = 0x1000000) }
        assertThrows(IllegalArgumentException::class.java) { base.colors.copy(onAccent = -1) }
        assertEquals(Int.MIN_VALUE, base.colors.copy(line = Int.MIN_VALUE).line)
        assertEquals(-1, base.colors.copy(lineStrong = -1).lineStrong)
        assertThrows(IllegalArgumentException::class.java) { AppearanceContrast.ratio(-1, 0) }
        assertThrows(IllegalArgumentException::class.java) { AppearanceContrast.over(0, -1) }
    }

    @Test fun finiteDensityDurationOpacityAndLegacyRadiusBoundsAreEnforced() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 0f, 3f).forEach { bad ->
            assertThrows(IllegalArgumentException::class.java) { base.copy(durationScale = bad) }
            assertThrows(IllegalArgumentException::class.java) { base.copy(densityScale = bad) }
            assertThrows(IllegalArgumentException::class.java) { base.copy(panelAlphaScale = bad) }
        }
        assertEquals(.25f, base.copy(durationScale = .25f).durationScale)
        assertEquals(2f, base.copy(durationScale = 2f).durationScale)
        assertEquals(.85f, base.copy(densityScale = .85f).densityScale)
        assertEquals(1.25f, base.copy(densityScale = 1.25f).densityScale)
        assertEquals(.2f, base.copy(panelAlphaScale = .2f).panelAlphaScale)
        assertEquals(1f, base.copy(panelAlphaScale = 1f).panelAlphaScale)
        (0..64).forEach { assertEquals(it, base.copy(radiusDp = it).radiusDp) }
        listOf(-1, 65, Int.MAX_VALUE).forEach {
            assertThrows(IllegalArgumentException::class.java) { base.copy(radiusDp = it) }
        }
    }

    @Test fun legacyStyleFieldsRemainIndependentAndCopyDoesNotMutatePriorValue() {
        val legacy = base.copy(character = AppearanceCharacter.GLASS, motion = AppearanceMotion.SPRINGY,
            radiusDp = 12, densityScale = .9f, monoProse = true, designators = true, panelAlphaScale = .62f)
        assertEquals(12, legacy.radiusDp)
        assertEquals(AppearanceMotion.SPRINGY, legacy.motion)
        assertEquals(0, base.radiusDp)
        assertEquals(AppearanceMotion.CUT, base.motion)
        assertEquals(base.colors, legacy.colors)
    }

    @Test fun motionRequiresEveryVisibleBoundaryAndNeverCallsIdleProgressWork() {
        for (bits in 0..31) for (motion in AppearanceMotion.entries) {
            val activity = bits and 1 != 0
            val surface = bits and 2 != 0
            val component = bits and 4 != 0
            val reduced = bits and 8 != 0
            val working = bits and 16 != 0
            val expected = activity && surface && component && !reduced && motion != AppearanceMotion.CUT
            assertEquals(expected, AppearanceMotionPolicy.stateChange(activity, surface, component, reduced, motion))
            assertEquals(expected && working,
                AppearanceMotionPolicy.progress(activity, surface, component, reduced, motion, working))
        }
    }
}
