package dev.phosphor.mobil3.ui

import org.junit.Test
import kotlin.test.*

/** Real snapshot state and numeric production policies plus source wiring, not Compose execution. */
class ControlsVisibilityPolicyTest {
    @Test fun defaultRetainsHideAndTapBehavior() {
        assertFalse(ControlsVisibilityPolicy.alwaysVisible(emptyMap<String, Any>()))
        assertFalse(ScopeUiState().controlsAlwaysVisible)
        assertFalse(ControlsVisibilityPolicy.afterHide(false))
        assertFalse(ControlsVisibilityPolicy.afterTap(true, false))
        assertTrue(ControlsVisibilityPolicy.afterTap(false, false))
        assertTrue(ControlsVisibilityPolicy.timeoutCanHide(false, 0, 0))
    }

    @Test fun enablingImmediatelyRevealsAndBlocksEveryHideReason() {
        assertTrue(ControlsVisibilityPolicy.visible(false, true))
        assertTrue(ControlsVisibilityPolicy.afterHide(true))
        assertTrue(ControlsVisibilityPolicy.afterTap(true, true))
        assertFalse(ControlsVisibilityPolicy.timeoutCanHide(true, 0, 0))
    }

    @Test fun actualStateRevisionRejectsOldTimerEvenAfterEnableThenDisable() {
        val state = ScopeUiState()
        val oldTimer = state.controlsVisibilityRevision
        state.controlsAlwaysVisible = true
        val enabledRevision = state.controlsVisibilityRevision
        assertTrue(enabledRevision > oldTimer)
        state.controlsAlwaysVisible = true
        assertEquals(enabledRevision, state.controlsVisibilityRevision)
        state.controlsAlwaysVisible = false
        assertFalse(ControlsVisibilityPolicy.timeoutCanHide(false, oldTimer, state.controlsVisibilityRevision))
        assertTrue(ControlsVisibilityPolicy.timeoutCanHide(false, state.controlsVisibilityRevision, state.controlsVisibilityRevision))
    }

    @Test fun sourceGatesBackTimeoutCancelledPullAndTapWithoutForcingOverSheets() {
        val screen = phase9Source("ui/PhosphorScreen.kt")
        assertFalse("consoleVisible = false" in screen)
        assertFalse("consoleVisible = !consoleVisible" in screen)
        assertEquals(3, Regex("ControlsVisibilityPolicy.afterHide\\(state.controlsAlwaysVisible\\)").findAll(screen).count())
        assertTrue("ControlsVisibilityPolicy.afterTap(consoleVisible, state.controlsAlwaysVisible)" in screen)
        assertTrue("LaunchedEffect(consoleShown, sheet, overflowComposed, state.controlsVisibilityRevision)" in screen)
        assertTrue("state.controlsAlwaysVisible, revision, state.controlsVisibilityRevision" in screen)
        assertTrue("visible = consoleShown && (sheet == Sheet.NONE || settingsPullActive)" in screen)
        assertTrue("sheet == Sheet.NONE && consoleShown && !state.controlsAlwaysVisible" in screen)
        val activity = phase9Source("MainActivity.kt")
        assertTrue("putBoolean(dev.phosphor.mobil3.ui.ControlsVisibilityPolicy.KEY, on)" in activity)
        assertTrue("ControlsVisibilityPolicy.alwaysVisible(p.all)" in activity)
    }

