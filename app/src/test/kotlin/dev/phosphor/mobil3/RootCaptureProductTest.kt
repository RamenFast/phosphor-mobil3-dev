package dev.phosphor.mobil3

import dev.phosphor.mobil3.settings.SettingsArchive
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CompletableFuture
import org.junit.Assert.*
import org.junit.Test

/** R01 unit contracts. Source assertions do not claim Android service/device acceptance. */
class RootCaptureProductTest {
    private val build = "a".repeat(64)
    private fun bytes(size: Int) = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
    private fun pcm(generation: Long = 7, sequence: Long = 0, rate: Int = 16000, channels: Int = 1, encoding: Int = 2, count: Int = 2) =
        bytes(104 + count * 2).putInt(10401).put(build.toByteArray()).putLong(generation).putInt(2)
            .putLong(sequence).putInt(rate).putInt(channels).putInt(encoding).putInt(count)
            .apply { repeat(count) { putShort(it.toShort()) } }.array()
    private fun progress(sequence: Long) = bytes(88).putInt(10401).put(build.toByteArray()).putLong(7).putInt(2).putLong(sequence).array()
    private fun source(path: String): String = listOf(File(path), File("app", path)).first { it.isFile }.readText()
    private fun main(name: String) = source("src/main/kotlin/dev/phosphor/mobil3/$name.kt")

    @Test fun rootOffAndUnacknowledgedSettingsCannotSelectOrAuthorizeRoot() {
        for (enabled in listOf(false, true)) for (ack in listOf(false, true)) for (standard in listOf(false, true)) {
            assertEquals(if (enabled && ack && !standard) CaptureBackend.ROOT else CaptureBackend.STANDARD,
                RootCapturePolicy.backend(enabled, ack, standard))
        }
        assertFalse(RootCapturePolicy.mayAuthorize(false, false))
        assertFalse(RootCapturePolicy.mayAuthorize(false, true))
        assertFalse(RootCapturePolicy.mayAuthorize(true, false))
        assertTrue(RootCapturePolicy.mayAuthorize(true, true))
        assertFalse(main("PhosphorApplication").contains("RootCaptureSession("))
        assertFalse(main("RootCaptureSettings").substringAfter("fun enabled(").substringBefore("fun enable(").contains("RootCaptureSession("))
    }

    @Test fun typedOwnershipCannotPretendRootHasProjectionOrStandardHasHelper() {
        for (mask in 0 until 16) {
            val running = mask and 1 != 0
            val projection = mask and 2 != 0
            val record = mask and 4 != 0
            val helper = mask and 8 != 0
            assertEquals(running && projection && record && !helper,
                RootCapturePolicy.owns(CaptureBackend.STANDARD, running, projection, record, helper))
            assertEquals(running && helper && !projection && !record,
                RootCapturePolicy.owns(CaptureBackend.ROOT, running, projection, record, helper))
        }
        assertFalse(SourceWakePolicy.root(false, true))
        assertFalse(SourceWakePolicy.root(true, false))
        assertTrue(SourceWakePolicy.root(true, true))
        assertFalse(SourceWakePolicy.capture(true, false))
    }

    @Test fun exactRootPcmFormatSequenceIdentityAndSizeFailClosed() {
        val stream = RootAudioProtocol.Stream(10401, build, 7, 2)
        assertArrayEquals(shortArrayOf(0, 1), stream.pcm(pcm()))
        assertEquals(1L, stream.sequence)
        assertThrows(IllegalArgumentException::class.java) { stream.pcm(pcm()) }
        for (invalid in listOf(pcm(generation = 6), pcm(sequence = 2), pcm(rate = 48000), pcm(channels = 2),
            pcm(encoding = 4), pcm(count = 161), pcm(count = 0), pcm().dropLast(1).toByteArray())) {
            assertThrows(IllegalArgumentException::class.java) { RootAudioProtocol.Stream(10401, build, 7, 2).pcm(invalid) }
        }
        assertThrows(IllegalArgumentException::class.java) { RootAudioProtocol.Stream(10402, build, 7, 2).pcm(pcm()) }
        assertThrows(IllegalArgumentException::class.java) { RootAudioProtocol.Stream(10401, "b".repeat(64), 7, 2).pcm(pcm()) }
    }

