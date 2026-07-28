package dev.phosphor.mobil3.nexus

import dev.phosphor.mobil3.state.AuditRecord
import dev.phosphor.mobil3.state.AuditKind
import dev.phosphor.mobil3.state.Capability
import dev.phosphor.mobil3.state.FrozenList
import dev.phosphor.mobil3.state.FrozenMap
import dev.phosphor.mobil3.state.PhosphorStateSnapshot
import dev.phosphor.mobil3.state.PrincipalId
import dev.phosphor.mobil3.state.PrincipalKind
import dev.phosphor.mobil3.state.Refusal
import dev.phosphor.mobil3.state.RefusalCode
import dev.phosphor.mobil3.state.SessionLiveness
import dev.phosphor.mobil3.state.SessionState
import dev.phosphor.mobil3.store.PhosphorStateStore
import dev.phosphor.mobil3.store.PhosphorDispatchResult
import dev.phosphor.mobil3.store.StoreHealth
import dev.phosphor.mobil3.store.CommitAuthorizationDecision
import dev.phosphor.mobil3.store.CommitAuthorizationFence
import dev.phosphor.mobil3.state.frozenSetOf

/**
 * Phase 05b-1 observation-only Nexus authority.
 *
 * This class is intentionally in-memory and Fortress-only. Its monitor is the only authority for
 * the current Nexus session, token grant, grant ledger, and trust policy. Observation revalidates a
 * Phase 05a snapshot candidate while holding this monitor, and keeps holding it through the causal
 * store's own atomic [PhosphorStateStore.observe] read so revocation/closure and observation have a
 * single linearization point.
 */
