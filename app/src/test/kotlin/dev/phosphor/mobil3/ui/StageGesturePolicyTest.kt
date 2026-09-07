package dev.phosphor.mobil3.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import java.io.File
import org.junit.Test
import kotlin.test.*
import dev.phosphor.mobil3.ui.StageGesturePolicy.ScopeFrame.*

/** Numeric production policy and source reachability. This does not execute Compose gestures. */
class StageGesturePolicyTest {
    private val root = Rect(0f, 0f, 400f, 800f)
    private val clear = listOf(Offset(200f, 300f))
    private val cardRect = Rect(100f, 500f, 300f, 600f)
    private val card = StageChromeBounds.Card.Console

    private fun mounted(bounds: Rect = cardRect): Pair<StageChromeBounds, Any> {
        val chrome = StageChromeBounds()
        val owner = Any()
        chrome.mount(card, owner, 1000L)
        chrome.sample(card, owner, bounds, 1000L)
        return chrome to owner
    }

    @Test fun noTimestampDoesNotInventAStartupFreeze() {
        assertFalse(StageChromeBounds().blocks(clear, 24f, 0L))
    }

    @Test fun observationPreservesFinalMotionTimestampAndNeverExtendsTheGuard() {
        val (chrome, owner) = mounted()
        chrome.motion(card, owner, true, 2000L)
        chrome.motion(card, owner, false, 2100L)
        chrome.sample(card, owner, cardRect, 2200L)
        assertTrue(chrome.observation().contains("Console=2100,false,true,100.0,500.0,300.0,600.0"))
        repeat(5) { chrome.observation() }
        assertTrue(chrome.blocks(clear, 24f, 2433L))
        assertFalse(chrome.blocks(clear, 24f, 2434L))
        chrome.dismiss(card, owner, 3000L)
        assertTrue(chrome.observation().contains("Console=3000,false,false,"))
        chrome.sample(card, Any(), cardRect, 3200L)
        assertTrue(chrome.blocks(clear, 24f, 3333L))
        assertFalse(chrome.blocks(clear, 24f, 3334L))
    }

    @Test fun movementBlocksThrough332And333ButNot334Milliseconds() {
        val (chrome, owner) = mounted()
        chrome.sample(card, owner, cardRect.translate(0f, -20f), 2000L)
        assertTrue(chrome.blocks(clear, 24f, 2332L))
        assertTrue(chrome.blocks(clear, 24f, 2333L))
        assertFalse(chrome.blocks(clear, 24f, 2334L))
    }

    @Test fun equalGeometryDoesNotExtendTheDeadline() {
        val (chrome, owner) = mounted()
        chrome.sample(card, owner, cardRect, 1333L)
        assertFalse(chrome.blocks(clear, 24f, 1334L))
    }

    @Test fun openingWithoutMeasurementFailsClosed() {
        val chrome = StageChromeBounds()
        chrome.mount(card, Any(), 1000L)
        assertTrue(chrome.blocks(clear, 24f, 5000L))
    }

    @Test fun activeMotionBlocksAcrossStationaryAnimationStepsAndCompletionRestartsTime() {
        val (chrome, owner) = mounted()
        chrome.motion(card, owner, true, 1100L)
        assertTrue(chrome.blocks(clear, 24f, 9000L))
        chrome.motion(card, owner, false, 9000L)
        chrome.motion(card, owner, false, 9200L)
        assertTrue(chrome.blocks(clear, 24f, 9333L))
        assertFalse(chrome.blocks(clear, 24f, 9334L))
    }

    @Test fun everyCardRetainsExitAndDisposalSettling() {
        for (slot in StageChromeBounds.Card.entries) {
            val chrome = StageChromeBounds()
            val owner = Any()
            chrome.mount(slot, owner, 1000L)
            chrome.sample(slot, owner, cardRect, 1000L)
            chrome.motion(slot, owner, true, 1500L)
            chrome.sample(slot, owner, cardRect.translate(10f, 20f), 2000L)
            assertTrue(chrome.blocks(clear, 24f, 5000L))
            chrome.dismiss(slot, owner, 5000L)
            assertTrue(chrome.blocks(clear, 24f, 5332L))
            assertTrue(chrome.blocks(clear, 24f, 5333L))
            assertFalse(chrome.blocks(listOf(Offset(200f, 550f)), 24f, 5334L))
        }
    }

