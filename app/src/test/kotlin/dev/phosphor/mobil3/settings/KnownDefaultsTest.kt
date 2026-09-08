package dev.phosphor.mobil3.settings

import dev.phosphor.mobil3.BackgroundLifecyclePolicy
import dev.phosphor.mobil3.PictureInPicturePolicy
import dev.phosphor.mobil3.ui.LightSettings
import dev.phosphor.mobil3.ui.Amoled
import dev.phosphor.mobil3.ui.ControlsVisibilityPolicy
import dev.phosphor.mobil3.ui.GridData
import dev.phosphor.mobil3.ui.ScopeUiState
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Test

class KnownDefaultsTest {
    @Test fun actualScopeUiStateConstructorUsesAcceptedInstrumentDefaults() {
        val state = ScopeUiState()
        assertSame(Amoled, state.room)
        assertEquals("amoled", state.room.id)
        assertEquals(1, state.modeIndex)
        assertEquals(7, state.beamIndex)
        assertEquals(1.8332275f, state.gain)
        assertTrue(state.autoGain)
        assertTrue(state.localAutoGain)
        assertFalse(state.grid)
        assertEquals(1, state.hudMode)
        assertEquals(1, state.bandMode)
        assertTrue(state.fullscreen)
        assertEquals(0.6f, state.geomAmount)
        assertEquals(6f, state.beamRandomLo)
        assertEquals(20f, state.beamRandomHi)
        assertEquals(3f, state.cycleSeconds)
        assertEquals(0, state.customCount)
        assertFalse(state.cyclePerTrack)
        // Clean startup does not create saved colors from picker illustrations.
        assertEquals(0, state.customColors.size)
    }

    @Test fun actualFiveKeyStateAndPoliciesKeepMissingDefaultsAndExplicitOpposites() {
        val state = ScopeUiState()
        assertTrue(state.pipAutoEnter)
        assertFalse(state.controlsAlwaysVisible)
        assertFalse(state.gridData)
        assertTrue(state.doubleTapPlayback)
        assertFalse(state.lingerBackground)
        assertTrue(PictureInPicturePolicy.autoEnter(emptyMap<String, Any>()))
        assertFalse(ControlsVisibilityPolicy.alwaysVisible(emptyMap<String, Any>()))
        assertFalse(BackgroundLifecyclePolicy.linger(emptyMap<String, Any>()))
        assertFalse(GridData.DEFAULT)

        state.pipAutoEnter = false
        state.controlsAlwaysVisible = true
        state.gridData = true
        state.doubleTapPlayback = false
        state.lingerBackground = true
        assertFalse(state.pipAutoEnter)
        assertTrue(state.controlsAlwaysVisible)
        assertTrue(state.gridData)
        assertFalse(state.doubleTapPlayback)
        assertTrue(state.lingerBackground)
        val existing = mapOf("pip_auto_enter" to false, "controls_always_visible" to true, "linger_background" to true)
        assertFalse(PictureInPicturePolicy.autoEnter(existing))
        assertTrue(ControlsVisibilityPolicy.alwaysVisible(existing))
        assertTrue(BackgroundLifecyclePolicy.linger(existing))
    }

    @Test fun untouchedControlValuesRoundTripAndOverridePreviouslyEditedDestination() {
        val state = ScopeUiState()
        val values = mapOf(
            "linger_background" to state.lingerBackground,
            "view_lock" to state.viewLock,
            "custom_selected_mask" to LightSettings().selectedMask,
            "cycle_seconds" to state.cycleSeconds,
            "cycle_per_track" to state.cyclePerTrack,
        )
        val exported = SettingsArchive.export(
            "dev.phosphor.mobil3.debug", "2.0.0-debug", "debug",
            "2026-09-06T21:00:00Z", values,
        )
        val destination = mutableMapOf<String, Any>(
            "linger_background" to true, "view_lock" to true,
            "custom_count" to 2, "cycle_seconds" to 60f, "cycle_per_track" to true,
            "custom_rgb" to "0,0,0,1,1,1,0,0,0",
        )
        destination.putAll(SettingsArchive.merge(SettingsArchive.decode(exported.json), destination))
        for ((key, value) in values) assertEquals(value, destination[key], key)
        assertTrue(exported.exportedKeys.contains("custom_rgb"))
        assertEquals("", destination["custom_rgb"])
        assertEquals(0, destination["custom_slot_count"])
        // A legacy count-only import instead keeps every stored RGB triple.
        val old = LightSettings.read(mapOf("custom_count" to 2, "custom_rgb" to "0,0,0,1,1,1,0,0,0"))
        assertEquals(old.slots, LightSettings.merge(old, mapOf("custom_count" to 0), true).slots)

    }

