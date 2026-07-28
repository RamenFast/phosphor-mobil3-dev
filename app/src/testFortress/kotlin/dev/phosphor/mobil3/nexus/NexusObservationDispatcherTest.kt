package dev.phosphor.mobil3.nexus

import dev.phosphor.mobil3.state.Capability
import dev.phosphor.mobil3.state.InitialSnapshots
import dev.phosphor.mobil3.state.PhosphorStateSnapshot
import dev.phosphor.mobil3.state.ActionRequest
import dev.phosphor.mobil3.state.AuditKind
import dev.phosphor.mobil3.state.PrincipalId
import dev.phosphor.mobil3.state.PrincipalKind
import dev.phosphor.mobil3.state.Refusal
import dev.phosphor.mobil3.state.RefusalCode
import dev.phosphor.mobil3.state.SetDisplayHud
import dev.phosphor.mobil3.state.SessionState
import dev.phosphor.mobil3.state.Transport
import dev.phosphor.mobil3.store.PhosphorDispatchResult
import dev.phosphor.mobil3.store.PhosphorStatePersistencePort
import dev.phosphor.mobil3.store.PhosphorStateStore
import dev.phosphor.mobil3.store.PhosphorStoreImage
import dev.phosphor.mobil3.store.PhosphorStoreLoadResult
import dev.phosphor.mobil3.store.PhosphorStorePersistenceResult
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Test

class NexusObservationDispatcherTest {
    private val cert = CertificateSha256("a".repeat(64))
    private val sessionId = NexusSessionId("session-1")
    private val tokenId = NexusTokenId("token-1")
    private val trustKey = NexusTrustKey.android(
        packageName = "dev.nexus.mobile",
        signing = AndroidSigningEvidence.of(cert),
        principalStableId = "nexus-mobile",
        profile = NexusBuildProfile.NEXUS,
    )

    @Test
    fun validStateObservationReturnsExactSnapshotAndHealthIdentity() {
        val fixture = fixture(capabilities = listOf(Capability.OBSERVE_STATE))
        val expected = fixture.store.snapshot

        val result = fixture.dispatcher.observe(fixture.projection(Capability.OBSERVE_STATE), 50L)

        val state = assertIs<NexusObservationResult.State>(result)
        assertSame(expected, state.snapshot)
        assertSame(fixture.store.health, state.health)
        assertEquals(setOf("snapshot", "health"), payloadFields(NexusObservationResult.State::class.java))
    }

    @Test
    fun drivingSessionMayObserveButIsNeverCreatedByObservation() {
        val driving = drivingFixture()

        val result = driving.dispatcher.observe(driving.projection(Capability.OBSERVE_STATE), 50L)

        assertIs<NexusObservationResult.State>(result)
    }

    @Test
    fun auditObservationIsLeastDisclosureAndImmutableWithoutStatePayload() {
        val fixture = fixture(capabilities = listOf(Capability.OBSERVE_STATE, Capability.OBSERVE_AUDIT))
        val result = fixture.dispatcher.observe(fixture.projection(Capability.OBSERVE_AUDIT), 50L)

        val audit = assertIs<NexusObservationResult.Audit>(result)
        assertTrue(audit.auditRecords.isNotEmpty())
        assertTrue(audit.auditRecords.all { it.actionType == null && it.provenance == null })
        assertSame(fixture.store.health, audit.health)
        assertEquals(setOf("auditRecords", "health"), payloadFields(NexusObservationResult.Audit::class.java))
        assertFailsUnsupported { audit.auditRecords.addForProbe() }
    }

    @Test
    fun wrongAndStaleCandidateFieldsRefuseWithoutDisclosure() {
        val current = fixture(capabilities = listOf(Capability.OBSERVE_STATE))
        val staleGeneration = fixture(
            session = observingSession(trustKey, tokenId, sessionId, listOf(Capability.OBSERVE_STATE), generation = 9L),
            token = current.token,
            grants = current.grants,
            policy = current.policy,
            capabilities = listOf(Capability.OBSERVE_STATE),
        ).projection(Capability.OBSERVE_STATE)
        assertRefused(current.dispatcher.observe(staleGeneration, 50L), NexusPreAuthRefusalCode.TOKEN_INVALID)

        val wrongSession = fixture(
            session = observingSession(trustKey, tokenId, NexusSessionId("session-2"), listOf(Capability.OBSERVE_STATE)),
            capabilities = listOf(Capability.OBSERVE_STATE),
        ).projection(Capability.OBSERVE_STATE)
        assertRefused(current.dispatcher.observe(wrongSession, 50L), NexusPreAuthRefusalCode.TOKEN_INVALID)

        val wrongToken = fixture(
            session = observingSession(trustKey, NexusTokenId("token-2"), sessionId, listOf(Capability.OBSERVE_STATE)),
            token = NexusTokenGrant.of(NexusTokenId("token-2"), trustKey, listOf(Capability.OBSERVE_STATE), 0L, 100L),
            grants = grants(trustKey, sessionId, listOf(Capability.OBSERVE_STATE)),
            capabilities = listOf(Capability.OBSERVE_STATE),
        ).projection(Capability.OBSERVE_STATE)
        assertRefused(current.dispatcher.observe(wrongToken, 50L), NexusPreAuthRefusalCode.TOKEN_INVALID)

        val otherTrust = NexusTrustKey.android(
            packageName = "dev.other.mobile",
            signing = AndroidSigningEvidence.of(cert),
            principalStableId = "other-nexus",
            profile = NexusBuildProfile.NEXUS,
        )
        val wrongTrust = fixture(
            session = observingSession(otherTrust, tokenId, sessionId, listOf(Capability.OBSERVE_STATE)),
            token = NexusTokenGrant.of(tokenId, otherTrust, listOf(Capability.OBSERVE_STATE), 0L, 100L),
            grants = grants(otherTrust, sessionId, listOf(Capability.OBSERVE_STATE)),
            capabilities = listOf(Capability.OBSERVE_STATE),
        ).projection(Capability.OBSERVE_STATE)
        assertRefused(current.dispatcher.observe(wrongTrust, 50L), NexusPreAuthRefusalCode.TRUST_TUPLE_REJECTED)
    }

    @Test
    fun missingGrantAndIncoherentAuthorityConstructionAreRejectedWithoutDisclosure() {
        val valid = fixture(capabilities = listOf(Capability.OBSERVE_STATE))
        assertFailsWith<IllegalArgumentException> {
            NexusObservationDispatcher(valid.store, valid.session, valid.token, NexusGrantLedger.of(), valid.policy)
        }
        assertFailsWith<IllegalArgumentException> {
            NexusObservationDispatcher(
                valid.store,
                valid.session,
                NexusTokenGrant.of(NexusTokenId("wrong-token"), trustKey, listOf(Capability.OBSERVE_STATE), 0L, 100L),
                valid.grants,
                valid.policy,
            )
        }
        val revokedPolicy = valid.policy.revokeToken(tokenId, valid.session, valid.grants).policy
        assertFailsWith<IllegalArgumentException> {
            NexusObservationDispatcher(valid.store, valid.session, valid.token, valid.grants, revokedPolicy)
        }

        val fixture = fixture(capabilities = listOf(Capability.OBSERVE_STATE))
        val revocation = fixture.policy.revokeToken(tokenId, fixture.session, fixture.grants)
        assertTrue(fixture.dispatcher.apply(revocation))
        assertRefused(fixture.dispatcher.observe(fixture.projection(Capability.OBSERVE_STATE), 50L), NexusPreAuthRefusalCode.ENTRY_UNAVAILABLE)
    }

