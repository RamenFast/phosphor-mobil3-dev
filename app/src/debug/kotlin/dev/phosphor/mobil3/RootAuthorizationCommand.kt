package dev.phosphor.mobil3

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

/** Fixed diagnostic only. No caller-supplied executable, command, environment, or arguments. */
internal object RootAuthorizationCommand {
    const val COMMAND = "/system/bin/id -u"
    private val candidates = listOf("su", "/system/bin/su", "/system/xbin/su", "/sbin/su", "/debug_ramdisk/su")
    const val OUTPUT_LIMIT = 4_096

    data class Result(
        val outcome: String,
        val attempts: List<String>,
        val exitCode: Int? = null,
        val stdout: String = "",
        val stderr: String = "",
        val cleanupConfirmed: Boolean = true,
        val launchErrors: List<String> = emptyList(),
    ) {
        val granted: Boolean get() = outcome == "root_granted"
        val fix: String get() = when (outcome) {
            "root_granted" -> "Next verify AudioPolicy capture. This receipt proves authorization only."
            "su_unavailable" -> "Check the existing root manager's app support. No standard su entry point launched."
            "authorization_timeout" -> "Check the Phosphor grant in the root manager, then explicitly retry this probe."
            "cleanup_unconfirmed" -> "Stop the debug app and inspect its child processes before retrying."
            "output_limit" -> "Inspect the root provider's compatibility. The fixed identity command exceeded its output limit."
            "read_failed" -> "Inspect the root provider's pipe failure, then explicitly retry this probe."
            else -> "Check the Phosphor root profile. A zero-exit UID 0 identity is required."
        }
    }

    fun run(start: (List<String>) -> Process = { ProcessBuilder(it).start() }): Result {
        val attempts = mutableListOf<String>()
        val errors = mutableListOf<String>()
        for (candidate in candidates) {
            attempts += candidate
            val process = try {
                start(listOf(candidate, "-c", COMMAND))
            } catch (failure: IOException) {
                errors += (failure.message ?: "IOException").take(512)
                if (!errors.last().contains("error=2,")) {
                    return Result("launch_failed", attempts.toList(), launchErrors = errors.toList())
                }
                continue
            } catch (_: SecurityException) {
                return Result("launch_denied", attempts.toList())
            }
            // A launched denial or timeout is not permission to try another root entry point.
            return observe(process).copy(attempts = attempts.toList(), launchErrors = errors.toList())
        }
        return Result("su_unavailable", attempts.toList(), launchErrors = errors.toList())
    }

    internal fun observe(process: Process, timeoutMs: Long = 3_000): Result {
        val stdout = BoundedOutput(process.inputStream)
        val stderr = BoundedOutput(process.errorStream)
        val readers = listOf(stdout, stderr).mapIndexed { index, output ->
            Thread(output, "root-identity-output-$index").apply { isDaemon = true; start() }
        }
        var timedOut = false
        var interrupted = false
        try {
            process.outputStream.close() // The fixed identity command never consumes caller input.
            timedOut = !process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            interrupted = true
            timedOut = true
        } catch (_: IOException) {
            timedOut = true
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
        fun remainingMs(): Long = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()).coerceAtLeast(1)
        try {
            process.waitFor(remainingMs(), TimeUnit.MILLISECONDS)
            readers.forEach { if (it.isAlive) it.join(remainingMs()) }
        } catch (_: InterruptedException) {
            interrupted = true
        } finally {
            if (interrupted) Thread.currentThread().interrupt()
        }
        val cleanup = !process.isAlive && readers.none { it.isAlive }
        // Do not read a collector's mutable buffer without a completed join.
        val out = if (!readers[0].isAlive) stdout.text() else ""
        val err = if (!readers[1].isAlive) stderr.text() else ""
        val exit = if (!process.isAlive) process.exitValue() else null
        val outcome = when {
            !cleanup -> "cleanup_unconfirmed"
            timedOut -> "authorization_timeout"
            stdout.overflow || stderr.overflow -> "output_limit"
            stdout.failed || stderr.failed -> "read_failed"
            exit == 0 && out.trim() == "0" -> "root_granted"
            else -> "authorization_failed"
        }
        return Result(outcome, emptyList(), exit, out, err, cleanup)
    }

    private class BoundedOutput(private val input: InputStream) : Runnable {
        private val bytes = ByteArrayOutputStream()
        @Volatile var overflow = false
        @Volatile var failed = false
        override fun run() {
            try {
                input.use {
                    val buffer = ByteArray(1_024)
                    while (true) {
                        val count = it.read(buffer)
                        if (count < 0) break
                        val keep = count.coerceAtMost(OUTPUT_LIMIT - bytes.size())
                        bytes.write(buffer, 0, keep)
                        if (keep < count) overflow = true
                    }
                }
            } catch (_: IOException) {
                failed = true
            }
        }
        fun text(): String = bytes.toString(Charsets.UTF_8.name())
    }
}
