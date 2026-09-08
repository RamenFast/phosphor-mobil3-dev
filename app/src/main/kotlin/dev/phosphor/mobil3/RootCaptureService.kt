package dev.phosphor.mobil3

/** Shares capture retirement, not MediaProjection identity. */
class RootCaptureService : CaptureService() {
    internal override val backend = CaptureBackend.ROOT
    companion object {
        private var pendingCheck: RootCaptureCheck? = null
        @Synchronized internal fun reserveCheck(check: RootCaptureCheck) {
            check(BuildConfig.DEBUG && check.accepts() && pendingCheck == null && CaptureService.quiescent() && RootHelperLease.available())
            pendingCheck = check
        }
        @Synchronized internal fun takeCheck(): RootCaptureCheck? = pendingCheck.also { pendingCheck = null }
        @Synchronized internal fun cancelCheck(check: RootCaptureCheck) { if (pendingCheck === check) pendingCheck = null }
    }
}

/** Only the debug DUMP receiver supplies this finite, own-UID observation sink. */
internal interface RootCaptureCheck {
    fun accepts(): Boolean
    fun ready(generation: Long)
    fun samples(generation: Long, pcm: ShortArray, normalizedFrames: Int)
    fun finished(generation: Long?, result: RootCaptureSession.Result?, error: String?)
}