    @Test
    fun currentGrantRemovalAndExplicitRevokeRefuseWithoutObservationDisclosure() {
        val capabilityRemoval = fixture(
            capabilities = listOf(Capability.OBSERVE_STATE, Capability.OBSERVE_AUDIT),
        )
        val auditProjection = capabilityRemoval.projection(Capability.OBSERVE_AUDIT)
        val withoutAudit = capabilityRemoval.grants.revoke(trustKey, Capability.OBSERVE_AUDIT)
        assertTrue(
            capabilityRemoval.dispatcher.apply(
                capabilityRemoval.session.revokeCapability(Capability.OBSERVE_AUDIT, withoutAudit),
            ),
        )
        assertRefused(
            capabilityRemoval.dispatcher.observe(auditProjection, 50L),
            NexusPreAuthRefusalCode.TOKEN_INVALID,
        )

        val explicitRevoke = fixture(capabilities = listOf(Capability.OBSERVE_STATE))
        val stateProjection = explicitRevoke.projection(Capability.OBSERVE_STATE)
        val withoutState = explicitRevoke.grants.revoke(trustKey, Capability.OBSERVE_STATE)
        val transition = explicitRevoke.session.revokeCapability(Capability.OBSERVE_STATE, withoutState)
        assertEquals(NexusClosureCause.EXPLICIT_REVOKE, transition.session.closureCause)
        assertTrue(explicitRevoke.dispatcher.apply(transition))
        assertRefused(
            explicitRevoke.dispatcher.observe(stateProjection, 50L),
            NexusPreAuthRefusalCode.ENTRY_UNAVAILABLE,
        )
    }

    @Test
    fun stalePolicyRevocationCannotReplaceTheCurrentTrustPolicyLineage() {
        val priorRevokedToken = NexusTokenId("prior-revoked-token")
        val currentPolicy = NexusTrustPolicy.of(revokedTokenIds = listOf(priorRevokedToken))
        val fixture = fixture(
            policy = currentPolicy,
            capabilities = listOf(Capability.OBSERVE_STATE),
        )
        val stalePolicy = NexusTrustPolicy.of()
        val staleRevocation = stalePolicy.revokeToken(tokenId, fixture.session, fixture.grants)

        assertFalse(fixture.dispatcher.apply(staleRevocation))
        assertIs<NexusObservationResult.State>(
            fixture.dispatcher.observe(fixture.projection(Capability.OBSERVE_STATE), 50L),
        )

        val currentRevocation = currentPolicy.revokeToken(tokenId, fixture.session, fixture.grants)
        assertTrue(fixture.dispatcher.apply(currentRevocation))
        assertRefused(
            fixture.dispatcher.observe(fixture.projection(Capability.OBSERVE_STATE), 50L),
            NexusPreAuthRefusalCode.ENTRY_UNAVAILABLE,
        )
    }

    @Test
    fun currentPolicyRevocationForTheWrongActiveSessionCannotReplaceAuthority() {
        val fixture = fixture(capabilities = listOf(Capability.OBSERVE_STATE))
        val wrongSessionId = NexusSessionId("session-2")
        val wrongSession = observingSession(
            trustKey,
            tokenId,
            wrongSessionId,
            listOf(Capability.OBSERVE_STATE),
        )
        val wrongGrants = grants(
            trustKey,
            wrongSessionId,
            listOf(Capability.OBSERVE_STATE),
        )
        val wrongSessionRevocation = fixture.policy.revokeToken(
            tokenId,
            wrongSession,
            wrongGrants,
        )

        assertFalse(fixture.dispatcher.apply(wrongSessionRevocation))
        assertIs<NexusObservationResult.State>(
            fixture.dispatcher.observe(fixture.projection(Capability.OBSERVE_STATE), 50L),
        )
    }

    @Test
    fun explicitDisconnectBinderDeathAndRemoteHeartbeatLossLinearizeBeforeLaterObservation() {
        val disconnect = fixture(capabilities = listOf(Capability.OBSERVE_STATE))
        assertTrue(disconnect.dispatcher.apply(disconnect.session.disconnect(NexusClosureCause.EXPLICIT_DISCONNECT, disconnect.grants)))
        assertRefused(disconnect.dispatcher.observe(disconnect.projection(Capability.OBSERVE_STATE), 50L), NexusPreAuthRefusalCode.ENTRY_UNAVAILABLE)

        val binderDeath = fixture(capabilities = listOf(Capability.OBSERVE_STATE))
        assertTrue(binderDeath.dispatcher.apply(binderDeath.session.binderDied(binderDeath.grants)))
        assertRefused(binderDeath.dispatcher.observe(binderDeath.projection(Capability.OBSERVE_STATE), 50L), NexusPreAuthRefusalCode.ENTRY_UNAVAILABLE)

        val tailnet = tailnetFixture(capabilities = listOf(Capability.OBSERVE_STATE))
        assertRefused(tailnet.dispatcher.observe(tailnet.projection(Capability.OBSERVE_STATE), 3_000L), NexusPreAuthRefusalCode.ENTRY_UNAVAILABLE)
        assertTrue(tailnet.dispatcher.apply(tailnet.session.evaluateRemoteLiveness(3_000L, tailnet.grants)))
        assertRefused(tailnet.dispatcher.observe(tailnet.projection(Capability.OBSERVE_STATE), 3_000L), NexusPreAuthRefusalCode.ENTRY_UNAVAILABLE)
    }

    @Test
    fun staleOpaqueTransitionRevocationAndGrantAddingObjectsAreRejected() {
        val fixture = fixture(capabilities = listOf(Capability.OBSERVE_STATE))
        val staleTransition = fixture.session.disconnect(NexusClosureCause.EXPLICIT_DISCONNECT, fixture.grants)
        assertTrue(fixture.dispatcher.apply(fixture.session.binderDied(fixture.grants)))
        assertFalse(fixture.dispatcher.apply(staleTransition))

        val grantAdding = fixture(capabilities = listOf(Capability.OBSERVE_STATE))
        val expandedGrants = NexusGrantLedger.of(
            grantAdding.grants.grants + NexusCapabilityGrant(
                requireNotNull(grantAdding.session.trustKey),
                Capability.OBSERVE_AUDIT,
                NexusGrantScope.PERSISTENT,
                null,
                "grant-added-audit",
            ),
        )
        val close = grantAdding.session.disconnect(NexusClosureCause.EXPLICIT_DISCONNECT, grantAdding.grants)
        assertFalse(grantAdding.dispatcher.apply(reflectTransition(close, expandedGrants)))

        val second = fixture(capabilities = listOf(Capability.OBSERVE_STATE))
        val staleRevocation = second.policy.revokeToken(tokenId, second.session, second.grants)
        assertTrue(second.dispatcher.apply(second.session.binderDied(second.grants)))
        assertFalse(second.dispatcher.apply(staleRevocation))

        val unrelated = fixture(capabilities = listOf(Capability.OBSERVE_STATE))
        assertFalse(unrelated.dispatcher.apply(unrelated.policy.revokeToken(NexusTokenId("other-token"), unrelated.session, unrelated.grants)))
    }

