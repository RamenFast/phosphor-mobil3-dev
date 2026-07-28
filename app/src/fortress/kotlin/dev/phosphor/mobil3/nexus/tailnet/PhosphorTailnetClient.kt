package dev.phosphor.mobil3.nexus.tailnet

import kotlin.math.min

/** Monotonic writer-generation fence used by integration code before handing frames to the runtime. */
class PhosphorTailnetGenerationFence {
    private var generation: Long = -1L
    fun advanceIfFresh(frameGeneration: Long): Boolean {
        require(frameGeneration >= 0L) { "frame generation cannot be negative" }
        if (frameGeneration <= generation) return false
        generation = frameGeneration
        return true
    }
}

/** Bounded reconnect policy helper. The actual socket protocol lives only in [PhosphorTailnetRuntime]. */
data class PhosphorTailnetBackoff(
    val baseMillis: Long = 1_000L,
    val maxMillis: Long = 60_000L,
    val jitterMillis: Long = 250L,
) {
    init {
        require(baseMillis in 1..maxMillis) { "backoff base must be bounded" }
        require(maxMillis in baseMillis..300_000L) { "backoff max must be bounded" }
        require(jitterMillis in 0..maxMillis) { "jitter must be bounded" }
    }

    fun delayMillis(failureCount: Int, jitterSampleMillis: Long): Long {
        require(failureCount >= 0) { "failure count cannot be negative" }
        require(jitterSampleMillis in -jitterMillis..jitterMillis) { "jitter sample outside configured bound" }
        val shifted = if (failureCount >= 30) Long.MAX_VALUE else baseMillis shl failureCount
        return (min(shifted, maxMillis) + jitterSampleMillis).coerceIn(1L, maxMillis + jitterMillis)
    }
}
