package dev.phosphor.mobil3.state

sealed interface CanonicalValue

data class CanonicalBoolean(val value: Boolean) : CanonicalValue
data class CanonicalFloatBits(val bits: Int) : CanonicalValue
data class CanonicalInt(val value: Int) : CanonicalValue
data class CanonicalLong(val value: Long) : CanonicalValue
data class CanonicalString(val value: String) : CanonicalValue
data class CanonicalColorList(val colors: FrozenList<RgbaColor>) : CanonicalValue {
    constructor(colors: Iterable<RgbaColor>) : this(FrozenList.copyOf(colors))
}

/** Structural payload used for idempotency comparison without delimiter or locale ambiguity. */
data class CanonicalPayload(
    val actionName: String,
    val arguments: FrozenList<Pair<String, CanonicalValue>>,
) {
    init {
        require(actionName.isNotBlank()) { "canonical action name must be nonblank" }
        require(arguments.map { it.first }.distinct().size == arguments.size) {
            "canonical argument names must be unique"
        }
        require(arguments == arguments.sortedBy { it.first }) {
            "canonical arguments must be sorted"
        }
    }

    companion object {
        fun of(actionName: String, vararg arguments: Pair<String, CanonicalValue>): CanonicalPayload =
            CanonicalPayload(actionName, FrozenList.copyOf(arguments.sortedBy { it.first }))
    }
}

enum class PrincipalPolicy {
    AUTHORIZED,
    HUMAN_ONLY,
}

