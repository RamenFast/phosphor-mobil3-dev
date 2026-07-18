package dev.phosphor.mobil3

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import org.json.JSONObject

// The deck's Android citizenship: ONE MediaSessionService, ONE MediaSession, TWO players
// — the local Rust deck (PhosphorPlayer) and the Tailscale bridge deck (RemotePlayer) —
// swapped with MediaSession.setPlayer(). The loaded deck owns the transport: lock screen,
// notification, earbuds and Bluetooth all drive whichever deck the session holds.
// SimpleBasePlayer does NOT handle audio focus or becoming-noisy — hand-rolled here.
class PlaybackService : MediaSessionService() {

    private lateinit var localPlayer: PhosphorPlayer
    private lateinit var remotePlayer: RemotePlayer
    private var session: MediaSession? = null
    private lateinit var audioManager: AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var resumeOnFocusGain = false
    private lateinit var main: Handler
    private var remotePolling = false

    private val activePlayer: Player get() = session?.player ?: localPlayer

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                activePlayer.playWhenReady = false // route died -> pause, never blast
            }
        }
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnFocusGain = false
                activePlayer.playWhenReady = false
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                resumeOnFocusGain = activePlayer.playWhenReady
                activePlayer.playWhenReady = false
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    activePlayer.playWhenReady = true
                }
            }
        }
    }

    private val focusOnPlay = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady) requestFocus()
        }
    }

    override fun onCreate() {
        super.onCreate()
        main = Handler(mainLooper)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        localPlayer = PhosphorPlayer(mainLooper)
        remotePlayer = RemotePlayer(mainLooper)
        localPlayer.addListener(focusOnPlay)
        remotePlayer.addListener(focusOnPlay)
        val sessionActivity = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, localPlayer)
            .setSessionActivity(sessionActivity)
            .build()
        // No controller connects yet at build time, so the session must be added
        // explicitly — onGetSession never fires, and without an added session the
        // service's notification machinery never engages.
        addSession(session!!)
        registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
    }

    // Swap which deck the one session controls. Main thread only; both players live on
    // mainLooper (the documented setPlayer constraint).
    private fun switchTo(target: Player) {
        val s = session ?: return
        if (s.player === target) return
        if (target === remotePlayer) {
            // Entering remote: silence the other feeders (one scope ring, one owner).
            localPlayer.playWhenReady = false
            startService(
                Intent(this, CaptureService::class.java).setAction(CaptureService.ACTION_STOP)
            )
        }
        s.setPlayer(target)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_REMOTE_CONNECT -> {
                val host = intent.getStringExtra(EXTRA_HOST) ?: return START_NOT_STICKY
                val port = intent.getIntExtra(EXTRA_PORT, 45777)
                val label = intent.getStringExtra(EXTRA_LABEL) ?: host
                startRemote(host, port, label)
                return START_NOT_STICKY
            }
            ACTION_REMOTE_DISCONNECT -> {
                stopRemote()
                return START_NOT_STICKY
            }
        }
        intent?.getStringExtra(EXTRA_OPEN)?.let { path ->
            // Loading a local file takes the deck back (stops remote first, the law).
            if (session?.player === remotePlayer) stopRemote()
            Thread {
                if (PhosphorNative.deckOpen(path)) {
                    requestFocus()
                    main.post {
                        switchTo(localPlayer)
                        localPlayer.onTrackOpened()
                    }
                }
            }.start()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun startRemote(host: String, port: Int, label: String) {
        switchTo(remotePlayer)
        remotePlayer.onConnecting(label)
        requestFocus()
        // v2 connect is non-blocking: rust owns link mechanics (timeout, watchdog,
        // reconnect backoff); this service owns policy via the status pump below.
        if (!PhosphorNative.remoteConnect(host, port, true, false)) {
            remotePlayer.onConnectFailed("couldn't start the bridge link")
            return
        }
        startRemotePoll()
    }

    private fun stopRemote() {
        remotePolling = false
        PhosphorNative.remoteDisconnect()
        remotePlayer.reset()
        switchTo(localPlayer)
    }

    // 1 Hz status+metadata pump. Rust exposes generation counters so quiet ticks cost
    // one JNI read; state transitions drive the player face; art rides art_id changes.
    private var lastMetaGen = -1
    private var lastArtGen = -1
    private var lastArtId = ""
    private var failingSinceMs = 0L
    private fun startRemotePoll() {
        if (remotePolling) return
        remotePolling = true
        lastMetaGen = -1; lastArtGen = -1; lastArtId = ""
        failingSinceMs = 0L
        main.post(object : Runnable {
            override fun run() {
                if (!remotePolling || session?.player !== remotePlayer) {
                    remotePolling = false
                    return
                }
                val status = runCatching { JSONObject(PhosphorNative.remoteStatus()) }.getOrNull()
                if (status != null) {
                    when (status.optString("state")) {
                        "streaming" -> {
                            failingSinceMs = 0L
                            remotePlayer.onConnected()
                        }
                        "stalled", "reconnecting", "connecting" -> {
                            // Give-up policy: 60 s of not-streaming → surface failure.
                            val now = System.currentTimeMillis()
                            if (failingSinceMs == 0L) failingSinceMs = now
                            if (now - failingSinceMs > 60_000) {
                                remotePolling = false
                                PhosphorNative.remoteDisconnect()
                                remotePlayer.onConnectFailed(
                                    status.optJSONObject("last_error")?.optString("error")
                                        ?: "bridge unreachable"
                                )
                                return
                            }
                            remotePlayer.onConnectionLost()
                        }
                        "failed" -> {
                            remotePolling = false
                            remotePlayer.onConnectFailed(
                                status.optJSONObject("last_error")?.let {
                                    it.optString("error") + " — " + it.optString("fix")
                                } ?: "bridge failed"
                            )
                            return
                        }
                    }
                    val mg = status.optInt("meta_gen")
                    if (mg != lastMetaGen) {
                        lastMetaGen = mg
                        runCatching { JSONObject(PhosphorNative.remoteMetadata()) }
                            .getOrNull()?.let { remotePlayer.onMeta(it) }
                    }
                    // Art: ask when the id changes; consume when the bytes land.
                    val artId = status.optString("art_id")
                    if (artId.isNotBlank() && artId != lastArtId) {
                        lastArtId = artId
                        PhosphorNative.remoteRequestArt(artId)
                    }
                    val ag = status.optInt("art_gen")
                    if (ag != lastArtGen) {
                        lastArtGen = ag
                        remotePlayer.onArt(PhosphorNative.remoteArt())
                    }
                }
                main.postDelayed(this, 1000)
            }
        })
    }

    private fun requestFocus() {
        val req = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener(focusListener)
            .build()
            .also { focusRequest = it }
        if (audioManager.requestAudioFocus(req) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            activePlayer.playWhenReady = false
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        remotePolling = false
        unregisterReceiver(noisyReceiver)
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        session?.release()
        localPlayer.release()
        remotePlayer.release()
        session = null
        PhosphorNative.remoteDisconnect()
        PhosphorNative.deckClose()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_OPEN = "open"
        const val ACTION_REMOTE_CONNECT = "dev.phosphor.mobil3.REMOTE_CONNECT"
        const val ACTION_REMOTE_DISCONNECT = "dev.phosphor.mobil3.REMOTE_DISCONNECT"
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
        const val EXTRA_LABEL = "label"
    }
}
