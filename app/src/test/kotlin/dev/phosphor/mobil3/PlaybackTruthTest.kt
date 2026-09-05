package dev.phosphor.mobil3

import org.json.JSONObject
import org.junit.Test
import java.io.File
import kotlin.test.*

class PlaybackTruthTest {
    private class Harness {
        val truth = PlaybackTruth()
        val worker = ArrayDeque<() -> Unit>()
        val main = ArrayDeque<() -> Unit>()
        val events = ArrayDeque<String>()
        val published = mutableListOf<PlaybackTruth.Metadata>()
        var reads = 0
        var failedOpens = 0
        var json = """{"path":"/same.flac","title":"tag title","artist":"tag artist","duration_ms":1234}"""
        fun start(path: String = "/same.flac") {
            events.add(JSONObject().put("event", "track_started").put("path", path).toString())
        }
        fun poll() = truth.poll(
            { worker.add(it) }, { main.add(it) },
            { reads++; events.removeFirstOrNull() }, { json }, { byteArrayOf(1, 2) }, { published.add(it) },
            { failedOpens++; truth.closed() },
        )
        fun drain() {
            while (worker.isNotEmpty()) worker.removeFirst().invoke()
            while (main.isNotEmpty()) main.removeFirst().invoke()
        }
    }

    @Test fun tagsAreReadAfterStartedAndOnlyConsumedOnce() {
        val h = Harness()
        h.truth.opened("/same.flac", "original.flac") { true }
        h.poll(); h.drain()
        assertTrue(h.published.isEmpty())
        h.start(); h.poll(); h.drain()
        assertEquals("tag title", h.published.single().title)
        assertEquals("tag artist", h.published.single().artist)
        assertEquals(1234L, h.published.single().durationMs)
        assertContentEquals(byteArrayOf(1, 2), h.published.single().artwork)
        h.poll(); h.drain()
        assertEquals(1, h.published.size)
    }

    @Test fun blankNullAndRiffTerminatedTagsHaveHonestFallbacks() {
        for (title in listOf(JSONObject.NULL, "", " \t", "\u0000", " \u0000 ")) {
            val h = Harness()
            h.truth.opened("/same.flac", "original.flac") { true }
            h.json = JSONObject().put("path", "/same.flac").put("title", title)
                .put("artist", " \u0000").put("album", JSONObject.NULL).put("duration_ms", 0).toString()
            h.start(); h.poll(); h.drain()
            assertEquals("original.flac", h.published.single().title)
            assertNull(h.published.single().artist)
            assertNull(h.published.single().album)
            assertNull(h.published.single().durationMs)
        }
        val h = Harness()
        h.truth.opened("/same.flac", "original.flac") { true }
        h.json = JSONObject().put("path", "/same.flac").put("title", "title\u0000")
            .put("artist", "artist\u0000").toString()
        h.start(); h.poll(); h.drain()
        assertEquals("title", h.published.single().title)
        assertEquals("artist", h.published.single().artist)
    }

    @Test fun samePathSupersededRequestCannotPublishAnAlreadyReadSnapshot() {
        val h = Harness()
        val requests = ArrayDeque<() -> Unit>()
        val slot = LatestRequestSlot<String>({ requests.add(it) }) { _, latest ->
            h.truth.opened("/same.flac", "original.flac", latest)
        }
        slot.enqueue("first-open"); requests.removeFirst().invoke()
        h.start(); h.poll(); h.worker.removeFirst().invoke()
        slot.enqueue("same-path-seek")
        while (h.main.isNotEmpty()) h.main.removeFirst().invoke()
        assertTrue(h.published.isEmpty())
        requests.removeFirst().invoke()
        h.start(); h.poll(); h.drain()
        assertEquals(1, h.published.size)
    }

    @Test fun localDurationMustFitMedia3MicrosecondsWithoutOverflow() {
        for (duration in listOf(-1L, 0L, 1L, 1234L, Long.MAX_VALUE / 1000,
            Long.MAX_VALUE / 1000 + 1, Long.MAX_VALUE)) {
            val h = Harness()
            h.truth.opened("/same.flac", "original.flac") { true }
            h.json = JSONObject().put("path", "/same.flac").put("duration_ms", duration).toString()
            h.start(); h.poll(); h.drain()
            val expected = duration.takeIf { it in 1..(Long.MAX_VALUE / 1000) }
            assertEquals(expected, h.published.single().durationMs)
            expected?.let { assertTrue(it * 1000 > 0) }
        }
    }

    @Test fun samePathOpenIdentityRejectsOldCallbackEvenWhenBothPredicatesAreTrue() {
        val h = Harness()
        h.truth.opened("/same.flac", "old.flac") { true }
        h.start(); h.poll(); h.worker.removeFirst().invoke()
        h.truth.opened("/same.flac", "new.flac") { true }
        h.drain()
        assertTrue(h.published.isEmpty())
    }

    @Test fun failedNativeOpenClearsPendingAndCachedMetadata() {
        val h = Harness()
        h.truth.opened("/same.flac", "old.flac") { true }
        h.start(); h.poll(); h.worker.removeFirst().invoke()
        h.truth.closed()
        h.truth.retain { true }
        h.drain()
        h.truth.publishCurrent { h.published.add(it) }
        assertTrue(h.published.isEmpty())
        h.truth.opened("/same.flac", "retry.flac") { true }
        h.start(); h.poll(); h.drain()
        assertEquals(1, h.published.size)
    }

