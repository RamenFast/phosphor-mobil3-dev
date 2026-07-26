package dev.phosphor.mobil3.nexus

import dev.phosphor.mobil3.state.Capability
import dev.phosphor.mobil3.state.FrozenSet
import dev.phosphor.mobil3.state.frozenSetOf

private val CONTROL_CAPABILITIES: Set<Capability> = setOf(
    Capability.CONTROL_SETTINGS,
    Capability.CONTROL_TRANSPORT,
    Capability.CONTROL_SCOPE,
    Capability.CONTROL_DISPLAY,
    Capability.CONTROL_THEMES,
    Capability.REQUEST_PERMISSIONS,
    Capability.CONTROL_FORTRESS,
)

private val OBSERVE_CAPABILITIES: Set<Capability> = setOf(
    Capability.OBSERVE_STATE,
    Capability.OBSERVE_GEOMETRY,
    Capability.OBSERVE_AUDIT,
)

enum class NexusGrantScope(val wireName: String) {
    PERSISTENT("persistent"),
    TRANSIENT("transient"),
}

data class NexusCapabilityGrant(
    val trustKey: NexusTrustKey,
    val capability: Capability,
    val scope: NexusGrantScope,
    val sessionId: NexusSessionId?,
    val receiptId: String,
) {
    init {
        receiptId.requireNexusToken("grant receipt id")
        when (scope) {
            NexusGrantScope.PERSISTENT -> require(sessionId == null) {
                "persistent grants are scoped to the complete trust tuple, not a session"
            }
            NexusGrantScope.TRANSIENT -> requireNotNull(sessionId) {
                "transient grants require a session id"
            }
        }
    }

    fun appliesTo(
        presentedTrustKey: NexusTrustKey,
        presentedSessionId: NexusSessionId,
        requestedCapability: Capability,
    ): Boolean =
        trustKey == presentedTrustKey &&
            capability == requestedCapability &&
            (scope == NexusGrantScope.PERSISTENT || sessionId == presentedSessionId)
}

class NexusGrantLedger private constructor(
    val grants: FrozenSet<NexusCapabilityGrant>,
) {
    init {
        require(
            grants.map { Triple(it.trustKey, it.capability, it.scope to it.sessionId) }.distinct().size == grants.size,
        ) { "grant ledger cannot contain duplicate trust/capability/scope entries" }
    }

    companion object {
        fun of(grants: Collection<NexusCapabilityGrant> = emptySet()): NexusGrantLedger =
            NexusGrantLedger(FrozenSet.copyOf(grants))
    }

    fun capabilitiesFor(
        trustKey: NexusTrustKey,
        sessionId: NexusSessionId,
    ): FrozenSet<Capability> = FrozenSet.copyOf(
        grants.filter { grant ->
            grant.trustKey == trustKey &&
                (grant.scope == NexusGrantScope.PERSISTENT || grant.sessionId == sessionId)
        }.map { it.capability },
    )

    fun allows(
        trustKey: NexusTrustKey,
        sessionId: NexusSessionId,
        capability: Capability,
    ): Boolean = grants.any { it.appliesTo(trustKey, sessionId, capability) }

    fun revoke(
        trustKey: NexusTrustKey,
        capability: Capability,
    ): NexusGrantLedger = NexusGrantLedger(
        FrozenSet.copyOf(grants.filterNot { it.trustKey == trustKey && it.capability == capability }),
    )

    fun clearTransient(sessionId: NexusSessionId): NexusGrantLedger = NexusGrantLedger(
        FrozenSet.copyOf(
            grants.filterNot { it.scope == NexusGrantScope.TRANSIENT && it.sessionId == sessionId },
        ),
    )

    fun hasTransient(sessionId: NexusSessionId): Boolean = grants.any {
        it.scope == NexusGrantScope.TRANSIENT && it.sessionId == sessionId
    }
}

/** Opaque result produced only by an actual [NexusSession] lifecycle method. */
sealed interface NexusSessionTransition {
    val previousSession: NexusSession
    val previousSessionId: NexusSessionId
    val previousGeneration: NexusGeneration
    val session: NexusSession
    val grants: NexusGrantLedger
}