enum class ActionType(
    val wireName: String,
    val requiredCapability: Capability,
    affectedFields: Set<StateField>,
    val opensHumanFlow: Boolean = false,
    val principalPolicy: PrincipalPolicy = PrincipalPolicy.AUTHORIZED,
) {
    TOGGLE_PLAYBACK("transport.play_pause", Capability.CONTROL_TRANSPORT, setOf(StateField.PLAYBACK_ACTIVE)),
    REQUEST_OPEN_FILE("source.file.open", Capability.CONTROL_TRANSPORT, setOf(StateField.SOURCE_KIND), true),
    REQUEST_SETTINGS_EXPORT("settings.export", Capability.CONTROL_SETTINGS, emptySet(), true),
    REQUEST_SETTINGS_IMPORT("settings.import", Capability.CONTROL_SETTINGS, StateField.entries.toSet(), true),
    REQUEST_MICROPHONE("source.mic.start", Capability.CONTROL_TRANSPORT, setOf(StateField.SOURCE_KIND), true),
    REQUEST_PLAYBACK_CAPTURE("source.capture.request", Capability.REQUEST_PERMISSIONS, setOf(StateField.SOURCE_KIND), true),
    STOP_LIVE_SOURCE("source.live.stop", Capability.CONTROL_TRANSPORT, setOf(StateField.SOURCE_KIND)),
    NEXT_TRACK("transport.next", Capability.CONTROL_TRANSPORT, setOf(StateField.QUEUE_INDEX)),
    PREVIOUS_TRACK("transport.prev", Capability.CONTROL_TRANSPORT, setOf(StateField.QUEUE_INDEX)),
    SEEK("transport.seek", Capability.CONTROL_TRANSPORT, setOf(StateField.POSITION_MILLIS)),
    REQUEST_REMOTE_SOURCE("transport.remote.start", Capability.CONTROL_TRANSPORT, setOf(StateField.SOURCE_KIND)),
    SET_SCOPE_MODE("scope.mode.set", Capability.CONTROL_SCOPE, setOf(StateField.SCOPE_MODE)),
    SET_BEAM_PRESET("scope.beam.set", Capability.CONTROL_SCOPE, setOf(StateField.BEAM_PRESET)),
    SET_FRAME_RATE("scope.fps.set", Capability.CONTROL_SCOPE, setOf(StateField.FRAME_RATE)),
    SET_OVERSAMPLE("scope.oversample.set", Capability.CONTROL_SCOPE, setOf(StateField.OVERSAMPLE)),
    SET_ACTIVE_THEME("theme.active.set", Capability.CONTROL_THEMES, setOf(StateField.ACTIVE_THEME_ID)),
    SET_FOCUS("scope.focus.set", Capability.CONTROL_SCOPE, setOf(StateField.FOCUS)),
    SET_CUSTOM_BEAM("scope.beam.custom.set", Capability.CONTROL_SCOPE, setOf(StateField.CUSTOM_BEAM)),
    SET_BEAM_CYCLE(
        "scope.beam.cycle.set",
        Capability.CONTROL_SCOPE,
        setOf(StateField.BEAM_CYCLE_SECONDS, StateField.BEAM_CYCLE_PER_TRACK),
    ),
    SET_BEAM_ENERGY("scope.beam.energy.set", Capability.CONTROL_SCOPE, setOf(StateField.BEAM_ENERGY)),
    SET_GLOW("scope.glow.set", Capability.CONTROL_SCOPE, setOf(StateField.GLOW)),
    RANDOMIZE_BEAM("scope.beam.random.tap", Capability.CONTROL_SCOPE, setOf(StateField.BEAM_RANDOM_GENERATION)),
    SET_BEAM_RANDOM_RANGE("scope.beam.random_range.set", Capability.CONTROL_SCOPE, setOf(StateField.BEAM_RANDOM_RANGE)),
    RANDOMIZE_GLOW("scope.glow.random.tap", Capability.CONTROL_SCOPE, setOf(StateField.GLOW_RANDOM_GENERATION)),
    SET_GLOW_RANDOM_RANGE("scope.glow.random_range.set", Capability.CONTROL_SCOPE, setOf(StateField.GLOW_RANDOM_RANGE)),
    SET_GEOMETRY_EFFECT("scope.geometry_fx.set", Capability.CONTROL_SCOPE, setOf(StateField.GEOMETRY_EFFECT)),
    SET_GEOMETRY_AMOUNT("scope.geometry_amount.set", Capability.CONTROL_SCOPE, setOf(StateField.GEOMETRY_AMOUNT)),
    SET_GRID("scope.grid.set", Capability.CONTROL_SCOPE, setOf(StateField.GRID_ENABLED)),
    SET_AUTO_GAIN("scope.gain_auto.set", Capability.CONTROL_SCOPE, setOf(StateField.AUTO_GAIN_ENABLED)),
    SET_VIEW_LOCK("scope.view_lock.set", Capability.CONTROL_SCOPE, setOf(StateField.VIEW_LOCKED)),
    SET_DISPLAY_HUD("display.hud.set", Capability.CONTROL_DISPLAY, setOf(StateField.DISPLAY_HUD)),
    SET_FULLSCREEN("display.fullscreen.set", Capability.CONTROL_DISPLAY, setOf(StateField.FULLSCREEN)),
    OPEN_CAPTURE_METADATA_SETTINGS("permission.open", Capability.REQUEST_PERMISSIONS, emptySet(), true),
    MARK_BESTIARY_FOUND("diagnostic.bestiary_found.set", Capability.CONTROL_SETTINGS, setOf(StateField.BESTIARY_FOUND)),
    SET_SCOPE_ROTATION_LOCK("rotation.scope_lock.set", Capability.CONTROL_DISPLAY, setOf(StateField.SCOPE_ROTATION_LOCKED)),
    SET_UI_PLACEMENT_LOCK("rotation.ui_lock.set", Capability.CONTROL_DISPLAY, setOf(StateField.UI_PLACEMENT_LOCKED)),
    SET_REMOTE_LATENCY_MODE("remote.latency_mode.set", Capability.CONTROL_TRANSPORT, setOf(StateField.REMOTE_LATENCY_MODE)),
    SET_REMOTE_NETWORK_MODE("remote.network_mode.set", Capability.CONTROL_TRANSPORT, setOf(StateField.REMOTE_NETWORK_MODE)),
    START_REMOTE_HOST(
        "remote.host.start",
        Capability.CONTROL_TRANSPORT,
        setOf(StateField.SOURCE_KIND, StateField.REMOTE_HOST_ID),
    ),
    SET_REMOTE_STREAMS(
        "remote.streams.set",
        Capability.CONTROL_TRANSPORT,
        setOf(StateField.REMOTE_AUDIO_ENABLED, StateField.REMOTE_GEOMETRY_ENABLED),
    ),
    DISCONNECT_REMOTE(
        "remote.disconnect",
        Capability.CONTROL_TRANSPORT,
        setOf(StateField.SOURCE_KIND, StateField.REMOTE_HOST_ID),
    ),
    CONFIRM_EPILEPSY_SAFETY(
        "safety.epilepsy.confirm",
        Capability.CONTROL_SETTINGS,
        setOf(StateField.EPILEPSY_ACKNOWLEDGED),
        principalPolicy = PrincipalPolicy.HUMAN_ONLY,
    ),
    SET_SCOPE_GAIN("scope.gain.set", Capability.CONTROL_SCOPE, setOf(StateField.SCOPE_GAIN)),
    ORBIT_SCOPE("scope.orbit.by", Capability.CONTROL_SCOPE, setOf(StateField.ORBIT_YAW, StateField.ORBIT_PITCH)),
    DOLLY_SCOPE("scope.dolly.by", Capability.CONTROL_SCOPE, setOf(StateField.DOLLY)),
    REQUEST_OPEN_FOLDER("source.folder.open", Capability.CONTROL_TRANSPORT, setOf(StateField.SOURCE_KIND), true),
    JUMP_TO_QUEUE("transport.queue.jump", Capability.CONTROL_TRANSPORT, setOf(StateField.QUEUE_INDEX)),
    SET_VOLUME("transport.volume.set", Capability.CONTROL_TRANSPORT, setOf(StateField.VOLUME_FRACTION)),
    ;

    val affectedFields: FrozenSet<StateField> = FrozenSet.copyOf(affectedFields)
}

