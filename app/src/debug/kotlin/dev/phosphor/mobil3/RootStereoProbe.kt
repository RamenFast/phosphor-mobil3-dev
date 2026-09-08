package dev.phosphor.mobil3

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.Process
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AtomicFile
import java.io.File
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.CompletableFuture
import org.json.JSONObject
import kotlin.math.min
import kotlin.math.sin

/** DUMP-only finite aggregate experiment. The product transport is deliberately not involved. */
internal object RootStereoProbe {
    private const val FRAMES = 240000
    private fun requireIdleSources() {
        val done = CompletableFuture<Boolean>()
        val gate = RootStartGate()
        val handler = Handler(Looper.getMainLooper())
        val work = Runnable {
            if (gate.accepts()) runCatching {
                CaptureService.quiescent() && MicController.quiescent() && !PlaybackService.ownsLocal() && !PlaybackService.ownsRelay()
            }.fold(done::complete, done::completeExceptionally)
        }
        handler.post(work)
        try { check(done.get(1, TimeUnit.SECONDS)) { "Another source or unresolved retirement owns audio. Leave it untouched" } }
        finally { gate.cancel(); handler.removeCallbacks(work) }
    }
    internal fun mode(allowSystem: Boolean) = if (allowSystem) 4 else 5
    internal fun sourceShouldStop(head: Long, elapsedMs: Long) = head >= FRAMES || elapsedMs >= 5500
    internal fun validatedFinal(payload: ByteArray, generation: Long): JSONObject {
        val result = JSONObject(payload.toString(Charsets.UTF_8))
        check(result.getLong("generation") == generation) { "Stereo FINAL generation mismatch" }
        return result
    }
    internal fun sample(frame: Int, channel: Int): Short {
        require(frame in 0 until FRAMES && channel in 0..1)
        val ramp = min(1.0, min(frame / 480.0, (FRAMES - 1 - frame) / 480.0))
        return (sin(2 * Math.PI * (if (channel == 0) 997 else 1499) * frame / 48000) * 32767 * 0.025 * ramp).toInt().toShort()
    }
    internal fun validateReady(e: JSONObject, uid: Int) {
        check(e.getString("stage") == "ready" && e.getInt("sample_rate") == 48000 &&
            e.getInt("channels") == 2 && e.getInt("encoding") == 2 && e.getInt("route_flags") == 2 &&
            !e.getBoolean("privileged_capture") && !e.getBoolean("physical_audibility_proven")) { "Stereo READY format or scope invalid" }
        val who = e.getJSONObject("monitor_identity")
        check(who.getInt("uid") >= 0 && who.getInt("uid") != uid && who.getInt("pid") == e.getInt("pid") &&
            who.getInt("session") > 0 && who.getInt("player") >= 0) { "Actual monitor identity invalid" }
        val route = e.getJSONObject("monitor_route")
        check(route.getInt("id") > 0 && route.getInt("type") !in listOf(0, 25)) { "Physical monitor route invalid" }
        check(e.getInt("monitor_rate") == 48000 && e.getInt("monitor_channels") == 2 &&
            e.getInt("monitor_encoding") == 2 && e.getDouble("monitor_gain") == 1.0) { "Monitor format or gain invalid" }
    }
    internal fun signalValid(e: JSONObject): Boolean = e.optBoolean("stereo_proven") &&
        e.optInt("measured_frames") == 144000 && e.optDouble("rms_l", 0.0) > 0.001 && e.optDouble("rms_r", 0.0) > 0.001 &&
        e.optDouble("peak", 1.0) < 32767 / 32768.0 && kotlin.math.abs(e.optDouble("correlation", 1.0)) < 0.1 &&
        e.optDouble("separation_997_db", 0.0) >= 30 && e.optDouble("separation_1499_db", 0.0) >= 30 &&
        e.optLong("frames") == e.optLong("written_frames", -1) && e.optInt("pending_samples", -1) == 0 &&
        e.optLong("monitor_head") > 0 && e.optLong("frames") in 240000..264000 &&
        e.optLong("queue_highwater_frames", -1) in 0..4800 &&
        e.optDouble("record_timestamp_rate", 0.0) in 47520.0..48480.0 &&
        e.optDouble("monitor_timestamp_rate", 0.0) in 47520.0..48480.0 &&
        e.optInt("sample_rate") == 48000 && e.optInt("channels") == 2 && e.optInt("encoding") == 2

