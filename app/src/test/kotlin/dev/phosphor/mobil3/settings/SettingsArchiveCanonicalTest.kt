package dev.phosphor.mobil3.settings

import org.junit.Assert.*
import org.junit.Test

class SettingsArchiveCanonicalTest {
    @Test fun androidQuoteGoldenVectorsCoverAsciiEscapesAndRawUnicode() {
        val vectors = listOf(
            "" to "\"\"",
            "phosphor.settings/2" to "\"phosphor.settings\\/2\"",
            "\"\\/" to "\"\\\"\\\\\\/\"",
            "\u00e9\u4e2d\ud83d\udc22\u0085\u2028\u2029" to "\"\u00e9\u4e2d\ud83d\udc22\u0085\u2028\u2029\"",
            "\u0000" to "\"\\u0000\"",
            "\u0001" to "\"\\u0001\"",
            "\u0002" to "\"\\u0002\"",
            "\u0003" to "\"\\u0003\"",
            "\u0004" to "\"\\u0004\"",
            "\u0005" to "\"\\u0005\"",
            "\u0006" to "\"\\u0006\"",
            "\u0007" to "\"\\u0007\"",
            "\b" to "\"\\b\"",
            "\t" to "\"\\t\"",
            "\n" to "\"\\n\"",
            "\u000b" to "\"\\u000b\"",
            "\u000c" to "\"\\f\"",
            "\r" to "\"\\r\"",
            "\u000e" to "\"\\u000e\"",
            "\u000f" to "\"\\u000f\"",
            "\u0010" to "\"\\u0010\"",
            "\u0011" to "\"\\u0011\"",
            "\u0012" to "\"\\u0012\"",
            "\u0013" to "\"\\u0013\"",
            "\u0014" to "\"\\u0014\"",
            "\u0015" to "\"\\u0015\"",
            "\u0016" to "\"\\u0016\"",
            "\u0017" to "\"\\u0017\"",
            "\u0018" to "\"\\u0018\"",
            "\u0019" to "\"\\u0019\"",
            "\u001a" to "\"\\u001a\"",
            "\u001b" to "\"\\u001b\"",
            "\u001c" to "\"\\u001c\"",
            "\u001d" to "\"\\u001d\"",
            "\u001e" to "\"\\u001e\"",
            "\u001f" to "\"\\u001f\"",
        )
        vectors.forEach { (input, expected) -> assertEquals(expected, SettingsArchive.canonicalQuote(input)) }
    }

    @Test fun sanitizedAsusExportMetadataAndBooleanHaveExactAndroidChecksum() {
        // ASUS export metadata retained; all other settings removed. Hash fixed independently.
        val wire = "{\"exported_at\":\"2026-09-09T23:23:29.246446Z\",\"schema\":\"phosphor.settings/2\",\"settings\":{\"pin_screen_brightness\":true},\"source_distribution\":\"debug\",\"source_package\":\"dev.phosphor.mobil3.debug\",\"source_version\":\"2.0.0-debug\",\"content_sha256\":\"03b83151fdb68a597d73c9273a9a90fc18776e4eb1cbb37597d00f46efbf4a72\"}"
        val decoded = SettingsArchive.decode(wire)
        assertEquals(mapOf("pin_screen_brightness" to true), decoded.values)
        val exported = SettingsArchive.export(decoded.sourcePackage, decoded.sourceVersion,
            decoded.sourceDistribution, decoded.exportedAt, decoded.values)
        assertEquals("03b83151fdb68a597d73c9273a9a90fc18776e4eb1cbb37597d00f46efbf4a72", exported.contentSha256)
        assertEquals(decoded.values, SettingsArchive.decode(exported.json).values)
    }

    @Test fun actualOffDiagnosticVectorKeepsAndroidHashWithoutHostFallback() {
        val wire = """{"schema":"phosphor.settings/2","source_version":"2.0.0-debug","settings":{"pin_screen_brightness":false},"content_sha256":"a1912253a5ca97012c1e28abfcc510f74a74881c4247b06a3c0deb6135dc1394","source_package":"dev.phosphor.mobil3.debug","source_distribution":"debug","exported_at":"2026-09-09T23:15:00Z"}"""
        assertEquals(mapOf("pin_screen_brightness" to false), SettingsArchive.decode(wire).values)
        val hostOnly = wire.replace("a1912253a5ca97012c1e28abfcc510f74a74881c4247b06a3c0deb6135dc1394",
            "2e3f0f2cc976c120616db481ec0c4084bb54cafdb3f08aa5babf6d2da56db2ce")
        val error = assertThrows(SettingsArchive.ArchiveException::class.java) { SettingsArchive.decode(hostOnly) }
        assertEquals("checksum_mismatch", error.error)
        assertThrows(SettingsArchive.ArchiveException::class.java) {
            SettingsArchive.decode(wire.replace("false", "true"))
        }
    }
}