sealed interface PhosphorAction {
    val type: ActionType
    fun canonicalPayload(): CanonicalPayload
    fun validate(): Refusal?
}

/**
 * Exact requested values for actions whose result is a direct assignment.
 *
 * Relative, generated, lifecycle, transport-handshake, and human-flow actions return null because
 * their accepted values require reduction against current or external state. AcceptedActionRecord
 * uses this projection to prevent a receipt from accepting different values than the typed request.
 */
fun PhosphorAction.directStateIntent(): FrozenMap<StateField, StateValue>? = when (this) {
    TogglePlayback,
    RequestOpenFile,
    RequestSettingsExport,
    RequestSettingsImport,
    RequestMicrophoneSource,
    RequestPlaybackCapture,
    StopLiveSource,
    NextTrack,
    PreviousTrack,
    RequestRemoteSource,
    RandomizeBeam,
    RandomizeGlow,
    OpenCaptureMetadataSettings,
    is StartRemoteHost,
    DisconnectRemote,
    is OrbitScope,
    is DollyScope,
    RequestOpenFolder,
    -> null

    is SeekTo -> frozenMapOf(StateField.POSITION_MILLIS to LongStateValue(positionMillis))
    is SetScopeMode -> frozenMapOf(StateField.SCOPE_MODE to IntStateValue(index))
    is SetBeamPreset -> frozenMapOf(StateField.BEAM_PRESET to IntStateValue(index))
    is SetFrameRate -> frozenMapOf(StateField.FRAME_RATE to IntStateValue(framesPerSecond))
    is SetOversample -> frozenMapOf(StateField.OVERSAMPLE to IntStateValue(factor))
    is SetActiveThemeId -> frozenMapOf(StateField.ACTIVE_THEME_ID to StringStateValue(themeId))
    is SetFocus -> frozenMapOf(StateField.FOCUS to FloatStateValue(focus))
    is SetCustomBeam -> frozenMapOf(StateField.CUSTOM_BEAM to BeamGradientStateValue(gradient))
    is SetBeamCycle -> frozenMapOf(
        StateField.BEAM_CYCLE_SECONDS to FloatStateValue(seconds),
        StateField.BEAM_CYCLE_PER_TRACK to BooleanStateValue(perTrack),
    )
    is SetBeamEnergy -> frozenMapOf(StateField.BEAM_ENERGY to FloatStateValue(energy))
    is SetGlow -> frozenMapOf(StateField.GLOW to FloatStateValue(glow))
    is SetBeamRandomRange -> frozenMapOf(StateField.BEAM_RANDOM_RANGE to FloatRangeStateValue(range))
    is SetGlowRandomRange -> frozenMapOf(StateField.GLOW_RANDOM_RANGE to FloatRangeStateValue(range))
    is SetGeometryEffect -> frozenMapOf(StateField.GEOMETRY_EFFECT to IntStateValue(kind))
    is SetGeometryAmount -> frozenMapOf(StateField.GEOMETRY_AMOUNT to FloatStateValue(amount))
    is SetGridEnabled -> frozenMapOf(StateField.GRID_ENABLED to BooleanStateValue(enabled))
    is SetAutoGain -> frozenMapOf(StateField.AUTO_GAIN_ENABLED to BooleanStateValue(enabled))
    is SetViewLock -> frozenMapOf(StateField.VIEW_LOCKED to BooleanStateValue(locked))
    is SetDisplayHud -> frozenMapOf(StateField.DISPLAY_HUD to StringStateValue(mode))
    is SetFullscreen -> frozenMapOf(StateField.FULLSCREEN to BooleanStateValue(enabled))
    MarkBestiaryFound -> frozenMapOf(StateField.BESTIARY_FOUND to BooleanStateValue(true))
    is SetScopeRotationLock -> frozenMapOf(StateField.SCOPE_ROTATION_LOCKED to BooleanStateValue(locked))
    is SetUiPlacementLock -> frozenMapOf(StateField.UI_PLACEMENT_LOCKED to BooleanStateValue(locked))
    is SetRemoteLatencyMode -> frozenMapOf(StateField.REMOTE_LATENCY_MODE to IntStateValue(mode))
    is SetRemoteNetworkMode -> frozenMapOf(StateField.REMOTE_NETWORK_MODE to IntStateValue(mode))
    is SetRemoteStreams -> frozenMapOf(
        StateField.REMOTE_AUDIO_ENABLED to BooleanStateValue(audio),
        StateField.REMOTE_GEOMETRY_ENABLED to BooleanStateValue(geometry),
    )
    ConfirmEpilepsySafety -> frozenMapOf(StateField.EPILEPSY_ACKNOWLEDGED to BooleanStateValue(true))
    is SetScopeGain -> frozenMapOf(StateField.SCOPE_GAIN to FloatStateValue(gain))
    is JumpToQueue -> frozenMapOf(StateField.QUEUE_INDEX to IntStateValue(index))
    is SetVolume -> frozenMapOf(StateField.VOLUME_FRACTION to FloatStateValue(fraction))
}

