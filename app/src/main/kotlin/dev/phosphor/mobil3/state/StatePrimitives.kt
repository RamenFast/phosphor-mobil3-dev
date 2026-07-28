package dev.phosphor.mobil3.state

/** Pure Kotlin, Android-free contracts for the inert `phosphor.state/2` schema. */
const val PHOSPHOR_STATE_SCHEMA: String = "phosphor.state/2"

/** A principal plus idempotency key remains reserved for at least this long. */
const val IDEMPOTENCY_TTL_MILLIS: Long = 24L * 60L * 60L * 1000L

/** Explicit retry ceiling so `retrying` can never imply unbounded background churn. */
const val MAX_CAPTURE_RETRY_BACKOFF_MILLIS: Long = 5L * 60L * 1000L

enum class Distribution(val wireName: String) {
    PLAY("play"),
    FORTRESS("fortress"),
    LOCAL_DEV("local_dev"),
}

enum class BuildProfile(val wireName: String) {
    PLAY_RELEASE("play_release"),
    FORTRESS_RELEASE("fortress_release"),
    LOCAL_DEVELOPMENT("local_development"),
}

enum class PrincipalKind(val wireName: String) {
    HUMAN("human"),
    NEXUS("nexus"),
    ENGINE("engine"),
    SYSTEM("system"),
    MIGRATION("migration"),
}

enum class Transport(val wireName: String) {
    UI("ui"),
    CLI("cli"),
    BINDER("binder"),
    TAILNET("tailnet"),
    LIFECYCLE("lifecycle"),
    MIGRATION("migration"),
}

enum class Capability(val wireName: String) {
    OBSERVE_STATE("observe.state"),
    OBSERVE_GEOMETRY("observe.geometry"),
    OBSERVE_AUDIT("observe.audit"),
    CONTROL_SETTINGS("control.settings"),
    CONTROL_TRANSPORT("control.transport"),
    CONTROL_SCOPE("control.scope"),
    CONTROL_DISPLAY("control.display"),
    CONTROL_THEMES("control.themes"),
    REQUEST_PERMISSIONS("request.permissions"),
    CONTROL_FORTRESS("control.fortress"),
}

enum class SessionState(val wireName: String) {
    ABSENT("absent"),
    AUTHENTICATING("authenticating"),
    OBSERVING("observing"),
    DRIVING("driving"),
    CLOSING("closing"),
}

enum class AvailabilityState(val wireName: String) {
    AVAILABLE("available"),
    SYSTEM_UNAVAILABLE("system_unavailable"),
    DISTRIBUTION_UNAVAILABLE("distribution_unavailable"),
    AUTHORITY_UNAVAILABLE("authority_unavailable"),
    SESSION_UNAVAILABLE("session_unavailable"),
}

enum class AuthorityState(val wireName: String) {
    WRITABLE("writable"),
    HUMAN_ONLY("human_only"),
    SYSTEM_BLOCKED("system_blocked"),
    DISTRIBUTION_BLOCKED("distribution_blocked"),
    SESSION_BLOCKED("session_blocked"),
}

enum class RefusalCode(val wireName: String) {
    INVALID_REQUEST("invalid_request"),
    INVALID_VALUE("invalid_value"),
    REVISION_CONFLICT("revision_conflict"),
    IDEMPOTENCY_CONFLICT("idempotency_conflict"),
    IDEMPOTENCY_INDEX_FULL("idempotency_index_full"),
    CAPABILITY_NOT_GRANTED("capability_not_granted"),
    CAPABILITY_REVOKED("capability_revoked"),
    SESSION_UNAVAILABLE("session_unavailable"),
    SYSTEM_UNAVAILABLE("system_unavailable"),
    SYSTEM_ROTATION_LOCKED("system_rotation_locked"),
    PERMISSION_REQUIRES_HUMAN("permission_requires_human"),
    DISTRIBUTION_UNAVAILABLE("distribution_unavailable"),
    AUTHORITY_UNAVAILABLE("authority_unavailable"),
    CAPTURE_SOURCE_OPTED_OUT("capture_source_opted_out"),
    SIGNER_MIGRATION_REQUIRED("signer_migration_required"),
}

enum class CaptureTruthState(val wireName: String) {
    UNAVAILABLE("unavailable"),
    PERMISSION_NEEDED("permission_needed"),
    STARTING("starting"),
    PRESENT_SILENT_OR_OPTED_OUT("present_silent_or_opted_out"),
    CONNECTED_NO_SIGNAL("connected_no_signal"),
    FLOWING("flowing"),
    STALLED("stalled"),
    RETRYING("retrying"),
    STOPPED("stopped"),
    ERROR("error"),
}

