package dev.phosphor.mobil3

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Executes pure product policy. Source assertions are not Android runtime evidence. */
class RootCaptureDeferredTest {
    private fun source(name: String): String {
        val path = "src/main/kotlin/dev/phosphor/mobil3/$name.kt"
        return listOf(File(path), File("app", path)).first { it.isFile }.readText()
    }

    @Test fun legacyFlagsNeverSelectOrAuthorizeProductRoot() {
        assertFalse(RootCapturePolicy.PRODUCT_AVAILABLE)
        for (enabled in listOf(false, true)) for (ack in listOf(false, true)) {
            for (standard in listOf(false, true)) {
                assertEquals(CaptureBackend.STANDARD, RootCapturePolicy.backend(enabled, ack, standard))
            }
            assertFalse(RootCapturePolicy.mayAuthorize(enabled, ack))
        }
    }

    @Test fun onlyAnExplicitDebugCheckCanStartRoot() {
        for (debug in listOf(false, true)) for (check in listOf(false, true)) {
            for (enabled in listOf(false, true)) {
                assertEquals(debug && check, RootCapturePolicy.mayStart(debug, check, enabled))
            }
        }
    }

    @Test fun authorizationGuardPrecedesPreferencesAndHelperCreation() {
        val enable = source("RootCaptureSettings").substringAfter("fun enable(context:").substringBefore("fun disable(")
        val guard = enable.substringBefore("if (busy")
        assertTrue(guard.contains("if (!RootCapturePolicy.PRODUCT_AVAILABLE)"))
        assertTrue(guard.contains("message = RootCapturePolicy.DEFERRED"))
        assertTrue(guard.contains("return"))
        assertFalse(guard.contains("prefs("))
        assertFalse(guard.contains("RootCaptureSession("))
        val read = source("RootCaptureSettings").substringAfter("fun enabled(context:").substringBefore("fun enable(")
        assertTrue(read.contains("RootCapturePolicy.backend("))
        assertFalse(read.contains(".edit"))
    }

    @Test fun deferredDisableReturnsBeforeAnyMutationOrCleanup() {
        val disable = source("RootCaptureSettings").substringAfter("fun disable(context: Context) {")
            .substringBefore("fun fix(").trimStart()
        assertTrue(disable.startsWith("if (!RootCapturePolicy.PRODUCT_AVAILABLE) return"))
        val guard = disable.substringBefore("val request")
        listOf("prefs(", "revision", "authorization", "requestStop", "stopExisting", "message =").forEach {
            assertFalse("Deferred disable must not touch $it", guard.contains(it))
        }
    }

    @Test fun sourceSheetCannotAdvertiseOperationalRootEvenForAStaleRootStatus() {
        val sheet = source("ui/Sheets")
        val gate = "if (dev.phosphor.mobil3.RootCapturePolicy.PRODUCT_AVAILABLE && (state.rootCaptureEnabled || state.captureRoot)) {"
        assertTrue(sheet.contains(gate))
        val before = sheet.substringBefore(gate)
        val gated = sheet.substringAfter(gate).substringBefore("if (state.captureStatus.isNotBlank())")
        val after = sheet.substringAfter("if (state.captureStatus.isNotBlank())")
        listOf("RETRY ROOT", "ROOT MANAGER", "Root input:").forEach {
            assertFalse(before.contains(it))
            assertTrue(gated.contains(it))
            assertFalse(after.contains(it))
        }
        assertTrue(gated.contains("actions.startStandardCapture()"))
        assertTrue(sheet.contains("actions.startCapture(); onDismiss()"))
        assertTrue(sheet.contains("actions.captureConsentNeeded()"))
        assertTrue(sheet.contains("Root capture is deferred from this release"))
        assertFalse(sheet.contains("authorized root capture uses"))
        assertFalse(sheet.contains("Root capture can include BY_SYSTEM"))
    }

    @Test fun manualApiAndCallerContainNoOperationalRootPlumbing() {
        val sheet = source("ui/ManualSheet")
        val caller = source("ui/PhosphorScreen").substringAfter("Sheet.MANUAL -> ManualSheet(")
            .substringBefore("Sheet.NONE")
        listOf("rootEnabled", "rootBusy", "rootStatus", "onRootCapture", "onRootManager").forEach {
            assertFalse("Manual API still carries $it", sheet.contains(it))
            assertFalse("Manual caller still carries $it", caller.contains(it))
        }
        assertTrue(caller.contains("onBestiaryFound"))
        assertTrue(caller.contains("onOpenLink"))
    }

    @Test fun staleUiCallbacksCannotChangeSourceOrOpenManager() {
        val activity = source("MainActivity")
        val toggle = activity.substringAfter("override fun setRootCapture(").substringBefore("override fun openRootManager(")
        val guard = toggle.substringBefore("selectSource()")
        assertTrue(guard.contains("if (!RootCapturePolicy.PRODUCT_AVAILABLE)"))
        assertTrue(guard.contains("return"))
        val manager = activity.substringAfter("override fun openRootManager()").substringBefore("private fun refreshRootState")
        assertTrue(manager.substringBefore("runCatching").contains("if (!RootCapturePolicy.PRODUCT_AVAILABLE)"))
        assertTrue(manager.substringBefore("runCatching").contains("return"))
        assertTrue(activity.contains("startCaptureBackend(explicitStandard = true)"))
        assertTrue(activity.contains("captureConsentNeeded(): Boolean = !RootCaptureSettings.enabled(this)"))
        val start = activity.substringAfter("private fun startCaptureBackend(").substringBefore("private fun launchCaptureConsent")
        assertTrue(start.contains("RootCaptureSettings.enabled(this)"))
        assertTrue(start.contains("Manifest.permission.RECORD_AUDIO"))
    }

    @Test fun serviceRejectsProductStartBeforeForegroundOrHelperAndKeepsDebugChecks() {
        val root = source("CaptureService").substringAfter("private fun startRoot()").substringBefore("private fun finishCapture")
        val guard = root.substringBefore("publishStatus(CaptureStatus(STATE_STARTING")
        assertTrue(guard.contains("if (BuildConfig.DEBUG) RootCaptureService.takeCheck() else null"))
        assertTrue(guard.contains("if (check != null && !check.accepts())"))
        assertTrue(guard.contains("if (!RootCapturePolicy.mayStart(BuildConfig.DEBUG, check != null, RootCaptureSettings.enabled(this)))"))
        assertTrue(guard.contains("RootCapturePolicy.DEFERRED"))
        assertTrue(guard.contains("return START_NOT_STICKY"))
        assertFalse(guard.contains("RootCaptureSession("))
        assertFalse(guard.contains("startForeground("))
        assertTrue(root.contains("if (check == null) 2 else 3"))
    }
}