    @Test fun removedVolumeLeavesAndroidOwnershipAndExistingHeartbeat() {
        val activity = phase9Source("MainActivity.kt")
        val console = phase9Source("ui/Console.kt")
        for (source in listOf(activity, console, phase9Source("ui/ScopeUiState.kt"), phase9Source("ui/PhosphorScreen.kt"))) {
            for (removed in listOf("volumeFraction", "volumeFrac", "setVolume", "onVolume", "setStreamVolume")) {
                assertFalse(removed in source, "Removed volume path remains: $removed")
            }
        }
        assertFalse("Mono(\"VOL\"" in console)
        assertFalse("DragRuleInline" in phase9Source("ui/Controls.kt"))
        assertTrue(".onSizeChanged { onHeightChanged(it.height) }" in console)
        assertTrue("FlatKey(\"MODE\"" in console)
        assertTrue("FlatKey(\"SRC\"" in console)
        assertTrue("OverflowHandleKey(" in console)
        val tick = activity.substringAfter("private val uiTick = object : Runnable {")
            .substringBefore("override fun onNewIntent(")
        val run = tick.substringAfter("override fun run() {").trimStart()
        val visibleGuard = "if (!activityStarted || !ui.presentationVisible || ui.pip || activityDestroyed) return"
        assertTrue(run.startsWith(visibleGuard))
        assertTrue(run.substringAfter(visibleGuard).trimStart().startsWith("refreshRotationAuthority()"))
        assertEquals(1, Regex("tick.postDelayed\\(this, 500\\)").findAll(activity).count())
        assertEquals(3, Regex("tick.post\\(uiTick\\)").findAll(activity).count())
        assertEquals(4, Regex("tick.removeCallbacks\\(uiTick\\)").findAll(activity).count())
        assertEquals(2, Regex("tick.removeCallbacks\\(uiTick\\)\\s+if \\(ui.presentationVisible\\) tick.post\\(uiTick\\)").findAll(activity).count())
        assertTrue("tick.removeCallbacks(uiTick)" in activity)
        assertTrue("if (PictureInPicturePolicy.shouldRebindSurface(leaving, activityStarted))" in activity)
        assertTrue("if (!ui.pip && activityStarted && ui.presentationVisible) tick.post(uiTick)" in activity)
    }

    @Test fun viewportUsesRemainingSafeHeightWithoutShrinkingSliderLanes() {
        for (density in listOf(1f, 2.625f, 3f)) {
            // 360dp frame, 24dp safe top, 200dp measured titled/seekable console, 8dp gap.
            val viewport = OverflowPopoutPolicy.viewportHeight(336f * density, 200f * density, 8f * density)
            assertEquals(128f * density, viewport, 0.001f)
            assertEquals(360f * density, viewport + (24f + 200f + 8f) * density, 0.001f)
            assertEquals(44, SliderGeometry.HIT_LANE_DP)
        }
        assertEquals(578f, OverflowPopoutPolicy.viewportHeight(776f, 190f, 8f))
        assertEquals(78f, OverflowPopoutPolicy.viewportHeight(336f, 250f, 8f))
        assertEquals(0f, OverflowPopoutPolicy.viewportHeight(198f, 200f, 8f))
        assertEquals(0f, OverflowPopoutPolicy.viewportHeight(0f, 0f, 8f))
    }

    @Test fun consoleReportsOccupiedHeightBeforeInsetsAndMargins() {
        // Producer wiring is checked separately from the numeric viewport consumer.
        // This does not execute Compose measurement or Android insets.
        val outer = phase9Source("ui/Console.kt").substringAfter("fun Console(")
            .substringBefore("contentAlignment = Alignment.BottomCenter")
        val observer = outer.indexOf(".onSizeChanged { onHeightChanged(it.height) }")
        val insets = outer.indexOf(".windowInsetsPadding(")
        val margin = outer.indexOf(".padding(start = Dim.cardMarginH")
        assertTrue(observer >= 0 && insets > observer && margin > insets)
        assertEquals(1, Regex("onHeightChanged\\(it.height\\)").findAll(outer).count())
        for (density in listOf(1f, 2.625f, 3f)) {
            for (bottomInset in listOf(0f, 24f, 48f)) {
                val occupied = (190f + 10f + bottomInset) * density
                val gap = 8f * density
                val viewport = OverflowPopoutPolicy.viewportHeight(336f * density, occupied, gap)
                val consoleTop = 360f * density - occupied
                val popoutBottom = 24f * density + viewport
                assertEquals(gap, consoleTop - popoutBottom, 0.001f)
            }
        }
    }

    @Test fun safeInsetsFollowEveryCounterclockwiseChromeQuadrant() {
        val physical = listOf(11, 23, 37, 49)
        assertEquals(physical, OverflowPopoutPolicy.rotatedInsets(physical, 0))
        assertEquals(listOf(49, 11, 23, 37), OverflowPopoutPolicy.rotatedInsets(physical, 1))
        assertEquals(listOf(37, 49, 11, 23), OverflowPopoutPolicy.rotatedInsets(physical, 2))
        assertEquals(listOf(23, 37, 49, 11), OverflowPopoutPolicy.rotatedInsets(physical, 3))
        assertEquals(physical, OverflowPopoutPolicy.rotatedInsets(physical, 4))
        assertEquals(listOf(23, 37, 49, 11), OverflowPopoutPolicy.rotatedInsets(physical, -1))
    }

