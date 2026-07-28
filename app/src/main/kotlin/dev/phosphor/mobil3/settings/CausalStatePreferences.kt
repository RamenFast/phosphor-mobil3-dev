package dev.phosphor.mobil3.settings

import android.content.SharedPreferences
import dev.phosphor.mobil3.state.PhosphorStateSnapshot
import dev.phosphor.mobil3.state.Refusal
import dev.phosphor.mobil3.state.RefusalCode
import dev.phosphor.mobil3.store.PhosphorStatePersistencePort
import dev.phosphor.mobil3.store.PhosphorStoreImage
import dev.phosphor.mobil3.store.PhosphorStoreLoadResult
import dev.phosphor.mobil3.store.PhosphorStorePersistenceResult

/** SharedPreferences adapter for the Phase 04 HUD causal store image. */
class CausalStatePreferences(
    private val preferences: PreferenceBoundary,
    private val compiledBaseSnapshot: PhosphorStateSnapshot,
    private val portablePreferences: PreferenceBoundary = preferences,
) : PhosphorStatePersistencePort {
    constructor(sharedPreferences: SharedPreferences, compiledBaseSnapshot: PhosphorStateSnapshot) : this(
        AndroidPreferenceBoundary(sharedPreferences),
        compiledBaseSnapshot,
    )

    constructor(
        causalPreferences: SharedPreferences,
        portablePreferences: SharedPreferences,
        compiledBaseSnapshot: PhosphorStateSnapshot,
    ) : this(
        AndroidPreferenceBoundary(causalPreferences),
        compiledBaseSnapshot,
        AndroidPreferenceBoundary(portablePreferences),
    )

    override fun load(): PhosphorStoreLoadResult {
        if (preferences.contains(KEY_CAUSAL_ENVELOPE)) {
            val envelope = preferences.getString(KEY_CAUSAL_ENVELOPE)
                ?: return PhosphorStoreLoadResult.CorruptEnvelope(
                    Refusal(
                        RefusalCode.INVALID_REQUEST,
                        "Do not fall back silently; preserve the private HUD causal envelope with its original string type and run explicit repair.",
                    ),
                )
            return try {
                PhosphorStoreLoadResult.CausalImage(HudCausalEnvelopeCodec.decode(envelope, compiledBaseSnapshot))
            } catch (error: HudCausalCodecException) {
                // A v1 envelope is old, not corrupt. Without this the store would latch
                // read-only forever on any device that ever ran a schema-v1 build, and
                // the HUD would silently fall back with no way to recover but a wipe.
                // The migration is explicit and checksum-bound, so a tampered envelope
                // still refuses rather than being re-checksummed into acceptance.
                if (error.error == CODE_SCHEMA_MIGRATION_REQUIRED) {
                    migrateLegacyEnvelope(envelope)
                } else {
                    PhosphorStoreLoadResult.CorruptEnvelope(error.refusal())
                }
            }
        }
        return PhosphorStoreLoadResult.LegacyBootstrap(
            hudModeRaw = rawLegacyInt(portablePreferences, KEY_LEGACY_HUD_MODE),
            nerdHudRaw = rawLegacyBoolean(preferences, KEY_LEGACY_NERD_HUD),
        )
    }

    /**
     * Upgrade a schema-v1 envelope in place.
     *
     * The rewritten envelope is only persisted after it decodes cleanly, so a failure
     * here leaves the original bytes untouched for a later repair. If the commit fails,
     * the migrated image is still returned: the user gets a working HUD now, and the
     * upgrade is simply retried on the next launch.
     */
    private fun migrateLegacyEnvelope(envelope: String): PhosphorStoreLoadResult = try {
        val migrated = HudCausalEnvelopeCodec.migrateLegacyV1(envelope, compiledBaseSnapshot)
        val image = HudCausalEnvelopeCodec.decode(migrated, compiledBaseSnapshot)
        preferences.editCommit { putString(KEY_CAUSAL_ENVELOPE, migrated) }
        PhosphorStoreLoadResult.CausalImage(image)
    } catch (error: HudCausalCodecException) {
        PhosphorStoreLoadResult.CorruptEnvelope(error.refusal())
    }

    private fun rawLegacyInt(source: PreferenceBoundary, key: String): String? = when {
        !source.contains(key) -> null
        else -> source.getIntOrNull(key)?.toString() ?: INVALID_LEGACY_TYPE
    }

    private fun rawLegacyBoolean(source: PreferenceBoundary, key: String): String? = when {
        !source.contains(key) -> null
        else -> source.getBooleanOrNull(key)?.toString() ?: INVALID_LEGACY_TYPE
    }

    override fun save(image: PhosphorStoreImage): PhosphorStorePersistenceResult {
        val encoded = try {
            HudCausalEnvelopeCodec.encode(image, compiledBaseSnapshot)
        } catch (error: HudCausalCodecException) {
            return PhosphorStorePersistenceResult.Failed(error.refusal())
        }
        val legacyMode = when (image.snapshot.effective.displayHud) {
            "on" -> 0
            "auto" -> 1
            "off" -> 2
            else -> return PhosphorStorePersistenceResult.Failed(
                Refusal(RefusalCode.INVALID_VALUE, "Persist only display.hud values on, auto, or off.", image.snapshot.revision),
            )
        }
        val causalKeys = setOf(KEY_CAUSAL_ENVELOPE, KEY_LEGACY_HUD_MODE)
        val priorCausal = preferences.snapshot().filterKeys { it in causalKeys }
        val priorPortable = portablePreferences.snapshot().filterKeys { it == KEY_LEGACY_HUD_MODE }
        val causalCommitted = preferences.editCommit {
            putString(KEY_CAUSAL_ENVELOPE, encoded)
            putInt(KEY_LEGACY_HUD_MODE, legacyMode)
        }
        val portableCommitted = causalCommitted && (
            portablePreferences === preferences || portablePreferences.editCommit {
                putInt(KEY_LEGACY_HUD_MODE, legacyMode)
            }
        )
        return if (portableCommitted) {
            PhosphorStorePersistenceResult.Saved
        } else {
            preferences.restore(priorCausal, causalKeys)
            if (portablePreferences !== preferences) {
                portablePreferences.restore(priorPortable, setOf(KEY_LEGACY_HUD_MODE))
            }
            PhosphorStorePersistenceResult.Failed(
                Refusal(
                    code = RefusalCode.SYSTEM_UNAVAILABLE,
                    fix = "Retry after storage is available; the HUD causal image and portable hud_mode were not committed together.",
                    currentRevision = image.snapshot.revision,
                ),
            )
        }
    }

    companion object {
        const val PREFERENCES_NAME = "phosphor.causal_state"
        const val KEY_CAUSAL_ENVELOPE = "__phosphor_private.hud_causal_envelope"
        const val KEY_LEGACY_HUD_MODE = "hud_mode"
        const val KEY_LEGACY_NERD_HUD = "nerd_hud"
        internal const val INVALID_LEGACY_TYPE = "__invalid_preference_type__"

        /** The codec's error string for an envelope written by a schema-v1 build. */
        private const val CODE_SCHEMA_MIGRATION_REQUIRED = "schema_migration_required"
    }
}

