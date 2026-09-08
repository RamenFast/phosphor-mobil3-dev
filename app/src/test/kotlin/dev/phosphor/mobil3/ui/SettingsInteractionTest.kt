package dev.phosphor.mobil3.ui

import org.junit.Assert.*
import org.junit.Test

class SettingsInteractionTest {
    @Test fun mountWithExistingFingerAndMultiplePointersCannotArm() {
        val owner = SettingsDismissOwner()
        assertNull(owner.down(0f, 0, fresh = false, pointers = 1))
        assertNull(owner.down(0f, 0, fresh = true, pointers = 2))
        assertEquals(0f, owner.remainder(null, 500f, direct = true, atTop = true), 0f)
        assertFalse(owner.committed)
    }

    @Test fun nestedScrollNeedsPointerDirectInputAndTopBoundary() {
        val owner = SettingsDismissOwner()
        val ticket = owner.down(0f, 0, true, 1)!!
        assertEquals(0f, owner.remainder(ticket, 500f, false, true), 0f)
        assertEquals(0f, owner.remainder(ticket, 500f, true, false), 0f)
        assertEquals(0f, owner.rawDp, 0f)
        assertEquals(SettingsDismissOwner.Release.RETURN, owner.release(ticket))
    }

    @Test fun sliderOrContentTravelWithoutTopRemainderDoesNotDismiss() {
        val owner = SettingsDismissOwner()
        val ticket = owner.down(0f, 0, true, 1)!!
        owner.observe(ticket, 1000f, 20, 1)
        assertEquals(SettingsDismissOwner.Release.RETURN, owner.release(ticket))
    }

    @Test fun deliberateSlowPullHasExactInclusiveThreshold() {
        for (distance in listOf(191.99f, 192f)) {
            val owner = SettingsDismissOwner()
            val ticket = owner.down(0f, 0, true, 1)!!
            owner.header(ticket, distance)
            owner.observe(ticket, distance, 2000, 1)
            assertEquals(if (distance >= 192f) SettingsDismissOwner.Release.CLOSE else
                SettingsDismissOwner.Release.RETURN, owner.release(ticket))
        }
    }

    @Test fun fastReleaseRequiresBothTravelAndDownwardSpeed() {
        for ((travel, y, expected) in listOf(
            Triple(63.99f, 100f, false), Triple(64f, 92f, true),
            Triple(64f, 91.9f, false), Triple(64f, -100f, false),
        )) {
            val owner = SettingsDismissOwner()
            val ticket = owner.down(0f, 0, true, 1)!!
            owner.remainder(ticket, travel, true, true)
            owner.observe(ticket, y, 100, 1)
            assertEquals(if (expected) SettingsDismissOwner.Release.CLOSE else
                SettingsDismissOwner.Release.RETURN, owner.release(ticket))
        }
    }

    @Test fun velocityBeforeEligibleRemainderCannotBecomeAFlick() {
        val owner = SettingsDismissOwner()
        val ticket = owner.down(0f, 0, true, 1)!!
        owner.observe(ticket, 1000f, 20, 1)
        owner.remainder(ticket, 64f, true, true)
        owner.observe(ticket, 1001f, 120, 1)
        assertEquals(SettingsDismissOwner.Release.RETURN, owner.release(ticket))
    }

    @Test fun reversalPaysBackOnlyOutstandingPullAndResetsVelocityAtZero() {
        val owner = SettingsDismissOwner()
        val ticket = owner.down(0f, 0, true, 1)!!
        owner.header(ticket, 100f)
        assertEquals(-100f, owner.reverse(ticket, -200f, true), 0f)
        assertEquals(0f, owner.rawDp, 0f)
        owner.observe(ticket, 100f, 20, 1)
        owner.header(ticket, 64f)
        assertEquals(SettingsDismissOwner.Release.RETURN, owner.release(ticket))
    }

    @Test fun cancellationAndLateFlingCannotCommitOrAffectReplacement() {
        val owner = SettingsDismissOwner()
        val old = owner.down(0f, 0, true, 1)!!
        owner.header(old, 250f)
        owner.cancel()
        assertEquals(SettingsDismissOwner.Release.NONE, owner.release(old))
        val replacement = owner.down(0f, 1000, true, 1)!!
        assertEquals(0f, owner.remainder(old, 250f, true, true), 0f)
        assertEquals(0f, owner.remainder(replacement, 250f, false, true), 0f)
        assertEquals(SettingsDismissOwner.Release.RETURN, owner.release(replacement))
    }