    @Test fun staleSheetRemovalAndSamplingCannotEraseReplacement() {
        val chrome = StageChromeBounds()
        val old = Any()
        val replacement = Any()
        val sheet = StageChromeBounds.Card.Sheet
        chrome.mount(sheet, old, 1000L)
        chrome.sample(sheet, old, cardRect, 1000L)
        chrome.mount(sheet, replacement, 2000L)
        chrome.sample(sheet, replacement, cardRect, 2000L)
        chrome.dismiss(sheet, old, 3000L)
        chrome.sample(sheet, old, Rect.Zero, 3000L)
        chrome.motion(sheet, old, true, 3000L)
        assertTrue(chrome.blocks(listOf(Offset(200f, 550f)), 24f, 4000L))
        assertFalse(chrome.blocks(clear, 24f, 4000L))
    }

    @Test fun marginUsesPhysicalPixelsAtSeveralDensitiesAndInclusiveEdges() {
        for (density in listOf(1f, 2.625f, 3f)) {
            val (chrome, _) = mounted()
            val m = StageGesturePolicy.MARGIN_DP * density
            for (point in listOf(
                Offset(100f - m, 550f), Offset(300f + m, 550f),
                Offset(200f, 500f - m), Offset(200f, 600f + m),
                Offset(100f - m, 500f - m), Offset(300f + m, 600f + m),
            )) assertTrue(chrome.blocks(listOf(point), m, 2000L), "density=$density point=$point")
            for (point in listOf(
                Offset(100f - m - .01f, 550f), Offset(300f + m + .01f, 550f),
                Offset(200f, 500f - m - .01f), Offset(200f, 600f + m + .01f),
            )) assertFalse(chrome.blocks(listOf(point), m, 2000L))
        }
    }

    @Test fun anyPointerIncludingThirdAndPermutationsTouchesChrome() {
        val (chrome, _) = mounted()
        val inside = Offset(200f, 550f)
        for (index in 0..2) {
            val points = mutableListOf(Offset(10f, 300f), Offset(20f, 300f))
            points.add(index, inside)
            assertTrue(chrome.blocks(points, 24f, 2000L))
        }
    }

    @Test fun bottomBoundaryAndOvershootAreInclusiveAtAllDensities() {
        for (density in listOf(1f, 2.625f, 3f)) {
            val band = 88f * density
            assertTrue(StageGesturePolicy.inBottomBand(Offset(20f, 800f - band), root, band))
            assertTrue(StageGesturePolicy.inBottomBand(Offset(20f, 900f), root, band))
            assertFalse(StageGesturePolicy.inBottomBand(Offset(20f, 800f - band - .01f), root, band))
        }
    }

    @Test fun firstSecondAndThirdPointerEdgeEntryLatchesUntilNewSequence() {
        for (index in 0..2) {
            val sequence = StageGesturePolicy.Sequence()
            assertEquals(Apply, sequence.frame(clear, root, 88f, false))
            val points = mutableListOf(Offset(10f, 300f), Offset(20f, 300f))
            points.add(index, Offset(200f, 712f))
            assertEquals(Blocked, sequence.frame(points, root, 88f, false))
            assertTrue(sequence.edgeRejected)
            repeat(3) { assertEquals(Blocked, sequence.frame(clear, root, 88f, false)) }
            assertEquals(Apply, StageGesturePolicy.Sequence().frame(clear, root, 88f, false))
        }
    }