interface PreferenceBoundary {
    fun contains(key: String): Boolean
    fun getString(key: String): String?
    fun getIntOrNull(key: String): Int?
    fun getBooleanOrNull(key: String): Boolean?
    fun editCommit(block: PreferenceEditorBoundary.() -> Unit): Boolean
    fun snapshot(): Map<String, Any?>
    fun restore(snapshot: Map<String, Any?>, keys: Set<String>)
}

interface PreferenceEditorBoundary {
    fun putString(key: String, value: String)
    fun putInt(key: String, value: Int)
    fun remove(key: String)
}

private class AndroidPreferenceBoundary(
    private val preferences: SharedPreferences,
) : PreferenceBoundary {
    override fun contains(key: String): Boolean = preferences.contains(key)
    override fun getString(key: String): String? = preferences.all[key] as? String
    override fun getIntOrNull(key: String): Int? = preferences.all[key] as? Int
    override fun getBooleanOrNull(key: String): Boolean? = preferences.all[key] as? Boolean
    override fun editCommit(block: PreferenceEditorBoundary.() -> Unit): Boolean {
        val editor = preferences.edit()
        val boundary = object : PreferenceEditorBoundary {
            override fun putString(key: String, value: String) { editor.putString(key, value) }
            override fun putInt(key: String, value: Int) { editor.putInt(key, value) }
            override fun remove(key: String) { editor.remove(key) }
        }
        boundary.block()
        return editor.commit()
    }
    override fun snapshot(): Map<String, Any?> = preferences.all
    override fun restore(snapshot: Map<String, Any?>, keys: Set<String>) {
        val editor = preferences.edit()
        keys.forEach { key ->
            when (val value = snapshot[key]) {
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Boolean -> editor.putBoolean(key, value)
                null -> editor.remove(key)
            }
        }
        editor.apply()
    }
}

private fun HudCausalCodecException.refusal(): Refusal = Refusal(
    code = RefusalCode.INVALID_REQUEST,
    fix = fix,
)