internal class NexusObservationDispatcher(
    private val store: PhosphorStateStore,
    initialSession: NexusSession,
    initialToken: NexusTokenGrant,
    initialGrants: NexusGrantLedger,
    initialTrustPolicy: NexusTrustPolicy,
) {
    init {
        require(initialSession.lifecycle == NexusLifecycle.OBSERVING || initialSession.lifecycle == NexusLifecycle.DRIVING) {
            "observation authority requires an active observable session"
        }
        val sessionId = requireNotNull(initialSession.id) { "observation authority requires a session id" }
        val trustKey = requireNotNull(initialSession.trustKey) { "observation authority requires a trust tuple" }
        val tokenId = requireNotNull(initialSession.tokenId) { "observation authority requires a token id" }
        require(initialToken.id == tokenId) { "observation authority token must match current session" }
        require(initialToken.trustKey == trustKey) { "observation authority token must match current trust tuple" }
        require(!initialToken.revoked) { "observation authority cannot start with a revoked token grant" }
        require(!initialTrustPolicy.isTokenRevoked(tokenId)) { "observation authority cannot start with a policy-revoked token" }
        require(initialToken.capabilities.containsAll(initialSession.capabilities)) {
            "session capabilities must remain inside token authority"
        }
        require(initialSession.capabilities.all { initialGrants.allows(trustKey, sessionId, it) }) {
            "session capabilities must be present in the grant ledger"
        }
    }

    private val monitor = Any()
    private var currentSession: NexusSession = initialSession
    private var currentToken: NexusTokenGrant = initialToken
    private var currentGrants: NexusGrantLedger = initialGrants
    private var currentTrustPolicy: NexusTrustPolicy = initialTrustPolicy
    private var authorityReadable: Boolean = true
    private var authorityWritable: Boolean = true
    private var authorityFix: Refusal? = null
    private var initialAttachResult: NexusSessionAttachResult = NexusSessionAttachResult.Refused(
        Refusal(RefusalCode.AUTHORITY_UNAVAILABLE, "Nexus authority has not attached yet.", store.snapshot.revision),
    )

    init {
        when (val loaded = NexusAuthorityCodec.decode(store.authorityPlane)) {
            NexusAuthorityDecodeResult.Absent -> {
                authorityFix = capabilityDenied().asStoreRefusal(store.snapshot.revision)
                failClosedInitialSession(initialSession, initialToken)
            }
            is NexusAuthorityDecodeResult.Corrupt -> {
                authorityReadable = false
                authorityWritable = false
                authorityFix = loaded.refusal
                currentSession = NexusSession.absent(initialSession.generation.next(), NexusClosureCause.PROTOCOL_DOWNGRADE)
                currentGrants = NexusGrantLedger.of()
                currentTrustPolicy = NexusTrustPolicy.of(revokedTokenIds = listOf(initialToken.id))
            }
            is NexusAuthorityDecodeResult.Loaded -> {
                val durable = loaded.image.withoutTransientGrants()
                currentTrustPolicy = initialTrustPolicy
                if (currentTrustPolicy.isTokenRevoked(initialToken.id) || initialToken.id in durable.revokedTokenIds) {
                    authorityFix = tokenRevoked().asStoreRefusal(initialSession.generation.value)
                    currentSession = NexusSession.absent(initialSession.generation.next(), NexusClosureCause.TOKEN_REVOKED)
                    initialAttachResult = NexusSessionAttachResult.Refused(requireNotNull(authorityFix))
                } else {
                    currentGrants = NexusGrantLedger.of(durable.grants.grants + initialGrants.grants.filter { it.scope == NexusGrantScope.TRANSIENT })
                    if (!initialSession.capabilities.all { currentGrants.allows(requireNotNull(initialSession.trustKey), requireNotNull(initialSession.id), it) }) {
                        authorityFix = capabilityDenied().asStoreRefusal(store.snapshot.revision)
                        failClosedInitialSession(initialSession, initialToken)
                    } else {
                        if (saveAuthority(authorityImage(), currentSession, AuditKind.AUTHENTICATION, requiredPersistentTrustKey = requireNotNull(initialSession.trustKey))) {
                            initialAttachResult = NexusSessionAttachResult.Attached(currentSession, currentGrants)
                        } else {
                            failClosedInitialSession(initialSession, initialToken)
                        }
                    }
                }
            }
        }
    }

    fun attachResult(): NexusSessionAttachResult = synchronized(monitor) { initialAttachResult }

    fun observe(
        projection: NexusObservationProjection,
        nowMonotonicMillis: Long,
    ): NexusObservationResult = synchronized(monitor) {
        revalidate(projection, nowMonotonicMillis)?.let { refusal ->
            return@synchronized NexusObservationResult.Refused(refusal)
        }
        when (projection.capability) {
            Capability.OBSERVE_STATE -> store.observe().let { observation ->
                NexusObservationResult.State(
                    snapshot = observation.snapshot,
                    health = observation.health,
                )
            }
            Capability.OBSERVE_AUDIT -> store.observe().let { observation ->
                NexusObservationResult.Audit(
                    auditRecords = FrozenList.copyOf(observation.auditRecords),
                    health = observation.health,
                )
            }
            Capability.OBSERVE_GEOMETRY -> NexusObservationResult.GeometryUnavailable(
                fix = "Read geometry from the future renderer-owned high-rate geometry stream after that authority is approved.",
            )
            else -> error("NexusObservationProjection admitted a non-observe capability")
        }
    }

    fun dispatch(
        projection: NexusDispatchProjection,
        nowMonotonicMillis: Long,
        nowWallTimeMillis: Long,
    ): PhosphorDispatchResult = synchronized(monitor) {
        authorityFix?.let { return@synchronized PhosphorDispatchResult.Failed(it) }
        store.dispatchAuthorized(
            action = projection.action,
            request = projection.request,
            monotonicMillis = nowMonotonicMillis,
            wallTimeMillis = nowWallTimeMillis,
            authorizationFence = CommitAuthorizationFence { action, request, snapshot, monotonicMillis, _ ->
                check(Thread.holdsLock(monitor)) {
                    "Nexus authority monitor must remain held through the shared-store commit"
                }
                val refusal = when {
                    action != projection.action || request != projection.request -> tokenInvalid()
                    else -> revalidateDispatch(projection, monotonicMillis)
                }
                if (refusal != null) {
                    CommitAuthorizationDecision.Refused(refusal.asStoreRefusal(snapshot.revision))
                } else {
                    CommitAuthorizationDecision.Authorized.exact(action, request, snapshot)
                }
            },
        )
    }

    fun apply(transition: NexusSessionTransition): Boolean = synchronized(monitor) {
        if (transition.previousSession !== currentSession) return@synchronized false
        if (transition.previousSessionId != currentSession.id) return@synchronized false
        if (transition.previousGeneration != currentSession.generation) return@synchronized false
        if (!currentGrants.containsAll(transition.grants)) return@synchronized false
        val nextImage = authorityImage(session = transition.session, grants = transition.grants)
        if (!saveAuthority(nextImage, transition.session, transition.lifecycleAuditKind())) return@synchronized false
        currentSession = transition.session
        currentGrants = transition.grants
        true
    }

    fun apply(revocation: NexusTokenRevocation): Boolean = synchronized(monitor) {
        if (revocation.previousPolicy !== currentTrustPolicy) return@synchronized false
        if (revocation.revokedTokenId != currentToken.id) return@synchronized false
        if (!revocation.policy.isTokenRevoked(currentToken.id)) return@synchronized false
        if (revocation.previousSession !== currentSession) return@synchronized false
        if (revocation.previousGeneration != currentSession.generation) return@synchronized false
        if (revocation.closedSessionId != currentSession.id) return@synchronized false
        if (revocation.session.lifecycle != NexusLifecycle.ABSENT) return@synchronized false
        if (!currentGrants.containsAll(revocation.grants)) return@synchronized false
        val nextImage = authorityImage(
            session = revocation.session,
            grants = revocation.grants,
            policy = revocation.policy,
            revokedTokenIds = setOf(revocation.revokedTokenId),
        )
        if (!saveAuthority(nextImage, revocation.session, AuditKind.REVOKE)) return@synchronized false
        currentTrustPolicy = revocation.policy
        currentSession = revocation.session
        currentGrants = revocation.grants
        true
    }

    fun recordHeartbeat(
        sessionId: NexusSessionId,
        generation: NexusGeneration,
        nowMonotonicMillis: Long,
    ): Boolean = recordHeartbeatSession(sessionId, generation, nowMonotonicMillis) != null

    internal fun recordHeartbeatSession(
        sessionId: NexusSessionId,
        generation: NexusGeneration,
        nowMonotonicMillis: Long,
    ): NexusSession? = synchronized(monitor) {
        if (currentSession.id != sessionId) return@synchronized null
        if (currentSession.generation != generation) return@synchronized null
        val next = try {
            currentSession.heartbeat(nowMonotonicMillis)
        } catch (_: IllegalArgumentException) {
            return@synchronized null
        }
        if (!saveAuthority(authorityImage(session = next), next)) return@synchronized null
        currentSession = next
        next
    }

    fun durableAuthorityImageForTest(): NexusAuthorityImage = synchronized(monitor) {
        authorityImage()
    }

    fun grantPersistent(
        grant: NexusCapabilityGrant,
        wallTimeMillis: Long,
    ): NexusAuthorityMutationResult = synchronized(monitor) {
        when (val result = NexusAuthorityStoreTransaction.grantPersistent(store, grant, wallTimeMillis)) {
            is NexusAuthorityTransactionResult.Refused -> NexusAuthorityMutationResult.Refused(result.refusal)
            is NexusAuthorityTransactionResult.Committed -> {
                currentGrants = NexusGrantLedger.of(result.image.grants.grants + currentGrants.grants.filter { it.scope == NexusGrantScope.TRANSIENT })
                NexusAuthorityMutationResult.Committed(result.image)
            }
        }
    }

    fun revokePersistent(
        trustKey: NexusTrustKey,
        capability: Capability,
        wallTimeMillis: Long,
    ): NexusAuthorityMutationResult = synchronized(monitor) {
        val result = NexusAuthorityStoreTransaction.revokePersistent(store, trustKey, capability, wallTimeMillis)
        if (result is NexusAuthorityTransactionResult.Refused) return@synchronized NexusAuthorityMutationResult.Refused(result.refusal)
        val committed = (result as NexusAuthorityTransactionResult.Committed).image
        val nextGrants = NexusGrantLedger.of(committed.grants.grants + currentGrants.grants.filter { it.scope == NexusGrantScope.TRANSIENT })
        val nextSession = if (currentSession.trustKey == trustKey && currentSession.id != null && capability in currentSession.capabilities) {
            currentSession.revokeCapability(capability, nextGrants).session
        } else {
            currentSession
        }
        if (nextSession !== currentSession && !saveAuthority(authorityImage(session = nextSession, grants = nextGrants), nextSession)) {
            return@synchronized NexusAuthorityMutationResult.Refused(requireNotNull(authorityFix))
        }
        currentSession = nextSession
        currentGrants = nextGrants
        NexusAuthorityMutationResult.Committed(committed)
    }

    private fun revalidate(
        projection: NexusObservationProjection,
        nowMonotonicMillis: Long,
    ): NexusPreAuthRefusal? {
        authorityFix?.let { return unavailable() }
        val candidate = projection.candidate
        val sessionId = currentSession.id ?: return unavailable()
        val trustKey = currentSession.trustKey ?: return unavailable()
        val tokenId = currentSession.tokenId ?: return unavailable()
        if (currentSession.lifecycle != NexusLifecycle.OBSERVING && currentSession.lifecycle != NexusLifecycle.DRIVING) {
            return unavailable()
        }
        if (remoteHeartbeatRefuses(currentSession, nowMonotonicMillis)) return unavailable()
        if (candidate.sessionId != sessionId) return tokenInvalid()
        if (candidate.tokenId != tokenId) return tokenInvalid()
        if (candidate.trustKey != trustKey) return trustRejected()
        if (candidate.generation != currentSession.generation) return tokenInvalid()
        if (candidate.capability != projection.capability) return capabilityDenied()
        if (projection.capability !in currentSession.capabilities) return capabilityDenied()
        if (currentSession.reachPath == null) return unavailable()
        if (!currentGrants.allows(trustKey, sessionId, projection.capability)) return capabilityDenied()
        if (currentTrustPolicy.isTokenRevoked(tokenId)) return tokenRevoked()
        return currentToken.validate(
            presentedId = tokenId,
            presentedTrustKey = trustKey,
            requestedCapabilities = frozenSetOf(projection.capability),
            nowMonotonicMillis = nowMonotonicMillis,
        )
    }

    private fun revalidateDispatch(
        projection: NexusDispatchProjection,
        nowMonotonicMillis: Long,
    ): NexusPreAuthRefusal? {
        authorityFix?.let { return unavailable() }
        val candidate = projection.candidate
        val sessionId = currentSession.id ?: return unavailable()
        val trustKey = currentSession.trustKey ?: return unavailable()
        val tokenId = currentSession.tokenId ?: return unavailable()
        if (currentSession.lifecycle != NexusLifecycle.OBSERVING && currentSession.lifecycle != NexusLifecycle.DRIVING) {
            return unavailable()
        }
        if (remoteHeartbeatRefuses(currentSession, nowMonotonicMillis)) return unavailable()
        if (candidate.sessionId != sessionId) return tokenInvalid()
        if (candidate.tokenId != tokenId) return tokenInvalid()
        if (candidate.trustKey != trustKey) return trustRejected()
        if (candidate.generation != currentSession.generation) return tokenInvalid()
        if (candidate.capability != projection.action.type.requiredCapability) return capabilityDenied()
        if (projection.request.requestedCapability != candidate.capability) return capabilityDenied()
        if (projection.request.principal.stableId != trustKey.principalStableId) return trustRejected()
        if (projection.request.sessionId != sessionId.value) return tokenInvalid()
        val expectedTransport = when (currentSession.reachPath) {
            NexusReachPath.BINDER -> dev.phosphor.mobil3.state.Transport.BINDER
            NexusReachPath.TAILNET -> dev.phosphor.mobil3.state.Transport.TAILNET
            null -> return unavailable()
        }
        if (projection.request.transport != expectedTransport) return tokenInvalid()
        if (candidate.capability !in currentSession.capabilities) return capabilityDenied()
        if (!currentGrants.allows(trustKey, sessionId, candidate.capability)) return capabilityDenied()
        if (currentTrustPolicy.isTokenRevoked(tokenId)) return tokenRevoked()
        return currentToken.validate(
            presentedId = tokenId,
            presentedTrustKey = trustKey,
            requestedCapabilities = frozenSetOf(candidate.capability),
            nowMonotonicMillis = nowMonotonicMillis,
        )
    }

    private fun remoteHeartbeatRefuses(session: NexusSession, nowMonotonicMillis: Long): Boolean {
        if (session.reachPath != NexusReachPath.TAILNET) return false
        val policy = session.heartbeatPolicy ?: return true
        val lastHeartbeat = session.lastHeartbeatMonotonicMillis ?: return true
        return try {
            policy.evaluate(lastHeartbeat, nowMonotonicMillis, session.lifecycle) != session.lifecycle
        } catch (_: IllegalArgumentException) {
            true
        }
    }

    private fun NexusGrantLedger.containsAll(candidate: NexusGrantLedger): Boolean = grants.containsAll(candidate.grants)

    private fun failClosedInitialSession(initialSession: NexusSession, initialToken: NexusTokenGrant) {
        currentSession = NexusSession.absent(initialSession.generation.next(), NexusClosureCause.PROTOCOL_DOWNGRADE)
        currentGrants = NexusGrantLedger.of()
        currentTrustPolicy = NexusTrustPolicy.of(revokedTokenIds = listOf(initialToken.id))
        initialAttachResult = NexusSessionAttachResult.Refused(
            authorityFix ?: Refusal(RefusalCode.AUTHORITY_UNAVAILABLE, "Nexus authority failed closed during session attach.", store.snapshot.revision),
        )
    }

    private fun authorityImage(
        session: NexusSession = currentSession,
        grants: NexusGrantLedger = currentGrants,
        policy: NexusTrustPolicy = currentTrustPolicy,
        revokedTokenIds: Set<NexusTokenId> = emptySet(),
    ): NexusAuthorityImage = NexusAuthorityImage(
        grants = if (session.lifecycle == NexusLifecycle.ABSENT || session.lifecycle == NexusLifecycle.CLOSING) {
            NexusGrantLedger.of(grants.grants.filter { it.scope == NexusGrantScope.PERSISTENT })
        } else {
            grants
        },
        revokedTokenIds = revokedTokenIds + setOf(currentToken.id).filter { policy.isTokenRevoked(it) },
        consumedIdempotencyKeys = store.idempotencyRecords.map { it.key }.toSet(),
    )

    private fun saveAuthority(
        image: NexusAuthorityImage,
        projectedSession: NexusSession,
        lifecycleAuditKind: AuditKind? = null,
        wallTimeMillis: Long? = null,
        requiredPersistentTrustKey: NexusTrustKey? = null,
    ): Boolean {
        var authorityDecodeRefusal: Refusal? = null
        val failure = store.commitAuthorityPlane(
            update = { storeImage ->
                val merged = when (val decoded = NexusAuthorityCodec.decode(storeImage.authorityPlane)) {
                    NexusAuthorityDecodeResult.Absent -> image
                    is NexusAuthorityDecodeResult.Corrupt -> {
                        authorityDecodeRefusal = decoded.refusal
                        return@commitAuthorityPlane storeImage
                    }
                    is NexusAuthorityDecodeResult.Loaded -> image.mergeDurable(decoded.image)
                }
                if (requiredPersistentTrustKey != null) {
                    val sessionId = projectedSession.id ?: return@commitAuthorityPlane storeImage.also {
                        authorityDecodeRefusal = unavailable().asStoreRefusal(storeImage.snapshot.revision)
                    }
                    val persistent = merged.withoutTransientGrants()
                    val missing = projectedSession.capabilities.any { capability ->
                        persistent.grants.grants.none { grant ->
                            grant.trustKey == requiredPersistentTrustKey &&
                                grant.scope == NexusGrantScope.PERSISTENT &&
                                grant.capability == capability
                        }
                    }
                    if (missing) {
                        authorityDecodeRefusal = capabilityDenied().asStoreRefusal(storeImage.snapshot.revision)
                        return@commitAuthorityPlane storeImage
                    }
                    if (projectedSession.trustKey != requiredPersistentTrustKey || projectedSession.id != sessionId) {
                        authorityDecodeRefusal = trustRejected().asStoreRefusal(storeImage.snapshot.revision)
                        return@commitAuthorityPlane storeImage
                    }
                }
                storeImage.copy(
                    snapshot = storeImage.snapshot.projectNexusAuthority(projectedSession),
                    authorityPlane = NexusAuthorityCodec.encode(merged),
                )
            },
            persisted = { runtimeImage ->
                runtimeImage.copy(snapshot = runtimeImage.snapshot.clearTransientNexusProjection())
            },
            lifecycleAuditKind = lifecycleAuditKind,
            wallTimeMillis = wallTimeMillis,
        )
        val refusal = failure ?: authorityDecodeRefusal
        if (refusal != null) {
            authorityWritable = false
            authorityFix = refusal
            return false
        }
        return true
    }

    private fun NexusAuthorityImage.mergeDurable(durable: NexusAuthorityImage): NexusAuthorityImage = copy(
        grants = NexusGrantLedger.of(durable.withoutTransientGrants().grants.grants + grants.grants),
        revokedTokenIds = revokedTokenIds + durable.revokedTokenIds,
        consumedIdempotencyKeys = consumedIdempotencyKeys + durable.consumedIdempotencyKeys,
        consumedNonces = NexusAuthorityCodec.evictNonceRecords(consumedNonces + durable.consumedNonces),
    )

    private fun NexusSessionTransition.lifecycleAuditKind(): AuditKind? = when (session.closureCause) {
        NexusClosureCause.BINDER_DEATH -> AuditKind.BINDER_DEATH
        NexusClosureCause.REMOTE_HEARTBEAT_LOSS -> AuditKind.HEARTBEAT_EXPIRY
        NexusClosureCause.EXPLICIT_REVOKE,
        NexusClosureCause.TOKEN_REVOKED,
        NexusClosureCause.SIGNER_CHANGED,
        NexusClosureCause.PACKAGE_REPLACED,
        -> AuditKind.REVOKE
        NexusClosureCause.EXPLICIT_DISCONNECT,
        NexusClosureCause.PROTOCOL_DOWNGRADE,
        null,
        -> null
    }

    private fun PhosphorStateSnapshot.projectNexusAuthority(session: NexusSession): PhosphorStateSnapshot {
        val sessionId = session.id?.value.takeIf { session.lifecycle != NexusLifecycle.ABSENT }
        val principal = session.trustKey?.principalStableId?.let { PrincipalId(PrincipalKind.NEXUS, it) }
        val nextCapabilities = if (principal == null || session.lifecycle == NexusLifecycle.ABSENT || session.lifecycle == NexusLifecycle.CLOSING) {
            capabilities.filterKeys { it.kind != PrincipalKind.NEXUS }
        } else {
            capabilities.filterKeys { it.kind != PrincipalKind.NEXUS } + (principal to session.capabilities)
        }
        return copy(
            session = sessionId,
            capabilities = FrozenMap.copyOf(nextCapabilities),
            liveness = SessionLiveness(
                sessionId = sessionId,
                state = when (session.lifecycle) {
                    NexusLifecycle.ABSENT -> SessionState.ABSENT
                    NexusLifecycle.AUTHENTICATING -> SessionState.AUTHENTICATING
                    NexusLifecycle.OBSERVING -> SessionState.OBSERVING
                    NexusLifecycle.DRIVING -> SessionState.DRIVING
                    NexusLifecycle.CLOSING -> SessionState.CLOSING
                },
                lastHeartbeatMonotonicMillis = session.lastHeartbeatMonotonicMillis,
            ),
        )
    }

    private fun PhosphorStateSnapshot.clearTransientNexusProjection(): PhosphorStateSnapshot = copy(
        session = null,
        capabilities = FrozenMap.copyOf(capabilities.filterKeys { it.kind != PrincipalKind.NEXUS }),
        liveness = SessionLiveness(null, SessionState.ABSENT),
    )

    private fun unavailable(): NexusPreAuthRefusal = NexusPreAuthRefusal(
        NexusPreAuthRefusalCode.ENTRY_UNAVAILABLE,
        "Establish a fresh observing Nexus session before retrying.",
    )

    private fun trustRejected(): NexusPreAuthRefusal = NexusPreAuthRefusal(
        NexusPreAuthRefusalCode.TRUST_TUPLE_REJECTED,
        "Re-authorize this exact trusted identity before retrying observation.",
    )

    private fun tokenInvalid(): NexusPreAuthRefusal = NexusPreAuthRefusal(
        NexusPreAuthRefusalCode.TOKEN_INVALID,
        "Re-authorize with the current session generation and token.",
    )

    private fun tokenRevoked(): NexusPreAuthRefusal = NexusPreAuthRefusal(
        NexusPreAuthRefusalCode.TOKEN_REVOKED,
        "Ask the user to issue a replacement token after reviewing grants.",
    )

    private fun capabilityDenied(): NexusPreAuthRefusal = NexusPreAuthRefusal(
        NexusPreAuthRefusalCode.CAPABILITY_DENIED,
        "Request only currently granted observation capabilities.",
    )

    private fun NexusPreAuthRefusal.asStoreRefusal(currentRevision: Long): Refusal = Refusal(
        code = when (code) {
            NexusPreAuthRefusalCode.ENTRY_UNAVAILABLE,
            NexusPreAuthRefusalCode.TRUST_TUPLE_REJECTED,
            NexusPreAuthRefusalCode.CHALLENGE_INVALID,
            NexusPreAuthRefusalCode.TOKEN_INVALID,
            -> RefusalCode.SESSION_UNAVAILABLE
            NexusPreAuthRefusalCode.SIGNER_MIGRATION_REQUIRED -> RefusalCode.SIGNER_MIGRATION_REQUIRED
            NexusPreAuthRefusalCode.PROTOCOL_UNSUPPORTED -> RefusalCode.INVALID_REQUEST
            NexusPreAuthRefusalCode.TOKEN_REVOKED -> RefusalCode.CAPABILITY_REVOKED
            NexusPreAuthRefusalCode.CAPABILITY_DENIED -> RefusalCode.CAPABILITY_NOT_GRANTED
        },
        fix = fix,
        currentRevision = currentRevision,
    )
}

internal typealias NexusRuntimeDispatcher = NexusObservationDispatcher

internal sealed interface NexusSessionAttachResult {
    data class Attached(val session: NexusSession, val grants: NexusGrantLedger) : NexusSessionAttachResult
    data class Refused(val refusal: Refusal) : NexusSessionAttachResult
}

internal sealed interface NexusAuthorityMutationResult {
    data class Committed(val image: NexusAuthorityImage) : NexusAuthorityMutationResult
    data class Refused(val refusal: Refusal) : NexusAuthorityMutationResult
}

internal sealed interface NexusObservationResult {
    data class State(
        val snapshot: PhosphorStateSnapshot,
        val health: StoreHealth,
    ) : NexusObservationResult

    data class Audit(
        val auditRecords: FrozenList<AuditRecord>,
        val health: StoreHealth,
    ) : NexusObservationResult

    data class GeometryUnavailable(val fix: String) : NexusObservationResult {
        init {
            fix.requireNexusToken("geometry unavailable fix", maxLength = 512)
        }
    }

    data class Refused(val refusal: NexusPreAuthRefusal) : NexusObservationResult
}
