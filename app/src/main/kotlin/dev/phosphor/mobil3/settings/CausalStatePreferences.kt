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
) : PhosphorStatePersistencePort {
    constructor(sharedPreferences: SharedPreferences, compiledBaseSnapshot: PhosphorStateSnapshot) : this(
        AndroidPreferenceBoundary(sharedPreferences),
        compiledBaseSnapshot,
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
                PhosphorStoreLoadResult.CorruptEnvelope(error.refusal())
            }
        }
        return PhosphorStoreLoadResult.LegacyBootstrap(
            hudModeRaw = rawLegacyInt(KEY_LEGACY_HUD_MODE),
            nerdHudRaw = rawLegacyBoolean(KEY_LEGACY_NERD_HUD),
        )
    }

    private fun rawLegacyInt(key: String): String? = when {
        !preferences.contains(key) -> null
        else -> preferences.getIntOrNull(key)?.toString() ?: INVALID_LEGACY_TYPE
    }

    private fun rawLegacyBoolean(key: String): String? = when {
        !preferences.contains(key) -> null
        else -> preferences.getBooleanOrNull(key)?.toString() ?: INVALID_LEGACY_TYPE
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
        val prior = preferences.snapshot().filterKeys { it == KEY_CAUSAL_ENVELOPE || it == KEY_LEGACY_HUD_MODE }
        val committed = preferences.editCommit {
            putString(KEY_CAUSAL_ENVELOPE, encoded)
            putInt(KEY_LEGACY_HUD_MODE, legacyMode)
        }
        return if (committed) {
            PhosphorStorePersistenceResult.Saved
        } else {
            preferences.restore(prior, setOf(KEY_CAUSAL_ENVELOPE, KEY_LEGACY_HUD_MODE))
            PhosphorStorePersistenceResult.Failed(
                Refusal(
                    code = RefusalCode.SYSTEM_UNAVAILABLE,
                    fix = "Retry after storage is available; the HUD causal image and rollback hud_mode were not committed.",
                    currentRevision = image.snapshot.revision,
                ),
            )
        }
    }

    companion object {
        const val PREFERENCES_NAME = "phosphor.prefs"
        const val KEY_CAUSAL_ENVELOPE = "__phosphor_private.hud_causal_envelope"
        const val KEY_LEGACY_HUD_MODE = "hud_mode"
        const val KEY_LEGACY_NERD_HUD = "nerd_hud"
        internal const val INVALID_LEGACY_TYPE = "__invalid_preference_type__"
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
