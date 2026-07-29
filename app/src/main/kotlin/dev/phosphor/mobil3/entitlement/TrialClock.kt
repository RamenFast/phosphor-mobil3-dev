package dev.phosphor.mobil3.entitlement

/**
 * A wall clock that cannot be walked backwards to win free days, and cannot rob honest
 * users of days they already have.
 *
 * The device clock is user-settable, so a trial anchored only to it can be reset by
 * changing the date. The naive defence, refusing to run when the clock moves back,
 * punishes ordinary things: travelling across timezones, an NTP correction after a flat
 * battery, or a factory-fresh phone whose clock starts in 1970.
 *
 * So this keeps a monotonic floor. Wall time may move forward freely and is trusted; a
 * backwards jump is ignored in favour of the last credible reading. The worst a clock
 * fiddler achieves is freezing their remaining trial, never extending it, and an honest
 * user whose clock corrects itself simply carries on.
 *
 * Deliberately not doing: hardware identifiers, server tokens or Play Integrity. For a
 * one-off $3.99 purchase that would cost more privacy than it protects revenue, and the
 * publishing plan says so explicitly (section 5.2).
 */
data class TrialClockState(
    /** Highest wall-clock reading accepted so far. The floor a rollback is measured against. */
    val lastCredibleMillis: Long,
    /** Elapsed-realtime at that reading. Survives clock edits; resets to 0 on reboot. */
    val elapsedRealtimeAtLastCredible: Long,
)

/** What a clock reading produced, and whether anything looked wrong. */
data class TrialClockReading(
    /** The time to actually use. Never earlier than the last credible reading. */
    val credibleNowMillis: Long,
    val state: TrialClockState,
    /** True when wall time moved backwards, so callers can explain it rather than fail silently. */
    val rollbackDetected: Boolean,
)

object TrialClock {

    /**
     * A jump this much larger than the elapsed-realtime delta is treated as a clock edit
     * rather than the passage of time.
     *
     * Two minutes absorbs NTP corrections and timezone shifts without absorbing the kind
     * of leap someone makes to skip a trial.
     */
    const val FORWARD_TOLERANCE_MILLIS: Long = 2L * 60 * 1000

    /** First reading. Whatever the clock says becomes the floor; there is nothing to compare to. */
    fun start(wallMillis: Long, elapsedRealtimeMillis: Long) = TrialClockReading(
        credibleNowMillis = wallMillis,
        state = TrialClockState(wallMillis, elapsedRealtimeMillis),
        rollbackDetected = false,
    )

    /**
     * Read the clock against the stored floor.
     *
     * [elapsedRealtimeMillis] is monotonic since boot and cannot be edited, so it is the
     * honest witness for how much time really passed. It resets on reboot, which is why
     * it corroborates rather than replaces wall time.
     */
    fun read(
        state: TrialClockState,
        wallMillis: Long,
        elapsedRealtimeMillis: Long,
    ): TrialClockReading {
        // A reboot resets elapsed-realtime, so a smaller value than stored means "rebooted"
        // rather than "time ran backwards". Nothing can be inferred from the delta then.
        val rebooted = elapsedRealtimeMillis < state.elapsedRealtimeAtLastCredible
        val realElapsed = if (rebooted) 0 else elapsedRealtimeMillis - state.elapsedRealtimeAtLastCredible
        val wallElapsed = wallMillis - state.lastCredibleMillis

        return when {
            // Backwards. Hold the floor: the trial neither advances nor rewinds.
            wallElapsed < 0 -> TrialClockReading(
                credibleNowMillis = state.lastCredibleMillis,
                state = state.copy(elapsedRealtimeAtLastCredible = elapsedRealtimeMillis),
                rollbackDetected = true,
            )

            // Forward by far more than the device actually ran. Credit only the time the
            // monotonic clock can vouch for, so a leap forward cannot burn the trial down
            // in one step. Skipped after a reboot, when there is no witness to compare to.
            !rebooted && wallElapsed > realElapsed + FORWARD_TOLERANCE_MILLIS -> {
                val credible = state.lastCredibleMillis + realElapsed
                TrialClockReading(
                    credibleNowMillis = credible,
                    state = TrialClockState(credible, elapsedRealtimeMillis),
                    rollbackDetected = true,
                )
            }

            // Ordinary forward motion, including any jump across a reboot we cannot check.
            else -> TrialClockReading(
                credibleNowMillis = wallMillis,
                state = TrialClockState(wallMillis, elapsedRealtimeMillis),
                rollbackDetected = false,
            )
        }
    }
}
