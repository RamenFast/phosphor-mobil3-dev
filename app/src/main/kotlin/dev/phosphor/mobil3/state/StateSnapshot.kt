package dev.phosphor.mobil3.state

data class RgbaColor(
    val red: UByte,
    val green: UByte,
    val blue: UByte,
    val alpha: UByte = UByte.MAX_VALUE,
)

data class FloatRangeValue(
    val minimum: Float,
    val maximum: Float,
) {
    init {
        require(minimum.isFinite() && maximum.isFinite()) { "range values must be finite" }
        require(minimum <= maximum) { "range minimum must not exceed maximum" }
    }
}

data class BeamGradient(
    val colors: FrozenList<RgbaColor>,
    val activeCount: Int,
) {
    constructor(colors: Iterable<RgbaColor>, activeCount: Int) : this(FrozenList.copyOf(colors), activeCount)

    init {
        require(colors.isNotEmpty()) { "a beam gradient requires at least one color" }
        require(activeCount in 1..colors.size) { "active beam count must address the supplied colors" }
    }
}

enum class StateValueKind {
    BOOLEAN,
    FLOAT,
    INT,
    LONG,
    STRING,
    NULLABLE_STRING,
    BEAM_GRADIENT,
    FLOAT_RANGE,
}

sealed interface StateValue {
    val kind: StateValueKind
}

data class BooleanStateValue(val value: Boolean) : StateValue {
    override val kind: StateValueKind = StateValueKind.BOOLEAN
}
data class FloatStateValue(val value: Float) : StateValue {
    override val kind: StateValueKind = StateValueKind.FLOAT

    init {
        require(value.isFinite()) { "state float must be finite" }
    }
}
data class IntStateValue(val value: Int) : StateValue {
    override val kind: StateValueKind = StateValueKind.INT
}
data class LongStateValue(val value: Long) : StateValue {
    override val kind: StateValueKind = StateValueKind.LONG
}
data class StringStateValue(val value: String) : StateValue {
    override val kind: StateValueKind = StateValueKind.STRING
}
data class NullableStringStateValue(val value: String?) : StateValue {
    override val kind: StateValueKind = StateValueKind.NULLABLE_STRING
}
data class BeamGradientStateValue(val value: BeamGradient) : StateValue {
    override val kind: StateValueKind = StateValueKind.BEAM_GRADIENT
}
data class FloatRangeStateValue(val value: FloatRangeValue) : StateValue {
    override val kind: StateValueKind = StateValueKind.FLOAT_RANGE
}

