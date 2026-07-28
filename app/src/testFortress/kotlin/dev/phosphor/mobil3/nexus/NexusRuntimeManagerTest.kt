package dev.phosphor.mobil3.nexus

import dev.phosphor.mobil3.state.Capability
import dev.phosphor.mobil3.state.FrozenSet
import dev.phosphor.mobil3.state.InitialSnapshots
import dev.phosphor.mobil3.state.Refusal
import dev.phosphor.mobil3.state.RefusalCode
import dev.phosphor.mobil3.state.SessionState
import dev.phosphor.mobil3.store.PhosphorStatePersistencePort
import dev.phosphor.mobil3.store.PhosphorStateStore
import dev.phosphor.mobil3.store.PhosphorStoreImage
import dev.phosphor.mobil3.store.PhosphorStoreLoadResult
import dev.phosphor.mobil3.store.PhosphorStorePersistenceResult
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertIs
import org.junit.Test

class NexusRuntimeManagerTest {
    private val trustKey = NexusTrustKey.android(
        packageName = "dev.nexus.mobile",
        signing = AndroidSigningEvidence.of(CertificateSha256("a".repeat(64))),
        principalStableId = "nexus-mobile",
        profile = NexusBuildProfile.NEXUS,
    )

    @Test fun attachBinderDoesNotPublishLocalAuthorityWhenInitialDurableSaveFails() {
        val store = PhosphorStateStore(
            MemoryPort(PhosphorStoreImage(InitialSnapshots.fortress(10L)), failSaves = true),
            InitialSnapshots.fortress(10L),
        )
        val manager = NexusRuntimeManager(store)
        val session = observingSession("session-fail", "token-fail", NexusGeneration(1))
        val token = NexusTokenGrant.of(requireNotNull(session.tokenId), trustKey, session.capabilities, 0L, 10_000L)
        val grants = grants(requireNotNull(session.id), session.capabilities)

        assertFailsWith<IllegalStateException> {
            manager.attachBinder(session, token, grants, NexusTrustPolicy.of())
        }
        assertFalse(manager.confirmBinderAlive(requireNotNull(session.id), session.generation, requireNotNull(session.tokenId)))
        assertFailsWith<IllegalStateException> { manager.dispatcher() }
    }

    @Test fun attachBinderRestoresPriorManagerStateWhenReplacementAttachFails() {
        val store = PhosphorStateStore(
            MemoryPort(PhosphorStoreImage(InitialSnapshots.fortress(10L)), failSavesAfter = 3),
            InitialSnapshots.fortress(10L),
        )
        val manager = NexusRuntimeManager(store)
        val first = observingSession("session-first", "token-first", NexusGeneration(1))
        val firstToken = NexusTokenGrant.of(requireNotNull(first.tokenId), trustKey, first.capabilities, 0L, 10_000L)
        val firstGrants = grants(requireNotNull(first.id), first.capabilities)
        seedPersistentGrants(store, firstGrants)
        manager.attachBinder(first, firstToken, firstGrants, NexusTrustPolicy.of())
        assertTrue(manager.confirmBinderAlive(requireNotNull(first.id), first.generation, requireNotNull(first.tokenId)))

        val replacement = observingSession("session-replacement", "token-replacement", NexusGeneration(2))
        val replacementToken = NexusTokenGrant.of(requireNotNull(replacement.tokenId), trustKey, replacement.capabilities, 0L, 10_000L)
        val replacementGrants = grants(requireNotNull(replacement.id), replacement.capabilities)

        assertFailsWith<IllegalStateException> {
            manager.attachBinder(replacement, replacementToken, replacementGrants, NexusTrustPolicy.of())
        }
        assertTrue(manager.confirmBinderAlive(requireNotNull(first.id), first.generation, requireNotNull(first.tokenId)))
        assertFalse(manager.confirmBinderAlive(requireNotNull(replacement.id), replacement.generation, requireNotNull(replacement.tokenId)))
    }