private fun noPayload(type: ActionType): CanonicalPayload = CanonicalPayload.of(type.wireName)

private fun invalid(message: String): Refusal = Refusal(RefusalCode.INVALID_VALUE, message)

object TogglePlayback : PhosphorAction {
    override val type = ActionType.TOGGLE_PLAYBACK
    override fun canonicalPayload() = noPayload(type)
    override fun validate(): Refusal? = null
}

object RequestOpenFile : PhosphorAction {
    override val type = ActionType.REQUEST_OPEN_FILE
    override fun canonicalPayload() = noPayload(type)
    override fun validate(): Refusal? = null
}

object RequestSettingsExport : PhosphorAction {
    override val type = ActionType.REQUEST_SETTINGS_EXPORT
    override fun canonicalPayload() = noPayload(type)
    override fun validate(): Refusal? = null
}

object RequestSettingsImport : PhosphorAction {
    override val type = ActionType.REQUEST_SETTINGS_IMPORT
    override fun canonicalPayload() = noPayload(type)
    override fun validate(): Refusal? = null
}

object RequestMicrophoneSource : PhosphorAction {
    override val type = ActionType.REQUEST_MICROPHONE
    override fun canonicalPayload() = noPayload(type)
    override fun validate(): Refusal? = null
}

object RequestPlaybackCapture : PhosphorAction {
    override val type = ActionType.REQUEST_PLAYBACK_CAPTURE
    override fun canonicalPayload() = noPayload(type)
    override fun validate(): Refusal? = null
}