    @Test
    fun concurrentObserveVersusRevokeHasOnlyPermittedLinearizedOutcomes() {
        val port = MemoryPort(PhosphorStoreImage(InitialSnapshots.fortress(10L)))
        val store = PhosphorStateStore(port, InitialSnapshots.fortress(10L))
        val fixture = fixture(capabilities = listOf(Capability.OBSERVE_STATE), store = store)
        val projection = fixture.projection(Capability.OBSERVE_STATE)
        var observed: NexusObservationResult? = null
        val observeStarted = CountDownLatch(1)
        lateinit var observing: Thread
        lateinit var revoking: Thread
        synchronized(store) {
            observing = thread(start = true) {
                observeStarted.countDown()
                observed = fixture.dispatcher.observe(projection, 50L)
            }
            assertTrue(observeStarted.await(5, TimeUnit.SECONDS))
            assertThreadState(observing, Thread.State.BLOCKED)
            val revokingStarted = CountDownLatch(1)
            revoking = thread(start = true) {
                revokingStarted.countDown()
                fixture.dispatcher.apply(fixture.policy.revokeToken(tokenId, fixture.session, fixture.grants))
            }
            assertTrue(revokingStarted.await(5, TimeUnit.SECONDS))
            assertThreadState(revoking, Thread.State.BLOCKED)
        }
        observing.join(5_000L)
        revoking.join(5_000L)
        assertIs<NexusObservationResult.State>(observed)
        assertRefused(fixture.dispatcher.observe(projection, 50L), NexusPreAuthRefusalCode.ENTRY_UNAVAILABLE)
    }

    @Test
    fun concurrentRevokeFirstForcesTheLaterObservationToRefuse() {
        val fixture = fixture(capabilities = listOf(Capability.OBSERVE_STATE))
        val projection = fixture.projection(Capability.OBSERVE_STATE)
        val revokeCompleted = CountDownLatch(1)
        var revoked = false
        var observed: NexusObservationResult? = null
        val revoking = thread(start = true) {
            revoked = fixture.dispatcher.apply(fixture.policy.revokeToken(tokenId, fixture.session, fixture.grants))
            revokeCompleted.countDown()
        }
        val observing = thread(start = true) {
            assertTrue(revokeCompleted.await(5, TimeUnit.SECONDS))
            observed = fixture.dispatcher.observe(projection, 50L)
        }

        revoking.join(5_000L)
        observing.join(5_000L)

        assertFalse(revoking.isAlive)
        assertFalse(observing.isAlive)
        assertTrue(revoked)
        assertRefused(requireNotNull(observed), NexusPreAuthRefusalCode.ENTRY_UNAVAILABLE)
    }

    @Test
    fun tailnetHeartbeatRefreshRequiresExactCurrentSessionGenerationAndOpenDeadline() {
        val tailnet = tailnetFixture(capabilities = listOf(Capability.OBSERVE_STATE))
        assertFalse(tailnet.dispatcher.recordHeartbeat(NexusSessionId("stale-session"), tailnet.session.generation, 500L))
        assertFalse(tailnet.dispatcher.recordHeartbeat(requireNotNull(tailnet.session.id), NexusGeneration(99L), 500L))
        assertFalse(tailnet.dispatcher.recordHeartbeat(requireNotNull(tailnet.session.id), tailnet.session.generation, 3_000L))
        assertTrue(tailnet.dispatcher.recordHeartbeat(requireNotNull(tailnet.session.id), tailnet.session.generation, 500L))
        assertRefused(
            tailnet.dispatcher.observe(tailnet.projection(Capability.OBSERVE_STATE), 400L),
            NexusPreAuthRefusalCode.ENTRY_UNAVAILABLE,
        )
        assertIs<NexusObservationResult.State>(tailnet.dispatcher.observe(tailnet.projection(Capability.OBSERVE_STATE), 3_499L))
    }

    @Test
    fun geometryObservationReturnsTypedUnavailableWithFix() {
        val fixture = fixture(capabilities = listOf(Capability.OBSERVE_STATE, Capability.OBSERVE_GEOMETRY))
        val result = fixture.dispatcher.observe(fixture.projection(Capability.OBSERVE_GEOMETRY), 50L)
        val unavailable = assertIs<NexusObservationResult.GeometryUnavailable>(result)
        assertTrue(unavailable.fix.contains("renderer-owned"))
    }

    @Test
    fun observationsAreTotallyCausallyInert() {
        val port = MemoryPort(PhosphorStoreImage(InitialSnapshots.fortress(10L)))
        val store = PhosphorStateStore(port, InitialSnapshots.fortress(10L))
        val fixture = fixture(capabilities = listOf(Capability.OBSERVE_STATE, Capability.OBSERVE_AUDIT), store = store)
        val beforeSnapshot = store.snapshot
        val beforeRevision = beforeSnapshot.revision
        val beforeSequence = beforeSnapshot.sequence
        val beforeAudit = store.auditRecords.size
        val beforeIdempotency = store.idempotencyRecords.size
        val beforeSaves = port.saves

        repeat(3) {
            assertIs<NexusObservationResult.State>(fixture.dispatcher.observe(fixture.projection(Capability.OBSERVE_STATE), 50L))
            assertIs<NexusObservationResult.Audit>(fixture.dispatcher.observe(fixture.projection(Capability.OBSERVE_AUDIT), 50L))
        }

        assertSame(beforeSnapshot, store.snapshot)
        assertEquals(beforeRevision, store.snapshot.revision)
        assertEquals(beforeSequence, store.snapshot.sequence)
        assertEquals(beforeAudit, store.auditRecords.size)
        assertEquals(beforeIdempotency, store.idempotencyRecords.size)
        assertEquals(beforeSaves, port.saves)
    }