data class InstrumentSettings(
    val playbackActive: Boolean = false,
    val positionMillis: Long = 0L,
    val queueIndex: Int = 0,
    val volumeFraction: Float = 1.0f,
    val sourceKind: String = "none",
    val remoteHostId: String? = null,
    val remoteAudioEnabled: Boolean = true,
    val remoteGeometryEnabled: Boolean = true,
    val scopeMode: Int = 0,
    val beamPreset: Int = 0,
    val frameRate: Int = 120,
    val oversample: Int = 1,
    val activeThemeId: String = "blossom-dark",
    val focus: Float = 0.3f,
    val customBeam: BeamGradient = BeamGradient(listOf(RgbaColor(255u, 255u, 255u)), 1),
    val beamCycleSeconds: Float = 0.0f,
    val beamCyclePerTrack: Boolean = false,
    val beamEnergy: Float = 1.0f,
    val glow: Float = 1.0f,
    val beamRandomGeneration: Long = 0L,
    val beamRandomRange: FloatRangeValue = FloatRangeValue(0.0f, 1.0f),
    val glowRandomGeneration: Long = 0L,
    val glowRandomRange: FloatRangeValue = FloatRangeValue(0.0f, 1.0f),
    val geometryEffect: Int = 0,
    val geometryAmount: Float = 0.0f,
    val gridEnabled: Boolean = true,
    val autoGainEnabled: Boolean = false,
    val scopeGain: Float = 1.0f,
    val viewLocked: Boolean = false,
    val orbitYaw: Float = 0.0f,
    val orbitPitch: Float = 0.0f,
    val dolly: Float = 0.0f,
    val displayHud: String = "auto",
    val fullscreen: Boolean = false,
    val scopeRotationLocked: Boolean = false,
    val uiPlacementLocked: Boolean = false,
    val remoteLatencyMode: Int = 0,
    val remoteNetworkMode: Int = 0,
    val bestiaryFound: Boolean = false,
    val epilepsyAcknowledged: Boolean = false,
) {
    init {
        require(positionMillis >= 0L) { "playback position must not be negative" }
        require(queueIndex >= 0) { "queue index must not be negative" }
        require(volumeFraction.isFinite() && volumeFraction in 0.0f..1.0f) {
            "volume fraction must be normalized"
        }
        require(sourceKind.isNotBlank()) { "source kind must be nonblank" }
        require(scopeMode >= 0 && beamPreset >= 0) { "scope and beam indices must not be negative" }
        require(frameRate in 15..240) { "frame rate must be from 15 through 240" }
        require(oversample in setOf(1, 2, 4)) { "oversample must be 1, 2, or 4" }
        require(activeThemeId.matches(Regex("[a-z0-9][a-z0-9._-]{1,63}"))) { "invalid active theme id" }
        require(focus.isFinite() && focus in 0.0f..4.0f) { "focus must be from 0.0 through 4.0" }
        require(beamCycleSeconds.isFinite() && beamCycleSeconds in 0.0f..3600.0f) {
            "beam cycle seconds must be from 0.0 through 3600.0"
        }
        require(beamEnergy.isFinite() && beamEnergy in 0.0f..4.0f) { "beam energy must be from 0.0 through 4.0" }
        require(glow.isFinite() && glow in 0.0f..4.0f) { "glow must be from 0.0 through 4.0" }
        require(geometryEffect >= 0) { "geometry effect must not be negative" }
        require(geometryAmount.isFinite() && geometryAmount in 0.0f..1.0f) {
            "geometry amount must be normalized"
        }
        require(scopeGain.isFinite() && scopeGain in 0.0f..4.0f) { "scope gain must be from 0.0 through 4.0" }
        require(orbitYaw.isFinite() && orbitPitch.isFinite() && dolly.isFinite()) {
            "view transform values must be finite"
        }
        require(displayHud in setOf("auto", "on", "off")) { "display HUD must be auto, on, or off" }
        require(remoteLatencyMode in 0..2 && remoteNetworkMode in 0..2) {
            "remote modes must be from 0 through 2"
        }
    }
}

/** Typed projection shared by acceptance validation and future protocol projections. */
fun InstrumentSettings.valueOf(field: StateField): StateValue = when (field) {
    StateField.PLAYBACK_ACTIVE -> BooleanStateValue(playbackActive)
    StateField.POSITION_MILLIS -> LongStateValue(positionMillis)
    StateField.QUEUE_INDEX -> IntStateValue(queueIndex)
    StateField.VOLUME_FRACTION -> FloatStateValue(volumeFraction)
    StateField.SOURCE_KIND -> StringStateValue(sourceKind)
    StateField.REMOTE_HOST_ID -> NullableStringStateValue(remoteHostId)
    StateField.REMOTE_AUDIO_ENABLED -> BooleanStateValue(remoteAudioEnabled)
    StateField.REMOTE_GEOMETRY_ENABLED -> BooleanStateValue(remoteGeometryEnabled)
    StateField.SCOPE_MODE -> IntStateValue(scopeMode)
    StateField.BEAM_PRESET -> IntStateValue(beamPreset)
    StateField.FRAME_RATE -> IntStateValue(frameRate)
    StateField.OVERSAMPLE -> IntStateValue(oversample)
    StateField.ACTIVE_THEME_ID -> StringStateValue(activeThemeId)
    StateField.FOCUS -> FloatStateValue(focus)
    StateField.CUSTOM_BEAM -> BeamGradientStateValue(customBeam)
    StateField.BEAM_CYCLE_SECONDS -> FloatStateValue(beamCycleSeconds)
    StateField.BEAM_CYCLE_PER_TRACK -> BooleanStateValue(beamCyclePerTrack)
    StateField.BEAM_ENERGY -> FloatStateValue(beamEnergy)
    StateField.GLOW -> FloatStateValue(glow)
    StateField.BEAM_RANDOM_GENERATION -> LongStateValue(beamRandomGeneration)
    StateField.BEAM_RANDOM_RANGE -> FloatRangeStateValue(beamRandomRange)
    StateField.GLOW_RANDOM_GENERATION -> LongStateValue(glowRandomGeneration)
    StateField.GLOW_RANDOM_RANGE -> FloatRangeStateValue(glowRandomRange)
    StateField.GEOMETRY_EFFECT -> IntStateValue(geometryEffect)
    StateField.GEOMETRY_AMOUNT -> FloatStateValue(geometryAmount)
    StateField.GRID_ENABLED -> BooleanStateValue(gridEnabled)
    StateField.AUTO_GAIN_ENABLED -> BooleanStateValue(autoGainEnabled)
    StateField.SCOPE_GAIN -> FloatStateValue(scopeGain)
    StateField.VIEW_LOCKED -> BooleanStateValue(viewLocked)
    StateField.ORBIT_YAW -> FloatStateValue(orbitYaw)
    StateField.ORBIT_PITCH -> FloatStateValue(orbitPitch)
    StateField.DOLLY -> FloatStateValue(dolly)
    StateField.DISPLAY_HUD -> StringStateValue(displayHud)
    StateField.FULLSCREEN -> BooleanStateValue(fullscreen)
    StateField.SCOPE_ROTATION_LOCKED -> BooleanStateValue(scopeRotationLocked)
    StateField.UI_PLACEMENT_LOCKED -> BooleanStateValue(uiPlacementLocked)
    StateField.REMOTE_LATENCY_MODE -> IntStateValue(remoteLatencyMode)
    StateField.REMOTE_NETWORK_MODE -> IntStateValue(remoteNetworkMode)
    StateField.BESTIARY_FOUND -> BooleanStateValue(bestiaryFound)
    StateField.EPILEPSY_ACKNOWLEDGED -> BooleanStateValue(epilepsyAcknowledged)
}

