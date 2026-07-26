package dev.phosphor.mobil3.nexus

import dev.phosphor.mobil3.state.ActionRequest
import dev.phosphor.mobil3.state.AcceptedActionRecord
import dev.phosphor.mobil3.state.ActionAcknowledgement
import dev.phosphor.mobil3.state.AuditKind
import dev.phosphor.mobil3.state.AuditRecord
import dev.phosphor.mobil3.state.BooleanStateValue
import dev.phosphor.mobil3.state.Capability
import dev.phosphor.mobil3.state.InitialSnapshots
import dev.phosphor.mobil3.state.PrincipalId
import dev.phosphor.mobil3.state.PrincipalKind
import dev.phosphor.mobil3.state.ProvenanceStamp
import dev.phosphor.mobil3.state.SettingsRowProjection
import dev.phosphor.mobil3.state.SettingsSchema
import dev.phosphor.mobil3.state.StateChange
import dev.phosphor.mobil3.state.StateDelta
import dev.phosphor.mobil3.state.StateField
import dev.phosphor.mobil3.state.TogglePlayback
import dev.phosphor.mobil3.state.Transport
import dev.phosphor.mobil3.state.frozenListOf
import dev.phosphor.mobil3.state.frozenMapOf
import dev.phosphor.mobil3.state.frozenSetOf
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Test

class NexusContractsTest {
    private val certA = CertificateSha256("a".repeat(64))
    private val certB = CertificateSha256("b".repeat(64))
    private val certC = CertificateSha256("c".repeat(64))
    private val endpointA = PinnedEndpointIdentity("sha256:endpoint-a")
    private val endpointB = PinnedEndpointIdentity("sha256:endpoint-b")
    private val sessionId = NexusSessionId("session-1")
    private val tokenId = NexusTokenId("token-1")

    private fun androidProof(
        packageName: String = "dev.nexus.mobile",
        current: CertificateSha256 = certA,
        history: Collection<CertificateSha256> = listOf(certA),
        principalStableId: String = "nexus-mobile",
        profile: NexusBuildProfile = NexusBuildProfile.NEXUS,
    ): AndroidPeerProof = AndroidPeerProof(
        packageName = packageName,
        signing = AndroidSigningEvidence.of(current, history),
        principalStableId = principalStableId,
        profile = profile,
    )

    private fun tailnetProof(
        nodePrincipal: String = "nexus-tailnet-node",
        endpoint: PinnedEndpointIdentity = endpointA,
        principalStableId: String = "nexus-tailnet",
        profile: NexusBuildProfile = NexusBuildProfile.NEXUS,
    ): TailnetPeerProof = TailnetPeerProof(
        nodePrincipal = nodePrincipal,
        pinnedEndpointIdentity = endpoint,
        principalStableId = principalStableId,
        profile = profile,
    )

    private fun androidEnrollment(
        packageName: String = "dev.nexus.mobile",
        principalStableId: String = "nexus-mobile",
        acceptedCurrent: Collection<CertificateSha256> = listOf(certA),
        acceptedLineage: Collection<CertificateSha256> = listOf(certA),
        revoked: Collection<CertificateSha256> = emptySet(),
        profiles: Collection<NexusBuildProfile> = listOf(NexusBuildProfile.NEXUS),
        capabilities: Collection<Capability> = listOf(
            Capability.OBSERVE_STATE,
            Capability.CONTROL_TRANSPORT,
        ),
        developmentOnly: Boolean = false,
        enrollmentId: String = "nexus-production",
    ): AndroidTrustEnrollment = AndroidTrustEnrollment.of(
        enrollmentId = enrollmentId,
        packageName = packageName,
        principalStableId = principalStableId,
        acceptedCurrentCertificates = acceptedCurrent,
        acceptedSigningLineage = acceptedLineage,
        revokedCertificates = revoked,
        allowedProfiles = profiles,
        maximumCapabilities = capabilities,
        developmentOnly = developmentOnly,
    )

    private fun tailnetEnrollment(
        nodePrincipal: String = "nexus-tailnet-node",
        principalStableId: String = "nexus-tailnet",
        endpoint: PinnedEndpointIdentity = endpointA,
        profiles: Collection<NexusBuildProfile> = listOf(NexusBuildProfile.NEXUS),
        developmentOnly: Boolean = false,
        enrollmentId: String = "tailnet-production",
    ): TailnetTrustEnrollment = TailnetTrustEnrollment.of(
        enrollmentId = enrollmentId,
        nodePrincipal = nodePrincipal,
        principalStableId = principalStableId,
        acceptedEndpointIdentities = listOf(endpoint),
        allowedProfiles = profiles,
        maximumCapabilities = listOf(Capability.OBSERVE_STATE, Capability.CONTROL_TRANSPORT),
        developmentOnly = developmentOnly,
    )

    /** Test-only seam. Production Phase 05a intentionally exposes no issuer factory. */
    private class TestServerAuthority(
        private val currentLedger: NexusNonceLedger,
    ) {
        fun issue(
            id: NexusChallengeId,
            reachPath: NexusReachPath,
            sessionId: NexusSessionId,
            serverNonce: NexusNonce,
            issuedMonotonicMillis: Long,
            expiresMonotonicMillis: Long,
        ): NexusChallengeIssuance {
            val issuerClass = Class.forName("dev.phosphor.mobil3.nexus.NexusNonceLedger\$ServerIssuer")
            val constructor = issuerClass.declaredConstructors.single { it.parameterCount == 1 }
            constructor.isAccessible = true
            val issuer = constructor.newInstance(currentLedger)
            val issue = issuerClass.declaredMethods.single {
                it.name.startsWith("issue-") && it.parameterCount == 6
            }
            issue.isAccessible = true
            return try {
                issue.invoke(
                    issuer,
                    id.value,
                    reachPath,
                    sessionId.value,
                    serverNonce.value,
                    issuedMonotonicMillis,
                    expiresMonotonicMillis,
                ) as NexusChallengeIssuance
            } catch (failure: java.lang.reflect.InvocationTargetException) {
                throw failure.targetException
            }
        }
    }

    private fun serverAuthority(currentLedger: NexusNonceLedger? = null): TestServerAuthority {
        val ledger = currentLedger ?: run {
            val constructor = NexusNonceLedger::class.java.declaredConstructors.single { it.parameterCount == 4 }
            constructor.isAccessible = true
            constructor.newInstance(
                frozenSetOf<NexusChallenge>(),
                frozenSetOf<NexusChallengeId>(),
                frozenSetOf<NexusNonce>(),
                frozenSetOf<NexusSessionId>(),
            ) as NexusNonceLedger
        }
        return TestServerAuthority(ledger)
    }

    private fun challenge(path: NexusReachPath = NexusReachPath.BINDER): NexusChallengeIssuance = serverAuthority().issue(
        id = NexusChallengeId("challenge-1"),
        reachPath = path,
        sessionId = sessionId,
        serverNonce = NexusNonce("server-nonce"),
        issuedMonotonicMillis = 10L,
        expiresMonotonicMillis = 100L,
    )