    @Test fun rotatedFixturesIdentifyPhysicalBottomNotLocalBottom() {
        // Full-bleed transform oracles only. Actual localPositionOf remains a live check.
        for (q in 0..3) {
            val w = if (q % 2 == 0) 400f else 800f
            val h = if (q % 2 == 0) 800f else 400f
            fun physical(p: Offset): Offset = when (q) {
                0 -> p
                1 -> Offset(p.y, w - p.x)
                2 -> Offset(w - p.x, h - p.y)
                else -> Offset(h - p.y, p.x)
            }
            val bottom = when (q) {
                0 -> Offset(200f, h - 88f)
                1 -> Offset(88f, 200f)
                2 -> Offset(200f, 88f)
                else -> Offset(w - 88f, 200f)
            }
            assertEquals(Blocked, StageGesturePolicy.Sequence().frame(listOf(physical(bottom)), root, 88f, false))
            val localBottomCenter = physical(Offset(w / 2f, h - 1f))
            assertEquals(q == 0, StageGesturePolicy.inBottomBand(localBottomCenter, root, 88f))
        }
    }

    @Test fun blockedToClearFrameIsBaselineOnlyThenMotionCanApply() {
        val sequence = StageGesturePolicy.Sequence()
        assertEquals(Apply, sequence.frame(clear, root, 88f, false))
        repeat(3) { assertEquals(Blocked, sequence.frame(clear, root, 88f, true)) }
        assertEquals(Rebase, sequence.frame(clear, root, 88f, false))
        assertEquals(Apply, sequence.frame(clear, root, 88f, false))
        assertEquals(Blocked, sequence.frame(clear, null, 88f, false))
        assertEquals(Rebase, sequence.frame(clear, root, 88f, false))
    }

    @Test fun slowUpwardPullIntentIsIndependentOfScopeRejection() {
        val sequence = StageGesturePolicy.Sequence()
        assertEquals(Blocked, sequence.frame(listOf(Offset(200f, 750f)), root, 88f, true))
        assertFalse(StageGesturePolicy.upwardPull(Offset(0f, -5f), 10f))
        assertTrue(StageGesturePolicy.upwardPull(Offset(0f, -11f), 10f))
        assertFalse(StageGesturePolicy.upwardPull(Offset(0f, 20f), 10f))
        assertFalse(StageGesturePolicy.upwardPull(Offset(30f, -20f), 10f))
        assertEquals(Blocked, sequence.frame(clear, root, 88f, false))
    }

    @Test fun soleCallerMapsAllPressedPointersAndGuardsEveryScopeMutation() {
        val screen = source("ui/PhosphorScreen.kt")
        assertEquals(1, Regex("\\.stageGestures\\(").findAll(screen).count())
        assertTrue("val stageGeometry = remember { StageGeometry() }" in screen)
        assertTrue(screen.indexOf("val stageGeometry =") < screen.indexOf("if (sheet == Sheet.NONE || bottomEdgePullActive)"))
        assertTrue("stageGeometry.position(stageGeometry.stage, local)" in screen)
        assertTrue("stageGeometry.refresh(now)" in screen)
        assertTrue("stageGeometry.bounds.blocks(" in screen)
        val owner = source("ui/Gestures.kt").substringAfter("fun Modifier.stageGestures(").substringBefore("fun Modifier.bottomBloomOverscroll")
        assertTrue("val policy = StageGesturePolicy.Sequence()" in owner)
        assertTrue("pressed.mapNotNull { host.physicalPosition(it.position) }" in owner)
        assertTrue("physicalPoints.size == pressed.size" in owner)
        val guard = owner.indexOf("if (scopeFrame != StageGesturePolicy.ScopeFrame.Apply)")
        assertTrue(guard > 0)
        for (setter in listOf("host.setGainAbsolute(", "host.orbitBy(", "host.dollyBy(", "host.modeStep(", "host.setGlowAbsolute(")) {
            assertTrue(owner.indexOf(setter) > guard, setter)
        }
        val rebase = owner.substringAfter("if (scopeFrame != StageGesturePolicy.ScopeFrame.Apply)").substringBefore("val a = pressed[0].position")
        for (baseline in listOf("gain = host.currentGain()", "glow = host.currentGlow()", "origin = pressed[0].position", "twoStartDist = (pressed[0].position - pressed[1].position).getDistance()", "pinchScale.reset(twoStartDist)", "twoOrigin = (pressed[0].position + pressed[1].position) / 2f", "continue")) {
            assertTrue(baseline in rebase, baseline)
        }
        assertFalse("physicalOrigin =" in rebase)
    }

