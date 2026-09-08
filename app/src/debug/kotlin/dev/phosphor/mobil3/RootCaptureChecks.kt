package dev.phosphor.mobil3

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AtomicFile
import java.io.File
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/** Three fixed DUMP-protected actions. No extras, other-app targets, or source takeover. */
internal object RootCaptureChecks {
    external fun nativeSnapshot(): String
    private fun <T> main(action: () -> T): T {
        val result = CompletableFuture<T>()
        val gate = RootStartGate()
        val handler = Handler(Looper.getMainLooper())
        val work = Runnable {
            if (gate.accepts()) {
                try { result.complete(action()) } catch (error: Exception) { result.completeExceptionally(error) }
            }
        }
        handler.post(work)
        try { return result.get(3, TimeUnit.SECONDS) }
        finally { gate.cancel(); handler.removeCallbacks(work) }
    }
    private class Fixture(private val manager: AudioManager, policy: Int) {
        private val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).setAllowedCapturePolicy(policy).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(16000)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(32000).build()
        @Volatile var started = 0L
            private set
        @Volatile var head = 0
            private set
        init {
            try {
                check(track.state == AudioTrack.STATE_NO_STATIC_DATA)
                val pcm = ShortArray(16000) { (sin(2 * PI * 997 * it / 16000) * 32767 * 0.025).toInt().toShort() }
                check(track.write(pcm, 0, pcm.size) == pcm.size)
                check(track.setLoopPoints(0, pcm.size, 4) == AudioTrack.SUCCESS)
            } catch (error: Exception) { track.release(); throw error }
        }
        private var closed = false
        @Synchronized fun start() {
            check(!closed)
            check(manager.mode == AudioManager.MODE_NORMAL) { "Communication mode active. Retry after the call ends" }
            track.play()
            check(track.playState == AudioTrack.PLAYSTATE_PLAYING) { "Fixture playback did not start" }
            started = SystemClock.elapsedRealtime()
        }
        @Synchronized fun observe() {
            if (closed) return
            check(manager.mode == AudioManager.MODE_NORMAL) { "Communication mode changed. Fixture stopped" }
            head = maxOf(head, track.playbackHeadPosition)
        }
        @Synchronized fun close(): Boolean {
            if (closed) return true
            var clean = true
            runCatching { observe(); track.stop() }.onFailure { clean = false }
            runCatching { track.release() }.onFailure { clean = false }
            closed = true
            return clean
        }
    }
    private class Signal {
        var frames = 0L
        var normalized = 0L
        var nonzero = 0L
        private var squares = 0.0
        private var previous = 0.0
        private var crossings = 0
        private var first = -1L
        private var last = -1L
        fun add(pcm: ShortArray, output: Int) {
            check(output == pcm.size * 3)
            for (sample in pcm) {
                val v = sample / 32768.0
                if (sample.toInt() != 0) nonzero++
                squares += v * v
                if (previous < 0 && v >= 0) { if (first < 0) first = frames; last = frames; crossings++ }
                previous = v
                frames++
            }
            normalized += output
        }
        fun json() = JSONObject().put("frames", frames).put("normalized_frames", normalized).put("nonzero", nonzero)
            .put("rms", if (frames == 0L) 0.0 else sqrt(squares / frames))
            .put("frequency_hz", if (last > first && crossings > 1) (crossings - 1) * 16000.0 / (last - first) else 0.0)
    }
    fun run(context: Context, allowSystem: Boolean) {
        val prefs = context.getSharedPreferences(PhosphorApplication.RUNTIME_PREFERENCES_NAME, Context.MODE_PRIVATE)
        val before = prefs.all.toMap()
        val data = identity(context).put("capture_policy", if (allowSystem) "ALLOW_CAPTURE_BY_SYSTEM" else "ALLOW_CAPTURE_BY_NONE")
        val manager = context.getSystemService(AudioManager::class.java)
        var fixture: Fixture? = null
        var reserved: RootCaptureCheck? = null
        var failure: String? = null
        var cleanup = true
        val gate = RootStartGate()
        val done = CompletableFuture<String?>()
        val signal = Signal()
        var generation: Long? = null
        var snapshot: JSONObject? = null
        try {
            check(manager.mode == AudioManager.MODE_NORMAL && !manager.isMusicActive) { "A call or player is active. Leave it untouched and retry when idle" }
            main {
                check(CaptureService.quiescent() && MicController.quiescent() && !PlaybackService.ownsLocal() && !PlaybackService.ownsRelay() && RootHelperLease.available()) { "An existing source/helper or cleanup owns the input. Stop it explicitly before testing" }
            }
            val tone = Fixture(manager, if (allowSystem) AudioAttributes.ALLOW_CAPTURE_BY_SYSTEM else AudioAttributes.ALLOW_CAPTURE_BY_NONE)
            fixture = tone
            val observer = object : RootCaptureCheck {
                override fun accepts() = gate.accepts()
                override fun ready(id: Long) { check(gate.accepts()); generation = id; tone.start() }
                override fun samples(id: Long, pcm: ShortArray, normalizedFrames: Int) {
                    check(generation == id) { "Stale fixture generation" }
                    if (!gate.accepts()) return
                    tone.observe()
                    signal.add(pcm, normalizedFrames)
                    if (snapshot == null && signal.frames >= 24000 && SystemClock.elapsedRealtime() - tone.started >= 1500) {
                        snapshot = JSONObject(nativeSnapshot())
                        check(snapshot!!.getInt("sample_rate") == 48000 && snapshot!!.getInt("frames") == 24000 && snapshot!!.getBoolean("duplicated_mono")) { "Native ring actual format/length mismatch" }
                    }
                }
                override fun finished(id: Long?, result: RootCaptureSession.Result?, error: String?) {
                    data.put("helper_generation", id).put("read_progress", result?.progress ?: 0)
                    done.complete(error ?: result?.error ?: if (result?.cleanup == true) null else "Root cleanup not proven")
                }
            }
            reserved = observer // Identity-only cleanup is safe even if admission never occurs.
            main {
                check(CaptureService.quiescent() && MicController.quiescent() && !PlaybackService.ownsLocal() && !PlaybackService.ownsRelay())
                RootCaptureService.reserveCheck(observer)
                check(gate.accepts())
                context.startForegroundService(BackgroundLifecycle.stamp(Intent(context, RootCaptureService::class.java), BackgroundLifecycle.policy.revision))
            }
            val deadline = SystemClock.elapsedRealtime() + 18000
            while (!done.isDone && SystemClock.elapsedRealtime() < deadline) { if (tone.started > 0) tone.observe(); SystemClock.sleep(20) }
            check(done.isDone) { "Product fixture deadline exceeded" }
            done.get()?.let { error(it) }
            check(tone.head >= 64000) { "Fixture AudioTrack did not advance for four seconds" }
            val pcm = signal.json()
            if (allowSystem) {
                check(signal.frames in 64000..80000 && pcm.getDouble("rms") > 0.00001 && pcm.getDouble("frequency_hz") in 975.0..1019.0) { "BY_SYSTEM expected 997 Hz PCM not proven: $pcm" }
                check(snapshot?.optDouble("rms", 0.0)?.let { it > 0.00001 } == true && snapshot!!.getDouble("frequency_hz") in 975.0..1019.0) { "Post-JNI ring tone not proven" }
            } else {
                check(data.getLong("read_progress") >= 4) { "Exclusion needs a progressing helper, not a dead reader" }
                check(signal.nonzero == 0L) { "BY_NONE unexpectedly produced nonzero PCM" }
                if (signal.frames > 0) check(snapshot?.getDouble("rms") == 0.0) { "Post-JNI silence not proven" }
            }
        } catch (error: Exception) { failure = error.message ?: error.toString() }
        finally {
            gate.cancel()
            cleanup = fixture?.close() ?: true
            data.put("playback_head_frames", fixture?.head ?: 0).put("fixture_started", fixture?.started?.let { it > 0 } ?: false)
            reserved?.let { observer ->
                runCatching {
                    val retired = main { RootCaptureService.cancelCheck(observer); CaptureService.stopCheck(observer) }
                    check(retired.get(12, TimeUnit.SECONDS) == null) { "Product service cleanup unconfirmed" }
                    check(main { CaptureService.quiescent() } && RootHelperLease.available())
                }.onFailure { cleanup = false; failure = failure ?: it.message }
            }
            data.put("pcm", signal.json()).put("native_ring", snapshot ?: JSONObject.NULL)
            if (before != prefs.all) { failure = failure ?: "Fixture changed local runtime preferences"; data.put("preferences_unchanged", false) }
            else data.put("preferences_unchanged", true)
            write(context, if (allowSystem) "root-capture-system.json" else "root-capture-none.json", data, failure, cleanup)
        }
    }
    fun tone(context: Context) {
        val data = identity(context)
        val prefs = context.getSharedPreferences(PhosphorApplication.RUNTIME_PREFERENCES_NAME, Context.MODE_PRIVATE)
        val before = prefs.all.toMap()
        var fixture: Fixture? = null
        var failure: String? = null
        var cleanup = true
        try {
            val owner = main { check(CaptureService.rootOwned() && CaptureService.ownsCapture()); CaptureService.rootObservation()!! }
            val manager = context.getSystemService(AudioManager::class.java)
            check(manager.mode == AudioManager.MODE_NORMAL && !manager.isMusicActive) { "A call or player is active. Retry when idle" }
            val tone = Fixture(manager, AudioAttributes.ALLOW_CAPTURE_BY_SYSTEM)
            fixture = tone
            tone.start()
            while (SystemClock.elapsedRealtime() - tone.started < 5000) {
                tone.observe()
                val current = main { CaptureService.rootObservation() }
                check(current?.first == owner.first) { "The UI-owned root session changed. No replacement will be started" }
                if (!data.has("native_ring") && current!!.second - owner.second >= 24000 && SystemClock.elapsedRealtime() - tone.started >= 1500) {
                    data.put("native_ring", JSONObject(nativeSnapshot()))
                }
                SystemClock.sleep(20)
            }
            check(tone.head >= 64000) { "Fixture playback advancement not proven" }
            val ring = data.getJSONObject("native_ring")
            check(ring.getInt("frames") == 24000 && ring.getInt("sample_rate") == 48000 && ring.getBoolean("duplicated_mono") && ring.getDouble("rms") > 0.00001 && ring.getDouble("frequency_hz") in 975.0..1019.0) { "UI-owned root downstream tone not proven" }
            data.put("helper_generation", owner.first)
        } catch (error: Exception) { failure = error.message ?: error.toString() }
        finally {
            cleanup = fixture?.close() ?: true
            data.put("playback_head_frames", fixture?.head ?: 0).put("preferences_unchanged", before == prefs.all)
            if (before != prefs.all) failure = failure ?: "Tone action changed runtime preferences"
            write(context, "root-capture-tone.json", data, failure, cleanup)
        }
    }
    private fun identity(context: Context) = JSONObject().put("package", context.packageName).put("build_commit", BuildConfig.BUILD_COMMIT)
        .put("helper_build", BuildConfig.ROOT_AUDIO_BUILD).put("app_uid", android.os.Process.myUid()).put("audibility_proven", false)
    private fun write(context: Context, name: String, data: JSONObject, error: String?, cleanup: Boolean) {
        val report = JSONObject().put("status", if (error == null && cleanup) "ok" else "error").put("tool", "pm3-root-product-check")
            .put("version", "1.0.0").put("ts", Instant.now().toString()).put("data", data.put("cleanup_confirmed", cleanup))
        if (error != null || !cleanup) report.put("error", error ?: "Fixture cleanup failed").put("fix", "Inspect the fixed fixture, owner and cleanup receipt. Keep system policy unchanged")
        val file = AtomicFile(File(context.filesDir, name))
        val out = file.startWrite()
        try { out.write(report.toString().toByteArray()); file.finishWrite(out) }
        catch (error: Exception) { file.failWrite(out); throw error }
    }
}
