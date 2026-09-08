package dev.phosphor.mobil3.ui

import org.junit.Assert.*
import org.junit.Test

class SettingsGestureAdapterTest {
    private fun down(adapter: SettingsGestureAdapter, time: Long = 0) {
        adapter.initial(1, 0f, time, true, false, 1)
        assertEquals(SettingsDismissOwner.Release.NONE, adapter.final())
    }
    private fun move(adapter: SettingsGestureAdapter, y: Float, time: Long, action: () -> Unit = {}) {
        adapter.initial(1, y, time, true, true, 1)
        action()
        assertEquals(SettingsDismissOwner.Release.NONE, adapter.final())
    }
    private fun up(adapter: SettingsGestureAdapter, y: Float, time: Long): SettingsDismissOwner.Release {
        adapter.initial(1, y, time, false, true, 1)
        return adapter.final()
    }

    @Test fun openingFingerNeverArmsIncludingItsReleaseAndTail() {
        val adapter = SettingsGestureAdapter()
        move(adapter, 250f, 100) { assertEquals(0f, adapter.header(250f), 0f) }
        assertEquals(SettingsDismissOwner.Release.NONE, up(adapter, 250f, 120))
        assertEquals(SettingsDismissOwner.Release.NONE, adapter.postFling())
        assertEquals(0f, adapter.rawDp, 0f)
        down(adapter, 200)
        move(adapter, 192f, 2000) { adapter.header(192f) }
        assertEquals(SettingsDismissOwner.Release.CLOSE, up(adapter, 192f, 2100))
    }

    @Test fun childConsumedSliderMoveCannotBecomeEligibleTravel() {
        val adapter = SettingsGestureAdapter()
        down(adapter)
        // A child consumes the event. It does not call either eligible travel seam.
        move(adapter, 600f, 40)
        assertEquals(SettingsDismissOwner.Release.RETURN, up(adapter, 600f, 50))
        assertEquals(0f, adapter.offsetDp, 0f)
    }

    @Test fun childScrollConsumptionLeavesOnlyTheDirectTopRemainder() {
        val adapter = SettingsGestureAdapter()
        down(adapter)
        move(adapter, 300f, 1000) {
            // Child consumed 260dp getting to top. Only the remaining40dp belongs to Settings.
            assertEquals(40f, adapter.remainder(40f, direct = true, atTop = true, childConsumedDp = 260f), 0f)
            assertEquals(0f, adapter.remainder(260f, direct = true, atTop = true), 0f)
        }
        assertEquals(40f, adapter.rawDp, 0f)
        assertEquals(SettingsDismissOwner.Release.RETURN, up(adapter, 300f, 1200))
    }

    @Test fun sideEffectsAndNonTopRemaindersCannotSpendThePointerBudget() {
        val adapter = SettingsGestureAdapter()
        down(adapter)
        move(adapter, 250f, 1000) {
            assertEquals(0f, adapter.remainder(250f, false, true), 0f)
            assertEquals(0f, adapter.remainder(250f, true, false), 0f)
        }
        assertEquals(SettingsDismissOwner.Release.RETURN, up(adapter, 250f, 1100))
    }

    @Test fun stationaryFingerDoesNotAuthorizeSyntheticDirectRemainder() {
        val adapter = SettingsGestureAdapter()
        down(adapter)
        assertEquals(0f, adapter.remainder(500f, true, true), 0f)
        move(adapter, 0f, 100) { assertEquals(0f, adapter.remainder(500f, true, true), 0f) }
        assertEquals(SettingsDismissOwner.Release.RETURN, up(adapter, 0f, 200))
    }

    @Test fun duplicateHeaderAndNestedCallbacksCannotDoubleCountMotion() {
        val adapter = SettingsGestureAdapter()
        down(adapter)
        move(adapter, 100f, 1000) {
            assertEquals(80f, adapter.header(80f), 0f)
            assertEquals(20f, adapter.remainder(100f, true, true), 0f)
            assertEquals(0f, adapter.header(100f), 0f)
        }
        assertEquals(100f, adapter.rawDp, 0f)
        assertEquals(SettingsDismissOwner.Release.RETURN, up(adapter, 100f, 1200))
    }