object StopLiveSource : PhosphorAction {
    override val type = ActionType.STOP_LIVE_SOURCE
    override fun canonicalPayload() = noPayload(type)
    override fun validate(): Refusal? = null
}

object NextTrack : PhosphorAction {
    override val type = ActionType.NEXT_TRACK
    override fun canonicalPayload() = noPayload(type)
    override fun validate(): Refusal? = null
}

object PreviousTrack : PhosphorAction {
    override val type = ActionType.PREVIOUS_TRACK
    override fun canonicalPayload() = noPayload(type)
    override fun validate(): Refusal? = null
}

data class SeekTo(val positionMillis: Long) : PhosphorAction {
    override val type = ActionType.SEEK
    override fun canonicalPayload() = CanonicalPayload.of(type.wireName, "position_millis" to CanonicalLong(positionMillis))
    override fun validate() = if (positionMillis >= 0L) null else invalid("Use a non-negative playback position.")
}

object RequestRemoteSource : PhosphorAction {
    override val type = ActionType.REQUEST_REMOTE_SOURCE
    override fun canonicalPayload() = noPayload(type)
    override fun validate(): Refusal? = null
}

data class SetScopeMode(val index: Int) : PhosphorAction {
    override val type = ActionType.SET_SCOPE_MODE
    override fun canonicalPayload() = CanonicalPayload.of(type.wireName, "index" to CanonicalInt(index))
    override fun validate() = if (index >= 0) null else invalid("Use a non-negative scope mode index.")
}

data class SetBeamPreset(val index: Int) : PhosphorAction {
    override val type = ActionType.SET_BEAM_PRESET
    override fun canonicalPayload() = CanonicalPayload.of(type.wireName, "index" to CanonicalInt(index))
    override fun validate() = if (index >= 0) null else invalid("Use a non-negative beam preset index.")
}

data class SetFrameRate(val framesPerSecond: Int) : PhosphorAction {
    override val type = ActionType.SET_FRAME_RATE
    override fun canonicalPayload() = CanonicalPayload.of(type.wireName, "fps" to CanonicalInt(framesPerSecond))
    override fun validate() = if (framesPerSecond in 15..240) null else invalid("Use a frame rate from 15 through 240.")
}

data class SetOversample(val factor: Int) : PhosphorAction {
    override val type = ActionType.SET_OVERSAMPLE
    override fun canonicalPayload() = CanonicalPayload.of(type.wireName, "factor" to CanonicalInt(factor))
    override fun validate() = if (factor in setOf(1, 2, 4)) null else invalid("Use oversample factor 1, 2, or 4.")
}

data class SetActiveThemeId(val themeId: String) : PhosphorAction {
    override val type = ActionType.SET_ACTIVE_THEME
    override fun canonicalPayload() = CanonicalPayload.of(type.wireName, "theme_id" to CanonicalString(themeId))
    override fun validate() = if (THEME_ID.matches(themeId)) null else invalid(
        "Use a lowercase theme id containing letters, numbers, dot, underscore, or hyphen.",
    )
}

