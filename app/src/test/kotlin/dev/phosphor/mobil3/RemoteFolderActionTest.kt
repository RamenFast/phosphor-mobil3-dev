package dev.phosphor.mobil3

import dev.phosphor.mobil3.ui.RemoteFolderAction
import dev.phosphor.mobil3.ui.RemoteBrowseRequest
import dev.phosphor.mobil3.ui.readRemoteListing
import org.junit.Assert.*
import org.junit.Test

class RemoteFolderActionTest {
    private class Fixture(root: String = "music", path: String = "", generation: Int = 18) {
        var peer: Pair<String, Int>? = "fixture-a" to 45777
        var request: RemoteBrowseRequest? = RemoteBrowseRequest(root, path, peer!!, 17)
        var requested: Pair<String, String>
            get() = request!!.let { it.root to it.path }
            set(value) { request = RemoteBrowseRequest(value.first, value.second, peer!!, 17) }
        val calls = mutableListOf<String>()
        val action = RemoteFolderAction(root, path, generation, request!!, { request }, { peer },
            { r, p -> calls += "browse:$r:$p"; requested = r to p },
            { r, p -> calls += "play:$r:$p" },
            { calls += "dismiss" },
        )
    }

    @Test fun rootFolderPlaysEmptyPathAndDismisses() {
        val f = Fixture()
        assertTrue(f.action.accepted)
        f.action.playFolder()
        assertEquals(listOf("play:music:", "dismiss"), f.calls)
    }

    @Test fun nestedFolderPlaysWholeDisplayedPath() {
        val f = Fixture(path = "Albums/Disc")
        f.action.playFolder()
        assertEquals(listOf("play:music:Albums/Disc", "dismiss"), f.calls)
    }

    @Test fun directoryBrowsesWithoutPlayingOrDismissing() {
        val f = Fixture(path = "Albums")
        f.action.directory("album.wav")
        assertEquals(listOf("browse:music:Albums/album.wav"), f.calls)
    }

    @Test fun filePlaysDirectPathAndDismisses() {
        val f = Fixture(path = "Albums/Disc")
        f.action.file("02.WAV")
        assertEquals(listOf("play:music:Albums/Disc/02.WAV", "dismiss"), f.calls)
    }

    @Test fun rootDirectoryAndFileKeepPathsRelative() {
        val folder = Fixture()
        folder.action.directory("Albums")
        assertEquals(listOf("browse:music:Albums"), folder.calls)
        val file = Fixture()
        file.action.file("01.wav")
        assertEquals(listOf("play:music:01.wav", "dismiss"), file.calls)
    }

    @Test fun upBrowsesParentAndRootUpDoesNothing() {
        val nested = Fixture(path = "Albums/Disc")
        nested.action.up()
        assertEquals(listOf("browse:music:Albums"), nested.calls)
        val first = Fixture(path = "Albums")
        first.action.up()
        assertEquals(listOf("browse:music:"), first.calls)
        val root = Fixture()
        root.action.up()
        assertTrue(root.calls.isEmpty())
    }

    @Test fun rootSwitchRejectsOldListingAndOldCallbacks() {
        val f = Fixture(path = "Albums")
        f.requested = "other" to ""
        assertFalse(f.action.accepted)
        f.action.playFolder()
        f.action.directory("Disc")
        f.action.file("01.wav")
        f.action.up()
        assertTrue(f.calls.isEmpty())
        val refreshed = Fixture(root = "other")
        refreshed.action.playFolder()
        assertEquals(listOf("play:other:", "dismiss"), refreshed.calls)
    }

    @Test fun pendingNestedBrowseRejectsPreviousFolderAction() {
        val f = Fixture()
        f.action.directory("Albums")
        f.action.playFolder()
        assertFalse(f.action.accepted)
        assertEquals(listOf("browse:music:Albums"), f.calls)
    }

    @Test fun malformedNamesAndMissingRootDoNothing() {
        val f = Fixture()
        for (name in listOf("", ".hidden", "..", "a/b", "a\u0000b")) {
            f.action.directory(name)
            f.action.file(name)
        }
        assertTrue(f.calls.isEmpty())
        val missing = Fixture(root = "")
        assertFalse(missing.action.accepted)
        missing.action.playFolder()
        assertTrue(missing.calls.isEmpty())
    }

    @Test fun linuxBackslashFilenameIsNotAPathSeparator() {
        val f = Fixture(path = "album\\live")
        f.action.file("01\\take.WAV")
        assertEquals(listOf("play:music:album\\live/01\\take.WAV", "dismiss"), f.calls)
    }

    @Test fun cachedOrOlderMatchingListingDoesNotBecomeAccepted() {
        for (generation in listOf(0, 16, 17)) {
            val f = Fixture(generation = generation)
            assertFalse(f.action.accepted)
            f.action.playFolder()
            f.action.file("01.wav")
            assertTrue(f.calls.isEmpty())
        }
        assertTrue(Fixture(generation = 18).action.accepted)
    }

    @Test fun hostSelectionInvalidatesCallbacksBeforeReplacementConnects() {
        val f = Fixture(path = "Albums")
        f.request = null
        // The actual native peer may still be A while B's source request is queued.
        f.action.playFolder()
        f.action.directory("Disc")
        f.action.file("01.wav")
        f.action.up()
        assertTrue(f.calls.isEmpty())
        f.peer = "fixture-b" to 45777
        f.request = RemoteBrowseRequest("music", "Albums", f.peer!!, 18)
        f.action.playFolder()
        assertFalse(f.action.accepted)
        assertTrue(f.calls.isEmpty())
        val replacement = RemoteFolderAction("music", "Albums", 19, f.request!!,
            { f.request }, { f.peer },
            { r, p -> f.calls += "browse:$r:$p" },
            { r, p -> f.calls += "play:$r:$p" },
            { f.calls += "dismiss" },
        )
        assertTrue(replacement.accepted)
        replacement.playFolder()
        assertEquals(listOf("play:music:Albums", "dismiss"), f.calls)
    }

