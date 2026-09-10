package dev.phosphor.mobil3

import android.content.Context
import android.os.PowerManager
import android.util.Log

/** One lock per actual source owner. The backend seam tests calls, not Android sleep behavior. */
internal class SourceWakeLock(
    private val create: () -> Backend,
    private val onFailure: (RuntimeException) -> Unit,
) {
    internal interface Backend {
        val isHeld: Boolean
        fun acquire()
        fun release()
    }

    private var backend: Backend? = null
    private var destroyed = false
    @Volatile var live = false
        private set

    fun localChanged(published: Boolean, playing: Boolean, ready: Boolean, failed: Boolean) =
        update(SourceWakePolicy.local(published, playing, ready, failed))

    fun remoteChanged(owned: Boolean, state: RemoteLinkState?) =
        update(SourceWakePolicy.remote(owned, state))

    fun captureChanged(recording: Boolean, projection: Boolean) =
        update(SourceWakePolicy.capture(recording, projection))

    fun rootChanged(recording: Boolean, helper: Boolean) = update(SourceWakePolicy.root(recording, helper))

    fun microphoneChanged(recording: Boolean, serviceDestroyed: Boolean) =
        update(SourceWakePolicy.microphone(recording, serviceDestroyed))

    fun stop() = update(false)

    @Synchronized fun destroy() {
        destroyed = true
        update(false)
    }

    @Synchronized private fun update(sourceLive: Boolean) {
        live = sourceLive && !destroyed
        try {
            if (live) {
                val lock = backend ?: create().also { backend = it }
                if (!lock.isHeld) lock.acquire()
            } else {
                backend?.let { if (it.isHeld) it.release() }
            }
        } catch (error: RuntimeException) {
            // Keep source truth separate from lock success. The next existing owner update retries.
            onFailure(error)
        }
    }

    companion object {
        fun forOwner(context: Context, owner: String): SourceWakeLock = SourceWakeLock(
            create = { screenBackend(context, owner) },
            onFailure = { Log.e("PhosphorWake", "$owner screen wake failed. Reopen the source and retry", it) },
        )

        @Suppress("DEPRECATION") // B11 requires a screen lock for each live source, including PiP.
        private fun screenBackend(context: Context, owner: String): Backend {
            val manager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val lock = manager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "Phosphor:$owner")
            lock.setReferenceCounted(false)
            return object : Backend {
                override val isHeld: Boolean get() = lock.isHeld
                override fun acquire() = lock.acquire()
                override fun release() = lock.release()
            }
        }
    }
}
