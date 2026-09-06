package dev.phosphor.mobil3.ui

/** Entry edges in the applied sheet frame, before its outer rotation. */
enum class SheetEntry {
    FROM_BOTTOM,
    FROM_TOP,
    /** Viewer-right in both UI-locked landscape orientations. */
    FROM_EDGE,
}

object SheetEntryPolicy {

    fun entry(
        landscape: Boolean,
        uiPlacementLocked: Boolean,
        sheetQuadrant: Int = 0,
    ): SheetEntry = when {
        landscape && uiPlacementLocked -> SheetEntry.FROM_EDGE
        uiPlacementLocked && Math.floorMod(sheetQuadrant, 4) == 2 -> SheetEntry.FROM_TOP
        else -> SheetEntry.FROM_BOTTOM
    }

    /** A committed gesture continues local +Y. Later requests cannot redirect it. */
    fun exit(entry: SheetEntry, fromDrag: Boolean, committed: SheetEntry?): SheetEntry =
        committed ?: if (fromDrag) SheetEntry.FROM_BOTTOM else entry

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
