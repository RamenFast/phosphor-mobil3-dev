package dev.phosphor.mobil3

import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant

/** Called only by the DUMP-protected explicit debug receiver action, never by app startup. */
internal object RootAuthorizationProbe {
    fun run(context: Context) {
        val started = SystemClock.elapsedRealtime()
        val result = RootAuthorizationCommand.run()
        val data = JSONObject()
            .put("package", context.packageName)
            .put("build_commit", BuildConfig.BUILD_COMMIT)
            .put("app_uid", Process.myUid())
            .put("app_pid", Process.myPid())
            .put("process_context", runCatching {
                File("/proc/self/attr/current").bufferedReader().use { it.readLine()?.take(256) }
            }.getOrNull() ?: JSONObject.NULL)
            .put("outcome", result.outcome)
            .put("command", RootAuthorizationCommand.COMMAND)
            .put("attempts", JSONArray(result.attempts))
            .put("launch_errors", JSONArray(result.launchErrors))
            .put("exit_code", result.exitCode ?: JSONObject.NULL)
            .put("stdout", result.stdout)
            .put("stderr", result.stderr)
            .put("cleanup_confirmed", result.cleanupConfirmed)
            .put("elapsed_ms", SystemClock.elapsedRealtime() - started)
            .put("capture_tested", false)
        val report = JSONObject()
            .put("status", if (result.granted) "ok" else "error")
            .put("tool", "pm3-root-authorization")
            .put("version", "1.0.0")
            .put("ts", Instant.now().toString())
            .put("data", data)
        if (!result.granted) report.put("error", result.outcome).put("fix", result.fix)
        val file = AtomicFile(File(context.filesDir, "root-authorization.json"))
        val output = file.startWrite()
        try {
            output.write((report.toString() + "\n").toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (failure: Throwable) {
            file.failWrite(output)
            throw failure
        }
    }
}
