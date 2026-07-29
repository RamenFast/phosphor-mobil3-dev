package dev.phosphor.mobil3.entitlement

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The entitlement rules, stated as the user-visible promises they encode.
 *
 * Each test name is the promise. If one fails, a user is being treated unfairly in a
 * specific way, which is more useful than knowing "the state machine broke".
 */
class EntitlementPolicyTest {

    private val day = 24L * 60 * 60 * 1000
    private val t0 = 1_700_000_000_000L

    private fun inputs(
        setupComplete: Boolean = true,
        startedAt: Long? = null,
        now: Long = t0,
        ownership: PlayOwnership = PlayOwnership.NOT_OWNED,
        lastKnown: Entitlement? = null,
    ) = EntitlementInputs(setupComplete, startedAt, now, ownership, lastKnown)

    // ── installing is not consent ────────────────────────────────────────────

    @Test
    fun installingTheAppDoesNotStartTheTrial() {
        // The seven days are a resource. They begin when the user asks, not when Play
        // finishes copying the APK.
        assertEquals(Entitlement.TRIAL_NOT_STARTED, EntitlementPolicy.evaluate(inputs()))
    }

    @Test
    fun setupIsNotAPaywall() {
        val state = EntitlementPolicy.evaluate(inputs(setupComplete = false))
        assertEquals(Entitlement.SETUP_INCOMPLETE, state)
        assertFalse(EntitlementPolicy.shouldOfferPurchase(state))
    }

    // ── the trial itself ─────────────────────────────────────────────────────

    @Test
    fun theTrialGrantsEveryFeatureRatherThanBeingALimitedDemo() {
        val state = EntitlementPolicy.evaluate(inputs(startedAt = t0, now = t0 + day))
        assertEquals(Entitlement.TRIAL_ACTIVE, state)
        assertTrue(EntitlementPolicy.grantsFullUse(state))
    }

    @Test
    fun sevenDaysMeansOneHundredAndSixtyEightHoursNotSixDaysAndAnHour() {
        val justInside = inputs(startedAt = t0, now = t0 + EntitlementPolicy.TRIAL_DURATION_MILLIS - 1)
        assertEquals(Entitlement.TRIAL_ACTIVE, EntitlementPolicy.evaluate(justInside))

        val exactlyAt = inputs(startedAt = t0, now = t0 + EntitlementPolicy.TRIAL_DURATION_MILLIS)
        assertEquals(Entitlement.TRIAL_EXPIRED, EntitlementPolicy.evaluate(exactlyAt))
    }

    @Test
    fun daysRemainingRoundsUpSoAPartialDayStillCounts() {
        // Telling someone "0 days left" while they still have hours would be a lie.
        assertEquals(7, EntitlementPolicy.trialDaysRemaining(inputs(startedAt = t0, now = t0)))
        assertEquals(1, EntitlementPolicy.trialDaysRemaining(inputs(startedAt = t0, now = t0 + 6 * day + 1)))
        assertEquals(
            0,
            EntitlementPolicy.trialDaysRemaining(
                inputs(startedAt = t0, now = t0 + EntitlementPolicy.TRIAL_DURATION_MILLIS),
            ),
        )
    }

    @Test
    fun expiryGatesPaidUseAndOffersTheUpgradeExactlyOnce() {
        val state = EntitlementPolicy.evaluate(
            inputs(startedAt = t0, now = t0 + 8 * day),
        )
        assertEquals(Entitlement.TRIAL_EXPIRED, state)
        assertFalse(EntitlementPolicy.grantsFullUse(state))
        assertTrue(EntitlementPolicy.shouldOfferPurchase(state))
    }

    // ── purchase ─────────────────────────────────────────────────────────────

    @Test
    fun buyingDuringTheTrialTakesEffectImmediately() {
        // Someone who pays on day two should not keep seeing trial messaging.
        val state = EntitlementPolicy.evaluate(
            inputs(startedAt = t0, now = t0 + 2 * day, ownership = PlayOwnership.OWNED),
        )
        assertEquals(Entitlement.PRO_VERIFIED, state)
        assertTrue(EntitlementPolicy.grantsFullUse(state))
    }