    private fun authRequest(
        proof: NexusPeerProof = androidProof(),
        path: NexusReachPath = proof.reachPath,
        requested: Collection<Capability> = listOf(
            Capability.OBSERVE_STATE,
            Capability.CONTROL_TRANSPORT,
        ),
        presentedTokenId: NexusTokenId = tokenId,
        explicitDevelopmentEnrollmentId: String? = null,
    ): NexusAuthRequest = NexusAuthRequest.of(
        challengeId = NexusChallengeId("challenge-1"),
        reachPath = path,
        serverNonce = NexusNonce("server-nonce"),
        clientNonce = NexusNonce("client-nonce"),
        sessionId = sessionId,
        tokenId = presentedTokenId,
        proof = proof,
        requestedCapabilities = requested,
        heartbeatIntervalMillis = 2_000L,
        explicitDevelopmentEnrollmentId = explicitDevelopmentEnrollmentId,
    )

    private fun token(
        proof: NexusPeerProof = androidProof(),
        capabilities: Collection<Capability> = listOf(
            Capability.OBSERVE_STATE,
            Capability.CONTROL_TRANSPORT,
        ),
        id: NexusTokenId = tokenId,
    ): NexusTokenGrant = NexusTokenGrant.of(
        id = id,
        trustKey = proof.trustKey,
        capabilities = capabilities,
        issuedMonotonicMillis = 0L,
        expiresMonotonicMillis = 1_000L,
    )

    private fun acceptedControlAction(
        principalStableId: String = "nexus-mobile",
        acceptedSessionId: String = sessionId.value,
        transport: Transport = Transport.BINDER,
        changed: Boolean = true,
    ): AcceptedActionRecord {
        val action = TogglePlayback
        val principal = PrincipalId(PrincipalKind.NEXUS, principalStableId)
        val request = ActionRequest(
            principal = principal,
            idempotencyKey = "accepted-control-key",
            expectedRevision = 0L,
            reason = "Nexus accepted transport control",
            requestedCapability = Capability.CONTROL_TRANSPORT,
            transport = transport,
            sessionId = acceptedSessionId,
        )
        val previous = InitialSnapshots.play()
        val stamp = ProvenanceStamp(
            receiptId = "accepted-control-receipt",
            writer = principal,
            reason = request.reason,
            transport = transport,
            sessionId = acceptedSessionId,
            monotonicMillis = 20L,
            wallTimeMillis = 20L,
        )
        val next = if (changed) {
            previous.copy(
                revision = 1L,
                sequence = 1L,
                wallTimeMillis = 20L,
                desired = previous.desired.copy(playbackActive = true),
                effective = previous.effective.copy(playbackActive = true),
                provenance = frozenMapOf(StateField.PLAYBACK_ACTIVE to stamp),
            )
        } else {
            previous
        }
        val delta = if (changed) {
            StateDelta(
                revision = 1L,
                sequence = 1L,
                changes = frozenListOf(
                    StateChange(
                        field = StateField.PLAYBACK_ACTIVE,
                        oldValue = BooleanStateValue(false),
                        newValue = BooleanStateValue(true),
                        provenance = stamp,
                    ),
                ),
                provenance = stamp,
            )
        } else {
            null
        }
        val acknowledgement = ActionAcknowledgement(
            receiptId = stamp.receiptId,
            actionType = action.type,
            changed = changed,
            revision = next.revision,
            sequence = next.sequence,
            effectiveValue = BooleanStateValue(changed),
            provenance = stamp,
            delta = delta,
            effects = frozenListOf(),
        )
        val audit = AuditRecord(
            receiptId = stamp.receiptId,
            kind = if (changed) AuditKind.ACTION else AuditKind.NO_OP,
            wallTimeMillis = stamp.wallTimeMillis,
            actionType = action.type,
            fields = action.type.affectedFields,
            provenance = stamp,
        )
        val projections = if (changed) {
            val schema = SettingsSchema.rows.single { it.field == StateField.PLAYBACK_ACTIVE }
            frozenListOf(
                SettingsRowProjection(
                    schema = schema,
                    desired = BooleanStateValue(true),
                    effective = BooleanStateValue(true),
                    availability = next.availability.getValue(StateField.PLAYBACK_ACTIVE),
                    authority = next.authority.getValue(StateField.PLAYBACK_ACTIVE),
                    provenance = stamp,
                    visibleLastWriter = stamp,
                ),
            )
        } else {
            frozenListOf()
        }
        return AcceptedActionRecord(
            request = request,
            action = action,
            canonicalPayload = action.canonicalPayload(),
            acknowledgement = acknowledgement,
            previousSnapshot = previous,
            snapshot = next,
            audit = audit,
            settingsProjections = projections,
        )
    }

    private fun authenticatedIdentity(
        proof: NexusPeerProof = androidProof(),
        capabilities: Collection<Capability> = listOf(
            Capability.OBSERVE_STATE,
            Capability.CONTROL_TRANSPORT,
        ),
        authenticatedSessionId: NexusSessionId = sessionId,
    ): NexusAuthenticatedIdentity {
        val developmentEnrollmentId = "test-auth-development"
        val developmentOnly = proof.profile == NexusBuildProfile.LOCAL_DEV
        val policy = when (proof) {
            is AndroidPeerProof -> NexusTrustPolicy.of(
                androidEnrollments = listOf(
                    AndroidTrustEnrollment.of(
                        enrollmentId = if (developmentOnly) developmentEnrollmentId else "test-auth-production",
                        packageName = proof.packageName,
                        principalStableId = proof.principalStableId,
                        acceptedCurrentCertificates = listOf(proof.signing.current),
                        acceptedSigningLineage = proof.signing.history,
                        allowedProfiles = listOf(proof.profile),
                        maximumCapabilities = capabilities,
                        developmentOnly = developmentOnly,
                    ),
                ),
            )
            is TailnetPeerProof -> NexusTrustPolicy.of(
                tailnetEnrollments = listOf(
                    TailnetTrustEnrollment.of(
                        enrollmentId = if (developmentOnly) developmentEnrollmentId else "test-auth-production",
                        nodePrincipal = proof.nodePrincipal,
                        principalStableId = proof.principalStableId,
                        acceptedEndpointIdentities = listOf(proof.pinnedEndpointIdentity),
                        allowedProfiles = listOf(proof.profile),
                        maximumCapabilities = capabilities,
                        developmentOnly = developmentOnly,
                    ),
                ),
            )
        }
        val issuance = serverAuthority().issue(
            id = NexusChallengeId("identity-challenge-${proof.reachPath.wireName}"),
            reachPath = proof.reachPath,
            sessionId = authenticatedSessionId,
            serverNonce = NexusNonce("identity-server-${proof.reachPath.wireName}"),
            issuedMonotonicMillis = 0L,
            expiresMonotonicMillis = 100L,
        )
        val challenge = issuance.challenge
        val request = NexusAuthRequest.of(
            challengeId = challenge.id,
            reachPath = challenge.reachPath,
            serverNonce = challenge.serverNonce,
            clientNonce = NexusNonce("identity-client-${proof.reachPath.wireName}"),
            sessionId = challenge.sessionId,
            tokenId = tokenId,
            proof = proof,
            requestedCapabilities = capabilities,
            heartbeatIntervalMillis = 1_000L,
            explicitDevelopmentEnrollmentId = developmentEnrollmentId.takeIf { developmentOnly },
        )
        val result = policy.authenticate(
            issuance = issuance,
            request = request,
            token = NexusTokenGrant.of(tokenId, proof.trustKey, capabilities, 0L, 100L),
            nonceLedger = issuance.ledger,
            nowMonotonicMillis = 50L,
            entryPolicy = if (developmentOnly) {
                NexusAgentEntryPolicy.localDevelopment(listOf(proof.reachPath))
            } else {
                NexusAgentEntryPolicy.fortressActivated(listOf(proof.reachPath))
            },
        )
        return assertIs<NexusAuthenticationResult.Accepted>(result).authentication
    }

