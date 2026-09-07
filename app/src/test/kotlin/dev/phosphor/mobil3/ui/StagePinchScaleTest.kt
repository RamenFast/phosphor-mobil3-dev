package dev.phosphor.mobil3.ui

import org.junit.Test
import kotlin.test.*

class StagePinchScaleTest {
    @Test fun slowOutwardMovementAccumulatesInsteadOfDisappearingEachFrame() {
        val pinch = StagePinchScale()
        assertNull(pinch.sample(400f))
        assertNull(pinch.sample(400.1f))
        assertNull(pinch.sample(400.2f))
        assertNull(pinch.sample(400.3f))
        assertEquals(401f / 400f, pinch.sample(401f))
        assertNull(pinch.sample(401.1f))
    }

    @Test fun slowInwardMovementAlsoAccumulates() {
        val pinch = StagePinchScale()
        pinch.reset(400f)
        assertNull(pinch.sample(399.9f))
        assertNull(pinch.sample(399.8f))
        assertEquals(399f / 400f, pinch.sample(399f))
    }

    @Test fun cumulativeScaleIsIndependentOfSamplingRate() {
        val pinch = StagePinchScale()
        pinch.reset(400f)
        var scale = 1f
        repeat(240) { n -> pinch.sample(400f + (n + 1) * 0.2f)?.let { scale *= it } }
        // At most the retained jitter threshold may remain unapplied.
        assertEquals(448f / 400f, scale, 0.0012f)
    }

    @Test fun guardAndRebaseDiscardBlockedTravelBeforeAnyGainChange() {
        val pinch = StagePinchScale()
        pinch.reset(400f)
        pinch.reset(500f)
        pinch.reset(600f)
        assertNull(pinch.sample(600f))
        assertNull(pinch.sample(600.1f))
        assertEquals(601f / 600f, pinch.sample(601f))
    }

    @Test fun stationaryJitterDoesNotDriftAndNewSequenceDoesNotReuseDistance() {
        val pinch = StagePinchScale()
        pinch.reset(400f)
        repeat(100) {
            assertNull(pinch.sample(400.1f))
            assertNull(pinch.sample(399.9f))
        }
        pinch.reset()
        assertNull(pinch.sample(100f))
        assertEquals(1.1f, pinch.sample(110f))
    }

    @Test fun invalidDistanceResetsWithoutApplyingAnInvalidScale() {
        val pinch = StagePinchScale()
        for (bad in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            pinch.reset(400f)
            assertNull(pinch.sample(bad))
            assertNull(pinch.sample(100f))
            assertEquals(2f, pinch.sample(200f))
        }
    }
}