data class SetFocus(val focus: Float) : PhosphorAction {
    override val type = ActionType.SET_FOCUS
    override fun canonicalPayload() = CanonicalPayload.of(type.wireName, "focus" to CanonicalFloatBits(focus.toBits()))
    override fun validate() = finiteRange(focus, 0.0f, 4.0f, "focus")
}

data class SetCustomBeam(val gradient: BeamGradient) : PhosphorAction {
    override val type = ActionType.SET_CUSTOM_BEAM
    override fun canonicalPayload() = CanonicalPayload.of(
        type.wireName,
        "active_count" to CanonicalInt(gradient.activeCount),
        "colors" to CanonicalColorList(gradient.colors),
    )
    override fun validate() = if (gradient.colors.size <= 256) null else invalid("Use at most 256 beam color stops.")
}

data class SetBeamCycle(val seconds: Float, val perTrack: Boolean) : PhosphorAction {
    override val type = ActionType.SET_BEAM_CYCLE
    override fun canonicalPayload() = CanonicalPayload.of(
        type.wireName,
        "per_track" to CanonicalBoolean(perTrack),
        "seconds" to CanonicalFloatBits(seconds.toBits()),
    )
    override fun validate() = finiteRange(seconds, 0.0f, 3600.0f, "beam cycle seconds")
}

data class SetBeamEnergy(val energy: Float) : PhosphorAction {
    override val type = ActionType.SET_BEAM_ENERGY
    override fun canonicalPayload() = CanonicalPayload.of(type.wireName, "energy" to CanonicalFloatBits(energy.toBits()))
    override fun validate() = finiteRange(energy, 0.0f, 4.0f, "beam energy")
}

data class SetGlow(val glow: Float) : PhosphorAction {
    override val type = ActionType.SET_GLOW
    override fun canonicalPayload() = CanonicalPayload.of(type.wireName, "glow" to CanonicalFloatBits(glow.toBits()))
    override fun validate() = finiteRange(glow, 0.0f, 4.0f, "glow")
}

object RandomizeBeam : PhosphorAction {
    override val type = ActionType.RANDOMIZE_BEAM
    override fun canonicalPayload() = noPayload(type)
    override fun validate(): Refusal? = null
}

data class SetBeamRandomRange(val range: FloatRangeValue) : PhosphorAction {
    override val type = ActionType.SET_BEAM_RANDOM_RANGE
    override fun canonicalPayload() = rangePayload(type, range)
    override fun validate() = boundedUnitRange(range, "beam random range")
}

object RandomizeGlow : PhosphorAction {
    override val type = ActionType.RANDOMIZE_GLOW
    override fun canonicalPayload() = noPayload(type)
    override fun validate(): Refusal? = null
}

data class SetGlowRandomRange(val range: FloatRangeValue) : PhosphorAction {
    override val type = ActionType.SET_GLOW_RANDOM_RANGE
    override fun canonicalPayload() = rangePayload(type, range)
    override fun validate() = boundedUnitRange(range, "glow random range")
}

data class SetGeometryEffect(val kind: Int) : PhosphorAction {
    override val type = ActionType.SET_GEOMETRY_EFFECT
    override fun canonicalPayload() = CanonicalPayload.of(type.wireName, "kind" to CanonicalInt(kind))
    override fun validate() = if (kind >= 0) null else invalid("Use a non-negative geometry effect index.")
}

data class SetGeometryAmount(val amount: Float) : PhosphorAction {
    override val type = ActionType.SET_GEOMETRY_AMOUNT
    override fun canonicalPayload() = CanonicalPayload.of(type.wireName, "amount" to CanonicalFloatBits(amount.toBits()))
    override fun validate() = finiteRange(amount, 0.0f, 1.0f, "geometry amount")
}

data class SetGridEnabled(val enabled: Boolean) : PhosphorAction {
    override val type = ActionType.SET_GRID
    override fun canonicalPayload() = CanonicalPayload.of(type.wireName, "enabled" to CanonicalBoolean(enabled))
    override fun validate(): Refusal? = null
}

