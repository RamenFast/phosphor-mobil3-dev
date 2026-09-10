package dev.phosphor.mobil3

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRecord
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal object MicrophoneRoutes {
    fun devices(context: Context): List<AudioDeviceInfo> = context.getSystemService(AudioManager::class.java)
        .getDevices(AudioManager.GET_DEVICES_INPUTS).filter { MicrophoneRoutePolicy.supported(it.isSource, it.type, Build.VERSION.SDK_INT) }
    fun choice(device: AudioDeviceInfo) = MicrophoneChoice(device.id, device.type, device.productName.toString(), device.address)
    fun choices(context: Context): List<MicrophoneChoice> = runCatching { devices(context).map(::choice) }.getOrDefault(emptyList())
    fun selected(context: Context): AudioDeviceInfo? {
        val devices = devices(context)
        val key = context.getSharedPreferences(PhosphorApplication.RUNTIME_PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(MicrophoneRoutePolicy.SELECTED, null)
        val selected = MicrophoneRoutePolicy.select(devices.map(::choice), key) ?: return null
        return devices.singleOrNull { it.id == selected.id }
    }
    fun routed(record: AudioRecord, device: AudioDeviceInfo): Boolean = runCatching {
        val actual = if (Build.VERSION.SDK_INT >= 36) record.routedDevices.map { it.id }
            else listOfNotNull(record.routedDevice?.id)
        MicrophoneRoutePolicy.routed(device.id, actual)
    }.getOrDefault(false)
}

/** One recorder generation owns only these temporary platform requests. Close off main. */
@Suppress("DEPRECATION")
internal class MicrophoneRouteLease(private val context: Context) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private var communication = false
    private var sco = false
    private var mode = false
    private var receiver: BroadcastReceiver? = null
    private var communicationListener: AudioManager.OnCommunicationDeviceChangedListener? = null
    @Volatile private var closed = false
    @Volatile private var waiting: CountDownLatch? = null
    fun cancel() { closed = true; waiting?.countDown() }
    fun establish(input: AudioDeviceInfo, current: () -> Boolean) {
        check(!closed && current()) { "Microphone request cancelled" }
        check(manager.mode == AudioManager.MODE_NORMAL) { "Another communication route is active. Finish it and retry" }
        mode = true
        manager.mode = AudioManager.MODE_IN_COMMUNICATION
        val ready = CountDownLatch(1)
        waiting = ready
        if (Build.VERSION.SDK_INT >= 31) {
            val candidates = manager.availableCommunicationDevices.filter { it.type == input.type }
            val target = candidates.filter { input.address.isNotBlank() && it.address == input.address }.singleOrNull()
                ?: candidates.filter { it.productName.toString() == input.productName.toString() }.singleOrNull()
                ?: candidates.singleOrNull()
                ?: error("Headset communication route is missing or ambiguous. Select the input again")
            val listener = AudioManager.OnCommunicationDeviceChangedListener { device ->
                if (device?.id == target.id) ready.countDown()
            }
            communicationListener = listener
            manager.addOnCommunicationDeviceChangedListener(context.mainExecutor, listener)
            communication = true
            check(manager.setCommunicationDevice(target)) { "Android refused this headset route. Select another input" }
            if (manager.communicationDevice?.id == target.id) ready.countDown()
        } else {
            val listener = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1) == AudioManager.SCO_AUDIO_STATE_CONNECTED) ready.countDown()
                }
            }
            receiver = listener
            val sticky = ContextCompat.registerReceiver(context, listener, IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED), ContextCompat.RECEIVER_EXPORTED)
            sco = true
            manager.startBluetoothSco()
            if (sticky?.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1) == AudioManager.SCO_AUDIO_STATE_CONNECTED) ready.countDown()
        }
        check(ready.await(5, TimeUnit.SECONDS) && current() && !closed) { "Headset connection timed out or was cancelled. Select the input and retry" }
        waiting = null
    }
    fun close(): String? {
        cancel()
        var failure: String? = null
        fun attempt(action: () -> Unit) { try { action() } catch (error: RuntimeException) { failure = failure ?: "Microphone route cleanup failed: ${error.message}. Stop and retry" } }
        if (Build.VERSION.SDK_INT >= 31) {
            communicationListener?.let { listener -> attempt { manager.removeOnCommunicationDeviceChangedListener(listener); communicationListener = null } }
            if (communication) attempt { manager.clearCommunicationDevice(); communication = false }
        }
        receiver?.let { listener -> attempt { context.unregisterReceiver(listener); receiver = null } }
        if (sco) attempt { manager.stopBluetoothSco(); sco = false }
        if (mode) attempt { manager.mode = AudioManager.MODE_NORMAL; mode = false }
        return failure
    }
}
