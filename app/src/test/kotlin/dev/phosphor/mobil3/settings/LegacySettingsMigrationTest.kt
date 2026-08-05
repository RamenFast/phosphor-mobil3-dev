package dev.phosphor.mobil3.settings

import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.json.JSONObject
import org.junit.Test

class LegacySettingsMigrationTest {
    @Test fun portableHudModeHasPriority() {
        assertEquals(
            LegacySettingsMigration.HUD_AUTO,
            LegacySettingsMigration.resolveHudMode(
                portable = mapOf(LegacySettingsMigration.HUD_MODE to LegacySettingsMigration.HUD_AUTO),
                causal = mapOf(LegacySettingsMigration.HUD_MODE to LegacySettingsMigration.HUD_ON),
            ),
        )
    }

    @Test fun causalMirrorPreservesHudWhenPortableValueIsAbsent() {
        assertEquals(
            LegacySettingsMigration.HUD_ON,
            LegacySettingsMigration.resolveHudMode(
                portable = emptyMap<String, Any>(),
                causal = mapOf(LegacySettingsMigration.HUD_MODE to LegacySettingsMigration.HUD_ON),
            ),
        )
    }

    @Test fun legacyBooleanMapsToOnAndOff() {
        assertEquals(
            LegacySettingsMigration.HUD_ON,
            LegacySettingsMigration.resolveHudMode(
                portable = mapOf(LegacySettingsMigration.NERD_HUD to true),
                causal = emptyMap<String, Any>(),
            ),
        )
        assertEquals(
            LegacySettingsMigration.HUD_OFF,
            LegacySettingsMigration.resolveHudMode(
                portable = emptyMap<String, Any>(),
                causal = mapOf(LegacySettingsMigration.NERD_HUD to false),
            ),
        )
    }

    @Test fun checksumBoundEnvelopeProvidesLastResortHudValue() {
        val payload = JSONObject()
            .put("display_hud", "auto")
            .put("revision", 7)
        val encoded = JSONObject()
            .put("schema", "phosphor.causal.hud/2")
            .put("payload", payload)
            .put("checksum_sha256", sha256(canonicalPayload(payload)))
            .toString()

        assertEquals(
            LegacySettingsMigration.HUD_AUTO,
            LegacySettingsMigration.resolveHudMode(
                portable = emptyMap<String, Any>(),
                causal = mapOf(LegacySettingsMigration.CAUSAL_ENVELOPE to encoded),
            ),
        )
    }

    @Test fun tamperedOrMalformedStateFallsBackToOff() {
        val tampered = JSONObject()
            .put("schema", "phosphor.causal.hud/2")
            .put("payload", JSONObject().put("display_hud", "on"))
            .put("checksum_sha256", "0".repeat(64))
            .toString()
        assertEquals(
            LegacySettingsMigration.HUD_OFF,
            LegacySettingsMigration.resolveHudMode(
                portable = mapOf(LegacySettingsMigration.HUD_MODE to "on"),
                causal = mapOf(LegacySettingsMigration.CAUSAL_ENVELOPE to tampered),
            ),
        )
    }

    @Test fun scrubSetIncludesRetiredAuthorityAndPhysicalRouteState() {
        assertTrue(LegacySettingsMigration.NERD_HUD in LegacySettingsMigration.PORTABLE_KEYS_TO_REMOVE)
        assertTrue(LegacySettingsMigration.CAUSAL_ENVELOPE in LegacySettingsMigration.PORTABLE_KEYS_TO_REMOVE)
        assertTrue(LegacySettingsMigration.PENDING_HUD_IMPORT in LegacySettingsMigration.PORTABLE_KEYS_TO_REMOVE)
        assertTrue(LegacySettingsMigration.REMOTE_NETWORK_MODE in LegacySettingsMigration.PORTABLE_KEYS_TO_REMOVE)
    }

    private fun canonicalPayload(payload: JSONObject): String = payload.keys().asSequence()
        .toList()
        .sorted()
        .joinToString(prefix = "{", postfix = "}", separator = ",") { key ->
            val value = payload.get(key)
            val encoded = when (value) {
                is String -> JSONObject.quote(value)
                else -> value.toString()
            }
            "${JSONObject.quote(key)}:$encoded"
        }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
