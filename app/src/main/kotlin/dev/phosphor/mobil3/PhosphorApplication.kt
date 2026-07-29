package dev.phosphor.mobil3

import android.annotation.SuppressLint
import android.app.Application
import dev.phosphor.mobil3.distribution.Distribution
import dev.phosphor.mobil3.distribution.DistributionCapabilities
import dev.phosphor.mobil3.settings.CausalStatePreferences
import dev.phosphor.mobil3.state.Capability
import dev.phosphor.mobil3.state.FrozenMap
import dev.phosphor.mobil3.state.InitialSnapshots
import dev.phosphor.mobil3.state.PhosphorStateSnapshot
import dev.phosphor.mobil3.state.PrincipalId
import dev.phosphor.mobil3.state.PrincipalKind
import dev.phosphor.mobil3.state.frozenSetOf
import dev.phosphor.mobil3.store.LOCAL_HUD_MIGRATION_PRINCIPAL_ID
import dev.phosphor.mobil3.store.LOCAL_HUMAN_PRINCIPAL_ID
import dev.phosphor.mobil3.store.PhosphorStateStore

/**
 * Process owner for the one Phosphor causal store.
 *
 * Activities and Fortress transport services must use this exact instance. A component must never
 * construct a second store over the same preferences because that would create competing revision,
 * audit, idempotency, and authority histories.
 */
class PhosphorApplication : Application() {
    /**
     * The one causal store for this process, built on first use.
     *
     * Lazy rather than assigned in [onCreate] because a ContentProvider's `onCreate`
     * runs BEFORE `Application.onCreate`. A cold process reached through the pm3
     * provider therefore hit an uninitialised `lateinit` and refused with a stack trace
     * instead of answering, which looked like the provider was broken rather than early.
     *
     * `by lazy` is synchronised by default, so two threads racing here still get one
     * store, which is the invariant that matters: a second store over the same
     * preferences would fork the revision, audit and authority histories.
     */
    val causalStore: PhosphorStateStore by lazy { buildCausalStore() }

    override fun onCreate() {
        super.onCreate()
        // Touch it so a normal app launch pays the cost up front, exactly as before.
        // The provider path can still build it earlier without crashing.
        causalStore
    }

    private fun buildCausalStore(): PhosphorStateStore {
        val base = compiledCausalBaseSnapshot(System.currentTimeMillis())
        val portablePreferences = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
        val runtimePreferences = getSharedPreferences(RUNTIME_PREFERENCES_NAME, MODE_PRIVATE)
        val causalPreferences = getSharedPreferences(CausalStatePreferences.PREFERENCES_NAME, MODE_PRIVATE)
        migrateLegacyRuntimePreferences(portablePreferences, runtimePreferences)
        migrateLegacyCausalEnvelope(portablePreferences, causalPreferences)
        return PhosphorStateStore(
            port = CausalStatePreferences(
                causalPreferences,
                portablePreferences,
                base,
            ),
            initialSnapshot = base,
            initialWallTimeMillis = base.wallTimeMillis,
        )
    }

    /**
     * Move device consent acknowledgements and runtime metadata out of portable settings.
     *
     * Both commits are intentionally synchronous. The source values remain until the excluded
     * destination file is durable, so an interrupted migration is safe to retry.
     */
    @SuppressLint("ApplySharedPref", "UseKtx")
    private fun migrateLegacyRuntimePreferences(
        portablePreferences: android.content.SharedPreferences,
        runtimePreferences: android.content.SharedPreferences,
    ) {
        val legacyValues = portablePreferences.all.filterKeys { it in RUNTIME_PREFERENCE_KEYS }
        if (legacyValues.isEmpty()) return
        val destination = runtimePreferences.edit()
        legacyValues.forEach { (key, value) ->
            if (!runtimePreferences.contains(key)) destination.putPreferenceValue(key, value)
        }
        if (!destination.commit()) return
        val source = portablePreferences.edit()
        legacyValues.keys.forEach(source::remove)
        source.commit()
    }

    /**
     * Move pre-2.0 private causal values out of the portable settings file.
     *
     * Copy first, then remove. This preserves same-device authority history while ensuring future
     * cloud backups and device transfers cannot include the private envelope.
     */
    @SuppressLint("ApplySharedPref", "UseKtx")
    private fun migrateLegacyCausalEnvelope(
        portablePreferences: android.content.SharedPreferences,
        causalPreferences: android.content.SharedPreferences,
    ) {
        val legacyValues = portablePreferences.all.filterKeys { it in CAUSAL_MIGRATION_KEYS }
        if (legacyValues.isEmpty()) return
        val destination = causalPreferences.edit()
        legacyValues.forEach { (key, value) ->
            if (!causalPreferences.contains(key)) destination.putPreferenceValue(key, value)
        }
        if (!destination.commit()) return
        val source = portablePreferences.edit()
        legacyValues.keys.forEach(source::remove)
        source.commit()
    }

    private fun android.content.SharedPreferences.Editor.putPreferenceValue(key: String, value: Any?) {
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
        const val PREFERENCES_NAME: String = "phosphor.prefs"
        const val RUNTIME_PREFERENCES_NAME: String = "phosphor.runtime"
        private val CAUSAL_MIGRATION_KEYS = setOf(
            CausalStatePreferences.KEY_CAUSAL_ENVELOPE,
            CausalStatePreferences.KEY_LEGACY_NERD_HUD,
        )
        private val RUNTIME_PREFERENCE_KEYS = setOf(
            "cal_date",
            "consent_seen",
            "epilepsy_ack",
            "last_source",
            "random_track_title",
        )

        internal fun compiledCausalBaseSnapshot(nowWallTimeMillis: Long): PhosphorStateSnapshot {
            val base = when {
                BuildConfig.DEBUG -> InitialSnapshots.localDevelopment(nowWallTimeMillis)
                DistributionCapabilities.profile.distribution == Distribution.PLAY ->
                    InitialSnapshots.play(nowWallTimeMillis)
                else -> InitialSnapshots.fortress(nowWallTimeMillis)
            }
            val human = PrincipalId(PrincipalKind.HUMAN, LOCAL_HUMAN_PRINCIPAL_ID)
            val migration = PrincipalId(PrincipalKind.MIGRATION, LOCAL_HUD_MIGRATION_PRINCIPAL_ID)
            return base.copy(
                desired = base.desired.copy(displayHud = "off"),
                effective = base.effective.copy(displayHud = "off"),
                capabilities = FrozenMap.copyOf(
                    mapOf(
                        human to frozenSetOf(Capability.CONTROL_DISPLAY),
                        migration to frozenSetOf(Capability.CONTROL_DISPLAY),
                    ),
                ),
            )
        }
    }
}
