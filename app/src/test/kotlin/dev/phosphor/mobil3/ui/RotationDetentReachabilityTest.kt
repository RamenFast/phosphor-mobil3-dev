package dev.phosphor.mobil3.ui

import java.io.File
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/** Source-only owner guards. These do not execute Android, Compose, sensors, or JNI. */
class RotationDetentReachabilityTest {

    @Test
    fun sourceOnlyDestructionClearsListenerOwnershipBeforeUnregistering() {
        val destroy = method(source("MainActivity.kt"), "override fun onDestroy()")
        val destroyed = destroy.indexOf("activityDestroyed = true")
        val capture = destroy.indexOf("val retiredGravityListener = gravityListener")
        val clear = destroy.indexOf("gravityListener = null")
        val unregister = destroy.indexOf("unregisterListener(it)")
        assertTrue(destroyed >= 0 && capture > destroyed && clear > capture && unregister > clear)
        assertTrue(unregister < destroy.indexOf("super.onDestroy()"))
    }

    @Test
    fun sourceOnlyRetiredActivityCannotRegisterOrMutateRotation() {
        val activity = source("MainActivity.kt")
        val register = method(activity, "private fun updateOrientationSensor()")
        assertTrue(register.substringAfter("{").trimStart().startsWith("if (!taskIsCurrent()) return"))
        val authority = method(activity, "private fun rotationAllowed(): Boolean")
        assertTrue(authority.substringAfter("{").trimStart().startsWith("if (!taskIsCurrent()) return false"))
        val callback = register.substringAfter("override fun onSensorChanged(e: android.hardware.SensorEvent) {")
        assertTrue(callback.trimStart().startsWith("if (!taskIsCurrent() || gravityListener !== this) return"))
    }

    @Test
    fun sourceOnlyGravitySensorRemainsAvailableForEveryAppLockCombination() {
        val fn = method(source("MainActivity.kt"), "private fun updateOrientationSensor()")
        assertFalse(fn.contains("scopeRotationLockState || uiPlacementLockState"))
        assertTrue(fn.contains("if (gravityListener == null)"))
        assertTrue(fn.contains("sm.registerListener("))
        assertTrue(fn.contains("if (previousCardinal != committedCardinal) routeOrientation()"))
        assertTrue(fn.contains("routeOrientation(force = true)"))
        for (axis in listOf("gx", "gy", "gz")) {
            assertTrue(fn.contains("$axis = 0.8f * $axis + 0.2f * e.values["))
        }
        assertTrue(fn.contains("if (horiz < 3.4f) return"))
        assertTrue(fn.contains("RotationDetent.shouldCommit(committedCardinal, degrees)"))
    }

    @Test
    fun sourceOnlyEveryRotationMutationRefreshesAuthorityBeforeDoingAnythingElse() {
        val activity = source("MainActivity.kt")
        for (signature in listOf(
            "override fun setScopeRotationLocked(locked: Boolean)",
            "override fun setUiPlacementLocked(locked: Boolean)",
            "private fun applyScopeRotationPreference()",
            "private fun routeOrientation(force: Boolean = false)",
            "private fun applyDetentedOrientation()",
        )) {
            val body = method(activity, signature).substringAfter("{").trimStart()
            assertTrue(body.startsWith("if (!rotationAllowed()) return"), signature)
        }
        val authority = method(activity, "private fun rotationAllowed(): Boolean")
        assertTrue(authority.contains("Settings.System.getInt("))
        assertTrue(authority.contains("Settings.System.ACCELEROMETER_ROTATION"))
        assertTrue(authority.contains("}.getOrDefault(true)"))
        assertTrue(authority.contains("ui.systemRotationLocked = locked"))
    }

    @Test
    fun sourceOnlySystemLockHoldsObservedActivityWithoutOverwritingStoredChoicesOrPresentation() {
        val activity = source("MainActivity.kt")
        val authority = method(activity, "private fun rotationAllowed(): Boolean")
        assertTrue(authority.contains("if (locked) {"))
        assertTrue(authority.contains("requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED"))
        for (forbidden in listOf(
            "lockedScopeOrientation", "lockedUiOrientation", "exactCurrentOrientation()",
            "prefs()", "rotationPresentation =", "setViewRotation(",
            "scopeRotationLockState =", "uiPlacementLockState =",
        )) assertFalse(authority.contains(forbidden), forbidden)
        val refresh = method(activity, "private fun refreshRotationAuthority(force: Boolean = false)")
        assertFalse(refresh.contains("prefs()"))
        val save = method(activity, "private fun saveTuning()")
        assertTrue(save.contains("putBoolean(\"scope_rotation_locked\", scopeRotationLockState)"))
        assertTrue(save.contains("putInt(\"scope_locked_orientation\", lockedScopeOrientation)"))
        assertTrue(save.contains("putBoolean(\"ui_placement_locked\", uiPlacementLockState)"))
        assertFalse(save.contains("systemRotationLocked"))
        assertFalse(save.contains("rotationPresentation"))
    }

