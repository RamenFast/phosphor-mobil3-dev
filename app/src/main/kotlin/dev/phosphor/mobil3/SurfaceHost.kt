package dev.phosphor.mobil3

import android.content.Context
import android.graphics.PixelFormat
import android.view.SurfaceHolder
import android.view.SurfaceView

/** All app, PiP, and overlay callbacks pass through this single native-surface authority. */
internal class SurfaceHost(
    context: Context,
    private val transparent: Boolean = false,
    private val status: (String) -> Unit = {},
    private val failed: () -> Unit = {},
    private val presented: () -> Unit = {},
) : SurfaceHolder.Callback {
    val view = SurfaceView(context)
    private var lease: SurfaceOwner.Lease? = null
    private var ready = false
    private var closed = false
    private var solidFallback = false
    private val callbacks = SurfaceCallbacks(status, failed)
    val failure: String? get() = callbacks.failure
    private var observedPlayer: androidx.media3.common.Player? = null
    private val itemListener = object : androidx.media3.common.Player.Listener {
        override fun onEvents(player: androidx.media3.common.Player, events: androidx.media3.common.Player.Events) {
            metadataChanged(player)
        }
    }
    init {
        view.holder.setFormat(if (transparent) PixelFormat.TRANSLUCENT else PixelFormat.OPAQUE)
        // HUD controls occupy separate rows, so the alpha surface cannot cover their targets.
        view.setZOrderOnTop(transparent)
        view.holder.addCallback(this)
    }
    override fun surfaceCreated(holder: SurfaceHolder) {
        callbacks.run {
            ready = true
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                holder.surface.setFrameRate(120f, android.view.Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
            }
        }
    }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        attach(holder, width, height)
    }
    private fun attach(holder: SurfaceHolder, width: Int, height: Int) {
        if (closed || failure != null || preferred() !== this || !ready || width <= 0 || height <= 0) return
        callbacks.run {
            if (!owner.accepts(lease)) lease = owner.claim(this)
            owner.change(lease) {
                val result = PhosphorNative.attachSurface(holder.surface, width, height,
                    view.resources.displayMetrics.density, transparent && !solidFallback)
                android.util.Log.i("PhosphorSurface", "attach owner=${System.identityHashCode(this)} generation=${lease?.generation} size=${width}x${height} result=$result")
                when {
                    result < 0 -> {
                        retire()
                        callbacks.fail("Surface unavailable. Return to the app and retry")
                    }
                    transparent && result == 0 && !solidFallback -> {
                        solidFallback = true
                        status("Requested TRANSPARENT · active SOLID: premultiplied Vulkan alpha unavailable")
                        holder.setFormat(PixelFormat.OPAQUE)
                    }
                    else -> {
                        if (!solidFallback) status(if (result == 1) "Requested TRANSPARENT · active TRANSPARENT" else "Active SOLID")
                        PhosphorNative.setRenderPaused(false)
                        presented()
                    }
                }
            }
        }
    }
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        android.util.Log.i("PhosphorSurface", "destroy generation=${lease?.generation} accepted=${owner.accepts(lease)}")
        ready = false
        retire()
    }
    private fun retire() {
        val retiring = lease
        lease = null
        callbacks.run { owner.release(retiring) }
    }
    private fun present() {
        if (ready && !closed && !owner.accepts(lease)) attach(view.holder, view.holder.surfaceFrame.width(), view.holder.surfaceFrame.height())
    }
    fun metadataChanged(player: androidx.media3.common.Player) {
        if (closed) return
        if (observedPlayer !== player) {
            observedPlayer?.removeListener(itemListener)
            observedPlayer = player
            player.addListener(itemListener)
        }
        owner.change(lease) {
            val metadata = player.mediaMetadata
            if (player.currentMediaItem == null && metadata.title == null && metadata.extras?.getString("source") == null) return@change
            val identity = tracks.identity(player.currentMediaItem?.mediaId, player.currentMediaItemIndex,
                metadata.extras?.getString("source"), metadata.extras?.getString("host") ?: metadata.extras?.getString("package"),
                metadata.title?.toString(), metadata.artist?.toString(), metadata.albumTitle?.toString())
            if (tracks.changed(identity)) PhosphorNative.cycleAdvance()
        }
    }
    fun close() {
        if (closed) return
        closed = true
        callbacks.run { observedPlayer?.removeListener(itemListener) }
        observedPlayer = null
        retire()
        callbacks.run { view.holder.removeCallback(this) }
        if (activity === this) activity = null
        if (hud === this) hud = null
        preferred()?.present()
    }
    companion object {
        private val tracks = PresentationTrackGate()
        private val owner = SurfaceOwner {
            PhosphorNative.setRenderPaused(true)
            check(PhosphorNative.surfaceDestroyed()) { "Native retirement acknowledgement lost. Restart Phosphor before retrying" }
        }
        private var activity: SurfaceHost? = null
        private var activityVisible = false
        private var hud: SurfaceHost? = null
        private var hudVisible = true
        private fun preferred(): SurfaceHost? = if (hud != null) hud?.takeIf { hudVisible } else activity?.takeIf { activityVisible }
        fun activity(host: SurfaceHost) { activity?.takeIf { it !== host }?.close(); activity = host; preferred()?.present() }
        fun activityVisible(visible: Boolean) {
            activityVisible = visible
            if (!visible) activity?.retire()
            preferred()?.present()
        }
        fun hud(host: SurfaceHost) { hud = host; hudVisible = true; host.present() }
        fun hudVisible(host: SurfaceHost, visible: Boolean) {
            if (hud !== host) return
            hudVisible = visible
            if (!visible) host.retire()
            else host.present()
        }
    }
}