    @Test
    fun authoritySaveFailureReturnsNoFakeAckAndLeavesAuthorityUnchanged() {
        val failure = Refusal(RefusalCode.SYSTEM_UNAVAILABLE, "Fix authority storage.", 0L)
        val port = MemoryPort(PhosphorStoreImage(InitialSnapshots.fortress(10L)), failSavesAfter = 2, failure = failure)
        val store = PhosphorStateStore(port, InitialSnapshots.fortress(10L))
        val fixture = fixture(capabilities = listOf(Capability.OBSERVE_STATE), store = store)
        val transition = fixture.session.disconnect(NexusClosureCause.EXPLICIT_DISCONNECT, fixture.grants)

        assertFalse(fixture.dispatcher.apply(transition))

        assertTrue(store.authorityPlane != null)
        assertRefused(
            fixture.dispatcher.observe(fixture.projection(Capability.OBSERVE_STATE), 50L),
            NexusPreAuthRefusalCode.ENTRY_UNAVAILABLE,
        )
        assertFalse(store.health.writable)
        assertSame(failure, store.health.fix)
    }

    @Test
    fun constructorAbsentAuthorityRefusesWithoutAttemptingPersistence() {
        val failure = Refusal(RefusalCode.SYSTEM_UNAVAILABLE, "Fix initial authority storage.", 0L)
        val port = MemoryPort(PhosphorStoreImage(InitialSnapshots.fortress(10L)), failSaves = true, failure = failure)
        val store = PhosphorStateStore(port, InitialSnapshots.fortress(10L))
        val session = observingSession(trustKey, tokenId, sessionId, listOf(Capability.OBSERVE_STATE))
        val token = NexusTokenGrant.of(tokenId, trustKey, listOf(Capability.OBSERVE_STATE), 0L, 100L)
        val grants = grants(trustKey, sessionId, listOf(Capability.OBSERVE_STATE))

        val dispatcher = NexusObservationDispatcher(store, session, token, grants, NexusTrustPolicy.of())

        assertRefused(
            dispatcher.observe(
                NexusObservationProjection.authorize(session, token, grants, NexusTrustPolicy.of(), Capability.OBSERVE_STATE, 50L),
                50L,
            ),
            NexusPreAuthRefusalCode.ENTRY_UNAVAILABLE,
        )
        assertFalse(dispatcher.apply(session.disconnect(NexusClosureCause.EXPLICIT_DISCONNECT, grants)))
        assertEquals(SessionState.ABSENT, store.snapshot.liveness.state)
        assertEquals(null, store.snapshot.session)
        assertEquals(null, store.authorityPlane)
        assertTrue(store.health.writable)
        assertEquals(null, store.health.fix)
    }

    @Test
    fun constructorLoadedAuthoritySaveFailureFailsClosedWithoutAdmittingFreshSession() {
        val session = observingSession(trustKey, tokenId, sessionId, listOf(Capability.OBSERVE_STATE))
        val token = NexusTokenGrant.of(tokenId, trustKey, listOf(Capability.OBSERVE_STATE), 0L, 100L)
        val grants = grants(trustKey, sessionId, listOf(Capability.OBSERVE_STATE))
        val durablePlane = NexusAuthorityCodec.encode(NexusAuthorityImage(grants = grants))
        val failure = Refusal(RefusalCode.SYSTEM_UNAVAILABLE, "Fix loaded authority storage.", 0L)
        val port = MemoryPort(
            PhosphorStoreImage(InitialSnapshots.fortress(10L), authorityPlane = durablePlane),
            failSaves = true,
            failure = failure,
        )
        val store = PhosphorStateStore(port, InitialSnapshots.fortress(10L))

        val dispatcher = NexusObservationDispatcher(store, session, token, grants, NexusTrustPolicy.of())

        assertRefused(
            dispatcher.observe(
                NexusObservationProjection.authorize(session, token, grants, NexusTrustPolicy.of(), Capability.OBSERVE_STATE, 50L),
                50L,
            ),
            NexusPreAuthRefusalCode.ENTRY_UNAVAILABLE,
        )
        assertFalse(dispatcher.apply(session.disconnect(NexusClosureCause.EXPLICIT_DISCONNECT, grants)))
        assertEquals(SessionState.ABSENT, store.snapshot.liveness.state)
        assertEquals(null, store.snapshot.session)
        assertEquals(durablePlane, store.authorityPlane)
        assertFalse(store.health.writable)
        assertSame(failure, store.health.fix)
    }

    @Test
    fun corruptAuthorityEnvelopeFailsClosedOnRestartWithoutGrantDisclosure() {
        val store = PhosphorStateStore(
            MemoryPort(PhosphorStoreImage(InitialSnapshots.fortress(10L), authorityPlane = "{\"schema\":\"phosphor.nexus.authority/1\",\"payload\":{},\"checksum_sha256\":\"${"0".repeat(64)}\"}")),
            InitialSnapshots.fortress(10L),
        )
        val fixture = fixture(capabilities = listOf(Capability.OBSERVE_STATE, Capability.CONTROL_DISPLAY), store = store)

        assertRefused(fixture.dispatcher.observe(fixture.projection(Capability.OBSERVE_STATE), 50L), NexusPreAuthRefusalCode.ENTRY_UNAVAILABLE)
        assertIs<PhosphorDispatchResult.Failed>(fixture.dispatcher.dispatch(fixture.dispatchProjection("on", "corrupt"), 50L, 20L))
    }

    @Test
    fun persistentGrantRevocationAndNoncePlaneRestoreWhileTransientGrantClears() {
        val transientGrant = NexusCapabilityGrant(trustKey, Capability.OBSERVE_AUDIT, NexusGrantScope.TRANSIENT, sessionId, "transient-audit")
        val image = NexusAuthorityImage(
            grants = NexusGrantLedger.of(fixture(capabilities = listOf(Capability.OBSERVE_STATE)).grants.grants + transientGrant),
            revokedTokenIds = setOf(tokenId),
            consumedIdempotencyKeys = setOf("idem-restore"),
            consumedNonces = setOf(NexusNonceRecord("nonce-a", 0L, NEXUS_NONCE_RETENTION_MILLIS, "binding-a"), NexusNonceRecord("nonce-b", 0L, NEXUS_NONCE_RETENTION_MILLIS, "binding-b")),
        )
        val encoded = NexusAuthorityCodec.encode(image)
        val decoded = assertIs<NexusAuthorityDecodeResult.Loaded>(NexusAuthorityCodec.decode(encoded)).image

        assertTrue(decoded.grants.allows(trustKey, sessionId, Capability.OBSERVE_STATE))
        assertFalse(decoded.grants.allows(trustKey, sessionId, Capability.OBSERVE_AUDIT))
        assertEquals(setOf(tokenId), decoded.revokedTokenIds)
        assertEquals(setOf("idem-restore"), decoded.consumedIdempotencyKeys)
        assertEquals(setOf("nonce-a", "nonce-b"), decoded.consumedNonces.map { it.value }.toSet())
    }