    @Test fun healthyIdleAdvancesReadProgressWithoutInventingPcm() {
        val stream = RootAudioProtocol.Stream(10401, build, 7, 2)
        repeat(100) { stream.progress(progress(it.toLong())) }
        assertEquals(0L, stream.sequence)
        assertEquals(100L, stream.progressSequence)
        assertThrows(IllegalArgumentException::class.java) { stream.progress(progress(99)) }
        assertThrows(IllegalArgumentException::class.java) { stream.progress(progress(101)) }
    }

    @Test fun splitAndCoalescedPrivateFramesRemainIndependent() {
        val select = RootAudioProtocol.select(7, 2)
        val heartbeat = RootAudioProtocol.control(1, 7)
        val decoder = RootAudioProtocol.Decoder()
        val frames = (select + heartbeat).asIterable().mapNotNull { decoder.byte(it.toInt() and 255) }
        assertEquals(listOf(20, 1), frames.map { it.first })
        assertEquals(12, frames[0].second.size)
        assertEquals(8, frames[1].second.size)
        decoder.eof()
        val partial = RootAudioProtocol.Decoder()
        heartbeat.dropLast(1).forEach { partial.byte(it.toInt() and 255) }
        assertThrows(IllegalArgumentException::class.java) { partial.eof() }
    }

    @Test fun normalizerPreservesAllChunkBoundariesAndExactTransportCounts() {
        val input = ShortArray(160) { (it * 397 - 31000).toShort() }
        val whole = RootPcmNormalizer().convert(input)
        assertEquals(960, whole.size)
        for (split in 1 until input.size) {
            val converter = RootPcmNormalizer()
            val combined = converter.convert(input.copyOfRange(0, split)) + converter.convert(input.copyOfRange(split, input.size))
            assertArrayEquals(whole, combined, 0f)
        }
        for (i in whole.indices step 2) {
            assertEquals(whole[i], whole[i + 1], 0f)
            assertTrue(whole[i] in -1f..1f)
        }
        assertThrows(IllegalArgumentException::class.java) { RootPcmNormalizer().convert(shortArrayOf()) }
    }

    @Test fun localRootKeysAreExcludedFromArchiveAndAllBackupPaths() {
        val archive = SettingsArchive.export("dev.phosphor.mobil3", "2.0.0", "release", "2026-09-08T00:00:00Z",
            mapOf("grid" to true, RootCapturePolicy.ENABLED to true, RootCapturePolicy.PROFILE_ACK to true))
        assertEquals(listOf(RootCapturePolicy.ENABLED, RootCapturePolicy.PROFILE_ACK), archive.skippedKeys)
        assertEquals(mapOf("grid" to true), SettingsArchive.decode(archive.json).values)
        for (path in listOf("backup_rules", "data_extraction_rules")) {
            val xml = source("src/main/res/xml/$path.xml")
            assertEquals(if (path == "backup_rules") 1 else 2, Regex("path=\"phosphor.runtime.xml\"").findAll(xml).count())
        }
        val settings = main("RootCaptureSettings")
        assertTrue(settings.contains("PhosphorApplication.RUNTIME_PREFERENCES_NAME"))
        assertFalse(settings.contains("PREFERENCES_NAME, Context.MODE_PRIVATE") && !settings.contains("RUNTIME_PREFERENCES_NAME"))
        val migration = main("PhosphorApplication").substringAfter("private val RUNTIME_PREFERENCE_KEYS")
        assertFalse(migration.contains(RootCapturePolicy.ENABLED))
        assertFalse(migration.contains(RootCapturePolicy.PROFILE_ACK))
    }

    @Test fun startupBranchPrecedesBothAndroidPermissionChainsAndExplicitBackendChanges() {
        val activity = main("MainActivity")
        val start = activity.substringAfter("private fun startCaptureBackend(").substringBefore("private fun launchCaptureConsent")
        assertTrue(start.indexOf("backend == CaptureBackend.ROOT") < start.indexOf("Manifest.permission.RECORD_AUDIO"))
        assertTrue(start.contains("withSourcesReleased(selection = selection)"))
        assertTrue(start.contains("CaptureService.currentStatus().backend == backend"))
        assertTrue(activity.contains("captureConsentNeeded(): Boolean = !RootCaptureSettings.enabled(this)"))
        assertTrue(activity.contains("startCaptureBackend(explicitStandard = true)"))
        assertFalse(main("RootCaptureSession").contains("MediaProjection"))
        assertFalse(main("RootCaptureSession").contains("RECORD_AUDIO"))
        assertFalse(main("RootCaptureSession").contains("startStandardCapture"))
    }