    @Test
    fun sourceOnlySteadySensorUnlockUsesExistingTickAndLifecycleToResumeBothOwners() {
        val activity = source("MainActivity.kt")
        val tick = activity.substringAfter("private val uiTick = object : Runnable {")
            .substringBefore("\n    }\n")
        assertTrue(tick.contains("refreshRotationAuthority()"))
        val run = tick.substringAfter("override fun run() {").trimStart()
        assertTrue(run.startsWith("refreshRotationAuthority()"))
        assertTrue(tick.contains("tick.postDelayed(this, 500)"))
        assertTrue(method(activity, "override fun onStop()").contains("tick.removeCallbacks(uiTick)"))
        for (signature in listOf(
            "override fun onResume()",
            "override fun onConfigurationChanged(newConfig: Configuration)",
            "override fun onWindowFocusChanged(hasFocus: Boolean)",
        )) assertTrue(method(activity, signature).contains("refreshRotationAuthority(force = true)"), signature)
        val authority = method(activity, "private fun rotationAllowed(): Boolean")
        assertTrue(authority.contains("if (ui.systemRotationLocked != locked) rotationAuthorityNeedsRouting = true"))
        val refresh = method(activity, "private fun refreshRotationAuthority(force: Boolean = false)")
        assertTrue(refresh.contains("if (rotationAuthorityNeedsRouting || force)"))
        assertTrue(refresh.contains("applyScopeRotationPreference()"))
        assertTrue(refresh.contains("routeOrientation(force = true)"))
        assertFalse(refresh.contains("lastSensorDeg"))
        assertFalse(refresh.contains("previousCardinal"))
    }

    @Test
    fun sourceOnlyActualRouteUsesPureHeldPresentationAndObservedDisplay() {
        val route = method(source("MainActivity.kt"), "private fun routeOrientation(force: Boolean = false)")
        for (link in listOf(
            "RotationDetent.presentation(",
            "systemRotationLocked = ui.systemRotationLocked",
            "current = ui.rotationPresentation",
            "scopeLocked = scopeRotationLockState",
            "uiLocked = uiPlacementLockState",
            "cardinal = committedCardinal",
            "displayQuadrant = currentDisplayRotation()",
            "ui.rotationPresentation = next",
            "PhosphorNative.setViewRotation(next.beamQuadrant)",
            "if (!scopeRotationLockState && !uiPlacementLockState)",
            "applyDetentedOrientation()",
        )) assertTrue(route.contains(link), link)
        assertFalse(route.contains("requestedOrientation"))
        val detent = method(source("MainActivity.kt"), "private fun applyDetentedOrientation()")
        assertTrue(detent.contains("RotationDetent.screenTarget(committedCardinal)"))
        assertTrue(detent.contains("ScreenTarget.UNSPECIFIED ->\n                return"))
        assertFalse(detent.contains("requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED"))
    }

    @Test
    fun sourceOnlyImportRestoresSavedChoicesWithoutReplacingHeldPresentation() {
        val activity = source("MainActivity.kt")
        val restore = method(activity, "private fun restoreTuning()")
        assertTrue(restore.contains("scopeRotationLockState = p.getBoolean(\"scope_rotation_locked\", true)"))
        assertTrue(restore.contains("uiPlacementLockState = p.getBoolean(\"ui_placement_locked\", false)"))
        assertTrue(restore.contains("updateOrientationSensor()"))
        assertFalse(restore.contains("rotationPresentation ="))
        assertFalse(restore.contains("systemRotationLocked ="))
        val imported = activity.substringAfter("}.onSuccess { imported ->")
            .substringBefore("}.onFailure { error ->")
        assertTrue(imported.contains("restoreTuning()"))
        assertTrue(imported.indexOf("restoreTuning()") < imported.indexOf("applyScopeRotationPreference()"))
    }

    @Test
    fun sourceOnlyFullSheetDisablesBothDependentControlsAndExplainsAndroidAuthority() {
        val sheets = source("ui/Sheets.kt")
        val scope = sheets.substringAfter("val scopeRotation: @Composable")
            .substringBefore("val uiPlacement: @Composable")
        val placement = sheets.substringAfter("val uiPlacement: @Composable")
            .substringBefore("if (wideEnoughForOneRow)")
        for (control in listOf(scope, placement)) {
            assertTrue(control.contains("ChipCell("))
            assertTrue(control.contains("enabled = !state.systemRotationLocked"))
        }
        assertTrue(sheets.contains("if (state.systemRotationLocked)"))
        assertTrue(sheets.contains("Android rotation lock is on. Enable system auto-rotate"))
        val cell = source("ui/Controls.kt").substringAfter("fun ChipCell(")
            .substringBefore("fun SwatchCell(")
        assertTrue(cell.contains(".clickable(enabled = enabled, onClick = onClick)"))
        assertTrue(cell.contains("!enabled -> p.muted"))
    }

