package dev.phosphor.mobil3

/** Fixed-size control state shared by the production parser and host tests. */
internal class RootEpoch {
    data class Binding(val controlSequence: Long, val epoch: Long)
    data class Pending(val binding: Binding, val requestedAt: Long)
    var acknowledged: Binding? = null
        private set
    var pending: Pending? = null
        private set
    var desired: Long? = null
        private set
    private var nextControl = 1L
    private var lastRequest: Long? = null

    fun desire(epoch: Long) {
        require(desired?.let { java.lang.Long.compareUnsigned(epoch, it) >= 0 } != false) { "Root epoch regression" }
        desired = epoch
    }

    fun request(now: Long): Binding? {
        val epoch = desired ?: return null
        if (pending != null || acknowledged?.epoch == epoch || lastRequest?.let { now - it < 125 } == true) return null
        require(nextControl < Long.MAX_VALUE) { "Root control sequence exhausted" }
        val binding = Binding(nextControl++, epoch)
        pending = Pending(binding, now)
        lastRequest = now
        return binding
    }

    fun ack(binding: Binding) {
        require(pending?.binding == binding) { "Root epoch ACK sequence mismatch" }
        acknowledged = binding
        pending = null
    }

    fun checkDeadline(now: Long) {
        if (expired(now)) throw RootEpochTimeout()
    }
    fun expired(now: Long) = pending?.let { now - it.requestedAt >= 1000 } == true

    fun cancel() { pending = null }
}
internal class RootEpochTimeout : IllegalStateException("Root epoch ACK timed out")

/** No stale batch enters interpolation state. Native still checks owner and epoch at publication. */
internal class RootEpochNormalizer(val owner: Long) {
    init { require(owner != 0L) { "Root activation owner is zero" } }
    private var epoch: Long? = null
    private var normalizer = RootPcmNormalizer()
    fun convert(batch: RootAudioProtocol.PcmBatch, desired: Long): FloatArray? {
        if (batch.readEpoch != desired) return null
        if (epoch != batch.readEpoch) {
            normalizer = RootPcmNormalizer()
            epoch = batch.readEpoch
        }
        return normalizer.convert(batch.samples)
    }
}
