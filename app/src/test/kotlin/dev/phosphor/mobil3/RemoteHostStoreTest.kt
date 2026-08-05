package dev.phosphor.mobil3

import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.Test

class RemoteHostStoreTest {
    @Test
    fun parseValidSeedWithMultipleHosts() {
        val store = RemoteHostStore(InMemoryHostPrefs(), "")

        val parsed = store.parseSeed("studio:studio.tailnet:9735,laptop:100.64.0.8:8080")

        assertEquals(
            listOf(
                RemoteHost("studio", "studio.tailnet", 9735),
                RemoteHost("laptop", "100.64.0.8", 8080),
            ),
            parsed,
        )
    }

    @Test
    fun parseSkipsMalformedEntriesAndKeepsGoodEntries() {
        val store = RemoteHostStore(InMemoryHostPrefs(), "")

        val parsed = store.parseSeed(
            "good:relay-one:9000,wrong-arity:relay-two,not-number:relay-three:nope," +
                "too:many:fields:8000,also-good:100.100.0.4:65535",
        )

        assertEquals(
            listOf(
                RemoteHost("good", "relay-one", 9000),
                RemoteHost("also-good", "100.100.0.4", 65535),
            ),
            parsed,
        )
    }

    @Test
    fun emptySeedProducesEmptyPlayHostList() {
        val store = RemoteHostStore(InMemoryHostPrefs(), "")

        assertEquals(emptyList(), store.parseSeed(""))
        assertEquals(emptyList(), store.hosts())
        assertIs<RemoteHostOutcome.Saved>(store.initialization)
    }

    @Test
    fun seedingRunsOnceAndDoesNotResurrectRemovedHost() {
        val prefs = InMemoryHostPrefs()
        val first = RemoteHostStore(prefs, "desktop:desktop.tailnet:9735")
        assertEquals(listOf(RemoteHost("desktop", "desktop.tailnet", 9735)), first.hosts())

        assertIs<RemoteHostOutcome.Saved>(first.remove("desktop.tailnet", 9735))
        val restarted = RemoteHostStore(prefs, "desktop:desktop.tailnet:9735")

        assertEquals(emptyList(), restarted.hosts())
    }

    @Test
    fun addUpdateAndRemoveRoundTripThroughPersistence() {
        val prefs = InMemoryHostPrefs()
        val first = RemoteHostStore(prefs, "")

        assertIs<RemoteHostOutcome.Saved>(first.add("Desk", "desk.tailnet", 9735))
        val afterAdd = RemoteHostStore(prefs, "ignored:seed:4000")
        assertEquals(listOf(RemoteHost("Desk", "desk.tailnet", 9735)), afterAdd.hosts())

        assertIs<RemoteHostOutcome.Saved>(
            afterAdd.update(
                existingHost = "desk.tailnet",
                existingPort = 9735,
                label = "Studio Desk",
                host = "studio-desk.tailnet",
                port = 10000,
            ),
        )
        val afterUpdate = RemoteHostStore(prefs, "")
        assertEquals(
            listOf(RemoteHost("Studio Desk", "studio-desk.tailnet", 10000)),
            afterUpdate.hosts(),
        )

        assertIs<RemoteHostOutcome.Saved>(afterUpdate.remove("studio-desk.tailnet", 10000))
        assertEquals(emptyList(), RemoteHostStore(prefs, "").hosts())
    }

    @Test
    fun hostsSurviveProcessDeathOverTheSamePreferences() {
        val prefs = InMemoryHostPrefs()
        val first = RemoteHostStore(prefs, "")
        assertIs<RemoteHostOutcome.Saved>(first.add("Office", "office-node", 7000))
        assertIs<RemoteHostOutcome.Saved>(first.add("Travel", "100.90.80.70", 7001))

        val restored = RemoteHostStore(prefs, "unrelated:seed:1234")

        assertEquals(first.hosts(), restored.hosts())
    }

    @Test
    fun tailscaleNamesAreNormalizedAndMalformedSuffixesAreRejected() {
        val store = RemoteHostStore(InMemoryHostPrefs(), "")

        val saved = assertIs<RemoteHostOutcome.Saved>(
            store.add("Studio", "STUDIO-DESK.EXAMPLE.TS.NET", 9735),
        )
        assertEquals("studio-desk.example.ts.net", saved.hosts.single().host)

        listOf(
            "bad/path.ts.net",
            ".ts.net",
            "-bad.example.ts.net",
            "bad..example.ts.net",
            "100.064.0.1",
        ).forEach { host ->
            assertIs<RemoteHostOutcome.Refused>(store.add("Invalid", host, 9735), host)
        }
    }

    @Test
    fun everyValidationRefusalCarriesANonBlankFix() {
        val store = RemoteHostStore(InMemoryHostPrefs(), "")
        assertIs<RemoteHostOutcome.Saved>(store.add("Existing", "existing-node", 9000))

        val refusals = listOf(
            store.add("   ", "relay", 8000),
            store.add("x".repeat(33), "relay", 8000),
            store.add("bad:label", "relay", 8000),
            store.add("bad,label", "relay", 8000),
            store.add("Relay", "   ", 8000),
            store.add("Relay", "relay node", 8000),
            store.add("Relay", "2001:db8::1", 8000),
            store.add("Relay", "relay,node", 8000),
            store.add("Relay", "192.168.1.5", 8000),
            store.add("Relay", "example.com", 8000),
            store.add("Relay", "relay", 0),
            store.add("Relay", "relay", 65536),
            store.add("Duplicate", "existing-node", 9000),
        )

        refusals.forEach { outcome ->
            val refusal = assertIs<RemoteHostOutcome.Refused>(outcome)
            assertTrue(refusal.fix.isNotBlank(), refusal.message)
        }
        val duplicate = assertIs<RemoteHostOutcome.Refused>(refusals.last())
        assertTrue(duplicate.fix.contains("Existing"))
    }