    @Test
    fun revokeDuringAuthoritySaveLinearizesBeforeLaterObservation() {
        val durableGrants = grants(trustKey, sessionId, listOf(Capability.OBSERVE_STATE))
        val port = BlockingPort(
            PhosphorStoreImage(
                InitialSnapshots.fortress(10L),
                authorityPlane = NexusAuthorityCodec.encode(NexusAuthorityImage(grants = durableGrants)),
            ),
        )
        val store = PhosphorStateStore(port, InitialSnapshots.fortress(10L))
        val fixture = fixture(
            capabilities = listOf(Capability.OBSERVE_STATE),
            grants = durableGrants,
            store = store,
        )
        val projection = fixture.projection(Capability.OBSERVE_STATE)
        var revoked = false
        val revoking = thread(start = true) {
            revoked = fixture.dispatcher.apply(fixture.policy.revokeToken(tokenId, fixture.session, fixture.grants))
        }
        assertTrue(port.saveEntered.await(5, TimeUnit.SECONDS))
        var observed: NexusObservationResult? = null
        val observing = thread(start = true) { observed = fixture.dispatcher.observe(projection, 50L) }
        assertThreadState(observing, Thread.State.BLOCKED)
        port.releaseSave.countDown()
        revoking.join(5_000L)
        observing.join(5_000L)

        assertTrue(revoked)
        assertRefused(requireNotNull(observed), NexusPreAuthRefusalCode.ENTRY_UNAVAILABLE)
    }

    @Test
    fun replayAfterRestartUsesPersistedStoreIdempotencyWithinTwentyFourHourRetention() {
        val port = MemoryPort(PhosphorStoreImage(InitialSnapshots.fortress(10L)))
        val store = PhosphorStateStore(port, InitialSnapshots.fortress(10L))
        val fixture = fixture(capabilities = listOf(Capability.OBSERVE_STATE, Capability.CONTROL_DISPLAY), store = store)
        val first = assertIs<PhosphorDispatchResult.Accepted>(fixture.dispatcher.dispatch(fixture.dispatchProjection("on", "idem-24h"), 50L, 20L))
        val restarted = PhosphorStateStore(MemoryPort(requireNotNull(port.lastSaved)), InitialSnapshots.fortress(10L))
        val restartedFixture = fixture(capabilities = listOf(Capability.OBSERVE_STATE, Capability.CONTROL_DISPLAY), store = restarted)

        val replay = assertIs<PhosphorDispatchResult.Replayed>(restartedFixture.dispatcher.dispatch(restartedFixture.dispatchProjection("on", "idem-24h", expected = first.acknowledgement.revision), 60L, 21L))

        assertEquals(first.acknowledgement.receiptId, replay.acknowledgement.receiptId)
    }

    @Test
    fun consumedNonceSurvivesHeartbeatTransitionRestartAndStillRefusesReplay() {
        val port = MemoryPort(PhosphorStoreImage(InitialSnapshots.fortress(10L)))
        val store = PhosphorStateStore(port, InitialSnapshots.fortress(10L))
        val fixture = tailnetFixture(capabilities = listOf(Capability.OBSERVE_STATE))
        fixture.grants.grants.filter { it.scope == NexusGrantScope.PERSISTENT }.forEachIndexed { index, grant ->
            assertIs<NexusAuthorityTransactionResult.Committed>(
                NexusAuthorityStoreTransaction.grantPersistent(store, grant, 1L + index),
            )
        }
        val dispatcher = NexusObservationDispatcher(store, fixture.session, fixture.token, fixture.grants, fixture.policy)
        val consumption = NexusNonceConsumption(
            value = "nonce-replay",
            binding = "uid=7|pkg=dev.nexus.mobile|request=req-1|client=client-1|challenge=challenge-1|server=server-1|trust=${trustKey.principalStableId}",
            issuedWallTimeMillis = 20L,
            expiresWallTimeMillis = 20L + NEXUS_NONCE_RETENTION_MILLIS,
        )
        assertIs<NexusAuthorityTransactionResult.Committed>(
            NexusAuthorityStoreTransaction.consumeNonce(store, consumption, 20L),
        )

        assertTrue(dispatcher.recordHeartbeat(requireNotNull(fixture.session.id), fixture.session.generation, 500L))
        val afterHeartbeat = assertIs<NexusAuthorityDecodeResult.Loaded>(NexusAuthorityCodec.decode(requireNotNull(port.lastSaved).authorityPlane)).image
        assertEquals(setOf("nonce-replay"), afterHeartbeat.consumedNonces.map { it.value }.toSet())

        val restartedStore = PhosphorStateStore(MemoryPort(requireNotNull(port.lastSaved)), InitialSnapshots.fortress(10L))
        val replay = assertIs<NexusAuthorityTransactionResult.Refused>(
            NexusAuthorityStoreTransaction.consumeNonce(restartedStore, consumption, 21L),
        )
        assertEquals(RefusalCode.IDEMPOTENCY_CONFLICT, replay.refusal.code)
    }

    @Test
    fun storeStateTracksObservingDrivingClosingAndAbsentButRestartClearsTransientProjection() {
        val port = MemoryPort(PhosphorStoreImage(InitialSnapshots.fortress(10L)))
        val store = PhosphorStateStore(port, InitialSnapshots.fortress(10L))
        val fixture = fixture(capabilities = listOf(Capability.OBSERVE_STATE, Capability.CONTROL_DISPLAY), store = store)

        assertEquals(SessionState.OBSERVING, store.snapshot.liveness.state)
        assertEquals(sessionId.value, store.snapshot.session)
        assertTrue(store.snapshot.capabilities.keys.any { it.kind == PrincipalKind.NEXUS })
        assertEquals(SessionState.ABSENT, requireNotNull(port.lastSaved).snapshot.liveness.state)
        assertEquals(null, port.lastSaved?.snapshot?.session)

        val driving = reflectSession(fixture.session, NexusLifecycle.DRIVING, "active-control")
        // Re-open a separate driving fixture to assert the projected driving state without relying on private control transitions.
        val drivingStore = storeWithAuthority(fixture.grants)
        NexusObservationDispatcher(drivingStore, driving, fixture.token, fixture.grants, fixture.policy)
        assertEquals(SessionState.DRIVING, drivingStore.snapshot.liveness.state)

        val tailnet = tailnetFixture(capabilities = listOf(Capability.OBSERVE_STATE))
        val heartbeatPolicy = requireNotNull(tailnet.session.heartbeatPolicy)
        val lastHeartbeat = requireNotNull(tailnet.session.lastHeartbeatMonotonicMillis)
        val closing = tailnet.session.evaluateRemoteLiveness(
            lastHeartbeat + heartbeatPolicy.closingAfterMillis,
            tailnet.grants,
        )
        assertTrue(tailnet.dispatcher.apply(closing))
        assertEquals(SessionState.CLOSING, tailnet.store.snapshot.liveness.state)
        val absent = closing.session.evaluateRemoteLiveness(
            lastHeartbeat + heartbeatPolicy.absentAfterMillis,
            closing.grants,
        )
        assertTrue(tailnet.dispatcher.apply(absent))
        assertEquals(SessionState.ABSENT, tailnet.store.snapshot.liveness.state)

        val restarted = PhosphorStateStore(MemoryPort(requireNotNull(port.lastSaved)), InitialSnapshots.fortress(10L))
        assertEquals(SessionState.ABSENT, restarted.snapshot.liveness.state)
        assertEquals(null, restarted.snapshot.session)
        assertFalse(restarted.snapshot.capabilities.keys.any { it.kind == PrincipalKind.NEXUS })
        val durable = assertIs<NexusAuthorityDecodeResult.Loaded>(NexusAuthorityCodec.decode(restarted.authorityPlane)).image
        assertTrue(durable.grants.allows(trustKey, sessionId, Capability.OBSERVE_STATE))
    }

