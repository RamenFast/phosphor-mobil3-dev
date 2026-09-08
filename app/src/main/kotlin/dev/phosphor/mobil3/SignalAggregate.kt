package dev.phosphor.mobil3

import kotlin.math.abs
import kotlin.math.sqrt

/** Single reader writer. Immutable latest metadata only, never audio history or a second reader. */
internal class SignalAggregate(val owner: Long, private val channels: Int) {
    init { require(channels in 1..2) }
    private var start: Long? = null
    private var measured: Long? = null
    private var readAt: Long? = null
    private var positiveAt: Long? = null
    private var reads = 0L
    private var ingress = 0L
    private var valid = 0L
    private var invalid = 0L
    private val squares = DoubleArray(channels)
    private val peaks = DoubleArray(channels)
    private val rails = LongArray(channels)
    @Volatile var latest = snapshot()
        private set

    private fun begin(count: Int, now: Long) {
        if (start == null || now < start!! || now - start!! >= WINDOW_MS) {
            start = now
            measured = null
            valid = 0
            invalid = 0
            squares.fill(0.0)
            peaks.fill(0.0)
            rails.fill(0)
        }
        reads = signalAdd(reads, 1)
        readAt = now
        if (count > 0) {
            positiveAt = now
            ingress = signalAdd(ingress, (count / channels).toLong())
        }
    }

    fun floats(samples: FloatArray, count: Int, now: Long) {
        val n = count.coerceIn(0, samples.size)
        begin(n, now)
        invalid = signalAdd(invalid, (n % channels).toLong())
        for (i in 0 until n / channels) {
            val offset = i * channels
            if ((0 until channels).any { !samples[offset + it].isFinite() }) {
                invalid = signalAdd(invalid, channels.toLong())
                continue
            }
            valid = signalAdd(valid, 1)
            measured = now
            for (c in 0 until channels) add(c, samples[offset + c].toDouble(), abs(samples[offset + c]) >= 1f)
        }
        latest = snapshot()
    }

    fun pcm16(samples: ShortArray, now: Long) {
        require(channels == 1)
        begin(samples.size, now)
        for (sample in samples) {
            valid = signalAdd(valid, 1)
            measured = now
            add(0, sample / 32768.0, sample == Short.MIN_VALUE || sample == Short.MAX_VALUE)
        }
        latest = snapshot()
    }

    fun progress(now: Long) {
        begin(0, now)
        latest = snapshot()
    }

    private fun add(channel: Int, value: Double, rail: Boolean) {
        squares[channel] += value * value
        peaks[channel] = maxOf(peaks[channel], abs(value))
        if (rail) rails[channel] = signalAdd(rails[channel], 1)
    }

    private fun snapshot() = SignalWindow(owner, start, measured, readAt, positiveAt, reads, ingress, valid, invalid,
        if (valid == 0L) emptyList() else List(channels) { SignalChannel(valid, sqrt(squares[it] / valid), peaks[it], rails[it]) })

    companion object { const val WINDOW_MS = 500L }
}
