package dev.phosphor.mobil3

/** A format/route change retires in-flight diagnostic reads, not the recorder itself. */
internal class SignalRecorderWindow(private val owner: Long) {
    private data class State(val descriptor: SignalDescriptor, val meter: SignalAggregate?)
    @Volatile private var state = State(SignalDescriptor(), null)
    @Synchronized fun observe(next: SignalDescriptor) {
        val old = state
        val sameInput = old.descriptor.format == next.format && old.descriptor.deviceFormat == next.deviceFormat &&
            old.descriptor.route == next.route && old.descriptor.requestedRoute == next.requestedRoute
        val meter = if (sameInput) old.meter else next.format?.channels?.takeIf { it in 1..2 }?.let { SignalAggregate(owner, it) }
        state = State(next, meter)
    }
    fun token(): SignalAggregate? = state.meter
    @Synchronized fun floats(token: SignalAggregate?, samples: FloatArray, count: Int, now: Long) {
        if (token != null && token === state.meter) token.floats(samples, count, now)
    }
    fun snapshot(): Pair<SignalDescriptor, SignalWindow?> {
        val current = state
        return current.descriptor to current.meter?.latest
    }
}
