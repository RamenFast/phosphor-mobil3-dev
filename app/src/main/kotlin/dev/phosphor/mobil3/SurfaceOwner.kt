package dev.phosphor.mobil3

/** Main-thread leases. The native surface is detached before its successor is granted. */
internal class SurfaceOwner(private val detach: () -> Unit) {
    data class Lease(val owner: Any, val generation: Long)
    private var generation = 0L
    var current: Lease? = null
        private set
    private var unavailable = false
    private fun retire() {
        unavailable = true
        detach()
        current = null
        unavailable = false
    }
    fun claim(owner: Any): Lease {
        check(!unavailable) { "Native retirement was not confirmed. Restart Phosphor before retrying" }
        current?.let { retire() }
        return Lease(owner, ++generation).also { current = it }
    }
    fun accepts(lease: Lease?): Boolean = !unavailable && lease != null && current == lease
    fun change(lease: Lease?, apply: () -> Unit): Boolean {
        if (!accepts(lease)) return false
        apply()
        return true
    }
    fun release(lease: Lease?): Boolean {
        if (!accepts(lease)) return false
        retire()
        return true
    }
}

/** Contains framework callback failures without skipping later retirement work. */
internal class SurfaceCallbacks(private val status: (String) -> Unit, private val failed: () -> Unit) {
    var failure: String? = null
        private set
    fun run(action: () -> Unit): Boolean = runCatching(action).onFailure {
        fail("Surface unavailable. Restart Phosphor before retrying")
    }.isSuccess
    fun fail(message: String) {
        if (failure != null) return
        failure = message
        runCatching { status(message) }
        runCatching(failed)
    }
}

/** Metadata identity stays in memory across app/PiP/HUD transfer, never in archives. */
internal class PresentationTrackGate {
    private var last: List<String?>? = null
    fun identity(item: String?, index: Int, source: String?, endpoint: String?, title: String?, artist: String?, album: String?): List<String?> {
        val stable = item?.takeIf { it.isNotBlank() && it != "remote:now" && it != "capture:now" }
        return if (stable != null) listOf(source ?: "local", endpoint, stable, index.toString())
        else listOf(source, endpoint, title, artist, album)
    }
    fun changed(identity: List<String?>): Boolean {
        if (identity == last) return false
        last = identity.toList()
        return true
    }
}
