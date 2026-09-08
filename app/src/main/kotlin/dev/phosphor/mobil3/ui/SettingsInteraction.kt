package dev.phosphor.mobil3.ui

/** Settings-only decision owner. Pointer observation itself does not consume child input. */
internal class SettingsDismissOwner {
    class Ticket internal constructor()
    enum class Release { NONE, RETURN, CLOSE }
    private data class Sample(val y: Float, val millis: Long)

    private var ticket: Ticket? = null
    private var latest: Sample? = null
    private val samples = ArrayDeque<Sample>()
    private var retired = false
    var committed = false
        private set
    var rawDp = 0f
        private set
    val offsetDp: Float get() = RESISTANCE_DP * (rawDp / (RESISTANCE_DP + rawDp))
    val active: Boolean get() = ticket != null

    fun down(y: Float, millis: Long, fresh: Boolean, pointers: Int): Ticket? {
        cancel()
        if (retired || committed || !fresh || pointers != 1 || !y.isFinite() || millis < 0) return null
        return Ticket().also {
            ticket = it
            latest = Sample(y, millis)
        }
    }

    fun observe(expected: Ticket, y: Float, millis: Long, pointers: Int): Boolean {
        if (ticket !== expected) return false
        val prior = latest ?: return false
        if (pointers != 1 || !y.isFinite() || millis < prior.millis) {
            cancel()
            return false
        }
        latest = Sample(y, millis)
        if (rawDp > 0f) rememberSample(latest!!)
        return true
    }

    fun header(expected: Ticket, deltaDp: Float): Float = travel(expected, deltaDp)

    fun remainder(expected: Ticket?, deltaDp: Float, direct: Boolean, atTop: Boolean): Float {
        if (expected == null || !direct || !atTop || deltaDp <= 0f) return 0f
        return travel(expected, deltaDp)
    }

    fun reverse(expected: Ticket?, deltaDp: Float, direct: Boolean): Float {
        if (expected == null || !direct || deltaDp >= 0f || rawDp <= 0f) return 0f
        return travel(expected, deltaDp.coerceAtLeast(-rawDp))
    }

    private fun travel(expected: Ticket, deltaDp: Float): Float {
        if (ticket !== expected || committed || retired) return 0f
        if (!deltaDp.isFinite()) {
            cancel()
            return 0f
        }
        val old = rawDp
        val next = old + deltaDp
        if (!next.isFinite()) {
            cancel()
            return 0f
        }
        rawDp = next.coerceAtLeast(0f)
        if (old == 0f && rawDp > 0f) {
            samples.clear()
            latest?.let(::rememberSample)
        } else if (rawDp == 0f) samples.clear()
        return rawDp - old
    }

    private fun rememberSample(sample: Sample) {
        if (samples.lastOrNull()?.millis == sample.millis) samples.removeLast()
        samples.addLast(sample)
        while (samples.size > 2 && sample.millis - samples.first().millis > VELOCITY_WINDOW_MS) {
            samples.removeFirst()
        }
        while (samples.size > MAX_SAMPLES) samples.removeFirst()
    }

    fun release(expected: Ticket): Release {
        if (ticket !== expected || retired || committed) return Release.NONE
        val first = samples.firstOrNull()
        val last = samples.lastOrNull()
        val elapsed = if (first != null && last != null) last.millis - first.millis else 0L
        val velocity = if (elapsed > 0 && first != null && last != null)
            (last.y - first.y) * 1000f / elapsed else 0f
        val close = rawDp >= SLOW_DISTANCE_DP ||
            (rawDp >= FLICK_DISTANCE_DP && velocity.isFinite() && velocity >= FLICK_DP_PER_SECOND)
        ticket = null
        latest = null
        samples.clear()
        if (close) {
            committed = true
            return Release.CLOSE
        }
        rawDp = 0f
        return Release.RETURN
    }

    fun cancel() {
        ticket = null
        latest = null
        samples.clear()
        if (!committed) rawDp = 0f
    }

    fun retire() {
        cancel()
        retired = true
    }

    companion object {
        const val SLOW_DISTANCE_DP = 192f
        const val FLICK_DISTANCE_DP = 64f
        const val FLICK_DP_PER_SECOND = 920f
        const val RESISTANCE_DP = 160f
        private const val VELOCITY_WINDOW_MS = 100L
        private const val MAX_SAMPLES = 32
    }
}

internal enum class SettingsSectionId {
    SIGNAL, BEAM, DISPLAY, MOTION, APPEARANCE, ABOUT
}

/** Presentation only. This owner never reads or writes tuning or source preferences. */
internal class SettingsPresentationOwner(
    initiallyExpanded: Set<SettingsSectionId> = setOf(SettingsSectionId.SIGNAL),
) {
    class Anchor internal constructor(val section: SettingsSectionId, internal val viewportY: Int)
    private val open = initiallyExpanded.toMutableSet()
    private var pending: Anchor? = null
    val expanded: Set<SettingsSectionId> get() = open.toSet()
    var scrollPx: Int = 0
        private set

    fun isExpanded(section: SettingsSectionId): Boolean = section in open

    fun toggle(section: SettingsSectionId, viewportY: Int): Anchor {
        if (!open.add(section)) open.remove(section)
        return Anchor(section, viewportY).also { pending = it }
    }

    fun anchorScroll(anchor: Anchor, newViewportY: Int, currentScroll: Int, maxScroll: Int): Int? {
        if (pending !== anchor) return null
        pending = null
        val target = currentScroll.toLong() + newViewportY.toLong() - anchor.viewportY.toLong()
        return target.coerceIn(0L, maxScroll.coerceAtLeast(0).toLong()).toInt().also { scrollPx = it }
    }

    fun rememberScroll(value: Int) { scrollPx = value.coerceAtLeast(0) }
    fun cancelAnchor() { pending = null }
}
