package dev.phosphor.mobil3.settings

/** UI owner for merge, persistence and native queue publication. Document decoding stays outside. */
internal class SettingsWriteOwner {
    private val thread = Thread.currentThread()
    private var writing = false

    fun <T> write(block: () -> T): T {
        check(Thread.currentThread() === thread) { "Settings writes must use their UI owner" }
        check(!writing) { "A settings write is already active" }
        writing = true
        return try { block() } finally { writing = false }
    }

    data class Failure(val stage: String, val restored: Boolean) {
        fun message() = if (restored) {
            "$stage failed. Previous settings restored. Try again."
        } else {
            "$stage failed and rollback failed. Saved settings are uncertain. Reopen Phosphor before another edit."
        }
    }

    fun commit(commit: () -> Boolean, publish: () -> Boolean, rollback: () -> Boolean): Failure? {
        check(Thread.currentThread() === thread && writing) { "Commit needs an active settings owner" }
        fun attempt(action: () -> Boolean) = try { action() } catch (_: Exception) { false }
        val stage = when {
            !attempt(commit) -> "Saving settings"
            !attempt(publish) -> "Renderer publication"
            else -> return null
        }
        return Failure(stage, attempt(rollback))
    }
}
