package dev.phosphor.mobil3

import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Recorder adapter. Only MicCaptureService constructs and starts it. No native ring ownership. */
internal class MicController(private val context: Context, private val session: CaptureMixSession,
                             private val attachment: Long, private val changed: () -> Unit,
                             private val failed: (String) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private val manager = context.getSystemService(AudioManager::class.java)
    private val lease = MicrophoneRouteLease(context)
    private val id = ids.incrementAndGet()
    @Volatile private var active = true
    @Volatile private var running = false
    @Volatile private var record: AudioRecord? = null
    @Volatile private var worker: Thread? = null
    @Volatile private var lost = false
    @Volatile private var verified = false
    @Volatile private var descriptor = SignalDescriptor()
    @Volatile private var status = "Microphone starting"
    @Volatile private var life = SignalLife.STARTING
    @Volatile private var meter: SignalAggregate? = null
    @Volatile private var silenced = false
    @Volatile private var muted = false
    @Volatile private var platformDetail = "Device format unavailable"
    @Volatile private var cleanupError: String? = null
    private var recordReleased = false
    private var routeListener: AudioRouting.OnRoutingChangedListener? = null
    private var recordingCallback: AudioManager.AudioRecordingCallback? = null
    private var devicesCallback: AudioDeviceCallback? = null

    fun isRecording() = active && running && verified && !lost
    fun observation() = SignalInput(SignalKind.MIC, id, nativeOwner = session.nativeOwner,
        life = life, reason = status, descriptor = descriptor, window = meter?.latest,
        contributing = isRecording() && !silenced && !muted && session.accepts(attachment))
    fun detail() = "$status · $platformDetail${if (silenced) " · silenced by Android input policy" else ""}${if (muted) " · system microphone muted" else ""}"
    private fun current() = active && session.accepts(attachment)
    fun start(started: (String?) -> Unit) {
        check(worker == null)
        worker = Thread({
            var startReplied = false
            try {
                val device = MicrophoneRoutes.selected(context) ?: error("Selected microphone is unavailable. Connect it or choose an input")
                check(current()) { "Microphone request cancelled" }
                val choices = MicrophoneRoutePolicy.candidates(device.sampleRates, device.channelCounts, device.encodings)
                check(choices.isNotEmpty()) { "This microphone has no supported mono/stereo PCM format. Select another input" }
                var reason = "Microphone format unavailable"
                var opened: AudioRecord? = null
                var fallbackTried = false
                for (format in choices) {
                    check(current()) { "Microphone request cancelled" }
                    check(MicrophoneRoutes.devices(context).any { it.id == device.id }) { "Selected microphone disconnected. Connect it and retry" }
                    try {
                        val candidate = open(format)
                        record = candidate
                        recordReleased = false
                        installCallbacks(candidate, device)
                        check(candidate.setPreferredDevice(device)) { "Android refused the selected microphone" }
                        candidate.startRecording()
                        check(candidate.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Microphone did not start" }
                        var route = awaitRoute(candidate, device, 250)
                        if (!route && MicrophoneRoutes.choice(device).bluetooth && !fallbackTried) {
                            fallbackTried = true
                            lease.establish(device, ::current)
                            route = awaitRoute(candidate, device, 5_000)
                        }
                        check(route) { "Requested microphone did not become the actual recording route" }
                        check(candidate.sampleRate in 8_000..192_000 && candidate.channelCount in 1..2 &&
                            candidate.audioFormat in setOf(AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_16BIT)) { "Actual microphone client format is unsupported" }
                        opened = candidate; break
                    } catch (error: SecurityException) { throw error }
                    catch (error: RuntimeException) {
                        reason = error.message ?: reason
                        releaseRecord()?.let { throw IllegalStateException(it) }
                    }
                }
                val rec = opened ?: error("$reason. Select another input and retry")
                check(current()) { "Microphone request cancelled" }
                verified = true; running = true; life = SignalLife.RUNNING
                val fmt = CapturePcm(rec.sampleRate, rec.channelCount)
                meter = SignalAggregate(id, fmt.channels)
                observe(rec)
                status = "Microphone active · ${MicrophoneRoutes.choice(device).label}"
                handler.post { if (current()) { changed(); started(null) } else started("Microphone request cancelled") }
                startReplied = true
                read(rec, device, fmt)
            } catch (error: Exception) {
                val message = error.message ?: "Microphone unavailable. Select the input and retry"
                val report = active
                running = false; verified = false
                session.detach(attachment)
                life = if (lost) SignalLife.DISCONNECTED else SignalLife.FAILED
                status = message
                if (!startReplied) handler.post { started(message) }
                if (report) handler.post { failed(message) }
            } finally {
                running = false; verified = false
                val recorderError = releaseRecord()
                val routeError = lease.close()
                cleanupError = recorderError ?: routeError
            }
        }, "microphone-$id").also { it.start() }
    }
    @SuppressLint("MissingPermission")
    private fun open(format: MicrophoneRoutePolicy.Format): AudioRecord {
        val mask = if (format.channels == 1) AudioFormat.CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO
        val encoding = if (format.floating) AudioFormat.ENCODING_PCM_FLOAT else AudioFormat.ENCODING_PCM_16BIT
        val minimum = AudioRecord.getMinBufferSize(format.rate, mask, encoding)
        check(minimum > 0) { "Android rejected this microphone format" }
        val bytes = format.channels * if (format.floating) 4 else 2
        val size = ((maxOf(minimum, format.rate / 10 * bytes) + bytes - 1) / bytes) * bytes
        check(size <= format.rate * bytes) { "Microphone requires an excessive platform buffer" }
        return AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(format.rate).setChannelMask(mask).setEncoding(encoding).build())
            .setBufferSizeInBytes(size).build().also {
                if (it.state != AudioRecord.STATE_INITIALIZED) { it.release(); error("Microphone did not initialize") }
            }
    }
    private fun awaitRoute(rec: AudioRecord, device: AudioDeviceInfo, timeout: Long): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (current() && SystemClock.elapsedRealtime() < deadline) {
            if (MicrophoneRoutes.routed(rec, device)) return true
            routeWake.await(25, TimeUnit.MILLISECONDS)
            routeWake = CountDownLatch(1)
        }
        return false
    }
    @Volatile private var routeWake = CountDownLatch(1)
    private fun installCallbacks(rec: AudioRecord, device: AudioDeviceInfo) {
        routeListener = AudioRouting.OnRoutingChangedListener {
            if (record === rec && active) {
                if (verified && !MicrophoneRoutes.routed(rec, device)) { lost = true; session.detach(attachment) }
                routeWake.countDown()
            }
        }.also { rec.addOnRoutingChangedListener(it, handler) }
        recordingCallback = object : AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
                if (record === rec && active) observe(rec)
            }
        }.also { rec.registerAudioRecordingCallback(context.mainExecutor, it) }
        devicesCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                if (record === rec && active && removedDevices.any { it.id == device.id }) {
                    lost = true; session.detach(attachment); routeWake.countDown()
                }
            }
        }.also { manager.registerAudioDeviceCallback(it, handler) }
    }
    private fun observe(rec: AudioRecord) {
        descriptor = observeSignalRecorder(rec)
        runCatching {
            val config = rec.activeRecordingConfiguration
            val nextSilenced = config?.isClientSilenced == true
            val nextMuted = manager.isMicrophoneMute
            if ((nextSilenced && !silenced) || (nextMuted && !muted)) session.clearMicrophone(attachment)
            silenced = nextSilenced
            muted = nextMuted
            platformDetail = config?.format?.let { "Device ${it.sampleRate} Hz · ${it.channelCount} channels · encoding ${it.encoding}" }
                ?: "Device format unavailable"
        }
    }
    private fun read(rec: AudioRecord, device: AudioDeviceInfo, fmt: CapturePcm) {
        val chunk = FloatArray(fmt.rate / 100 * fmt.channels + fmt.channels)
        val shorts = ShortArray(chunk.size)
        var frames = 0L
        var tail = 0
        var tailEpoch = -1L
        var observedAt = 0L
        while (current()) {
            check(!lost && MicrophoneRoutes.routed(rec, device)) { lost = true; "Selected microphone route was lost. Connect it and retry" }
            val epoch = PhosphorNative.captureReadEpoch()
            if (tailEpoch != epoch) tail = 0
            val count = if (rec.audioFormat == AudioFormat.ENCODING_PCM_FLOAT) {
                rec.read(chunk, tail, chunk.size - fmt.channels, AudioRecord.READ_BLOCKING)
            } else {
                val n = rec.read(shorts, 0, shorts.size - fmt.channels, AudioRecord.READ_BLOCKING)
                if (n > 0) for (i in 0 until n) chunk[tail + i] = shorts[i] / 32768f
                n
            }
            check(count >= 0) { "Microphone read failed ($count). Check permission and retry" }
            if (!current()) break
            check(!lost && MicrophoneRoutes.routed(rec, device)) { lost = true; "Selected microphone route was lost. Connect it and retry" }
            val total = tail + count
            val complete = total - total % fmt.channels
            val now = SystemClock.elapsedRealtime()
            if (now - observedAt >= 250) { observe(rec); observedAt = now }
            meter?.floats(chunk, complete, now)
            if (complete > 0) {
                if (!silenced && !muted) session.offer(true, chunk, complete, fmt, frames, captureClock(rec),
                    SystemClock.elapsedRealtimeNanos(), attachment, epoch)
                frames += complete / fmt.channels
            }
            tail = total - complete; tailEpoch = epoch
            for (i in 0 until tail) chunk[i] = chunk[complete + i]
            if (count == 0) routeWake.await(10, TimeUnit.MILLISECONDS)
        }
        if (lost) error("Selected microphone disconnected. Connect it and retry")
    }
    private fun releaseRecord(): String? {
        val rec = record ?: return null
        var error: String? = null
        fun attempt(action: () -> Unit) { try { action() } catch (failure: RuntimeException) { error = error ?: failure.message ?: "Microphone cleanup failed" } }
        if (!recordReleased) runCatching { if (rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) rec.stop() }
        routeListener?.let { callback -> attempt { rec.removeOnRoutingChangedListener(callback); routeListener = null } }
        recordingCallback?.let { callback -> attempt { rec.unregisterAudioRecordingCallback(callback); recordingCallback = null } }
        devicesCallback?.let { callback -> attempt { manager.unregisterAudioDeviceCallback(callback); devicesCallback = null } }
        if (!recordReleased) {
            runCatching { rec.setPreferredDevice(null) }
            attempt { rec.release(); recordReleased = true }
        }
        if (recordReleased && routeListener == null && recordingCallback == null && devicesCallback == null) record = null
        return error
    }
    fun cancel() { active = false; running = false; verified = false; session.detach(attachment); lease.cancel(); routeWake.countDown() }
    fun finish(): String? {
        cancel()
        val error = ReaderStop.finish(worker, { runCatching { record?.let { if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) it.stop() } } }, {}, {})
        if (error != null) return error
        // A completed reader permits retrying failed cleanup, never concurrent record release.
        val recorderError = releaseRecord()
        val routeError = lease.close()
        cleanupError = recorderError ?: routeError
        return cleanupError
    }
    companion object {
        private val ids = AtomicLong()
        fun stopForLocal(requestId: Long, reply: (Long, String?, Boolean) -> Unit) = MicCaptureService.stopForLocal(requestId, reply)
        fun quiescent() = MicCaptureService.quiescent()
    }
}

/** Getters only. Called by the recorder owner after start and at bounded existing read boundaries. */
internal fun observeSignalRecorder(rec: AudioRecord): SignalDescriptor = runCatching {
    val encoding = when (rec.audioFormat) {
        AudioFormat.ENCODING_PCM_FLOAT -> "float PCM"
        AudioFormat.ENCODING_PCM_16BIT -> "PCM16"
        else -> "encoding ${rec.audioFormat}"
    }
    val format = if (rec.sampleRate > 0 && rec.channelCount > 0) SignalFormat(rec.sampleRate, rec.channelCount, encoding) else null
    val route = rec.routedDevice?.let { "${it.productName} · type ${it.type} · device ${it.id}" }
    SignalDescriptor(format, route, android.os.SystemClock.elapsedRealtime())
}.getOrElse { SignalDescriptor(unavailable = "Recorder observation unavailable: ${it.javaClass.simpleName}") }

/** Called on the owner's main thread after a reader reports failure. */
internal fun retireSourceReaderFailure(
    isCurrent: () -> Boolean,
    stop: () -> Unit,
    publishFailure: () -> Unit = {},
) {
    if (isCurrent()) {
        stop()
        publishFailure()
    }
}