    @Test
    fun failedWriteIsReportedAndPreviousSnapshotRemainsPublished() {
        val prefs = InMemoryHostPrefs()
        val store = RemoteHostStore(prefs, "")
        assertIs<RemoteHostOutcome.Saved>(store.add("Stable", "stable-node", 7000))
        prefs.failNextWrite = true

        val result = store.add("Unsaved", "unsaved-node", 7001)

        val failure = assertIs<RemoteHostOutcome.Failed>(result)
        assertTrue(failure.fix.isNotBlank())
        assertEquals(listOf(RemoteHost("Stable", "stable-node", 7000)), store.hosts())
        assertEquals(store.hosts(), RemoteHostStore(prefs, "").hosts())
    }

    @Test
    fun initializationWriteFailureIsVisibleAndPublishesNoSeed() {
        val prefs = InMemoryHostPrefs().apply { failNextWrite = true }

        val store = RemoteHostStore(prefs, "Relay:relay-node:8000")

        val failure = assertIs<RemoteHostOutcome.Failed>(store.initialization)
        assertTrue(failure.fix.isNotBlank())
        assertEquals(emptyList(), store.hosts())
        assertEquals(
            listOf(RemoteHost("Relay", "relay-node", 8000)),
            RemoteHostStore(prefs, "Relay:relay-node:8000").hosts(),
        )
    }

    @Test
    fun awkwardLegalValuesRoundTripExactly() {
        val prefs = InMemoryHostPrefs()
        val store = RemoteHostStore(prefs, "")
        // Interior spaces and hyphens are legitimate and must survive verbatim.
        val awkward = RemoteHost("Living Room Scope", "living-room-scope.tailnet", 49152)

        assertIs<RemoteHostOutcome.Saved>(store.add(awkward.label, awkward.host, awkward.port))

        assertEquals(listOf(awkward), RemoteHostStore(prefs, "").hosts())
    }

    @Test
    fun surroundingWhitespaceIsTrimmedSoNearDuplicatesCannotAccumulate() {
        val prefs = InMemoryHostPrefs()
        val store = RemoteHostStore(prefs, "")

        // A user typing a padded label means the same endpoint as the unpadded one.
        assertIs<RemoteHostOutcome.Saved>(store.add("  Studio  ", "  studio.tailnet  ", 45777))
        assertEquals(
            listOf(RemoteHost("Studio", "studio.tailnet", 45777)),
            store.hosts(),
        )

        // Because the stored form is trimmed, re-adding the unpadded pair is a duplicate
        // rather than a second row that looks identical in the sheet.
        val refusal = store.add("Studio", "studio.tailnet", 45777)
        assertIs<RemoteHostOutcome.Refused>(refusal)
        assertTrue(refusal.fix.isNotBlank())
        assertEquals(1, store.hosts().size)

        // And the trimmed form is what survives a restart.
        assertEquals(
            listOf(RemoteHost("Studio", "studio.tailnet", 45777)),
            RemoteHostStore(prefs, "").hosts(),
        )
    }

    @Test
    fun rejectedDelimiterCannotCorruptThePersistedStore() {
        val prefs = InMemoryHostPrefs()
        val store = RemoteHostStore(prefs, "")
        val stable = RemoteHost("Stable host", "stable-node", 9735)
        assertIs<RemoteHostOutcome.Saved>(store.add(stable.label, stable.host, stable.port))

        assertIs<RemoteHostOutcome.Refused>(store.add("bad:label", "other-node", 9736))
        assertIs<RemoteHostOutcome.Refused>(store.add("Other", "bad,node", 9736))

        assertEquals(listOf(stable), store.hosts())
        assertEquals(listOf(stable), RemoteHostStore(prefs, "").hosts())
    }

    @Test
    fun dedicatedPreferenceFileNameIsStable() {
        assertEquals("remote_hosts", RemoteHostStore.PREFERENCES_NAME)
    }

    @Test
    fun relayAddressesStayInsideTheTailscaleBoundary() {
        val store = RemoteHostStore(InMemoryHostPrefs(), "")

        assertIs<RemoteHostOutcome.Saved>(store.add("MagicDNS", "studio-pc", 45777))
        assertIs<RemoteHostOutcome.Saved>(store.add("Full DNS", "studio.example.ts.net", 45777))
        assertIs<RemoteHostOutcome.Saved>(store.add("Tailnet IP", "100.127.255.254", 45777))
        assertIs<RemoteHostOutcome.Refused>(store.add("Private LAN", "192.168.1.5", 45777))
        assertIs<RemoteHostOutcome.Refused>(store.add("Public DNS", "example.com", 45777))
        assertIs<RemoteHostOutcome.Refused>(store.add("Outside range", "100.128.0.1", 45777))
    }

}