    @Test fun mode6PrecedesScopeGuardAndMultitouchRetiresWithoutTakeover() {
        val owner = source("ui/Gestures.kt").substringAfter("fun Modifier.stageGestures(").substringBefore("fun Modifier.bottomBloomOverscroll")
        assertTrue(owner.indexOf("StageGesturePolicy.upwardPull(travel, slop)") < owner.indexOf("if (scopeFrame !="))
        assertTrue("host.bottomPullArmed() &&" in owner)
        assertTrue("pressed.size >= 2 && (bottomCandidate || mode == 6)" in owner)
        val cancel = owner.substringAfter("pressed.size >= 2 && (bottomCandidate || mode == 6)").substringBefore("val physicalPoints")
        assertTrue("host.cancelBottomChromePull()" in cancel)
        assertTrue("mode = 7" in cancel)
        assertTrue("if (pressed.size == 1 && mode in 2..5) mode = 7" in cancel)
        assertTrue("if (mode == 7)" in cancel)
        assertTrue("host.releaseBottomChromePull(velocity.calculateVelocity().y)" in owner)
        assertTrue("physicalOrigin.y <= physicalRoot.top + Dim.topGestureBand.toPx()" in owner)
        assertFalse("size.height - Dim.bottomGestureBand" in owner)
    }

    @Test fun realCardsReportAfterTheirTransformsAndBeforePadding() {
        val screen = source("ui/PhosphorScreen.kt")
        assertTrue("reference.localPositionOf(source, point)" in screen)
        assertTrue("Offset.Zero, Offset(w, 0f), Offset(w, h), Offset(0f, h)" in screen)
        assertTrue("takeIf { it.isAttached }" in screen)
        assertTrue("onDispose { geometry.dismiss(card, measurement) }" in screen)
        assertTrue("while (currentMoving)" in screen)
        assertTrue("chromeMoving = transition.isRunning" in screen)
        val console = source("ui/Console.kt")
        assertTrue(console.indexOf("StageChromeBounds.Card.Console") < console.indexOf(".padding(horizontal = Dim.consolePadH"))
        val overflow = console.substringAfter("fun OverflowPopout(")
        assertTrue(overflow.indexOf("translationY =") < overflow.indexOf("StageChromeBounds.Card.Overflow"))
        assertTrue(overflow.indexOf("StageChromeBounds.Card.Overflow") < overflow.indexOf(".padding(Dim.popoutPad)"))
        val sheet = source("ui/Sheets.kt").substringAfter("fun SheetHost(").substringBefore("fun DragRule(")
        assertTrue(sheet.indexOf("translationY =") < sheet.indexOf("StageChromeBounds.Card.Sheet"))
        assertTrue(sheet.indexOf("StageChromeBounds.Card.Sheet") < sheet.indexOf(".padding(Dim.sheetPad)"))
        assertTrue("!openState.isIdle || dismissOffset.isRunning" in sheet)
    }

    private fun source(path: String): String = phase8Source(path)
}

internal fun phase8Source(path: String): String {
    var directory = File("").absoluteFile
    repeat(5) {
        for (prefix in listOf("app/src/main/kotlin/dev/phosphor/mobil3/", "src/main/kotlin/dev/phosphor/mobil3/")) {
            val candidate = File(directory, prefix + path)
            if (candidate.isFile) return candidate.readText()
        }
        directory = directory.parentFile ?: directory
    }
    error("Source unavailable: $path. Fix: run from the project Gradle wrapper.")
}