    // The following tests inspect source only. They do not construct Activity, execute SharedPreferences,
    // load JNI, run Compose, or prove Android orientation, import delivery, or rendering behavior.
    @Test fun sourceOnlySnapshotIncludesUntouchedControlsWithoutInventingRgb() {
        val save = section(source("MainActivity.kt"), "private fun saveTuning()", "private fun restoreTuning(")
        for (write in listOf(
            "putBoolean(\"linger_background\", ui.lingerBackground)",
            "putBoolean(\"view_lock\", ui.viewLock)",
        )) assertTrue(save.contains(write), write)
        assertFalse(save.contains("putString(\"custom_rgb\""))
    }

    @Test fun sourceOnlyActivityFallbacksReadExistingPreferencesWithoutSeedingWrites() {
        val activity = source("MainActivity.kt")
        val restore = section(activity, "private fun restoreTuning(", "override fun captureConsentNeeded()")
        for (read in listOf(
            "ui.modeIndex = p.getInt(\"mode\", 1).also { PhosphorNative.setMode(it) }",
            "ui.beamIndex = p.getInt(\"beam\", 7)",
            "gainValue = p.getFloat(\"gain\", 1.8332275f)",
            "val autoGain = p.getBoolean(\"auto_gain\", true)",
            "ui.grid = p.getBoolean(\"grid\", false).also { PhosphorNative.setGrid(it) }",
            "ui.focus = p.getFloat(\"focus\", 0.3f).also { PhosphorNative.setFocus(it) }",
            "ui.hudMode = p.getInt(\"hud_mode\", 1).coerceIn(0, 2)",
            "ui.bandMode = p.getInt(\"band_mode\", 1)",
            "ui.fullscreen = p.getBoolean(\"fullscreen\", true)",
            "ui.geomAmount = p.getFloat(\"geom_amount\", 0.6f)",
            "range(\"beam_random_range\", 1f, 30f, 6f, 20f)",
            "LightSettings.read(p.all)",
            "paletteById(p.getString(\"room\", \"amoled\") ?: \"amoled\")",
        )) assertTrue(restore.contains(read), read)
        assertTrue(activity.contains("private var gainValue = 1.8332275f"))
        assertTrue(File("src/main/kotlin/dev/phosphor/mobil3/ui/ScopeUiState.kt").readText().contains("var focus by mutableFloatStateOf(0.3f)"))
        assertTrue(File("src/main/kotlin/dev/phosphor/mobil3/ui/PhosphorScreen.kt").readText().contains("focusValue = state.focus"))
        assertTrue(activity.contains("putFloat(\"focus\", ui.focus)"))
        assertTrue(restore.contains("ui.gain = gainValue"))
        assertTrue(restore.contains("PhosphorNative.setGain(gainValue)"))
        assertTrue(restore.contains("PhosphorNative.setGainAuto(autoGain)"))
        assertTrue(restore.contains("ui.autoGain = autoGain"))
        assertTrue(restore.contains("ui.localAutoGain = autoGain"))
        assertFalse(restore.contains(".edit"))
        assertFalse(restore.contains("putFloat("))
        assertFalse(restore.contains("BuildConfig.VERSION"))
        val local = section(activity, "private fun applyLocalGainPolicy()", "private fun applyImmersive()")
        assertTrue(local.contains("prefs().getBoolean(\"auto_gain\", true)"))
        assertTrue(local.contains("PhosphorNative.setGain(gainValue)"))
        assertTrue(local.contains("PhosphorNative.setGainAuto(on)"))
        assertTrue(local.contains("ui.localAutoGain = on"))
    }