    @Test fun commitHappensOnceAndKeepsItsExitOffset() {
        val owner = SettingsDismissOwner()
        val ticket = owner.down(0f, 0, true, 1)!!
        owner.header(ticket, 200f)
        val offset = owner.offsetDp
        assertEquals(SettingsDismissOwner.Release.CLOSE, owner.release(ticket))
        assertEquals(SettingsDismissOwner.Release.NONE, owner.release(ticket))
        owner.cancel()
        assertNull(owner.down(0f, 10, true, 1))
        assertEquals(offset, owner.offsetDp, 0f)
    }

    @Test fun multiPointerBackwardsTimeAndNonFiniteMotionCancel() {
        for (mode in 0..3) {
            val owner = SettingsDismissOwner()
            val ticket = owner.down(0f, 100, true, 1)!!
            owner.header(ticket, 250f)
            when (mode) {
                0 -> owner.observe(ticket, 250f, 200, 2)
                1 -> owner.observe(ticket, 250f, 99, 1)
                2 -> owner.observe(ticket, Float.NaN, 200, 1)
                else -> owner.header(ticket, Float.POSITIVE_INFINITY)
            }
            assertEquals(SettingsDismissOwner.Release.NONE, owner.release(ticket))
            assertEquals(0f, owner.offsetDp, 0f)
        }
    }

    @Test fun retirementRejectsFuturePointers() {
        val owner = SettingsDismissOwner()
        val ticket = owner.down(0f, 0, true, 1)!!
        owner.header(ticket, 250f)
        owner.retire()
        assertEquals(SettingsDismissOwner.Release.NONE, owner.release(ticket))
        assertNull(owner.down(0f, 100, true, 1))
    }

    @Test fun resistanceIsMonotonicBoundedAndProgressivelyStronger() {
        val owner = SettingsDismissOwner()
        val ticket = owner.down(0f, 0, true, 1)!!
        var prior = 0f
        var slope = Float.MAX_VALUE
        repeat(1000) {
            owner.header(ticket, 1f)
            val current = owner.offsetDp
            assertTrue(current > prior)
            assertTrue(current < SettingsDismissOwner.RESISTANCE_DP)
            assertTrue(current - prior <= slope + 0.0001f)
            slope = current - prior
            prior = current
        }
    }

    @Test fun expansionIsMultiOpenAndExternalSnapshotsAreIndependent() {
        val owner = SettingsPresentationOwner()
        val snapshot = owner.expanded
        owner.toggle(SettingsSectionId.BEAM, 25)
        assertTrue(owner.isExpanded(SettingsSectionId.SIGNAL))
        assertTrue(owner.isExpanded(SettingsSectionId.BEAM))
        assertFalse(SettingsSectionId.BEAM in snapshot)
        owner.toggle(SettingsSectionId.SIGNAL, 0)
        assertFalse(owner.isExpanded(SettingsSectionId.SIGNAL))
        assertTrue(owner.isExpanded(SettingsSectionId.BEAM))
    }

    @Test fun exactAnchorCorrectionIsClampedAndSingleUse() {
        val owner = SettingsPresentationOwner()
        val anchor = owner.toggle(SettingsSectionId.BEAM, 40)
        assertEquals(120, owner.anchorScroll(anchor, 60, 100, 200))
        assertNull(owner.anchorScroll(anchor, 60, 100, 200))
        val edge = owner.toggle(SettingsSectionId.ABOUT, 0)
        assertEquals(200, owner.anchorScroll(edge, Int.MAX_VALUE, Int.MAX_VALUE, 200))
        val top = owner.toggle(SettingsSectionId.ABOUT, Int.MAX_VALUE)
        assertEquals(0, owner.anchorScroll(top, Int.MIN_VALUE, 10, 200))
    }

    @Test fun newGestureToggleOrReturnInvalidatesPendingAnchorNotPresentation() {
        val owner = SettingsPresentationOwner()
        owner.rememberScroll(123)
        val old = owner.toggle(SettingsSectionId.BEAM, 0)
        val current = owner.toggle(SettingsSectionId.DISPLAY, 10)
        assertNull(owner.anchorScroll(old, 20, 123, 500))
        owner.cancelAnchor()
        assertNull(owner.anchorScroll(current, 20, 123, 500))
        assertEquals(123, owner.scrollPx)
        assertTrue(owner.isExpanded(SettingsSectionId.BEAM))
        assertTrue(owner.isExpanded(SettingsSectionId.DISPLAY))
    }
}