    @Test
    fun sourceOnlyChromeAndSheetTransformsDoNotReadImportedLockChoicesAsPresentation() {
        val screen = source("ui/PhosphorScreen.kt")
        val orientation = screen.substringAfter("val actualLandscape =")
            .substringBefore("var consoleVisible by remember")
        assertTrue(orientation.contains("LocalConfiguration.current.orientation"))
        assertTrue(orientation.contains("val rotation = state.rotationPresentation"))
        assertTrue(orientation.contains("val uiLocked = rotation.uiPlacementLocked"))
        assertTrue(orientation.contains("val chromeQuadrant = rotation.chromeQuadrant"))
        assertTrue(orientation.contains("RotationDetent.chromeLandscape(rotation, actualLandscape)"))
        assertFalse(orientation.contains("actions.isScopeRotationLocked()"))
        assertFalse(orientation.contains("actions.isUiPlacementLocked()"))
        assertFalse(orientation.contains("actions.lockedUiLandscape()"))
        assertFalse(orientation.contains("requestedOrientation"))
        assertTrue(screen.contains("val sheetQuadrant = if (uiLocked) state.uprightQuadrant else 0"))
        assertTrue(screen.contains("Modifier.uprightRotate(sheetQuadrant)"))
        val state = source("ui/ScopeUiState.kt")
        assertTrue(state.contains("var systemRotationLocked by mutableStateOf(true)"))
        assertTrue(state.contains("var rotationPresentation by mutableStateOf(RotationDetent.Presentation())"))
    }

    @Test
    fun sourceOnlyStableSettingsPullHostReadsCurrentAppliedFrameAtBegin() {
        val screen = source("ui/PhosphorScreen.kt")
        assertTrue(screen.contains("val currentChromeLandscape = rememberUpdatedState(chromeLandscape)"))
        assertTrue(screen.contains("val currentUiLocked = rememberUpdatedState(uiLocked)"))
        assertTrue(screen.contains("val settingsPullHost = remember(settingsReveal) {"))
        val host = screen.substringAfter("val settingsPullHost = remember(settingsReveal) {")
            .substringBefore("val overflowPullHost")
        assertTrue(host.contains("object : PullGestureHost"))
        val begin = host.substringAfter("override fun begin() {").substringBefore("override fun dragBy(")
        assertTrue(begin.contains("if (currentChromeLandscape.value && currentUiLocked.value)"))
        assertTrue(begin.contains("if (rootWidthPx > 0) rootWidthPx * 0.82f"))
        assertTrue(begin.contains("else if (rootHeightPx > 0) rootHeightPx * 0.82f"))
        assertFalse(begin.contains("if (chromeLandscape && uiLocked)"))
        assertFalse(begin.contains("sheetLandscape"))
        assertFalse(begin.contains("actions.isUiPlacementLocked()"))
        assertTrue(begin.indexOf("settingsReveal.setTravelPx(") < begin.indexOf("settingsReveal.begin("))
        assertTrue(screen.contains("settingsPullHost.begin()\n                                                settingsPullHost.dragBy("))
        val measured = source("ui/Sheets.kt").substringAfter("sheetWidthPx = it.width")
            .substringBefore(".graphicsLayer {")
        assertTrue(measured.contains("entryReveal?.setTravelPx("))
        assertTrue(measured.contains("SheetEntryPolicy.travelPx("))
        assertTrue(measured.contains("landscape, uiLocked, it.width, it.height,"))
        // Wiring only. The host object and Compose remember lifetime are not executed here.
    }

    private fun method(source: String, signature: String): String {
        assertTrue(source.contains(signature), "missing owner: $signature")
        return source.substringAfter(signature).substringBefore("\n    }")
    }

    private fun source(relative: String): String {
        var dir = File("").absoluteFile
        repeat(4) {
            val candidate = File(dir, "src/main/kotlin/dev/phosphor/mobil3/$relative")
            if (candidate.isFile) return candidate.readText()
            val fromRepo = File(dir, "app/src/main/kotlin/dev/phosphor/mobil3/$relative")
            if (fromRepo.isFile) return fromRepo.readText()
            dir = dir.parentFile ?: return@repeat
        }
        fail("could not locate $relative")
    }
}
