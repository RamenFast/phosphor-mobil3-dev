package dev.phosphor.mobil3

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

// Debug-only. Runs the deterministic offscreen engine render and writes
// selftest.json + selftest.png into the app files dir for pm3 smoke to pull.
class SelfTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                val report = PhosphorNative.selfTest(context.filesDir.absolutePath)
                Log.i("phosphor-mobil3", "SELFTEST done: $report")
            } catch (t: Throwable) {
                Log.e("phosphor-mobil3", "SELFTEST failed", t)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
