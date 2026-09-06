package dev.phosphor.mobil3.settings

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Reads the one user-facing value from the removed causal store, then lets the app erase it. */
internal object LegacySettingsMigration {
    const val CAUSAL_PREFERENCES = "phosphor.causal_state"
    const val LEGACY_TAILNET_PREFERENCES = "phosphor.pm3.tailnet"
    const val HUD_MODE = "hud_mode"
    const val NERD_HUD = "nerd_hud"
    const val CAUSAL_ENVELOPE = "__phosphor_private.hud_causal_envelope"
    const val PENDING_HUD_IMPORT = "__phosphor_private.pending_settings_import_hud"
    const val REMOTE_NETWORK_MODE = "remote_network_mode"
    const val LEGACY_TAILNET_KEY_ALIAS = "phosphor.pm3.tailnet-secret.v1"
    val PORTABLE_KEYS_TO_REMOVE = setOf(
        NERD_HUD,
        CAUSAL_ENVELOPE,
        PENDING_HUD_IMPORT,
        REMOTE_NETWORK_MODE,
    )

    fun resolveHudMode(
        portable: Map<String, *>,
        causal: Map<String, *>,
    ): Int {
        validMode(portable[HUD_MODE])?.let { return it }
        validMode(causal[HUD_MODE])?.let { return it }
        legacyBoolean(portable[NERD_HUD])?.let { return it }
        legacyBoolean(causal[NERD_HUD])?.let { return it }
        envelopeMode(causal[CAUSAL_ENVELOPE] as? String)?.let { return it }
        envelopeMode(portable[CAUSAL_ENVELOPE] as? String)?.let { return it }
        return HUD_AUTO
    }

    private fun validMode(value: Any?): Int? = (value as? Int)?.takeIf { it in HUD_ON..HUD_OFF }

    private fun legacyBoolean(value: Any?): Int? = when (value) {
        true -> HUD_ON
        false -> HUD_OFF
        else -> null
    }

    private fun envelopeMode(encoded: String?): Int? {
        if (encoded == null || encoded.toByteArray(Charsets.UTF_8).size > MAX_ENVELOPE_BYTES) return null
        return runCatching {
            val root = JSONObject(encoded)
            val schema = root.optString("schema")
            if (schema != "phosphor.causal.hud/1" && schema != "phosphor.causal.hud/2") return null
            val payload = root.optJSONObject("payload") ?: return null
            val checksum = root.optString("checksum_sha256")
            if (!checksum.matches(Regex("[0-9a-f]{64}"))) return null
            if (checksum != sha256(canonicalJson(payload))) return null
            when (payload.optString("display_hud")) {
                "on" -> HUD_ON
                "auto" -> HUD_AUTO
                "off" -> HUD_OFF
                else -> null
            }
        }.getOrNull()
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun canonicalJson(value: Any?): String = when {
        value == null || value === JSONObject.NULL -> "null"
        value is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(
            prefix = "{",
            postfix = "}",
            separator = ",",
        ) { key -> "${canonicalString(key)}:${canonicalJson(value.get(key))}" }
        value is JSONArray -> (0 until value.length()).joinToString(
            prefix = "[",
            postfix = "]",
            separator = ",",
        ) { index -> canonicalJson(value.get(index)) }
        value is String -> canonicalString(value)
        value is Boolean -> value.toString()
        value is Byte || value is Short || value is Int || value is Long -> value.toString()
        else -> error("unsupported legacy causal JSON value")
    }

    private fun canonicalString(value: String): String = buildString(value.length + 2) {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000c' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (
                    character.code < 0x20 ||
                    character.code in 0xd800..0xdfff ||
                    character == '\u2028' ||
                    character == '\u2029'
                ) {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
        append('"')
    }

    const val HUD_ON = 0
    const val HUD_AUTO = 1
    const val HUD_OFF = 2
    private const val MAX_ENVELOPE_BYTES = 512 * 1024
}