    @Test fun sourceOnlyScopeLockCapturesMissingOrientationAndUsesActualOrientationOwner() {
        val activity = source("MainActivity.kt")
        val restore = section(activity, "private fun restoreTuning(", "override fun captureConsentNeeded()")
        assertTrue(activity.contains("private var scopeRotationLockState by mutableStateOf(true)"))
        assertTrue(restore.contains("scopeRotationLockState = p.getBoolean(\"scope_rotation_locked\", true)"))
        assertTrue(restore.contains("lockedScopeOrientation = p.getInt("))
        assertTrue(restore.contains("\"scope_locked_orientation\",\n            if (scopeRotationLockState) exactCurrentOrientation() else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED"))
        assertTrue(restore.indexOf("lockedScopeOrientation =") < restore.indexOf("updateOrientationSensor()"))
        val owner = section(activity, "private fun applyScopeRotationPreference()", "private fun updateOrientationSensor()")
        assertTrue(owner.contains("requestedOrientation = if (scopeRotationLockState)"))
        assertTrue(owner.contains(") lockedScopeOrientation else exactCurrentOrientation()"))
        for (orientation in listOf("PORTRAIT", "REVERSE_PORTRAIT", "LANDSCAPE", "REVERSE_LANDSCAPE")) {
            assertTrue(owner.contains("ActivityInfo.SCREEN_ORIENTATION_$orientation"))
        }
        val create = section(activity, "override fun onCreate(savedInstanceState: Bundle?)", "override fun onResume()")
        assertTrue(create.indexOf("restoreTuning()") < create.indexOf("applyScopeRotationPreference()"))
        val imported = section(activity, "private fun acceptSettingsArchive(", "private val openSettingsArchive")
        assertTrue(imported.indexOf("restoreTuning(lightPublished)") < imported.indexOf("applyScopeRotationPreference()"))
    }

    @Test fun sourceOnlyManualGainAndRuntimeRecordingGuardsRemainSeparateFromPortableMerge() {
        val activity = source("MainActivity.kt")
        val save = section(activity, "private fun saveTuning()", "private fun restoreTuning(")
        val portable = save.substringBefore("runtimePrefs().edit")
        assertTrue(portable.contains("putFloat(\"gain\", gainValue)"))
        assertFalse(portable.contains("putFloat(\"gain\", ui.gain)"))
        assertTrue(portable.contains("putBoolean(\"auto_gain\", prefs().getBoolean(\"auto_gain\", true))"))
        for (key in listOf("last_source", "random_track_title", "cal_date", "consent_seen", "epilepsy_ack")) {
            assertFalse(portable.contains("\"$key\""), key)
        }
        assertTrue(save.contains("if (taskIsCurrent()) putString("))
        assertTrue(save.contains("runtimeInputSource(ui, mic.isRecording())"))
        assertTrue(activity.contains("private fun prefs() = getSharedPreferences(PhosphorApplication.PREFERENCES_NAME, MODE_PRIVATE)"))
        assertTrue(activity.contains("private fun runtimePrefs() = getSharedPreferences(PhosphorApplication.RUNTIME_PREFERENCES_NAME, MODE_PRIVATE)"))
        val imported = section(activity, "private fun acceptSettingsArchive(", "Triple(imported, guard?.pending, guard != null)")
        assertTrue(imported.contains("preferenceValueSnapshots(prefs().all, imported.values.keys)"))
        assertEquals(1, Regex(Regex.escape("imported.values.forEach")).findAll(imported).count())
        for (type in listOf("Boolean", "Int", "Float", "String")) {
            assertTrue(imported.contains("is $type -> editor.put$type(key, value)"))
        }
        assertTrue(imported.contains("settingsWriteOwner.write"))
        assertTrue(imported.contains("commit = { editor.commit() }"))
        assertTrue(imported.contains("settingsWriteOwner.commit("))
        assertTrue(imported.contains("restorePreferenceSnapshots(priorValues)"))
        assertFalse(imported.contains(".clear("))
        assertFalse(imported.contains("runtimePrefs()"))
    }

