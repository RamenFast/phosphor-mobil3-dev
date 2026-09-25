package dev.phosphor.mobil3.ui

import org.junit.Test
import kotlin.test.*

/** State and real source wiring only. Compose tap latency needs the root's phone checks. */
class DoubleTapPolicyTest {
    @Test fun actualUiDefaultIsEnabledAndIndependentOfLinger() {
        val state = ScopeUiState()
        assertTrue(state.doubleTapPlayback)
        state.doubleTapPlayback = false
        assertFalse(state.doubleTapPlayback)
        assertFalse(state.lingerBackground)
    }

    @Test fun existingTapRecognizerUsesSettingKeyLiteralNullAndCurrentPlaybackAction() {
        val screen = phase8Source("ui/PhosphorScreen.kt")
        val tap = screen.substringAfter(".pointerInput(state.doubleTapPlayback)")
            .substringBefore("// The gesture readout ribbon")
        assertTrue("detectTapGestures(" in tap)
        assertTrue("onDoubleTap = if (state.doubleTapPlayback)" in tap)
        assertTrue("{ currentActions.togglePlay() }" in tap)
        assertTrue("} else null" in tap)
        assertEquals(1, Regex("currentActions\\.togglePlay\\(\\)").findAll(tap).count())
        assertTrue("val currentActions by rememberUpdatedState(actions)" in screen)
        assertTrue("if (overflowComposed) closeOverflow(Sheet.NONE)" in tap)
        assertTrue("else consoleVisible = ControlsVisibilityPolicy.afterTap(consoleVisible, state.controlsAlwaysVisible)" in tap)
        assertFalse("onDoubleTap = {" in tap)
    }

    @Test fun saveRestoreAndPresentOnlyImportUseTheDedicatedBoolean() {
        val activity = phase8Source("MainActivity.kt")
        val save = activity.substringAfter("private fun saveTuning()").substringBefore("private fun restoreTuning(")
        assertTrue("putBoolean(\"double_tap_playback\", ui.doubleTapPlayback)" in save.substringBefore("runtimePrefs()"))
        val restore = activity.substringAfter("private fun restoreTuning(lightPublished: Boolean = false)").substringBefore("override fun")
        assertTrue("ui.doubleTapPlayback = p.getBoolean(\"double_tap_playback\", true)" in restore)
        // Import already merges decoded present values and restores the actual UI state.
        assertTrue("imported.values.forEach" in activity || "decoded.values.forEach" in activity)
        assertTrue("is Boolean ->" in activity)
    }
}
