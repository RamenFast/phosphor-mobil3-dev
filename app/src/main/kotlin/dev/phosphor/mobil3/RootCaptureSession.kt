package dev.phosphor.mobil3

import android.content.Context
import android.os.Process
import android.os.SystemClock
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONObject

/** One owner thread, fixed pipes, no privileged Process.destroy or asynchronous PCM queue. */
internal class RootCaptureSession(private val context: Context, val mode: Int = 2) {
    val generation = generations.updateAndGet {
        check(it < Long.MAX_VALUE) { "Root generation exhausted" }
        it + 1
    }
    val completion = CompletableFuture<Result>()
    @Volatile private var stopRequested = false
    @Volatile var live = false
        private set
    @Volatile var frames = 0L
        private set
    @Volatile var lastPcmAt = 0L
        private set
    private val signalMeter = SignalAggregate(generation, 1)
    @Volatile private var signal = SignalInput(SignalKind.ROOT, generation,
        readUnit = "received PCM/progress observations (not AudioRecord read calls)",
        normalized = "48,000 Hz · stereo float transport · duplicated mono, not original stereo")
    internal fun signalObservation(): SignalInput = signal
    data class Result(val error: String?, val cleanup: Boolean, val authorized: Boolean = false, val progress: Long = 0)

    fun requestStop() { stopRequested = true; signal = signal.copy(life = SignalLife.STOPPING) }
    fun awaitStop(): String? = try {
        val result = completion.get(8, TimeUnit.SECONDS)
        if (result.cleanup) null else "Root cleanup is unconfirmed. Restart Phosphor only after verifying the helper and policy ended"
    } catch (_: Exception) { "Root helper did not retire within eight seconds. Source replacement is blocked" }

