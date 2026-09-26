package dev.phosphor.mobil3.ui

import kotlin.test.assertEquals
import org.junit.Test

class AutoFramePreferenceTest {
    @Test fun defaultMalformedAndOutOfRangeLocalValuesAreNeutral() {
        val key = AutoFramePreference.KEY
        for (values in listOf<Map<String, *>>(
            emptyMap<String, Any>(), mapOf(key to "1.1"), mapOf(key to 1),
            mapOf(key to Float.NaN), mapOf(key to Float.NEGATIVE_INFINITY),
            mapOf(key to Math.nextDown(AutoFramePreference.MIN)),
            mapOf(key to Math.nextUp(AutoFramePreference.MAX)),
        )) assertEquals(AutoFramePreference.DEFAULT, AutoFramePreference.read(values), values.toString())
    }

    @Test fun legalStoredEndpointsAndInteriorValuesRoundTripExactly() {
        for (value in listOf(AutoFramePreference.MIN, 0.5f, 1f, AutoFramePreference.MAX)) {
            assertEquals(value, AutoFramePreference.read(mapOf(AutoFramePreference.KEY to value)))
            assertEquals(value, AutoFramePreference.normalize(value))
        }
    }

    @Test fun nativeBoundaryNormalizationClampsFiniteAndResetsNonfinite() {
        assertEquals(AutoFramePreference.MIN, AutoFramePreference.normalize(-1f))
        assertEquals(AutoFramePreference.MAX, AutoFramePreference.normalize(99f))
        assertEquals(AutoFramePreference.DEFAULT, AutoFramePreference.normalize(Float.NaN))
        assertEquals(AutoFramePreference.DEFAULT, AutoFramePreference.normalize(Float.POSITIVE_INFINITY))
    }
}