    @Test fun failedPreflightCanRestoreOnlyTheSurvivingOpen() {
        val h = Harness()
        var current = true
        h.truth.opened("/same.flac", "old.flac") { current }
        h.start(); h.poll(); h.worker.removeFirst().invoke()
        current = false
        h.truth.retain { true }
        h.drain()
        assertTrue(h.published.isEmpty())
        h.truth.publishCurrent { h.published.add(it) }
        assertEquals("tag title", h.published.single().title)
    }

    @Test fun pathMismatchAndEndedNeverInventMetadataOrAdvance() {
        val h = Harness()
        h.truth.opened("/same.flac", "original.flac") { true }
        h.start("/other.flac"); h.poll(); h.drain()
        h.start(); h.json = """{"path":"/other.flac","title":"stale"}"""; h.poll(); h.drain()
        h.events.add("""{"event":"playback_ended","path":"/same.flac"}""")
        h.poll(); h.drain()
        assertTrue(h.published.isEmpty())
        assertTrue(h.events.isEmpty())
        assertEquals(0, h.failedOpens) // A matching TrackStarted was consumed, despite a mismatched payload.
    }

    @Test fun failedDecoderReopenClearsOnlyTheCurrentRequestAndCanRecover() {
        val h = Harness()
        var current = true
        h.truth.opened("/same.flac", "original.flac") { current }
        current = false
        h.events.add("""{"event":"playback_ended","path":"/same.flac"}""")
        h.poll(); h.drain()
        assertEquals(0, h.failedOpens)
        h.truth.retain { true } // A failed newer preflight cannot resurrect an already failed decoder.
        h.poll(); h.drain()
        assertEquals(1, h.failedOpens)
        assertTrue(h.published.isEmpty())
        h.truth.opened("/same.flac", "retry.flac") { true }
        h.start(); h.poll(); h.drain()
        h.events.add("""{"event":"playback_ended","path":"/same.flac"}""")
        h.poll(); h.drain()
        assertEquals(1, h.failedOpens) // Ordinary EOF after TrackStarted is not an open failure.
        assertEquals(1, h.published.size)
    }

    @Test fun watcherHasAtMostOnePollIncludingWhileMainPublicationWaits() {
        val h = Harness()
        h.truth.opened("/same.flac", "original.flac") { true }
        h.start()
        repeat(100) { h.poll() }
        assertEquals(1, h.worker.size)
        assertEquals(0, h.reads)
        h.worker.removeFirst().invoke()
        repeat(100) { h.poll() }
        assertTrue(h.worker.isEmpty())
        h.drain()
        h.poll(); h.drain()
        assertEquals(2, h.reads)
    }

    @Test fun successfulCurrentNativeSeekPublishesOnePositionDiscontinuity() {
        val base = File("src/main/kotlin/dev/phosphor/mobil3")
        val service = File(base, "PlaybackService.kt").readText()
        val player = File(base, "PhosphorPlayer.kt").readText()
        val seek = service.substringAfter("if (sameTarget && request.positionMs != null)")
            .substringBefore("if (!releaseReaders(isLatest))")
        val success = seek.substringAfter("} else {")
        assertFalse(seek.substringBefore("} else {").contains("onNativeSeekCompleted"))
        assertTrue(success.contains("if (!destroying && !stopping && isLatest())"))
        assertTrue(success.indexOf("deckPublish") < success.indexOf("onNativeSeekCompleted"))
        assertEquals(1, Regex("localPlayer.onNativeSeekCompleted\\(\\)").findAll(service).count())
        val publish = player.substringAfter("internal fun onNativeSeekCompleted()")
            .substringBefore("internal fun onTrackMetadata")
        assertTrue(publish.contains("nativeSeekPosition = PhosphorNative.deckPositionMs()"))
        assertTrue(publish.contains("invalidateState()"))
        assertTrue(player.contains("b.setPositionDiscontinuity(Player.DISCONTINUITY_REASON_SEEK, it)"))
        val consumed = player.substringAfter("nativeSeekPosition?.let {").substringBefore("}")
        assertTrue(consumed.contains("nativeSeekPosition = null"))
        assertTrue(player.substringAfter("fun setQueue(").substringBefore("fun queueSize")
            .contains("nativeSeekPosition = null"))
        assertTrue(player.substringAfter("override fun handleStop()").contains("nativeSeekPosition = null"))
    }

    @Test fun productionHasOneEventReaderOnExistingWatcherAndNoPlayerMetadataRead() {
        val base = File("src/main/kotlin/dev/phosphor/mobil3")
        val service = File(base, "PlaybackService.kt").readText()
        val player = File(base, "PhosphorPlayer.kt").readText()
        assertEquals(1, Regex("PhosphorNative::deckPollEvent").findAll(service).count())
        assertFalse(player.contains("PhosphorNative.deckMetadata"))
        assertFalse(player.contains("PhosphorNative.deckCoverArt"))
        val watcher = service.substringAfter("private fun startEndWatcher()").substringBefore("private fun startRemote")
        assertTrue(watcher.contains("playbackTruth.poll("))
        assertTrue(watcher.contains("localDeckExecutor.execute"))
        assertTrue(watcher.contains("localPlayer.onTrackMetadata"))
        assertTrue(service.contains("playbackTruth.retain(isLatest)"))
        assertTrue(service.contains("playbackTruth.opened(path, request.titles[index], isLatest)"))
    }
}