private class LifecycleSessionTransition(
    override val previousSession: NexusSession,
    override val previousSessionId: NexusSessionId,
    override val previousGeneration: NexusGeneration,
    override val session: NexusSession,
    override val grants: NexusGrantLedger,
) : NexusSessionTransition {
    init {
        require(previousSession.id == previousSessionId) {
            "transition must retain the exact previous session identity"
        }
        require(previousSession.generation == previousGeneration) {
            "transition must retain the exact previous generation"
        }
        require(session.id == previousSessionId || session.lifecycle == NexusLifecycle.ABSENT) {
            "transition must bind the exact previous session or a closed absent state"
        }
        require(session.generation == previousGeneration || session.generation == previousGeneration.next()) {
            "transition generation must derive from the exact previous generation"
        }
        if (session.lifecycle == NexusLifecycle.CLOSING || session.lifecycle == NexusLifecycle.ABSENT) {
            require(!grants.hasTransient(previousSessionId)) {
                "closing or absent sessions must atomically clear every transient grant"
            }
            requireNotNull(session.closureCause) { "closing or absent transition requires a truthful cause" }
        }
        if (session.lifecycle == NexusLifecycle.CLOSING) {
            require(session.id == previousSessionId) {
                "closing transition must retain the exact retiring session identity"
            }
        }
    }
}

private fun NexusSession.lifecycleTransition(
    next: NexusSession,
    grants: NexusGrantLedger,
): NexusSessionTransition = LifecycleSessionTransition(
    previousSession = this,
    previousSessionId = requireNotNull(id) { "previous present session is required" },
    previousGeneration = generation,
    session = next,
    grants = grants,
)

enum class NexusLifecycle(val wireName: String) {
    ABSENT("absent"),
    AUTHENTICATING("authenticating"),
    OBSERVING("observing"),
    DRIVING("driving"),
    CLOSING("closing"),
}

enum class NexusClosureCause(val wireName: String) {
    EXPLICIT_DISCONNECT("explicit_disconnect"),
    EXPLICIT_REVOKE("explicit_revoke"),
    BINDER_DEATH("binder_death"),
    TOKEN_REVOKED("token_revoked"),
    PROTOCOL_DOWNGRADE("protocol_downgrade"),
    PACKAGE_REPLACED("package_replaced"),
    SIGNER_CHANGED("signer_changed"),
    REMOTE_HEARTBEAT_LOSS("remote_heartbeat_loss"),
}

data class NexusHeartbeatPolicy(
    val intervalMillis: Long = NEXUS_DEFAULT_HEARTBEAT_MILLIS,
) {
    init {
        require(intervalMillis in NEXUS_MIN_HEARTBEAT_MILLIS..NEXUS_MAX_HEARTBEAT_MILLIS) {
            "heartbeat interval must be from 1 through 5 seconds"
        }
    }

    val closingAfterMillis: Long = checkedNexusMultiply(
        intervalMillis,
        NEXUS_REMOTE_CLOSING_AFTER_MISSED_HEARTBEATS,
        "remote closing deadline",
    )
    val absentAfterMillis: Long = checkedNexusMultiply(
        intervalMillis,
        NEXUS_REMOTE_ABSENT_AFTER_MISSED_HEARTBEATS,
        "remote absent deadline",
    )

    fun evaluate(
        lastHeartbeatMonotonicMillis: Long,
        nowMonotonicMillis: Long,
        current: NexusLifecycle,
    ): NexusLifecycle {
        lastHeartbeatMonotonicMillis.requireNexusNonNegative("last heartbeat")
        nowMonotonicMillis.requireNexusNonNegative("heartbeat observation time")
        require(nowMonotonicMillis >= lastHeartbeatMonotonicMillis) {
            "heartbeat observation cannot move backwards"
        }
        if (current == NexusLifecycle.ABSENT) return NexusLifecycle.ABSENT
        val elapsed = nowMonotonicMillis - lastHeartbeatMonotonicMillis
        return when {
            elapsed >= absentAfterMillis -> NexusLifecycle.ABSENT
            elapsed >= closingAfterMillis -> NexusLifecycle.CLOSING
            current == NexusLifecycle.CLOSING -> NexusLifecycle.CLOSING
            else -> current
        }
    }
}

