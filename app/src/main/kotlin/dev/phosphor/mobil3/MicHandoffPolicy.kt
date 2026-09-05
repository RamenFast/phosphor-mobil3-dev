package dev.phosphor.mobil3

internal class MicStopStatusToken {
    private var current: String? = null
    fun select(request: String?) { current = request }
    fun accepts(request: String?): Boolean = request != null && request == current
}

/** Both the request's capture idle and the B4 source release must finish before start. */
internal class MicHandoffPolicy(
    private val start: ((String?) -> Unit) -> Unit,
    private val publish: (String?) -> Unit,
) {
    private class Request(val id: String, val afterSequence: Long) {
        var idle = false
        var released = false
        var starting = false
    }

    private var pending: Request? = null
    val isPending: Boolean get() = pending != null

    fun request(id: String, afterSequence: Long) {
        pending = Request(id, afterSequence)
    }

    fun cancel() { pending = null }

    fun sourcesReleased(id: String) {
        val request = pending?.takeIf { it.id == id } ?: return
        request.released = true
        startIfReady(request)
    }

    fun captureStatus(sequence: Long, idle: Boolean, requestId: String?, error: String? = null) {
        val request = pending?.takeIf { it.id == requestId && sequence > it.afterSequence } ?: return
        if (request.starting) return
        if (error != null) {
            pending = null
            publish(error)
            return
        }
        if (idle) request.idle = true
        startIfReady(request)
    }

    private fun startIfReady(request: Request) {
        if (!request.idle || !request.released || request.starting) return
        request.starting = true
        start { error ->
            if (pending === request) {
                pending = null
                publish(error)
            }
        }
    }
}

/** The Android adapter and JVM tests use the same initialization/start/arm contract. */
internal fun startMicRecording(
    initialized: () -> Boolean,
    startRecording: () -> Unit,
    recording: () -> Boolean,
    armScope: () -> Unit,
): String? = try {
    check(initialized()) { "Microphone did not initialize, change the audio route and retry" }
    startRecording()
    check(recording()) { "Microphone did not start recording, change the audio route and retry" }
    armScope()
    null
} catch (error: Exception) {
    "Microphone could not start: ${error.message ?: "audio unavailable"}. Check microphone permission and retry"
}
