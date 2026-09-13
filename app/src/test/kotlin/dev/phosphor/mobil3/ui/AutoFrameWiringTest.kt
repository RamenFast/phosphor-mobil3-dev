package dev.phosphor.mobil3.ui

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/** Supplementary adapter checks. Pure mapping and native controller tests own behavioral evidence. */
class AutoFrameWiringTest {
    private fun source(relative: String): String = listOf(
        File("app/src/main/kotlin/dev/phosphor/mobil3/$relative"),
        File("src/main/kotlin/dev/phosphor/mobil3/$relative"),
    ).first { it.isFile }.readText()

    private fun section(text: String, start: String, end: String): String =
        text.substringAfter(start).substringBefore(end)

    @Test fun bothStageZoomPathsUseTheSharedPolicyAfterExistingOwners() {
        val gestures = source("ui/Gestures.kt")
        assertEquals(2, Regex("StageZoomPolicy\\.adjust").findAll(gestures).count())
        assertTrue(gestures.indexOf("if (host.inspecting())") < gestures.indexOf("StageZoomPolicy.adjust"))
        assertTrue(gestures.indexOf("if (host.is3d())") < gestures.indexOf("StageZoomPolicy.adjust"))
        assertTrue(gestures.contains("host.setAutoFrameScale(autoFrameScale)"))
        assertTrue(gestures.contains("host.setGainAbsolute(gain)"))
        assertTrue(gestures.contains("auto frame × %.3f"))
        assertTrue(gestures.contains("manual gain × %.2f"))
    }

    @Test fun remoteGeometryCannotClaimTheLocalPreference() {
        val screen = source("ui/PhosphorScreen.kt")
        assertTrue(screen.contains("override fun autoFrameArmed() = state.localAutoGain &&"))
        assertTrue(screen.contains("!(state.remote && state.remoteGeometry)"))
        val sheets = source("ui/Sheets.kt")
        assertTrue(sheets.contains("saved, not applied to desktop geometry"))
        assertTrue(sheets.contains("Remote geometry keeps desktop gain ownership"))
    }

    @Test fun resetChangesOnlyTheGlobalPreferenceAndManualSliderStillTakesOver() {
        val activity = source("MainActivity.kt")
        val framing = section(activity, "override fun setAutoFrameScale", "override fun setGainAuto")
        assertTrue(framing.contains("AutoFramePreference.normalize"))
        assertTrue(framing.contains("PhosphorNative.setAutoFrameScale"))
        assertTrue(activity.contains("putFloat(AutoFramePreference.KEY, autoFrameScale)"))
        val reset = framing.substringAfter("override fun resetAutoFrameScale()")
        assertTrue(reset.contains("setAutoFrameScale(AutoFramePreference.DEFAULT)"))
        assertTrue(reset.contains("finishAutoFrameScale()"))
        assertTrue(framing.contains("putFloat(AutoFramePreference.KEY, value).commit()"))
        assertTrue(source("ui/Gestures.kt").contains("if (frameEdited) host.finishAutoFrameScale()"))
        assertTrue(source("ui/Sheets.kt").contains("RETRY FRAMING SAVE"))
        assertTrue(activity.substringAfter("companion object {").substringBefore("private var sourceSelection")
            .contains("private val autoFrameSave = AutoFrameSave()"))
        assertTrue(activity.contains("if (autoFrameSave.pending == null) putFloat(AutoFramePreference.KEY, autoFrameScale)"))
        assertTrue(activity.contains("if (AutoFramePreference.KEY in imported.values) autoFrameSave.restored()"))
        for (unowned in listOf("setGain(", "setGainAuto(", "start", "displayPaused", "sourceSelection")) {
            assertFalse(framing.contains(unowned), unowned)
        }
        val manual = section(activity, "override fun setGainAbsolute", "private var lastRemoteGainMs")
        assertTrue(manual.contains("putBoolean(\"auto_gain\", false)"))
        assertTrue(manual.contains("ui.manualGain = gainValue"))
        assertTrue(source("ui/Sheets.kt").contains("MANUAL GAIN · TAKES OVER AUTO"))
    }

    @Test fun restoreAndSourcePolicyPublishTheSameStoredValueThroughTheNativeBridge() {
        val activity = source("MainActivity.kt")
        val restore = section(activity, "private fun restoreTuning", "override fun captureConsentNeeded")
        assertTrue(restore.contains("AutoFramePreference.read(p.all)"))
        assertTrue(restore.contains("PhosphorNative.setAutoFrameScale(autoFrameScale)"))
        val sourcePolicy = section(activity, "private fun applyLocalGainPolicy", "private fun applyImmersive")
        assertTrue(sourcePolicy.contains("PhosphorNative.setAutoFrameScale(autoFrameScale)"))
        assertFalse(sourcePolicy.contains("autoFrameScale ="))
        val bridge = source("PhosphorNative.kt")
        assertTrue(bridge.contains("external fun setAutoFrameScale(scale: Float)"))
    }
}
