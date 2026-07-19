package dev.phosphor.mobil3

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata as PlatformMediaMetadata
import android.media.session.MediaController as PlatformMediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState as PlatformPlaybackState
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.view.KeyEvent
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URL

// The deck's Android citizenship: ONE MediaSessionService, ONE MediaSession, TWO players
// — the local Rust deck (PhosphorPlayer) and the Tailscale bridge deck (RemotePlayer) —
// swapped with MediaSession.setPlayer(). The loaded deck owns the transport: lock screen,
// notification, earbuds and Bluetooth all drive whichever deck the session holds.
// SimpleBasePlayer does NOT handle audio focus or becoming-noisy — hand-rolled here.
class PlaybackService : MediaSessionService() {

    private lateinit var localPlayer: PhosphorPlayer
    private lateinit var remotePlayer: RemotePlayer
    private lateinit var capturePlayer: CaptureMirrorPlayer
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
    private lateinit var platformSessionManager: MediaSessionManager
    private lateinit var notificationListenerComponent: ComponentName
    private var captureActive = false
    private var captureSessionsListenerRegistered = false
    private var externalCaptureController: PlatformMediaController? = null
    private var captureTrackKey: String? = null
    private var captureArtwork: ByteArray? = null
    private var captureArtGeneration = 0

    private val activePlayer: Player get() = session?.player ?: localPlayer