    @Test fun appliedSafeDrawingPolicyMapsAsymmetricPhysicalEdgesInAllQuadrants() {
        val physical = listOf(11, 23, 37, 49)
        val expected = listOf(
            listOf(11, 23, 37, 49), listOf(49, 11, 23, 37),
            listOf(37, 49, 11, 23), listOf(23, 37, 49, 11),
        )
        for (quadrant in 0..3) {
            assertEquals(expected[quadrant], ChromeInsetPolicy.safeInsets(
                physical, CornerRadii(), 16f, 6f, quadrant,
            ))
            assertEquals(expected[quadrant], OverflowPopoutPolicy.rotatedInsets(physical, quadrant))
        }
    }

    @Test fun oddFramesSwapContentGuttersBeforeCornerClearanceAndMapTheUnionOnce() {
        // Top-left radius 100, local gutters H0/V20. The 60/80/100 triangle gives
        // physical L40/T80 at q0, but L80/T40 when the physical gutters swap.
        // Right/bottom system insets remain present beside those corner clearances.
        val expected = listOf(
            listOf(40, 80, 7, 11), listOf(11, 80, 40, 7),
            listOf(7, 11, 40, 80), listOf(40, 7, 11, 80),
        )
        for (quadrant in 0..3) {
            assertEquals(expected[quadrant], ChromeInsetPolicy.safeInsets(
                listOf(0, 0, 7, 11), CornerRadii(topLeft = 100), 0f, 20f, quadrant,
            ))
        }
        assertEquals(listOf(0, 100, 7, 11), ChromeInsetPolicy.safeInsets(
            listOf(0, 100, 7, 11), CornerRadii(topLeft = 100), 0f, 20f, 0,
        ))
        assertEquals(listOf(30, 20, 40, 40), ChromeInsetPolicy.safeInsets(
            listOf(0, 0, 0, 0), CornerRadii(10, 20, 30, 40), 0f, 0f, 0,
        ))
        assertEquals(listOf(0, 0, 0, 0), ChromeInsetPolicy.safeInsets(
            listOf(0, 0, 0, 0), CornerRadii(10, 20, 30, 40), 40f, 40f, 0,
        ))
    }

    @Test fun nestedSheetUsesTotalChromeAndSheetFrameIncludingOddGutters() {
        val expectedByTotal = listOf(
            listOf(40, 80, 7, 11), listOf(11, 80, 40, 7),
            listOf(7, 11, 40, 80), listOf(40, 7, 11, 80),
        )
        val totalFrames = listOf(
            listOf(0, 1, 2, 3), listOf(1, 2, 3, 0),
            listOf(2, 3, 0, 1), listOf(3, 0, 1, 2),
        )
        for (chrome in 0..3) for (sheet in 0..3) {
            assertEquals(expectedByTotal[totalFrames[chrome][sheet]], ChromeInsetPolicy.safeInsets(
                listOf(0, 0, 7, 11), CornerRadii(topLeft = 100), 0f, 20f, chrome + sheet,
            ), "chrome=$chrome sheet=$sheet")
        }
    }

