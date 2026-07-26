package dev.phosphor.mobil3.state

data class SourceClockIdentity(
    val sourceId: String,
    val clockId: String,
) {
    init {
        require(sourceId.isNotBlank()) { "geometry source id must be nonblank" }
        require(clockId.isNotBlank()) { "geometry clock id must be nonblank" }
    }
}

data class NormalizedPoint(
    val x: Float,
    val y: Float,
) {
    init {
        require(x.isFinite() && y.isFinite()) { "beam coordinates must be finite" }
        require(x in -1.0f..1.0f && y in -1.0f..1.0f) {
            "beam coordinates must be normalized"
        }
    }
}

data class BeamSegment(
    val start: NormalizedPoint,
    val end: NormalizedPoint,
    val intensity: Float,
) {
    init {
        require(intensity.isFinite() && intensity in 0.0f..1.0f) {
            "beam intensity must be normalized"
        }
    }
}

enum class GeometryLayer(val wireName: String) {
    TRUE_BEAM("true_beam"),
    PHOSPHOR_GEOMETRY("phosphor_geometry"),
    PROJECTM_FIELD("projectm_field"),
}

enum class GeometryQualityTier(val wireName: String, val fidelityRank: Int) {
    FULL("full", 2),
    REDUCED("reduced", 1),
    MINIMAL("minimal", 0),
}

enum class ProjectMMotionState(val wireName: String) {
    LIVE("live"),
    STILL("still"),
}

data class GeometryLoadSheddingPolicy(
    val degradationOrder: FrozenList<GeometryLayer> = DEFAULT_ORDER,
) {
    init {
        require(degradationOrder == DEFAULT_ORDER) {
            "load shedding must degrade ProjectM, then Phosphor geometry, before the true beam"
        }
    }

    companion object {
        val DEFAULT_ORDER: FrozenList<GeometryLayer> = frozenListOf(
            GeometryLayer.PROJECTM_FIELD,
            GeometryLayer.PHOSPHOR_GEOMETRY,
            GeometryLayer.TRUE_BEAM,
        )
    }
}

data class ProjectMFieldMetadata(
    val enabled: Boolean,
    val presetId: String? = null,
    val fieldMonotonicMillis: Long? = null,
    val motionState: ProjectMMotionState? = null,
    val parameters: FrozenMap<String, Float> = frozenMapOf(),
    val featureValues: FrozenMap<String, Float> = frozenMapOf(),
) {
    init {
        if (enabled) {
            require(!presetId.isNullOrBlank()) { "enabled ProjectM field requires a preset id" }
            requireNotNull(fieldMonotonicMillis) { "enabled ProjectM field requires timing" }
            require(fieldMonotonicMillis >= 0L) { "ProjectM timing must not be negative" }
            requireNotNull(motionState) { "enabled ProjectM field requires an explicit motion truth state" }
        }
        if (!enabled) require(
            presetId == null && fieldMonotonicMillis == null && motionState == null &&
                parameters.isEmpty() && featureValues.isEmpty(),
        ) {
            "disabled ProjectM field must not imply live field state"
        }
        require(parameters.keys.all { it.isNotBlank() }) { "ProjectM parameter names must be nonblank" }
        require(parameters.values.all { it.isFinite() }) { "ProjectM parameters must be finite" }
        require(featureValues.keys.all { it.isNotBlank() }) { "ProjectM feature names must be nonblank" }
        require(featureValues.values.all { it.isFinite() }) { "ProjectM features must be finite" }
    }
}

data class GeometryLayerState(
    val enabled: Boolean,
    val blend: Float,
    val qualityTier: GeometryQualityTier,
) {
    init {
        require(blend.isFinite() && blend in 0.0f..1.0f) { "layer blend must be normalized" }
        if (!enabled) require(blend == 0.0f) { "disabled layers must have zero blend" }
        if (!enabled) require(qualityTier == GeometryQualityTier.MINIMAL) {
            "disabled layers must not advertise rendered quality"
        }
        if (enabled) require(blend > 0.0f) { "enabled layers require a visible nonzero blend" }
    }
}

