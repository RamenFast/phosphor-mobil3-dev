package dev.phosphor.mobil3

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Native source teardown can touch shared rings even when no source exists. */
internal fun cleanupOwnedSource(owned: Boolean, cleanup: () -> Unit) {
    if (owned) cleanup()
}

/** Success means the reader has joined and its final cleanup has returned. Call off main. */
internal object ReaderStop {
    const val TIMEOUT = "Audio reader did not finish, stop the source and retry"
    fun finish(
        reader: Thread?,
        stop: () -> Unit,
        release: () -> Unit,
        cleanup: () -> Unit,
        timeoutMs: Long = 2_000,
    ): String? {
        var error: String? = null
        fun attempt(action: () -> Unit) {
            try {
                action()
            } catch (failure: Exception) {
                error = error ?: (failure.message ?: "Source cleanup failed")
            }
        }
        attempt(stop)
        attempt { reader?.join(timeoutMs.coerceAtLeast(1)) }
        val timedOut = reader?.isAlive == true
        attempt(release)
        attempt(cleanup)
        return error ?: if (timedOut) TIMEOUT else null
    }
}

internal class SourceStopRequest(val id: Long) {
    private val result = CompletableFuture<String?>()
    @Volatile var releasedOwner = false
        private set
    // Both owners set running=false before completing, even when join/cleanup fails.
    @Volatile var stoppedOwner = false
        private set

    @Synchronized
    fun complete(requestId: Long, error: String?, owned: Boolean = false): Boolean {
        if (requestId != id || result.isDone) return false
        releasedOwner = owned && error == null
        stoppedOwner = owned
        result.complete(error)
        return true
    }

    fun await(timeoutMs: Long, isCurrent: () -> Boolean): String? {
        if (!isCurrent()) return "Source request was superseded"
        val error = try {
            result.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            "Source stop timed out, stop the source and retry"
        } catch (failure: Exception) {
            failure.message ?: "Source stop failed, stop the source and retry"
        }
        return if (isCurrent()) error else "Source request was superseded"
    }
}

/** A replacement capture must not start in the old service's cleaned-up instance. */
internal class SourceStopCompletion {
    val result = CompletableFuture<String?>()
    private var cleanupDone = false
    private var destroyed = false

    @Synchronized
    fun cleanupFinished(error: String?) {
        if (error != null) {
            result.complete(error)
        } else {
            cleanupDone = true
            if (destroyed) result.complete(null)
        }
    }

    @Synchronized
    fun ownerDestroyed() {
        destroyed = true
        if (cleanupDone) result.complete(null)
    }
}
