package dev.phosphor.mobil3.settings

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.json.JSONObject
import java.security.MessageDigest

class SettingsArchiveTest {
    private val metadata = arrayOf(
        "dev.phosphor.mobil3",
        "2.0.0",
        "local_dev",
        "2026-07-25T23:45:00Z",
    )

    @Test
    fun roundTripIsDeterministicAndAllowlisted() {
        val preferences = linkedMapOf<String, Any>(
            "room" to "blossom_dark",
            "gain" to 1.25f,
            "grid" to true,
            "mode" to 4,
            "remote_network_mode" to 2,
            "host" to "private-host",
            "consent_seen" to true,
        )
        val first = SettingsArchive.export(metadata[0], metadata[1], metadata[2], metadata[3], preferences)
        val second = SettingsArchive.export(metadata[0], metadata[1], metadata[2], metadata[3], preferences.toSortedMap())
        assertEquals(first.contentSha256, second.contentSha256)
        assertEquals(listOf("gain", "grid", "mode", "room"), first.exportedKeys)
        assertEquals(listOf("consent_seen", "host", "remote_network_mode"), first.skippedKeys)
        assertFalse(first.json.contains("private-host"))

        val imported = SettingsArchive.decode(first.json)
        assertEquals(4, imported.values["mode"])
        assertEquals(1.25f, imported.values["gain"])
        assertEquals("blossom_dark", imported.values["room"])
        assertTrue(imported.skippedKeys.isEmpty())
    }

    @Test
    fun tamperingIsRejected() {
        val archive = SettingsArchive.export(
            metadata[0], metadata[1], metadata[2], metadata[3], mapOf("gain" to 1.25f)
        ).json
        val tampered = archive.replace("1.25", "6.25")
        val error = assertFailsWith<SettingsArchive.ArchiveException> { SettingsArchive.decode(tampered) }
        assertEquals("checksum_mismatch", error.error)
    }

    @Test
    fun provenanceMetadataTamperingIsRejected() {
        val archive = SettingsArchive.export(
            metadata[0], metadata[1], metadata[2], metadata[3], mapOf("gain" to 1.25f)
        ).json
        val tampered = archive.replace("local_dev", "fortress")
        val error = assertFailsWith<SettingsArchive.ArchiveException> { SettingsArchive.decode(tampered) }
        assertEquals("checksum_mismatch", error.error)
    }

    @Test
    fun exportTimestampMustBeRfc3339() {
        val error = assertFailsWith<SettingsArchive.ArchiveException> {
            SettingsArchive.export(metadata[0], metadata[1], metadata[2], "yesterday", mapOf("grid" to true))
        }
        assertEquals("metadata_invalid", error.error)
    }

    @Test
    fun unknownMajorIsRejectedWithFix() {
        val archive = SettingsArchive.export(
            metadata[0], metadata[1], metadata[2], metadata[3], mapOf("grid" to true)
        ).json.replace(SettingsArchive.SCHEMA, "phosphor.settings/9")
        val error = assertFailsWith<SettingsArchive.ArchiveException> { SettingsArchive.decode(archive) }
        assertEquals("unsupported_schema", error.error)
        assertTrue(error.fix.isNotBlank())
    }

    @Test
    fun unknownScalarFieldIsVerifiedThenSkipped() {
        val exported = SettingsArchive.export(
            metadata[0], metadata[1], metadata[2], metadata[3], mapOf("grid" to true)
        )
        val root = JSONObject(exported.json)
        val settings = root.getJSONObject("settings")
        settings.put("future_field", "inert")
        val canonical = "{" +
            "\"exported_at\":\"${metadata[3]}\"," +
            "\"schema\":\"${SettingsArchive.SCHEMA}\"," +
            "\"settings\":{\"future_field\":\"inert\",\"grid\":true}," +
            "\"source_distribution\":\"${metadata[2]}\"," +
            "\"source_package\":\"${metadata[0]}\"," +
            "\"source_version\":\"${metadata[1]}\"}"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        root.put("content_sha256", digest)
        val imported = SettingsArchive.decode(root.toString())
        assertTrue(imported.values.containsKey("grid"))
        assertEquals(listOf("future_field"), imported.skippedKeys)
    }

    @Test
    fun nestedOrExecutableShapedValueIsRejected() {
        val exported = SettingsArchive.export(
            metadata[0], metadata[1], metadata[2], metadata[3], mapOf("grid" to true)
        )
        val root = JSONObject(exported.json)
        root.getJSONObject("settings").put("future", JSONObject().put("script", "run"))
        val error = assertFailsWith<SettingsArchive.ArchiveException> { SettingsArchive.decode(root.toString()) }
        assertEquals("non_inert_value", error.error)
    }

    @Test
    fun executableShapedTopLevelFieldIsRejectedRatherThanIgnored() {
        val exported = SettingsArchive.export(
            metadata[0], metadata[1], metadata[2], metadata[3], mapOf("grid" to true)
        )
        val root = JSONObject(exported.json)
        root.put("script", JSONObject().put("command", "run"))
        val error = assertFailsWith<SettingsArchive.ArchiveException> { SettingsArchive.decode(root.toString()) }
        assertEquals("unknown_archive_field", error.error)
    }

    @Test
    fun knownOutOfRangeValueIsRejectedEvenWithAValidArchiveHash() {
        val error = assertFailsWith<SettingsArchive.ArchiveException> {
            SettingsArchive.export(metadata[0], metadata[1], metadata[2], metadata[3], mapOf("gain" to 99f))
        }
        assertEquals("invalid_setting_value", error.error)
    }
}