    @Test fun delayedDirectNestedDeliveryBeforeNextPointerEventIsStillBounded() {
        val adapter = SettingsGestureAdapter()
        down(adapter)
        move(adapter, 192f, 1000)
        assertEquals(192f, adapter.remainder(300f, true, true), 0f)
        assertEquals(0f, adapter.remainder(300f, true, true), 0f)
        assertEquals(SettingsDismissOwner.Release.CLOSE, up(adapter, 192f, 1100))
    }

    @Test fun slowThresholdIsInclusiveThroughActualAdapter() {
        for (distance in listOf(191.99f, 192f)) {
            val adapter = SettingsGestureAdapter()
            down(adapter)
            move(adapter, distance, 1000) { adapter.remainder(distance, true, true) }
            assertEquals(if (distance >= 192f) SettingsDismissOwner.Release.CLOSE else
                SettingsDismissOwner.Release.RETURN, up(adapter, distance, 1200))
        }
    }

    @Test fun delayedNestedFlickStillUsesItsEligiblePhysicalSample() {
        val adapter = SettingsGestureAdapter()
        down(adapter)
        move(adapter, 64f, 50)
        assertEquals(64f, adapter.remainder(64f, true, true), 0f)
        assertEquals(SettingsDismissOwner.Release.CLOSE, up(adapter, 64f, 50))
    }

    @Test fun headerUsesMeasuredVelocityAndMinimumDistance() {
        for ((distance, end, expected) in listOf(
            Triple(63.99f, 50L, false), Triple(64f, 50L, true), Triple(64f, 200L, false),
        )) {
            val adapter = SettingsGestureAdapter()
            down(adapter)
            move(adapter, distance, end) { adapter.header(distance) }
            assertEquals(if (expected) SettingsDismissOwner.Release.CLOSE else
                SettingsDismissOwner.Release.RETURN, up(adapter, distance, end))
        }
    }

    @Test fun reversalConsumesOnlyOutstandingRawTravelAndLeavesContentRemainder() {
        val adapter = SettingsGestureAdapter()
        down(adapter)
        move(adapter, 100f, 1000) { adapter.remainder(100f, true, true) }
        move(adapter, -100f, 1500) {
            assertEquals(-100f, adapter.reverse(-200f, true), 0f)
            assertEquals(0f, adapter.rawDp, 0f)
        }
        assertEquals(SettingsDismissOwner.Release.RETURN, up(adapter, -100f, 1600))
    }

    @Test fun cancelAfterQualifiedTravelCannotCloseAndRequiresFreshDown() {
        val adapter = SettingsGestureAdapter()
        down(adapter)
        move(adapter, 250f, 1000) { adapter.header(250f) }
        adapter.cancel()
        move(adapter, 500f, 1100) { assertEquals(0f, adapter.header(250f), 0f) }
        assertEquals(SettingsDismissOwner.Release.NONE, up(adapter, 500f, 1200))
        down(adapter, 1300)
        assertEquals(SettingsDismissOwner.Release.RETURN, up(adapter, 0f, 1400))
    }

    @Test fun geometryRestartAndSourceLossUseCancellationNotRelease() {
        val adapter = SettingsGestureAdapter()
        down(adapter)
        move(adapter, 192f, 1000) { adapter.remainder(192f, true, true) }
        adapter.cancel() // The actual pointerInput finally path for geometry/source key changes.
        assertEquals(SettingsDismissOwner.Release.NONE, adapter.final())
        assertEquals(SettingsDismissOwner.Release.NONE, adapter.postFling())
        assertFalse(adapter.committed)
    }

    @Test fun multiplePointersLostPointerInvalidTimeAndNonFinitePositionCancel() {
        for (case in 0..4) {
            val adapter = SettingsGestureAdapter()
            down(adapter)
            move(adapter, 250f, 1000) { adapter.header(250f) }
            when (case) {
                0 -> adapter.initial(1, 250f, 1100, true, true, 2)
                1 -> adapter.initial(2, 250f, 1100, true, true, 1)
                2 -> adapter.initial(1, 250f, 999, true, true, 1)
                3 -> adapter.initial(1, Float.NaN, 1100, true, true, 1)
                4 -> adapter.initial(1, 250f, 1100, true, true, 1, directPointer = false)
            }
            assertEquals(SettingsDismissOwner.Release.NONE, adapter.final())
            assertEquals(SettingsDismissOwner.Release.NONE, up(adapter, 250f, 1200))
            assertEquals(0f, adapter.offsetDp, 0f)
        }
    }

