package dev.phosphor.mobil3.ui

import androidx.compose.ui.graphics.Color
import java.io.File
import org.junit.Test
import kotlin.test.*

/** Numeric geometry and source wiring. These tests do not execute Compose pointer events. */
class SliderGeometryTest {
    @Test fun allFourRuleDomainsShareInsetHitAndDrawCoordinates() {
        val domains = listOf(0f to 240_000f, 0.3f to 3f, 1f to 30f, 0f to 1f)
        for ((min, max) in domains) for (density in listOf(1f, 2.625f, 3f)) {
            val g = SliderGeometry(240f * density, density)
            assertEquals(44, SliderGeometry.HIT_LANE_DP)
            assertEquals(2f * density, g.trackPx)
            assertEquals(8f * density, g.thumbPx)
            assertEquals(g.thumbPx / 2f, g.xAt(0f))
            assertEquals(240f * density - g.thumbPx / 2f, g.xAt(1f))
            for (fraction in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
                assertEquals(fraction, g.fractionAt(g.xAt(fraction)), 0.00001f)
                assertEquals(min + fraction * (max - min), g.valueAt(g.xAt(fraction), min, max), 0.0001f)
            }
        }
    }

    @Test fun tapsAtAndOutsideEndsClampAndCollapsedTracksStayFinite() {
        val g = SliderGeometry(100f, 1f)
        assertEquals(0f, g.fractionAt(-100f))
        assertEquals(1f, g.fractionAt(200f))
        assertEquals(3f, g.valueAt(50f, 3f, 3f))
        for (width in listOf(0f, 2f, 8f)) {
            val narrow = SliderGeometry(width, 1f)
            assertEquals(0f, narrow.fractionAt(100f))
            assertTrue(narrow.xAt(1f).isFinite())
        }
    }

    @Test fun rangeSelectsNearestInitialThumbWithLowerTieAndNeverCrosses() {
        val g = SliderGeometry(208f, 1f)
        assertEquals(0, g.nearestThumb(g.xAt(0.5f), 2f, 8f, 0f, 10f))
        assertEquals(1, g.nearestThumb(g.xAt(0.75f), 2f, 8f, 0f, 10f))
        assertEquals(8f to 8f, g.moveThumb(0, g.xAt(1f), 2f, 8f, 0f, 10f))
        assertEquals(2f to 2f, g.moveThumb(1, g.xAt(0f), 2f, 8f, 0f, 10f))
        assertEquals(0f to 8f, g.moveThumb(0, -100f, 2f, 8f, 0f, 10f))
        assertEquals(2f to 10f, g.moveThumb(1, 1000f, 2f, 8f, 0f, 10f))
        // Range callbacks use the normalized form after pointer geometry maps physical pixels.
        val unit = SliderGeometry(1f, 0f)
        assertEquals(g.nearestThumb(g.xAt(.7f), 2f, 8f, 0f, 10f), unit.nearestThumb(.7f, 2f, 8f, 0f, 10f))
    }

    @Test fun everyRoomUsesCurrentBeamTintWithRoomAccentFallback() {
        for (room in Rooms) {
            assertEquals(room.accent, sliderAccent(room.copy(beamAccent = Color.Unspecified)))
            for (beam in listOf(Color.Red, Color.Green, Color.Blue)) {
                assertEquals(beam, sliderAccent(room.copy(beamAccent = beam)))
            }
        }
    }

    @Test fun realRulesUseSharedGeometryAndOneRecognizerNotAnOverlayTapHandler() {
        val console = phase9Source("ui/Console.kt").substringAfter("fun SeekRule(").substringBefore("fun Console(")
        assertTrue(".height(SliderGeometry.HIT_LANE_DP.dp)" in console)
        assertTrue(".sliderTrack(p, frac)" in console)
        assertTrue(".consoleSeekGesture(" in console)
        assertTrue("onCancel = { scrub = -1f }" in console)
        val controls = phase9Source("ui/Controls.kt")
        assertTrue("SliderGeometry(size.width, density)" in controls)
        assertTrue("sliderAccent(p)" in controls)
        val lane = controls.substringAfter("internal fun SliderLane(").substringBefore("fun DragRuleInline(")
        assertTrue(".height(SliderGeometry.HIT_LANE_DP.dp)" in lane)
        assertTrue(".consoleSeekGesture(" in lane)
        assertFalse("detectTapGestures" in lane)
        val rules = phase9Source("ui/Sheets.kt").substringAfter("fun DragRule(").substringBefore("fun SourceSheet(")
        assertEquals(2, Regex("SliderLane\\(").findAll(rules).count())
        assertTrue("unit.nearestThumb" in rules)
        assertTrue("unit.moveThumb" in rules)
        assertFalse("detectHorizontalDragGestures" in rules)
        assertFalse("fun DragRuleInline(" in controls)
    }

    @Test fun seekRecognizerRetainsVerticalRejectionCurrentCallbacksAndCancelOnInterruptedScrub() {
        val seek = phase9Source("ui/Gestures.kt").substringAfter("fun Modifier.consoleSeekGesture(")
            .substringBefore("class RibbonState")
        assertTrue("SliderGeometry(size.width.toFloat(), density)" in seek)
        assertTrue("geometry.fractionAt(change.position.x)" in seek)
        assertTrue("abs(travel.x) > abs(travel.y) * 1.35f" in seek)
        assertTrue("pressed.size > 1" in seek)
        assertTrue("change.isConsumed" in seek)
        assertTrue("rememberUpdatedState(onStart)" in seek)
        assertTrue("if (finishedNormally) currentCommit(fraction)" in seek)
        assertTrue("else if (started) currentCancel()" in seek)
    }
}

internal fun phase9Source(path: String): String = listOf(
    File("src/main/kotlin/dev/phosphor/mobil3/$path"),
    File("app/src/main/kotlin/dev/phosphor/mobil3/$path"),
).first { it.isFile }.readText()