enum class StateField(val wireName: String, val valueKind: StateValueKind) {
    PLAYBACK_ACTIVE("transport.playback_active", StateValueKind.BOOLEAN),
    POSITION_MILLIS("transport.position_millis", StateValueKind.LONG),
    QUEUE_INDEX("transport.queue_index", StateValueKind.INT),
    VOLUME_FRACTION("transport.volume_fraction", StateValueKind.FLOAT),
    SOURCE_KIND("source.kind", StateValueKind.STRING),
    REMOTE_HOST_ID("remote.host_id", StateValueKind.NULLABLE_STRING),
    REMOTE_AUDIO_ENABLED("remote.audio_enabled", StateValueKind.BOOLEAN),
    REMOTE_GEOMETRY_ENABLED("remote.geometry_enabled", StateValueKind.BOOLEAN),
    SCOPE_MODE("scope.mode", StateValueKind.INT),
    BEAM_PRESET("scope.beam.preset", StateValueKind.INT),
    FRAME_RATE("scope.fps", StateValueKind.INT),
    OVERSAMPLE("scope.oversample", StateValueKind.INT),
    ACTIVE_THEME_ID("theme.active_id", StateValueKind.STRING),
    FOCUS("scope.focus", StateValueKind.FLOAT),
    CUSTOM_BEAM("scope.beam.custom", StateValueKind.BEAM_GRADIENT),
    BEAM_CYCLE_SECONDS("scope.beam.cycle_seconds", StateValueKind.FLOAT),
    BEAM_CYCLE_PER_TRACK("scope.beam.cycle_per_track", StateValueKind.BOOLEAN),
    BEAM_ENERGY("scope.beam.energy", StateValueKind.FLOAT),
    GLOW("scope.glow", StateValueKind.FLOAT),
    BEAM_RANDOM_GENERATION("scope.beam.random_generation", StateValueKind.LONG),
    BEAM_RANDOM_RANGE("scope.beam.random_range", StateValueKind.FLOAT_RANGE),
    GLOW_RANDOM_GENERATION("scope.glow.random_generation", StateValueKind.LONG),
    GLOW_RANDOM_RANGE("scope.glow.random_range", StateValueKind.FLOAT_RANGE),
    GEOMETRY_EFFECT("scope.geometry.effect", StateValueKind.INT),
    GEOMETRY_AMOUNT("scope.geometry.amount", StateValueKind.FLOAT),
    GRID_ENABLED("scope.grid.enabled", StateValueKind.BOOLEAN),
    AUTO_GAIN_ENABLED("scope.gain.auto", StateValueKind.BOOLEAN),
    SCOPE_GAIN("scope.gain", StateValueKind.FLOAT),
    VIEW_LOCKED("scope.view_lock", StateValueKind.BOOLEAN),
    ORBIT_YAW("scope.orbit.yaw", StateValueKind.FLOAT),
    ORBIT_PITCH("scope.orbit.pitch", StateValueKind.FLOAT),
    DOLLY("scope.dolly", StateValueKind.FLOAT),
    DISPLAY_HUD("display.hud", StateValueKind.STRING),
    FULLSCREEN("display.fullscreen", StateValueKind.BOOLEAN),
    SCOPE_ROTATION_LOCKED("rotation.scope_lock", StateValueKind.BOOLEAN),
    UI_PLACEMENT_LOCKED("rotation.ui_lock", StateValueKind.BOOLEAN),
    REMOTE_LATENCY_MODE("remote.latency_mode", StateValueKind.INT),
    REMOTE_NETWORK_MODE("remote.network_mode", StateValueKind.INT),
    BESTIARY_FOUND("diagnostic.bestiary_found", StateValueKind.BOOLEAN),
    EPILEPSY_ACKNOWLEDGED("safety.epilepsy_acknowledged", StateValueKind.BOOLEAN),
}