    @Test fun callbackChecksCurrentPeerAndDisconnectWithoutWaitingForPoll() {
        val f = Fixture()
        for (peer in listOf("fixture-b" to 45777, "fixture-a" to 47888, null)) {
            f.peer = peer
            f.action.playFolder()
            f.action.file("01.wav")
            assertFalse(f.action.accepted)
        }
        assertTrue(f.calls.isEmpty())
    }

    @Test fun closeReopenAndRootRoundTripRejectIdenticalLookingOldRequests() {
        val f = Fixture()
        f.request = null
        f.requested = "music" to ""
        assertFalse(f.action.accepted)
        f.action.playFolder()
        val roundTrip = Fixture()
        roundTrip.requested = "other" to ""
        roundTrip.requested = "music" to ""
        roundTrip.action.playFolder()
        assertFalse(roundTrip.action.accepted)
        assertTrue(f.calls.isEmpty())
        assertTrue(roundTrip.calls.isEmpty())
    }

    @Test fun signedGenerationWrapStillRequiresOneNewListing() {
        val peer = "fixture-a" to 45777
        val request = RemoteBrowseRequest("music", "", peer, Int.MAX_VALUE)
        assertFalse(request.accepts("music", "", Int.MAX_VALUE, peer))
        assertFalse(request.accepts("music", "", Int.MAX_VALUE - 1, peer))
        assertTrue(request.accepts("music", "", Int.MIN_VALUE, peer))
        assertFalse(request.accepts("other", "", Int.MIN_VALUE, peer))
        assertFalse(request.accepts("music", "Disc", Int.MIN_VALUE, peer))
    }

    @Test fun changedGenerationReadCannotPairOldJsonWithNewRevision() {
        var generation = 17
        assertNull(readRemoteListing({ generation }, { generation++; "old listing" }))
        assertEquals(18 to "current listing", readRemoteListing({ generation }, { "current listing" }))
    }

    @Test fun rootSelectionBrowsesOnlyAnotherNonblankRoot() {
        val peer = "fixture-a" to 45777
        val request = RemoteBrowseRequest("music", "Albums", peer, 17)
        val calls = mutableListOf<String>()
        val browse: (String, String) -> Unit = { r, p -> calls += "browse:$r:$p" }
        request.selectRoot("", request, peer, true, browse)
        request.selectRoot("music", request, peer, true, browse)
        assertTrue(calls.isEmpty())
        request.selectRoot("archive", request, peer, true, browse)
        assertEquals(listOf("browse:archive:"), calls)
    }

    @Test fun retainedRootSelectionRejectsClosedReplacedAndChangedPeerContexts() {
        val peer = "fixture-a" to 45777
        val request = RemoteBrowseRequest("music", "Albums", peer, 17)
        val replacement = RemoteBrowseRequest("music", "Albums", peer, 18)
        val calls = mutableListOf<String>()
        val browse: (String, String) -> Unit = { r, p -> calls += "browse:$r:$p" }
        request.selectRoot("archive", request, peer, false, browse)
        request.selectRoot("archive", null, peer, true, browse)
        request.selectRoot("archive", replacement, peer, true, browse)
        request.selectRoot("archive", request, "fixture-b" to 45777, true, browse)
        request.selectRoot("archive", request, "fixture-a" to 47888, true, browse)
        request.retire()
        request.selectRoot("archive", request, peer, true, browse)
        assertTrue(calls.isEmpty())
    }

    @Test fun playRetiresAllRequestCallbacksBeforeDispatchAndDismiss() {
        val peer = "fixture-a" to 45777
        val request = RemoteBrowseRequest("music", "Albums", peer, 17)
        val calls = mutableListOf<String>()
        val browse: (String, String) -> Unit = { r, p -> calls += "browse:$r:$p" }
        lateinit var action: RemoteFolderAction
        action = RemoteFolderAction("music", "Albums", 18, request, { request }, { peer }, browse,
            { r, p ->
                assertFalse(action.accepted)
                request.selectRoot("archive", request, peer, true, browse)
                calls += "play:$r:$p"
            },
            { assertFalse(action.accepted); calls += "dismiss" },
        )
        action.playFolder()
        action.playFolder()
        action.file("01.wav")
        action.directory("Disc")
        action.up()
        assertEquals(listOf("play:music:Albums", "dismiss"), calls)
    }

    @Test fun requestRetirementDisablesRetainedFolderAndRootCallbacks() {
        val f = Fixture(path = "Albums")
        val request = f.request!!
        request.retire()
        request.retire()
        assertFalse(f.action.accepted)
        f.action.playFolder()
        f.action.file("01.wav")
        f.action.directory("Disc")
        f.action.up()
        request.selectRoot("archive", f.request, f.peer, true) { r, p -> f.calls += "browse:$r:$p" }
        assertTrue(f.calls.isEmpty())
    }

    @Test fun sourceSheetWiresRootAuthorityAndDismissalDisposalRetirement() {
        val source = java.io.File("src/main/kotlin/dev/phosphor/mobil3/ui/Sheets.kt").readText()
        assertTrue(source.contains("rootRequest?.selectRoot(id, browseRequest, currentPeer(), browsing, ::requestBrowse)"))
        assertTrue(source.contains("DisposableEffect(Unit)"))
        assertTrue(source.contains("onDispose { browseRequest?.retire() }"))
        assertTrue(source.contains("dismiss = { clearBrowse(); onDismiss() }"))
    }
}