    fun run(context: Context, allowSystem: Boolean) {
        val mode = mode(allowSystem)
        val generation = SystemClock.elapsedRealtimeNanos().coerceAtLeast(1)
        val began = SystemClock.elapsedRealtime()
        val data = JSONObject().put("package", context.packageName).put("helper_build", BuildConfig.ROOT_AUDIO_BUILD)
            .put("version_name", BuildConfig.VERSION_NAME).put("version_code", BuildConfig.VERSION_CODE).put("build_commit", BuildConfig.BUILD_COMMIT)
            .put("generation", generation).put("mode", mode).put("app_uid", Process.myUid()).put("app_pid", Process.myPid())
            .put("physical_audibility_proven", false).put("hardware_acceptance", false).put("source_requested_frames", FRAMES)
        var child: java.lang.Process? = null
        var tone: AudioTrack? = null
        var lease = false
        var ready = false
        var acceptingReady = true
        var fixtureClean = true
        var sourceStopped = false
        var toneStart = 0L
        var sourceHead = 0L
        var result: JSONObject? = null
        var final: JSONObject? = null
        var exit: Int? = null
        var error: String? = null
        var protocolClean = true
        val decoder = RootAudioProtocol.Decoder()
        val progress = RootAudioProtocol.Stream(Process.myUid(), BuildConfig.ROOT_AUDIO_BUILD, generation, mode)
        val stderr = StringBuilder()
        val manager = context.getSystemService(AudioManager::class.java)
        val processPolicy = manager.allowedCapturePolicy
        var controlFrames = 0
        fun control(kind: Int) {
            check(++controlFrames <= 100) { "Finite control budget exceeded" }
            child?.outputStream?.apply { write(RootAudioProtocol.control(kind, generation)); flush() }
        }
        fun sourceObservation(track: AudioTrack) {
            sourceHead = Integer.toUnsignedLong(track.playbackHeadPosition)
            val ts = AudioTimestamp()
            val valid = track.getTimestamp(ts)
            data.put("source_head", sourceHead).put("source_timestamp_valid", valid)
                .put("source_timestamp_frame", if (valid) ts.framePosition else -1)
                .put("source_timestamp_ns", if (valid) ts.nanoTime else -1)
        }
        fun consumeUnchecked() {
            val p = child ?: return
            var count = 0
            while (p.inputStream.available() > 0 && count++ < 65536) {
                val value = p.inputStream.read()
                check(value >= 0) { "Unexpected stereo pipe EOF" }
                val frame = decoder.byte(value) ?: continue
                when (frame.first) {
                    11 -> {
                        check(acceptingReady && !ready && result == null && final == null) { "Late or duplicate stereo READY" }
                        val e = RootAudioProtocol.helper(frame.second, Process.myUid(), BuildConfig.ROOT_AUDIO_BUILD, generation, mode)
                        validateReady(e, Process.myUid())
                        requireIdleSources()
                        check(manager.mode == AudioManager.MODE_NORMAL && manager.allowedCapturePolicy == processPolicy) { "Audio mode or process policy changed" }
                        data.put("ready", e)
                        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setAllowedCapturePolicy(if (allowSystem) AudioAttributes.ALLOW_CAPTURE_BY_SYSTEM else AudioAttributes.ALLOW_CAPTURE_BY_NONE).build()
                        val track = AudioTrack.Builder().setAudioAttributes(attributes).setAudioFormat(AudioFormat.Builder().setSampleRate(48000)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                            .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(FRAMES * 4).build()
                        tone = track // Publish ownership before the first operation that can fail.
                        check(track.state == AudioTrack.STATE_NO_STATIC_DATA && track.sampleRate == 48000 && track.channelCount == 2 && track.audioFormat == 2)
                        val samples = ShortArray(FRAMES * 2) { sample(it / 2, it % 2) }
                        val written = track.write(samples, 0, samples.size, AudioTrack.WRITE_NON_BLOCKING)
                        data.put("source_written_frames", written / 2).put("source_session", track.audioSessionId)
                            .put("source_policy", attributes.allowedCapturePolicy).put("source_process_policy", processPolicy)
                            .put("source_actual_buffer_frames", track.bufferSizeInFrames)
                        check(written == samples.size && track.setVolume(1f) == AudioTrack.SUCCESS) { "Fixture write or gain failed" }
                        ready = true
                        control(2)
                        toneStart = SystemClock.elapsedRealtime()
                        track.play()
                    }
                    12 -> {
                        check(result == null && final == null) { "Late or duplicate stereo RESULT" }
                        result = RootAudioProtocol.helper(frame.second, Process.myUid(), BuildConfig.ROOT_AUDIO_BUILD, generation, mode)
                        data.put("helper", result)
                    }
                    13 -> {
                        check(!data.has("native") && final == null) { "Duplicate native evidence" }
                        val e = JSONObject(frame.second.toString(Charsets.UTF_8))
                        check(e.getLong("generation") == generation)
                        data.put("native", e)
                    }
                    14 -> {
                        check(final == null) { "Duplicate stereo FINAL" }
                        final = validatedFinal(frame.second, generation)
                        data.put("supervisor", final)
                    }
                    16 -> { check(ready && result == null && final == null); progress.progress(frame.second) }
                    else -> throw IllegalStateException("Unexpected aggregate stereo frame ${frame.first}")
                }
            }
            count = 0
            while (p.errorStream.available() > 0 && count++ < 8192) {
                val value = p.errorStream.read()
                if (value < 0) break
                if (stderr.length < 2048) stderr.append(value.toChar())
            }
        }
        fun consume() {
            try { consumeUnchecked() }
            catch (t: Throwable) { protocolClean = false; throw t }
        }
        try {
            check(BuildConfig.DEBUG && manager.mode == AudioManager.MODE_NORMAL && !manager.isMusicActive) { "Debug idle normal-mode probe required. Leave existing playback untouched" }
            requireIdleSources()
            RootHelperLease.acquire(); lease = true
            RootHelperCode.stage(context)
            val executable = File(context.applicationInfo.nativeLibraryDir, "libphosphor_root_launcher.so")
            check(executable.isFile && executable.canExecute()) { "Installed fixed launcher unavailable" }
            child = ProcessBuilder(executable.absolutePath).apply { environment().clear() }.start()
            child!!.outputStream.apply { write(RootAudioProtocol.select(generation, mode)); flush() }
            var heartbeat = SystemClock.elapsedRealtime() + 250
            while (SystemClock.elapsedRealtime() - began < 17000) {
                consume()
                val now = SystemClock.elapsedRealtime()
                if (ready && !sourceStopped) {
                    sourceObservation(tone!!)
                    if (sourceShouldStop(sourceHead, now - toneStart)) { tone!!.stop(); sourceStopped = true }
                }
                check(manager.mode == AudioManager.MODE_NORMAL && manager.allowedCapturePolicy == processPolicy) { "Audio mode or process policy changed" }
                if (child!!.waitFor(1, TimeUnit.MILLISECONDS)) { consume(); decoder.eof(); exit = child!!.exitValue(); break }
                if (now >= heartbeat && final == null) { requireIdleSources(); control(1); heartbeat = now + 250 }
                SystemClock.sleep(2)
            }
            check(exit != null) { "Stereo supervisor finite deadline exceeded" }
        } catch (t: Throwable) {
            error = t.toString().take(1000)
            runCatching { control(3) }
        } finally {
            acceptingReady = false
            if (tone != null) {
                if (!sourceStopped) runCatching { sourceObservation(tone!!); tone!!.stop() }.onFailure { fixtureClean = false }
                runCatching { tone!!.release() }.onFailure { fixtureClean = false }
            }
            runCatching { child?.outputStream?.close() }
            val deadline = SystemClock.elapsedRealtime() + 7500
            while (child != null && exit == null && SystemClock.elapsedRealtime() < deadline) {
                runCatching { consume() }.onFailure { protocolClean = false; if (error == null) error = it.toString().take(1000) }
                if (child!!.waitFor(10, TimeUnit.MILLISECONDS)) {
                    runCatching { consume(); decoder.eof() }.onFailure { protocolClean = false }
                    exit = child!!.exitValue()
                }
            }
            runCatching { child?.inputStream?.close() }
            runCatching { child?.errorStream?.close() }
        }
        val cleanup = fixtureClean && protocolClean && (child == null || (exit != null && final?.optBoolean("cleanup_confirmed") == true &&
            !final!!.optBoolean("killed", true) && (result?.optBoolean("cleanup_confirmed") == true || !final!!.optBoolean("child_started", true))))
        if (lease) RootHelperLease.release(cleanup)
        val success = error == null && ready && cleanup && result?.optString("status") == "ok" && signalValid(result!!) &&
            final?.optString("status") == "ok" && final!!.optInt("child_wait_status", -1) == 0 && exit == 0 && sourceHead >= FRAMES &&
            data.optInt("source_written_frames") == FRAMES
        data.put("cleanup_confirmed", cleanup).put("source_process_policy_preserved", manager.allowedCapturePolicy == processPolicy)
            .put("read_progress", progress.progressSequence).put("control_frames", controlFrames)
            .put("elapsed_ms", SystemClock.elapsedRealtime() - began).put("supervisor_exit", exit ?: JSONObject.NULL).put("stderr", stderr.toString())
        val report = JSONObject().put("status", if (success) "ok" else "error").put("tool", "pm3-root-stereo-probe")
            .put("version", "1.0.0").put("ts", Instant.now().toString()).put("data", data)
        if (!success) report.put("error", error ?: result?.optString("error")?.takeIf { it.isNotBlank() } ?: "Stereo, source progression or cleanup not proven")
            .put("fix", "Inspect identity, route, stereo and cleanup evidence. Do not retry uncertain cleanup or change system policy.")
        val file = AtomicFile(File(context.filesDir, if (allowSystem) "root-stereo-system.json" else "root-stereo-none.json"))
        val output = file.startWrite()
        try { output.write(report.toString().toByteArray()); file.finishWrite(output) }
        catch (t: Throwable) { file.failWrite(output); throw t }
    }
}
