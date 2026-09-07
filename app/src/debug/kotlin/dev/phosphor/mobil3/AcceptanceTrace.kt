package dev.phosphor.mobil3

import android.os.SystemClock
import android.util.Log

/** Local, explicitly enabled observations. Never records metadata, PCM, or account data. */
internal object AcceptanceTrace {
    private const val TAG = "PhosphorAcceptance"

    fun record(event: String, fields: () -> String) {
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "uptime_ms=${SystemClock.uptimeMillis()} event=$event ${fields()}")
        }
    }
}
