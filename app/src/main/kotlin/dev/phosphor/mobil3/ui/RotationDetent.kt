package dev.phosphor.mobil3.ui

/**
 * The rotation detent, extracted so it can be tested without a phone.
 *
 * `MainActivity` owns a sensor, a low-pass filter and a Looper, so the decision it makes
 * cannot be exercised on the host. The decision itself is pure arithmetic, and it is the
 * part that determines whether rotation feels deliberate or twitchy, so it lives here.
 */
object RotationDetent {

    /** Activity orientation selected after a gravity cardinal commits. */
    enum class ScreenTarget {
        PORTRAIT,
        LANDSCAPE,
        REVERSE_PORTRAIT,
        REVERSE_LANDSCAPE,
        UNSPECIFIED,
    }

    /**
     * How close to a NEW cardinal the phone must be turned before the chrome follows.
     * Tight, so leaving the current orientation takes a real turn rather than a lean.
     */
    const val COMMIT_TOLERANCE = 18

    /**
     * How far the phone may drift while KEEPING its orientation. Wide, so a hand-held
     * phone at an untidy angle stays put instead of hunting between two orientations.
     *
     * The gap between the two is the detent. Both stay under 45°, so no angle can
     * satisfy two cardinals at once.
     */
    const val HOLD_TOLERANCE = 38

    /** No orientation committed yet: the first credible reading takes it. */
    const val NONE = -1

    /** Nearest cardinal (0/90/180/270) to a gravity angle in degrees. */
    fun nearestCardinal(degrees: Int): Int = ((degrees + 45) / 90) % 4 * 90

    /** Signed offset from that cardinal, in -180..180. */
    fun offsetFromCardinal(degrees: Int): Int {
        val cardinal = nearestCardinal(degrees)
        return ((degrees - cardinal + 540) % 360) - 180
    }

    /**
     * Whether a reading should move the chrome to a new orientation.
     *
     * Asymmetric on purpose: keeping the current orientation is easy, taking a new one
     * is not. A symmetric window flips at exactly 45°, which is why a small wrist tilt
     * used to reorient the whole UI.
     */
    fun shouldCommit(committed: Int, degrees: Int): Boolean {
        val cardinal = nearestCardinal(degrees)
        val offset = offsetFromCardinal(degrees)
        val leaving = committed != NONE && cardinal != committed
        val tolerance = if (leaving) COMMIT_TOLERANCE else HOLD_TOLERANCE
        return offset in -tolerance..tolerance
    }

    /** The orientation after a reading: the new cardinal if it commits, else unchanged. */
    fun next(committed: Int, degrees: Int): Int =
        if (shouldCommit(committed, degrees)) nearestCardinal(degrees) else committed

    /**
     * Translate the accelerometer cardinal into Android's screen-orientation vocabulary.
     *
     * The sensor angle grows in the opposite direction from Android's landscape names.
     * Keeping this conversion beside the detent makes both landscape quadrants explicit
     * and host-testable.
     */
    fun screenTarget(cardinal: Int): ScreenTarget = when (cardinal) {
        0 -> ScreenTarget.PORTRAIT
        90 -> ScreenTarget.REVERSE_LANDSCAPE
        180 -> ScreenTarget.REVERSE_PORTRAIT
        270 -> ScreenTarget.LANDSCAPE
        else -> ScreenTarget.UNSPECIFIED
    }
}
