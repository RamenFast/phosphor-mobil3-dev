package dev.phosphor.mobil3

import java.io.File
import java.io.InputStream

internal fun stageAudioFile(destination: File, open: () -> InputStream?): String {
    try {
        val input = open() ?: error("Provider returned no audio stream")
        val copied = input.use { source ->
            destination.parentFile?.mkdirs()
            destination.outputStream().use { source.copyTo(it) }
        }
        check(copied > 0) { "Audio document is empty" }
        return destination.absolutePath
    } catch (error: Exception) {
        destination.delete()
        throw error
    }
}
