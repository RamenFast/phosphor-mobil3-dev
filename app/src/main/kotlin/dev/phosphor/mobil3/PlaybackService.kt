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
import android.os.Bundle
import android.os.Handler
import android.os.ResultReceiver
import android.os.SystemClock
import android.provider.DocumentsContract
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.core.content.IntentCompat
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

// The deck's Android citizenship: ONE MediaSessionService, ONE MediaSession, TWO players
// — the local Rust deck (PhosphorPlayer) and the Tailscale bridge deck (RemotePlayer) —
// swapped with MediaSession.setPlayer(). The loaded deck owns the transport: lock screen,
// notification, earbuds and Bluetooth all drive whichever deck the session holds.
// SimpleBasePlayer does NOT handle audio focus or becoming-noisy — hand-rolled here.
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private lateinit var localPlayer: PhosphorPlayer
    private lateinit var remotePlayer: RemotePlayer
    private lateinit var capturePlayer: CaptureMirrorPlayer
    private var session: MediaSession? = null
    private lateinit var audioManager: AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private val focusResume = LocalFocusResume()
    private lateinit var main: Handler
    private var remotePolling = false
    private data class RemoteEndpoint(val host: String, val port: Int, val label: String)
    private var remoteEndpoint: RemoteEndpoint? = null
    private var remoteGainApplied = false
    private lateinit var platformSessionManager: MediaSessionManager
    private lateinit var notificationListenerComponent: ComponentName
    private var captureActive = false
    private var captureSessionsListenerRegistered = false
    private val captureBinding = CaptureControllerBinding<PlatformMediaController>()
    private val externalCaptureController: PlatformMediaController? get() = captureBinding.current
    private var externalControllerCallback: PlatformMediaController.Callback? = null
    private var captureTrackKey: String? = null
    private var captureArtwork: ByteArray? = null
    private var captureArtGeneration = 0
    private val taskRevision = BackgroundLifecycle.policy.revision
    private val captureOwner = CaptureOwnerToken()
    private val priorShutdown = retirement.pending

    private sealed interface LocalDeckRequest {
        data class Tree(val uri: String, val start: Int, val transportRevision: Long) : LocalDeckRequest
        data class Document(val uri: String, val transportRevision: Long) : LocalDeckRequest
        data class Path(val path: String, val transportRevision: Long) : LocalDeckRequest
        data class Release(
            val stopReaders: Boolean = false,
            val micRequest: String? = null,
            val after: () -> Unit,
        ) : LocalDeckRequest
        data class Play(
            val index: Int,
            val positionMs: Long?,
            val queueUris: MutableList<String?>,
            val queuePaths: MutableList<String?>,
            val titles: List<String>,
            val fromEof: Boolean = false,
            val transportRevision: Long,
        ) : LocalDeckRequest

        data object Close : LocalDeckRequest
        data object Retain : LocalDeckRequest
        data class Shutdown(
            val readers: List<CompletableFuture<String?>>,
            val disconnectRemote: Boolean,
            val completion: CompletableFuture<String?>,
        ) : LocalDeckRequest
    }

    private val localDeckExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "local-deck")
    }
    private val localDeckRequests = LatestRequestSlot<LocalDeckRequest>(
        schedule = { task -> localDeckExecutor.execute(task) },
        consume = { request, isLatest ->
            runCatching {
                val error = SourceRetirement.await(priorShutdown)
                if (error == null) runLocalDeckRequest(request, isLatest)
                else {
                    if (request is LocalDeckRequest.Shutdown) {
                        request.completion.complete(error)
                        main.post { if (!destroying) stopSelf() }
                    }
                    reportLocal("Previous source did not stop: $error", isLatest)
                }
            }
                .onFailure {
                    if (request is LocalDeckRequest.Shutdown) {
                        request.completion.complete("Source shutdown failed: ${it.message}")
                        main.post { if (!destroying) stopSelf() }
                    }
                    Log.e(TAG, "local deck request failed", it)
                    reportLocal("Local audio failed: ${it.message}. Choose a readable file or folder and retry", isLatest)
                    finishSelection(isLatest)
                }
        },
    )
    @Volatile private var destroying = false
    @Volatile private var stopping = false
    private var facesReleased = false
    // These fields belong only to localDeckExecutor.
    private var openedLocalPath: String? = null
    private var openedLocalQueuePaths: MutableList<String?>? = null
    private var openedLocalIndex = -1
    private val stopSequence = AtomicLong()
    private val localQueuePolicy = LocalQueuePolicy()
    private val playbackTruth = PlaybackTruth()
    private val sourceSurvival = LocalSourceSurvival()
    private val sourceWake = SourceWakeLock.forOwner(this, "playback")
    private var advancingAtEnd = false

    // Player events own local transport truth. Remote wake comes only from the existing link pump.
    private fun updatePlaybackWake() = synchronized(sourceSurvival) {
        when (session?.player) {
            localPlayer -> sourceWake.localChanged(
                published = !stopping && !destroying && !sourceSurvival.loss().native && localPlayer.queueSize() > 0,
                playing = localPlayer.isPlaying,
                ready = localPlayer.playbackState == Player.STATE_READY,
                failed = localPlayer.playerError != null,
            )
            capturePlayer, null -> sourceWake.stop()
            else -> Unit
        }
    }

    private fun retireNativeWake() = synchronized(sourceSurvival) {
        sourceSurvival.nativeReplacing()
        sourceWake.stop()
    }

    private val activePlayer: Player get() = session?.player ?: localPlayer

    private val activeSessionsChanged =
        MediaSessionManager.OnActiveSessionsChangedListener {
            if (captureActive) refreshCaptureController()
        }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (stopping || destroying) return
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                localPlayer.recordTransportIntent(false)
                activePlayer.playWhenReady = false // route died -> pause, never blast
            }
        }
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (stopping || destroying) return@OnAudioFocusChangeListener
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                localPlayer.recordTransportIntent(false)
                focusResume.cancel()
                activePlayer.playWhenReady = false
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                val wasPlaying = activePlayer.playWhenReady
                localPlayer.recordTransportIntent(false)
                activePlayer.playWhenReady = false
                focusResume.arm(wasPlaying, localPlayer.transportRevision())
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (focusResume.take(localPlayer.transportRevision())) {
                    activePlayer.playWhenReady = true
                }
            }
        }
    }

    private val focusOnPlay = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (!stopping && !destroying) updatePlaybackWake()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady) requestFocus()
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Fresh service = nothing staged is open yet: sweep every transient audio copy
        // (settings/prefs untouched). Covers force-stop exits that skip onDestroy.
        // Prune only on the serial deck worker after it knows which staged file to retain.
        main = Handler(mainLooper)
        owner = this
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        platformSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        notificationListenerComponent =
            ComponentName(this, CaptureNotificationListenerService::class.java)
        localPlayer = PhosphorPlayer(mainLooper)
        remotePlayer = RemotePlayer(mainLooper)
        remotePlayer.onTransportIntent = localPlayer::recordTransportIntent
        capturePlayer = CaptureMirrorPlayer(mainLooper).apply {
            playPauseRouter = ::routeCapturePlayPause
            nextRouter = { routeCaptureSkip(next = true) }
            previousRouter = { routeCaptureSkip(next = false) }
            seekRouter = { position ->
                externalCaptureController?.let { controller ->
                    val state = controller.playbackState
                    val duration = controller.metadata?.getLong(PlatformMediaMetadata.METADATA_KEY_DURATION) ?: 0L
                    if (CaptureMirrorPolicy.seekable(state?.state ?: PlatformPlaybackState.STATE_NONE,
                            state?.actions ?: 0L, duration)) {
                        controller.transportControls.seekTo(position.coerceIn(0L, duration))
                    }
                }
            }
        }
        localPlayer.onSwitchTrack = ::stageAndOpen
        localPlayer.onSeek = ::seekLocalDeck
        localPlayer.onStopRequested = ::closeLocalDeck
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
        captureOwner.restore(CaptureService.currentOwnerId(), ::beginCaptureMirror)
    }

    // Swap which deck the one session controls. Main thread only; both players live on
    // mainLooper (the documented setPlayer constraint).
    private fun switchTo(target: Player) {
        val s = session ?: return
        if (s.player === target) return
        sourceWake.stop()
        if (target !== capturePlayer && captureActive) {
            leaveCaptureMirror()
            startService(
                Intent(this, CaptureService::class.java).setAction(CaptureService.ACTION_STOP)
            )
        }
        if (target === remotePlayer) {
            // Entering remote: silence the other feeders (one scope ring, one owner).
            localPlayer.setPublishedPlaying(false)
            // Reader release was acknowledged by the serial request before remote startup.
        }
        s.setPlayer(target)
        updatePlaybackWake()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (stopping || destroying || !BackgroundLifecycle.accepts(intent)) return START_NOT_STICKY
        if (BackgroundLifecycle.policy.removed) {
            // A retained source may receive transport commands, never a new source intent.
            if (intent?.action in SOURCE_ACTIONS || intent?.hasExtra(EXTRA_OPEN) == true) return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_CAPTURE_STARTED -> {
                val ownerId = intent.getLongExtra(CaptureService.EXTRA_CAPTURE_OWNER, -1)
                if (!CaptureService.isOwner(ownerId)) return START_NOT_STICKY
                captureOwner.attach(ownerId)
                // Startup already followed the explicit source-release request. This is status,
                // not a new source selection that can supersede a later tree request.
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
                        unregisterCaptureSessionsListener()
                        clearExternalCaptureController()
                        publishCaptureMetadata(null)
                        publishCapturePlayback(null)
                    }
                }
                return START_NOT_STICKY
            }
            ACTION_REMOTE_CONNECT -> {
                beginLocalSelection()
                val host = intent.getStringExtra(EXTRA_HOST) ?: return START_NOT_STICKY
                val port = intent.getIntExtra(EXTRA_PORT, 45777)
                val label = intent.getStringExtra(EXTRA_LABEL) ?: host
                localDeckRequests.enqueue(LocalDeckRequest.Release(stopReaders = true) { startRemote(host, port, label) })
                return START_NOT_STICKY
            }
            ACTION_REMOTE_DISCONNECT -> {
                closeLocalDeck()
                stopRemote()
                return START_NOT_STICKY
            }
            ACTION_RELEASE_LOCAL -> {
                beginLocalSelection()
                val reply = IntentCompat.getParcelableExtra(intent, EXTRA_RELEASE_REPLY, ResultReceiver::class.java)
                localDeckRequests.enqueue(LocalDeckRequest.Release(
                    stopReaders = true,
                    micRequest = intent.getStringExtra(CaptureService.EXTRA_MIC_REQUEST),
                ) {
                    reply?.send(0, Bundle().apply {
                        putLong(EXTRA_SOURCE_REVISION, localSourcePublication.current.revision)
                    })
                })
                return START_NOT_STICKY
            }
            ACTION_OPEN_TREE -> {
                beginLocalSelection()
                intent.getStringExtra(EXTRA_TREE_URI)?.let { uri ->
                    localDeckRequests.enqueue(LocalDeckRequest.Tree(uri, intent.getIntExtra(EXTRA_QUEUE_START, 0), localPlayer.transportRevision()))
                }
                return START_NOT_STICKY
            }
            ACTION_OPEN_DOCUMENT -> {
                beginLocalSelection()
                intent.getStringExtra(EXTRA_DOCUMENT_URI)?.let { uri ->
                    localDeckRequests.enqueue(LocalDeckRequest.Document(uri, localPlayer.transportRevision()))
                }
                return START_NOT_STICKY
            }
        }
        intent?.getStringExtra(EXTRA_OPEN)?.let { path ->
            beginLocalSelection()
            localDeckRequests.enqueue(LocalDeckRequest.Path(path, localPlayer.transportRevision()))
        }
        return super.onStartCommand(intent, flags, startId)
    }

    // ── Phone-local capture face: external Android session → our ONE MediaSession. ──
    private fun beginCaptureMirror() {
        sourceWake.stop() // The mirror never owns the projection or its screen lock.
        val previousPlayer = session?.player
        CaptureMirrorPolicy.attach(session?.player, capturePlayer) { session?.setPlayer(it) }
        if (captureActive) {
            refreshCaptureController()
            return
        }
        // Capture supersedes either deck without creating a second MediaSession.
        if (previousPlayer === remotePlayer) {
            remotePolling = false
            remoteEndpoint = null
            PhosphorNative.remoteDisconnect()
            remotePlayer.reset()
        }
        localPlayer.setPublishedPlaying(false)
        captureActive = true
        captureTrackKey = null
        captureArtwork = null
        capturePlayer.activate()
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
        captureOwner.clear()
        captureArtGeneration++
        captureTrackKey = null
        captureArtwork = null
        clearExternalCaptureController()
        unregisterCaptureSessionsListener()
        capturePlayer.reset()
    }

    private fun unregisterCaptureSessionsListener() {
        if (captureSessionsListenerRegistered) {
            runCatching {
                platformSessionManager.removeOnActiveSessionsChangedListener(activeSessionsChanged)
            }
            captureSessionsListenerRegistered = false
        }
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

    private fun refreshCaptureController(excluded: android.media.session.MediaSession.Token? = null) {
        if (!captureActive) return
        val controllers = runCatching {
            platformSessionManager.getActiveSessions(notificationListenerComponent)
        }.getOrElse {
            unregisterCaptureSessionsListener()
            clearExternalCaptureController()
            publishCaptureMetadata(null)
            publishCapturePlayback(null)
            return
        }
        chooseCaptureController(controllers.filter { it.sessionToken != excluded })
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
                it.sessionToken == externalCaptureController?.sessionToken
            }
        )
        if (chosen !== externalCaptureController) {
            clearExternalCaptureController()
            val isCurrent = captureBinding.bind(chosen)
            if (chosen != null) {
                val callback = object : PlatformMediaController.Callback() {
                    override fun onMetadataChanged(metadata: PlatformMediaMetadata?) {
                        if (captureActive && isCurrent()) publishCaptureMetadata(metadata)
                    }

                    override fun onPlaybackStateChanged(state: PlatformPlaybackState?) {
                        if (captureActive && isCurrent()) {
                            publishCaptureMetadata(chosen.metadata)
                            publishCapturePlayback(state)
                        }
                    }

                    override fun onSessionDestroyed() {
                        if (captureActive && isCurrent()) {
                            clearExternalCaptureController()
                            publishCaptureMetadata(null)
                            publishCapturePlayback(null)
                            refreshCaptureController(chosen.sessionToken)
                        }
                    }
                }
                externalControllerCallback = callback
                runCatching { chosen.registerCallback(callback, main) }.onFailure {
                    clearExternalCaptureController()
                }
            }
            captureTrackKey = null
            captureArtwork = null
            captureArtGeneration++
        }
        publishCaptureMetadata(externalCaptureController?.metadata)
        publishCapturePlayback(externalCaptureController?.playbackState)
    }

    private fun clearExternalCaptureController() {
        val previous = externalCaptureController
        captureBinding.bind(null)
        externalControllerCallback?.let { callback ->
            runCatching { previous?.unregisterCallback(callback) }
        }
        externalControllerCallback = null
        captureArtGeneration++
        captureTrackKey = null
        captureArtwork = null
    }

    private fun publishCaptureMetadata(metadata: PlatformMediaMetadata?) {
        if (!captureActive) return
        val controller = externalCaptureController
        if (metadata == null || controller == null ||
            !CaptureMirrorPolicy.available(controller.playbackState?.state ?: PlatformPlaybackState.STATE_NONE)) {
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
        if (state == null || !CaptureMirrorPolicy.available(state.state)) publishCaptureMetadata(null)
        capturePlayer.updatePlayback(
            state = state?.state ?: PlatformPlaybackState.STATE_NONE,
            actions = state?.actions ?: 0L,
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
        val uri = value.toUri()
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
            source.scale(
                (source.width * scale).toInt().coerceAtLeast(1),
                (source.height * scale).toInt().coerceAtLeast(1),
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

    private fun routeCapturePlayPause(play: Boolean) {
        localPlayer.recordTransportIntent(play)
        val controller = externalCaptureController ?: return
        val state = controller.playbackState ?: return
        if (!CaptureMirrorPolicy.available(state.state)) return
        if (play == CaptureMirrorPolicy.playing(state.state)) return
        val actions = state.actions
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

    private fun routeCaptureSkip(next: Boolean) {
        val controller = externalCaptureController ?: return
        val state = controller.playbackState ?: return
        val action = if (next) PlatformPlaybackState.ACTION_SKIP_TO_NEXT else PlatformPlaybackState.ACTION_SKIP_TO_PREVIOUS
        if (!CaptureMirrorPolicy.supports(state.state, state.actions, action)) return
        if (next) controller.transportControls.skipToNext() else controller.transportControls.skipToPrevious()
    }

    // Queue documents are staged on demand and removed on startup, shutdown, and track changes.
    private var queueUris: MutableList<String?> = mutableListOf()
    private var queuePaths: MutableList<String?> = mutableListOf()
    private var queueTitles: List<String> = emptyList()

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

    private fun stagedPath(
        i: Int,
        requestUris: MutableList<String?>,
        requestPaths: MutableList<String?>,
    ): String? {
        requestPaths.getOrNull(i)?.let { return it }
        val uriStr = requestUris.getOrNull(i) ?: return null
        val uri = uriStr.toUri()
        val name = "q$i-" + (uri.lastPathSegment ?: "track").substringAfterLast('/')
            .substringAfterLast(':').replace('/', '_')
        val dst = java.io.File(stagedRoot(), "queue/${java.util.UUID.randomUUID()}-${name.takeLast(96)}")
        return stageAudioFile(dst) { contentResolver.openInputStream(uri) }
            .also { requestPaths[i] = it }
    }

    private fun stageAndOpen(i: Int) {
        enqueueLocalPlay(i, positionMs = null)
    }

    private fun seekLocalDeck(i: Int, positionMs: Long) {
        enqueueLocalPlay(i, positionMs.coerceAtLeast(0L))
    }

    private fun enqueueLocalPlay(i: Int, positionMs: Long?) {
        if (destroying || stopping) return
        beginLocalSelection()
        localDeckRequests.enqueue(
            LocalDeckRequest.Play(i, positionMs, queueUris, queuePaths, queueTitles, advancingAtEnd, localPlayer.transportRevision())
        )
    }

    private fun closeLocalDeck() {
        if (!destroying && !stopping) {
            beginLocalSelection()
            localDeckRequests.enqueue(LocalDeckRequest.Close)
        }
    }

    private fun runLocalDeckRequest(
        request: LocalDeckRequest,
        isLatest: () -> Boolean,
    ) {
        when (request) {
            is LocalDeckRequest.Shutdown -> {
                // Terminal work runs even if Android destroys the service while readers stop.
                var failure: String? = null
                fun stop(action: () -> Unit) {
                    runCatching(action).onFailure {
                        failure = failure ?: "Source shutdown failed: ${it.message}"
                        Log.e(TAG, "Owned source shutdown failed", it)
                    }
                }
                stop { if (openedLocalPath != null) closeOpenedLocal() else playbackTruth.closed() }
                stop { if (request.disconnectRemote) PhosphorNative.remoteDisconnect() }
                request.readers.forEach { reader ->
                    val error = runCatching { reader.get(4, TimeUnit.SECONDS) }
                        .getOrElse { "Reader shutdown did not complete: ${it.message}" }
                    if (error != null) Log.e(TAG, "Task source shutdown: $error")
                }
                stop { pruneStaged() }
                // This barrier fences native deck ownership only. A later selection must
                // retry the actual mic/capture owners, not inherit an immutable timeout.
                request.completion.complete(failure)
                main.post { if (!destroying) stopSelf() }
            }
            LocalDeckRequest.Retain -> {
                // A prepared but unpublished replacement is not a source that may linger.
                val lost = sourceSurvival.loss().native
                if (lost) {
                    if (openedLocalPath != null) closeOpenedLocal() else playbackTruth.closed()
                } else playbackTruth.retain(isLatest)
                main.post {
                    if (!destroying && !stopping && isLatest()) {
                        localQueuePolicy.failed(preservesNative = !lost)
                        if (lost) {
                            remotePolling = false
                            remoteEndpoint = null
                            remotePlayer.reset()
                            localPlayer.setPublishedPlaying(false)
                            localPlayer.setQueue(emptyList(), 0)
                        }
                    }
                }
            }
            LocalDeckRequest.Close -> {
                retireNativeWake()
                closeOpenedLocal()
            }
            is LocalDeckRequest.Release -> {
                if (request.stopReaders && !releaseReaders(isLatest, request.micRequest)) return
                retireNativeWake()
                closeOpenedLocal()
                if (!destroying && !stopping && isLatest()) main.post {
                    if (!destroying && !stopping && isLatest()) {
                        localPlayer.setPublishedPlaying(false)
                        localPlayer.setQueue(emptyList(), 0)
                        queueUris = mutableListOf()
                        queuePaths = mutableListOf()
                        queueTitles = emptyList()
                        sourceSurvival.published()
                        updatePlaybackWake()
                        localSourcePublication.published(LocalSourcePublication.Source.OTHER)
                        request.after()
                    }
                }
            }
            is LocalDeckRequest.Tree -> {
                val tree = request.uri.toUri()
                val current = { !destroying && !stopping && isLatest() }
                val source = DocumentTreeSource(contentResolver, tree, current)
                val entries = FolderTreeWalker(source::children, current) { _, error ->
                    reportLocal(error, isLatest)
                }.walk(DocumentsContract.getTreeDocumentId(tree))
                if (!current()) return
                if (entries.isEmpty()) {
                    reportLocal("No supported audio in this tree, choose another folder", isLatest)
                    finishSelection(isLatest)
                    return
                }
                runLocalPlayRequest(LocalDeckRequest.Play(
                    request.start.coerceIn(entries.indices), null,
                    entries.mapTo(mutableListOf<String?>()) {
                        DocumentsContract.buildDocumentUriUsingTree(tree, it.id).toString()
                    },
                    MutableList(entries.size) { null }, entries.map { it.name },
                    transportRevision = request.transportRevision,
                ), isLatest)
            }
            is LocalDeckRequest.Document -> {
                val uri = request.uri.toUri()
                val title = contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "track"
                runLocalPlayRequest(LocalDeckRequest.Play(
                    0, null, mutableListOf(request.uri), mutableListOf(null), listOf(title),
                    transportRevision = request.transportRevision,
                ), isLatest)
            }
            is LocalDeckRequest.Path -> runLocalPlayRequest(LocalDeckRequest.Play(
                0, null, mutableListOf(null), mutableListOf(request.path),
                listOf(request.path.substringAfterLast('/')),
                transportRevision = request.transportRevision,
            ), isLatest)
            is LocalDeckRequest.Play -> runLocalPlayRequest(request, isLatest)
        }
    }

    private fun closeOpenedLocal() {
        playbackTruth.closed()
        PhosphorNative.deckClose()
        openedLocalPath = null
        openedLocalQueuePaths = null
        openedLocalIndex = -1
    }

    private fun reportLocal(message: String, isLatest: () -> Boolean) {
        Log.w(TAG, "local audio: $message")
        main.post {
            if (!destroying && !stopping && isLatest()) Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun finishSelection(isLatest: () -> Boolean) {
        if (destroying || stopping || !isLatest()) return
        if (sourceSurvival.loss().native) {
            closeOpenedLocal()
            publishNativeFailure(isLatest)
            return
        }
        playbackTruth.retain(isLatest)
        main.post {
            if (!destroying && !stopping && isLatest()) {
                localQueuePolicy.failed(preservesNative = true)
                playbackTruth.publishCurrent(localPlayer::onTrackMetadata)
                startEndWatcher()
                val readers = sourceSurvival.loss().readers
                if (readers.isNotEmpty()) publishLocalSource(localSourcePublication.readersReleased(readers))
            }
        }
    }

    private fun beginLocalSelection() {
        localQueuePolicy.beginSelection()
        localSourcePublication.selected()
    }

    private fun publishLocalSource(snapshot: LocalSourcePublication.Snapshot) {
        sendBroadcast(Intent(ACTION_LOCAL_SOURCE_CHANGED).setPackage(packageName)
            .putExtra(EXTRA_SOURCE_REVISION, snapshot.revision))
    }

    private fun publishNativeFailure(isLatest: () -> Boolean) {
        main.post {
            if (destroying || stopping || !isLatest()) return@post
            // A superseded release may have disconnected remote before its guarded face reset.
            // Retire that session/poll too, so it cannot republish a source that no longer exists.
            remotePolling = false
            remoteEndpoint = null
            remotePlayer.reset()
            leaveCaptureMirror()
            localQueuePolicy.failed(preservesNative = false)
            localPlayer.setPublishedPlaying(false)
            localPlayer.setQueue(emptyList(), 0)
            switchTo(localPlayer)
            queueUris = mutableListOf()
            queuePaths = mutableListOf()
            queueTitles = emptyList()
            localSourcePublication.failed(released = true)?.let(::publishLocalSource)
        }
    }

    private fun releaseReaders(isLatest: () -> Boolean, micRequest: String? = null): Boolean {
        val current = { !destroying && !stopping && isLatest() }
        if (!current()) return false
        val id = stopSequence.incrementAndGet()
        val micStop = SourceStopRequest(id)
        val captureStop = SourceStopRequest(id)
        val releaseEpoch = sourceSurvival.epoch()
        fun recordRelease(stop: SourceStopRequest, reader: LocalSourcePublication.Reader) {
            if (!destroying && !stopping && sourceSurvival.readerStopped(releaseEpoch, reader, stop)) {
                // A stale request may finish its stop after the newest invalid request rejected.
                // Only a real newer source publication, not request supersession, retires this loss.
                val loss = sourceSurvival.loss()
                publishLocalSource(if (loss.native) {
                    localSourcePublication.published(LocalSourcePublication.Source.NONE)
                } else localSourcePublication.readersReleased(loss.readers))
            }
        }
        val reply = object : ResultReceiver(main) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                val accepted = captureStop.complete(
                    resultData?.getLong(CaptureService.EXTRA_STOP_REQUEST, -1) ?: -1,
                    if (resultCode == 0) null else resultData?.getString(CaptureService.EXTRA_STOP_ERROR)
                        ?: "Capture stop failed, stop capture and retry",
                    resultData?.getBoolean(CaptureService.EXTRA_STOP_OWNED, false) == true,
                )
                if (accepted) recordRelease(captureStop, LocalSourcePublication.Reader.CAPTURE)
            }
        }
        main.post {
            if (!current()) {
                micStop.complete(id, "Source request was superseded")
                captureStop.complete(id, "Source request was superseded")
                return@post
            }
            MicController.stopForLocal(id) { requestId, error, owned ->
                if (micStop.complete(requestId, error, owned)) recordRelease(micStop, LocalSourcePublication.Reader.MIC)
            }
            startService(Intent(this, CaptureService::class.java)
                .setAction(CaptureService.ACTION_STOP)
                .putExtra(CaptureService.EXTRA_STOP_REQUEST, id)
                .putExtra(CaptureService.EXTRA_MIC_REQUEST, micRequest)
                .putExtra(CaptureService.EXTRA_STOP_REPLY, reply))
        }
        val micError = micStop.await(4_000, current)
        val captureError = captureStop.await(4_000, current)
        val error = micError ?: captureError
        if (error != null) {
            reportLocal(error, isLatest)
            finishSelection(isLatest)
            return false
        }
        if (!current()) return false
        // Disconnecting remote also destroys a potentially published nonlocal source.
        cleanupOwnedSource(remoteEndpoint != null) {
            retireNativeWake()
            PhosphorNative.remoteDisconnect()
        }
        val reset = CompletableFuture<Unit>()
        main.post {
            if (current()) {
                remotePolling = false
                remoteEndpoint = null
                remotePlayer.reset()
                leaveCaptureMirror()
            }
            reset.complete(Unit)
        }
        reset.get(4, TimeUnit.SECONDS)
        return current()
    }

    private fun runLocalPlayRequest(
        request: LocalDeckRequest.Play,
        isLatest: () -> Boolean,
    ) {
        if (destroying || stopping || !isLatest()) return
        val previous = openedLocalIndex.takeIf { openedLocalQueuePaths === request.queuePaths }
        for (index in localTrackCandidates(request.queueUris.size, request.index, previous, request.fromEof)) {
            if (destroying || stopping || !isLatest()) return
            val path = runCatching { stagedPath(index, request.queueUris, request.queuePaths) }
                .onFailure { reportLocal("Skipped ${request.titles[index]}: ${it.message}", isLatest) }
                .getOrNull() ?: continue
            if (destroying || stopping || !isLatest()) return
            if (!PhosphorNative.deckValidate(path)) {
                reportLocal("Skipped ${request.titles[index]}: no decodable audio, choose another file", isLatest)
                if (request.queueUris[index] != null) {
                    java.io.File(path).delete()
                    request.queuePaths[index] = null
                }
                continue
            }
            if (destroying || stopping || !isLatest()) return
            val sameTarget = openedLocalPath == path && openedLocalQueuePaths === request.queuePaths &&
                openedLocalIndex == index
            if (sameTarget && request.positionMs != null) {
                retireNativeWake()
                if (!PhosphorNative.deckSeekMs(request.positionMs)) {
                    closeOpenedLocal()
                    reportLocal("Seek failed, retry this track", isLatest)
                    publishNativeFailure(isLatest)
                } else {
                    val publishOpen = playbackTruth.opened(
                        path, request.titles[index], isLatest, PhosphorNative.deckOpenIdentity(), newItem = false,
                    )
                    main.post {
                        if (!destroying && !stopping && isLatest()) {
                            if (!publishOpen()) return@post
                            PhosphorNative.deckPublish(!localPlayer.playWhenReady)
                            localPlayer.onNativeSeekCompleted()
                            sourceSurvival.published()
                            updatePlaybackWake()
                            localQueuePolicy.published()
                            startEndWatcher()
                        }
                    }
                }
                return
            }
            if (!releaseReaders(isLatest)) return
            if (destroying || stopping || !isLatest()) return
            openedLocalPath = null
            openedLocalQueuePaths = null
            openedLocalIndex = -1
            retireNativeWake()
            if (!PhosphorNative.deckOpen(path)) {
                closeOpenedLocal()
                reportLocal("Skipped ${request.titles[index]}: audio output could not open", isLatest)
                publishNativeFailure(isLatest)
                continue
            }
            openedLocalPath = path
            openedLocalQueuePaths = request.queuePaths
            openedLocalIndex = index
            if (request.positionMs != null && request.positionMs > 0 && index == request.index) {
                if (!PhosphorNative.deckSeekMs(request.positionMs)) {
                    closeOpenedLocal()
                    reportLocal("Seek failed, retry this track", isLatest)
                    publishNativeFailure(isLatest)
                    return
                }
            }
            val publishOpen = playbackTruth.opened(
                path, request.titles[index], isLatest, PhosphorNative.deckOpenIdentity(), newItem = true,
            )
            main.post {
                if (destroying || stopping || !isLatest()) return@post
                if (!publishOpen()) return@post
                queueUris = request.queueUris
                queuePaths = request.queuePaths
                queueTitles = request.titles
                localPlayer.setQueue(request.titles.map { PhosphorPlayer.QueueEntry("", it) }, index)
                switchTo(localPlayer)
                localPlayer.onTrackOpened(request.transportRevision)
                if (localPlayer.playWhenReady) requestFocus()
                PhosphorNative.deckPublish(!localPlayer.playWhenReady)
                sourceSurvival.published()
                updatePlaybackWake()
                localQueuePolicy.published()
                publishLocalSource(localSourcePublication.published(LocalSourcePublication.Source.LOCAL))
                startEndWatcher()
            }
            if (destroying || stopping || !isLatest()) return
            val next = if (index + 1 < request.queueUris.size) {
                runCatching { stagedPath(index + 1, request.queueUris, request.queuePaths) }.getOrNull()
            } else null
            request.queuePaths.indices.forEach { j ->
                if (j != index && request.queuePaths[j] != next) request.queuePaths[j] = null
            }
            pruneStaged(setOfNotNull(path, next))
            return
        }
        reportLocal("No playable entries remain, choose another file or folder", isLatest)
        if (sourceSurvival.loss().native || sourceSurvival.loss().readers.isNotEmpty()) {
            finishSelection(isLatest)
            return
        }
        val previousIndex = openedLocalIndex
        val previousQueue = openedLocalQueuePaths
        playbackTruth.retain(isLatest)
        main.post {
            if (destroying || stopping || !isLatest()) return@post
            if (previousQueue === request.queuePaths && previousIndex >= 0) {
                val playing = localPlayer.playWhenReady && !request.fromEof
                localPlayer.setQueue(request.titles.map { PhosphorPlayer.QueueEntry("", it) }, previousIndex)
                localPlayer.setPublishedPlaying(playing)
                localQueuePolicy.exhausted(request.fromEof)
            } else {
                localQueuePolicy.failed(preservesNative = previousQueue != null)
            }
            playbackTruth.publishCurrent(localPlayer::onTrackMetadata)
            startEndWatcher()
        }
    }

    // End-of-track watcher: drives auto-advance through the queue.
    private var watching = false
    private fun startEndWatcher() {
        if (watching) return
        watching = true
        main.post(object : Runnable {
            override fun run() {
                if (destroying || stopping || session?.player !== localPlayer || localPlayer.queueSize() == 0) {
                    watching = false
                    return
                }
                updatePlaybackWake()
                playbackTruth.poll(
                    schedule = { task -> localDeckExecutor.execute {
                        runCatching(task).onFailure { Log.w(TAG, "Local metadata unavailable", it) }
                    } },
                    onMain = { task -> main.post { if (!destroying && !stopping) task() } },
                    readEvent = PhosphorNative::deckPollEvent,
                    readMetadata = PhosphorNative::deckMetadata,
                    readArtwork = PhosphorNative::deckCoverArt,
                    publish = { if (session?.player === localPlayer) localPlayer.onTrackMetadata(it) },
                    newItem = { if (session?.player === localPlayer) PhosphorNative.confirmLocalItem(it) },
                    terminal = { result, isCurrent ->
                        if (isCurrent() && session?.player === localPlayer) {
                            val continueQueue = localQueuePolicy.mayAdvance() && localPlayer.playWhenReady &&
                                result != PlaybackTruth.Terminal.OUTPUT_FAILED
                            localPlayer.onNativeTerminal(result)
                            updatePlaybackWake()
                            if (result != PlaybackTruth.Terminal.ENDED) reportLocal(
                                "Local audio stopped, check the audio route or choose a readable file and retry", isCurrent,
                            )
                            if (continueQueue) {
                                advancingAtEnd = true
                                try { localPlayer.advanceIfPossible() } finally { advancingAtEnd = false }
                            }
                        }
                    },
                )
                val dur = localPlayer.currentDurationMs()
                val pos = PhosphorNative.deckPositionMs()
                if (localQueuePolicy.mayAdvance() && localPlayer.playWhenReady && dur > 0 && pos >= dur - 350) {
                    advancingAtEnd = true
                    try {
                        if (!localPlayer.advanceIfPossible()) {
                            localPlayer.playWhenReady = false // end of queue: rest
                        }
                    } finally {
                        advancingAtEnd = false
                    }
                }
                main.postDelayed(this, 400)
            }
        })
    }

    private fun startRemote(host: String, port: Int, label: String) {
        sourceWake.stop()
        switchTo(remotePlayer)
        remotePlayer.onConnecting(label)
        requestFocus()
        val endpoint = RemoteEndpoint(host, port, label)
        remoteEndpoint = endpoint
        PhosphorNative.remoteDisconnect()
        connectRemoteNow(endpoint)
    }

    private fun stopRemote() {
        sourceWake.stop()
        remotePolling = false
        remoteEndpoint = null
        PhosphorNative.remoteDisconnect()
        remotePlayer.reset()
        switchTo(localPlayer)
    }

    private fun prefs() = getSharedPreferences("phosphor.prefs", MODE_PRIVATE)

    private fun connectRemoteNow(endpoint: RemoteEndpoint) {
        // Latency is policy, not session state: apply before every fresh link.
        PhosphorNative.remoteSetLatencyMode(
            prefs().getInt("remote_latency_mode", 2).coerceIn(0, 2)
        )
        remoteGainApplied = false
        // v2 connect is non-blocking: rust owns timeout/watchdog/backoff; the
        // service owns route selection and the status pump.
        if (!PhosphorNative.remoteConnect(endpoint.host, endpoint.port, true, false)) {
            sourceWake.stop()
            remoteEndpoint = null
            remotePlayer.onConnectFailed("couldn't start the bridge link")
            return
        }
        startRemotePoll()
    }

    /**
     * Stop trying and tell the truth about why.
     *
     * The engine populates `last_error` with both an `error` and a `fix`, and the relay
     * guarantees a `fix` on every error frame. Both are carried through to the player so
     * the user reads the remedy rather than a bare failure.
     */
    private fun giveUpOnRemote(status: JSONObject) {
        sourceWake.stop()
        remotePolling = false
        PhosphorNative.remoteDisconnect()
        remoteEndpoint = null
        val failure = RemoteLinkTruth.failureText(status)
        remotePlayer.onConnectFailed(failure.ifBlank { "bridge unreachable" })
    }

    /**
     * Give-up policy: 60 s without a live link surfaces the failure.
     *
     * Returns true when the caller should stop pumping. The clock covers every
     * not-streaming state, including a relay that greets and then sends nothing.
     */
    private fun giveUpIfStuck(status: JSONObject): Boolean {
        if (System.currentTimeMillis() - failingSinceMs <= REMOTE_GIVE_UP_MS) return false
        giveUpOnRemote(status)
        return true
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
                if (stopping || destroying || !remotePolling || session?.player !== remotePlayer) {
                    remotePolling = false
                    return
                }
                val status = runCatching { JSONObject(PhosphorNative.remoteStatus()) }.getOrNull()
                val reading = status?.let { RemoteLinkTruth.read(it) }
                synchronized(sourceSurvival) {
                    sourceWake.remoteChanged(
                        owned = !stopping && !destroying && !sourceSurvival.loss().native && remoteEndpoint != null,
                        state = reading?.state,
                    )
                }
                if (status != null && reading != null) {
                    // The read is a tested pure function (RemoteLinkTruth) so the rules
                    // about what counts as a live link live in one place and are provable
                    // on the host, rather than being spread through this pump.
                    when (reading.state) {
                        RemoteLinkState.STREAMING, RemoteLinkState.SILENT -> {
                            failingSinceMs = 0L
                            // A silent link is healthy, so it never counts against the
                            // give-up clock. It just has nothing to draw.
                            if (reading.state == RemoteLinkState.SILENT) {
                                remotePlayer.onSilent()
                            } else {
                                remotePlayer.onConnected()
                            }
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
                        RemoteLinkState.GREETED -> {
                            // Greeted but nothing flowing yet. Still counts against the
                            // give-up clock: a relay that greets and never sends is broken.
                            if (failingSinceMs == 0L) failingSinceMs = System.currentTimeMillis()
                            if (giveUpIfStuck(status)) return
                            remotePlayer.onGreeted()
                        }
                        RemoteLinkState.STALLED -> {
                            // Frozen socket, not a dropped one. Acceptance H-04 forbids
                            // showing this as a live trace, and it is not a reconnect either.
                            if (failingSinceMs == 0L) failingSinceMs = System.currentTimeMillis()
                            if (giveUpIfStuck(status)) return
                            remotePlayer.onConnectionStalled()
                        }
                        RemoteLinkState.RECONNECTING, RemoteLinkState.CONNECTING -> {
                            if (failingSinceMs == 0L) failingSinceMs = System.currentTimeMillis()
                            if (giveUpIfStuck(status)) return
                            remotePlayer.onConnectionLost()
                        }
                        RemoteLinkState.FAILED -> {
                            giveUpOnRemote(status)
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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        session.takeUnless { stopping || destroying }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // MediaSessionService's default can pause external capture or stop paused linger.
        BackgroundLifecycle.removeTask(this, taskRevision)
    }

    private fun taskRemoved(keep: Boolean) {
        if (stopping || destroying) return
        if (keep) {
            localSourcePublication.selected()
            localDeckRequests.enqueue(LocalDeckRequest.Retain)
        } else {
            beginShutdown(stopReaders = true)
        }
    }

    private fun beginShutdown(stopReaders: Boolean) {
        if (stopping) return
        stopping = true
        sourceWake.destroy()
        val completion = CompletableFuture<String?>()
        retirement.add(this, completion)
        focusResume.cancel()
        if (stopReaders || remoteEndpoint != null ||
            (session?.player === localPlayer && localPlayer.queueSize() > 0)) {
            localSourcePublication.published(LocalSourcePublication.Source.NONE)
        } else {
            localSourcePublication.selected()
        }
        val readers = if (stopReaders) {
            val mic = CompletableFuture<String?>()
            MicController.stopForLocal(stopSequence.incrementAndGet()) { _, error, _ -> mic.complete(error) }
            listOf(mic, CaptureService.stopExisting())
        } else emptyList()
        val disconnect = remoteEndpoint != null
        remotePolling = false
        remoteEndpoint = null
        localPlayer.recordTransportIntent(false)
        // One terminal request supersedes pending opens. Destruction must not replace it.
        localDeckRequests.enqueue(LocalDeckRequest.Shutdown(readers, disconnect, completion))
        localDeckExecutor.shutdown()
        leaveCaptureMirror()
        localPlayer.setPublishedPlaying(false)
        releaseFaces()
    }

    private fun releaseFaces() {
        if (facesReleased) return
        facesReleased = true
        localPlayer.onSwitchTrack = null
        localPlayer.onSeek = null
        localPlayer.onStopRequested = null
        remotePlayer.onTransportIntent = null
        session?.release()
        session = null
        localPlayer.release()
        remotePlayer.release()
        capturePlayer.release()
    }

    override fun onDestroy() {
        beginShutdown(stopReaders = false)
        destroying = true
        sourceWake.destroy()
        if (owner === this) owner = null
        unregisterReceiver(noisyReceiver)
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        super.onDestroy()
    }

    companion object {
        private var owner: PlaybackService? = null
        internal fun hasLiveWakeSource(): Boolean = owner?.sourceWake?.live == true
        private val retirement = SourceRetirement()
        internal fun ownsLocal(): Boolean = owner?.let {
            !it.stopping && !it.destroying && !it.sourceSurvival.loss().native &&
                it.session?.player === it.localPlayer && it.localPlayer.queueSize() > 0
        } == true
        internal fun ownsRelay(): Boolean = owner?.let {
            !it.stopping && !it.sourceSurvival.loss().native && it.remoteEndpoint != null
        } == true
        internal fun removeTask(keep: Boolean) { owner?.taskRemoved(keep) }
        internal fun captureStopped(captureOwnerId: Long) {
            owner?.takeIf { !it.stopping && !it.destroying && it.captureOwner.accepts(captureOwnerId) }?.endCaptureMirror()
        }
        private val SOURCE_ACTIONS = setOf(
            ACTION_CAPTURE_STARTED, ACTION_RELEASE_LOCAL, ACTION_REMOTE_CONNECT,
            ACTION_OPEN_TREE, ACTION_OPEN_DOCUMENT,
        )
        internal val localSourcePublication = LocalSourcePublication()
        const val ACTION_LOCAL_SOURCE_CHANGED = "dev.phosphor.mobil3.LOCAL_SOURCE_CHANGED"
        const val EXTRA_SOURCE_REVISION = "source_revision"
        private const val TAG = "PhosphorPlayback"
        /**
         * Surface a link that remains non-streaming for 60 seconds.
         *
         * The engine's backoff ladder reaches 15 seconds, so this ceiling allows several
         * complete retry cycles before the session becomes a visible failure.
         */
        private const val REMOTE_GIVE_UP_MS = 60_000L
        const val EXTRA_OPEN = "open"
        const val ACTION_OPEN_TREE = "dev.phosphor.mobil3.OPEN_TREE"
        const val EXTRA_TREE_URI = "tree_uri"
        const val ACTION_OPEN_DOCUMENT = "dev.phosphor.mobil3.OPEN_DOCUMENT"
        const val EXTRA_DOCUMENT_URI = "document_uri"
        const val EXTRA_QUEUE_START = "queue_start"
        const val ACTION_RELEASE_LOCAL = "dev.phosphor.mobil3.RELEASE_LOCAL"
        const val EXTRA_RELEASE_REPLY = "release_reply"
        const val ACTION_REMOTE_CONNECT = "dev.phosphor.mobil3.REMOTE_CONNECT"
        const val ACTION_REMOTE_DISCONNECT = "dev.phosphor.mobil3.REMOTE_DISCONNECT"
        const val ACTION_CAPTURE_STARTED = "dev.phosphor.mobil3.CAPTURE_STARTED"
        const val ACTION_CAPTURE_STOPPED = "dev.phosphor.mobil3.CAPTURE_STOPPED"
        const val ACTION_CAPTURE_ACCESS_CHANGED =
            "dev.phosphor.mobil3.CAPTURE_ACCESS_CHANGED"
        const val EXTRA_CAPTURE_ACCESS_GRANTED = "capture_access_granted"
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
        const val EXTRA_LABEL = "label"
        private const val MAX_ART_EDGE = 1024
        internal val ACTIVE_PLATFORM_STATES = setOf(
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
@androidx.annotation.OptIn(UnstableApi::class)
internal class CaptureMirrorPlayer(looper: android.os.Looper) : SimpleBasePlayer(looper) {
    override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()

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
                if (active && CaptureMirrorPolicy.canPlayPause(platformState, actions)) add(Player.COMMAND_PLAY_PAUSE)
                if (supports(PlatformPlaybackState.ACTION_SKIP_TO_NEXT)) {
                    addAll(Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                }
                if (supports(PlatformPlaybackState.ACTION_SKIP_TO_PREVIOUS)) {
                    addAll(
                        Player.COMMAND_SEEK_TO_PREVIOUS,
                        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    )
                }
                if (seekable()) {
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
            PlatformPlaybackState.STATE_NONE -> Player.STATE_IDLE
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
                    if (platformState == PlatformPlaybackState.STATE_PLAYING) {
                        val position = positionMs + (SystemClock.elapsedRealtime() - positionUpdateElapsedMs).coerceAtLeast(0)
                        if (durationMs > 0) position.coerceAtMost(durationMs) else position
                    } else positionMs
                }
            }
        }
        return builder.build()
    }

    private fun supports(vararg candidates: Long): Boolean =
        active && candidates.any { CaptureMirrorPolicy.supports(platformState, actions, it) }

    private fun seekable(): Boolean = active && CaptureMirrorPolicy.seekable(platformState, actions, durationMs)

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
            .setIsSeekable(seekable())
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
        this.title = title?.ifBlank { null }
        this.artist = artist?.ifBlank { null }
        this.album = album?.ifBlank { null }
        this.sourcePackage = sourcePackage
        this.durationMs = durationMs.takeIf { it in 1..(Long.MAX_VALUE / 1000) } ?: C.TIME_UNSET
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
        this.actions = if (CaptureMirrorPolicy.available(state)) actions else 0L
        playing = CaptureMirrorPolicy.playing(state)
        this.positionMs = positionMs
        this.positionUpdateElapsedMs = positionUpdateElapsedMs
        invalidateState()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (active) {
            CaptureMirrorPolicy.routePlayPause(platformState, actions, playWhenReady) {
                playPauseRouter?.invoke(it)
            }
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int,
    ): ListenableFuture<*> {
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ->
                if (supports(PlatformPlaybackState.ACTION_SKIP_TO_NEXT)) nextRouter?.invoke()
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM ->
                if (supports(PlatformPlaybackState.ACTION_SKIP_TO_PREVIOUS)) previousRouter?.invoke()
            else -> if (seekable()) {
                seekRouter?.invoke(positionMs.coerceIn(0L, durationMs))
            }
        }
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        if (playing) playPauseRouter?.invoke(false)
        return Futures.immediateVoidFuture()
    }
}
