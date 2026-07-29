package dev.phosphor.mobil3.ui

/**
 * Where a sheet enters from, and along which axis.
 *
 * Extracted from `SheetHost` so the rule can be checked on the host. The composable
 * needs a running Compose tree, but the decision is three booleans, and it is the
 * decision Ben reported twice: a card that arrives from an edge it has no relationship
 * to reads as arriving from nowhere.
 *
 * The principle: a card must appear to come OUT OF the transport bar the finger
 * touched. Where the bar sits depends on whether UI placement is locked, so the entry
 * has to follow it.
 */
enum class SheetEntry {
    /** Rises from the bottom, centred. Portrait, and unlocked landscape. */
    FROM_BOTTOM,

    /** Slides in from the anchored edge. Locked landscape only. */
    FROM_EDGE,
}

object SheetEntryPolicy {

    /**
     * Portrait always rises. Landscape depends on the lock:
     *
     * - LOCKED: the phone is held sideways and the console is pinned to an edge, so the
     *   card slides in along that edge.
     * - UNLOCKED: the console is centred, so a card entering from a side edge has no
     *   relationship to what was touched. It rises, exactly like portrait.
     */
    fun entry(landscape: Boolean, uiPlacementLocked: Boolean): SheetEntry =
        if (landscape && uiPlacementLocked) SheetEntry.FROM_EDGE else SheetEntry.FROM_BOTTOM

    /** True when the reveal animates X rather than Y. */
    fun animatesHorizontally(landscape: Boolean, uiPlacementLocked: Boolean): Boolean =
        entry(landscape, uiPlacementLocked) == SheetEntry.FROM_EDGE

    /**
     * The pull travel is measured along the axis the card actually moves on.
     *
     * Using height for a sideways card made the drag feel like it had to cover the whole
     * screen before anything appeared.
     */
    fun travelPx(landscape: Boolean, uiPlacementLocked: Boolean, widthPx: Int, heightPx: Int): Int =
        if (animatesHorizontally(landscape, uiPlacementLocked)) widthPx else heightPx
}
