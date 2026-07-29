package dev.phosphor.mobil3.entitlement

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The clock rules, written as the situations real people end up in.
 *
 * Two failure modes matter here and they pull in opposite directions: someone winding the
 * clock back to farm free trials, and someone flying to another timezone who should not
 * be punished for it. Each test names which one it is protecting.
 */
class TrialClockTest {

    private val minute = 60L * 1000
    private val hour = 60 * minute
    private val day = 24 * hour
    private val t0 = 1_700_000_000_000L

    @Test
    fun theFirstReadingIsTrustedBecauseThereIsNothingToCompareTo() {
        val reading = TrialClock.start(t0, 1000)
        assertEquals(t0, reading.credibleNowMillis)
        assertFalse(reading.rollbackDetected)
    }

    @Test
    fun ordinaryTimePassingIsSimplyBelieved() {
        val start = TrialClock.start(t0, 1000)
        val later = TrialClock.read(start.state, t0 + hour, 1000 + hour)
        assertEquals(t0 + hour, later.credibleNowMillis)
        assertFalse(later.rollbackDetected)
    }

    @Test
    fun windingTheClockBackFreezesTheTrialRatherThanRewindingIt() {
        // The trial-farming case. The best this achieves is standing still.
        val start = TrialClock.start(t0, 1000)
        val threeDaysIn = TrialClock.read(start.state, t0 + 3 * day, 1000 + 3 * day)

        val cheated = TrialClock.read(threeDaysIn.state, t0 - 30 * day, 1000 + 3 * day + minute)

        assertTrue(cheated.rollbackDetected)
        assertEquals(t0 + 3 * day, cheated.credibleNowMillis)
    }

    @Test
    fun aClockThatComesBackFromARollbackResumesNormally() {
        // Having frozen a cheater, we must not brick the app for them forever either.
        val start = TrialClock.start(t0, 1000)
        val rolledBack = TrialClock.read(start.state, t0 - 10 * day, 1000 + minute)
        assertTrue(rolledBack.rollbackDetected)

        val corrected = TrialClock.read(rolledBack.state, t0 + hour, 1000 + hour)
        assertEquals(t0 + hour, corrected.credibleNowMillis)
        assertFalse(corrected.rollbackDetected)
    }

    @Test
    fun jumpingTheClockForwardCannotBurnTheTrialDownInOneStep() {
        // The mirror attack: skip to expiry to force the paywall, or an NTP correction
        // that would otherwise eat the whole trial. Only witnessed time is credited.
        val start = TrialClock.start(t0, 1000)

        val leapt = TrialClock.read(start.state, t0 + 30 * day, 1000 + 5 * minute)

        assertTrue(leapt.rollbackDetected)
        assertEquals(t0 + 5 * minute, leapt.credibleNowMillis)
    }

    @Test
    fun aTimezoneShiftIsAbsorbedRatherThanTreatedAsTampering() {
        // Flying somewhere should not trip anything. An hour of wall time with an hour of
        // real time behind it is just an hour.
        val start = TrialClock.start(t0, 1000)
        val flown = TrialClock.read(start.state, t0 + hour, 1000 + hour)
        assertFalse(flown.rollbackDetected)
        assertEquals(t0 + hour, flown.credibleNowMillis)
    }

    @Test
    fun anNtpNudgeInsideToleranceIsAccepted() {
        // Small corrections are normal and must not be treated as an attack.
        val start = TrialClock.start(t0, 1000)
        val nudged = TrialClock.read(start.state, t0 + minute + 30_000, 1000 + minute)
        assertFalse(nudged.rollbackDetected)
        assertEquals(t0 + minute + 30_000, nudged.credibleNowMillis)
    }

    @Test
    fun aRebootIsNotMistakenForTimeTravel() {
        // Elapsed-realtime resets to near zero on boot. Reading that as "time went
        // backwards" would flag every restart as tampering.
        val start = TrialClock.start(t0, 5 * day)
        val afterReboot = TrialClock.read(start.state, t0 + hour, 30_000)
        assertFalse(afterReboot.rollbackDetected)
        assertEquals(t0 + hour, afterReboot.credibleNowMillis)
    }

    @Test
    fun aLongPoweredOffStretchStillCountsAsTimePassing() {
        // A phone off for a fortnight really did spend a fortnight. Since elapsed-realtime
        // cannot vouch for it, wall time is accepted rather than the trial being frozen.
        val start = TrialClock.start(t0, 5 * day)
        val afterHoliday = TrialClock.read(start.state, t0 + 14 * day, 60_000)
        assertFalse(afterHoliday.rollbackDetected)
        assertEquals(t0 + 14 * day, afterHoliday.credibleNowMillis)
    }

    @Test
    fun theCredibleFloorNeverGoesDownAcrossAWholeMessySequence() {
        // The invariant that actually protects the trial, checked over a run of edits,
        // reboots and corrections rather than one step at a time.
        var state = TrialClock.start(t0, 1000).state
        var floor = t0
        val script = listOf(
            (t0 + hour) to (1000 + hour),
            (t0 - 40 * day) to (1000 + hour + minute),
            (t0 + 2 * hour) to (1000 + 2 * hour),
            (t0 + 500 * day) to (1000 + 2 * hour + minute),
            (t0 + 3 * hour) to 5_000L,
        )
        script.forEach { (wall, elapsed) ->
            val reading = TrialClock.read(state, wall, elapsed)
            assertTrue(
                reading.credibleNowMillis >= floor,
                "credible time went backwards: ${reading.credibleNowMillis} < $floor",
            )
            floor = maxOf(floor, reading.credibleNowMillis)
            state = reading.state
        }
    }

    @Test
    fun aFrozenClockCannotBeUsedToRunTheTrialForever() {
        // End to end with the policy: a cheater who pins the clock keeps whatever days
        // they had, and gains none.
        val start = TrialClock.start(t0, 1000)
        val sixDaysIn = TrialClock.read(start.state, t0 + 6 * day, 1000 + 6 * day)

        val pinned = TrialClock.read(sixDaysIn.state, t0 + 6 * day, 1000 + 60 * day)

        val state = EntitlementPolicy.evaluate(
            EntitlementInputs(
                setupComplete = true,
                trialStartedAtMillis = t0,
                nowMillis = pinned.credibleNowMillis,
                ownership = PlayOwnership.NOT_OWNED,
            ),
        )
        // Still inside the seven days, which is correct: they really have only used six.
        assertEquals(Entitlement.TRIAL_ACTIVE, state)
        assertEquals(1, EntitlementPolicy.trialDaysRemaining(
            EntitlementInputs(true, t0, pinned.credibleNowMillis, PlayOwnership.NOT_OWNED),
        ))
    }
}
