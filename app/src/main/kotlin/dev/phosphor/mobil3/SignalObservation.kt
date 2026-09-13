package dev.phosphor.mobil3

/** All Kotlin timestamps in this model use elapsedRealtime milliseconds. */
internal enum class SignalKind(val label: String) {
    NONE("No source"), MIC("Microphone"), CAPTURE("Playback capture"), ROOT("Everything playing › root"),
    LOCAL("Local file"), RELAY("PC relay"), UNKNOWN("Source changing"),
}

internal enum class SignalLife {
    STARTING, RUNNING, STOPPING, CLEANUP_UNCONFIRMED, ENDED, FAILED, PERMISSION, DISCONNECTED, RECONNECTING, STALLED,
}

/** An owner-local diagnostic receipt, not acknowledgement that the reader has stopped. */
internal data class SignalRetirement(val requested: SignalLife, val reason: String) {
    fun observation(cleanupDone: Boolean, cleanupError: String?): Pair<SignalLife, String> = when {
        cleanupDone && cleanupError != null -> SignalLife.CLEANUP_UNCONFIRMED to cleanupError
        requested == SignalLife.FAILED || requested == SignalLife.PERMISSION -> requested to reason
        !cleanupDone -> SignalLife.STOPPING to "Capture is stopping"
        else -> SignalLife.ENDED to reason
    }
}

internal data class SignalFormat(val rate: Int, val channels: Int, val encoding: String) {
    fun label() = "$rate Hz · ${if (channels == 1) "mono" else if (channels == 2) "stereo" else "$channels channels"} · $encoding"
}

internal data class SignalDescriptor(
    val format: SignalFormat? = null,
    val route: String? = null,
    val observedAt: Long? = null,
    val unavailable: String = "Recorder has not reported its format or route",
    val deviceFormat: SignalFormat? = null,
    val requestedRoute: String? = null,
)

internal data class SignalChannel(val samples: Long, val rms: Double, val peak: Double, val fullScale: Long)

internal data class SignalStereo(val nonSilentPairs: Long, val identicalPairs: Long, val differenceRms: Double)

internal data class SignalWindow(
    val owner: Long,
    val startedAt: Long?,
    val measuredAt: Long?,
    val lastReadAt: Long?,
    val lastPositiveAt: Long?,
    val reads: Long,
    val ingressFrames: Long,
    val validFrames: Long,
    val invalidSamples: Long,
    val channels: List<SignalChannel>,
    val stereo: SignalStereo? = null,
)

internal data class SignalInput(
    val kind: SignalKind,
    val owner: Long,
    val session: Long = owner,
    val nativeOwner: Long? = null,
    val life: SignalLife = SignalLife.STARTING,
    val reason: String = "",
    val descriptor: SignalDescriptor = SignalDescriptor(),
    val window: SignalWindow? = null,
    val progressAt: Long? = null,
    val contributing: Boolean = false,
    val normalized: String = "48,000 Hz · stereo float scope pipeline (configured, not hardware negotiation)",
    val receiptAt: Long? = null,
    val receiptCount: Long? = null,
    val receiptUnit: String = "input frames",
    val receiptAgeIsUpperBound: Boolean = false,
    val readUnit: String = "completed reads",
)

internal data class SignalPlayback(
    val kind: SignalKind, val revision: Long, val life: SignalLife, val reason: String,
    val intent: Boolean?, val transport: Boolean?, val transportAt: Long?,
    val relaySession: Long? = null, val link: RemoteLinkReading? = null, val linkAt: Long? = null,
    val localOpen: Long? = null,
)

internal data class SignalDisplay(val paused: Boolean, val black: Boolean, val held: Boolean, val pending: Boolean)

internal data class SignalCheckView(
    val status: String = "Observation unavailable",
    val rows: List<Pair<String, String>> = listOf("Input" to "Expand to read current owners"),
)

internal fun signalAge(now: Long, at: Long?): Long? = at?.takeIf { it >= 0 && now >= it }?.let { now - it }
internal fun signalAdd(value: Long, extra: Long): Long = if (extra > Long.MAX_VALUE - value) Long.MAX_VALUE else value + extra
