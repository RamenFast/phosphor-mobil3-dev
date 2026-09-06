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
        val terminals = mutableListOf<PlaybackTruth.Terminal>()
        val confirmedItems = mutableListOf<Long>()
        var nativeOpen = 0L
        fun open(path: String, filename: String, isLatest: () -> Boolean) {
            truth.opened(path, filename, isLatest, ++nativeOpen, newItem = true).invoke()
        }
        fun prepare(newItem: Boolean = true, latest: () -> Boolean = { true }): () -> Boolean =
            truth.opened("/same.flac", "same.flac", latest, ++nativeOpen, newItem)
        var json = """{"path":"/same.flac","title":"tag title","artist":"tag artist","duration_ms":1234}"""
        fun start(path: String = "/same.flac", openId: Long = nativeOpen) {
            events.add(JSONObject().put("event", "track_started").put("path", path).put("open_id", openId).toString())
        }
        fun poll() = truth.poll(
            { worker.add(it) }, { main.add(it) },
            {
                reads++
                events.removeFirstOrNull()?.let { raw -> JSONObject(raw).also {
                    if (!it.has("open_id")) it.put("open_id", nativeOpen)
                }.toString() }
            }, { json }, { byteArrayOf(1, 2) }, { published.add(it) },
            { result, current ->
                assertTrue(current())
                terminals.add(result)
                if (result == PlaybackTruth.Terminal.START_FAILED) { failedOpens++; truth.closed() }
            },
            { confirmedItems.add(it) },
        )
        fun drain() {
            while (worker.isNotEmpty()) worker.removeFirst().invoke()
            while (main.isNotEmpty()) main.removeFirst().invoke()
        }
    }

    @Test fun onlyStartedCurrentPublishedNewItemConfirmsOnceIncludingSamePathReplay() {
        val h = Harness()
        val first = h.prepare()
        assertTrue(first())
        assertTrue(h.confirmedItems.isEmpty()) // deckOpen and main publication alone are not decoder proof.
        h.start(); h.poll(); h.drain()
        assertEquals(listOf(1L), h.confirmedItems)
        repeat(3) { h.start(); h.poll(); h.drain() }
        h.truth.publishCurrent { h.published.add(it) }
        assertEquals(listOf(1L), h.confirmedItems)
        assertTrue(h.prepare().invoke()) // Explicit replay is a new item despite identical path.
        h.start(); h.poll(); h.drain()
        assertEquals(listOf(1L, 2L), h.confirmedItems)
        assertFalse(first())
    }

    @Test fun sameItemSeekAndRetainedFailedSelectionNeverCreatePeakBoundary() {
        val h = Harness()
        assertTrue(h.prepare().invoke())
        h.start(); h.poll(); h.drain()
        assertTrue(h.prepare(newItem = false).invoke())
        h.start(); h.poll(); h.drain()
        h.truth.retain { true } // Failed new-file preflight preserves the audible item.
        h.start(); h.poll(); h.drain()
        h.truth.publishCurrent { h.published.add(it) }
        assertEquals(listOf(1L), h.confirmedItems)
    }

    @Test fun unprovenFailedClosedOrUnpublishedNativeOpensCannotResetPeak() {
        for (failure in listOf("playback_ended", "playback_drained", "output_error")) {
            val h = Harness()
            assertTrue(h.prepare().invoke())
            h.events.add("""{"event":"$failure","path":"/same.flac"}""")
            h.poll(); h.drain()
            h.start(); h.poll(); h.drain()
            assertTrue(h.confirmedItems.isEmpty())
        }
        val h = Harness()
        val publish = h.prepare()
        h.start(); h.poll(); h.drain()
        assertTrue(h.confirmedItems.isEmpty())
        h.truth.closed()
        assertFalse(publish())
        h.start(); h.poll(); h.drain()
        assertTrue(h.confirmedItems.isEmpty())
    }

    @Test fun staleNativeEventAndDelayedMainProofCannotResetNewerSamePathItem() {
        val h = Harness()
        assertTrue(h.prepare().invoke())
        h.start(); h.poll(); h.worker.removeFirst().invoke()
        assertTrue(h.prepare().invoke())
        h.drain()
        assertTrue(h.confirmedItems.isEmpty())
        h.start(openId = 1); h.poll(); h.drain() // Old path matches but native identity does not.
        h.events.add("""{"event":"output_error","path":"/same.flac","open_id":1}""")
        h.poll(); h.drain()
        assertTrue(h.confirmedItems.isEmpty())
        assertTrue(h.terminals.isEmpty())
        h.start(); h.poll(); h.drain()
        assertEquals(listOf(2L), h.confirmedItems)
    }

    @Test fun latestRequestSlotRejectsUnpublishedAndAlreadyReadNewItemProof() {
        val h = Harness()
        val tasks = ArrayDeque<() -> Unit>()
        var publish: (() -> Boolean)? = null
        val slot = LatestRequestSlot<Boolean>({ tasks.add(it) }) { newItem, latest ->
            publish = h.prepare(newItem, latest)
        }
        slot.enqueue(true); tasks.removeFirst().invoke()
        val oldPublish = publish!!
        slot.enqueue(false)
        assertFalse(oldPublish())
        tasks.removeFirst().invoke()
        assertTrue(publish.invoke())
        h.start(); h.poll(); h.worker.removeFirst().invoke()
        slot.enqueue(true)
        h.drain()
        assertTrue(h.confirmedItems.isEmpty())
        tasks.removeFirst().invoke()
        assertTrue(publish.invoke())
        h.start(); h.poll(); h.drain()
        assertEquals(listOf(3L), h.confirmedItems)
    }

    @Test fun newItemConfirmationUsesServiceTruthNotActivityMetadataOrCapture() {
        // Source-only Android/JNI boundaries. Native delivery and Activity lifecycle are root gates.
        val base = File("src/main/kotlin/dev/phosphor/mobil3")
        val service = File(base, "PlaybackService.kt").readText()
        val activity = File(base, "MainActivity.kt").readText()
        val seek = service.substringAfter("if (sameTarget && request.positionMs != null)")
            .substringBefore("if (!releaseReaders(isLatest))")
        assertTrue(seek.contains("PhosphorNative.deckOpenIdentity(), newItem = false"))
        val fresh = service.substringAfter("if (!PhosphorNative.deckOpen(path))")
            .substringBefore("// End-of-track watcher")
        assertTrue(fresh.contains("PhosphorNative.deckOpenIdentity(), newItem = true"))
        assertTrue(service.contains("if (!publishOpen()) return@post"))
        assertEquals(1, Regex("PhosphorNative.confirmLocalItem").findAll(service).count())
        assertFalse(activity.contains("confirmLocalItem"))
        assertTrue(activity.contains("PhosphorNative.cycleAdvance()")) // Existing metadata-driven cycle is untouched.
        assertFalse(File(base, "CaptureService.kt").readText().contains("confirmLocalItem"))
        val native = File(base, "PhosphorNative.kt").readText()
        assertTrue(native.contains("external fun confirmLocalItem(openId: Long)"))
    }

    @Test fun tagsAreReadAfterStartedAndOnlyConsumedOnce() {
        val h = Harness()
        h.open("/same.flac", "original.flac") { true }
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
            h.open("/same.flac", "original.flac") { true }
            h.json = JSONObject().put("path", "/same.flac").put("title", title)
                .put("artist", " \u0000").put("album", JSONObject.NULL).put("duration_ms", 0).toString()
            h.start(); h.poll(); h.drain()
            assertEquals("original.flac", h.published.single().title)
            assertNull(h.published.single().artist)
            assertNull(h.published.single().album)
            assertNull(h.published.single().durationMs)
        }
        val h = Harness()
        h.open("/same.flac", "original.flac") { true }
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
            h.open("/same.flac", "original.flac", latest)
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
            h.open("/same.flac", "original.flac") { true }
            h.json = JSONObject().put("path", "/same.flac").put("duration_ms", duration).toString()
            h.start(); h.poll(); h.drain()
            val expected = duration.takeIf { it in 1..(Long.MAX_VALUE / 1000) }
            assertEquals(expected, h.published.single().durationMs)
            expected?.let { assertTrue(it * 1000 > 0) }
        }
    }

    @Test fun samePathOpenIdentityRejectsOldCallbackEvenWhenBothPredicatesAreTrue() {
        val h = Harness()
        h.open("/same.flac", "old.flac") { true }
        h.start(); h.poll(); h.worker.removeFirst().invoke()
        h.open("/same.flac", "new.flac") { true }
        h.drain()
        assertTrue(h.published.isEmpty())
    }

    @Test fun failedNativeOpenClearsPendingAndCachedMetadata() {
        val h = Harness()
        h.open("/same.flac", "old.flac") { true }
        h.start(); h.poll(); h.worker.removeFirst().invoke()
        h.truth.closed()
        h.truth.retain { true }
        h.drain()
        h.truth.publishCurrent { h.published.add(it) }
        assertTrue(h.published.isEmpty())
        h.open("/same.flac", "retry.flac") { true }
        h.start(); h.poll(); h.drain()
        assertEquals(1, h.published.size)
    }

    @Test fun failedPreflightCanRestoreOnlyTheSurvivingOpen() {
        val h = Harness()
        var current = true
        h.open("/same.flac", "old.flac") { current }
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
        h.open("/same.flac", "original.flac") { true }
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
        h.open("/same.flac", "original.flac") { current }
        current = false
        h.events.add("""{"event":"playback_ended","path":"/same.flac"}""")
        h.poll(); h.drain()
        assertEquals(0, h.failedOpens)
        h.truth.retain { true } // A failed newer preflight cannot resurrect an already failed decoder.
        h.poll(); h.drain()
        assertEquals(1, h.failedOpens)
        assertTrue(h.published.isEmpty())
        h.open("/same.flac", "retry.flac") { true }
        h.start(); h.poll(); h.drain()
        h.events.add("""{"event":"playback_ended","path":"/same.flac"}""")
        h.poll(); h.drain()
        assertEquals(1, h.failedOpens) // Ordinary EOF after TrackStarted is not an open failure.
        assertEquals(1, h.published.size)
    }

    @Test fun watcherHasAtMostOnePollIncludingWhileMainPublicationWaits() {
        val h = Harness()
        h.open("/same.flac", "original.flac") { true }
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

    @Test fun drainedAndOutputFailurePublishOnlyOnMainAndOnceForTheExactOpen() {
        for ((event, result) in listOf(
            "playback_drained" to PlaybackTruth.Terminal.ENDED,
            "output_error" to PlaybackTruth.Terminal.OUTPUT_FAILED,
        )) {
            val h = Harness()
            h.open("/same.flac", "track") { true }
            h.start(); h.poll(); h.drain()
            h.events.add("""{"event":"$event","path":"/same.flac"}""")
            h.poll(); h.worker.removeFirst().invoke()
            assertTrue(h.terminals.isEmpty())
            h.drain()
            assertEquals(listOf(result), h.terminals)
            repeat(3) {
                h.events.add("""{"event":"$event","path":"/same.flac"}""")
                h.poll(); h.drain()
            }
            assertEquals(listOf(result), h.terminals)
        }
    }

    @Test fun delayedTerminalCannotClearSamePathSeekReplacementEvenWithTrueOldPredicate() {
        for (event in listOf("playback_ended", "playback_drained", "output_error")) {
            val h = Harness()
            h.open("/same.flac", "old") { true }
            h.events.add("""{"event":"$event","path":"/same.flac"}""")
            h.poll(); h.worker.removeFirst().invoke()
            h.open("/same.flac", "seek replacement") { true }
            h.drain()
            assertTrue(h.terminals.isEmpty())
            h.start(); h.poll(); h.drain()
            assertEquals(1, h.published.size)
        }
    }

    @Test fun terminalSupersededBeforeDeliveryIsRetainedOnlyForTheSurvivingDeck() {
        val h = Harness()
        var latest = true
        h.open("/same.flac", "track") { latest }
        h.start(); h.poll(); h.drain()
        h.events.add("""{"event":"playback_drained","path":"/same.flac"}""")
        h.poll(); h.worker.removeFirst().invoke()
        latest = false
        h.drain()
        assertTrue(h.terminals.isEmpty())
        h.truth.retain { true }
        h.poll(); h.drain()
        assertEquals(listOf(PlaybackTruth.Terminal.ENDED), h.terminals)
        var republished = false
        h.truth.publishCurrent { republished = true }
        assertFalse(republished)
        h.truth.closed()
        h.truth.retain { true }
        h.poll(); h.drain()
        assertEquals(1, h.terminals.size)
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
        assertTrue(service.contains("val publishOpen = playbackTruth.opened("))
        assertTrue(service.contains("PhosphorNative.deckOpenIdentity(), newItem = true"))
    }
}