enum class CaptureCause(val wireName: String) {
    NONE("none"),
    SOURCE_OPT_OUT("source_opt_out"),
    PROTECTED_OR_DRM("protected_or_drm"),
    SILENT_CONTENT("silent_content"),
    UNKNOWN("unknown"),
}

enum class CaptureEvidenceKind(val wireName: String) {
    SOURCE_POLICY_OPT_OUT("source_policy_opt_out"),
    DRM_OR_PROTECTION("drm_or_protection"),
    MEASURED_SILENCE("measured_silence"),
    INCONCLUSIVE("inconclusive"),
}

data class CaptureEvidence(
    val kind: CaptureEvidenceKind,
    val receiptId: String,
    val observedWallTimeMillis: Long,
    val summary: String,
) {
    init {
        require(receiptId.isNotBlank()) { "capture evidence receipt id must be nonblank" }
        require(observedWallTimeMillis >= 0L) { "capture evidence time must not be negative" }
        require(summary.isNotBlank()) { "capture evidence summary must be nonblank" }
    }
}

data class Fix(
    val code: RefusalCode,
    val message: String,
) {
    init {
        require(message.isNotBlank()) { "fix must be nonblank" }
    }
}

data class Availability(
    val state: AvailabilityState,
    val fix: Fix? = null,
) {
    init {
        if (state == AvailabilityState.AVAILABLE) {
            require(fix == null) { "available fields must not advertise a repair" }
        } else {
            requireNotNull(fix) { "unavailable fields require a fix" }
            require(fix.code in state.allowedFixCodes) {
                "${state.wireName} requires a semantically matching fix code"
            }
        }
    }
}

data class Authority(
    val state: AuthorityState,
    val fix: Fix? = null,
) {
    init {
        if (state == AuthorityState.WRITABLE) {
            require(fix == null) { "writable fields must not advertise a repair" }
        } else {
            requireNotNull(fix) { "blocked authority requires a fix" }
            require(fix.code in state.allowedFixCodes) {
                "${state.wireName} requires a semantically matching fix code"
            }
        }
    }
}

private val AvailabilityState.allowedFixCodes: Set<RefusalCode>
    get() = when (this) {
        AvailabilityState.AVAILABLE -> emptySet()
        AvailabilityState.SYSTEM_UNAVAILABLE -> setOf(RefusalCode.SYSTEM_UNAVAILABLE)
        AvailabilityState.DISTRIBUTION_UNAVAILABLE -> setOf(RefusalCode.DISTRIBUTION_UNAVAILABLE)
        AvailabilityState.AUTHORITY_UNAVAILABLE -> setOf(
            RefusalCode.AUTHORITY_UNAVAILABLE,
            RefusalCode.PERMISSION_REQUIRES_HUMAN,
        )
        AvailabilityState.SESSION_UNAVAILABLE -> setOf(
            RefusalCode.SESSION_UNAVAILABLE,
            RefusalCode.CAPABILITY_REVOKED,
        )
    }

private val AuthorityState.allowedFixCodes: Set<RefusalCode>
    get() = when (this) {
        AuthorityState.WRITABLE -> emptySet()
        AuthorityState.HUMAN_ONLY -> setOf(
            RefusalCode.AUTHORITY_UNAVAILABLE,
            RefusalCode.PERMISSION_REQUIRES_HUMAN,
        )
        AuthorityState.SYSTEM_BLOCKED -> setOf(
            RefusalCode.AUTHORITY_UNAVAILABLE,
            RefusalCode.SYSTEM_UNAVAILABLE,
            RefusalCode.SYSTEM_ROTATION_LOCKED,
        )
        AuthorityState.DISTRIBUTION_BLOCKED -> setOf(RefusalCode.DISTRIBUTION_UNAVAILABLE)
        AuthorityState.SESSION_BLOCKED -> setOf(
            RefusalCode.SESSION_UNAVAILABLE,
            RefusalCode.CAPABILITY_REVOKED,
        )
    }

data class PrincipalId(
    val kind: PrincipalKind,
    val stableId: String,
) {
    init {
        require(stableId.isNotBlank()) { "principal stableId must be nonblank" }
    }
}

data class CapabilityGrant(
    val capability: Capability,
    val granted: Boolean,
    val sessionScoped: Boolean = false,
)