/** Inert bounded-frame contract. No producer, listener, socket, Binder, or renderer is added in Phase 03. */
data class GeometryFrame(
    val event: String = "geometry.frame",
    val sourceClock: SourceClockIdentity,
    val sequence: Long,
    val monotonicMillis: Long,
    val scopeMode: Int,
    val segments: FrozenList<BeamSegment>,
    val beamEnergy: Float,
    val signalState: CaptureTruthState,
    val layerStates: FrozenMap<GeometryLayer, GeometryLayerState>,
    val projectM: ProjectMFieldMetadata,
    val droppedFrames: Long,
    val qualityTier: GeometryQualityTier,
    val loadSheddingPolicy: GeometryLoadSheddingPolicy = GeometryLoadSheddingPolicy(),
) {
    init {
        require(event == "geometry.frame") { "geometry event must remain canonical" }
        require(sequence >= 0L) { "geometry sequence must not be negative" }
        require(monotonicMillis >= 0L) { "geometry monotonic time must not be negative" }
        require(scopeMode >= 0) { "scope mode must not be negative" }
        require(segments.size <= MAX_GEOMETRY_SEGMENTS) { "geometry frame exceeds its bounded segment budget" }
        require(beamEnergy.isFinite() && beamEnergy >= 0.0f) { "beam energy must be finite and non-negative" }
        require(layerStates.keys == GeometryLayer.entries.toSet()) {
            "every geometry layer must remain explicitly labeled"
        }
        val trueBeam = layerStates.getValue(GeometryLayer.TRUE_BEAM)
        val phosphorGeometry = layerStates.getValue(GeometryLayer.PHOSPHOR_GEOMETRY)
        val projectMField = layerStates.getValue(GeometryLayer.PROJECTM_FIELD)
        require(trueBeam.enabled) {
            "true beam geometry must remain available"
        }
        require(trueBeam.blend >= MIN_TRUE_BEAM_BLEND) {
            "true beam must remain legible at every blended output"
        }
        require(qualityTier == trueBeam.qualityTier) {
            "frame quality must truthfully report true-beam quality"
        }
        require(phosphorGeometry.qualityTier.fidelityRank <= trueBeam.qualityTier.fidelityRank) {
            "Phosphor geometry must degrade before true-beam quality"
        }
        require(projectMField.qualityTier.fidelityRank <= phosphorGeometry.qualityTier.fidelityRank) {
            "ProjectM must degrade before Phosphor geometry and the true beam"
        }
        require(projectMField.enabled == projectM.enabled) {
            "ProjectM layer identity and metadata must agree"
        }
        if (projectM.enabled) require(projectM.fieldMonotonicMillis == monotonicMillis) {
            "ProjectM and true beam must share the frame clock"
        }
        if (signalState == CaptureTruthState.FLOWING) {
            require(segments.isNotEmpty()) { "flowing geometry requires true-beam segments" }
            require(beamEnergy > 0.0f) { "flowing geometry requires measured nonzero beam energy" }
            require(segments.any { it.intensity > 0.0f && it.start != it.end }) {
                "flowing geometry requires at least one visible nonzero true-beam segment"
            }
            if (projectM.enabled) require(projectM.motionState == ProjectMMotionState.LIVE) {
                "flowing ProjectM geometry must be explicitly live"
            }
        }
        if (signalState in NO_SIGNAL_GEOMETRY_STATES) {
            require(segments.isEmpty() && beamEnergy == 0.0f) {
                "$signalState must not imply live beam geometry"
            }
            if (projectM.enabled) require(projectM.motionState == ProjectMMotionState.STILL) {
                "$signalState must make the ProjectM field honestly still"
            }
        }
        require(droppedFrames >= 0L) { "dropped geometry count must not be negative" }
    }

    companion object {
        const val MAX_GEOMETRY_SEGMENTS: Int = 4096
        const val MIN_TRUE_BEAM_BLEND: Float = 0.1f
        private val NO_SIGNAL_GEOMETRY_STATES = setOf(
            CaptureTruthState.UNAVAILABLE,
            CaptureTruthState.PERMISSION_NEEDED,
            CaptureTruthState.STARTING,
            CaptureTruthState.PRESENT_SILENT_OR_OPTED_OUT,
            CaptureTruthState.CONNECTED_NO_SIGNAL,
            CaptureTruthState.STALLED,
            CaptureTruthState.RETRYING,
            CaptureTruthState.STOPPED,
            CaptureTruthState.ERROR,
        )
    }
}