    fun run(
        ready: () -> Long = { 0L },
        readEpoch: () -> Long = { PhosphorNative.captureReadEpoch() },
        samples: (RootAudioProtocol.PcmBatch, FloatArray?, Long) -> Unit = { _, _, _ -> },
        idle: () -> Unit = {},
    ) {
        var child: java.lang.Process? = null
        var lease = false
        var gotReady = false
        var helper: JSONObject? = null
        var final: JSONObject? = null
        var evidence = false
        var exit: Int? = null
        var failure: String? = null
        var protocolFailed = false
        var stopAt: Long? = null
        val decoder = RootAudioProtocol.Decoder()
        val stream = RootAudioProtocol.Stream(Process.myUid(), BuildConfig.ROOT_AUDIO_BUILD, generation, mode)
        var visual: RootEpochNormalizer? = null
        var captureStarted = false
        val started = SystemClock.elapsedRealtime()
        var nextHeartbeat = started + 250
        var lastProgress = started
        var idleShown = false
        fun control(kind: Int) {
            if (kind == 3) stream.stop()
            child?.outputStream?.apply { write(RootAudioProtocol.control(kind, generation)); flush() }
        }
        fun epochControl(now: Long) {
            if (gotReady && mode in 2..3 && helper == null && final == null && !stopRequested) {
                stream.epoch.desire(readEpoch())
                stream.request(now)?.let { wire -> child?.outputStream?.apply { write(wire); flush() } }
            }
            stream.epoch.checkDeadline(now)
        }
        fun consume() {
            val p = child ?: return
            var bytes = 0
            while (p.inputStream.available() > 0 && bytes++ < 65536) {
                val frame = decoder.byte(p.inputStream.read()) ?: continue
                when (frame.first) {
                    11 -> {
                        check(mode != 1 && !gotReady && helper == null && final == null) { "Duplicate or stale root READY" }
                        val data = RootAudioProtocol.helper(frame.second, Process.myUid(), BuildConfig.ROOT_AUDIO_BUILD, generation, mode)
                        check(data.getString("stage") == "ready" && data.getInt("sample_rate") == 16000 && data.getInt("channels") == 1 && data.getInt("encoding") == 2 && data.getInt("route_flags") == 3) { "Root actual format or render route mismatch" }
                        stream.ready(data)
                        gotReady = true
                        lastProgress = SystemClock.elapsedRealtime()
                        signal = signal.copy(descriptor = SignalDescriptor(
                            SignalFormat(data.getInt("sample_rate"), data.getInt("channels"), "PCM16"),
                            observedAt = lastProgress), progressAt = lastProgress)
                        if (!stopRequested) {
                            live = true
                            visual = RootEpochNormalizer(ready())
                            signal = signal.copy(life = SignalLife.RUNNING, nativeOwner = visual?.owner)
                            if (stopRequested) control(3) else epochControl(SystemClock.elapsedRealtime())
                        } else control(3)
                    }
                    12 -> {
                        check(helper == null && final == null && mode != 1) { "Duplicate root RESULT" }
                        helper = RootAudioProtocol.helper(frame.second, Process.myUid(), BuildConfig.ROOT_AUDIO_BUILD, generation, mode)
                        stream.result()
                        live = false
                        signal = signal.copy(life = SignalLife.ENDED)
                    }
                    13 -> {
                        check(!evidence && final == null) { "Duplicate root evidence" }
                        val data = JSONObject(frame.second.toString(Charsets.UTF_8))
                        check(data.getLong("generation") == generation) { "Native generation mismatch" }
                        evidence = true
                    }
                    14 -> {
                        check(final == null) { "Duplicate root FINAL" }
                        val data = JSONObject(frame.second.toString(Charsets.UTF_8))
                        // Before SELECT/setup succeeds, native can only report an error, never authorization.
                        check(data.optLong("generation", generation) == generation &&
                            (data.has("generation") || data.optString("status") == "error")) { "Native final generation mismatch" }
                        final = data
                        live = false
                        signal = signal.copy(life = SignalLife.ENDED)
                    }
                    15 -> {
                        check(gotReady && helper == null && final == null && mode in 2..3) { "PCM outside root session" }
                        val pcm = stream.pcm(frame.second)
                        lastProgress = SystemClock.elapsedRealtime()
                        if (!stopRequested) {
                            val binding = checkNotNull(visual)
                            signalMeter.pcm16(pcm.samples, lastProgress)
                            val desired = readEpoch()
                            stream.epoch.desire(desired)
                            val normalized = binding.convert(pcm, desired)
                            samples(pcm, normalized, binding.owner)
                            frames += pcm.samples.size
                            lastPcmAt = lastProgress
                            signal = signal.copy(progressAt = lastProgress, window = signalMeter.latest)
                            idleShown = false
                        }
                    }
                    16 -> {
                        check(gotReady && helper == null && final == null && mode in 2..3) { "Progress outside root session" }
                        stream.progress(frame.second)
                        lastProgress = SystemClock.elapsedRealtime()
                        signalMeter.progress(lastProgress)
                        signal = signal.copy(progressAt = lastProgress, window = signalMeter.latest)
                        if (!stopRequested && lastProgress - lastPcmAt > 1000 && !idleShown) { idleShown = true; idle() }
                    }
                    17 -> {
                        check(gotReady && helper == null && final == null && mode in 2..3) { "ACK outside root session" }
                        stream.ack(frame.second, if (stopRequested) null else SystemClock.elapsedRealtime())
                        if (!captureStarted && !stopRequested) {
                            stream.start()
                            control(2)
                            captureStarted = true
                        }
                    }
                    else -> error("Unknown root pipe frame")
                }
            }
            var errors = 0
            while (p.errorStream.available() > 0 && errors++ < 8192) p.errorStream.read()
        }
        try {
            check(mode == 1 || mode == 2 || (BuildConfig.DEBUG && mode == 3)) { "Unsupported fixed product mode" }
            RootHelperLease.acquire(); lease = true
            check(!stopRequested) { "Root request retired before launch" }
            RootHelperCode.stage(context)
            val executable = File(context.applicationInfo.nativeLibraryDir, "libphosphor_root_launcher.so")
            check(executable.isFile && executable.canExecute()) { "Installed root launcher is unavailable. Reinstall the exact packaged app" }
            val process = ProcessBuilder(executable.absolutePath).apply { environment().clear() }.start()
            child = process
            process.outputStream.apply { write(RootAudioProtocol.select(generation, mode)); flush() }
            nextHeartbeat = SystemClock.elapsedRealtime() + 250
            while (exit == null) {
                epochControl(SystemClock.elapsedRealtime())
                try {
                    consume()
                    if (process.waitFor(1, TimeUnit.MILLISECONDS)) { consume(); decoder.eof(); exit = process.exitValue(); break }
                } catch (error: Exception) {
                    protocolFailed = protocolFailed || error !is RootEpochTimeout
                    throw error
                }
                val now = SystemClock.elapsedRealtime()
                if (stopRequested && stopAt == null) { stopAt = now; live = false; control(3) }
                if (now >= nextHeartbeat && final == null) { control(1); nextHeartbeat = now + 250 }
                check(stopAt?.let { now - it <= 7500 } ?: true) { "Root stop deadline exceeded" }
                check(gotReady || now - started < 12000) { "Root authorization or startup timed out" }
                check(!gotReady || helper != null || now - lastProgress < 4500) { "Root helper read loop stopped progressing" }
                if (mode == 3) check(now - started < 18000) { "Controlled product deadline exceeded" }
                SystemClock.sleep(5)
            }
        } catch (error: Exception) {
            stopRequested = true
            val cause = error.message ?: error.javaClass.simpleName
            failure = cause
            protocolFailed = protocolFailed || cause.contains("frame", true) || cause.contains("generation", true) || cause.contains("sequence", true)
            live = false
            signal = signal.copy(life = SignalLife.FAILED, reason = cause)
            runCatching { control(3) }
        } finally {
            live = false
            // EOF retires the privileged supervisor even if STOP could not be written.
            runCatching { child?.outputStream?.close() }
            val deadline = minOf(SystemClock.elapsedRealtime() + 7500, stopAt?.plus(7500) ?: Long.MAX_VALUE)
            val process = child
            while (process != null && exit == null && SystemClock.elapsedRealtime() < deadline) {
                runCatching { consume() }.onFailure { protocolFailed = true; failure = failure ?: it.message }
                if (process.waitFor(5, TimeUnit.MILLISECONDS)) {
                    runCatching { consume(); decoder.eof() }.onFailure { protocolFailed = true }
                    exit = process.exitValue()
                }
            }
            runCatching { child?.inputStream?.close() }
            runCatching { child?.errorStream?.close() }
            val reaped = final?.optBoolean("cleanup_confirmed") == true && final?.optBoolean("killed", true) == false && exit != null
            val cleanup = child == null || (!protocolFailed && reaped &&
                (final?.optBoolean("child_started", true) == false || helper?.optBoolean("cleanup_confirmed") == true))
            val authorized = mode == 1 && cleanup && evidence && exit == 0 && final?.optBoolean("authorized") == true && final?.optString("status") == "ok"
            if (failure == null && final?.optString("status") != "ok") failure = final?.optString("error")?.takeIf { it.isNotBlank() } ?: "Root helper ended without a confirmed result"
            if (failure == null && helper?.optString("status") == "error") failure = helper?.optString("error")
            if (lease) RootHelperLease.release(cleanup)
            signal = signal.copy(life = when {
                !cleanup -> SignalLife.CLEANUP_UNCONFIRMED
                failure != null -> SignalLife.FAILED
                else -> SignalLife.ENDED
            }, reason = failure.orEmpty())
            completion.complete(Result(failure, cleanup, authorized, stream.progressSequence))
        }
    }

    companion object { private val generations = AtomicLong(SystemClock.elapsedRealtime().coerceAtLeast(1)) }
}
