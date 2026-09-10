package dev.phosphor.mobil3.ui

import dev.phosphor.mobil3.settings.appearance.AppearanceContrast
import dev.phosphor.mobil3.settings.appearance.CuratedAppearances
import org.junit.Assert.*
import org.junit.Test

class StageReadabilityTest {
    @Test fun fourFamiliesKeepAuthoredBytesAndReadableBlackPlotText() {
        for (value in CuratedAppearances.all) {
            val before = value.copy()
            val ink = StageReadability.plotInk(value.colors.ink)
            assertTrue(AppearanceContrast.ratio(ink, 0) >= 4.5)
            if (AppearanceContrast.ratio(value.colors.ink, 0) >= 4.5) assertEquals(value.colors.ink, ink)
            assertEquals(before, value)
        }
        assertEquals(0xffffff, StageReadability.plotInk(CuratedAppearances.light.colors.ink))
    }

    @Test fun statusUsesActualMeasuredWidthsNotAssumedLinearFontScaling() {
        // Pixel inputs to the actual layout policy. TextMeasurer supplies real font-scale widths.
        assertFalse(StageReadability.stackStatus(800, 360, 400, 33))
        assertTrue(StageReadability.stackStatus(792, 360, 400, 33))
        assertFalse(StageReadability.stackStatus(793, 360, 400, 33))
        assertTrue(StageReadability.stackStatus(800, 500, 450, 33))
        assertTrue(StageReadability.stackStatus(600, 300, 280, 33))
        for (height in listOf(60f, 96f, 140f)) {
            assertTrue(StageReadability.signalTopDp(true, height) >= height + 8f)
        }
        assertEquals(88f, StageReadability.signalTopDp(false, 140f))
        assertEquals(104f, StageReadability.signalTopDp(true, 96f))
        assertEquals(148f, StageReadability.signalTopDp(true, 140f))
    }

    @Test fun cornerDesignatorDoesNotMoveMainLegendAndGrowsOnlyWhenBoxesIntersect() {
        // Unscaled and1.3/2-sized measured boxes, including the wide font2 stacked row.
        assertEquals(56, StageReadability.legendHeight(56, 56, 26, 26, 12, 12, 3, 2, 10))
        assertEquals(48, StageReadability.legendHeight(48, 64, 34, 17, 12, 12, 3, 2, 10))
        assertEquals(60, StageReadability.legendHeight(48, 67, 44, 20, 16, 15, 3, 2, 10))
        assertEquals(48, StageReadability.legendHeight(48, 306, 68, 28, 24, 24, 3, 2, 10))
        val height = StageReadability.legendHeight(48, 67, 44, 20, 16, 15, 3, 2, 10)
        val mainTop = (height - 20) / 2
        assertTrue(mainTop >= 3 + 15 + 2)
        assertEquals(height / 2, mainTop + 10)
    }

    @Test fun focusKeepsFullPassingAccentAndHasMarginAgainstItsOnlyAdjacentFill() {
        for (value in CuratedAppearances.all) {
            val tokens = ConsoleTactileTokens.from(value)
            assertTrue(AppearanceContrast.ratio(tokens.focusRing, tokens.well) >= 4.6)
            if (AppearanceContrast.ratio(value.colors.accent, tokens.well) >= 4.6)
                assertEquals(value.colors.accent, tokens.focusRing)
        }
    }
}