    @Test
    fun authenticationLifecycleAuditAndReceiptCounterPersistWithAuthorityProjection() {
        val port = MemoryPort(PhosphorStoreImage(InitialSnapshots.fortress(10L)))
        val store = PhosphorStateStore(port, InitialSnapshots.fortress(10L))

        val fixture = fixture(capabilities = listOf(Capability.OBSERVE_STATE), store = store)

        assertIs<NexusSessionAttachResult.Attached>(fixture.dispatcher.attachResult())
        assertEquals(listOf(AuditKind.GRANT, AuditKind.AUTHENTICATION), store.auditRecords.map { it.kind })
        assertEquals("hud-2", store.auditRecords.last().receiptId)
        assertEquals(3L, requireNotNull(port.lastSaved).nextReceiptOrdinal)
        val restarted = PhosphorStateStore(MemoryPort(requireNotNull(port.lastSaved)), InitialSnapshots.fortress(10L))
        assertEquals(listOf(AuditKind.GRANT, AuditKind.AUTHENTICATION), restarted.auditRecords.map { it.kind })
        assertEquals(3L, requireNotNull(port.lastSaved).nextReceiptOrdinal)
    }

    @Test
    fun persistentGrantAndRevokeTransactionsPersistAuditAndAuthorityAtomically() {
        val port = MemoryPort(PhosphorStoreImage(InitialSnapshots.fortress(10L)))
        val store = PhosphorStateStore(port, InitialSnapshots.fortress(10L))
        val fixture = fixture(capabilities = listOf(Capability.OBSERVE_STATE), store = store)
        val grant = NexusCapabilityGrant(trustKey, Capability.OBSERVE_AUDIT, NexusGrantScope.PERSISTENT, null, "grant-audit-receipt")

        assertIs<NexusAuthorityMutationResult.Committed>(fixture.dispatcher.grantPersistent(grant, 30L))
        var durable = assertIs<NexusAuthorityDecodeResult.Loaded>(NexusAuthorityCodec.decode(requireNotNull(port.lastSaved).authorityPlane)).image
        assertTrue(durable.grants.allows(trustKey, sessionId, Capability.OBSERVE_AUDIT))
        assertEquals(listOf(AuditKind.GRANT, AuditKind.AUTHENTICATION, AuditKind.GRANT), store.auditRecords.map { it.kind })

        assertIs<NexusAuthorityMutationResult.Committed>(fixture.dispatcher.revokePersistent(trustKey, Capability.OBSERVE_AUDIT, 31L))
        durable = assertIs<NexusAuthorityDecodeResult.Loaded>(NexusAuthorityCodec.decode(requireNotNull(port.lastSaved).authorityPlane)).image
        assertFalse(durable.grants.allows(trustKey, sessionId, Capability.OBSERVE_AUDIT))
        assertEquals(listOf(AuditKind.GRANT, AuditKind.AUTHENTICATION, AuditKind.GRANT, AuditKind.REVOKE), store.auditRecords.map { it.kind })
        assertEquals(5L, requireNotNull(port.lastSaved).nextReceiptOrdinal)
    }

    @Test
    fun loadedAuthorityDoesNotMergeCallerPersistentGrantsDuringAttach() {
        val durable = NexusAuthorityCodec.encode(NexusAuthorityImage(grants = grants(trustKey, sessionId, listOf(Capability.OBSERVE_STATE))))
        val store = PhosphorStateStore(MemoryPort(PhosphorStoreImage(InitialSnapshots.fortress(10L), authorityPlane = durable)), InitialSnapshots.fortress(10L))
        val session = observingSession(trustKey, tokenId, sessionId, listOf(Capability.OBSERVE_STATE, Capability.OBSERVE_AUDIT))
        val token = NexusTokenGrant.of(tokenId, trustKey, listOf(Capability.OBSERVE_STATE, Capability.OBSERVE_AUDIT), 0L, 100L)
        val callerPersistent = grants(trustKey, sessionId, listOf(Capability.OBSERVE_STATE, Capability.OBSERVE_AUDIT))

        val dispatcher = NexusObservationDispatcher(store, session, token, callerPersistent, NexusTrustPolicy.of())
        assertIs<NexusSessionAttachResult.Refused>(dispatcher.attachResult())
        val restored = assertIs<NexusAuthorityDecodeResult.Loaded>(NexusAuthorityCodec.decode(store.authorityPlane)).image
        assertFalse(restored.grants.allows(trustKey, sessionId, Capability.OBSERVE_AUDIT))
    }

    @Test
    fun freshStoreWithCallerPersistentGrantsRefusesAndLeavesAuthorityAbsent() {
        val store = store()
        val session = observingSession(trustKey, tokenId, sessionId, listOf(Capability.OBSERVE_STATE))
        val token = NexusTokenGrant.of(tokenId, trustKey, listOf(Capability.OBSERVE_STATE), 0L, 100L)
        val callerGrants = grants(trustKey, sessionId, listOf(Capability.OBSERVE_STATE))

        val dispatcher = NexusObservationDispatcher(store, session, token, callerGrants, NexusTrustPolicy.of())

        assertIs<NexusSessionAttachResult.Refused>(dispatcher.attachResult())
        assertRefused(dispatcher.observe(NexusObservationProjection.authorize(session, token, callerGrants, NexusTrustPolicy.of(), Capability.OBSERVE_STATE, 50L), 50L), NexusPreAuthRefusalCode.ENTRY_UNAVAILABLE)
        assertEquals(null, store.authorityPlane)
        assertTrue(store.auditRecords.isEmpty())
        assertEquals(SessionState.ABSENT, store.snapshot.liveness.state)
    }

    @Test
    fun storeLevelGrantSeedsFirstDurableAuthorityAndAuditsInSameSave() {
        val port = MemoryPort(PhosphorStoreImage(InitialSnapshots.fortress(10L)))
        val store = PhosphorStateStore(port, InitialSnapshots.fortress(10L))
        val grant = grants(trustKey, sessionId, listOf(Capability.OBSERVE_STATE)).grants.single()

        assertIs<NexusAuthorityTransactionResult.Committed>(NexusAuthorityStoreTransaction.grantPersistent(store, grant, 30L))
        val saved = requireNotNull(port.lastSaved)
        val durable = assertIs<NexusAuthorityDecodeResult.Loaded>(NexusAuthorityCodec.decode(saved.authorityPlane)).image
        assertTrue(durable.grants.allows(trustKey, sessionId, Capability.OBSERVE_STATE))
        assertEquals(listOf(AuditKind.GRANT), saved.audit.records.map { it.kind })
        assertEquals(2L, saved.nextReceiptOrdinal)
    }