data class SetAutoGain(val enabled: Boolean) : PhosphorAction {
    override val type = ActionType.SET_AUTO_GAIN
    override fun canonicalPayload() = CanonicalPayload.of(type.wireName, "enabled" to CanonicalBoolean(enabled))
    override fun validate(): Refusal? = null
}

data class SetViewLock(val locked: Boolean) : PhosphorAction {
    override val type = ActionType.SET_VIEW_LOCK
    override fun canonicalPayload() = CanonicalPayload.of(type.wireName, "locked" to CanonicalBoolean(locked))
    override fun validate(): Refusal? = null
}

data class SetDisplayHud(val mode: String) : PhosphorAction {
    override val type = ActionType.SET_DISPLAY_HUD
    override fun canonicalPayload() = CanonicalPayload.of(type.wireName, "mode" to CanonicalString(mode))
    override fun validate() = if (mode in setOf("auto", "on", "off")) null else invalid(
        "Use display.hud mode auto, on, or off.",
    )
}

data class SetFullscreen(val enabled: Boolean) : PhosphorAction {
    override val type = ActionType.SET_FULLSCREEN
    override fun canonicalPayload() = CanonicalPayload.of(type.wireName, "enabled" to CanonicalBoolean(enabled))
    override fun validate(): Refusal? = null
}

object OpenCaptureMetadataSettings : PhosphorAction {
    override val type = ActionType.OPEN_CAPTURE_METADATA_SETTINGS
    override fun canonicalPayload() = noPayload(type)
    override fun validate(): Refusal? = null
}

object MarkBestiaryFound : PhosphorAction {
    override val type = ActionType.MARK_BESTIARY_FOUND
    override fun canonicalPayload() = noPayload(type)
    override fun validate(): Refusal? = null
}

data class SetScopeRotationLock(val locked: Boolean) : PhosphorAction {
    override val type = ActionType.SET_SCOPE_ROTATION_LOCK
    override fun canonicalPayload() = CanonicalPayload.of(type.wireName, "locked" to CanonicalBoolean(locked))
    override fun validate(): Refusal? = null
}

data class SetUiPlacementLock(val locked: Boolean) : PhosphorAction {
    override val type = ActionType.SET_UI_PLACEMENT_LOCK
    override fun canonicalPayload() = CanonicalPayload.of(type.wireName, "locked" to CanonicalBoolean(locked))
    override fun validate(): Refusal? = null
}

data class SetRemoteLatencyMode(val mode: Int) : PhosphorAction {
    override val type = ActionType.SET_REMOTE_LATENCY_MODE
    override fun canonicalPayload() = CanonicalPayload.of(type.wireName, "mode" to CanonicalInt(mode))
    override fun validate() = if (mode in 0..2) null else invalid("Use remote latency mode 0, 1, or 2.")
}

data class SetRemoteNetworkMode(val mode: Int) : PhosphorAction {
    override val type = ActionType.SET_REMOTE_NETWORK_MODE
    override fun canonicalPayload() = CanonicalPayload.of(type.wireName, "mode" to CanonicalInt(mode))
    override fun validate() = if (mode in 0..2) null else invalid("Use remote network mode 0, 1, or 2.")
}

data class StartRemoteHost(val label: String, val host: String, val port: Int) : PhosphorAction {
    override val type = ActionType.START_REMOTE_HOST
    override fun canonicalPayload() = CanonicalPayload.of(
        type.wireName,
        "host" to CanonicalString(host),
        "label" to CanonicalString(label),
        "port" to CanonicalInt(port),
    )
    override fun validate(): Refusal? = when {
        label.isBlank() -> invalid("Remote host label must be nonblank.")
        host.isBlank() -> invalid("Remote host address must be nonblank.")
        port !in 1..65535 -> invalid("Remote host port must be from 1 through 65535.")
        else -> null
    }
}

