package dev.phosphor.mobil3

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import java.util.concurrent.CompletableFuture

/** A removed task retires its callbacks, even when an existing service source lingers. */
internal class BackgroundLifecyclePolicy {
    data class Removal(val keepPlayback: Boolean, val keepCapture: Boolean, val keepMicrophone: Boolean = false)

    var revision = 0L
        private set
    var removed = false
        private set
    var activityRevision = 0L
        private set
    var taskId: Int? = null
        private set

    fun enterActivity(task: Int = 0): Long {
        if (!removed && taskId != null && taskId != task) revision++
        taskId = task
        removed = false
        activityRevision++
        return revision
    }

    fun leaveActivity(request: Long) {
        if (request == activityRevision) activityRevision++
    }

    fun acceptsActivity(request: Long): Boolean = !removed && request == activityRevision

    fun accepts(request: Long): Boolean = !removed && request == revision

    fun callbackRevision(serviceRevision: Long, currentTaskPresent: Boolean?): Long? = when {
        removed || currentTaskPresent == true -> null
        currentTaskPresent == false -> revision
        serviceRevision == revision -> revision
        else -> null
    }

    fun remove(request: Long, linger: Boolean, local: Boolean, relay: Boolean, capture: Boolean, microphone: Boolean = false): Removal? {
        if (!accepts(request)) return null
        removed = true
        revision++
        return Removal(linger && (local || relay || capture), linger && capture, linger && microphone)
    }

    companion object {
        const val LINGER_KEY = "linger_background"
        const val DEFAULT_LINGER = false
        fun linger(values: Map<String, *>): Boolean = values[LINGER_KEY] as? Boolean ?: DEFAULT_LINGER
    }
}

/** An old owner's cleanup must finish before a replacement can touch the shared native source. */
internal class SourceRetirement {
    private val owners = linkedMapOf<Any, CompletableFuture<String?>>()
    val pending: CompletableFuture<String?>
        get() = owners.values.fold(CompletableFuture.completedFuture<String?>(null)) { result, next ->
            result.thenCombine(next) { previous, current -> previous ?: current }
        }

    fun add(owner: Any, completion: CompletableFuture<String?>) {
        owners.entries.removeAll { it.value.isDone && !it.value.isCompletedExceptionally && it.value.getNow(null) == null }
        owners[owner] = completion
    }

    fun retryFailed(retry: (Any) -> Unit) {
        owners.filterValues { it.isDone && !it.isCompletedExceptionally && it.getNow(null) != null }
            .keys.toList().forEach(retry)
    }

    companion object {
        /** Call on the serial worker. An unresolved predecessor is not a new owner's failure. */
        fun await(previous: CompletableFuture<String?>): String? = previous.get()
    }
}

internal class CaptureStopLifecycle {
    private var completion = SourceStopCompletion()
    private var destroyed = false
    private var taskRemoved = false
    val result: CompletableFuture<String?> get() = completion.result
    fun cleanupFinished(error: String?) = completion.cleanupFinished(error)
    fun ownerDestroyed() { destroyed = true; completion.ownerDestroyed() }
    fun removeTask() { taskRemoved = true }
    fun shouldStopService(error: String?): Boolean = !destroyed && (taskRemoved || error == null)
    fun retry(): SourceStopCompletion = SourceStopCompletion().also {
        if (destroyed) it.ownerDestroyed()
        completion = it
    }
}

internal class CaptureOwnerToken {
    private var current: Long? = null
    fun attach(owner: Long) { current = owner }
    fun clear() { current = null }
    fun accepts(owner: Long): Boolean = current == owner
    fun restore(owner: Long?, begin: () -> Unit) {
        if (owner == null || current == owner) return
        current = owner
        begin()
    }
}

internal class ActivityControllerBinding {
    private var generation = 0L
    fun start(): Long = ++generation
    fun cancel() { ++generation }
    fun accepts(request: Long): Boolean = request == generation
}

/** Main-thread task coordination. These are stop rendezvous, not new audio owners. */
internal object BackgroundLifecycle {
    val policy = BackgroundLifecyclePolicy()
    const val EXTRA_REVISION = "task_revision"
    const val EXTRA_ACTIVITY_REVISION = "activity_revision"

    fun stamp(intent: Intent, revision: Long): Intent = intent.putExtra(EXTRA_REVISION, revision)

    fun accepts(intent: Intent?): Boolean = intent == null ||
        ((!intent.hasExtra(EXTRA_REVISION) || policy.accepts(intent.getLongExtra(EXTRA_REVISION, -1))) &&
            (!intent.hasExtra(EXTRA_ACTIVITY_REVISION) ||
                policy.acceptsActivity(intent.getLongExtra(EXTRA_ACTIVITY_REVISION, -1))))

    fun removeTask(context: Context, revision: Long) {
        // A retained service can outlive several tasks. The callback has no task ID, so
        // an existing current app task takes precedence over an older removal callback.
        val currentTaskPresent = policy.taskId?.let { task ->
            runCatching {
                context.getSystemService(ActivityManager::class.java).appTasks.any { it.taskInfo.taskId == task }
            }.onFailure {
                android.util.Log.w("PhosphorPlayback", "Task membership unavailable", it)
            }.getOrNull()
        }
        val removedRevision = policy.callbackRevision(revision, currentTaskPresent) ?: return
        val prefs = context.getSharedPreferences(PhosphorApplication.PREFERENCES_NAME, Context.MODE_PRIVATE)
        val removal = policy.remove(
            removedRevision,
            BackgroundLifecyclePolicy.linger(prefs.all),
            PlaybackService.ownsLocal(), PlaybackService.ownsRelay(), CaptureService.ownsCapture(), MicCaptureService.established(),
        ) ?: return
        PlaybackService.localSourcePublication.selected()
        context.getSharedPreferences(PhosphorApplication.RUNTIME_PREFERENCES_NAME, Context.MODE_PRIVATE).edit {
            putString("last_source", if (removal.keepCapture) "capture" else if (removal.keepMicrophone) "mic" else "none")
            if (!removal.keepCapture) remove("consent_seen")
        }
        MicCaptureService.removeTask(removal.keepMicrophone)
        CaptureService.removeTask(removal.keepCapture)
        PlaybackService.removeTask(removal.keepPlayback)
    }
}
