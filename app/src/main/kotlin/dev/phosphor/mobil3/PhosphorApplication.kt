package dev.phosphor.mobil3

import android.annotation.SuppressLint
import android.app.Application
import android.content.SharedPreferences
import dev.phosphor.mobil3.settings.LegacySettingsMigration
import java.security.KeyStore

/** Process startup owns settings migration and removal of retired private product state. */
class PhosphorApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        android.util.Log.i(
            "Phosphor",
            "build ${BuildConfig.VERSION_NAME} ${BuildConfig.BUILD_COMMIT}",
        )
        val portable = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
        val runtime = getSharedPreferences(RUNTIME_PREFERENCES_NAME, MODE_PRIVATE)
        migrateLegacyRuntimePreferences(portable, runtime)
        migrateAndScrubLegacyProductState(portable)
    }

    /** Copy first and remove second so an interrupted runtime migration is safe to retry. */
    @SuppressLint("ApplySharedPref", "UseKtx")
    private fun migrateLegacyRuntimePreferences(
        portable: SharedPreferences,
        runtime: SharedPreferences,
    ) {
        val legacyValues = portable.all.filterKeys { it in RUNTIME_PREFERENCE_KEYS }
        if (legacyValues.isEmpty()) return
        val destination = runtime.edit()
        legacyValues.forEach { (key, value) ->
            if (!runtime.contains(key)) destination.putPreferenceValue(key, value)
        }
        if (!destination.commit()) return
        val source = portable.edit()
        legacyValues.keys.forEach(source::remove)
        source.commit()
    }

    /** Preserve the HUD mode, then remove retired causal and authority containers. */
    @SuppressLint("ApplySharedPref", "UseKtx")
    private fun migrateAndScrubLegacyProductState(portable: SharedPreferences) {
        val causal = getSharedPreferences(LegacySettingsMigration.CAUSAL_PREFERENCES, MODE_PRIVATE)
        val hudMode = LegacySettingsMigration.resolveHudMode(portable.all, causal.all)
        val editor = portable.edit().putInt(LegacySettingsMigration.HUD_MODE, hudMode)
        LegacySettingsMigration.PORTABLE_KEYS_TO_REMOVE.forEach(editor::remove)
        val committed = editor.commit()
        if (!committed) return

        deleteSharedPreferences(LegacySettingsMigration.CAUSAL_PREFERENCES)
        deleteSharedPreferences(LegacySettingsMigration.LEGACY_TAILNET_PREFERENCES)
        runCatching {
            val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keys.containsAlias(LegacySettingsMigration.LEGACY_TAILNET_KEY_ALIAS)) {
                keys.deleteEntry(LegacySettingsMigration.LEGACY_TAILNET_KEY_ALIAS)
            }
        }
    }

    private fun SharedPreferences.Editor.putPreferenceValue(key: String, value: Any?) {
        when (value) {
            is String -> putString(key, value)
            is Int -> putInt(key, value)
            is Long -> putLong(key, value)
            is Float -> putFloat(key, value)
            is Boolean -> putBoolean(key, value)
            is Set<*> -> {
                val strings = value.filterIsInstance<String>().toSet()
                if (strings.size == value.size) putStringSet(key, strings)
            }
        }
    }

    companion object {
        const val PREFERENCES_NAME = "phosphor.prefs"
        const val RUNTIME_PREFERENCES_NAME = "phosphor.runtime"
        private val RUNTIME_PREFERENCE_KEYS = setOf(
            "cal_date",
            "consent_seen",
            "epilepsy_ack",
            "last_source",
            "random_track_title",
        )
    }
}