    @Test fun sourceOnlyStrictRestoreRetiresCustomModeWithoutSeedingIllustrativeRgb() {
        val restore = section(source("MainActivity.kt"), "private fun restoreTuning(", "override fun captureConsentNeeded()")
        val range = section(restore, "fun range(", "range(\"beam_random_range\"")
        assertTrue(range.contains("if (parts.size != 2) return dLo to dHi"))
        assertTrue(range.contains("val lo = parts[0].toFloatOrNull() ?: return dLo to dHi"))
        assertTrue(range.contains("val hi = parts[1].toFloatOrNull() ?: return dLo to dHi"))
        assertTrue(range.contains("!lo.isFinite() || !hi.isFinite() || lo !in min..max || hi !in lo..max"))
        assertTrue(range.contains("return lo to hi"))
        assertFalse(range.contains("mapNotNull"))
        assertFalse(range.contains("coerceIn"))
        assertTrue(restore.contains("LightSettings.read(p.all)"))
        assertTrue(restore.contains("else if (!applyLight(it)) markLightRestoreUnconfirmed(it)"))
        val activity = source("MainActivity.kt")
        val apply = section(activity, "private fun applyLight(", "override fun rollLight()")
        assertTrue(apply.indexOf("LightCycleGuard.evaluate") < apply.indexOf("editor.putLight(safe)"))
        assertTrue(apply.indexOf("editor.commit()") < apply.indexOf("publishNativeLight(safe, deletedSlot)"))
        assertTrue(apply.contains("restorePreferenceSnapshots(prior)"))
        assertFalse(apply.contains("FloatArray(9)"))
        val empty = LightSettings.read(emptyMap<String, Any>())
        assertTrue(empty.slots.isEmpty())
        assertEquals(0, empty.selectedMask)
        assertEquals(7, empty.preset)
        val old = LightSettings.read(mapOf("custom_count" to 0, "custom_rgb" to "0,1,0,1,0,1,0,0,0"))
        assertEquals(3, old.slots.size)
        assertEquals(0, old.selectedMask)
        // Source wiring only. Native policy tests separately exercise the real resolver.
        val native = repoFile("rust/src/jni_glue.rs").readText()
        assertTrue(native.contains("Java_dev_phosphor_mobil3_PhosphorNative_setLight"))
        assertTrue(native.contains("if !settings.valid() || !(-1..=5).contains(&deleted) { return 0; }"))
        assertTrue(native.contains("len % 3 != 0"))
        val render = repoFile("rust/src/render.rs").readText()
        assertTrue(render.contains("Cmd::SetLight(settings, deleted)"))
        assertTrue(render.contains("light.apply_edit(settings, light_clock.elapsed().as_secs_f64(), deleted)"))
        assertTrue(render.contains("light.observe(light_clock.elapsed().as_secs_f64())"))
        assertTrue(render.contains("r.theme = phosphor_beam::THEME_PRESETS[beam_color].1"))

    }