data class PhosphorStateSnapshot(
    val schema: String = PHOSPHOR_STATE_SCHEMA,
    val session: String? = null,
    val revision: Long = 0L,
    val sequence: Long = 0L,
    val wallTimeMillis: Long = 0L,
    val distribution: Distribution = Distribution.PLAY,
    val buildProfile: BuildProfile = BuildProfile.PLAY_RELEASE,
    val desired: InstrumentSettings = InstrumentSettings(),
    val effective: InstrumentSettings = InstrumentSettings(),
    val availability: FrozenMap<StateField, Availability> = FrozenMap.copyOf(
        StateField.entries.associateWith { Availability(AvailabilityState.AVAILABLE) },
    ),
    val authority: FrozenMap<StateField, Authority> = FrozenMap.copyOf(
        StateField.entries.associateWith { Authority(AuthorityState.WRITABLE) },
    ),
    val fixes: FrozenMap<StateField, Fix> = frozenMapOf(),
    val capabilities: FrozenMap<PrincipalId, FrozenSet<Capability>> = frozenMapOf(),
    val provenance: FrozenMap<StateField, ProvenanceStamp> = frozenMapOf(),
    val liveness: SessionLiveness = SessionLiveness(null, SessionState.ABSENT),
    val capture: CaptureTruth = CaptureTruth(CaptureTruthState.STOPPED),
    val contestedFields: FrozenSet<StateField> = frozenSetOf(),
) {
    init {
        require(schema == PHOSPHOR_STATE_SCHEMA) { "unsupported state schema $schema" }
        require(revision >= 0L) { "revision must not be negative" }
        require(sequence >= 0L) { "sequence must not be negative" }
        require(wallTimeMillis >= 0L) { "wall time must not be negative" }
        require(
            distribution to buildProfile in setOf(
                Distribution.PLAY to BuildProfile.PLAY_RELEASE,
                Distribution.FORTRESS to BuildProfile.FORTRESS_RELEASE,
                Distribution.LOCAL_DEV to BuildProfile.LOCAL_DEVELOPMENT,
            ),
        ) { "distribution and build profile must describe the same compiled identity" }
        require(session == liveness.sessionId) { "snapshot session and liveness session must agree" }
        require(availability.keys == StateField.entries.toSet()) { "availability must exactly cover every state field" }
        require(authority.keys == StateField.entries.toSet()) { "authority must exactly cover every state field" }
        require(provenance.keys.all { it in StateField.entries }) { "provenance contains an unknown state field" }
        StateField.entries.forEach { field ->
            val expectedFix = availability.getValue(field).fix ?: authority.getValue(field).fix
            require(fixes[field] == expectedFix) {
                "fix plane must exactly project availability/authority for ${field.wireName}"
            }
        }
        require(fixes.keys.all { field ->
            availability.getValue(field).fix != null || authority.getValue(field).fix != null
        }) { "fix plane must not contain repairs for writable available fields" }
    }
}

object InitialSnapshots {
    fun play(nowWallMillis: Long = 0L): PhosphorStateSnapshot = PhosphorStateSnapshot(
        wallTimeMillis = nowWallMillis,
        distribution = Distribution.PLAY,
        buildProfile = BuildProfile.PLAY_RELEASE,
    )

    fun fortress(nowWallMillis: Long = 0L): PhosphorStateSnapshot = PhosphorStateSnapshot(
        wallTimeMillis = nowWallMillis,
        distribution = Distribution.FORTRESS,
        buildProfile = BuildProfile.FORTRESS_RELEASE,
    )
}