    private fun grantLedger(
        proof: NexusPeerProof = androidProof(),
        includeObserve: Boolean = true,
        includeControl: Boolean = true,
        controlSessionId: NexusSessionId = sessionId,
    ): NexusGrantLedger {
        val grants = buildList {
            if (includeObserve) {
                add(
                    NexusCapabilityGrant(
                        trustKey = proof.trustKey,
                        capability = Capability.OBSERVE_STATE,
                        scope = NexusGrantScope.PERSISTENT,
                        sessionId = null,
                        receiptId = "grant-observe",
                    ),
                )
            }
            if (includeControl) {
                add(
                    NexusCapabilityGrant(
                        trustKey = proof.trustKey,
                        capability = Capability.CONTROL_TRANSPORT,
                        scope = NexusGrantScope.TRANSIENT,
                        sessionId = controlSessionId,
                        receiptId = "grant-control",
                    ),
                )
            }
        }
        return NexusGrantLedger.of(grants)
    }

    private fun observingSession(
        proof: NexusPeerProof = androidProof(),
        authenticatedCapabilities: Collection<Capability> = listOf(
            Capability.OBSERVE_STATE,
            Capability.CONTROL_TRANSPORT,
        ),
        ledger: NexusGrantLedger = grantLedger(proof),
        generation: Long = 1L,
        observedSessionId: NexusSessionId = sessionId,
    ): NexusSession = NexusSession.authenticating(
        authentication = authenticatedIdentity(proof, authenticatedCapabilities, observedSessionId),
        generation = NexusGeneration(generation),
        nowMonotonicMillis = 0L,
    ).establish(ledger)

    private fun fortressSource(fileName: String): String {
        val relative = "src/fortress/kotlin/dev/phosphor/mobil3/nexus/$fileName"
        return listOf(File(relative), File("app/$relative"))
            .firstOrNull { it.isFile }
            ?.readText()
            ?: error("cannot locate Fortress Nexus source $fileName")
    }

    @Test
    fun trustProofsAreTypedAndCannotCrossBinderAndTailnetEvidence() {
        assertFailsWith<IllegalArgumentException> { CertificateSha256("A".repeat(64)) }
        assertFailsWith<IllegalArgumentException> {
            AndroidSigningEvidence.of(certA, listOf(certB))
        }
        assertFailsWith<IllegalArgumentException> {
            androidProof(packageName = "not-a-package")
        }
        assertFailsWith<IllegalArgumentException> {
            androidProof(profile = NexusBuildProfile.FORTRESS)
        }
        assertFailsWith<IllegalArgumentException> {
            tailnetProof(profile = NexusBuildProfile.PLAY)
        }
        assertEquals(NexusReachPath.BINDER, androidProof().reachPath)
        assertEquals(NexusTrustKind.ANDROID_PACKAGE, androidProof().trustKey.kind)
        assertEquals(NexusReachPath.TAILNET, tailnetProof().reachPath)
        assertEquals(NexusTrustKind.TAILNET_NODE, tailnetProof().trustKey.kind)
        assertNull(tailnetProof().trustKey.currentCertificate)
        assertTrue(tailnetProof().trustKey.signingHistory.isEmpty())
    }

    @Test
    fun collectionInputsAreDefensivelyCopied() {
        val history = mutableSetOf(certA)
        val evidence = AndroidSigningEvidence.of(certA, history)
        history += certB
        assertEquals(frozenListOf(certA), evidence.history)

        val capabilities = mutableSetOf(Capability.OBSERVE_STATE)
        val enrollment = androidEnrollment(capabilities = capabilities)
        capabilities += Capability.CONTROL_FORTRESS
        assertEquals(frozenSetOf(Capability.OBSERVE_STATE), enrollment.maximumCapabilities)

        val grants = mutableSetOf(
            NexusCapabilityGrant(
                androidProof().trustKey,
                Capability.OBSERVE_STATE,
                NexusGrantScope.PERSISTENT,
                null,
                "g1",
            ),
        )
        val ledger = NexusGrantLedger.of(grants)
        grants.clear()
        assertEquals(1, ledger.grants.size)
    }

    @Test
    fun androidEnrollmentRequiresExactPackageCurrentCertificateLineageProfileAndDevelopmentId() {
        val production = androidEnrollment(
            acceptedCurrent = listOf(certB),
            acceptedLineage = listOf(certA, certB),
        )
        val rotatedProof = androidProof(current = certB, history = listOf(certA, certB))
        assertTrue(production.accepts(rotatedProof, null))
        assertFalse(production.accepts(rotatedProof.copy(packageName = "dev.other.mobile"), null))
        assertFalse(production.accepts(androidProof(current = certC, history = listOf(certC)), null))
        assertFalse(production.accepts(androidProof(current = certB, history = listOf(certB)), null))
        assertFalse(production.accepts(androidProof(current = certB, history = listOf(certC, certB)), null))
        assertFalse(production.accepts(rotatedProof.copy(profile = NexusBuildProfile.LOCAL_DEV), null))

        val development = androidEnrollment(
            profiles = listOf(NexusBuildProfile.LOCAL_DEV),
            developmentOnly = true,
            enrollmentId = "local-dev-enrollment",
        )
        val devProof = androidProof(profile = NexusBuildProfile.LOCAL_DEV)
        assertFalse(development.accepts(devProof, null))
        assertFalse(development.accepts(devProof, "wrong-enrollment"))
        assertTrue(development.accepts(devProof, "local-dev-enrollment"))
        assertFailsWith<IllegalArgumentException> {
            androidEnrollment(revoked = listOf(certA))
        }
    }