    @Test
    fun aVerifiedPurchaseOutlivesAnExpiredTrial() {
        val state = EntitlementPolicy.evaluate(
            inputs(startedAt = t0, now = t0 + 400 * day, ownership = PlayOwnership.OWNED),
        )
        assertEquals(Entitlement.PRO_VERIFIED, state)
    }

    @Test
    fun aPendingPurchaseGrantsNothingYetButIsNeverNaggedAt() {
        // The money is in flight. Withhold the feature, but do not ask again.
        val state = EntitlementPolicy.evaluate(
            inputs(startedAt = t0, now = t0 + 8 * day, ownership = PlayOwnership.PENDING),
        )
        assertEquals(Entitlement.PURCHASE_PENDING, state)
        assertFalse(EntitlementPolicy.grantsFullUse(state))
        assertFalse(EntitlementPolicy.shouldOfferPurchase(state))
    }

    @Test
    fun aRefundEventuallyRemovesProRatherThanBeingIgnored() {
        // Play is authoritative when it actually answers.
        val state = EntitlementPolicy.evaluate(
            inputs(
                startedAt = t0,
                now = t0 + 400 * day,
                ownership = PlayOwnership.NOT_OWNED,
                lastKnown = Entitlement.PRO_VERIFIED,
            ),
        )
        assertEquals(Entitlement.TRIAL_EXPIRED, state)
    }

    // ── the network is not the user's fault ──────────────────────────────────

    @Test
    fun aPayingUserIsNeverRelockedByAnUnreachablePlay() {
        // The failure mode that would most annoy someone who has already paid.
        val state = EntitlementPolicy.evaluate(
            inputs(
                startedAt = t0,
                now = t0 + 400 * day,
                ownership = PlayOwnership.UNKNOWN,
                lastKnown = Entitlement.PRO_VERIFIED,
            ),
        )
        assertEquals(Entitlement.BILLING_UNAVAILABLE, state)
        assertTrue(EntitlementPolicy.grantsFullUse(state))
    }

    @Test
    fun anUnreachablePlayDoesNotInventProForSomeoneWhoNeverPaid() {
        // The mirror of the rule above: offline must not become a free unlock.
        val state = EntitlementPolicy.evaluate(
            inputs(startedAt = t0, now = t0 + 8 * day, ownership = PlayOwnership.UNKNOWN),
        )
        assertEquals(Entitlement.TRIAL_EXPIRED, state)
        assertFalse(EntitlementPolicy.grantsFullUse(state))
    }

    @Test
    fun anUnreachablePlayStillLetsAFreshTrialRun() {
        // No purchase is involved, so being offline is no reason to withhold the trial.
        val state = EntitlementPolicy.evaluate(
            inputs(startedAt = t0, now = t0 + day, ownership = PlayOwnership.UNKNOWN),
        )
        assertEquals(Entitlement.TRIAL_ACTIVE, state)
    }

    // ── clock edges ──────────────────────────────────────────────────────────

    @Test
    fun aBackwardsClockCannotExtendTheTrialPastItsSpan() {
        // Defence in depth: TrialClock should have corrected this already, but a negative
        // elapsed must never read as time remaining here either.
        val elapsed = EntitlementPolicy.elapsedTrialMillis(
            inputs(startedAt = t0, now = t0 - 100 * day),
        )
        assertEquals(0, elapsed)
    }

    @Test
    fun everyStateIsEitherFullUseOrExplicitlyNot() {
        // Guards against a future state being added and silently defaulting to granted.
        Entitlement.entries.forEach { state ->
            val granted = EntitlementPolicy.grantsFullUse(state)
            val expected = state in setOf(
                Entitlement.TRIAL_ACTIVE,
                Entitlement.PRO_VERIFIED,
                Entitlement.BILLING_UNAVAILABLE,
            )
            assertEquals(expected, granted, "unclassified entitlement state: $state")
        }
    }
}
