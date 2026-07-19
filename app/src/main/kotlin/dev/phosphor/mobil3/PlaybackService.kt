package dev.phosphor.mobil3

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
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
    private lateinit var connectivityManager: ConnectivityManager
    private var remotePolling = false
    private data class RemoteEndpoint(val host: String, val port: Int, val label: String)
    private var remoteEndpoint: RemoteEndpoint? = null
    private var remoteNetworkCallback: ConnectivityManager.NetworkCallback? = null
    private var boundNetwork: Network? = null
    private var remoteGainApplied = false

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
        connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        localPlayer = PhosphorPlayer(mainLooper)
        remotePlayer = RemotePlayer(mainLooper)
        localPlayer.onSwitchTrack = ::stageAndOpen
        remotePlayer.onStopRequested = ::stopRemote
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
            ACTION_REMOTE_POLICY_CHANGED -> {
                remoteEndpoint?.let { configureRemoteNetwork(it) }
                return START_NOT_STICKY
            }
        }
        intent?.getStringExtra(EXTRA_OPEN)?.let { path ->
            // Clear the local face before taking the session back; otherwise its previous
            // track can flash between the remote reset and this new file opening.
            localPlayer.setQueue(
                listOf(PhosphorPlayer.QueueEntry(path, path.substringAfterLast('/'))), 0
            )
            queuePaths = mutableListOf(path)
            queueUris = mutableListOf(null)
            if (session?.player === remotePlayer) stopRemote()
            stageAndOpen(0)
        }
        if (intent?.action == ACTION_OPEN_QUEUE) {
            val uris = intent.getStringArrayListExtra(EXTRA_QUEUE_URIS) ?: arrayListOf()
            val titles = intent.getStringArrayListExtra(EXTRA_QUEUE_TITLES) ?: arrayListOf()
            val start = intent.getIntExtra(EXTRA_QUEUE_START, 0)
            queueUris = uris.map { it as String? }.toMutableList()
            queuePaths = MutableList(uris.size) { null }
            localPlayer.setQueue(
                uris.mapIndexed { i, _ ->
                    PhosphorPlayer.QueueEntry("", titles.getOrElse(i) { "track ${i + 1}" })
                },
                start,
            )
            if (session?.player === remotePlayer) stopRemote()
            stageAndOpen(start)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    // ── The queue engine: SAF URIs staged into filesDir on demand, next prefetched. ──
    private var queueUris: MutableList<String?> = mutableListOf()
    private var queuePaths: MutableList<String?> = mutableListOf()
    private var stageGen = 0

    private fun stagedPath(i: Int): String? {
        queuePaths.getOrNull(i)?.let { return it }
        val uriStr = queueUris.getOrNull(i) ?: return null
        val uri = android.net.Uri.parse(uriStr ?: return null)
        val name = "q$i-" + (uri.lastPathSegment ?: "track").substringAfterLast('/')
            .substringAfterLast(':').replace('/', '_')
        val dst = java.io.File(filesDir, "queue/$name")
        dst.parentFile?.mkdirs()
        return runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                dst.outputStream().use { input.copyTo(it) }
            }
            dst.absolutePath.also { queuePaths[i] = it }
        }.getOrNull()
    }

    private fun stageAndOpen(i: Int) {
        val gen = ++stageGen
        Thread {
            val path = stagedPath(i)
            if (path != null && gen == stageGen && PhosphorNative.deckOpen(path)) {
                requestFocus()
                main.post {
                    if (gen != stageGen) return@post
                    switchTo(localPlayer)
                    localPlayer.onTrackOpened()
                    startEndWatcher()
                }
                // Prefetch the next entry so the gapless hand-off has a local file ready.
                if (i + 1 < queueUris.size) stagedPath(i + 1)
            }
        }.start()
    }

    // End-of-track watcher: drives auto-advance through the queue.
    private var watching = false
    private fun startEndWatcher() {
        if (watching) return
        watching = true
        main.post(object : Runnable {
            override fun run() {
                if (session?.player !== localPlayer || localPlayer.queueSize() == 0) {
                    watching = false
                    return
                }
                val dur = localPlayer.currentDurationMs()
                val pos = PhosphorNative.deckPositionMs()
                if (localPlayer.playWhenReady && dur > 0 && pos >= dur - 350) {
                    if (!localPlayer.advanceIfPossible()) {
                        localPlayer.playWhenReady = false // end of queue: rest
                    }
                }
                main.postDelayed(this, 400)
            }
        })
    }

    private fun startRemote(host: String, port: Int, label: String) {
        switchTo(remotePlayer)
        remotePlayer.onConnecting(label)
        requestFocus()
        val endpoint = RemoteEndpoint(host, port, label)
        remoteEndpoint = endpoint
        configureRemoteNetwork(endpoint)
    }

    private fun stopRemote() {
        remotePolling = false
        remoteEndpoint = null
        PhosphorNative.remoteDisconnect()
        clearRemoteNetwork()
        remotePlayer.reset()
        switchTo(localPlayer)
    }

    private fun prefs() = getSharedPreferences("phosphor.prefs", MODE_PRIVATE)

    /**
     * Rust owns the bridge TcpStream, so Android cannot bind that socket directly.
     * The honest fallback is a process default bind established before remoteConnect.
     * It is cleared on every loss/stop/failure so unrelated future sockets never inherit
     * a dead route. Auto removes the bind and returns routing to Android.
     */
    private fun configureRemoteNetwork(endpoint: RemoteEndpoint) {
        PhosphorNative.remoteDisconnect()
        clearRemoteNetwork()
        remotePlayer.onConnecting(endpoint.label)
        val mode = prefs().getInt("remote_network_mode", 0).coerceIn(0, 2)
        if (mode == 0) {
            connectRemoteNow(endpoint)
            return
        }
        val canRequest = checkSelfPermission(android.Manifest.permission.ACCESS_NETWORK_STATE) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(android.Manifest.permission.CHANGE_NETWORK_STATE) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!canRequest) {
            remotePlayer.onConnectFailed(
                "network route permission missing — add ACCESS_NETWORK_STATE and CHANGE_NETWORK_STATE"
            )
            return
        }
        val transport = if (mode == 1) {
            NetworkCapabilities.TRANSPORT_WIFI
        } else {
            NetworkCapabilities.TRANSPORT_CELLULAR
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (remoteNetworkCallback !== this || remoteEndpoint != endpoint) return
                if (boundNetwork == network) return
                PhosphorNative.remoteDisconnect()
                if (boundNetwork != null) connectivityManager.bindProcessToNetwork(null)
                val didBind = runCatching {
                    connectivityManager.bindProcessToNetwork(network)
                }.getOrDefault(false)
                if (!didBind) {
                    boundNetwork = null
                    remotePlayer.onConnectionLost()
                    return
                }
                boundNetwork = network
                remotePlayer.onConnecting(endpoint.label)
                connectRemoteNow(endpoint)
            }

            override fun onLost(network: Network) {
                if (remoteNetworkCallback !== this || boundNetwork != network) return
                PhosphorNative.remoteDisconnect()
                if (boundNetwork != null) connectivityManager.bindProcessToNetwork(null)
                boundNetwork = null
                remoteGainApplied = false
                remotePlayer.onConnectionLost()
                // This request stays registered. Its next onAvailable owns reconnect.
            }

            override fun onUnavailable() {
                if (remoteNetworkCallback !== this) return
                PhosphorNative.remoteDisconnect()
                if (boundNetwork != null) connectivityManager.bindProcessToNetwork(null)
                boundNetwork = null
                remotePlayer.onConnectFailed("requested network unavailable — choose auto or another route")
            }
        }
        remoteNetworkCallback = callback
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(transport)
            .build()
        runCatching { connectivityManager.requestNetwork(request, callback, main) }
            .onFailure {
                remoteNetworkCallback = null
                if (boundNetwork != null) connectivityManager.bindProcessToNetwork(null)
                remotePlayer.onConnectFailed("network request failed — ${it.message ?: "check route permission"}")
            }
    }

    private fun connectRemoteNow(endpoint: RemoteEndpoint) {
        // Latency is policy, not session state: apply before every fresh link.
        PhosphorNative.remoteSetLatencyMode(
            prefs().getInt("remote_latency_mode", 2).coerceIn(0, 2)
        )
        remoteGainApplied = false
        // v2 connect is non-blocking: rust owns timeout/watchdog/backoff; the
        // service owns route selection and the status pump.
        if (!PhosphorNative.remoteConnect(endpoint.host, endpoint.port, true, false)) {
            remoteEndpoint = null
            clearRemoteNetwork()
            remotePlayer.onConnectFailed("couldn't start the bridge link")
            return
        }
        startRemotePoll()
    }

    private fun clearRemoteNetwork() {
        remoteNetworkCallback?.let { callback ->
            runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        }
        remoteNetworkCallback = null
        if (boundNetwork != null) connectivityManager.bindProcessToNetwork(null)
        boundNetwork = null
    }

    // 1 Hz status+metadata pump. Rust exposes generation counters so quiet ticks cost
    // one JNI read; state transitions drive the player face; art rides art_id changes.
    private var lastMetaGen = -1
    private var lastArtGen = -1
    private var failingSinceMs = 0L
    private fun startRemotePoll() {
        if (remotePolling) return
        remotePolling = true
        lastMetaGen = -1; lastArtGen = -1
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
                            if (!remoteGainApplied) {
                                val p = prefs()
                                PhosphorNative.remoteScopeCtl(
                                    "gain",
                                    if (p.getBoolean("auto_gain", false)) "auto"
                                    else String.format(
                                        java.util.Locale.US, "%.2f", p.getFloat("gain", 1f)
                                    ),
                                )
                                remoteGainApplied = true
                            }
                        }
                        "stalled", "reconnecting", "connecting" -> {
                            // Give-up policy: 60 s of not-streaming → surface failure.
                            val now = System.currentTimeMillis()
                            if (failingSinceMs == 0L) failingSinceMs = now
                            if (now - failingSinceMs > 60_000) {
                                remotePolling = false
                                PhosphorNative.remoteDisconnect()
                                remoteEndpoint = null
                                clearRemoteNetwork()
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
                            remoteEndpoint = null
                            clearRemoteNetwork()
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
                            .getOrNull()?.let { metadata ->
                                // The desired id lives on M. RemotePlayer clears the old
                                // bytes before returning the new content-addressed request.
                                remotePlayer.onMeta(metadata)?.let {
                                    PhosphorNative.remoteRequestArt(it)
                                }
                            }
                    }
                    val ag = status.optInt("art_gen")
                    if (ag != lastArtGen) {
                        lastArtGen = ag
                        // status.art_id identifies the R reply, not the desired M art.
                        // RemotePlayer rejects it if a newer track already owns the session.
                        remotePlayer.onArt(status.optString("art_id"), PhosphorNative.remoteArt())
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
        remoteEndpoint = null
        clearRemoteNetwork()
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
        const val ACTION_OPEN_QUEUE = "dev.phosphor.mobil3.OPEN_QUEUE"
        const val EXTRA_QUEUE_URIS = "queue_uris"
        const val EXTRA_QUEUE_TITLES = "queue_titles"
        const val EXTRA_QUEUE_START = "queue_start"
        const val ACTION_REMOTE_CONNECT = "dev.phosphor.mobil3.REMOTE_CONNECT"
        const val ACTION_REMOTE_DISCONNECT = "dev.phosphor.mobil3.REMOTE_DISCONNECT"
        const val ACTION_REMOTE_POLICY_CHANGED = "dev.phosphor.mobil3.REMOTE_POLICY_CHANGED"
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
        const val EXTRA_LABEL = "label"
    }
}