    @Test fun releaseAndFlingOrderingCannotProvideASecondDecision() {
        for (flingFirst in listOf(false, true)) {
            val adapter = SettingsGestureAdapter()
            down(adapter)
            move(adapter, 192f, 1000) { adapter.header(192f) }
            if (flingFirst) assertEquals(SettingsDismissOwner.Release.NONE, adapter.postFling())
            assertEquals(SettingsDismissOwner.Release.CLOSE, up(adapter, 192f, 1100))
            val exit = adapter.offsetDp
            assertEquals(SettingsDismissOwner.Release.NONE, adapter.final())
            assertEquals(SettingsDismissOwner.Release.NONE, adapter.postFling())
            adapter.cancel()
            assertEquals(exit, adapter.offsetDp, 0f)
        }
    }

    @Test fun retirementCannotAffectAReplacementOpening() {
        val old = SettingsGestureAdapter()
        down(old)
        move(old, 250f, 1000) { old.header(250f) }
        old.retire()
        down(old, 2000)
        move(old, 250f, 3000) { assertEquals(0f, old.header(250f), 0f) }
        assertEquals(SettingsDismissOwner.Release.NONE, up(old, 250f, 3100))
        val replacement = SettingsGestureAdapter()
        down(replacement)
        assertEquals(SettingsDismissOwner.Release.RETURN, up(replacement, 0f, 100))
    }

    @Test fun anchorRequiresMatchingPostLayoutAndAppliesOnlyOnce() {
        val adapter = SettingsAnchorAdapter(SettingsPresentationOwner())
        val request = adapter.toggle(SettingsSectionId.BEAM, 100)
        assertNull(adapter.correction(request, 120, 200, 600))
        assertTrue(adapter.laidOut(request))
        assertEquals(20, adapter.correction(request, 120, 200, 600))
        assertNull(adapter.correction(request, 120, 200, 600))
    }

    @Test fun toggleReplacementCancelsOldLayoutAndQueuedCorrection() {
        val adapter = SettingsAnchorAdapter(SettingsPresentationOwner())
        val old = adapter.toggle(SettingsSectionId.BEAM, 100)
        assertTrue(adapter.laidOut(old))
        val replacement = adapter.toggle(SettingsSectionId.DISPLAY, 50)
        assertFalse(adapter.laidOut(old))
        assertNull(adapter.correction(old, 200, 100, 600))
        assertTrue(adapter.laidOut(replacement))
        assertEquals(10, adapter.correction(replacement, 60, 100, 600))
        assertTrue(adapter.owner.isExpanded(SettingsSectionId.BEAM))
        assertTrue(adapter.owner.isExpanded(SettingsSectionId.DISPLAY))
    }

    @Test fun inputGeometryAndRetirementCancelPendingAnchorNotExpandedStateOrScroll() {
        repeat(3) {
            val presentation = SettingsPresentationOwner()
            presentation.rememberScroll(200)
            val adapter = SettingsAnchorAdapter(presentation)
            val old = adapter.toggle(SettingsSectionId.ABOUT, 80)
            adapter.laidOut(old)
            adapter.cancel() // Same seam for new input, geometry and retirement.
            assertNull(adapter.correction(old, 160, 200, 600))
            assertTrue(presentation.isExpanded(SettingsSectionId.ABOUT))
            assertEquals(200, presentation.scrollPx)
        }
    }

    @Test fun motionBeforeTopEligibilityAndUpwardReleaseDoNotCreateAFlick() {
        val adapter = SettingsGestureAdapter()
        down(adapter)
        move(adapter, 1000f, 20) {
            adapter.remainder(0f, true, false, childConsumedDp = 1000f)
        }
        move(adapter, 1064f, 1020) { adapter.remainder(64f, true, true) }
        assertEquals(SettingsDismissOwner.Release.RETURN, up(adapter, 1064f, 1100))

        val reverse = SettingsGestureAdapter()
        down(reverse)
        move(reverse, 80f, 20) { reverse.header(80f) }
        move(reverse, 64f, 120) { reverse.header(-16f) }
        assertEquals(SettingsDismissOwner.Release.RETURN, up(reverse, 64f, 130))
    }

    @Test fun shortUpwardReleaseCannotBorrowTheEarlierDownwardVelocity() {
        for (releaseTime in listOf(50L, 51L, 60L)) {
            val adapter = SettingsGestureAdapter()
            down(adapter)
            move(adapter, 80f, 40) { adapter.header(80f) }
            move(adapter, 70f, 50) { adapter.header(-10f) }
            assertEquals(SettingsDismissOwner.Release.RETURN, up(adapter, 70f, releaseTime))
        }
    }

