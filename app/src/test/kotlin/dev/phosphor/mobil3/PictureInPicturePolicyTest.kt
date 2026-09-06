package dev.phosphor.mobil3

import dev.phosphor.mobil3.ui.ScopeUiState
import dev.phosphor.mobil3.ui.phase9Source
import org.junit.Test
import kotlin.test.*

class PictureInPicturePolicyTest {
    @Test fun autoEntryDefaultsTrueInPolicyAndActualState() {
        assertTrue(PictureInPicturePolicy.autoEnter(emptyMap<String, Any>()))
        assertTrue(ScopeUiState().pipAutoEnter)
    }

    @Test fun automaticPathsAreExclusiveAndOnlyReadTheirOwnSetting() {
        for (sdk in 29..36) for (auto in listOf(false, true)) {
            for (linger in listOf(false, true)) for (controls in listOf(false, true)) {
                val settings = mapOf("pip_auto_enter" to auto, "linger_background" to linger, "controls_always_visible" to controls)
                val enabled = PictureInPicturePolicy.autoEnter(settings)
                assertEquals(auto, enabled)
                assertEquals(sdk >= 31 && auto, PictureInPicturePolicy.platformAutoEnter(sdk, enabled))
                assertEquals(sdk < 31 && auto, PictureInPicturePolicy.enterOnLeave(sdk, enabled, false))
                assertFalse(PictureInPicturePolicy.enterOnLeave(sdk, enabled, true))
            }
        }
    }

    @Test fun manualEntryIgnoresAutoLingerAndControlsAndAvoidsDuplicateEntry() {
        val state = ScopeUiState()
        for (auto in listOf(false, true)) for (linger in listOf(false, true)) for (controls in listOf(false, true)) {
            state.pipAutoEnter = auto
            state.lingerBackground = linger
            state.controlsAlwaysVisible = controls
            assertTrue(PictureInPicturePolicy.enterManually(state.pip))
        }
        state.pip = true
        assertFalse(PictureInPicturePolicy.enterManually(state.pip))
    }

    @Test fun realActivityCallbacksKeepManualSeparateAndRefreshAutoParams() {
        val activity = phase9Source("MainActivity.kt")
        val params = activity.substringAfter("private fun pictureInPictureParams()").substringBefore("override fun onStart()")
        assertTrue("PictureInPicturePolicy.platformAutoEnter(Build.VERSION.SDK_INT, ui.pipAutoEnter)" in params)
        assertTrue("PictureInPicturePolicy.enterOnLeave(Build.VERSION.SDK_INT, ui.pipAutoEnter, isInPictureInPictureMode)" in params)
        val manual = params.substringAfter("override fun enterPictureInPicture()")
        assertTrue("PictureInPicturePolicy.enterManually(isInPictureInPictureMode)" in manual)
        assertTrue("enterPictureInPictureMode(pictureInPictureParams())" in manual)
        for (forbidden in listOf("linger", "controlsAlwaysVisible", "controls_always_visible")) assertFalse(forbidden in params)
        assertFalse("ui.pipAutoEnter" in manual)
        val setter = activity.substringAfter("override fun setPipAutoEnter(on: Boolean)").substringBefore("override fun openCaptureMetadataSettings")
        assertTrue("ui.pipAutoEnter = on" in setter)
        assertTrue("putBoolean(PictureInPicturePolicy.KEY, on)" in setter)
        assertTrue("updatePictureInPictureParams()" in setter)
        assertTrue("ui.pip = isInPictureInPictureMode" in activity)
        assertTrue("ui.pipAutoEnter = PictureInPicturePolicy.autoEnter(p.all)" in activity)
    }

    @Test fun quickAndFullSettingsShareLiveStateAndManualActionsAreReachable() {
        val screen = phase9Source("ui/PhosphorScreen.kt")
        val sheets = phase9Source("ui/Sheets.kt")
        val console = phase9Source("ui/Console.kt")
        assertTrue("active = state.pipAutoEnter" in sheets)
        assertTrue("active = state.pipAutoEnter" in console)
        assertTrue("actions.setPipAutoEnter(!state.pipAutoEnter)" in sheets)
        assertTrue("onPipAutoEnter = { actions.setPipAutoEnter(!state.pipAutoEnter) }" in screen)
        assertTrue("override fun setPipAutoEnter(on: Boolean) = actions.setPipAutoEnter(on)" in screen)
        assertTrue("override fun enterPictureInPicture() = actions.enterPictureInPicture()" in screen)
        assertTrue("FlatKey(\"ENTER PiP\", p) { actions.enterPictureInPicture() }" in sheets)
        assertTrue("Triple(\"PiP\", SettingsGlyph.Display, onPictureInPicture)" in console)
    }
}
