package dev.phosphor.mobil3

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The ADD / EDIT / REMOVE flow exactly as the sheet drives it.
 *
 * `RemoteHostEditor` hands back three raw strings and the caller turns them into a store
 * call, so the seam that actually ships is "three strings in, refusal text or success
 * out". These tests drive that seam directly, because a Compose sheet cannot be unit
 * tested here and blind coordinate-tapping on a device proves very little.
 *
 * The port is deliberately a String: it comes from a text field, and a user can type
 * anything into it.
 */
class RemoteHostEditorFlowTest {

    /**
     * Mirrors MainActivity.saveRemoteHost: parse the typed port, route to add or update,
     * and flatten the outcome to "null means saved, text means refused".
     */
    private fun save(
        store: RemoteHostStore,
        existingHost: String,
        existingPort: Int,
        label: String,
        host: String,
        port: String,
    ): String? {
        val parsedPort = port.trim().toIntOrNull()
            ?: return "Enter a port number from 1 through 65535."
        val outcome = if (existingHost.isEmpty()) {
            store.add(label, host, parsedPort)
        } else {
            store.update(existingHost, existingPort, label, host, parsedPort)
        }
        return when (outcome) {
            is RemoteHostOutcome.Saved -> null
            is RemoteHostOutcome.Refused -> "${outcome.message} ${outcome.fix}"
            is RemoteHostOutcome.Failed -> "${outcome.message} ${outcome.fix}"
        }
    }

    @Test
    fun aPlayUserWithNoSeedCanAddTheirFirstRelayAndReachIt() {
        // The whole point of this work: on Play the seed is empty, so without ADD there
        // is no way to reach any relay at all.
        val prefs = InMemoryHostPrefs()
        val store = RemoteHostStore(prefs, "")
        assertEquals(emptyList(), store.hosts())

        assertEquals(null, save(store, "", 0, "Studio PC", "studio.tailnet", "45777"))

        assertEquals(
            listOf(RemoteHost("Studio PC", "studio.tailnet", 45777)),
            store.hosts(),
        )
        // And it is durable, so the relay is still there after the process dies.
        assertEquals(
            listOf(RemoteHost("Studio PC", "studio.tailnet", 45777)),
            RemoteHostStore(prefs, "").hosts(),
        )
    }

    @Test
    fun theDefaultPortIsTheOneTheRelayActuallyListensOn() {
        // The editor prefills 45777. If that default were wrong, the common case would
        // fail for a user who never reads the protocol doc.
        val store = RemoteHostStore(InMemoryHostPrefs(), "")
        assertEquals(null, save(store, "", 0, "Desk", "desk.tailnet", "45777"))
        assertEquals(45777, store.hosts().single().port)
    }

    @Test
    fun everyWayAUserCanMistypeAPortIsRefusedWithAReadableFix() {
        val store = RemoteHostStore(InMemoryHostPrefs(), "")
        listOf("", "   ", "abc", "45777x", "1.5", "-1", "0", "65536", "99999999999") .forEach { bad ->
            val refusal = save(store, "", 0, "Desk", "desk.tailnet", bad)
            assertTrue(refusal != null && refusal.isNotBlank(), "port '$bad' should be refused")
        }
        assertEquals(emptyList(), store.hosts())
    }

    @Test
    fun editingARelayKeepsExactlyOneRowRatherThanAddingASecond() {
        val store = RemoteHostStore(InMemoryHostPrefs(), "")
        assertEquals(null, save(store, "", 0, "Old name", "box.tailnet", "45777"))

        assertEquals(
            null,
            save(store, "box.tailnet", 45777, "New name", "box.tailnet", "45777"),
        )

        assertEquals(listOf(RemoteHost("New name", "box.tailnet", 45777)), store.hosts())
    }

    @Test
    fun editingCanMoveARelayToADifferentAddress() {
        val store = RemoteHostStore(InMemoryHostPrefs(), "")
        assertEquals(null, save(store, "", 0, "Desk", "old.tailnet", "45777"))

        assertEquals(null, save(store, "old.tailnet", 45777, "Desk", "new.tailnet", "45778"))

        assertEquals(listOf(RemoteHost("Desk", "new.tailnet", 45778)), store.hosts())
    }

    @Test
    fun addingTheSameEndpointTwiceNamesTheRelayAlreadyUsingIt() {
        val store = RemoteHostStore(InMemoryHostPrefs(), "")
        assertEquals(null, save(store, "", 0, "Studio", "studio.tailnet", "45777"))

        val refusal = save(store, "", 0, "Studio again", "studio.tailnet", "45777")

        assertTrue(refusal != null && refusal.contains("Studio"), "refusal should name the existing relay: $refusal")
        assertEquals(1, store.hosts().size)
    }

    @Test
    fun removingASeededRelayIsDurableAndDoesNotComeBack() {
        // Seeding is one-shot on purpose: deleting a compiled endpoint is a real user
        // decision, not something the next launch quietly undoes.
        val prefs = InMemoryHostPrefs()
        val seed = "thinkcenter:100.66.109.56:45777,interserve-linux:100.114.165.77:45777"
        val store = RemoteHostStore(prefs, seed)
        assertEquals(2, store.hosts().size)

        assertIs<RemoteHostOutcome.Saved>(store.remove("100.66.109.56", 45777))

        assertEquals(listOf(RemoteHost("interserve-linux", "100.114.165.77", 45777)), store.hosts())
        // Re-seeding with the same build config must not resurrect it.
        assertEquals(
            listOf(RemoteHost("interserve-linux", "100.114.165.77", 45777)),
            RemoteHostStore(prefs, seed).hosts(),
        )
    }

    @Test
    fun anIpv6LiteralIsRefusedClearlyRatherThanCorruptingTheList() {
        // ':' is the field separator, so an IPv6 literal would split into nonsense. The
        // refusal has to explain that rather than silently mangling the entry.
        val store = RemoteHostStore(InMemoryHostPrefs(), "")

        val refusal = save(store, "", 0, "v6 box", "fd7a:115c:a1e0::1", "45777")

        assertTrue(refusal != null && refusal.isNotBlank())
        assertEquals(emptyList(), store.hosts())
    }

    @Test
    fun aRefusedEntryLeavesTheWorkingRelaysUntouched() {
        // The user is usually adding a second relay while the first one is in use. A bad
        // entry must not disturb what already works.
        val prefs = InMemoryHostPrefs()
        val store = RemoteHostStore(prefs, "interserve-linux:100.114.165.77:45777")

        assertTrue(save(store, "", 0, "", "", "nope") != null)

        assertEquals(listOf(RemoteHost("interserve-linux", "100.114.165.77", 45777)), store.hosts())
        assertEquals(
            listOf(RemoteHost("interserve-linux", "100.114.165.77", 45777)),
            RemoteHostStore(prefs, "").hosts(),
        )
    }

    @Test
    fun aStorageFailureIsReportedRatherThanLookingLikeASave() {
        // Silently reporting success on a failed write would leave the user with a relay
        // that vanishes on next launch and no idea why.
        val prefs = InMemoryHostPrefs()
        val store = RemoteHostStore(prefs, "")
        prefs.failWrites = true

        val refusal = save(store, "", 0, "Studio", "studio.tailnet", "45777")

        assertTrue(refusal != null && refusal.isNotBlank())
        prefs.failWrites = false
        assertEquals(emptyList(), RemoteHostStore(prefs, "").hosts())
    }
}