    @Test fun renewedDownwardMotionUsesOnlyItsOwnVelocitySegment() {
        for (fast in listOf(false, true)) {
            val adapter = SettingsGestureAdapter()
            down(adapter)
            move(adapter, 80f, 40) { adapter.header(80f) }
            move(adapter, 70f, 50) { adapter.header(-10f) }
            val nextY = if (fast) 90f else 71f
            move(adapter, nextY, 60) { adapter.header(nextY - 70f) }
            assertEquals(
                if (fast) SettingsDismissOwner.Release.CLOSE else SettingsDismissOwner.Release.RETURN,
                up(adapter, nextY, 60),
            )
        }
    }

    @Test fun deliberateDistanceRemainsIndependentOfFlickDirection() {
        val adapter = SettingsGestureAdapter()
        down(adapter)
        move(adapter, 210f, 1000) { adapter.header(210f) }
        move(adapter, 200f, 1100) { adapter.header(-10f) }
        assertEquals(SettingsDismissOwner.Release.CLOSE, up(adapter, 200f, 1100))
    }

    @Test fun presentationOwnerIsRememberedBeforeHiddenAndPipBranches() {
        val candidates = listOf(
            java.io.File("src/main/kotlin/dev/phosphor/mobil3/ui/PhosphorScreen.kt"),
            java.io.File("app/src/main/kotlin/dev/phosphor/mobil3/ui/PhosphorScreen.kt"),
        )
        val source = candidates.first { it.isFile }.readText()
            .substringAfter("fun PhosphorScreen(state:")
        val owner = source.indexOf("val settingsPresentation = rememberSettingsPresentationState()")
        assertTrue(owner >= 0)
        assertTrue(owner < source.indexOf("if (!state.presentationVisible) return"))
        assertTrue(owner < source.indexOf("if (state.pip)"))
    }

    @Test fun nonFiniteEligibleTravelCancelsRatherThanLeavingAQualifiedPull() {
        for (case in 0..2) {
            val adapter = SettingsGestureAdapter()
            down(adapter)
            move(adapter, 250f, 1000) { adapter.header(250f) }
            move(adapter, 300f, 1100) {
                when (case) {
                    0 -> adapter.header(Float.NaN)
                    1 -> adapter.remainder(Float.POSITIVE_INFINITY, true, true)
                    2 -> adapter.remainder(50f, true, true, childConsumedDp = Float.NaN)
                }
            }
            assertEquals(SettingsDismissOwner.Release.NONE, up(adapter, 300f, 1200))
            assertEquals(0f, adapter.offsetDp, 0f)
        }
    }

    @Test fun remainingFingerAfterMultiPointerCancellationCannotRearm() {
        val adapter = SettingsGestureAdapter()
        down(adapter)
        move(adapter, 250f, 1000) { adapter.header(250f) }
        adapter.initial(1, 250f, 1100, true, true, 2)
        adapter.final()
        adapter.initial(2, 500f, 1200, true, true, 1)
        assertEquals(0f, adapter.remainder(250f, true, true), 0f)
        assertEquals(SettingsDismissOwner.Release.NONE, adapter.final())
        assertFalse(adapter.committed)
    }

    @Test fun allSixStableSectionsSurviveRetirementAndBoundedAnchorCorrection() {
        val owner = SettingsPresentationOwner(initiallyExpanded = emptySet())
        val adapter = SettingsAnchorAdapter(owner)
        SettingsSectionId.entries.forEach { section ->
            val request = adapter.toggle(section, 100)
            assertTrue(adapter.laidOut(request))
            assertFalse(adapter.laidOut(request))
            assertEquals(20, adapter.correction(request, 500, 80, 100))
        }
        adapter.cancel()
        assertEquals(SettingsSectionId.entries.toSet(), owner.expanded)
        assertEquals(100, owner.scrollPx)
        val reopen = adapter.toggle(SettingsSectionId.BEAM, 0)
        adapter.laidOut(reopen)
        assertEquals(-100, adapter.correction(reopen, -500, 100, 600))
        assertEquals(SettingsSectionId.entries.toSet() - SettingsSectionId.BEAM, owner.expanded)
    }
}