data class SessionLiveness(
    val sessionId: String?,
    val state: SessionState,
    val lastHeartbeatMonotonicMillis: Long? = null,
) {
    init {
        if (state == SessionState.ABSENT) {
            require(sessionId == null) { "an absent session cannot retain an id" }
        } else {
            require(!sessionId.isNullOrBlank()) { "a present session requires an id" }
        }
    }

    fun canDrive(session: String?): Boolean =
        state == SessionState.DRIVING && sessionId != null && sessionId == session
}

data class ProvenanceStamp(
    val receiptId: String,
    val writer: PrincipalId,
    val reason: String,
    val transport: Transport,
    val sessionId: String?,
    val monotonicMillis: Long,
    val wallTimeMillis: Long,
) {
    init {
        require(receiptId.isNotBlank()) { "receipt id must be nonblank" }
        require(reason.isNotBlank()) { "provenance reason must be nonblank" }
        require(monotonicMillis >= 0L) { "monotonic time must not be negative" }
        require(wallTimeMillis >= 0L) { "wall time must not be negative" }
    }
}

data class CaptureTruth(
    val state: CaptureTruthState,
    val stateSinceWallTimeMillis: Long = 0L,
    val cause: CaptureCause = CaptureCause.NONE,
    val evidence: CaptureEvidence? = null,
    val authority: Authority? = null,
    val fix: Fix? = null,
    val attempt: Int? = null,
    val nextRetryWallTimeMillis: Long? = null,
    val backoffMillis: Long? = null,
) {
    init {
        require(stateSinceWallTimeMillis >= 0L) { "capture state time must not be negative" }
        if (state == CaptureTruthState.PRESENT_SILENT_OR_OPTED_OUT) {
            require(cause != CaptureCause.NONE) {
                "present_silent_or_opted_out requires an evidence-qualified cause"
            }
        } else {
            require(cause == CaptureCause.NONE) {
                "cause is only valid for present_silent_or_opted_out"
            }
        }


        when (cause) {
            CaptureCause.SOURCE_OPT_OUT -> require(evidence?.kind == CaptureEvidenceKind.SOURCE_POLICY_OPT_OUT) {
                "source_opt_out requires source-policy evidence"
            }
            CaptureCause.PROTECTED_OR_DRM -> require(evidence?.kind == CaptureEvidenceKind.DRM_OR_PROTECTION) {
                "protected_or_drm requires protection evidence"
            }
            CaptureCause.SILENT_CONTENT -> require(evidence?.kind == CaptureEvidenceKind.MEASURED_SILENCE) {
                "silent_content requires measured-silence evidence"
            }
            CaptureCause.UNKNOWN -> require(evidence?.kind == CaptureEvidenceKind.INCONCLUSIVE) {
                "unknown cause requires inconclusive evidence"
            }
            CaptureCause.NONE -> require(evidence == null) { "evidence requires a capture cause" }
        }
        if (evidence != null) {
            require(evidence.observedWallTimeMillis >= stateSinceWallTimeMillis) {
                "capture evidence must be observed at or after the qualified transition"
            }
        }

        if (state == CaptureTruthState.UNAVAILABLE) {
            requireNotNull(authority) { "unavailable capture requires authority" }
            requireNotNull(fix) { "unavailable capture requires a fix" }
            require(authority.state != AuthorityState.WRITABLE) {
                "unavailable capture authority must identify a blocker"
            }
            require(fix == authority.fix) {
                "unavailable capture fix must exactly project its blocking authority"
            }
        }

        if (state == CaptureTruthState.RETRYING) {
            requireNotNull(attempt) { "retrying capture requires an attempt" }
            require(attempt > 0) { "retry attempt must be positive" }
            requireNotNull(nextRetryWallTimeMillis) { "retrying capture requires a next timestamp" }
            require(nextRetryWallTimeMillis > stateSinceWallTimeMillis) {
                "next retry must follow the retrying transition"
            }
            requireNotNull(backoffMillis) { "retrying capture requires backoff" }
            require(backoffMillis in 1L..MAX_CAPTURE_RETRY_BACKOFF_MILLIS) {
                "retry backoff must be positive and bounded"
            }
            val scheduledRetry = try {
                Math.addExact(stateSinceWallTimeMillis, backoffMillis)
            } catch (overflow: ArithmeticException) {
                throw IllegalArgumentException("retry schedule must not overflow", overflow)
            }
            require(nextRetryWallTimeMillis == scheduledRetry) {
                "next retry must equal transition time plus the declared backoff"
            }
        } else {
            require(attempt == null && nextRetryWallTimeMillis == null && backoffMillis == null) {
                "retry metadata is only valid while retrying"
            }
        }
    }
}