    @Test fun sourceOnlyFiveKeyMatrixRetainsOneStateRestoreActionArchiveAndFullUiOwner() {
        val activity = source("MainActivity.kt")
        val restore = section(activity, "private fun restoreTuning(", "override fun captureConsentNeeded()")
        val state = source("ui/ScopeUiState.kt")
        val archive = source("settings/SettingsArchive.kt")
        val sheets = source("ui/Sheets.kt")
        val screen = source("ui/PhosphorScreen.kt")
        val rows = listOf(
            listOf("pip_auto_enter", "pipAutoEnter", "setPipAutoEnter", "PictureInPicturePolicy.KEY", "PictureInPicturePolicy.autoEnter(p.all)"),
            listOf("controls_always_visible", "controlsAlwaysVisible", "setControlsAlwaysVisible", "dev.phosphor.mobil3.ui.ControlsVisibilityPolicy.KEY", "dev.phosphor.mobil3.ui.ControlsVisibilityPolicy.alwaysVisible(p.all)"),
            listOf("grid_data", "gridData", "setGridData", "dev.phosphor.mobil3.ui.GridData.KEY", "p.getBoolean(dev.phosphor.mobil3.ui.GridData.KEY, dev.phosphor.mobil3.ui.GridData.DEFAULT)"),
            listOf("double_tap_playback", "doubleTapPlayback", "setDoubleTapPlayback", "\"double_tap_playback\"", "p.getBoolean(\"double_tap_playback\", true)"),
            listOf("linger_background", "lingerBackground", "setLingerBackground", "BackgroundLifecyclePolicy.LINGER_KEY", "BackgroundLifecyclePolicy.linger(p.all)"),
        )
        for ((key, field, action, storageKey, read) in rows) {
            assertEquals(1, Regex("\\bvar $field\\b").findAll(state).count(), "$key state")
            assertEquals(1, Regex(Regex.escape("ui.$field = $read")).findAll(restore).count(), "$key restore")
            val write = activity.substringAfter("override fun $action(on: Boolean)").substringBefore("override fun")
            assertTrue(write.contains("ui.$field = on"), "$key action state")
            assertEquals(1, Regex(Regex.escape("putBoolean($storageKey, on)")).findAll(write).count(), "$key typed write")
            assertEquals(1, Regex(Regex.escape("\"$key\" to Spec(Kind.BOOLEAN)")).findAll(archive).count(), "$key archive")
            assertEquals(1, Regex(Regex.escape("actions.$action(!state.$field)")).findAll(sheets).count(), "$key full UI")
            assertTrue(screen.contains("override fun $action(on: Boolean) = actions.$action(on)"), "$key action adapter")
        }
        val console = source("ui/Console.kt")
        assertEquals(1, Regex(Regex.escape("AUTO PiP · ")).findAll(console).count())
        assertTrue(console.contains("onClick = onPipAutoEnter"))
        assertTrue(screen.contains("onPipAutoEnter = { actions.setPipAutoEnter(!state.pipAutoEnter) }"))
        for (field in listOf("controlsAlwaysVisible", "gridData", "doubleTapPlayback", "lingerBackground")) {
            assertFalse(console.contains("!state.$field"), "$field has no quick toggle")
        }
    }

    @Test fun sourceOnlyLightLegRangeAndPhotosensitivityConfirmationRemainOwnedByLightSheet() {
        val light = source("ui/LightSheet.kt")
        assertTrue(light.contains("\"LEG seconds\", light.seconds, 0.1f, 60f"))
        assertTrue(light.contains("\"Minimum seconds\", light.intervalMin, 0.1f, 60f"))
        assertTrue(light.contains("\"Maximum seconds\", light.intervalMax, 0.1f, 60f"))
        assertTrue(light.contains("onLightChange(light.copy(perTrack = false))"))
        assertTrue(light.contains("onLightChange(light.copy(perTrack = true))"))
        assertTrue(light.contains("val pending = state.lightPending"))
        assertTrue(light.contains("ackEpilepsy()"))
        assertTrue(light.contains("if (epilepsyAcknowledged()) onLightChange(pending)"))
        assertTrue(light.contains("LightKey(\"KEEP SAFE\", p) { state.lightPending = null }"))
        assertTrue(light.contains("heightIn(min = 48.dp)"))
        assertTrue(light.contains("setProgress { change(it.coerceIn(min, max)); true }"))
        val policy = source("ui/LightSettings.kt")
        assertTrue(policy.contains("if (requested.randomInterval) requested.intervalMin else requested.seconds"))
        assertTrue(policy.contains("acknowledged || requested.perTrack || minimum >= 1f"))
        assertTrue(policy.contains("intervalMax = requested.intervalMax.coerceAtLeast(1f)"))
        val activity = source("MainActivity.kt")
        assertTrue(activity.contains("override fun epilepsyAcknowledged(): Boolean = runtimePrefs().getBoolean(\"epilepsy_ack\", false)"))
        assertTrue(activity.contains("override fun ackEpilepsy() { runtimePrefs().edit { putBoolean(\"epilepsy_ack\", true) } }"))
    }

