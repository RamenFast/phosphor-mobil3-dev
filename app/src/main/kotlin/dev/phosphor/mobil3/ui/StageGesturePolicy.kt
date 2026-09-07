package dev.phosphor.mobil3.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/** Numeric geometry only. Compose owns coordinate conversion and monotonic timestamps. */
internal class StageChromeBounds {
    enum class Card { Console, Sheet, Overflow }

    private class Slot {
        var owner: Any? = null
        var bounds: Rect? = null
        var moving = false
        var changedAt: Long? = null
    }

    private val slots = Card.entries.map { Slot() }

    /** Read-only timing evidence, formatted only by the enabled debug observer. */
    fun observation(): String = slots.mapIndexed { index, slot ->
        val rect = slot.bounds?.let { "${it.left},${it.top},${it.right},${it.bottom}" } ?: "none"
        "${Card.entries[index]}=${slot.changedAt ?: -1},${slot.moving},${slot.owner != null},$rect"
    }.joinToString(" ")

    fun mount(card: Card, owner: Any, now: Long) {
        slots[card.ordinal].apply {
            this.owner = owner
            bounds = null
            moving = false
            changedAt = now
        }
    }

    fun sample(card: Card, owner: Any, bounds: Rect, now: Long) {
        slots[card.ordinal].takeIf { it.owner === owner }?.apply {
            if (this.bounds != bounds) {
                this.bounds = bounds
                changedAt = now
            }
        }
    }

    fun motion(card: Card, owner: Any, moving: Boolean, now: Long) {
        slots[card.ordinal].takeIf { it.owner === owner }?.apply {
            if (this.moving != moving) {
                this.moving = moving
                changedAt = now
            }
        }
    }

    fun dismiss(card: Card, owner: Any, now: Long) {
        slots[card.ordinal].takeIf { it.owner === owner }?.apply {
            this.owner = null
            moving = false
            changedAt = now
        }
    }

    fun blocks(points: List<Offset>, marginPx: Float, now: Long): Boolean = slots.any { slot ->
        val settling = slot.changedAt?.let { now - it <= StageGesturePolicy.SETTLE_MS } == true
        slot.moving || settling || (slot.owner != null && slot.bounds == null) ||
            (slot.owner != null && slot.bounds?.let { bounds ->
                points.any { StageGesturePolicy.contains(bounds.inflate(marginPx), it) }
            } == true)
    }
}

internal object StageGesturePolicy {
    const val SETTLE_MS = 333L
    const val MARGIN_DP = 24f

    fun contains(bounds: Rect, point: Offset): Boolean =
        point.x >= bounds.left && point.x <= bounds.right &&
            point.y >= bounds.top && point.y <= bounds.bottom

    fun inBottomBand(point: Offset, root: Rect, bandPx: Float): Boolean =
        point.y >= root.bottom - bandPx

    fun upwardPull(travel: Offset, slop: Float): Boolean =
        travel.y < -slop && kotlin.math.abs(travel.y) > kotlin.math.abs(travel.x) * 1.35f

    enum class ScopeFrame { Blocked, Rebase, Apply }

    /** One instance per existing arbiter sequence. Edge rejection ends only at all-up. */
    class Sequence {
        var edgeRejected = false
            private set
        private var wasBlocked = false

        fun frame(points: List<Offset>, root: Rect?, bandPx: Float, chromeBlocked: Boolean): ScopeFrame {
            if (root != null && points.any { inBottomBand(it, root, bandPx) }) edgeRejected = true
            val blocked = root == null || edgeRejected || chromeBlocked
            val result = when {
                blocked -> ScopeFrame.Blocked
                wasBlocked -> ScopeFrame.Rebase
                else -> ScopeFrame.Apply
            }
            wasBlocked = blocked
            return result
        }
    }
}
