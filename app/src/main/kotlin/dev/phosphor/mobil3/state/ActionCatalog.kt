package dev.phosphor.mobil3.state

sealed interface ScopeControlBinding {
    val scopeActionMethod: String
}

data class TypedActionBinding(
    override val scopeActionMethod: String,
    val actionType: ActionType,
) : ScopeControlBinding

data class NonAgentExceptionBinding(
    override val scopeActionMethod: String,
    val reason: String,
) : ScopeControlBinding {
    init {
        require(reason.isNotBlank()) { "non-agent exceptions require a reason" }
    }
}

/**
 * Exact Phase 03 projection of the current `ScopeActions` interface.
 *
 * This catalog is inert. It does not dispatch, mutate, observe, or adapt Android values.
 */
object StateActionCatalog {
    val entries: FrozenList<ScopeControlBinding> = FrozenList.copyOf(listOf(
        action("togglePlay", ActionType.TOGGLE_PLAYBACK),
        action("openFile", ActionType.REQUEST_OPEN_FILE),
        action("exportSettings", ActionType.REQUEST_SETTINGS_EXPORT),
        action("importSettings", ActionType.REQUEST_SETTINGS_IMPORT),
        action("startMic", ActionType.REQUEST_MICROPHONE),
        action("startCapture", ActionType.REQUEST_PLAYBACK_CAPTURE),
        action("stopLive", ActionType.STOP_LIVE_SOURCE),
        exception("captureConsentNeeded", "read helper; an agent cannot forge Android consent"),
        action("next", ActionType.NEXT_TRACK),
        action("prev", ActionType.PREVIOUS_TRACK),
        action("seekTo", ActionType.SEEK),
        action("startRemote", ActionType.REQUEST_REMOTE_SOURCE),
        action("setMode", ActionType.SET_SCOPE_MODE),
        action("setBeam", ActionType.SET_BEAM_PRESET),
        action("setFps", ActionType.SET_FRAME_RATE),
        action("setOversample", ActionType.SET_OVERSAMPLE),
        action("setRoom", ActionType.SET_ACTIVE_THEME),
        action("setFocus", ActionType.SET_FOCUS),
        action("setCustomBeam", ActionType.SET_CUSTOM_BEAM),
        action("setBeamCycle", ActionType.SET_BEAM_CYCLE),
        action("setBeamEnergy", ActionType.SET_BEAM_ENERGY),
        action("setGlow", ActionType.SET_GLOW),
        action("tapBeamRandom", ActionType.RANDOMIZE_BEAM),
        action("setBeamRandomRange", ActionType.SET_BEAM_RANDOM_RANGE),
        action("tapGlowRandom", ActionType.RANDOMIZE_GLOW),
        action("setGlowRandomRange", ActionType.SET_GLOW_RANDOM_RANGE),
        action("setGeomFx", ActionType.SET_GEOMETRY_EFFECT),
        action("setGeomAmount", ActionType.SET_GEOMETRY_AMOUNT),
        action("setGrid", ActionType.SET_GRID),
        action("setGainAuto", ActionType.SET_AUTO_GAIN),
        action("setViewLock", ActionType.SET_VIEW_LOCK),
        action("setHudMode", ActionType.SET_DISPLAY_HUD),
        action("setFullscreen", ActionType.SET_FULLSCREEN),
        action("openCaptureMetadataSettings", ActionType.OPEN_CAPTURE_METADATA_SETTINGS),
        exception("openLink", "external URL opening remains an explicit human/system intent"),
        action("markBestiaryFound", ActionType.MARK_BESTIARY_FOUND),
        exception("isScopeRotationLocked", "read helper for Android rotation authority"),
        action("setScopeRotationLocked", ActionType.SET_SCOPE_ROTATION_LOCK),
        exception("isUiPlacementLocked", "read helper"),
        exception("lockedUiLandscape", "read helper"),
        action("setUiPlacementLocked", ActionType.SET_UI_PLACEMENT_LOCK),
        action("setRemoteLatencyMode", ActionType.SET_REMOTE_LATENCY_MODE),
        action("setRemoteNetworkMode", ActionType.SET_REMOTE_NETWORK_MODE),
        exception("remoteHosts", "read helper whose future state projection is not a mutation"),
        action("startRemoteHost", ActionType.START_REMOTE_HOST),
        action("setRemoteStreams", ActionType.SET_REMOTE_STREAMS),
        action("disconnectRemote", ActionType.DISCONNECT_REMOTE),
        exception("epilepsyAcknowledged", "read helper for a safety confirmation"),
        action("ackEpilepsy", ActionType.CONFIRM_EPILEPSY_SAFETY),
        action("setGainAbsolute", ActionType.SET_SCOPE_GAIN),
        action("orbitBy", ActionType.ORBIT_SCOPE),
        action("dollyBy", ActionType.DOLLY_SCOPE),
        action("openFolder", ActionType.REQUEST_OPEN_FOLDER),
        action("jumpToQueue", ActionType.JUMP_TO_QUEUE),
        exception("volumeFrac", "read helper"),
        action("setVolume", ActionType.SET_VOLUME),
        exception("makeSurface", "Android rendering adapter, not a user or agent action"),
    ))

    fun validate(expectedScopeMethods: Set<String>): List<String> = buildList {
        val methods = entries.map { it.scopeActionMethod }
        methods.groupingBy { it }.eachCount().filterValues { it != 1 }.keys.forEach {
            add("duplicate ScopeActions method $it")
        }

        val missing = expectedScopeMethods - methods.toSet()
        val unexpected = methods.toSet() - expectedScopeMethods
        missing.sorted().forEach { add("missing ScopeActions method $it") }
        unexpected.sorted().forEach { add("unexpected ScopeActions method $it") }

        val actionTypes = entries.filterIsInstance<TypedActionBinding>().map { it.actionType }
        actionTypes.groupingBy { it }.eachCount().filterValues { it != 1 }.keys.forEach {
            add("duplicate typed action ${it.wireName}")
        }

        (ActionType.entries.toSet() - actionTypes.toSet()).sortedBy { it.wireName }.forEach {
            add("unbound typed action ${it.wireName}")
        }
    }

    private fun action(method: String, type: ActionType): TypedActionBinding = TypedActionBinding(method, type)

    private fun exception(method: String, reason: String): NonAgentExceptionBinding =
        NonAgentExceptionBinding(method, reason)
}