    @Test
    fun tailnetEnrollmentRequiresExactNodePinProfileAndExplicitDevelopmentEnrollment() {
        val production = tailnetEnrollment()
        assertTrue(production.accepts(tailnetProof(), null))
        assertFalse(production.accepts(tailnetProof(nodePrincipal = "other-node"), null))
        assertFalse(production.accepts(tailnetProof(endpoint = endpointB), null))

        val development = tailnetEnrollment(
            profiles = listOf(NexusBuildProfile.LOCAL_DEV),
            developmentOnly = true,
            enrollmentId = "tailnet-dev",
        )
        val devProof = tailnetProof(profile = NexusBuildProfile.LOCAL_DEV)
        assertFalse(development.accepts(devProof, null))
        assertTrue(development.accepts(devProof, "tailnet-dev"))
    }

    @Test
    fun authRequestRejectsReachPathNonceProfileAndHeartbeatInconsistency() {
        assertFailsWith<IllegalArgumentException> {
            authRequest(path = NexusReachPath.TAILNET)
        }
        assertFailsWith<IllegalArgumentException> {
            NexusAuthRequest.of(
                challengeId = NexusChallengeId("c"),
                reachPath = NexusReachPath.BINDER,
                serverNonce = NexusNonce("same"),
                clientNonce = NexusNonce("same"),
                sessionId = sessionId,
                tokenId = tokenId,
                proof = androidProof(),
                requestedCapabilities = listOf(Capability.OBSERVE_STATE),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            authRequest(proof = androidProof(profile = NexusBuildProfile.LOCAL_DEV))
        }
        assertFailsWith<IllegalArgumentException> {
            authRequest().copy(heartbeatIntervalMillis = 999L)
        }
        assertFailsWith<IllegalArgumentException> {
            authRequest(requested = listOf(Capability.CONTROL_TRANSPORT))
        }
    }

    @Test
    fun nonceLedgerRejectsChallengeMismatchExpiryAndReplay() {
        val issuance = challenge()
        val request = authRequest()
        val consumed = issuance.ledger.consume(issuance, request, 50L)
        assertFailsWith<IllegalArgumentException> { consumed.consume(issuance, request, 50L) }

        val foreign = serverAuthority().issue(
            id = NexusChallengeId("fresh-challenge"),
            reachPath = NexusReachPath.BINDER,
            sessionId = sessionId,
            serverNonce = NexusNonce("fresh-server"),
            issuedMonotonicMillis = 10L,
            expiresMonotonicMillis = 100L,
        )
        assertFailsWith<IllegalArgumentException> {
            consumed.consume(
                foreign,
                request.copy(
                    challengeId = NexusChallengeId("fresh-challenge"),
                    serverNonce = NexusNonce("fresh-server"),
                    clientNonce = NexusNonce("fresh-client"),
                ),
                50L,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            issuance.ledger.consume(issuance, request.copy(challengeId = NexusChallengeId("other")), 50L)
        }
        assertFailsWith<IllegalArgumentException> {
            issuance.ledger.consume(issuance, request.copy(serverNonce = NexusNonce("other")), 50L)
        }
        assertFailsWith<IllegalArgumentException> {
            issuance.ledger.consume(issuance, request, 100L)
        }
        assertFailsWith<IllegalArgumentException> {
            serverAuthority().issue(
                NexusChallengeId("c"),
                NexusReachPath.BINDER,
                NexusSessionId("server-session"),
                NexusNonce("n"),
                Long.MAX_VALUE,
                Long.MAX_VALUE,
            )
        }
    }

    @Test
    fun tokenIsBoundToIdTrustTupleCapabilitiesTimeAndRevocation() {
        val proof = androidProof()
        val token = token(proof)
        assertNull(
            token.validate(
                tokenId,
                proof.trustKey,
                frozenSetOf(Capability.OBSERVE_STATE),
                50L,
            ),
        )
        assertEquals(
            NexusPreAuthRefusalCode.TOKEN_INVALID,
            token.validate(
                NexusTokenId("other"),
                proof.trustKey,
                frozenSetOf(Capability.OBSERVE_STATE),
                50L,
            )?.code,
        )
        assertEquals(
            NexusPreAuthRefusalCode.CAPABILITY_DENIED,
            token.validate(
                tokenId,
                proof.trustKey,
                frozenSetOf(Capability.CONTROL_FORTRESS),
                50L,
            )?.code,
        )
        assertEquals(
            NexusPreAuthRefusalCode.TOKEN_INVALID,
            token.validate(tokenId, proof.trustKey, frozenSetOf(Capability.OBSERVE_STATE), 1_000L)?.code,
        )
        val revoked = token.revoke(75L)
        assertEquals(
            NexusPreAuthRefusalCode.TOKEN_REVOKED,
            revoked.validate(
                tokenId,
                proof.trustKey,
                frozenSetOf(Capability.OBSERVE_STATE),
                80L,
            )?.code,
        )
        assertFailsWith<IllegalArgumentException> { revoked.revoke(76L) }
        assertFailsWith<IllegalArgumentException> {
            token.copy(revokedAtMonotonicMillis = token.expiresMonotonicMillis)
        }
    }

    @Test
    fun trustPolicyAuthenticatesProductionBinderWithoutDisclosingStateOnFailure() {
        val proof = androidProof()
        val policy = NexusTrustPolicy.of(androidEnrollments = listOf(androidEnrollment()))
        val acceptedIssuance = challenge()
        val accepted = policy.authenticate(
            issuance = acceptedIssuance,
            request = authRequest(proof),
            token = token(proof),
            nonceLedger = acceptedIssuance.ledger,
            nowMonotonicMillis = 50L,
            entryPolicy = NexusAgentEntryPolicy.fortressActivated(listOf(NexusReachPath.BINDER)),
        )
        val authenticated = assertIs<NexusAuthenticationResult.Accepted>(accepted).authentication
        assertEquals(proof.trustKey, authenticated.trustKey)
        assertEquals(sessionId, authenticated.sessionId)

        val refusedIssuance = challenge()
        val refused = policy.authenticate(
            issuance = refusedIssuance,
            request = authRequest(proof.copy(packageName = "dev.spoof.mobile")),
            token = token(proof),
            nonceLedger = refusedIssuance.ledger,
            nowMonotonicMillis = 50L,
            entryPolicy = NexusAgentEntryPolicy.fortressActivated(listOf(NexusReachPath.BINDER)),
        )
        val refusal = assertIs<NexusAuthenticationResult.Refused>(refused).refusal
        assertTrue(
            refusal.code == NexusPreAuthRefusalCode.SIGNER_MIGRATION_REQUIRED ||
                refusal.code == NexusPreAuthRefusalCode.TRUST_TUPLE_REJECTED,
        )
        assertEquals(
            setOf("code", "fix"),
            NexusPreAuthRefusal::class.java.declaredFields
                .filterNot { it.isSynthetic || java.lang.reflect.Modifier.isStatic(it.modifiers) }
                .map { it.name }
                .toSet(),
        )
    }

    @Test
    fun unsupportedProtocolReachesTypedPreAuthRefusal() {
        val unsupported = androidProof().copy(protocol = "phosphor.nexus/1")
        val issuance = challenge()
        val result = NexusTrustPolicy.of(androidEnrollments = listOf(androidEnrollment())).authenticate(
            issuance = issuance,
            request = authRequest(unsupported),
            token = token(unsupported),
            nonceLedger = issuance.ledger,
            nowMonotonicMillis = 50L,
            entryPolicy = NexusAgentEntryPolicy.fortressActivated(listOf(NexusReachPath.BINDER)),
        )
        assertEquals(
            NexusPreAuthRefusalCode.PROTOCOL_UNSUPPORTED,
            assertIs<NexusAuthenticationResult.Refused>(result).refusal.code,
        )
    }

    @Test
    fun policyRejectsWrongPresentedTokenIdAndRevokedTokenLedger() {
        val proof = androidProof()
        val basePolicy = NexusTrustPolicy.of(androidEnrollments = listOf(androidEnrollment()))
        val wrongIdIssuance = challenge()
        val wrongId = basePolicy.authenticate(
            wrongIdIssuance,
            authRequest(proof, presentedTokenId = NexusTokenId("presented-other")),
            token(proof),
            wrongIdIssuance.ledger,
            50L,
            NexusAgentEntryPolicy.fortressActivated(listOf(NexusReachPath.BINDER)),
        )
        assertEquals(
            NexusPreAuthRefusalCode.TOKEN_INVALID,
            assertIs<NexusAuthenticationResult.Refused>(wrongId).refusal.code,
        )

        val revokedPolicy = basePolicy.revokeToken(
            tokenId = tokenId,
            currentSession = NexusSession.absent(NexusGeneration(0L), NexusClosureCause.EXPLICIT_DISCONNECT),
            currentGrants = NexusGrantLedger.of(),
        ).policy
        val revokedIssuance = challenge()
        val revoked = revokedPolicy.authenticate(
            revokedIssuance,
            authRequest(proof),
            token(proof),
            revokedIssuance.ledger,
            50L,
            NexusAgentEntryPolicy.fortressActivated(listOf(NexusReachPath.BINDER)),
        )
        assertEquals(
            NexusPreAuthRefusalCode.TOKEN_REVOKED,
            assertIs<NexusAuthenticationResult.Refused>(revoked).refusal.code,
        )
    }

    @Test
    fun localDevelopmentTailnetAuthenticationRequiresExplicitEnrollment() {
        val proof = tailnetProof(profile = NexusBuildProfile.LOCAL_DEV)
        val enrollment = tailnetEnrollment(
            profiles = listOf(NexusBuildProfile.LOCAL_DEV),
            developmentOnly = true,
            enrollmentId = "tailnet-dev",
        )
        val policy = NexusTrustPolicy.of(tailnetEnrollments = listOf(enrollment))
        val issuance = challenge(NexusReachPath.TAILNET)
        val accepted = policy.authenticate(
            issuance = issuance,
            request = authRequest(
                proof = proof,
                explicitDevelopmentEnrollmentId = "tailnet-dev",
            ),
            token = token(proof),
            nonceLedger = issuance.ledger,
            nowMonotonicMillis = 50L,
            entryPolicy = NexusAgentEntryPolicy.localDevelopment(listOf(NexusReachPath.TAILNET)),
        )
        assertIs<NexusAuthenticationResult.Accepted>(accepted)
    }

    @Test
    fun playFirstReleasePolicyIsStructurallyDisabledAndFortressInertRefusesAuth() {
        val play = NexusAgentEntryPolicy.playFirstRelease()
        assertEquals(dev.phosphor.mobil3.state.Distribution.PLAY, play.distribution)
        assertFalse(play.enabled)
        assertTrue(play.allowedReachPaths.isEmpty())

        val proof = androidProof()
        val issuance = challenge()
        val result = NexusTrustPolicy.of(androidEnrollments = listOf(androidEnrollment())).authenticate(
            issuance,
            authRequest(proof),
            token(proof),
            issuance.ledger,
            50L,
            NexusAgentEntryPolicy.fortressInert(),
        )
        assertEquals(
            NexusPreAuthRefusalCode.ENTRY_UNAVAILABLE,
            assertIs<NexusAuthenticationResult.Refused>(result).refusal.code,
        )
    }

    @Test
    fun ambiguousTrustEnrollmentsAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            NexusTrustPolicy.of(
                androidEnrollments = listOf(
                    androidEnrollment(enrollmentId = "a"),
                    androidEnrollment(enrollmentId = "b"),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            NexusTrustPolicy.of(
                androidEnrollments = listOf(androidEnrollment(enrollmentId = "same")),
                tailnetEnrollments = listOf(tailnetEnrollment(enrollmentId = "same")),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            NexusTrustPolicy.of(
                androidEnrollments = listOf(
                    androidEnrollment(enrollmentId = "principal-a"),
                    androidEnrollment(enrollmentId = "principal-b", principalStableId = "attacker-selected-alias"),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            NexusTrustPolicy.of(
                tailnetEnrollments = listOf(
                    tailnetEnrollment(enrollmentId = "node-a"),
                    tailnetEnrollment(enrollmentId = "node-b", principalStableId = "attacker-selected-alias"),
                ),
            )
        }
    }

    @Test
    fun sessionAuthorityCannotExceedAuthenticatedTokenCapabilityCeiling() {
        val proof = androidProof()
        val auth = authenticatedIdentity(proof, listOf(Capability.OBSERVE_STATE))
        val session = NexusSession.authenticating(auth, NexusGeneration(1L), 0L)
        val ledgerWithExtraControl = grantLedger(proof)
        val observing = session.establish(ledgerWithExtraControl)
        assertEquals(frozenSetOf(Capability.OBSERVE_STATE), observing.capabilities)
        assertFalse(Capability.CONTROL_TRANSPORT in observing.capabilities)
    }

    @Test
    fun persistentAndTransientGrantsAreScopedAndTransientClearsBySession() {
        val proof = androidProof()
        val ledger = grantLedger(proof)
        assertTrue(ledger.allows(proof.trustKey, sessionId, Capability.OBSERVE_STATE))
        assertTrue(ledger.allows(proof.trustKey, sessionId, Capability.CONTROL_TRANSPORT))
        assertFalse(
            ledger.allows(proof.trustKey, NexusSessionId("other-session"), Capability.CONTROL_TRANSPORT),
        )
        val cleared = ledger.clearTransient(sessionId)
        assertTrue(cleared.allows(proof.trustKey, sessionId, Capability.OBSERVE_STATE))
        assertFalse(cleared.allows(proof.trustKey, sessionId, Capability.CONTROL_TRANSPORT))
    }

    @Test
    fun heartbeatNegotiationClosesAtThreeMissedAndBecomesAbsentAtFour() {
        assertFailsWith<IllegalArgumentException> { NexusHeartbeatPolicy(999L) }
        assertFailsWith<IllegalArgumentException> { NexusHeartbeatPolicy(5_001L) }
        val policy = NexusHeartbeatPolicy(1_000L)
        assertEquals(NexusLifecycle.OBSERVING, policy.evaluate(0L, 2_999L, NexusLifecycle.OBSERVING))
        assertEquals(NexusLifecycle.CLOSING, policy.evaluate(0L, 3_000L, NexusLifecycle.OBSERVING))
        assertEquals(NexusLifecycle.ABSENT, policy.evaluate(0L, 4_000L, NexusLifecycle.CLOSING))
        assertFailsWith<IllegalArgumentException> {
            policy.evaluate(10L, 9L, NexusLifecycle.OBSERVING)
        }

        val proof = tailnetProof()
        val session = observingSession(proof = proof)
        assertFailsWith<IllegalArgumentException> { session.heartbeat(3_000L) }
        val closingTransition = session.evaluateRemoteLiveness(3_000L, grantLedger(proof))
        val closing = closingTransition.session
        assertEquals(NexusLifecycle.CLOSING, closing.lifecycle)
        assertTrue(closing.capabilities.isEmpty())
        assertFalse(closing.presence().eyeVisible)
        val absent = closing.evaluateRemoteLiveness(4_000L, closingTransition.grants).session
        assertEquals(NexusLifecycle.ABSENT, absent.lifecycle)
        assertEquals(NexusClosureCause.REMOTE_HEARTBEAT_LOSS, absent.closureCause)
    }

    @Test
    fun explicitRevokeDisconnectAndBinderDeathRemoveAuthorityImmediately() {
        val proof = androidProof()
        val ledger = grantLedger(proof)
        val observing = observingSession(proof = proof, ledger = ledger)
        assertTrue(observing.presence().eyeVisible)
        assertFalse(observing.presence().handVisible)

        val withoutControl = ledger.revoke(proof.trustKey, Capability.CONTROL_TRANSPORT)
        val revokedControl = observing.revokeCapability(Capability.CONTROL_TRANSPORT, withoutControl).session
        assertEquals(NexusLifecycle.OBSERVING, revokedControl.lifecycle)
        assertFalse(Capability.CONTROL_TRANSPORT in revokedControl.capabilities)
        assertFalse(revokedControl.presence().handVisible)

        val withoutObserve = withoutControl.revoke(proof.trustKey, Capability.OBSERVE_STATE)
        val revokedObserve = revokedControl.revokeCapability(Capability.OBSERVE_STATE, withoutObserve).session
        assertEquals(NexusLifecycle.ABSENT, revokedObserve.lifecycle)
        assertFalse(revokedObserve.presence().eyeVisible)

        val binderDead = observing.binderDied(ledger).session
        assertEquals(NexusLifecycle.ABSENT, binderDead.lifecycle)
        assertEquals(NexusClosureCause.BINDER_DEATH, binderDead.closureCause)
        assertTrue(binderDead.capabilities.isEmpty())
        assertFailsWith<IllegalArgumentException> {
            binderDead.disconnect(NexusClosureCause.EXPLICIT_DISCONNECT, NexusGrantLedger.of())
        }
    }

    @Test
    fun policyTokenRevocationAtomicallyClosesMatchingSessionAndClearsTransientGrants() {
        val proof = androidProof()
        val ledger = grantLedger(proof)
        val observing = observingSession(proof = proof, ledger = ledger)
        val revocation = NexusTrustPolicy.of().revokeToken(tokenId, observing, ledger)
        assertTrue(revocation.policy.isTokenRevoked(tokenId))
        assertEquals(NexusLifecycle.ABSENT, revocation.session.lifecycle)
        assertEquals(NexusClosureCause.TOKEN_REVOKED, revocation.session.closureCause)
        assertEquals(tokenId, revocation.revokedTokenId)
        assertEquals(sessionId, revocation.closedSessionId)
        assertEquals(observing.generation, revocation.previousGeneration)
        assertEquals(observing.generation.next(), revocation.session.generation)
        assertFalse(revocation.grants.hasTransient(sessionId))
    }

    @Test
    fun phase05aHandPresenceAndFreshCommitTransitionRemainUnavailable() {
        val observing = observingSession()
        val accepted = acceptedControlAction()
        val ledger = grantLedger()
        val token = token()
        val projection = NexusDispatchProjection.authorize(
            session = observing,
            token = token,
            grants = ledger,
            trustPolicy = NexusTrustPolicy.of(),
            action = accepted.action,
            request = accepted.request,
            nowMonotonicMillis = 50L,
        )
        assertEquals(NexusLifecycle.OBSERVING, observing.lifecycle)
        assertTrue(observing.presence().eyeVisible)
        assertFalse(observing.presence().handVisible)
        assertEquals(sessionId, projection.candidate.sessionId)
        assertTrue(NexusFreshCommitRequirement::class.java.isInterface)
        assertTrue(NexusSession::class.java.declaredMethods.none { it.name == "beginDriving" || it.name == "settleControl" })
        assertFailsWith<ClassNotFoundException> {
            Class.forName("dev.phosphor.mobil3.nexus.NexusFreshCommitEvidence")
        }
        assertFailsWith<ClassNotFoundException> {
            Class.forName("dev.phosphor.mobil3.nexus.NexusFreshCommitLedger")
        }
    }

    @Test
    fun revocationCannotGrantAnUnrelatedCapabilityFromTheReplacementLedger() {
        val proof = androidProof()
        val session = observingSession(
            proof = proof,
            authenticatedCapabilities = listOf(
                Capability.OBSERVE_STATE,
                Capability.CONTROL_TRANSPORT,
                Capability.CONTROL_SCOPE,
            ),
            ledger = grantLedger(proof),
        )
        val replacementLedger = NexusGrantLedger.of(
            listOf(
                NexusCapabilityGrant(
                    proof.trustKey,
                    Capability.OBSERVE_STATE,
                    NexusGrantScope.PERSISTENT,
                    null,
                    "grant-observe",
                ),
                NexusCapabilityGrant(
                    proof.trustKey,
                    Capability.CONTROL_SCOPE,
                    NexusGrantScope.TRANSIENT,
                    sessionId,
                    "grant-unrelated",
                ),
            ),
        )
        val revoked = session.revokeCapability(Capability.CONTROL_TRANSPORT, replacementLedger).session
        assertEquals(frozenSetOf(Capability.OBSERVE_STATE), revoked.capabilities)
    }

    @Test
    fun dispatchProjectionRequiresExactPrincipalSessionTransportCapabilityAndCurrentToken() {
        val proof = androidProof()
        val ledger = grantLedger(proof)
        val session = observingSession(proof = proof, ledger = ledger)
        val token = token(proof)
        val policy = NexusTrustPolicy.of()
        val request = ActionRequest(
            principal = PrincipalId(PrincipalKind.NEXUS, proof.principalStableId),
            idempotencyKey = "idem-1",
            expectedRevision = 7L,
            reason = "toggle playback",
            requestedCapability = Capability.CONTROL_TRANSPORT,
            transport = Transport.BINDER,
            sessionId = sessionId.value,
        )
        val projection = NexusDispatchProjection.authorize(
            session,
            token,
            ledger,
            policy,
            TogglePlayback,
            request,
            50L,
        )
        assertSame(TogglePlayback, projection.action)
        assertSame(request, projection.request)

        assertFailsWith<IllegalArgumentException> {
            NexusDispatchProjection.authorize(
                session,
                token,
                ledger,
                policy,
                TogglePlayback,
                request.copy(principal = PrincipalId(PrincipalKind.NEXUS, "spoof")),
                50L,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            NexusDispatchProjection.authorize(
                session,
                token,
                ledger,
                policy,
                TogglePlayback,
                request.copy(transport = Transport.TAILNET),
                50L,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            NexusDispatchProjection.authorize(session, token, ledger, policy, TogglePlayback, request, 1_000L)
        }
    }

    @Test
    fun observeOnlySessionCanObserveButCannotMutate() {
        val proof = androidProof()
        val ledger = grantLedger(proof, includeControl = false)
        val session = observingSession(
            proof = proof,
            authenticatedCapabilities = listOf(Capability.OBSERVE_STATE),
            ledger = ledger,
        )
        val token = token(proof, capabilities = listOf(Capability.OBSERVE_STATE))
        val policy = NexusTrustPolicy.of()
        val observation = NexusObservationProjection.authorize(
            session,
            token,
            ledger,
            policy,
            Capability.OBSERVE_STATE,
            50L,
        )
        assertEquals(Capability.OBSERVE_STATE, observation.capability)

        val request = ActionRequest(
            PrincipalId(PrincipalKind.NEXUS, proof.principalStableId),
            "idem",
            0L,
            "toggle",
            Capability.CONTROL_TRANSPORT,
            Transport.BINDER,
            sessionId.value,
        )
        assertFailsWith<IllegalArgumentException> {
            NexusDispatchProjection.authorize(session, token, ledger, policy, TogglePlayback, request, 50L)
        }
    }

    @Test
    fun countersAndDeadlinesFailClosedOnOverflow() {
        assertFailsWith<IllegalArgumentException> { NexusGeneration(Long.MAX_VALUE).next() }
        assertFailsWith<IllegalArgumentException> {
            serverAuthority().issue(
                NexusChallengeId("c"),
                NexusReachPath.BINDER,
                NexusSessionId("overflow-session"),
                NexusNonce("n"),
                Long.MAX_VALUE,
                Long.MAX_VALUE,
            )
        }
    }

    @Test
    fun trustedServerAuthorityIssuesAuthenticatesAndResumesItsConsumedLedger() {
        val authority = serverAuthority()
        val issuance = authority.issue(
            id = NexusChallengeId("trusted-challenge"),
            reachPath = NexusReachPath.BINDER,
            sessionId = NexusSessionId("trusted-session"),
            serverNonce = NexusNonce("trusted-server-nonce"),
            issuedMonotonicMillis = 10L,
            expiresMonotonicMillis = 100L,
        )
        val request = authRequest().copy(
            challengeId = issuance.challenge.id,
            sessionId = issuance.challenge.sessionId,
            serverNonce = issuance.challenge.serverNonce,
        )
        val accepted = NexusTrustPolicy.of(androidEnrollments = listOf(androidEnrollment())).authenticate(
            issuance = issuance,
            request = request,
            token = token(),
            nonceLedger = issuance.ledger,
            nowMonotonicMillis = 50L,
            entryPolicy = NexusAgentEntryPolicy.fortressActivated(listOf(NexusReachPath.BINDER)),
        )
        val consumed = assertIs<NexusAuthenticationResult.Accepted>(accepted).nonceLedger
        assertEquals(NexusSessionId("trusted-session"), assertIs<NexusAuthenticationResult.Accepted>(accepted).authentication.sessionId)
        assertFailsWith<IllegalArgumentException> { consumed.consume(issuance, request, 50L) }

        val resumed = serverAuthority(consumed)
        assertFailsWith<IllegalArgumentException> {
            resumed.issue(
                issuance.challenge.id,
                issuance.challenge.reachPath,
                issuance.challenge.sessionId,
                issuance.challenge.serverNonce,
                60L,
                120L,
            )
        }
        val next = resumed.issue(
            NexusChallengeId("trusted-challenge-2"),
            NexusReachPath.BINDER,
            NexusSessionId("trusted-session-2"),
            NexusNonce("trusted-server-nonce-2"),
            60L,
            120L,
        )
        val nextRequest = request.copy(
            challengeId = next.challenge.id,
            sessionId = next.challenge.sessionId,
            serverNonce = next.challenge.serverNonce,
            clientNonce = NexusNonce("trusted-client-nonce-2"),
        )
        next.ledger.consume(next, nextRequest, 70L)
    }

    @Test
    fun phase05aPublicApisCannotMintAnAcceptableServerChallenge() {
        val issuance = challenge()
        val request = authRequest()
        val unrelated = serverAuthority().issue(
            NexusChallengeId("unrelated-challenge"),
            NexusReachPath.BINDER,
            NexusSessionId("unrelated-session"),
            NexusNonce("unrelated-server-nonce"),
            10L,
            100L,
        )
        assertFailsWith<IllegalArgumentException> {
            unrelated.ledger.consume(issuance, request, 50L)
        }
        val consumed = issuance.ledger.consume(issuance, request, 50L)
        assertFailsWith<IllegalArgumentException> { consumed.consume(issuance, request, 50L) }
        assertTrue(NexusChallenge::class.java.isInterface)
        assertTrue(NexusChallenge::class.java.constructors.isEmpty())
        assertTrue(NexusChallenge::class.java.declaredMethods.none { it.name in setOf("issue", "of", "copy") })
        val jvmVisibleLedgerMinting = NexusNonceLedger::class.java.methods.filter {
            it.name == "empty" || it.name.contains("issue", ignoreCase = true)
        }
        assertTrue(jvmVisibleLedgerMinting.all { it.isSynthetic })
        val issuerClass = Class.forName("dev.phosphor.mobil3.nexus.NexusNonceLedger\$ServerIssuer")
        assertTrue(java.lang.reflect.Modifier.isPrivate(issuerClass.modifiers))
        assertFailsWith<NoSuchFieldException> { issuerClass.getField("Companion") }
        assertTrue(issuerClass.methods.none { method ->
            method.name.startsWith("issue") || method.name.startsWith("initial") || method.name.startsWith("resume")
        })
        val privateIssue = issuerClass.declaredMethods.single { it.name.startsWith("issue-") }
        assertTrue(java.lang.reflect.Modifier.isPrivate(privateIssue.modifiers))
        assertFailsWith<NoSuchMethodException> {
            issuerClass.getMethod(privateIssue.name, *privateIssue.parameterTypes)
        }
        assertFailsWith<ClassNotFoundException> {
            Class.forName("dev.phosphor.mobil3.nexus.NexusNonceLedger\$ServerAuthority")
        }
        val source = fortressSource("NexusContracts.kt")
        assertTrue(source.contains("private class ServerIssuer private constructor"))
        assertTrue(source.contains("private fun issue("))
        assertFalse(source.contains("class ServerAuthority"))
        assertFalse(source.contains("internal fun issue("))
        assertFalse(source.contains("fun initial()"))
        assertFalse(source.contains("fun resume("))
        assertFalse(source.contains("fun empty(): NexusNonceLedger"))
        assertFalse(Regex("(?m)^    fun issue\\(").containsMatchIn(source))
    }

    @Test
    fun phase05aAuthenticatedIdentityIsPolicyProducedAndTupleBound() {
        val authenticated = authenticatedIdentity()
        assertEquals(androidProof().trustKey, authenticated.trustKey)
        assertEquals(frozenSetOf(Capability.OBSERVE_STATE, Capability.CONTROL_TRANSPORT), authenticated.grantedCapabilities)
        assertTrue(NexusAuthenticatedIdentity::class.java.isInterface)
        assertTrue(NexusAuthenticatedIdentity::class.java.constructors.isEmpty())
        assertTrue(NexusAuthenticatedIdentity::class.java.declaredMethods.none { it.name in setOf("prove", "of", "copy") })
        assertFalse(java.lang.reflect.Modifier.isPublic(authenticated.javaClass.modifiers))
        val source = fortressSource("NexusContracts.kt")
        assertFalse(source.contains("NexusAuthenticatedIdentity.prove"))
        assertEquals(2, Regex("PolicyAuthenticatedIdentity\\(").findAll(source).count())
    }

    @Test
    fun phase05aStaleSnapshotsHaveNoCallableCommitPermitOrFencePath() {
        val proof = androidProof()
        val ledger = grantLedger(proof)
        val session = observingSession(proof = proof, ledger = ledger)
        val token = token(proof)
        val policy = NexusTrustPolicy.of()
        val projection = NexusDispatchProjection.authorize(
            session,
            token,
            ledger,
            policy,
            TogglePlayback,
            acceptedControlAction().request,
            50L,
        )
        val newer = policy.revokeToken(tokenId, session, ledger)
        assertTrue(newer.policy.isTokenRevoked(tokenId))
        assertEquals(sessionId, projection.candidate.sessionId)
        assertEquals(session.generation, projection.candidate.generation)
        assertTrue(NexusDispatchProjection::class.java.declaredMethods.none { it.name.contains("commit", ignoreCase = true) })
        assertTrue(NexusAuthorizationCandidate::class.java.declaredMethods.none { it.name.contains("commit", ignoreCase = true) })
        assertFailsWith<ClassNotFoundException> { Class.forName("dev.phosphor.mobil3.nexus.NexusCommitPermit") }
        assertFailsWith<ClassNotFoundException> { Class.forName("dev.phosphor.mobil3.nexus.NexusAuthorizationFence") }
        val source = fortressSource("NexusDispatcherContracts.kt")
        assertFalse(source.contains("class NexusCommitPermit"))
        assertFalse(source.contains("fun revalidateBeforeCommit"))
    }

    @Test
    fun phase05aFreshCommitIsOnlyAnInertFutureRequirement() {
        val observing = observingSession()
        assertEquals(NexusLifecycle.OBSERVING, observing.lifecycle)
        assertFalse(observing.presence().handVisible)
        assertTrue(NexusFreshCommitRequirement::class.java.isInterface)
        assertTrue(NexusFreshCommitRequirement::class.java.constructors.isEmpty())
        assertTrue(NexusSession::class.java.declaredMethods.none { it.name == "beginDriving" })
        assertFailsWith<ClassNotFoundException> { Class.forName("dev.phosphor.mobil3.nexus.NexusFreshCommitEvidence") }
        assertFailsWith<ClassNotFoundException> { Class.forName("dev.phosphor.mobil3.nexus.NexusFreshCommitLedger") }
        assertFailsWith<ClassNotFoundException> { Class.forName("dev.phosphor.mobil3.nexus.NexusDrivingBegin") }
        val sessionSource = fortressSource("NexusSessionContracts.kt")
        assertFalse(sessionSource.contains("fun beginDriving"))
        assertFalse(sessionSource.contains("lifecycle = NexusLifecycle.DRIVING"))
        val dispatcherSource = fortressSource("NexusDispatcherContracts.kt")
        assertFalse(dispatcherSource.contains("fun verify("))
        assertFalse(dispatcherSource.contains("NexusFreshCommitLedger"))
    }

    @Test
    fun phase05aOnlyLifecycleAndPolicyProduceOpaqueTransitionResults() {
        val proof = androidProof()
        val ledger = grantLedger(proof)
        val session = observingSession(proof = proof, ledger = ledger)
        val updatedLedger = ledger.revoke(proof.trustKey, Capability.CONTROL_TRANSPORT)
        val transition = session.revokeCapability(Capability.CONTROL_TRANSPORT, updatedLedger)
        assertSame(session, transition.previousSession)
        assertEquals(sessionId, transition.previousSessionId)
        assertEquals(session.generation, transition.previousGeneration)
        assertEquals(session.generation.next(), transition.session.generation)
        assertEquals(NexusLifecycle.OBSERVING, transition.session.lifecycle)
        assertFalse(transition.grants.allows(proof.trustKey, sessionId, Capability.CONTROL_TRANSPORT))
        assertTrue(NexusSessionTransition::class.java.isInterface)
        assertTrue(NexusSessionTransition::class.java.constructors.isEmpty())
        assertTrue(NexusSessionTransition::class.java.declaredMethods.none { it.name in setOf("prove", "copy", "of") })
        assertFalse(java.lang.reflect.Modifier.isPublic(transition.javaClass.modifiers))

        val revocation = NexusTrustPolicy.of().revokeToken(tokenId, session, ledger)
        assertSame(session, revocation.previousSession)
        assertEquals(tokenId, revocation.revokedTokenId)
        assertEquals(sessionId, revocation.closedSessionId)
        assertEquals(session.generation, revocation.previousGeneration)
        assertEquals(session.generation.next(), revocation.session.generation)
        assertEquals(NexusClosureCause.TOKEN_REVOKED, revocation.session.closureCause)
        assertFalse(revocation.grants.hasTransient(sessionId))
        assertTrue(NexusTokenRevocation::class.java.isInterface)
        assertTrue(NexusTokenRevocation::class.java.constructors.isEmpty())
        assertTrue(NexusTokenRevocation::class.java.declaredMethods.none { it.name in setOf("prove", "copy", "of") })
        assertFalse(java.lang.reflect.Modifier.isPublic(revocation.javaClass.modifiers))

        val sessionSource = fortressSource("NexusSessionContracts.kt")
        val policySource = fortressSource("NexusContracts.kt")
        assertFalse(sessionSource.contains("NexusSessionTransition.prove"))
        assertFalse(policySource.contains("NexusTokenRevocation.prove"))
    }
}
