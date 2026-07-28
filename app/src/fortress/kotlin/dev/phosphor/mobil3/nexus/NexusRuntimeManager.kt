package dev.phosphor.mobil3.nexus

import dev.phosphor.mobil3.state.Capability
import dev.phosphor.mobil3.store.PhosphorStateStore

/** Process-wide owner for the single Nexus runtime authority over the one application store. */
internal data class NexusRuntimeAttachment(
    val session: NexusSession,
    val token: NexusTokenGrant,
    val grants: NexusGrantLedger,
)

internal data class NexusRuntimeTransitionResult(
    val session: NexusSession,
    val grants: NexusGrantLedger,
    val closed: Boolean,
)

internal class NexusRuntimeManager(private val store: PhosphorStateStore) {
    private val monitor = Any()
    private var session = NexusSession.absent(NexusGeneration(0), NexusClosureCause.EXPLICIT_DISCONNECT)
    private var token: NexusTokenGrant? = null
    private var grants = NexusGrantLedger.of()
    private var trustPolicy = NexusTrustPolicy.of()
    private var dispatcher: NexusRuntimeDispatcher? = null

    fun attachBinder(
        nextSession: NexusSession,
        nextToken: NexusTokenGrant,
        nextGrants: NexusGrantLedger,
        nextPolicy: NexusTrustPolicy,
    ): NexusRuntimeDispatcher = synchronized(monitor) {
        val previousSession = session
        val previousToken = token
        val previousGrants = grants
        val previousPolicy = trustPolicy
        val previousDispatcher = dispatcher
        if (session.id != null && !closeCurrentLocked(NexusClosureCause.PACKAGE_REPLACED)) {
            throw IllegalStateException("Persist the previous Nexus closure before attaching a replacement Binder session.")
        }
        try {
            val nextDispatcher = NexusRuntimeDispatcher(store, nextSession, nextToken, nextGrants, nextPolicy)
            when (val attach = nextDispatcher.attachResult()) {
                is NexusSessionAttachResult.Attached -> {
                    check(attach.session == nextSession && attach.grants.grants.containsAll(nextGrants.grants)) {
                        "Shared Nexus authority attached a different Binder session projection."
                    }
                }
                is NexusSessionAttachResult.Refused -> throw IllegalStateException(attach.refusal.fix)
            }
            session = nextSession
            token = nextToken
            grants = nextGrants
            trustPolicy = nextPolicy
            dispatcher = nextDispatcher
            nextDispatcher
        } catch (failure: Throwable) {
            session = previousSession
            token = previousToken
            grants = previousGrants
            trustPolicy = previousPolicy
            dispatcher = previousDispatcher
            throw failure
        }
    }

    fun attachTailnet(
        nextSession: NexusSession,
        nextToken: NexusTokenGrant,
        nextGrants: NexusGrantLedger,
        nextPolicy: NexusTrustPolicy,
    ): NexusRuntimeDispatcher = synchronized(monitor) {
        require(nextSession.reachPath == NexusReachPath.TAILNET) { "Tailnet attach requires a tailnet session." }
        attachSharedLocked(nextSession, nextToken, nextGrants, nextPolicy, NexusClosureCause.EXPLICIT_DISCONNECT, "Tailnet")
    }

    fun attachTailnet(
        trustKey: NexusTrustKey,
        sessionId: NexusSessionId,
        tokenId: NexusTokenId,
        generation: NexusGeneration,
        capabilities: Set<dev.phosphor.mobil3.state.Capability>,
        durableGrants: NexusGrantLedger,
        trustPolicy: NexusTrustPolicy,
        heartbeatIntervalMillis: Long,
        leaseMillis: Long,
        nowMonotonicMillis: Long,
    ): NexusRuntimeAttachment = synchronized(monitor) {
        val token = NexusTokenGrant.of(tokenId, trustKey, capabilities, nowMonotonicMillis, nowMonotonicMillis + leaseMillis)
        val grants = NexusGrantLedger.of(durableGrants.grants + capabilities.map { capability ->
            NexusCapabilityGrant(trustKey, capability, NexusGrantScope.TRANSIENT, sessionId, "tailnet-grant-${capability.wireName}")
        })
        val session = NexusSession.tailnetObserving(
            trustKey = trustKey,
            sessionId = sessionId,
            tokenId = tokenId,
            generation = generation,
            capabilities = capabilities,
            heartbeatIntervalMillis = heartbeatIntervalMillis,
            nowMonotonicMillis = nowMonotonicMillis,
        )
        attachSharedLocked(session, token, grants, trustPolicy, NexusClosureCause.EXPLICIT_DISCONNECT, "Tailnet")
        NexusRuntimeAttachment(session, token, grants)
    }

