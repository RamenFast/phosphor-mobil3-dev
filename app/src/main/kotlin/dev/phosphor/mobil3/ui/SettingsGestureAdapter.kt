package dev.phosphor.mobil3.ui

/** The production pointer/nested-scroll boundary, independent of Compose event classes. */
internal class SettingsGestureAdapter {
    private val owner = SettingsDismissOwner()
    private var ticket: SettingsDismissOwner.Ticket? = null
    private var pointer: Long? = null
    private var y = 0f
    private var millis = 0L
    private var eventY = 0f
    private var eventMillis = 0L
    private var releasePending = false
    private var budget = 0f
    private var delivered = false
    private var pendingDelivery = false
    var requiresReopen = false
        private set
    val offsetDp: Float get() = owner.offsetDp
    val rawDp: Float get() = owner.rawDp
    val committed: Boolean get() = owner.committed

    /** Initial pass. Observation never consumes input. Coordinates are outside the moving card. */
    fun initial(
        id: Long, positionDp: Float, timeMillis: Long,
        pressed: Boolean, previouslyPressed: Boolean, pointers: Int,
        directPointer: Boolean = true,
    ) {
        if (pendingDelivery) cancel()
        if (requiresReopen) return
        budget = 0f
        releasePending = false
        delivered = false
        if (!directPointer || pointers != 1 || !positionDp.isFinite() || timeMillis < 0) {
            cancel()
            return
        }
        if (pressed && !previouslyPressed) {
            ticket = owner.down(positionDp, timeMillis, fresh = true, pointers = pointers)
            pointer = if (ticket != null) id else null
            y = positionDp
            millis = timeMillis
        } else if (pointer != id || ticket == null) {
            // In particular, a finger already down when Settings mounts cannot become a ticket.
            cancel()
            return
        }
        if (timeMillis < millis) {
            cancel()
            return
        }
        ticket?.let { if (!owner.observe(it, y, millis, 1)) { cancel(); return } }
        eventY = positionDp
        eventMillis = timeMillis
        budget = if (pressed && previouslyPressed) positionDp - y else 0f
        if (!budget.isFinite()) {
            cancel()
            return
        }
        releasePending = !pressed && previouslyPressed
    }

    fun header(deltaDp: Float): Float {
        val consumed = admit(deltaDp) { owner.header(it, deltaDpBounded(deltaDp)) }
        completeDelivery()
        return consumed
    }

    fun childDown(id: Long) {
        if (pointer == id) cancel()
    }

    fun remainder(deltaDp: Float, direct: Boolean, atTop: Boolean, childConsumedDp: Float = 0f): Float {
        if (direct) {
            if (!childConsumedDp.isFinite()) { cancel(); return 0f }
            if (budget > 0f) budget = (budget - childConsumedDp.coerceAtLeast(0f)).coerceAtLeast(0f)
            completeDelivery()
        }
        if (!direct || !atTop || deltaDp <= 0f) return 0f
        return admit(deltaDp) { owner.remainder(it, deltaDpBounded(deltaDp), true, true) }
    }

    fun reverse(deltaDp: Float, direct: Boolean): Float {
        if (!direct || deltaDp >= 0f) return 0f
        return admit(deltaDp) { owner.reverse(it, deltaDpBounded(deltaDp), true) }
    }

    private fun deltaDpBounded(delta: Float): Float =
        if (delta > 0f) minOf(delta, budget) else maxOf(delta, budget)

    private fun completeDelivery() {
        if (ticket == null) return
        delivered = true
        pendingDelivery = false
    }

    private inline fun admit(delta: Float, travel: (SettingsDismissOwner.Ticket) -> Float): Float {
        val current = ticket ?: return 0f
        if (!delta.isFinite()) {
            cancel()
            return 0f
        }
        if (releasePending || budget == 0f || delta * budget <= 0f) return 0f
        val consumed = travel(current)
        budget -= consumed
        return consumed
    }

    /** Final pass. Child consumption alone is not eligible travel. Release decides exactly once. */
    fun final(consumedByChild: Boolean = false): SettingsDismissOwner.Release {
        val current = ticket ?: return SettingsDismissOwner.Release.NONE
        // Consumption queues Scrollable work. Final is not an acknowledgement that it ran.
        pendingDelivery = consumedByChild && eventY != y && !releasePending && !delivered
        // Up can carry a final reversal without a separate nested move. Repay only:
        // unproven positive terminal travel must never create dismissal distance.
        if (releasePending && eventY < y) owner.reverse(current, eventY - y, true)
        // A scrollable may deliver its nested remainder after the Final pass. Keep the
        // previous physical sample until eligibility begins, including that delivery order.
        if ((owner.rawDp > 0f || releasePending) && !owner.observe(current, eventY, eventMillis, 1)) {
            cancel()
            return SettingsDismissOwner.Release.NONE
        }
        y = eventY
        millis = eventMillis
        if (!releasePending) return SettingsDismissOwner.Release.NONE
        ticket = null
        pointer = null
        budget = 0f
        releasePending = false
        return owner.release(current)
    }

    // Fling callbacks cannot manufacture a pointer release or another close decision.
    fun postFling(): SettingsDismissOwner.Release = SettingsDismissOwner.Release.NONE

    fun cancel() {
        if (pendingDelivery) requiresReopen = true
        pendingDelivery = false
        delivered = false
        ticket = null
        pointer = null
        budget = 0f
        releasePending = false
        owner.cancel()
    }

    fun retire() { cancel(); owner.retire() }
}

/** A layout receipt and application belong to one toggle and one visible opening. */
internal class SettingsAnchorAdapter(val owner: SettingsPresentationOwner) {
    class Request internal constructor(
        internal val anchor: SettingsPresentationOwner.Anchor,
        val revision: Long,
    ) {
        val section: SettingsSectionId get() = anchor.section
    }
    private var revision = 0L
    private var pending: Request? = null
    private var laidOut: Request? = null

    fun toggle(section: SettingsSectionId, viewportY: Int): Request {
        cancel()
        return Request(owner.toggle(section, viewportY), revision).also { pending = it }
    }

    fun laidOut(request: Request): Boolean {
        if (pending !== request || laidOut === request || request.revision != revision) return false
        laidOut = request
        return true
    }

    /** The caller applies this correction synchronously, with no suspension after validation. */
    fun correction(request: Request, newViewportY: Int, scroll: Int, maxScroll: Int): Int? {
        if (pending !== request || laidOut !== request || request.revision != revision) return null
        pending = null
        laidOut = null
        return owner.anchorScroll(request.anchor, newViewportY, scroll, maxScroll)?.minus(scroll)
    }

    fun cancel() {
        revision++
        pending = null
        laidOut = null
        owner.cancelAnchor()
    }
}
