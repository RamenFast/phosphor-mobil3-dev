package dev.phosphor.mobil3

enum class CaptureBackend { STANDARD, ROOT }

internal object RootCapturePolicy {
    const val ENABLED = "root_capture_enabled"
    const val PROFILE_ACK = "root_capture_profile_ack"
    const val CAPABILITY = "16 kHz mono · duplicated mono"
    const val PRODUCT_AVAILABLE = false
    const val DEFERRED = "Root capture is coming later. Use standard capture with Android consent for now."
    fun mayStart(debug: Boolean, controlledCheck: Boolean, enabled: Boolean) =
        (debug && controlledCheck) || (PRODUCT_AVAILABLE && enabled)
    fun backend(enabled: Boolean, acknowledged: Boolean, explicitStandard: Boolean = false) =
        if (PRODUCT_AVAILABLE && enabled && acknowledged && !explicitStandard) CaptureBackend.ROOT else CaptureBackend.STANDARD
    fun mayAuthorize(explicitEnable: Boolean, acknowledged: Boolean) = PRODUCT_AVAILABLE && explicitEnable && acknowledged
    fun owns(backend: CaptureBackend, running: Boolean, projection: Boolean, record: Boolean, helper: Boolean) =
        running && when (backend) {
            CaptureBackend.STANDARD -> projection && record && !helper
            CaptureBackend.ROOT -> helper && !projection && !record
        }
}

/** Shared by authorization, product and the fixed debug feasibility entry. */
internal object RootHelperLease {
    private var active = false
    private var uncertain = false
    @Synchronized fun acquire() {
        check(!active && !uncertain) { "Root helper is active or cleanup is unconfirmed. Stop capture before retrying" }
        active = true
    }
    @Synchronized fun release(clean: Boolean) { active = false; uncertain = uncertain || !clean }
    @Synchronized fun available() = !active && !uncertain
}

/** A cancelled queued debug start never gains ownership after its caller times out. */
internal class RootStartGate {
    private val active = java.util.concurrent.atomic.AtomicBoolean(true)
    fun accepts() = active.get()
    fun cancel() { active.set(false) }
}