    private fun attachSharedLocked(
        nextSession: NexusSession,
        nextToken: NexusTokenGrant,
        nextGrants: NexusGrantLedger,
        nextPolicy: NexusTrustPolicy,
        replacementCause: NexusClosureCause,
        label: String,
    ): NexusRuntimeDispatcher {
        val previousSession = session
        val previousToken = token
        val previousGrants = grants
        val previousPolicy = trustPolicy
        val previousDispatcher = dispatcher
        if (session.id != null && !closeCurrentLocked(replacementCause)) {
            throw IllegalStateException("Persist the previous Nexus closure before attaching a replacement $label session.")
        }
        try {
            val nextDispatcher = NexusRuntimeDispatcher(store, nextSession, nextToken, nextGrants, nextPolicy)
            when (val attach = nextDispatcher.attachResult()) {
                is NexusSessionAttachResult.Attached -> {
                    check(attach.session == nextSession && attach.grants.grants.containsAll(nextGrants.grants)) {
                        "Shared Nexus authority attached a different $label session projection."
                    }
                }
                is NexusSessionAttachResult.Refused -> throw IllegalStateException(attach.refusal.fix)
            }
            session = nextSession
            token = nextToken
            grants = nextGrants
            trustPolicy = nextPolicy
            dispatcher = nextDispatcher
            return nextDispatcher
        } catch (failure: Throwable) {
            session = previousSession
            token = previousToken
            grants = previousGrants
            trustPolicy = previousPolicy
            dispatcher = previousDispatcher
            throw failure
        }
    }

    fun dispatcher(): NexusRuntimeDispatcher = synchronized(monitor) {
        dispatcher ?: throw IllegalStateException("Authenticate through a Nexus transport before using runtime authority.")
    }

    fun confirmBinderAlive(sessionId: NexusSessionId, generation: NexusGeneration, tokenId: NexusTokenId): Boolean = synchronized(monitor) {
        dispatcher != null &&
            session.reachPath == NexusReachPath.BINDER &&
            session.id == sessionId &&
            session.generation == generation &&
            session.tokenId == tokenId &&
            token?.id == tokenId
    }

    fun recordTailnetHeartbeat(
        sessionId: NexusSessionId,
        generation: NexusGeneration,
        nowMonotonicMillis: Long,
    ): NexusRuntimeAttachment? = synchronized(monitor) {
        val active = session
        val activeToken = token ?: return@synchronized null
        val activeDispatcher = dispatcher ?: return@synchronized null
        if (active.reachPath != NexusReachPath.TAILNET || active.id != sessionId || active.generation != generation) {
            return@synchronized null
        }
        val next = activeDispatcher.recordHeartbeatSession(sessionId, generation, nowMonotonicMillis)
            ?: return@synchronized null
        session = next
        NexusRuntimeAttachment(next, activeToken, grants)
    }

    fun evaluateTailnetLiveness(
        sessionId: NexusSessionId,
        nowMonotonicMillis: Long,
    ): NexusRuntimeTransitionResult? = synchronized(monitor) {
        val active = session
        val activeDispatcher = dispatcher ?: return@synchronized null
        if (active.reachPath != NexusReachPath.TAILNET || active.id != sessionId) return@synchronized null
        val transition = try {
            active.evaluateRemoteLiveness(nowMonotonicMillis, grants)
        } catch (_: IllegalArgumentException) {
            return@synchronized null
        }
        if (!activeDispatcher.apply(transition)) return@synchronized null
        session = transition.session
        grants = transition.grants
        val closed = transition.session.lifecycle == NexusLifecycle.ABSENT
        if (closed) {
            token = null
            dispatcher = null
        }
        NexusRuntimeTransitionResult(transition.session, transition.grants, closed)
    }

    fun closeBinderDeath() = close(NexusClosureCause.BINDER_DEATH)
    fun close(cause: NexusClosureCause): Boolean = synchronized(monitor) { closeCurrentLocked(cause) }

    fun revokeActiveIfMatches(
        trustKey: NexusTrustKey,
        capability: Capability,
        cause: NexusClosureCause = NexusClosureCause.EXPLICIT_REVOKE,
    ): Boolean = synchronized(monitor) {
        require(cause == NexusClosureCause.EXPLICIT_REVOKE || cause == NexusClosureCause.TOKEN_REVOKED) {
            "revocation close must use a revocation cause"
        }
        val active = session
        if (active.id == null || dispatcher == null) return@synchronized false
        if (active.trustKey != trustKey) return@synchronized false
        if (capability !in active.capabilities) return@synchronized false
        closeCurrentLocked(cause)
    }

    private fun closeCurrentLocked(cause: NexusClosureCause): Boolean {
        val active = session
        val activeDispatcher = dispatcher
        if (active.id == null || activeDispatcher == null) return false
        val transition = if (cause == NexusClosureCause.BINDER_DEATH && active.reachPath == NexusReachPath.BINDER) {
            active.binderDied(grants)
        } else {
            active.disconnect(cause, grants)
        }
        if (!activeDispatcher.apply(transition)) return false
        session = transition.session
        grants = transition.grants
        token = null
        dispatcher = null
        return true
    }
}

internal object NexusRuntimeManagers {
    private val managers = java.util.WeakHashMap<PhosphorStateStore, NexusRuntimeManager>()
    fun forStore(store: PhosphorStateStore): NexusRuntimeManager = synchronized(managers) {
        managers.getOrPut(store) { NexusRuntimeManager(store) }
    }
}