    @Test fun sourceOnlyApplicationWritesResolvedHudBeforeActivityRestoresIt() {
        assertTrue(repoFile("app/src/main/AndroidManifest.xml").readText()
            .contains("android:name=\".PhosphorApplication\""))
        val application = source("PhosphorApplication.kt")
        val create = section(application, "override fun onCreate()", "private fun migrateLegacyRuntimePreferences(")
        assertTrue(create.contains("migrateAndScrubLegacyProductState(portable)"))
        val migration = section(application, "private fun migrateAndScrubLegacyProductState(", "private fun SharedPreferences.Editor.putPreferenceValue")
        val resolve = "val hudMode = LegacySettingsMigration.resolveHudMode(portable.all, causal.all)"
        val write = "portable.edit().putInt(LegacySettingsMigration.HUD_MODE, hudMode)"
        assertTrue(migration.contains(resolve))
        assertTrue(migration.contains(write))
        assertTrue(migration.indexOf(resolve) < migration.indexOf(write))
        assertTrue(migration.indexOf(write) < migration.indexOf("val committed = editor.commit()"))
        val restore = section(source("MainActivity.kt"), "private fun restoreTuning(", "override fun captureConsentNeeded()")
        assertTrue(restore.contains("ui.hudMode = p.getInt(\"hud_mode\", 1).coerceIn(0, 2)"))
        // Real empty-store resolver behavior is exercised in LegacySettingsMigrationTest.
        // This checks the actual Android writer/reader linkage, not Android execution.
    }

    @Test fun sourceOnlyFirstRemoteSessionCannotPersistDisplayGainAsLocalAuthority() {
        val activity = source("MainActivity.kt")
        val start = section(activity, "override fun startRemoteHost(", "override fun setRemoteStreams(")
        assertTrue(start.contains("persistAutomaticGain()"))
        val automatic = section(activity, "private fun persistAutomaticGain()", "private fun saveTuning()")
        assertTrue(automatic.contains("prefs().edit { putFloat(\"gain\", gainValue) }"))
        assertTrue(automatic.indexOf("automaticPersistenceAllowed == false") < automatic.indexOf("prefs().edit"))
        assertFalse(automatic.contains("ui.gain"))
        assertFalse(start.contains("ui.autoGain"))
        val poll = source("PlaybackService.kt").substringAfter("private fun startRemotePoll()")
        assertTrue(poll.contains("if (p.getBoolean(\"auto_gain\", true)) \"auto\""))
        assertTrue(poll.contains("p.getFloat(\"gain\", 1.8332275f)"))
        assertFalse(poll.contains("p.getBoolean(\"auto_gain\", false)"))
        val save = section(activity, "private fun saveTuning()", "private fun restoreTuning(")
        assertTrue(save.contains("putBoolean(\"auto_gain\", prefs().getBoolean(\"auto_gain\", true))"))
        assertTrue(save.contains("putFloat(\"gain\", gainValue)"))
        assertFalse(save.contains("ui.autoGain"))
        assertFalse(save.contains("ui.localAutoGain"))
        val local = section(activity, "private fun applyLocalGainPolicy()", "private fun applyImmersive()")
        assertTrue(local.contains("prefs().getBoolean(\"auto_gain\", true)"))
        assertTrue(local.contains("PhosphorNative.setGain(gainValue)"))
        assertTrue(activity.contains("remoteGain?.let { ui.autoGain = it.optBoolean(\"auto\", false) }"))
        // Remote gain status may still display false. Direct typed preference reads keep
        // explicit local false authoritative and use true only when the key is absent.
        // No relay, SharedPreferences, or Activity lifecycle is executed by this check.
    }

    private fun source(path: String) = repoFile("app/src/main/kotlin/dev/phosphor/mobil3/$path").readText()

    private fun repoFile(path: String) = listOf(File(path), File("../$path"))
        .first { it.isFile }

    private fun section(text: String, from: String, until: String): String {
        assertTrue(text.contains(from), "missing source boundary: $from")
        val rest = text.substringAfter(from)
        assertTrue(rest.contains(until), "missing source boundary: $until")
        return rest.substringBefore(until)
    }
}
