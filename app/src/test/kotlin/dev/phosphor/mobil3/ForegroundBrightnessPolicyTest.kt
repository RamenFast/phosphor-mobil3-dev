package dev.phosphor.mobil3

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ForegroundBrightnessPolicyTest {
    private val foreground = ForegroundBrightnessPolicy.WindowState(true, true, true, true)
    private fun source(name: String): String {
        val path = "src/main/kotlin/dev/phosphor/mobil3/$name.kt"
        return listOf(File(path), File("app", path)).first { it.isFile }.readText()
    }

    @Test fun absentAndMalformedLocalValuesStayOff() {
        assertFalse(ForegroundBrightnessPolicy.requested(emptyMap<String, Any>()))
        for (bad in listOf<Any>(false, "true", 1, 0, 1f)) {
            assertFalse(ForegroundBrightnessPolicy.requested(mapOf(ForegroundBrightnessPolicy.KEY to bad)))
        }
        assertTrue(ForegroundBrightnessPolicy.requested(mapOf(ForegroundBrightnessPolicy.KEY to true)))
    }

    @Test fun everyForegroundConditionIsRequiredAndInactivePinPreservesSourceWake() {
        for (mask in 0 until 256) {
            fun bit(n: Int) = mask and (1 shl n) != 0
            val state = ForegroundBrightnessPolicy.WindowState(bit(1), bit(2), bit(3), bit(4), bit(5), bit(6), bit(7))
            val expected = mask == 31
            val active = ForegroundBrightnessPolicy.active(bit(0), state)
            assertEquals("mask=$mask", expected, active)
            assertEquals(if (expected) 1f else -1f, ForegroundBrightnessPolicy.brightness(active), 0f)
            for (sourceAwake in listOf(false, true)) {
                assertEquals(sourceAwake || expected, ForegroundBrightnessPolicy.awake(sourceAwake, active))
            }
        }
    }

    @Test fun pauseNoSourceFocusPipHudDestroyAndRecreationSchedulesRestore() {
        // Source/playback state is deliberately not an activation input.
        var state = foreground
        fun request() = ForegroundBrightnessPolicy.brightness(ForegroundBrightnessPolicy.active(true, state))
        assertEquals(1f, request(), 0f)
        assertTrue(ForegroundBrightnessPolicy.awake(false, ForegroundBrightnessPolicy.active(true, state)))
        for (outside in listOf(foreground.copy(resumed = false), foreground.copy(started = false),
            foreground.copy(focused = false), foreground.copy(pip = true), foreground.copy(hud = true),
            foreground.copy(current = false), foreground.copy(destroyed = true))) {
            state = outside
            assertEquals(-1f, request(), 0f)
            state = foreground
            assertEquals(1f, request(), 0f)
        }
        state = ForegroundBrightnessPolicy.WindowState()
        assertEquals(-1f, request(), 0f)
        state = state.copy(started = true, current = true)
        assertEquals(-1f, request(), 0f)
        state = state.copy(resumed = true, focused = true)
        assertEquals(1f, request(), 0f)
        assertEquals(-1f, ForegroundBrightnessPolicy.brightness(ForegroundBrightnessPolicy.active(false, state)), 0f)
    }

    @Test fun activityUsesOneEventDrivenAttributeWriterNotTheWakeTicker() {
        val activity = source("MainActivity")
        val apply = activity.substringAfter("private fun applyBrightnessPin()").substringBefore("override fun setPinScreenBrightness")
        assertTrue(apply.contains("if (attributes.screenBrightness != requested)"))
        assertTrue(apply.contains("attributes.screenBrightness = requested"))
        assertTrue(apply.contains("window.attributes = attributes"))
        assertTrue(apply.contains("reassertSourceWake()"))
        assertEquals(1, Regex("attributes.screenBrightness =").findAll(activity).count())
        val ticker = activity.substringAfter("private val uiTick =").substringBefore("private fun handleIntent(")
        assertFalse(ticker.contains("applyBrightnessPin()"))
        assertFalse(ticker.contains("screenBrightness"))
        val wake = activity.substringAfter("private fun reassertSourceWake()").substringBefore("// The transport law")
        assertTrue(wake.contains("SourceWakePolicy.visible("))
        assertTrue(wake.contains("started = activityStarted && !activityDestroyed"))
        assertTrue(wake.contains("sourceLive = MicCaptureService.hasLiveWakeSource() || PlaybackService.hasLiveWakeSource() || CaptureService.hasLiveWakeSource()"))
        assertTrue(wake.contains("ForegroundBrightnessPolicy.awake(sourceAwake, brightnessPinActive())"))
        assertFalse(wake.contains("screenBrightness"))
        assertFalse(wake.contains("applyBrightnessPin()"))
        val policy = activity.substringAfter("private fun brightnessPinActive()").substringBefore("private fun applyBrightnessPin()")
        listOf("activityStarted", "activityResumed", "activityFocused", "taskIsCurrent()", "activityDestroyed",
            "ui.pip || isInPictureInPictureMode", "FloatingHudService.presenting").forEach { assertTrue(policy.contains(it)) }
    }

    @Test fun lifecycleAdaptersRestoreBeforeRetirementAndHandleFocusLoss() {
        val activity = source("MainActivity")
        for ((method, state) in listOf("onPause()" to "activityResumed = false", "onStop()" to "activityStarted = false",
            "onDestroy()" to "activityDestroyed = true")) {
            val body = activity.substringAfter("override fun $method").substringBefore("\n    }")
            assertTrue(body.indexOf(state) >= 0)
            assertTrue(body.indexOf(state) < body.indexOf("applyBrightnessPin()"))
        }
        val focus = activity.substringAfter("override fun onWindowFocusChanged(").substringBefore("override fun orbitBy")
        assertTrue(focus.indexOf("activityFocused = hasFocus") < focus.indexOf("applyBrightnessPin()"))
        assertTrue(focus.indexOf("applyBrightnessPin()") < focus.indexOf("if (hasFocus)"))
        for (entry in listOf("override fun onResume()", "override fun onPictureInPictureModeChanged(",
            "override fun onConfigurationChanged(", "override fun makeSurface()")) {
            assertTrue(entry, activity.substringAfter(entry).substringBefore("\n    }").contains("applyBrightnessPin()"))
        }
        val hud = activity.substringAfter("private val hudChanged:").substringBefore("private val controllerBinding")
        assertTrue(hud.indexOf("applyBrightnessPin()") < hud.indexOf("moveTaskToBack(true)"))
    }

    @Test fun bothActualInterfacesRequireTheSetterAndTheSheetAdapterForwardsIt() {
        val screen = source("ui/PhosphorScreen")
        val scope = screen.substringAfter("interface ScopeActions").substringBefore("@Composable")
        val sheet = source("ui/Sheets").substringAfter("interface SheetActions")
        for (contract in listOf(scope, sheet)) {
            assertTrue(contract.contains("fun setPinScreenBrightness(on: Boolean)\n"))
            assertFalse(contract.contains("fun setPinScreenBrightness(on: Boolean) {}"))
        }
        assertTrue(screen.contains("override fun setPinScreenBrightness(on: Boolean) = actions.setPinScreenBrightness(on)"))
        assertEquals(1, Regex("override fun setPinScreenBrightness").findAll(screen).count())
    }

    @Test fun persistenceImportAndUiStaySeparateFromAudioAndGlobalBrightness() {
        val activity = source("MainActivity")
        val setter = activity.substringAfter("override fun setPinScreenBrightness(").substringBefore("private fun reassertSourceWake")
        assertTrue(setter.contains("if (!taskIsCurrent()) return"))
        assertTrue(setter.contains("putBoolean(ForegroundBrightnessPolicy.KEY, on).commit()"))
        assertTrue(setter.indexOf("applyBrightnessPin()") < setter.indexOf(".commit()"))
        assertTrue(setter.contains("saving failed"))
        val restore = activity.substringAfter("private fun restoreTuning(").substringBefore("PhosphorNative.setPauseBlack")
        assertTrue(restore.contains("ForegroundBrightnessPolicy.requested(p.all)"))
        assertTrue(restore.contains("applyBrightnessPin()"))
        val import = activity.substringAfter("private fun acceptSettingsArchive(").substringBefore("private val openSettingsArchive")
        assertTrue(import.indexOf("settingsWriteOwner.commit(") < import.indexOf("restoreTuning(lightPublished)"))
        for (name in listOf("settings/instrument/InstrumentSetup", "settings/appearance/AppearanceDocument", "FloatingHudService")) {
            assertFalse(source(name).contains("pin_screen_brightness"))
            assertFalse(source(name).contains("screenBrightness"))
        }
        val sheet = source("ui/Sheets")
        assertTrue(sheet.contains("actions.setPinScreenBrightness(!state.pinScreenBrightness)"))
        assertTrue(sheet.contains("Thermal, panel and accessibility limits"))
        assertFalse(activity.contains("Settings.System.put"))
        assertFalse(activity.contains("Settings.System.SCREEN_BRIGHTNESS"))
    }
}