    @Test fun tailnetHeartbeatClosingAndAbsentAdvanceTheExactSharedDispatcherState() {
        val tailnetTrust = NexusTrustKey.tailnet(
            nodePrincipal = "nexus-tailnet-node",
            pinnedEndpointIdentity = PinnedEndpointIdentity("relay-ed25519:abc123"),
            principalStableId = "nexus-tailnet-node",
            profile = NexusBuildProfile.LOCAL_DEV,
        )
        val store = PhosphorStateStore(
            MemoryPort(PhosphorStoreImage(InitialSnapshots.fortress(10L))),
            InitialSnapshots.fortress(10L),
        )
        val manager = NexusRuntimeManager(store)
        val sessionId = NexusSessionId("tailnet-session")
        val tokenId = NexusTokenId("tailnet-token")
        val durable = NexusGrantLedger.of(
            listOf(
                NexusCapabilityGrant(
                    tailnetTrust,
                    Capability.OBSERVE_STATE,
                    NexusGrantScope.PERSISTENT,
                    null,
                    "tailnet-observe-grant",
                ),
            ),
        )
        seedPersistentGrants(store, durable)

        val attached = manager.attachTailnet(
            trustKey = tailnetTrust,
            sessionId = sessionId,
            tokenId = tokenId,
            generation = NexusGeneration(1),
            capabilities = setOf(Capability.OBSERVE_STATE),
            durableGrants = durable,
            trustPolicy = NexusTrustPolicy.of(),
            heartbeatIntervalMillis = 1_000L,
            leaseMillis = 4_000L,
            nowMonotonicMillis = 0L,
        )
        val refreshed = requireNotNull(
            manager.recordTailnetHeartbeat(sessionId, attached.session.generation, 100L),
        )
        assertEquals(100L, refreshed.session.lastHeartbeatMonotonicMillis)

        val closing = requireNotNull(manager.evaluateTailnetLiveness(sessionId, 3_100L))
        assertEquals(NexusLifecycle.CLOSING, closing.session.lifecycle)
        assertFalse(closing.closed)
        assertEquals(SessionState.CLOSING, store.snapshot.liveness.state)
        assertEquals(null, manager.recordTailnetHeartbeat(sessionId, attached.session.generation, 3_101L))

        val absent = requireNotNull(manager.evaluateTailnetLiveness(sessionId, 4_100L))
        assertEquals(NexusLifecycle.ABSENT, absent.session.lifecycle)
        assertTrue(absent.closed)
        assertEquals(SessionState.ABSENT, store.snapshot.liveness.state)
        assertFailsWith<IllegalStateException> { manager.dispatcher() }
    }

    private fun observingSession(sessionValue: String, tokenValue: String, generation: NexusGeneration): NexusSession {
        val sessionId = NexusSessionId(sessionValue)
        val tokenId = NexusTokenId(tokenValue)
        val capabilities = FrozenSet.copyOf(setOf(Capability.OBSERVE_STATE))
        val auth = authenticatedIdentity(trustKey, NexusReachPath.BINDER, sessionId, tokenId, capabilities)
        return NexusSession.authenticating(auth, generation, 0L).establish(grants(sessionId, capabilities))
    }

    private fun grants(session: NexusSessionId, capabilities: Collection<Capability>): NexusGrantLedger =
        NexusGrantLedger.of(capabilities.mapIndexed { index, capability ->
            NexusCapabilityGrant(trustKey, capability, NexusGrantScope.PERSISTENT, null, "manager-grant-$index-${capability.wireName}")
        })

    private fun seedPersistentGrants(store: PhosphorStateStore, ledger: NexusGrantLedger) {
        ledger.grants.forEachIndexed { index, grant ->
            assertIs<NexusAuthorityTransactionResult.Committed>(
                NexusAuthorityStoreTransaction.grantPersistent(store, grant, 1_000L + index),
            )
        }
    }

    private fun authenticatedIdentity(
        trustKey: NexusTrustKey,
        reachPath: NexusReachPath,
        sessionId: NexusSessionId,
        tokenId: NexusTokenId,
        capabilities: FrozenSet<Capability>,
    ): NexusAuthenticatedIdentity {
        val type = Class.forName("dev.phosphor.mobil3.nexus.PolicyAuthenticatedIdentity")
        val constructor = type.declaredConstructors.single { it.parameterCount == 6 }
        constructor.isAccessible = true
        return constructor.newInstance(trustKey, reachPath, sessionId.value, tokenId.value, capabilities, 1_000L) as NexusAuthenticatedIdentity
    }

    private class MemoryPort(
        image: PhosphorStoreImage,
        private val failSaves: Boolean = false,
        private val failSavesAfter: Int? = null,
        private val failure: Refusal = Refusal(RefusalCode.SYSTEM_UNAVAILABLE, "Fix persistence.", 0L),
    ) : PhosphorStatePersistencePort {
        private var image: PhosphorStoreImage = image
        private var saves: Int = 0
        override fun load(): PhosphorStoreLoadResult = PhosphorStoreLoadResult.CausalImage(image)
        override fun save(image: PhosphorStoreImage): PhosphorStorePersistenceResult {
            saves += 1
            if (failSaves || (failSavesAfter != null && saves > failSavesAfter)) return PhosphorStorePersistenceResult.Failed(failure)
            this.image = image
            return PhosphorStorePersistenceResult.Saved
        }
    }
}