    @Test
    fun storeLevelGrantSaveFailureLeavesAuthorityAuditAndCounterAbsent() {
        val failure = Refusal(RefusalCode.SYSTEM_UNAVAILABLE, "Fix authority storage.", 0L)
        val port = MemoryPort(PhosphorStoreImage(InitialSnapshots.fortress(10L)), failSaves = true, failure = failure)
        val store = PhosphorStateStore(port, InitialSnapshots.fortress(10L))
        val grant = grants(trustKey, sessionId, listOf(Capability.OBSERVE_STATE)).grants.single()

        val result = assertIs<NexusAuthorityTransactionResult.Refused>(NexusAuthorityStoreTransaction.grantPersistent(store, grant, 30L))

        assertSame(failure, result.refusal)
        assertEquals(null, store.authorityPlane)
        assertTrue(store.auditRecords.isEmpty())
        assertEquals(1L, requireNotNull(port.lastSaved).nextReceiptOrdinal)
    }

    @Test
    fun revokedDurableGrantBetweenNonceConsumeAndAttachRefusesWithoutAuthenticationAudit() {
        val port = MemoryPort(PhosphorStoreImage(InitialSnapshots.fortress(10L)))
        val store = PhosphorStateStore(port, InitialSnapshots.fortress(10L))
        val grant = grants(trustKey, sessionId, listOf(Capability.OBSERVE_STATE)).grants.single()
        assertIs<NexusAuthorityTransactionResult.Committed>(NexusAuthorityStoreTransaction.grantPersistent(store, grant, 30L))
        assertIs<NexusAuthorityTransactionResult.Committed>(NexusAuthorityStoreTransaction.consumeNonce(store, NexusNonceConsumption("nonce-before-revoke", "binding-before-revoke", 31L, 31L + NEXUS_NONCE_RETENTION_MILLIS), 31L))
        assertIs<NexusAuthorityTransactionResult.Committed>(NexusAuthorityStoreTransaction.revokePersistent(store, trustKey, Capability.OBSERVE_STATE, 32L))

        val session = observingSession(trustKey, tokenId, sessionId, listOf(Capability.OBSERVE_STATE))
        val token = NexusTokenGrant.of(tokenId, trustKey, listOf(Capability.OBSERVE_STATE), 0L, 100L)
        val dispatcher = NexusObservationDispatcher(store, session, token, grants(trustKey, sessionId, listOf(Capability.OBSERVE_STATE)), NexusTrustPolicy.of())

        assertIs<NexusSessionAttachResult.Refused>(dispatcher.attachResult())
        assertEquals(listOf(AuditKind.GRANT, AuditKind.REVOKE), store.auditRecords.map { it.kind })
    }

    private fun fixture(
        session: NexusSession = observingSession(trustKey, tokenId, sessionId, listOf(Capability.OBSERVE_STATE)),
        token: NexusTokenGrant = NexusTokenGrant.of(tokenId, requireNotNull(session.trustKey), session.authenticatedCapabilityCeiling, 0L, 100L),
        grants: NexusGrantLedger = grants(requireNotNull(session.trustKey), requireNotNull(session.id), session.authenticatedCapabilityCeiling),
        policy: NexusTrustPolicy = NexusTrustPolicy.of(),
        capabilities: Collection<Capability>,
        store: PhosphorStateStore? = null,
    ): Fixture {
        val effectiveSession = if (session.capabilities.containsAll(capabilities)) session else observingSession(
            requireNotNull(session.trustKey),
            requireNotNull(session.tokenId),
            requireNotNull(session.id),
            capabilities,
        )
        val effectiveGrants = if (grants.grants.map { it.capability }.containsAll(capabilities)) grants else grants(
            requireNotNull(effectiveSession.trustKey),
            requireNotNull(effectiveSession.id),
            capabilities,
        )
        val effectiveToken = if (token.capabilities.containsAll(capabilities)) token else NexusTokenGrant.of(
            requireNotNull(effectiveSession.tokenId),
            requireNotNull(effectiveSession.trustKey),
            capabilities,
            0L,
            100L,
        )
        val effectiveStore = store ?: storeWithAuthority(effectiveGrants)
        if (store != null && effectiveStore.authorityPlane == null) {
            effectiveGrants.grants.filter { it.scope == NexusGrantScope.PERSISTENT }.forEachIndexed { index, grant ->
                assertIs<NexusAuthorityTransactionResult.Committed>(NexusAuthorityStoreTransaction.grantPersistent(effectiveStore, grant, 1L + index))
            }
        }
        return Fixture(
            session = effectiveSession,
            token = effectiveToken,
            grants = effectiveGrants,
            policy = policy,
            store = effectiveStore,
            dispatcher = NexusObservationDispatcher(effectiveStore, effectiveSession, effectiveToken, effectiveGrants, policy),
        )
    }

    private fun drivingFixture(): Fixture {
        val capabilities = listOf(Capability.OBSERVE_STATE, Capability.CONTROL_TRANSPORT)
        val session = observingSession(trustKey, tokenId, sessionId, capabilities)
        val driving = reflectSession(
            session = session,
            lifecycle = NexusLifecycle.DRIVING,
            activeControlReceiptId = "active-control-receipt",
        )
        val grants = grants(trustKey, sessionId, capabilities)
        val token = NexusTokenGrant.of(tokenId, trustKey, capabilities, 0L, 100L)
        val store = storeWithAuthority(grants)
        return Fixture(
            driving,
            token,
            grants,
            NexusTrustPolicy.of(),
            store,
            NexusObservationDispatcher(store, driving, token, grants, NexusTrustPolicy.of()),
        )
    }

    private fun tailnetFixture(capabilities: Collection<Capability>): Fixture {
        val key = NexusTrustKey.tailnet(
            nodePrincipal = "nexus-tailnet-node",
            pinnedEndpointIdentity = PinnedEndpointIdentity("sha256:endpoint-a"),
            principalStableId = "nexus-tailnet",
            profile = NexusBuildProfile.NEXUS,
        )
        val session = observingSession(key, tokenId, sessionId, capabilities, NexusReachPath.TAILNET)
        val grants = grants(key, sessionId, capabilities)
        val token = NexusTokenGrant.of(tokenId, key, capabilities, 0L, 10_000L)
        val store = storeWithAuthority(grants)
        return Fixture(
            session,
            token,
            grants,
            NexusTrustPolicy.of(),
            store,
            NexusObservationDispatcher(store, session, token, grants, NexusTrustPolicy.of()),
        )
    }

    private fun observingSession(
        key: NexusTrustKey,
        token: NexusTokenId,
        session: NexusSessionId,
        capabilities: Collection<Capability>,
        reachPath: NexusReachPath = NexusReachPath.BINDER,
        generation: Long = 1L,
    ): NexusSession {
        val auth = authenticatedIdentity(key, reachPath, session, token, dev.phosphor.mobil3.state.FrozenSet.copyOf(capabilities))
        val authenticating = NexusSession.authenticating(auth, NexusGeneration(generation), 0L)
        return authenticating.establish(grants(key, session, capabilities))
    }