    @Test fun rootArmsOnlyAfterValidatedCurrentReadyAndRetiresBeforeReplacement() {
        val service = main("CaptureService")
        val root = service.substringAfter("private fun startRoot()").substringBefore("private fun finishCapture")
        assertFalse(root.substringBefore("ready = {").contains("setRingActive(true)"))
        val ready = root.substringAfter("ready = {").substringBefore("samples =")
        assertTrue(ready.indexOf("!cleanedUp") < ready.indexOf("setRingActive(true)"))
        assertTrue(ready.contains("rootSession === session && session.live"))
        assertTrue(ready.contains("localSourcePublication.accepts(sourceRevision)"))
        assertTrue(root.contains("if (running && rootSession === session && owner === this)"))
        assertTrue(service.contains("val ownedRing = running"))
        assertTrue(service.indexOf("val rootError = oldRoot?.awaitStop()") < service.indexOf("val readerError = ReaderStop.finish("))
        assertTrue(service.contains("retirement.add(this, stopCompletion.result)"))
        assertTrue(service.contains("RootCapturePolicy.owns(it.backend"))
        assertTrue(service.contains("PlaybackService.captureStopped(captureOwnerId)"))
        assertTrue(main("PlaybackService").contains("CaptureService.stopIntent(this)"))
    }

    @Test fun sharedRetirementRequiresActualCleanupAndDestroyedOwner() {
        val lifecycle = CaptureStopLifecycle()
        val retirement = SourceRetirement()
        retirement.add("root", lifecycle.result)
        val replacement = retirement.pending
        lifecycle.cleanupFinished(null)
        assertFalse(replacement.isDone)
        lifecycle.ownerDestroyed()
        assertNull(replacement.get())
        val failed = CompletableFuture.completedFuture<String?>("root cleanup unconfirmed")
        retirement.add("failed root", failed)
        assertEquals("root cleanup unconfirmed", retirement.pending.get())
    }

    @Test fun abandonedQueuedFixtureCannotAcquireOrStopAnotherOwner() {
        val gate = RootStartGate()
        var starts = 0
        val queued = { if (gate.accepts()) starts++ }
        gate.cancel()
        queued()
        assertEquals(0, starts)
        val service = main("CaptureService")
        assertTrue(service.contains("if (owner?.rootCheck === check) stopExisting() else CompletableFuture.completedFuture(null)"))
        val checks = source("src/debug/kotlin/dev/phosphor/mobil3/RootCaptureChecks.kt")
        assertFalse(checks.contains("CaptureService.stopExisting()"))
        assertTrue(checks.contains("handler.removeCallbacks(work)"))
        assertTrue(checks.contains("override fun accepts() = gate.accepts()"))
        assertTrue(checks.contains("RootCaptureService.cancelCheck(observer); CaptureService.stopCheck(observer)"))
    }

    @Test fun actualManifestRolesAndFixedDebugBoundaryRemainSeparate() {
        val manifest = source("src/main/AndroidManifest.xml")
        val root = manifest.substringAfter("android:name=\".RootCaptureService\"").substringBefore("</service>")
        assertTrue(root.contains("android:exported=\"false\""))
        assertTrue(root.contains("android:foregroundServiceType=\"specialUse\""))
        assertTrue(root.contains("android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"))
        assertFalse(root.contains("mediaProjection"))
        assertFalse(root.contains("mediaPlayback"))
        assertFalse(manifest.contains("ROOT_CAPTURE_SYSTEM_TEST"))
        assertTrue(main("CaptureService").contains("android.os.Build.VERSION.SDK_INT >= 34"))
        assertTrue(main("CaptureService").contains("else startForeground(NOTIF_ID, buildNotification())"))
    }

    @Test fun failedProductChecksRetainMeasuredSignalAndRingEvidence() {
        val checks = source("src/debug/kotlin/dev/phosphor/mobil3/RootCaptureChecks.kt")
            .substringAfter("fun run(context:").substringBefore("fun tone(context:")
        val cleanup = checks.substringAfter("} finally {")
        assertTrue(cleanup.contains("data.put(\"pcm\", signal.json()).put(\"native_ring\", snapshot ?: JSONObject.NULL)"))
        assertTrue(cleanup.indexOf("data.put(\"pcm\"") < cleanup.indexOf("write(context,"))
        assertTrue(checks.contains("signal.frames in 64000..80000"))
        assertTrue(checks.contains("signal.nonzero == 0L"))
    }
}
