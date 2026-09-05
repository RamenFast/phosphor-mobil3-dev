package dev.phosphor.mobil3

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalAudioStagingTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun cachesOnlyAnActuallyCopiedNonemptyFileAndClosesInput() {
        val destination = File(temporary.root, "queue/track.wav")
        var closed = false
        val bytes = byteArrayOf(1, 2, 3, 4)
        val path = stageAudioFile(destination) {
            object : ByteArrayInputStream(bytes) {
                override fun close() { closed = true; super.close() }
            }
        }
        assertEquals(destination.absolutePath, path)
        assertContentEquals(bytes, destination.readBytes())
        assertTrue(closed)
    }

    @Test
    fun nullStreamDoesNotReturnAnOldStagedPath() {
        val destination = temporary.newFile("track.wav").apply { writeText("old bytes") }
        assertFails { stageAudioFile(destination) { null } }
        assertFalse(destination.exists())
    }

    @Test
    fun emptyStreamIsNotAStagedTrack() {
        val destination = File(temporary.root, "empty.wav")
        assertFails { stageAudioFile(destination) { ByteArrayInputStream(byteArrayOf()) } }
        assertFalse(destination.exists())
    }

    @Test
    fun partialCopyFailureClosesStreamAndDeletesOutput() {
        val destination = File(temporary.root, "partial.wav")
        var closed = false
        val input = object : InputStream() {
            var reads = 0
            override fun read(): Int = if (reads++ < 3) 7 else throw IOException("provider disconnected")
            override fun close() { closed = true }
        }
        assertFails { stageAudioFile(destination) { input } }
        assertFalse(destination.exists())
        assertTrue(closed)
    }

    @Test
    fun providerOpenFailureDoesNotCreateAPath() {
        val destination = File(temporary.root, "denied.wav")
        assertFails { stageAudioFile(destination) { throw SecurityException("tree permission revoked") } }
        assertFalse(destination.exists())
    }

    @Test
    fun failedEntryDoesNotPreventALaterIndependentStage() {
        val bad = File(temporary.root, "bad.wav")
        assertFails { stageAudioFile(bad) { null } }
        val good = File(temporary.root, "good.wav")
        assertEquals(good.absolutePath, stageAudioFile(good) { ByteArrayInputStream(byteArrayOf(9)) })
        assertFalse(bad.exists())
        assertContentEquals(byteArrayOf(9), good.readBytes())
    }
}