    @Test fun sourceOnlyEveryChromeInsetCallerReceivesItsActualFrameAndPhysicalEdges() {
        val screen = phase9Source("ui/PhosphorScreen.kt")
        assertTrue(screen.contains("CompositionLocalProvider(LocalChromeInsetQuadrant provides chromeQuadrant) {"))
        val chrome = screen.substringAfter("CompositionLocalProvider(LocalChromeInsetQuadrant provides chromeQuadrant) {")
        assertTrue(chrome.contains("Box(Modifier.fillMaxSize().uprightRotate(chromeQuadrant))"))
        assertTrue(chrome.contains(".windowInsetsPadding(chromeSafeDrawingInsets(18.dp, 18.dp))"))
        assertTrue(chrome.contains("StatusBand("))
        assertTrue(chrome.contains("Console("))
        val sheets = chrome.substringAfter("// Layer 2: sheets")
        assertTrue(sheets.contains("LocalChromeInsetQuadrant provides (chromeQuadrant + sheetQuadrant)"))
        assertTrue(sheets.contains("Box(Modifier.uprightRotate(sheetQuadrant))"))
        assertTrue(phase9Source("ui/Sheets.kt").contains("chromeSafeDrawingInsets(Dim.cardMarginH, Dim.cardMarginBottom)"))
        val console = phase9Source("ui/Console.kt")
        assertTrue(console.contains("chromeSafeDrawingInsets(16.dp, 6.dp)"))
        assertTrue(console.contains("chromeSafeDrawingInsets(Dim.cardMarginH, Dim.cardMarginBottom)"))
        assertTrue(console.contains("ChromeInsetPolicy.rotatedInsets(physical, quadrant)"))
        val insets = phase9Source("ui/Insets.kt")
        assertTrue(insets.contains("LocalChromeInsetQuadrant = compositionLocalOf { 0 }"))
        assertTrue(insets.contains("appliedQuadrant: Int = LocalChromeInsetQuadrant.current"))
        assertTrue(insets.contains("val insets = ChromeInsetPolicy.safeInsets("))
        assertTrue(insets.contains("quadrant = appliedQuadrant"))
        // getLeft/getRight resolve physical edges using the actual layout direction.
        assertTrue(insets.contains("val layoutDirection = LocalLayoutDirection.current"))
        assertTrue(insets.contains("safeDrawing.getLeft(density, layoutDirection)"))
        assertTrue(insets.contains("safeDrawing.getRight(density, layoutDirection)"))
        assertTrue(insets.contains("WindowInsets(left = insets[0], top = insets[1], right = insets[2], bottom = insets[3])"))
        assertTrue(insets.contains("view.addOnLayoutChangeListener(listener)"))
        assertTrue(insets.contains("onDispose { view.removeOnLayoutChangeListener(listener) }"))
        // Source-only propagation guard, not Compose measurement or Android RTL/cutout execution.
    }

    @Test fun dismissalReversalConsumesOnlyDisplacementAndRetainsExistingCloseThreshold() {
        assertEquals(0f, OverflowPopoutPolicy.reverseDelta(-20f, 0f))
        assertEquals(0f, OverflowPopoutPolicy.reverseDelta(20f, 12f))
        assertEquals(-7f, OverflowPopoutPolicy.reverseDelta(-7f, 12f))
        assertEquals(-12f, OverflowPopoutPolicy.reverseDelta(-20f, 12f))
        assertFalse(OverflowPopoutPolicy.shouldClose(0f, 128f))
        assertFalse(OverflowPopoutPolicy.shouldClose(40f, 100f))
        assertTrue(OverflowPopoutPolicy.shouldClose(40.1f, 100f))
        assertTrue(OverflowPopoutPolicy.shouldClose(128f, 128f))
        assertFalse(OverflowPopoutPolicy.shouldClose(1f, 0f))
    }

    @Test fun popoutMeasuresInsideRotatedFrameAndKeepsAnchoringAndSingleScreenClosePath() {
        val screen = phase9Source("ui/PhosphorScreen.kt")
        val popout = screen.substringAfter("// The ⋯ overflow popout").substringBefore("// Layer 2: sheets")
        assertTrue(screen.indexOf(".uprightRotate(chromeQuadrant)") < screen.indexOf("// The ⋯ overflow popout"))
        assertTrue("chromeSafeDrawingInsets(0.dp, 0.dp, appliedQuadrant = 0)" in popout)
        assertTrue("OverflowPopoutPolicy.rotatedInsets(" in popout)
        assertTrue("chromeQuadrant," in popout)
        assertTrue("BoxWithConstraints(" in popout)
        assertTrue(".windowInsetsPadding(WindowInsets(left = insets[0], top = insets[1], right = insets[2]))" in popout)
        assertTrue("maxHeight.value, consoleHeight.value, Dim.popoutGap.value" in popout)
        assertTrue("Box(Modifier.padding(bottom = consoleHeight + Dim.popoutGap))" in popout)
        assertTrue("maxHeight = viewportHeight" in popout)
        assertTrue("onPictureInPicture = { actions.enterPictureInPicture() }" in popout)
        assertTrue("onPipAutoEnter = { actions.setPipAutoEnter(!state.pipAutoEnter) }" in popout)
        assertTrue("onRequestClose = { closeOverflow(Sheet.NONE) }" in popout)
        assertTrue("BackHandler(enabled = overflowComposed) { closeOverflow(Sheet.NONE) }" in screen)
        assertTrue("!overflowComposed && !state.controlsAlwaysVisible" in screen)
    }
}
