package dev.phosphor.mobil3

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import android.os.SystemClock
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import kotlin.math.sin

/** Explicit DUMP-protected feasibility only. No normal-startup caller exists. */
internal object RootAudioProbe {
    private var cleanupUncertain = false // Accessed under SelfTestReceiver's single-flight lock.
    private const val FIX = "Inspect the named stage and helper cleanup. Verify no helper or policy remains before retrying. Do not change system policy."
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun directory(file: File, uid: Int) {
        try { Os.mkdir(file.path, 448) } catch (e: ErrnoException) { if (e.errno != OsConstants.EEXIST) throw e }
        val stat = Os.lstat(file.path)
        check(OsConstants.S_ISDIR(stat.st_mode) && stat.st_uid == uid && stat.st_mode and 63 == 0) { "Private helper directory identity/mode failed" }
    }
    private fun verify(file: File, uid: Int, expected: String) {
        val fd = Os.open(file.path, OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW or OsConstants.O_CLOEXEC, 0)
        FileInputStream(fd).use { input ->
            val stat = Os.fstat(fd)
            check(OsConstants.S_ISREG(stat.st_mode) && stat.st_uid == uid && stat.st_mode and 146 == 0 && stat.st_size in 1L..1048576L) { "Sealed helper inode identity/type/mode/size failed" }
            val bytes = input.readBytesBounded(1048576)
            check(sha(bytes) == expected) { "Sealed helper digest mismatch" }
        }
    }
    private fun java.io.InputStream.readBytesBounded(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            check(count > 0 && out.size() + count <= max) { "Asset size/progress limit" }
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }
    private fun stage(context: Context) {
        val uid = Process.myUid()
        val expected = context.assets.open("root-audio/helper.sha256").use { it.readBytesBounded(64).toString(Charsets.US_ASCII) }
        check(expected.matches(Regex("[a-f0-9]{64}"))) { "Packaged helper digest invalid" }
        val data = context.noBackupFilesDir
        val fixed = File("/data/user/${uid / 100000}/dev.phosphor.mobil3.debug/no_backup")
        check(context.packageName == "dev.phosphor.mobil3.debug" && data.canonicalPath == fixed.canonicalPath) { "Unsupported fixed private data placement" }
        val managed = Os.lstat(data.path)
        check(OsConstants.S_ISDIR(managed.st_mode) && managed.st_uid == uid && managed.st_mode and 2 == 0) { "Android-managed no_backup owner/type/world-write failed" }
        val base = File(data, "root-helper").also { directory(it, uid) }
        val version = File(base, BuildConfig.ROOT_AUDIO_BUILD).also { directory(it, uid) }
        val target = File(version, "helper.jar")
        if (target.exists()) { verify(target, uid, expected); return }
        val temp = File(version, "helper.jar.new")
        val fd = Os.open(temp.path, OsConstants.O_WRONLY or OsConstants.O_CREAT or OsConstants.O_EXCL or OsConstants.O_NOFOLLOW or OsConstants.O_CLOEXEC, 256)
        var published = false
        try {
            FileOutputStream(fd).use { out ->
                Os.fchmod(fd, 256) // Read-only before writing through the exclusive already-open handle.
                val bytes = context.assets.open("root-audio/helper.jar").use { it.readBytesBounded(1048576) }
                check(sha(bytes) == expected) { "Bundled DEX digest mismatch" }
                out.write(bytes)
                out.fd.sync()
            }
            verify(temp, uid, expected)
            check(!target.exists()) { "Unexpected concurrent helper publication" }
            Os.rename(temp.path, target.path)
            published = true
            verify(target, uid, expected)
        } finally {
            if (!published) runCatching { Os.remove(temp.path) }
        }
    }
    private fun fixture(): AudioTrack {
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_ALL).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(32000).build()
        try {
            check(track.state == AudioTrack.STATE_NO_STATIC_DATA) { "Fixture state invalid" }
            val samples = ShortArray(16000) { (sin(2 * Math.PI * 997 * it / 16000) * 32767 * 0.025).toInt().toShort() }
            check(track.write(samples, 0, samples.size) == samples.size) { "Fixture partial write" }
            check(track.setLoopPoints(0, samples.size, 4) == AudioTrack.SUCCESS) { "Fixture loop setup failed" }
            return track
        } catch (error: Throwable) { track.release(); throw error }
    }
    fun run(context: Context) {
        val started = SystemClock.elapsedRealtime()
        val data = JSONObject().put("package", context.packageName).put("version_name", BuildConfig.VERSION_NAME)
            .put("version_code", BuildConfig.VERSION_CODE).put("build_commit", BuildConfig.BUILD_COMMIT)
            .put("helper_build", BuildConfig.ROOT_AUDIO_BUILD).put("app_uid", Process.myUid()).put("app_pid", Process.myPid())
            .put("hardware_acceptance", false).put("audibility_proven", false)
        var child: java.lang.Process? = null
        var tone: AudioTrack? = null
        var result: JSONObject? = null
        var final: JSONObject? = null
        var ready = false
        var acceptingReady = true
        var fixtureClean = true
        var error: String? = null
        var exit: Int? = null
        val stderr = StringBuilder()
        val decoder = RootAudioProtocol.Decoder()
        var controlFrames = 0
        fun control(kind: Int) {
            check(++controlFrames <= 100) { "Control frame budget exhausted" }
            child?.outputStream?.apply { write(RootAudioProtocol.control(kind)); flush() }
        }
        fun consume() {
            val p = child ?: return
            var count = 0
            while (p.inputStream.available() > 0 && count++ < 16384) {
                val value = p.inputStream.read()
                check(value >= 0) { "Unexpected pipe EOF" }
                val frame = decoder.byte(value) ?: continue
                when (frame.first) {
                    11 -> {
                        check(acceptingReady && !ready && result == null && final == null) { "Stale or duplicate READY" }
                        val observation = RootAudioProtocol.helper(frame.second, Process.myUid(), BuildConfig.ROOT_AUDIO_BUILD)
                        check(observation.getString("stage") == "ready") { "Invalid READY stage" }
                        data.put("ready", observation)
                        check(context.getSystemService(AudioManager::class.java).mode == AudioManager.MODE_NORMAL) { "Communication mode active" }
                        tone = fixture()
                        tone.play()
                        ready = true
                        control(2)
                    }
                    12 -> {
                        check(result == null && final == null) { "Duplicate or late RESULT" }
                        result = RootAudioProtocol.helper(frame.second, Process.myUid(), BuildConfig.ROOT_AUDIO_BUILD)
                        data.put("helper", result)
                    }
                    13 -> {
                        check(!data.has("native") && final == null) { "Duplicate native evidence" }
                        data.put("native", JSONObject(frame.second.toString(Charsets.UTF_8)))
                    }
                    14 -> {
                        check(final == null) { "Duplicate FINAL" }
                        final = JSONObject(frame.second.toString(Charsets.UTF_8))
                        data.put("supervisor", final)
                    }
                    else -> throw IllegalStateException("Unknown supervisor frame")
                }
            }
            count = 0
            while (p.errorStream.available() > 0 && count++ < 8192) {
                val value = p.errorStream.read()
                if (value < 0) break
                if (stderr.length < 2048) stderr.append(value.toChar())
            }
        }
        try {
            check(!cleanupUncertain) { "Previous root cleanup is uncertain. Refusing another session" }
            val manager = context.getSystemService(AudioManager::class.java)
            check(manager.mode == AudioManager.MODE_NORMAL) { "Communication mode active" }
            stage(context)
            val executable = File(context.applicationInfo.nativeLibraryDir, "libphosphor_root_launcher.so")
            check(executable.isFile && executable.canExecute()) { "Packaged native launcher is not extracted executable code" }
            val builder = ProcessBuilder(executable.absolutePath)
            builder.environment().clear()
            child = builder.start()
            var heartbeat = 0L
            while (SystemClock.elapsedRealtime() - started < 23000) {
                consume()
                if (child!!.waitFor(1, TimeUnit.MILLISECONDS)) { consume(); decoder.eof(); exit = child.exitValue(); break }
                val now = SystemClock.elapsedRealtime()
                if (now >= heartbeat && final == null) { control(1); heartbeat = now + 250 }
                if (ready && manager.mode != AudioManager.MODE_NORMAL) throw IllegalStateException("Communication mode changed during fixture")
                SystemClock.sleep(10)
            }
            check(exit != null) { "Supervisor finite deadline exceeded" }
        } catch (failure: Throwable) {
            error = "${failure.javaClass.simpleName}: ${failure.message}".take(1000)
            runCatching { control(3) }
        } finally {
            acceptingReady = false
            try { tone?.stop() } catch (failure: Throwable) { fixtureClean = false; data.put("fixture_stop_error", failure.toString().take(500)) }
            try { tone?.release() } catch (failure: Throwable) { fixtureClean = false; data.put("fixture_release_error", failure.toString().take(500)) }
            // EOF and the native alarm own privileged termination. Never rely on Process.destroy().
            runCatching { child?.outputStream?.close() }
            val stopDeadline = SystemClock.elapsedRealtime() + 2000
            while (child != null && exit == null && SystemClock.elapsedRealtime() < stopDeadline) {
                runCatching { consume() }.onFailure { if (error == null) error = it.toString().take(1000) }
                if (child.waitFor(10, TimeUnit.MILLISECONDS)) { runCatching { consume(); decoder.eof() }; exit = child.exitValue() }
            }
            runCatching { child?.inputStream?.close() }
            runCatching { child?.errorStream?.close() }
        }
        val cleanup = fixtureClean && (child == null || (exit != null && final?.optBoolean("cleanup_confirmed") == true &&
            !final.optBoolean("killed", true) && (result?.optBoolean("cleanup_confirmed") == true || !ready && !data.has("native"))))
        if (!cleanup) cleanupUncertain = true
        val success = error == null && cleanup && RootAudioProtocol.success(ready, result, final, exit, fixtureClean)
        data.put("cleanup_confirmed", cleanup).put("supervisor_exit", exit ?: JSONObject.NULL)
            .put("stderr", stderr.toString()).put("elapsed_ms", SystemClock.elapsedRealtime() - started)
        val report = JSONObject().put("status", if (success) "ok" else "error").put("tool", "pm3-root-audio-feasibility")
            .put("version", "1.0.0").put("ts", Instant.now().toString()).put("data", data)
        if (!success) report.put("error", error ?: result?.optString("error")?.takeIf { it.isNotBlank() }
            ?: final?.optString("error")?.takeIf { it.isNotBlank() } ?: "Controlled tone or cleanup not proven").put("fix", FIX)
        val file = AtomicFile(File(context.filesDir, "root-audio-feasibility.json"))
        val stream = file.startWrite()
        try { stream.write(report.toString().toByteArray()); file.finishWrite(stream) }
        catch (failure: Throwable) { file.failWrite(stream); throw failure }
    }
}
