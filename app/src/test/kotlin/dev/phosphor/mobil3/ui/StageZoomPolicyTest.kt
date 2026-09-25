package dev.phosphor.mobil3.ui

import kotlin.math.exp
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class StageZoomPolicyTest {
    @Test fun autoPinchChangesOnlyPreferredFramingWithinItsRange() {
        val outward = StageZoomPolicy.adjust(3f, 0.5f, true, 1.5f)
        assertEquals(StageZoomOwner.AUTO_FRAME, outward.owner)
        assertEquals(0.75f, outward.value)
        val capped = StageZoomPolicy.adjust(3f, AutoFramePreference.MAX, true, 2f)
        assertEquals(AutoFramePreference.MAX, capped.value)
    }

    @Test fun autoOneFingerUpMeansCloserAndDownMeansFarther() {
        val up = StageZoomPolicy.adjust(2f, 0.8f, true, exp(-(-40f) * 0.0042f))
        val down = StageZoomPolicy.adjust(2f, 0.8f, true, exp(-(40f) * 0.0042f))
        assertTrue(up.value > 0.8f)
        assertTrue(down.value < 0.8f)
        assertEquals(StageZoomOwner.AUTO_FRAME, up.owner)
    }

    @Test fun manualModeUsesTheSharedGainScale() {
        assertEquals(12f, StageZoomPolicy.adjust(6f, 0.4f, false, 2f).value)
        assertEquals(GainScale.MAX, StageZoomPolicy.adjust(40f, 0.4f, false, 2f).value)
        assertEquals(0.1f, StageZoomPolicy.adjust(0.2f, 1.1f, false, 0.1f).value)
        assertEquals(StageZoomOwner.MANUAL_GAIN, StageZoomPolicy.adjust(2f, 1f, false, 1.1f).owner)
    }

    @Test fun invalidFactorsCannotChangeEitherOwner() {
        for (factor in listOf(Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, 0f, -1f)) {
            assertEquals(0.75f, StageZoomPolicy.adjust(2f, 0.75f, true, factor).value)
            assertEquals(2f, StageZoomPolicy.adjust(2f, 0.75f, false, factor).value)
        }
    }

    @Test fun neutralCrossingIsReportedForEitherMeaning() {
        assertTrue(StageZoomPolicy.adjust(0.8f, 0.7f, false, 2f).crossedNeutral)
        assertTrue(StageZoomPolicy.adjust(2f, 0.8f, true, 1.3f).crossedNeutral)
    }

    @Test fun sizeMultiplierIsShortAndReadable() {
        assertEquals("×0.25", GainWords.multiplier(0.25f))
        assertEquals("×0.5", GainWords.multiplier(0.5f))
        assertEquals("×1", GainWords.multiplier(1f))
        assertEquals("×1.5", GainWords.multiplier(1.5f))
        assertEquals("×12", GainWords.multiplier(12.4f))
        assertEquals(GainScale.MIN, GainWords.fromSlider(0f), 0.0001f)
        assertEquals(GainScale.MAX, GainWords.fromSlider(1f), 0.01f)
        assertEquals(0.5f, GainWords.toSlider(GainWords.fromSlider(0.5f)), 0.001f)
    }
}
