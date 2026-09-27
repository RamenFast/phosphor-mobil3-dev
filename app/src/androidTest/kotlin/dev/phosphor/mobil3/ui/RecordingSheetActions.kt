package dev.phosphor.mobil3.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.lang.reflect.Proxy

/**
 * Only the activity boundary is replaced. Production composables own hit testing, gestures,
 * navigation within sheets, layout and choice policy. Unexpected actions fail rather than pass.
 * No native engine, microphone, network, persisted settings or permissions are touched.
 */
internal class RecordingSheetActions(
    private val state: ScopeUiState,
    private val manual: () -> Unit = {},
) : SheetActions by unsupportedActions() {
    val calls = mutableListOf<String>()
    var scopeLocked by mutableStateOf(false)
    var keysLocked by mutableStateOf(false)
    override fun remoteHosts(): List<Pair<String, Pair<String, Int>>> = emptyList()
    override fun captureConsentNeeded() = false
    override fun isScopeRotationLocked() = scopeLocked
    override fun isUiPlacementLocked() = keysLocked
    override fun cancelAppearancePreview() = Unit
    override fun openManual() = manual()
    override fun setGainAuto(on: Boolean) { calls += "auto:$on"; state.autoGain = on }
    override fun setViewLock(on: Boolean) { calls += "view:$on"; state.viewLock = on }
    override fun setPipAutoEnter(on: Boolean) { calls += "pip:$on"; state.pipAutoEnter = on }
    override fun setDeveloperView(on: Boolean) { state.developerView = on }
    override fun chooseAndStartMicrophone(id: Int) {
        calls += "chooseAndStart:$id"
        state.selectedMicrophone = id
        state.sourceLabel = "mic"
        state.live = true
        state.microphoneActive = true
    }
    override fun chooseMicrophone(id: Int) { calls += "choose:$id"; state.selectedMicrophone = id }
    override fun startMic() { calls += "start"; state.live = true; state.sourceLabel = "mic" }
    override fun stopMicrophone() { calls += "stop"; state.live = false; state.microphoneActive = false }
}

private fun unsupportedActions(): SheetActions = Proxy.newProxyInstance(
    SheetActions::class.java.classLoader, arrayOf(SheetActions::class.java),
) { proxy, method, args ->
    when (method.name) {
        "equals" -> proxy === args?.firstOrNull()
        "hashCode" -> System.identityHashCode(proxy)
        "toString" -> "Unsupported test action"
        else -> error("Unexpected SheetActions.${method.name}. Add an explicit recording boundary for this test.")
    }
} as SheetActions