@ConsistentCopyVisibility
data class NexusSession private constructor(
    val id: NexusSessionId?,
    val trustKey: NexusTrustKey?,
    val tokenId: NexusTokenId?,
    val reachPath: NexusReachPath?,
    val generation: NexusGeneration,
    val lifecycle: NexusLifecycle,
    val authenticatedCapabilityCeiling: FrozenSet<Capability>,
    val capabilities: FrozenSet<Capability>,
    val heartbeatPolicy: NexusHeartbeatPolicy?,
    val lastHeartbeatMonotonicMillis: Long?,
    val activeControlReceiptId: String?,
    val closureCause: NexusClosureCause?,
) {
    init {
        when (lifecycle) {
            NexusLifecycle.ABSENT -> {
                require(
                    id == null && trustKey == null && tokenId == null && reachPath == null &&
                        authenticatedCapabilityCeiling.isEmpty() && capabilities.isEmpty() && heartbeatPolicy == null &&
                        lastHeartbeatMonotonicMillis == null && activeControlReceiptId == null,
                ) { "absent session cannot retain identity, authority, heartbeat, or active control" }
                requireNotNull(closureCause) { "absent session requires a truthful closure cause" }
            }
            NexusLifecycle.AUTHENTICATING -> {
                require(id != null && trustKey != null && tokenId != null && reachPath != null) {
                    "authenticating session requires bound identity fields"
                }
                require(authenticatedCapabilityCeiling.isNotEmpty()) {
                    "authenticating session requires the token-authorized capability ceiling"
                }
                require(capabilities.isEmpty()) { "authenticating session cannot have authority" }
                require(activeControlReceiptId == null && closureCause == null) {
                    "authenticating session cannot be driving or closing"
                }
            }
            NexusLifecycle.OBSERVING -> {
                requireActiveIdentity()
                require(authenticatedCapabilityCeiling.containsAll(capabilities)) {
                    "effective capabilities must remain within the authenticated ceiling"
                }
                require(Capability.OBSERVE_STATE in capabilities) {
                    "observing session requires observe.state"
                }
                require(activeControlReceiptId == null && closureCause == null) {
                    "observing session cannot retain active-control or closure metadata"
                }
            }
            NexusLifecycle.DRIVING -> {
                requireActiveIdentity()
                require(authenticatedCapabilityCeiling.containsAll(capabilities)) {
                    "effective capabilities must remain within the authenticated ceiling"
                }
                require(Capability.OBSERVE_STATE in capabilities) {
                    "driving session must remain observable"
                }
                require(capabilities.any { it in CONTROL_CAPABILITIES }) {
                    "driving session requires a control capability"
                }
                require(!activeControlReceiptId.isNullOrBlank()) {
                    "driving session requires the accepted active-control receipt"
                }
                require(closureCause == null) { "driving session cannot have a closure cause" }
            }
            NexusLifecycle.CLOSING -> {
                requireActiveIdentity()
                require(authenticatedCapabilityCeiling.isNotEmpty()) {
                    "closing session retains only its authenticated capability ceiling for audit truth"
                }
                require(capabilities.isEmpty()) { "closing session authority must be cleared" }
                require(activeControlReceiptId == null) { "closing session cannot retain active control" }
                requireNotNull(closureCause) { "closing session requires a cause" }
            }
        }
        lastHeartbeatMonotonicMillis?.requireNexusNonNegative("last heartbeat")
        if (reachPath == NexusReachPath.TAILNET && lifecycle != NexusLifecycle.ABSENT) {
            requireNotNull(heartbeatPolicy) { "tailnet session requires heartbeat policy" }
            requireNotNull(lastHeartbeatMonotonicMillis) { "tailnet session requires heartbeat evidence" }
        }
        if (reachPath == NexusReachPath.BINDER) {
            require(heartbeatPolicy == null && lastHeartbeatMonotonicMillis == null) {
                "Binder liveness is owned by Binder death, not remote heartbeat decay"
            }
        }
    }

    private fun requireActiveIdentity() {
        require(id != null && trustKey != null && tokenId != null && reachPath != null) {
            "present session requires identity, token, and reach path"
        }
    }

    companion object {
        fun authenticating(
            authentication: NexusAuthenticatedIdentity,
            generation: NexusGeneration,
            nowMonotonicMillis: Long,
        ): NexusSession {
            nowMonotonicMillis.requireNexusNonNegative("authentication start time")
            return NexusSession(
                id = authentication.sessionId,
                trustKey = authentication.trustKey,
                tokenId = authentication.tokenId,
                reachPath = authentication.reachPath,
                generation = generation,
                lifecycle = NexusLifecycle.AUTHENTICATING,
                authenticatedCapabilityCeiling = authentication.grantedCapabilities,
                capabilities = frozenSetOf(),
                heartbeatPolicy = authentication.reachPath.takeIf { it == NexusReachPath.TAILNET }
                    ?.let { NexusHeartbeatPolicy(authentication.heartbeatIntervalMillis) },
                lastHeartbeatMonotonicMillis = nowMonotonicMillis.takeIf {
                    authentication.reachPath == NexusReachPath.TAILNET
                },
                activeControlReceiptId = null,
                closureCause = null,
            )
        }

        fun absent(
            generation: NexusGeneration,
            cause: NexusClosureCause,
        ): NexusSession = NexusSession(
            id = null,
            trustKey = null,
            tokenId = null,
            reachPath = null,
            generation = generation,
            lifecycle = NexusLifecycle.ABSENT,
            authenticatedCapabilityCeiling = frozenSetOf(),
            capabilities = frozenSetOf(),
            heartbeatPolicy = null,
            lastHeartbeatMonotonicMillis = null,
            activeControlReceiptId = null,
            closureCause = cause,
        )
    }

    fun establish(grantLedger: NexusGrantLedger): NexusSession {
        require(lifecycle == NexusLifecycle.AUTHENTICATING) {
            "only an authenticating session can become observing"
        }
        val sessionId = requireNotNull(id)
        val key = requireNotNull(trustKey)
        val effective = FrozenSet.copyOf(
            grantLedger.capabilitiesFor(key, sessionId).filter { it in authenticatedCapabilityCeiling },
        )
        require(Capability.OBSERVE_STATE in effective) {
            "session establishment requires observe.state"
        }
        return copy(
            lifecycle = NexusLifecycle.OBSERVING,
            capabilities = effective,
        )
    }

    fun heartbeat(nowMonotonicMillis: Long): NexusSession {
        require(reachPath == NexusReachPath.TAILNET) { "only tailnet sessions accept heartbeats" }
        require(lifecycle == NexusLifecycle.OBSERVING || lifecycle == NexusLifecycle.DRIVING) {
            "closing or absent session cannot accept heartbeat"
        }
        val previous = requireNotNull(lastHeartbeatMonotonicMillis)
        require(nowMonotonicMillis >= previous) { "heartbeat cannot move backwards" }
        require(requireNotNull(heartbeatPolicy).evaluate(previous, nowMonotonicMillis, lifecycle) == lifecycle) {
            "a heartbeat arriving after the closing deadline cannot revive authority"
        }
        return copy(lastHeartbeatMonotonicMillis = nowMonotonicMillis)
    }

    fun evaluateRemoteLiveness(
        nowMonotonicMillis: Long,
        grantLedger: NexusGrantLedger,
    ): NexusSessionTransition {
        require(reachPath == NexusReachPath.TAILNET) { "only tailnet sessions use remote heartbeat decay" }
        val sessionId = requireNotNull(id)
        val evaluated = requireNotNull(heartbeatPolicy).evaluate(
            lastHeartbeatMonotonicMillis = requireNotNull(lastHeartbeatMonotonicMillis),
            nowMonotonicMillis = nowMonotonicMillis,
            current = lifecycle,
        )
        val next = when (evaluated) {
            NexusLifecycle.CLOSING -> copy(
                lifecycle = NexusLifecycle.CLOSING,
                capabilities = frozenSetOf(),
                activeControlReceiptId = null,
                closureCause = NexusClosureCause.REMOTE_HEARTBEAT_LOSS,
                generation = if (lifecycle == NexusLifecycle.CLOSING) generation else generation.next(),
            )
            NexusLifecycle.ABSENT -> absent(
                generation = generation.next(),
                cause = NexusClosureCause.REMOTE_HEARTBEAT_LOSS,
            )
            else -> this
        }
        val nextLedger = if (next.lifecycle == NexusLifecycle.CLOSING || next.lifecycle == NexusLifecycle.ABSENT) {
            grantLedger.clearTransient(sessionId)
        } else {
            grantLedger
        }
        return lifecycleTransition(next, nextLedger)
    }

    fun revokeCapability(
        capability: Capability,
        updatedLedger: NexusGrantLedger,
    ): NexusSessionTransition {
        require(lifecycle != NexusLifecycle.ABSENT) { "absent session has no capability to revoke" }
        require(capability in capabilities) { "session does not currently carry the revoked capability" }
        val sessionId = requireNotNull(id)
        val key = requireNotNull(trustKey)
        require(!updatedLedger.allows(key, sessionId, capability)) {
            "updated ledger still grants the revoked capability"
        }
        val remaining = FrozenSet.copyOf(
            capabilities.filter { existing ->
                existing != capability && updatedLedger.allows(key, sessionId, existing)
            },
        )
        val next = if (Capability.OBSERVE_STATE !in remaining) {
            absent(generation.next(), NexusClosureCause.EXPLICIT_REVOKE)
        } else {
            copy(
                generation = generation.next(),
                lifecycle = NexusLifecycle.OBSERVING,
                capabilities = remaining,
                activeControlReceiptId = null,
            )
        }
        val finalLedger = if (next.lifecycle == NexusLifecycle.ABSENT) {
            updatedLedger.clearTransient(sessionId)
        } else {
            updatedLedger
        }
        return lifecycleTransition(next, finalLedger)
    }

    fun disconnect(
        cause: NexusClosureCause,
        grantLedger: NexusGrantLedger,
    ): NexusSessionTransition {
        require(lifecycle != NexusLifecycle.ABSENT) { "an absent session cannot disconnect again" }
        require(cause != NexusClosureCause.REMOTE_HEARTBEAT_LOSS) {
            "remote heartbeat loss must use evaluateRemoteLiveness"
        }
        val sessionId = requireNotNull(id)
        return lifecycleTransition(
            next = absent(generation.next(), cause),
            grants = grantLedger.clearTransient(sessionId),
        )
    }

    fun binderDied(grantLedger: NexusGrantLedger): NexusSessionTransition {
        require(reachPath == NexusReachPath.BINDER) { "Binder death requires a Binder session" }
        return disconnect(NexusClosureCause.BINDER_DEATH, grantLedger)
    }

    fun presence(): NexusPresence = NexusPresence(
        eyeVisible = lifecycle == NexusLifecycle.OBSERVING || lifecycle == NexusLifecycle.DRIVING,
        handVisible = lifecycle == NexusLifecycle.DRIVING && activeControlReceiptId != null,
        activeControlReceiptId = activeControlReceiptId,
    )
}

data class NexusPresence(
    val eyeVisible: Boolean,
    val handVisible: Boolean,
    val activeControlReceiptId: String?,
) {
    init {
        if (handVisible) {
            require(eyeVisible && !activeControlReceiptId.isNullOrBlank()) {
                "hand presence requires observation and an accepted active-control receipt"
            }
        } else {
            require(activeControlReceiptId == null) {
                "inactive hand cannot retain a control receipt"
            }
        }
    }
}

internal fun Capability.isNexusObserveCapability(): Boolean = this in OBSERVE_CAPABILITIES
internal fun Capability.isNexusControlCapability(): Boolean = this in CONTROL_CAPABILITIES