    private val activeSessionsChanged =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            if (captureActive) chooseCaptureController(controllers.orEmpty())
        }

    private val externalControllerCallback = object : PlatformMediaController.Callback() {
        override fun onMetadataChanged(metadata: PlatformMediaMetadata?) {
            if (captureActive) publishCaptureMetadata(metadata)
        }

        override fun onPlaybackStateChanged(state: PlatformPlaybackState?) {
            if (captureActive) publishCapturePlayback(state)
        }

        override fun onSessionDestroyed() {
            if (captureActive) refreshCaptureController()
        }
    }

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
        // Fresh service = nothing staged is open yet: sweep every transient audio copy
        // (settings/prefs untouched). Covers force-stop exits that skip onDestroy.
        Thread { pruneStaged() }.start()
        main = Handler(mainLooper)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        platformSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        notificationListenerComponent =
            ComponentName(this, CaptureNotificationListenerService::class.java)
        localPlayer = PhosphorPlayer(mainLooper)
        remotePlayer = RemotePlayer(mainLooper)
        capturePlayer = CaptureMirrorPlayer(mainLooper).apply {
            playPauseRouter = ::routeCapturePlayPause
            // Without notification access there is no controller — but media KEYS need
            // no permission and the system routes them to the active app (Ben's next/
            // back-dead-on-capture repro, 07-18). Precise controller when we have one,
            // system-wide key otherwise.
            nextRouter = {
                externalCaptureController?.transportControls?.skipToNext()
                    ?: dispatchSystemMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            }
            previousRouter = {
                externalCaptureController?.transportControls?.skipToPrevious()
                    ?: dispatchSystemMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            }
            seekRouter = { externalCaptureController?.transportControls?.seekTo(it) }
        }
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
        if (target !== capturePlayer && captureActive) {
            leaveCaptureMirror()
            startService(
                Intent(this, CaptureService::class.java).setAction(CaptureService.ACTION_STOP)
            )
        }
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
            ACTION_CAPTURE_STARTED -> {
                beginCaptureMirror()
                return START_NOT_STICKY
            }
            ACTION_CAPTURE_STOPPED -> {
                endCaptureMirror()
                return START_NOT_STICKY
            }
            ACTION_CAPTURE_ACCESS_CHANGED -> {
                if (captureActive) {
                    if (intent.getBooleanExtra(EXTRA_CAPTURE_ACCESS_GRANTED, false)) {
                        registerCaptureSessionsListener()
                        refreshCaptureController()
                    } else {
                        clearExternalCaptureController()
                        publishCaptureMetadata(null)
                        publishCapturePlayback(null)
                    }
                }
                return START_NOT_STICKY
            }
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

    // ── Phone-local capture face: external Android session → our ONE MediaSession. ──
    private fun beginCaptureMirror() {
        if (captureActive) {
            refreshCaptureController()
            return
        }
        // Capture supersedes either deck without creating a second MediaSession.
        if (session?.player === remotePlayer) {
            remotePolling = false
            remoteEndpoint = null
            PhosphorNative.remoteDisconnect()
            clearRemoteNetwork()
            remotePlayer.reset()
        }
        localPlayer.playWhenReady = false
        captureActive = true
        captureTrackKey = null
        captureArtwork = null
        capturePlayer.activate()
        session?.setPlayer(capturePlayer)
        registerCaptureSessionsListener()
        refreshCaptureController()
    }

    private fun endCaptureMirror() {
        if (!captureActive) return
        leaveCaptureMirror()
        // Keep the now-empty capture player attached until another source is explicitly
        // chosen. Switching back to localPlayer here would resurrect its old title/art.
    }

    private fun leaveCaptureMirror() {
        if (!captureActive) return
        captureActive = false
        captureArtGeneration++
        captureTrackKey = null
        captureArtwork = null
        clearExternalCaptureController()
        if (captureSessionsListenerRegistered) {
            runCatching {
                platformSessionManager.removeOnActiveSessionsChangedListener(activeSessionsChanged)
            }
            captureSessionsListenerRegistered = false
        }
        capturePlayer.reset()
    }

    private fun registerCaptureSessionsListener() {
        if (captureSessionsListenerRegistered) return
        runCatching {
            platformSessionManager.addOnActiveSessionsChangedListener(
                activeSessionsChanged,
                notificationListenerComponent,
                main,
            )
        }.onSuccess {
            captureSessionsListenerRegistered = true
        }
    }

    private fun refreshCaptureController() {
        if (!captureActive) return
        val controllers = runCatching {
            platformSessionManager.getActiveSessions(notificationListenerComponent)
        }.getOrElse {
            clearExternalCaptureController()
            publishCaptureMetadata(null)
            publishCapturePlayback(null)
            return
        }
        chooseCaptureController(controllers)
    }

    /**
     * Never mirror Phosphor itself. Prefer PLAYING, then another active state, then a
     * metadata-bearing session; retain the current controller on an exact tie.
     */
    private fun chooseCaptureController(controllers: List<PlatformMediaController>) {
        val candidates = controllers.filter { it.packageName != packageName }
        val chosen = candidates.maxWithOrNull(
            compareBy<PlatformMediaController> {
                it.playbackState?.state == PlatformPlaybackState.STATE_PLAYING
            }.thenBy {
                it.playbackState?.state in ACTIVE_PLATFORM_STATES
            }.thenBy {
                it.metadata != null
            }.thenBy {
                it === externalCaptureController
            }
        )
        if (chosen !== externalCaptureController) {
            clearExternalCaptureController()
            externalCaptureController = chosen
            chosen?.registerCallback(externalControllerCallback, main)
            captureTrackKey = null
            captureArtwork = null
            captureArtGeneration++
        }
        publishCaptureMetadata(chosen?.metadata)
        publishCapturePlayback(chosen?.playbackState)
    }

    private fun clearExternalCaptureController() {
        externalCaptureController?.let {
            runCatching { it.unregisterCallback(externalControllerCallback) }
        }
        externalCaptureController = null
        captureArtGeneration++
        captureTrackKey = null
        captureArtwork = null
    }

    private fun publishCaptureMetadata(metadata: PlatformMediaMetadata?) {
        if (!captureActive) return
        val controller = externalCaptureController
        if (metadata == null || controller == null) {
            captureTrackKey = null
            captureArtwork = null
            captureArtGeneration++
            capturePlayer.updateMetadata(null, null, null, null, C.TIME_UNSET, null)
            return
        }

        val title = metadata.getText(PlatformMediaMetadata.METADATA_KEY_TITLE)?.toString()
            ?.ifBlank { null }
            ?: metadata.getText(PlatformMediaMetadata.METADATA_KEY_DISPLAY_TITLE)?.toString()
                ?.ifBlank { null }
        val artist = metadata.getText(PlatformMediaMetadata.METADATA_KEY_ARTIST)?.toString()
            ?.ifBlank { null }
            ?: metadata.getText(PlatformMediaMetadata.METADATA_KEY_ALBUM_ARTIST)?.toString()
                ?.ifBlank { null }
        val album = metadata.getText(PlatformMediaMetadata.METADATA_KEY_ALBUM)?.toString()
            ?.ifBlank { null }
        val mediaId = metadata.getString(PlatformMediaMetadata.METADATA_KEY_MEDIA_ID)
            ?.ifBlank { null }
        val artUri = metadata.getString(PlatformMediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?.ifBlank { null }
            ?: metadata.getString(PlatformMediaMetadata.METADATA_KEY_ART_URI)?.ifBlank { null }
        val bitmap = metadata.getBitmap(PlatformMediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(PlatformMediaMetadata.METADATA_KEY_ART)
        val duration = if (metadata.containsKey(PlatformMediaMetadata.METADATA_KEY_DURATION)) {
            metadata.getLong(PlatformMediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0L)
        } else C.TIME_UNSET
        val nextTrackKey = listOf(
            controller.packageName,
            mediaId.orEmpty(),
            title.orEmpty(),
            artist.orEmpty(),
            album.orEmpty(),
            artUri.orEmpty(),
        ).joinToString("\u0000")
        val trackChanged = nextTrackKey != captureTrackKey
        if (trackChanged) {
            captureTrackKey = nextTrackKey
            captureArtwork = null
            captureArtGeneration++
        }
        if (bitmap == null && artUri == null) {
            captureArtwork = null
            captureArtGeneration++
        }
        capturePlayer.updateMetadata(
            title,
            artist,
            album,
            controller.packageName,
            duration,
            captureArtwork,
        )
        if (bitmap != null || artUri != null) {
            resolveCaptureArtwork(controller, nextTrackKey, bitmap, artUri)
        }
    }

    private fun publishCapturePlayback(state: PlatformPlaybackState?) {
        if (!captureActive) return
        // Transport is ALWAYS offered while capturing: with a controller we route
        // precisely; without one the routers fall back to system media keys — so the
        // console and notification never show a dead face (Ben's repro, 07-18).
        val guaranteed = PlatformPlaybackState.ACTION_PLAY or
            PlatformPlaybackState.ACTION_PAUSE or
            PlatformPlaybackState.ACTION_PLAY_PAUSE or
            PlatformPlaybackState.ACTION_SKIP_TO_NEXT or
            PlatformPlaybackState.ACTION_SKIP_TO_PREVIOUS
        capturePlayer.updatePlayback(
            state = state?.state ?: PlatformPlaybackState.STATE_NONE,
            actions = (state?.actions ?: 0L) or guaranteed,
            positionMs = state?.position ?: C.TIME_UNSET,
            positionUpdateElapsedMs = state?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime(),
        )
    }

    /** Resolve either platform Bitmap or ART_URI off-main, then publish compressed bytes. */
    private fun resolveCaptureArtwork(
        owner: PlatformMediaController,
        trackKey: String,
        bitmap: Bitmap?,
        artUri: String?,
    ) {
        val generation = ++captureArtGeneration
        Thread({
            val decoded = bitmap ?: artUri?.let(::decodeArtworkUri)
            val bytes = decoded?.let(::compressArtwork)
            main.post {
                if (
                    captureActive &&
                    externalCaptureController === owner &&
                    captureTrackKey == trackKey &&
                    captureArtGeneration == generation
                ) {
                    captureArtwork = bytes?.takeIf { it.isNotEmpty() }
                    capturePlayer.setArtwork(captureArtwork)
                }
            }
        }, "capture-art").start()
    }

    private fun decodeArtworkUri(value: String): Bitmap? = runCatching {
        val uri = Uri.parse(value)
        val stream = when (uri.scheme?.lowercase()) {
            "http", "https" -> URL(value).openConnection().apply {
                connectTimeout = 4_000
                readTimeout = 4_000
            }.getInputStream()
            else -> contentResolver.openInputStream(uri)
        }
        stream?.use(BitmapFactory::decodeStream)
    }.getOrNull()

    private fun compressArtwork(source: Bitmap): ByteArray? = runCatching {
        val largest = maxOf(source.width, source.height)
        val scaled = if (largest > MAX_ART_EDGE) {
            val scale = MAX_ART_EDGE.toFloat() / largest
            Bitmap.createScaledBitmap(
                source,
                (source.width * scale).toInt().coerceAtLeast(1),
                (source.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else source
        ByteArrayOutputStream().use { out ->
            val format = if (scaled.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            if (!scaled.compress(format, 88, out)) return@runCatching null
            out.toByteArray()
        }.also {
            if (scaled !== source) scaled.recycle()
        }
    }.getOrNull()

    /** System-wide media key: routes to the active app, no permission needed. */
    private fun dispatchSystemMediaKey(code: Int) {
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private fun routeCapturePlayPause(play: Boolean) {
        val controller = externalCaptureController ?: run {
            dispatchSystemMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            return
        }
        val actions = controller.playbackState?.actions ?: 0L
        val directAction = if (play) PlatformPlaybackState.ACTION_PLAY else PlatformPlaybackState.ACTION_PAUSE
        if (actions and directAction != 0L) {
            if (play) controller.transportControls.play() else controller.transportControls.pause()
        } else if (actions and PlatformPlaybackState.ACTION_PLAY_PAUSE != 0L) {
            controller.dispatchMediaButtonEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            )
            controller.dispatchMediaButtonEvent(
                KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            )
        }
    }

    // ── The queue engine: SAF URIs staged into files/staged on demand, next prefetched.
    //    Staged copies are TRANSIENT: pruned on service create/destroy and after every
    //    open (441 MB of forgotten .wav copies taught this — Ben's storage audit). ──
    private var queueUris: MutableList<String?> = mutableListOf()
    private var queuePaths: MutableList<String?> = mutableListOf()
    private var stageGen = 0

    private fun stagedRoot() = java.io.File(filesDir, "staged").apply { mkdirs() }

    /**
     * Delete every staged audio copy except [keep] (absolute paths). Also sweeps the
     * legacy locations that leaked before staging was unified: audio dropped in the
     * filesDir root (loadUri's old home, incl. current_track.*) and the old queue/ dir.
     * Deleting a file the deck still has open is safe (unlink semantics) — the space
     * frees when the deck closes it.
     */
    private fun pruneStaged(keep: Set<String> = emptySet()) {
        runCatching {
            stagedRoot().walkBottomUp().forEach { f ->
                if (f.isFile && f.absolutePath !in keep) f.delete()
                else if (f.isDirectory && f != stagedRoot()) f.delete() // empty dirs only
            }
            java.io.File(filesDir, "queue").deleteRecursively()
            val rootKeep = setOf("profileInstalled", "selftest.json", "selftest.png")
            filesDir.listFiles()?.forEach { f ->
                if (f.isFile && f.name !in rootKeep && f.absolutePath !in keep) f.delete()
            }
        }
    }

    private fun stagedPath(i: Int): String? {
        queuePaths.getOrNull(i)?.let { return it }
        val uriStr = queueUris.getOrNull(i) ?: return null
        val uri = android.net.Uri.parse(uriStr ?: return null)
        val name = "q$i-" + (uri.lastPathSegment ?: "track").substringAfterLast('/')
            .substringAfterLast(':').replace('/', '_')
        val dst = java.io.File(stagedRoot(), "queue/$name")
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
                // Prefetch the next entry so the gapless hand-off has a local file ready,
                // then drop every other staged copy — two tracks is the whole budget.
                val next = if (i + 1 < queueUris.size) stagedPath(i + 1) else null
                queuePaths.indices.forEach { j ->
                    if (j != i && queuePaths[j] != null && queuePaths[j] != next) {
                        queuePaths[j] = null
                    }
                }
                pruneStaged(setOfNotNull(path, next))
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
        leaveCaptureMirror()
        remotePolling = false
        remoteEndpoint = null
        clearRemoteNetwork()
        unregisterReceiver(noisyReceiver)
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        session?.release()
        localPlayer.release()
        remotePlayer.release()
        capturePlayer.release()
        session = null
        PhosphorNative.remoteDisconnect()
        PhosphorNative.deckClose()
        pruneStaged() // exit leaves no transient audio behind
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
        const val ACTION_CAPTURE_STARTED = "dev.phosphor.mobil3.CAPTURE_STARTED"
        const val ACTION_CAPTURE_STOPPED = "dev.phosphor.mobil3.CAPTURE_STOPPED"
        const val ACTION_CAPTURE_ACCESS_CHANGED =
            "dev.phosphor.mobil3.CAPTURE_ACCESS_CHANGED"
        const val EXTRA_CAPTURE_ACCESS_GRANTED = "capture_access_granted"
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
        const val EXTRA_LABEL = "label"
        private const val MAX_ART_EDGE = 1024
        private val ACTIVE_PLATFORM_STATES = setOf(
            PlatformPlaybackState.STATE_PLAYING,
            PlatformPlaybackState.STATE_BUFFERING,
            PlatformPlaybackState.STATE_CONNECTING,
            PlatformPlaybackState.STATE_FAST_FORWARDING,
            PlatformPlaybackState.STATE_REWINDING,
            PlatformPlaybackState.STATE_SKIPPING_TO_NEXT,
            PlatformPlaybackState.STATE_SKIPPING_TO_PREVIOUS,
            PlatformPlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM,
        )
    }
}

/**
 * A Media3 face for the selected phone-local Android media session. It owns no audio and
 * no MediaSession: PlaybackService temporarily installs it into Phosphor's single session.
 */
private class CaptureMirrorPlayer(looper: android.os.Looper) : SimpleBasePlayer(looper) {
    var playPauseRouter: ((Boolean) -> Unit)? = null
    var nextRouter: (() -> Unit)? = null
    var previousRouter: (() -> Unit)? = null
    var seekRouter: ((Long) -> Unit)? = null

    private var active = false
    private var title: String? = null
    private var artist: String? = null
    private var album: String? = null
    private var sourcePackage: String? = null
    private var artworkBytes: ByteArray? = null
    private var durationMs = C.TIME_UNSET
    private var platformState = PlatformPlaybackState.STATE_NONE
    private var actions = 0L
    private var playing = false
    private var positionMs = C.TIME_UNSET
    private var positionUpdateElapsedMs = 0L

    override fun getState(): State {
        val commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_GET_METADATA,
                Player.COMMAND_RELEASE,
            )
            .apply {
                if (supports(
                        PlatformPlaybackState.ACTION_PLAY,
                        PlatformPlaybackState.ACTION_PAUSE,
                        PlatformPlaybackState.ACTION_PLAY_PAUSE,
                    )
                ) add(Player.COMMAND_PLAY_PAUSE)
                if (supports(PlatformPlaybackState.ACTION_SKIP_TO_NEXT)) {
                    addAll(Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                }
                if (supports(PlatformPlaybackState.ACTION_SKIP_TO_PREVIOUS)) {
                    addAll(
                        Player.COMMAND_SEEK_TO_PREVIOUS,
                        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    )
                }
                if (supports(PlatformPlaybackState.ACTION_SEEK_TO)) {
                    addAll(
                        Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                        Player.COMMAND_SEEK_BACK,
                        Player.COMMAND_SEEK_FORWARD,
                    )
                }
            }
            .build()
        val playback = when (platformState) {
            PlatformPlaybackState.STATE_BUFFERING,
            PlatformPlaybackState.STATE_CONNECTING -> Player.STATE_BUFFERING
            PlatformPlaybackState.STATE_ERROR -> Player.STATE_IDLE
            PlatformPlaybackState.STATE_NONE -> if (active) Player.STATE_READY else Player.STATE_IDLE
            else -> Player.STATE_READY
        }
        val builder = State.Builder()
            .setAvailableCommands(commands)
            .setPlayWhenReady(playing, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(playback)
            .setMaxSeekToPreviousPositionMs(Long.MAX_VALUE)
        if (active) {
            builder.setPlaylist(listOf(ghost("capture:prev"), nowItem(), ghost("capture:next")))
            builder.setCurrentMediaItemIndex(1)
            if (positionMs != C.TIME_UNSET) {
                builder.setContentPositionMs {
                    if (playing) {
                        positionMs + (SystemClock.elapsedRealtime() - positionUpdateElapsedMs)
                    } else positionMs
                }
            }
        }
        return builder.build()
    }

    private fun supports(vararg candidates: Long): Boolean =
        candidates.any { actions and it != 0L }

    private fun ghost(id: String): MediaItemData =
        MediaItemData.Builder(id)
            .setMediaItem(MediaItem.Builder().setMediaId(id).build())
            .build()

    private fun nowItem(): MediaItemData {
        val metadata = MediaMetadata.Builder()
            .setTitle(title ?: "everything playing")
            .setArtist(artist)
            .setAlbumTitle(album)
            .setExtras(Bundle().apply {
                putString("source", "capture")
                sourcePackage?.let { putString("package", it) }
            })
            .apply {
                artworkBytes?.let {
                    setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                }
            }
            .build()
        return MediaItemData.Builder("capture:now")
            .setMediaItem(
                MediaItem.Builder()
                    .setMediaId("capture:now")
                    .setMediaMetadata(metadata)
                    .build()
            )
            .setDurationUs(if (durationMs == C.TIME_UNSET) C.TIME_UNSET else durationMs * 1000)
            .setIsSeekable(supports(PlatformPlaybackState.ACTION_SEEK_TO))
            .build()
    }

    fun activate() {
        active = true
        clearFace()
        invalidateState()
    }

    fun reset() {
        active = false
        clearFace()
        invalidateState()
    }

    private fun clearFace() {
        title = null
        artist = null
        album = null
        sourcePackage = null
        artworkBytes = null
        durationMs = C.TIME_UNSET
        platformState = PlatformPlaybackState.STATE_NONE
        actions = 0L
        playing = false
        positionMs = C.TIME_UNSET
        positionUpdateElapsedMs = 0L
    }

    fun updateMetadata(
        title: String?,
        artist: String?,
        album: String?,
        sourcePackage: String?,
        durationMs: Long,
        artwork: ByteArray?,
    ) {
        this.title = title
        this.artist = artist
        this.album = album
        this.sourcePackage = sourcePackage
        this.durationMs = durationMs
        artworkBytes = artwork
        invalidateState()
    }

    fun setArtwork(bytes: ByteArray?) {
        artworkBytes = bytes
        invalidateState()
    }

    fun updatePlayback(
        state: Int,
        actions: Long,
        positionMs: Long,
        positionUpdateElapsedMs: Long,
    ) {
        platformState = state
        this.actions = actions
        playing = state == PlatformPlaybackState.STATE_PLAYING
        this.positionMs = positionMs
        this.positionUpdateElapsedMs = positionUpdateElapsedMs
        invalidateState()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady != playing) {
            playPauseRouter?.invoke(playWhenReady)
            playing = playWhenReady
            invalidateState()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int,
    ): ListenableFuture<*> {
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> nextRouter?.invoke()
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> previousRouter?.invoke()
            else -> if (supports(PlatformPlaybackState.ACTION_SEEK_TO)) {
                seekRouter?.invoke(positionMs)
                this.positionMs = positionMs
                positionUpdateElapsedMs = SystemClock.elapsedRealtime()
            }
        }
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        if (playing) playPauseRouter?.invoke(false)
        playing = false
        invalidateState()
        return Futures.immediateVoidFuture()
    }
}
