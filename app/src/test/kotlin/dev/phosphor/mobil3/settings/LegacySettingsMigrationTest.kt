package dev.phosphor.mobil3.settings

import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.json.JSONObject
import org.junit.Test

class LegacySettingsMigrationTest {
    @Test fun emptyStoresResolveToAutoAndRemainAutoAfterArchiveRoundTrip() {
        val resolved = LegacySettingsMigration.resolveHudMode(emptyMap<String, Any>(), emptyMap<String, Any>())
        assertEquals(LegacySettingsMigration.HUD_AUTO, resolved)
        val exported = SettingsArchive.export(
            "dev.phosphor.mobil3.debug", "2.0.0-debug", "debug", "2026-09-06T00:00:00Z",
            mapOf(LegacySettingsMigration.HUD_MODE to resolved),
        )
        val imported = SettingsArchive.decode(exported.json).values
        assertEquals(LegacySettingsMigration.HUD_AUTO, imported[LegacySettingsMigration.HUD_MODE])
        assertEquals(
            LegacySettingsMigration.HUD_AUTO,
            LegacySettingsMigration.resolveHudMode(imported, emptyMap<String, Any>()),
        )
        // This executes the resolver and archive, not Application or SharedPreferences.
    }

    @Test fun everyExplicitPortableModeWinsAndSurvivesRepeatedResolution() {
        for (mode in LegacySettingsMigration.HUD_ON..LegacySettingsMigration.HUD_OFF) {
            for (causalMode in LegacySettingsMigration.HUD_ON..LegacySettingsMigration.HUD_OFF) {
                val portable = mapOf(LegacySettingsMigration.HUD_MODE to mode)
                val causal = mapOf(LegacySettingsMigration.HUD_MODE to causalMode)
                val resolved = LegacySettingsMigration.resolveHudMode(portable, causal)
                assertEquals(mode, resolved)
                assertEquals(mode, LegacySettingsMigration.resolveHudMode(
                    mapOf(LegacySettingsMigration.HUD_MODE to resolved), emptyMap<String, Any>(),
                ))
            }
        }
    }

    @Test fun validCausalModesAndBothLegacyBooleanSourcesKeepTheirPrecedence() {
        for (mode in LegacySettingsMigration.HUD_ON..LegacySettingsMigration.HUD_OFF) {
            assertEquals(mode, LegacySettingsMigration.resolveHudMode(
                mapOf(LegacySettingsMigration.NERD_HUD to false),
                mapOf(LegacySettingsMigration.HUD_MODE to mode),
            ))
        }
        for (value in listOf(false, true)) {
            val expected = if (value) LegacySettingsMigration.HUD_ON else LegacySettingsMigration.HUD_OFF
            assertEquals(expected, LegacySettingsMigration.resolveHudMode(
                mapOf(LegacySettingsMigration.NERD_HUD to value),
                mapOf(LegacySettingsMigration.NERD_HUD to !value),
            ))
            assertEquals(expected, LegacySettingsMigration.resolveHudMode(
                emptyMap<String, Any>(), mapOf(LegacySettingsMigration.NERD_HUD to value),
            ))
        }
    }

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
        val modes = listOf(
            "on" to LegacySettingsMigration.HUD_ON,
            "auto" to LegacySettingsMigration.HUD_AUTO,
            "off" to LegacySettingsMigration.HUD_OFF,
        )
        for (schema in listOf("phosphor.causal.hud/1", "phosphor.causal.hud/2")) {
            for ((label, expected) in modes) {
                val payload = JSONObject()
                    .put("display_hud", label)
                    .put("revision", 7)
                val encoded = JSONObject()
                    .put("schema", schema)
                    .put("payload", payload)
                    .put("checksum_sha256", sha256(canonicalPayload(payload)))
                    .toString()
                val envelope = mapOf(LegacySettingsMigration.CAUSAL_ENVELOPE to encoded)

                assertEquals(expected, LegacySettingsMigration.resolveHudMode(
                    portable = emptyMap<String, Any>(), causal = envelope,
                ), "$schema $label in causal store")
                assertEquals(expected, LegacySettingsMigration.resolveHudMode(
                    portable = envelope, causal = emptyMap<String, Any>(),
                ), "$schema $label in portable store")
            }
        }
    }

    @Test fun tamperedOrMalformedStateFallsBackToAutoWithoutAcceptingItsContents() {
        val tampered = JSONObject()
            .put("schema", "phosphor.causal.hud/2")
            .put("payload", JSONObject().put("display_hud", "on"))
            .put("checksum_sha256", "0".repeat(64))
            .toString()
        assertEquals(
            LegacySettingsMigration.HUD_AUTO,
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