    private fun grants(key: NexusTrustKey, session: NexusSessionId, capabilities: Collection<Capability>): NexusGrantLedger =
        NexusGrantLedger.of(capabilities.mapIndexed { index, capability ->
            NexusCapabilityGrant(key, capability, NexusGrantScope.PERSISTENT, null, "grant-$index-${capability.wireName}")
        })

    private fun Fixture.projection(capability: Capability): NexusObservationProjection = NexusObservationProjection.authorize(
        session = session,
        token = token,
        grants = grants,
        trustPolicy = policy,
        capability = capability,
        nowMonotonicMillis = 50L,
    )

    private fun Fixture.dispatchProjection(mode: String, key: String, expected: Long = 0L): NexusDispatchProjection = NexusDispatchProjection.authorize(
        session = session,
        token = token,
        grants = grants,
        trustPolicy = policy,
        action = SetDisplayHud(mode),
        request = ActionRequest(
            principal = PrincipalId(PrincipalKind.NEXUS, requireNotNull(session.trustKey).principalStableId),
            idempotencyKey = key,
            expectedRevision = expected,
            reason = "test nexus dispatch",
            requestedCapability = Capability.CONTROL_DISPLAY,
            transport = Transport.BINDER,
            sessionId = requireNotNull(session.id).value,
        ),
        nowMonotonicMillis = 50L,
    )

    private fun store(snapshot: PhosphorStateSnapshot = InitialSnapshots.fortress(10L)): PhosphorStateStore =
        PhosphorStateStore(MemoryPort(PhosphorStoreImage(snapshot)), snapshot)

    private fun storeWithAuthority(grants: NexusGrantLedger, snapshot: PhosphorStateSnapshot = InitialSnapshots.fortress(10L)): PhosphorStateStore =
        PhosphorStateStore(MemoryPort(PhosphorStoreImage(snapshot, authorityPlane = NexusAuthorityCodec.encode(NexusAuthorityImage(grants = grants)))), snapshot)

    private fun assertRefused(result: NexusObservationResult, code: NexusPreAuthRefusalCode) {
        val refused = assertIs<NexusObservationResult.Refused>(result)
        assertEquals(setOf("refusal"), payloadFields(NexusObservationResult.Refused::class.java))
        val refusal = refused.refusal
        assertEquals(code, refusal.code)
        assertEquals(setOf("code", "fix"), payloadFields(refusal::class.java))
    }

    private fun reflectTransition(base: NexusSessionTransition, replacementGrants: NexusGrantLedger): NexusSessionTransition {
        val type = Class.forName("dev.phosphor.mobil3.nexus.LifecycleSessionTransition")
        val constructor = type.declaredConstructors.single { it.parameterCount == 5 }
        constructor.isAccessible = true
        return constructor.newInstance(
            base.previousSession,
            base.previousSessionId.value,
            base.previousGeneration.value,
            base.session,
            replacementGrants,
        ) as NexusSessionTransition
    }

    private fun reflectSession(
        session: NexusSession,
        lifecycle: NexusLifecycle,
        activeControlReceiptId: String?,
    ): NexusSession {
        val constructor = NexusSession::class.java.declaredConstructors.single { it.parameterCount == 12 }
        constructor.isAccessible = true
        return constructor.newInstance(
            session.id?.value,
            session.trustKey,
            session.tokenId?.value,
            session.reachPath,
            session.generation.value,
            lifecycle,
            session.authenticatedCapabilityCeiling,
            session.capabilities,
            session.heartbeatPolicy,
            session.lastHeartbeatMonotonicMillis,
            activeControlReceiptId,
            null,
        ) as NexusSession
    }

    private fun payloadFields(type: Class<*>): Set<String> = type.declaredFields
        .filterNot { it.isSynthetic || java.lang.reflect.Modifier.isStatic(it.modifiers) }
        .map { it.name }
        .toSet()

    private fun assertThreadState(thread: Thread, expected: Thread.State) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            if (thread.state == expected) return
            Thread.yield()
        }
        assertEquals(expected, thread.state)
    }

    private fun assertFailsUnsupported(block: () -> Unit) {
        try {
            block()
        } catch (_: RuntimeException) {
            return
        }
        error("expected immutable collection to reject mutation")
    }

    private fun List<*>.addForProbe() {
        @Suppress("UNCHECKED_CAST")
        (this as MutableList<Any?>).add(null)
    }

    private data class Fixture(
        val session: NexusSession,
        val token: NexusTokenGrant,
        val grants: NexusGrantLedger,
        val policy: NexusTrustPolicy,
        val store: PhosphorStateStore,
        val dispatcher: NexusObservationDispatcher,
    )

    private fun authenticatedIdentity(
        trustKey: NexusTrustKey,
        reachPath: NexusReachPath,
        sessionId: NexusSessionId,
        tokenId: NexusTokenId,
        capabilities: dev.phosphor.mobil3.state.FrozenSet<Capability>,
    ): NexusAuthenticatedIdentity {
        val type = Class.forName("dev.phosphor.mobil3.nexus.PolicyAuthenticatedIdentity")
        val constructor = type.declaredConstructors.single { it.parameterCount == 6 }
        constructor.isAccessible = true
        return constructor.newInstance(trustKey, reachPath, sessionId.value, tokenId.value, capabilities, 1_000L) as NexusAuthenticatedIdentity
    }

    private open class MemoryPort(
        image: PhosphorStoreImage,
        private val failSaves: Boolean = false,
        private val failSavesAfter: Int? = null,
        private val failure: Refusal = Refusal(RefusalCode.SYSTEM_UNAVAILABLE, "Fix persistence.", 0L),
    ) : PhosphorStatePersistencePort {
        private var image: PhosphorStoreImage = image
        var saves: Int = 0
            private set
        val lastSaved: PhosphorStoreImage? get() = image

        override fun load(): PhosphorStoreLoadResult = PhosphorStoreLoadResult.CausalImage(image)
        override fun save(image: PhosphorStoreImage): PhosphorStorePersistenceResult {
            saves += 1
            if (failSaves || (failSavesAfter != null && saves > failSavesAfter)) return PhosphorStorePersistenceResult.Failed(failure)
            this.image = image
            return PhosphorStorePersistenceResult.Saved
        }
    }

    private class BlockingPort(
        image: PhosphorStoreImage,
        private val blockOnAttempt: Int = 2,
    ) : MemoryPort(image) {
        val saveEntered = CountDownLatch(1)
        val releaseSave = CountDownLatch(1)
        private var attempts = 0
        override fun save(image: PhosphorStoreImage): PhosphorStorePersistenceResult {
            attempts += 1
            if (attempts >= blockOnAttempt) {
                saveEntered.countDown()
                assertTrue(releaseSave.await(5, TimeUnit.SECONDS))
            }
            return super.save(image)
        }
    }
}
