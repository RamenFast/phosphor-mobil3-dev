package dev.phosphor.mobil3

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RootEpochTest {
    private val build = "a".repeat(64)
    private fun bytes(size: Int) = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
    private fun id(b: ByteBuffer, mode: Int = 2) = b.putInt(10401).put(build.toByteArray()).putLong(42).putInt(mode)
    private fun ack(control: Long = 1, epoch: Long = 0, next: Long = 0) = id(bytes(104)).putLong(control).putLong(epoch).putLong(next).array()
    private fun pcm(sequence: Long = 0, control: Long = 1, epoch: Long = 0, count: Int = 1) =
        id(bytes(120 + count * 2)).putLong(sequence).putInt(16000).putInt(1).putInt(2).putInt(count)
            .putLong(control).putLong(epoch).apply { repeat(count) { putShort(32767) } }.array()
    private fun stream(): RootAudioProtocol.Stream = RootAudioProtocol.Stream(10401, build, 42, 2).also {
        it.ready(JSONObject().put("pcm_epoch_schema", 1))
        it.epoch.desire(0)
        assertNotNull(it.request(0))
    }
    private fun started() = stream().also { it.ack(ack()); it.start() }
    private fun invalid(action: () -> Unit) { assertThrows(IllegalArgumentException::class.java, action) }

    @Test fun initialAckBeforeStartAndOpaqueEpochVectors() {
        val s = stream()
        invalid { s.start() }
        invalid { s.pcm(pcm()) }
        s.ack(ack())
        invalid { s.pcm(pcm()) }
        s.start()
        val batch = s.pcm(pcm())
        assertEquals(0, batch.readEpoch)
        assertEquals(1, batch.controlSequence)
        assertArrayEquals(shortArrayOf(32767), batch.samples)
        s.epoch.desire(Long.MIN_VALUE)
        val request = s.request(125)!!
        val b = ByteBuffer.wrap(request).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(36, request.size)
        assertEquals(4, b.getInt(4))
        assertEquals(Long.MIN_VALUE, b.getLong(28))
        invalid { s.pcm(pcm(1, 2, Long.MIN_VALUE)) }
        s.ack(ack(2, Long.MIN_VALUE, 1))
        invalid { s.pcm(pcm(1)) }
        assertEquals(Long.MIN_VALUE, s.pcm(pcm(1, 2, Long.MIN_VALUE, 160)).readEpoch)
    }

    @Test fun boundedCoalescingDeadlineAndRegression() {
        val s = started()
        s.epoch.desire(5)
        assertNull(s.request(124))
        assertNotNull(s.request(125))
        s.epoch.desire(6)
        s.epoch.desire(7)
        assertNull(s.request(1000))
        s.epoch.checkDeadline(1124)
        assertThrows(IllegalStateException::class.java) { s.epoch.checkDeadline(1125) }
        s.ack(ack(2, 5))
        assertEquals(7L, s.epoch.desired)
        assertNotNull(s.request(1125))
        assertEquals(7, s.epoch.pending!!.binding.epoch)
        invalid { s.epoch.desire(6) }
        s.ack(ack(3, 7))
        assertNull(s.request(1250))
    }

    @Test fun stoppedPendingAckAndResultUseProductionParser() {
        val s = started()
        s.epoch.desire(1)
        s.request(125)
        s.stop()
        s.ack(ack(2, 1))
        invalid { s.start() }
        invalid { s.request(250) }
        s.pcm(pcm(control = 2, epoch = 1))
        s.result()
        invalid { s.ack(ack(2, 1, 1)) }
        invalid { s.pcm(pcm(1, 2, 1)) }
        invalid { s.result() }
        val finite = started()
        finite.epoch.desire(1)
        finite.request(125)
        finite.result()
        assertNull(finite.epoch.pending)
        finite.epoch.checkDeadline(5000)
    }

    @Test fun malformedIdentityShapeSequenceAndModesFailClosed() {
        for (offset in listOf(0, 68, 76, 80, 88, 92, 96, 100, 104, 112)) {
            val data = pcm().also { it[offset] = (it[offset].toInt() xor 1).toByte() }
            invalid { started().pcm(data) }
        }
        for (bad in listOf(pcm(count = 0), pcm(count = 161), pcm().copyOf(106), pcm() + 0, pcm().dropLast(1).toByteArray())) {
            invalid { started().pcm(bad) }
        }
        for (offset in listOf(0, 68, 76, 80, 88, 96)) {
            val data = ack().also { it[offset] = (it[offset].toInt() xor 1).toByte() }
            invalid { stream().ack(data) }
        }
        val s = stream()
        s.ack(ack())
        invalid { s.ack(ack()) }
        for (mode in listOf(0, 1, 4, 5)) {
            val other = RootAudioProtocol.Stream(10401, build, 42, mode)
            invalid { other.ready(JSONObject().put("pcm_epoch_schema", 1)) }
            invalid { other.request(0) }
            invalid { other.ack(ack()) }
            invalid { other.pcm(pcm()) }
        }
        for (schema in listOf(0, 2, "1", 1.0)) {
            invalid { RootAudioProtocol.Stream(10401, build, 42, 2).ready(JSONObject().put("pcm_epoch_schema", schema)) }
        }
    }

    @Test fun normalizerRejectsOldBatchAndResetsOnlyEligibleTransition() {
        val visual = RootEpochNormalizer(Long.MIN_VALUE)
        val a = RootAudioProtocol.PcmBatch(shortArrayOf(32767), 0, 1, 4)
        assertNotNull(visual.convert(a, 4))
        assertNull(visual.convert(a, 5))
        val b = RootAudioProtocol.PcmBatch(ShortArray(160), 1, 2, 5)
        assertArrayEquals(FloatArray(960), visual.convert(b, 5), 0f)
        assertEquals(Long.MIN_VALUE, visual.owner)
        invalid { RootEpochNormalizer(0) }
        val input = ShortArray(160) { (it * 397 - 31000).toShort() }
        val whole = RootEpochNormalizer(1).convert(RootAudioProtocol.PcmBatch(input, 0, 1, 5), 5)
        for (split in 1..159) {
            val chunks = RootEpochNormalizer(1)
            val first = chunks.convert(RootAudioProtocol.PcmBatch(input.copyOfRange(0, split), 0, 1, 5), 5)!!
            // A stale batch cannot contaminate same-epoch interpolation state.
            assertNull(chunks.convert(a, 5))
            val next = chunks.convert(RootAudioProtocol.PcmBatch(input.copyOfRange(split, 160), 1, 1, 5), 5)!!
            assertArrayEquals(whole, first + next, 0f)
        }
    }

    @Test fun javaVectorsPassActualDecoderAtEverySplit() {
        val directory = System.getenv("ROOT_EPOCH_VECTORS") ?: return
        val names = listOf("initial-ack.bin", "old-pcm.bin", "new-ack.bin", "new-pcm.bin")
        val wire = names.map { File(directory, it).readBytes() }.reduce(ByteArray::plus)
        for (split in 0..wire.size) {
            val decoder = RootAudioProtocol.Decoder()
            val s = stream()
            var frames = 0
            fun consume(part: ByteArray) {
                for (byte in part) {
                    val frame = decoder.byte(byte.toInt() and 255) ?: continue
                    when (frames++) {
                        0 -> { s.ack(frame.second); s.start(); s.epoch.desire(Long.MIN_VALUE); s.request(125) }
                        1 -> assertEquals(0, s.pcm(frame.second).readEpoch)
                        2 -> s.ack(frame.second)
                        3 -> assertEquals(Long.MIN_VALUE, s.pcm(frame.second).readEpoch)
                    }
                }
            }
            consume(wire.copyOfRange(0, split))
            consume(wire.copyOfRange(split, wire.size))
            decoder.eof()
            assertEquals(4, frames)
            assertEquals(2, s.sequence)
        }
    }

    @Test fun lateAckRetainsBindingForCleanDrainButCannotRefreshDeadline() {
        val s = started()
        s.epoch.desire(1)
        s.request(125)
        assertThrows(RootEpochTimeout::class.java) { s.ack(ack(2, 1), 1125) }
        s.stop()
        assertNull(s.epoch.pending)
        assertEquals(1L, s.epoch.acknowledged!!.epoch)
        s.pcm(pcm(control = 2, epoch = 1))
        s.result()
        invalid { s.ack(ack(2, 1, 1)) }
        val zeroReads = started()
        for (epoch in 1L..8) {
            zeroReads.epoch.desire(epoch)
            zeroReads.request(epoch * 125)
            zeroReads.ack(ack(epoch + 1, epoch, 0), epoch * 125)
            assertEquals(0L, zeroReads.sequence)
        }
    }

    @Test fun sequenceExhaustionAndMissingCapabilityFailWithoutWrap() {
        invalid { RootAudioProtocol.Stream(10401, build, 42, 2).ready(JSONObject()) }
        val s = started()
        val field = RootAudioProtocol.Stream::class.java.getDeclaredField("sequence").apply { isAccessible = true }
        field.setLong(s, Long.MAX_VALUE)
        invalid { s.pcm(pcm(sequence = Long.MAX_VALUE)) }
        assertEquals(Long.MAX_VALUE, s.sequence)
        s.epoch.desire(1)
        RootEpoch::class.java.getDeclaredField("nextControl").apply { isAccessible = true }.setLong(s.epoch, Long.MAX_VALUE)
        invalid { s.request(125) }
        assertNull(s.epoch.pending)
    }

    @Test fun productionWiringKeepsOriginalOwnerAndPreReadSnapshot() {
        fun source(path: String) = listOf(File(path), File("..", path)).first { it.isFile }.readText()
        val service = source("app/src/main/kotlin/dev/phosphor/mobil3/CaptureService.kt").substringAfter("private fun startRoot()")
        assertTrue(service.contains("CompletableFuture<Long>()"))
        assertTrue(service.contains("val readOwner = PhosphorNative.setRingActive(true)"))
        assertTrue(service.contains("accepted.complete(readOwner)"))
        assertTrue(service.contains("pushCaptureRead(normalized, normalized.size, readOwner, batch.readEpoch)"))
        assertFalse(service.contains("pushCaptureSamples("))
        val session = source("app/src/main/kotlin/dev/phosphor/mobil3/RootCaptureSession.kt")
        assertTrue(session.contains("RootEpochNormalizer(ready())"))
        assertTrue(session.contains("binding.convert(pcm, desired)"))
        assertTrue(session.contains("samples(pcm, normalized, binding.owner)"))
        assertTrue(session.contains("protocolFailed = protocolFailed || error !is RootEpochTimeout"))
        assertTrue(session.contains("bytes++ < 65536"))
        assertTrue(session.contains("errors++ < 8192"))
        assertTrue(session.contains("now - started < 18000"))
        val helper = source("root-helper/java/dev/phosphor/mobil3/root/AudioPolicyMain.java")
        val loop = helper.substringAfter("long sequence=0")
        assertTrue(loop.indexOf("streamControl(epochs, false, sequence)") < loop.indexOf("epochs.beforeRead()"))
        assertTrue(loop.indexOf("epochs.beforeRead()") < loop.indexOf("record.read(block"))
        assertTrue(loop.contains("Protocol.pcm(identity,sequence,readEpoch,block,count)"))
        assertTrue(loop.contains("streamFrames>=80000"))
        assertTrue(loop.contains("<5000 || mode==2"))
        assertEquals(1, Regex("record.startRecording\\(\\)").findAll(helper).count())
    }

    @Test fun epochAckCannotReleaseActualLeaseAndUncertainCleanupStaysSticky() {
        val name = RootHelperLease::class.java.name
        val loader = object : ClassLoader(RootHelperLease::class.java.classLoader) {
            @Synchronized override fun loadClass(requested: String, resolve: Boolean): Class<*> {
                if (requested != name) return super.loadClass(requested, resolve)
                val type = findLoadedClass(requested) ?: run {
                    val bytes = checkNotNull(parent.getResourceAsStream(requested.replace('.', '/') + ".class"))
                        .use { it.readBytes() }
                    defineClass(requested, bytes, 0, bytes.size)
                }
                if (resolve) resolveClass(type)
                return type
            }
        }
        run {
            val type = loader.loadClass(name)
            val lease = type.getField("INSTANCE").get(null)
            fun available() = type.getMethod("available").invoke(lease) as Boolean
            fun acquire() { type.getMethod("acquire").invoke(lease) }
            fun release(clean: Boolean) { type.getMethod("release", Boolean::class.javaPrimitiveType).invoke(lease, clean) }
            assertTrue(available())
            acquire()
            started() // A real parser ACK does not release the active helper owner.
            assertFalse(available())
            val error = assertThrows(java.lang.reflect.InvocationTargetException::class.java) { acquire() }
            assertTrue(error.cause is IllegalStateException)
            release(true)
            assertTrue(available())
            acquire()
            release(false)
            assertFalse(available())
            release(true)
            assertFalse(available())
            assertThrows(java.lang.reflect.InvocationTargetException::class.java) { acquire() }
        }
    }
}
