package dev.phosphor.mobil3

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

// Debug-only. Runs the deterministic offscreen engine render and writes
// selftest.json + selftest.png into the app files dir for pm3 smoke to pull.
class SelfTestReceiver : BroadcastReceiver() {
    companion object {
        private val active = AtomicBoolean(false)
        internal fun accepts(action: String?) = action == "dev.phosphor.mobil3.SELFTEST" ||
            action == "dev.phosphor.mobil3.ROOT_AUTH_PROBE" || action == "dev.phosphor.mobil3.KSU_AUTH_PROBE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (!accepts(intent.action) || !active.compareAndSet(false, true)) return
        val pending = goAsync()
        Thread {
            try {
                if (intent.action == "dev.phosphor.mobil3.ROOT_AUTH_PROBE" || intent.action == "dev.phosphor.mobil3.KSU_AUTH_PROBE") {
                    RootAuthorizationProbe.run(context, kernelSu = intent.action == "dev.phosphor.mobil3.KSU_AUTH_PROBE")
                    Log.i("phosphor-mobil3", "ROOT_AUTH_PROBE receipt written")
                } else {
                    val report = PhosphorNative.selfTest(context.filesDir.absolutePath)
                    Log.i("phosphor-mobil3", "SELFTEST done: $report")
                }
            } catch (t: Throwable) {
                Log.e("phosphor-mobil3", "SELFTEST failed", t)
            } finally {
                active.set(false)
                pending.finish()
            }
        }.apply { name = "phosphor-debug-selftest" }.start()
    }
}
