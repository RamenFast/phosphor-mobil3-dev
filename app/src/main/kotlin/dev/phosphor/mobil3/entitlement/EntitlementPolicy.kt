package dev.phosphor.mobil3.entitlement

/**
 * What the user is currently allowed to do, and why.
 *
 * This is deliberately a pure state machine with no Android and no Play Billing in it.
 * The rules below are the part that can be got subtly wrong in ways a user would feel
 * (a trial that relocks on a flaky network, a refund that never takes effect, a clock
 * change that steals days), so they live somewhere they can be tested exhaustively on
 * the host. Billing wiring, storage and UI all sit outside and feed this.
 *
 * Rules are from `docs/dev/GOOGLE-PLAY-PUBLISHING-PLAN.md` section 5.1.
 */
enum class Entitlement {
    /** First run has not finished. Nothing is granted or withheld yet. */
    SETUP_INCOMPLETE,

    /** Setup is done and the user has not chosen to start the trial. Installing is not consent. */
    TRIAL_NOT_STARTED,

    /** Every normal feature is available. Not a limited demo. */
    TRIAL_ACTIVE,

    /** The seven days elapsed. Settings and content survive; paid instrument use is gated. */
    TRIAL_EXPIRED,

    /** Play has taken the purchase but not confirmed it. Grants nothing yet. */
    PURCHASE_PENDING,

    /** Play confirmed and acknowledged a non-consumable purchase. Permanent for that account. */
    PRO_VERIFIED,

    /**
     * Billing is unreachable, so the last known state stands.
     *
     * This exists so a user who has already paid is never relocked by a dead network.
     * Only an authoritative Play answer can take Pro away.
     */
    BILLING_UNAVAILABLE,
}

/** What Play last told us about ownership, kept separate from what we decided to grant. */
enum class PlayOwnership {
    /** Play answered and the user owns Pro. */
    OWNED,

    /** Play answered and the user does not own Pro, including after a refund or revocation. */
    NOT_OWNED,

    /** Play took the purchase but has not settled it. */
    PENDING,

    /** No authoritative answer. Could not reach Play, or have not asked yet. */
    UNKNOWN,
}

/**
 * The durable facts the state machine reads.
 *
 * [trialStartedAtMillis] is null until the user explicitly starts the trial. [nowMillis]
 * is the caller's best credible wall clock, already corrected for rollback by
 * [TrialClock], so this class never has to reason about a lying clock.
 */
data class EntitlementInputs(
    val setupComplete: Boolean,
    val trialStartedAtMillis: Long?,
    val nowMillis: Long,
    val ownership: PlayOwnership,
    /** What we granted last time. Lets an unreachable Play preserve a paid user's access. */
    val lastKnown: Entitlement? = null,
)

object EntitlementPolicy {

    /** Seven full days, as an exact elapsed span rather than a calendar guess. */
    const val TRIAL_DURATION_MILLIS: Long = 7L * 24 * 60 * 60 * 1000

    /**
     * Decide the current entitlement.
     *
     * Order matters. Ownership is checked before the trial so a user who buys during the
     * trial is immediately Pro rather than waiting for the trial to lapse first.
     */
    fun evaluate(inputs: EntitlementInputs): Entitlement = when {
        // A confirmed purchase outranks everything, including an unfinished setup: someone
        // who has paid should never be shown a gate.
        inputs.ownership == PlayOwnership.OWNED -> Entitlement.PRO_VERIFIED

        // Play took the money but has not settled. Grant nothing yet, and say so plainly
        // rather than pretending the purchase failed.
        inputs.ownership == PlayOwnership.PENDING -> Entitlement.PURCHASE_PENDING

        // Play is unreachable. If we ever saw a verified purchase, that stands: a dead
        // network must not relock a paying user. Otherwise fall through to trial rules,
        // since an unreachable Play is no reason to withhold a trial nobody paid for.
        inputs.ownership == PlayOwnership.UNKNOWN &&
            inputs.lastKnown == Entitlement.PRO_VERIFIED -> Entitlement.BILLING_UNAVAILABLE

        !inputs.setupComplete -> Entitlement.SETUP_INCOMPLETE

        // Installing is not consent. The trial starts only when the user asks for it.
        inputs.trialStartedAtMillis == null -> Entitlement.TRIAL_NOT_STARTED

        elapsedTrialMillis(inputs) < TRIAL_DURATION_MILLIS -> Entitlement.TRIAL_ACTIVE

        else -> Entitlement.TRIAL_EXPIRED
    }

    /** How long the trial has run. Never negative, so a clock skew cannot extend it. */
    fun elapsedTrialMillis(inputs: EntitlementInputs): Long {
        val start = inputs.trialStartedAtMillis ?: return 0
        return (inputs.nowMillis - start).coerceAtLeast(0)
    }

    /** Whole days left, rounded up so a partial day still reads as a day. 0 once expired. */
    fun trialDaysRemaining(inputs: EntitlementInputs): Int {
        if (inputs.trialStartedAtMillis == null) return 0
        val remaining = TRIAL_DURATION_MILLIS - elapsedTrialMillis(inputs)
        if (remaining <= 0) return 0
        val day = 24L * 60 * 60 * 1000
        return ((remaining + day - 1) / day).toInt()
    }

    /**
     * Whether the paid instrument is usable.
     *
     * Deliberately generous at the edges: a pending purchase and an unreachable Play both
     * keep working, because in each case the user has done nothing wrong.
     */
    fun grantsFullUse(state: Entitlement): Boolean = when (state) {
        Entitlement.TRIAL_ACTIVE,
        Entitlement.PRO_VERIFIED,
        Entitlement.BILLING_UNAVAILABLE,
        -> true
        // Setup is not a paywall; the user simply has not finished it yet.
        Entitlement.SETUP_INCOMPLETE,
        Entitlement.TRIAL_NOT_STARTED,
        Entitlement.TRIAL_EXPIRED,
        Entitlement.PURCHASE_PENDING,
        -> false
    }

    /**
     * Whether to show the Pro sheet.
     *
     * Only when the user is genuinely out of options. A pending purchase must not be
     * nagged at, since the money is already in flight.
     */
    fun shouldOfferPurchase(state: Entitlement): Boolean = state == Entitlement.TRIAL_EXPIRED
}
