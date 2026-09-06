package dev.phosphor.mobil3.ui

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
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

    @Test fun sourceKeepsOnlyExistingQueueJumpAndConsoleVolumeInsteadOfDeckUi() {
        val sheets = phase9Source("ui/Sheets.kt")
        val source = sheets.substringAfter("fun SourceSheet(").substringBefore("fun ModeSheet(")
        assertTrue("itemsIndexed(state.queueTitles)" in source)
        assertTrue("i == state.queueIndex" in source)
        assertTrue("actions.jumpToQueue(i)" in source)
        assertFalse("SeekRule(" in source)
        assertFalse("StoneKey(if (state.playing)" in source)
        val screen = phase9Source("ui/PhosphorScreen.kt")
        assertTrue("override fun jumpToQueue(index: Int) = actions.jumpToQueue(index)" in screen)
        assertFalse("volumeFrac = { actions.volumeFrac() }" in screen)
        assertTrue("onVolume = { actions.setVolume(it) }" in screen)
        assertTrue("DragRuleInline(" in phase9Source("ui/Console.kt"))
        for (text in listOf(screen, sheets, phase9Source("ui/Console.kt"), phase9Source("ui/Glyphs.kt"))) {
            for (removed in listOf("Sheet.DECK", "DeckSheet", "SettingsGlyph.Deck", "onDeck")) assertFalse(removed in text)
        }
        assertTrue("deckOpen" in phase9Source("PhosphorNative.kt"))
    }

    @Test fun suppliedVolumeInvalidatesActualPinnedPausedStateWithoutSliderInput() {
        val state = ScopeUiState().apply {
            controlsAlwaysVisible = true
            playing = false
        }
        val observer = SnapshotStateObserver { it() }
        val scope = Any()
        var invalidations = 0
        val onChanged: (Any) -> Unit = { invalidations++ }
        var displayed = -1f
        Snapshot.sendApplyNotifications()
        observer.start()
        try {
            for (supplied in listOf(0.2f, 0.8f, 0f, 1f)) {
                observer.observeReads(scope, onChanged) {
                    displayed = state.volumeFraction
                }
                val before = invalidations
                Snapshot.withMutableSnapshot { state.volumeFraction = supplied }
                assertEquals(before + 1, invalidations)
                observer.observeReads(scope, onChanged) {
                    displayed = state.volumeFraction
                }
                assertEquals(supplied, displayed)
                assertFalse(state.playing)
                assertTrue(state.controlsAlwaysVisible)
            }
        } finally {
            observer.stop()
            observer.clear()
        }
    }

    @Test fun volumeUsesExistingLifecycleHeartbeatAndImmediateReadbackWithoutChangingCubicBackend() {
        val activity = phase9Source("MainActivity.kt")
        val publish = "ui.volumeFraction = volumeFrac()"
        val tick = activity.substringAfter("private val uiTick = object : Runnable {")
            .substringBefore("override fun onNewIntent(")
        assertTrue("override fun run() {\n            $publish\n            controller?.let" in tick)
        assertTrue("tick.postDelayed(this, 500)" in tick)
        assertEquals(1, Regex("tick.postDelayed\\(this, 500\\)").findAll(activity).count())
        val start = activity.substringAfter("override fun onStart() {").substringBefore("override fun onStop() {")
        val stop = activity.substringAfter("override fun onStop() {").substringBefore("private fun sessionPlaying(")
        val resume = activity.substringAfter("override fun onResume() {").substringBefore("override fun onPictureInPictureModeChanged(")
        assertTrue("tick.post(uiTick)" in start)
        assertTrue("tick.removeCallbacks(uiTick)" in stop)
        assertEquals(1, Regex("tick.post\\(uiTick\\)").findAll(activity).count())
        assertEquals(1, Regex("tick.removeCallbacks\\(uiTick\\)").findAll(activity).count())
        assertTrue("refreshCaptureMetadataAccess()\n        $publish" in resume)
        val read = activity.substringAfter("override fun volumeFrac(): Float {").substringBefore("override fun setVolume(")
        assertTrue("getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)" in read)
        assertTrue("getStreamVolume(android.media.AudioManager.STREAM_MUSIC)" in read)
        assertTrue("return Math.cbrt((cur.toFloat() / max).toDouble()).toFloat()" in read)
        val write = activity.substringAfter("override fun setVolume(frac: Float) {")
        assertTrue("frac.coerceIn(0f, 1f).let { it * it * it }" in write)
        assertTrue("(cubic * max).toInt().coerceIn(0, max)" in write)
        assertTrue("android.media.AudioManager.STREAM_MUSIC," in write)
        assertTrue("0,\n        )\n        $publish" in write)
        assertEquals(3, Regex(Regex.escape(publish)).findAll(activity).count())
        val console = phase9Source("ui/Console.kt").substringAfter("fun Console(")
            .substringBefore("private fun OverflowHandleKey(")
        assertTrue("state.volumeFraction, p," in console)
        assertTrue("onChange = onVolume" in console)
        assertFalse("volumeFrac()" in console)
        assertFalse("var volume" in console)
        assertTrue("var volumeFraction by mutableFloatStateOf(0f)" in phase9Source("ui/ScopeUiState.kt"))
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

    @Test fun allQuickContentSharesOneBoundedScrollOwnerBeforeTopRemainderDismissal() {
        val popout = phase9Source("ui/Console.kt").substringAfter("fun OverflowPopout(")
            .substringBefore("// One quick-settings cell")
        assertEquals(1, Regex("rememberScrollState\\(").findAll(popout).count())
        assertEquals(1, Regex("\\.verticalScroll\\(scroll\\)").findAll(popout).count())
        assertTrue(".heightIn(max = maxHeight)" in popout)
        assertTrue(".nestedScroll(dismissScroll)\n            .verticalScroll(scroll)\n            .padding(Dim.popoutPad)" in popout)
        assertFalse("pointerInput" in popout)
        assertFalse("detectVerticalDragGestures" in popout)
        val content = popout.substringAfter(".padding(Dim.popoutPad)")
        for (action in listOf("onPictureInPicture", "onLight", "onRoom", "onSettings", "onFps", "onHud", "onGrid", "onPipAutoEnter")) {
            assertTrue(action in content, action)
        }
        assertTrue("AUTO PiP" in content)
        assertTrue("source != NestedScrollSource.UserInput || available.y <= 0f || scroll.canScrollBackward" in popout)
        assertTrue("OverflowPopoutPolicy.reverseDelta(available.y, dismissPx)" in popout)
        assertTrue("override suspend fun onPreFling" in popout)
        assertTrue("if (!dragging) return Velocity.Zero" in popout)
        assertTrue("OverflowPopoutPolicy.shouldClose(dismissPx, heightPx.toFloat())" in popout)
        assertTrue("currentClose()" in popout)
        assertTrue("reveal.settleTo(true, currentStyle, currentReduced)" in popout)
        assertTrue("rememberUpdatedState(onRequestClose)" in popout)
        assertTrue("StageChromeBounds.Card.Overflow" in popout)
        assertTrue("reveal.setTravelPx(it.height.toFloat())" in popout)
    }

    @Test fun popoutMeasuresInsideRotatedFrameAndKeepsAnchoringAndSingleScreenClosePath() {
        val screen = phase9Source("ui/PhosphorScreen.kt")
        val popout = screen.substringAfter("// The ⋯ overflow popout").substringBefore("// Layer 2: sheets")
        assertTrue(screen.indexOf(".uprightRotate(chromeQuadrant)") < screen.indexOf("// The ⋯ overflow popout"))
        assertTrue("chromeSafeDrawingInsets(0.dp, 0.dp)" in popout)
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
