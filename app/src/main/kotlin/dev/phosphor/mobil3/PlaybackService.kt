package dev.phosphor.mobil3

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

// The deck's Android citizenship: MediaSessionService gives lock-screen controls, the
// media notification, media buttons, and Bluetooth clients. SimpleBasePlayer does NOT
// handle audio focus or becoming-noisy — those are hand-rolled here (plan D4).
class PlaybackService : MediaSessionService() {

    private lateinit var player: PhosphorPlayer
    private var session: MediaSession? = null
    private lateinit var audioManager: AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var resumeOnFocusGain = false

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                player.playWhenReady = false // headphones yanked -> pause, never blast
            }
        }
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnFocusGain = false
                player.playWhenReady = false
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                resumeOnFocusGain = player.playWhenReady
                player.playWhenReady = false
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    player.playWhenReady = true
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        player = PhosphorPlayer(mainLooper)
        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (playWhenReady) requestFocus()
            }
        })
        session = MediaSession.Builder(this, player).build()
        // No controller connects yet (the UI talks JNI until M5), so the session must be
        // added explicitly — onGetSession never fires, and without an added session the
        // service's notification machinery never engages.
        addSession(session!!)
        registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra(EXTRA_OPEN)?.let { path ->
            val main = Handler(mainLooper)
            Thread {
                if (PhosphorNative.deckOpen(path)) {
                    requestFocus()
                    main.post { player.onTrackOpened() }
                }
            }.start()
        }
        return super.onStartCommand(intent, flags, startId)
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
        audioManager.requestAudioFocus(req)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        android.util.Log.i(
            "phosphor-mobil3",
            "onUpdateNotification fg=$startInForegroundRequired playing=${player.playWhenReady} state=${player.playbackState}"
        )
        super.onUpdateNotification(session, startInForegroundRequired)
    }

    override fun onDestroy() {
        unregisterReceiver(noisyReceiver)
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        session?.run {
            player.release()
            release()
        }
        session = null
        PhosphorNative.deckClose()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_OPEN = "open"
    }
}