data class SetRemoteStreams(val audio: Boolean, val geometry: Boolean) : PhosphorAction {
    override val type = ActionType.SET_REMOTE_STREAMS
    override fun canonicalPayload() = CanonicalPayload.of(
        type.wireName,
        "audio" to CanonicalBoolean(audio),
        "geometry" to CanonicalBoolean(geometry),
    )
    override fun validate() = if (audio || geometry) null else invalid("Keep at least one remote stream enabled.")
}

object DisconnectRemote : PhosphorAction {
    override val type = ActionType.DISCONNECT_REMOTE
    override fun canonicalPayload() = noPayload(type)
    override fun validate(): Refusal? = null
}

object ConfirmEpilepsySafety : PhosphorAction {
    override val type = ActionType.CONFIRM_EPILEPSY_SAFETY
    override fun canonicalPayload() = noPayload(type)
    override fun validate(): Refusal? = null
}

data class SetScopeGain(val gain: Float) : PhosphorAction {
    override val type = ActionType.SET_SCOPE_GAIN
    override fun canonicalPayload() = CanonicalPayload.of(type.wireName, "gain" to CanonicalFloatBits(gain.toBits()))
    override fun validate() = finiteRange(gain, 0.0f, 4.0f, "scope gain")
}

data class OrbitScope(val deltaYaw: Float, val deltaPitch: Float) : PhosphorAction {
    override val type = ActionType.ORBIT_SCOPE
    override fun canonicalPayload() = CanonicalPayload.of(
        type.wireName,
        "delta_pitch" to CanonicalFloatBits(deltaPitch.toBits()),
        "delta_yaw" to CanonicalFloatBits(deltaYaw.toBits()),
    )
    override fun validate() = when {
        !deltaYaw.isFinite() || !deltaPitch.isFinite() -> invalid("Orbit deltas must be finite.")
        else -> null
    }
}

data class DollyScope(val delta: Float) : PhosphorAction {
    override val type = ActionType.DOLLY_SCOPE
    override fun canonicalPayload() = CanonicalPayload.of(type.wireName, "delta" to CanonicalFloatBits(delta.toBits()))
    override fun validate() = if (delta.isFinite()) null else invalid("Dolly delta must be finite.")
}

object RequestOpenFolder : PhosphorAction {
    override val type = ActionType.REQUEST_OPEN_FOLDER
    override fun canonicalPayload() = noPayload(type)
    override fun validate(): Refusal? = null
}

data class JumpToQueue(val index: Int) : PhosphorAction {
    override val type = ActionType.JUMP_TO_QUEUE
    override fun canonicalPayload() = CanonicalPayload.of(type.wireName, "index" to CanonicalInt(index))
    override fun validate() = if (index >= 0) null else invalid("Use a non-negative queue index.")
}

data class SetVolume(val fraction: Float) : PhosphorAction {
    override val type = ActionType.SET_VOLUME
    override fun canonicalPayload() = CanonicalPayload.of(type.wireName, "fraction" to CanonicalFloatBits(fraction.toBits()))
    override fun validate() = finiteRange(fraction, 0.0f, 1.0f, "volume fraction")
}

private val THEME_ID = Regex("[a-z0-9][a-z0-9._-]{1,63}")

private fun finiteRange(value: Float, minimum: Float, maximum: Float, label: String): Refusal? =
    if (value.isFinite() && value in minimum..maximum) {
        null
    } else {
        invalid("Use $label from $minimum through $maximum.")
    }

private fun rangePayload(type: ActionType, range: FloatRangeValue): CanonicalPayload = CanonicalPayload.of(
    type.wireName,
    "maximum" to CanonicalFloatBits(range.maximum.toBits()),
    "minimum" to CanonicalFloatBits(range.minimum.toBits()),
)

private fun boundedUnitRange(range: FloatRangeValue, label: String): Refusal? =
    if (range.minimum >= 0.0f && range.maximum <= 1.0f) {
        null
    } else {
        invalid("Use $label within 0.0 through 1.0.")
    }
