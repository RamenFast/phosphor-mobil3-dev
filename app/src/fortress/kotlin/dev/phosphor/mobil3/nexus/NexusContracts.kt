package dev.phosphor.mobil3.nexus

import dev.phosphor.mobil3.state.Capability
import dev.phosphor.mobil3.state.FrozenList
import dev.phosphor.mobil3.state.FrozenSet
import dev.phosphor.mobil3.state.frozenListOf
import dev.phosphor.mobil3.state.frozenSetOf

const val PHOSPHOR_NEXUS_PROTOCOL: String = "phosphor.nexus/2"
const val NEXUS_DEFAULT_HEARTBEAT_MILLIS: Long = 2_000L
const val NEXUS_MIN_HEARTBEAT_MILLIS: Long = 1_000L
const val NEXUS_MAX_HEARTBEAT_MILLIS: Long = 5_000L
const val NEXUS_REMOTE_CLOSING_AFTER_MISSED_HEARTBEATS: Long = 3L
const val NEXUS_REMOTE_ABSENT_AFTER_MISSED_HEARTBEATS: Long = 4L

private val ANDROID_PACKAGE_PATTERN = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
private val LOWER_SHA256_PATTERN = Regex("[0-9a-f]{64}")

internal fun String.requireNexusToken(name: String, maxLength: Int = 256): String = also {
    require(isNotBlank()) { "$name must be nonblank" }
    require(length <= maxLength) { "$name must not exceed $maxLength characters" }
    require(none { it.isISOControl() }) { "$name must not contain control characters" }
}

internal fun Long.requireNexusNonNegative(name: String): Long = also {
    require(it >= 0L) { "$name must not be negative" }
}

internal fun checkedNexusAdd(left: Long, right: Long, name: String): Long {
    left.requireNexusNonNegative("$name left operand")
    right.requireNexusNonNegative("$name right operand")
    return try {
        Math.addExact(left, right)
    } catch (overflow: ArithmeticException) {
        throw IllegalArgumentException("$name must not overflow", overflow)
    }
}

internal fun checkedNexusMultiply(left: Long, right: Long, name: String): Long {
    left.requireNexusNonNegative("$name left operand")
    right.requireNexusNonNegative("$name right operand")
    return try {
        Math.multiplyExact(left, right)
    } catch (overflow: ArithmeticException) {
        throw IllegalArgumentException("$name must not overflow", overflow)
    }
}

@JvmInline
value class CertificateSha256(val value: String) {
    init {
        require(LOWER_SHA256_PATTERN.matches(value)) {
            "certificate SHA-256 must be exactly 64 lowercase hexadecimal characters"
        }
    }
}

@JvmInline
value class PinnedEndpointIdentity(val value: String) {
    init {
        value.requireNexusToken("pinned endpoint identity")
    }
}

@JvmInline
value class NexusSessionId(val value: String) {
    init {
        value.requireNexusToken("session id")
    }
}

@JvmInline
value class NexusChallengeId(val value: String) {
    init {
        value.requireNexusToken("challenge id")
    }
}

@JvmInline
value class NexusNonce(val value: String) {
    init {
        value.requireNexusToken("nonce")
    }
}

@JvmInline
value class NexusTokenId(val value: String) {
    init {
        value.requireNexusToken("token id")
    }
}

@JvmInline
value class NexusGeneration(val value: Long) {
    init {
        value.requireNexusNonNegative("session generation")
    }

    fun next(): NexusGeneration = NexusGeneration(checkedNexusAdd(value, 1L, "session generation"))
}

enum class NexusBuildProfile(val wireName: String) {
    PLAY("play"),
    FORTRESS("fortress"),
    NEXUS("nexus"),
    LOCAL_DEV("local_dev"),
}

enum class NexusReachPath(val wireName: String) {
    BINDER("binder"),
    TAILNET("tailnet"),
}

enum class NexusTrustKind(val wireName: String) {
    ANDROID_PACKAGE("android_package"),
    TAILNET_NODE("tailnet_node"),
}

/** Android signing evidence reported by PackageManager. It is evidence, not an allowlist. */
data class AndroidSigningEvidence(
    val current: CertificateSha256,
    /** Oldest-to-current signing lineage as reported by PackageManager. */
    val history: FrozenList<CertificateSha256>,
) {
    init {
        require(history.isNotEmpty()) { "signing history must not be empty" }
        require(history.toSet().size == history.size) { "signing history cannot contain duplicate certificates" }
        require(history.last() == current) { "signing history must end with the current certificate" }
    }

    companion object {
        fun of(
            current: CertificateSha256,
            history: Collection<CertificateSha256> = listOf(current),
        ): AndroidSigningEvidence = AndroidSigningEvidence(current, FrozenList.copyOf(history))
    }
}

sealed interface NexusPeerProof {
    val principalStableId: String
    val profile: NexusBuildProfile
    val protocol: String
    val reachPath: NexusReachPath
    val trustKey: NexusTrustKey
}

data class AndroidPeerProof(
    val packageName: String,
    val signing: AndroidSigningEvidence,
    override val principalStableId: String,
    override val profile: NexusBuildProfile,
    override val protocol: String = PHOSPHOR_NEXUS_PROTOCOL,
) : NexusPeerProof {
    override val reachPath: NexusReachPath = NexusReachPath.BINDER
    override val trustKey: NexusTrustKey = NexusTrustKey.android(
        packageName = packageName,
        signing = signing,
        principalStableId = principalStableId,
        profile = profile,
        protocol = protocol,
    )

    init {
        require(ANDROID_PACKAGE_PATTERN.matches(packageName)) { "invalid Android package name" }
        principalStableId.requireNexusToken("principal stable id")
        protocol.requireNexusToken("Nexus protocol")
        require(profile == NexusBuildProfile.NEXUS || profile == NexusBuildProfile.LOCAL_DEV) {
            "Binder peers must use nexus or local_dev profile"
        }
    }
}

data class TailnetPeerProof(
    val nodePrincipal: String,
    val pinnedEndpointIdentity: PinnedEndpointIdentity,
    override val principalStableId: String,
    override val profile: NexusBuildProfile,
    override val protocol: String = PHOSPHOR_NEXUS_PROTOCOL,
) : NexusPeerProof {
    override val reachPath: NexusReachPath = NexusReachPath.TAILNET
    override val trustKey: NexusTrustKey = NexusTrustKey.tailnet(
        nodePrincipal = nodePrincipal,
        pinnedEndpointIdentity = pinnedEndpointIdentity,
        principalStableId = principalStableId,
        profile = profile,
        protocol = protocol,
    )

    init {
        nodePrincipal.requireNexusToken("tailnet node principal")
        principalStableId.requireNexusToken("principal stable id")
        protocol.requireNexusToken("Nexus protocol")
        require(profile == NexusBuildProfile.NEXUS || profile == NexusBuildProfile.LOCAL_DEV) {
            "tailnet peers must use nexus or local_dev profile"
        }
    }
}

@ConsistentCopyVisibility
data class NexusTrustKey private constructor(
    val kind: NexusTrustKind,
    val subject: String,
    val principalStableId: String,
    val currentCertificate: CertificateSha256?,
    val signingHistory: FrozenList<CertificateSha256>,
    val pinnedEndpointIdentity: PinnedEndpointIdentity?,
    val profile: NexusBuildProfile,
    val protocol: String,
) {
    init {
        subject.requireNexusToken("trust subject")
        principalStableId.requireNexusToken("principal stable id")
        protocol.requireNexusToken("Nexus protocol")
        when (kind) {
            NexusTrustKind.ANDROID_PACKAGE -> {
                require(ANDROID_PACKAGE_PATTERN.matches(subject)) { "invalid Android package trust subject" }
                requireNotNull(currentCertificate) { "Android trust key requires a current certificate" }
                require(signingHistory.isNotEmpty() && signingHistory.last() == currentCertificate) {
                    "Android trust key signing lineage must end with current certificate"
                }
                require(signingHistory.toSet().size == signingHistory.size) {
                    "Android trust key signing lineage cannot contain duplicates"
                }
                require(pinnedEndpointIdentity == null) { "Android trust key cannot contain a tailnet endpoint" }
            }
            NexusTrustKind.TAILNET_NODE -> {
                require(currentCertificate == null && signingHistory.isEmpty()) {
                    "tailnet trust key cannot contain Android signing evidence"
                }
                requireNotNull(pinnedEndpointIdentity) { "tailnet trust key requires a pinned endpoint identity" }
            }
        }
    }

    companion object {
        fun android(
            packageName: String,
            signing: AndroidSigningEvidence,
            principalStableId: String,
            profile: NexusBuildProfile,
            protocol: String = PHOSPHOR_NEXUS_PROTOCOL,
        ): NexusTrustKey = NexusTrustKey(
            kind = NexusTrustKind.ANDROID_PACKAGE,
            subject = packageName,
            principalStableId = principalStableId,
            currentCertificate = signing.current,
            signingHistory = signing.history,
            pinnedEndpointIdentity = null,
            profile = profile,
            protocol = protocol,
        )

        fun tailnet(
            nodePrincipal: String,
            pinnedEndpointIdentity: PinnedEndpointIdentity,
            principalStableId: String,
            profile: NexusBuildProfile,
            protocol: String = PHOSPHOR_NEXUS_PROTOCOL,
        ): NexusTrustKey = NexusTrustKey(
            kind = NexusTrustKind.TAILNET_NODE,
            subject = nodePrincipal,
            principalStableId = principalStableId,
            currentCertificate = null,
            signingHistory = frozenListOf(),
            pinnedEndpointIdentity = pinnedEndpointIdentity,
            profile = profile,
            protocol = protocol,
        )
    }
}

data class AndroidTrustEnrollment(
    val enrollmentId: String,
    val packageName: String,
    val principalStableId: String,
    val acceptedCurrentCertificates: FrozenSet<CertificateSha256>,
    /** Oldest-to-newest accepted signer lineage. */
    val acceptedSigningLineage: FrozenList<CertificateSha256>,
    val revokedCertificates: FrozenSet<CertificateSha256>,
    val allowedProfiles: FrozenSet<NexusBuildProfile>,
    val maximumCapabilities: FrozenSet<Capability>,
    val developmentOnly: Boolean,
) {
    init {
        enrollmentId.requireNexusToken("enrollment id")
        require(ANDROID_PACKAGE_PATTERN.matches(packageName)) { "invalid enrolled Android package" }
        principalStableId.requireNexusToken("enrolled principal stable id")
        require(acceptedCurrentCertificates.isNotEmpty()) { "at least one current certificate must be accepted" }
        require(acceptedSigningLineage.isNotEmpty()) { "accepted signing lineage must not be empty" }
        require(acceptedSigningLineage.toSet().size == acceptedSigningLineage.size) {
            "accepted signing lineage cannot contain duplicate certificates"
        }
        require(acceptedCurrentCertificates.all { it in acceptedSigningLineage }) {
            "every accepted current certificate must belong to the accepted signing lineage"
        }
        require(revokedCertificates.none { it in acceptedCurrentCertificates }) {
            "a revoked certificate cannot remain accepted as current"
        }
        require(allowedProfiles.isNotEmpty()) { "at least one build profile must be allowed" }
        require(allowedProfiles.all { it == NexusBuildProfile.NEXUS || it == NexusBuildProfile.LOCAL_DEV }) {
            "Android Nexus enrollment can only allow nexus or local_dev profiles"
        }
        if (developmentOnly) {
            require(allowedProfiles == frozenSetOf(NexusBuildProfile.LOCAL_DEV)) {
                "development enrollment must be local_dev only"
            }
        } else {
            require(NexusBuildProfile.NEXUS in allowedProfiles) {
                "production enrollment must allow nexus profile"
            }
        }
    }

    companion object {
        fun of(
            enrollmentId: String,
            packageName: String,
            principalStableId: String,
            acceptedCurrentCertificates: Collection<CertificateSha256>,
            acceptedSigningLineage: Collection<CertificateSha256>,
            revokedCertificates: Collection<CertificateSha256> = emptySet(),
            allowedProfiles: Collection<NexusBuildProfile>,
            maximumCapabilities: Collection<Capability>,
            developmentOnly: Boolean,
        ): AndroidTrustEnrollment = AndroidTrustEnrollment(
            enrollmentId = enrollmentId,
            packageName = packageName,
            principalStableId = principalStableId,
            acceptedCurrentCertificates = FrozenSet.copyOf(acceptedCurrentCertificates),
            acceptedSigningLineage = FrozenList.copyOf(acceptedSigningLineage),
            revokedCertificates = FrozenSet.copyOf(revokedCertificates),
            allowedProfiles = FrozenSet.copyOf(allowedProfiles),
            maximumCapabilities = FrozenSet.copyOf(maximumCapabilities),
            developmentOnly = developmentOnly,
        )
    }

    fun accepts(proof: AndroidPeerProof, explicitDevelopmentEnrollmentId: String?): Boolean {
        if (proof.packageName != packageName || proof.principalStableId != principalStableId) return false
        if (proof.profile !in allowedProfiles || proof.protocol != PHOSPHOR_NEXUS_PROTOCOL) return false
        if (proof.signing.current !in acceptedCurrentCertificates) return false
        val currentIndex = acceptedSigningLineage.indexOf(proof.signing.current)
        if (currentIndex < 0) return false
        val requiredEvidence = acceptedSigningLineage.toList().take(currentIndex + 1)
        if (proof.signing.history.toList() != requiredEvidence) return false
        if (proof.signing.history.any { it in revokedCertificates }) return false
        return if (developmentOnly) explicitDevelopmentEnrollmentId == enrollmentId else explicitDevelopmentEnrollmentId == null
    }

    fun canonicalTrustKey(proof: AndroidPeerProof): NexusTrustKey {
        require(accepts(proof, if (developmentOnly) enrollmentId else null)) {
            "proof must be accepted before its trust key can be canonicalized"
        }
        return NexusTrustKey.android(
            packageName = packageName,
            signing = AndroidSigningEvidence.of(
                current = proof.signing.current,
                history = acceptedSigningLineage.toList().take(acceptedSigningLineage.indexOf(proof.signing.current) + 1),
            ),
            principalStableId = principalStableId,
            profile = proof.profile,
            protocol = PHOSPHOR_NEXUS_PROTOCOL,
        )
    }
}

data class TailnetTrustEnrollment(
    val enrollmentId: String,
    val nodePrincipal: String,
    val principalStableId: String,
    val acceptedEndpointIdentities: FrozenSet<PinnedEndpointIdentity>,
    val allowedProfiles: FrozenSet<NexusBuildProfile>,
    val maximumCapabilities: FrozenSet<Capability>,
    val developmentOnly: Boolean,
) {
    init {
        enrollmentId.requireNexusToken("enrollment id")
        nodePrincipal.requireNexusToken("enrolled tailnet node principal")
        principalStableId.requireNexusToken("enrolled principal stable id")
        require(acceptedEndpointIdentities.isNotEmpty()) { "at least one endpoint identity must be pinned" }
        require(allowedProfiles.isNotEmpty()) { "at least one build profile must be allowed" }
        require(allowedProfiles.all { it == NexusBuildProfile.NEXUS || it == NexusBuildProfile.LOCAL_DEV }) {
            "tailnet enrollment can only allow nexus or local_dev profiles"
        }
        if (developmentOnly) {
            require(allowedProfiles == frozenSetOf(NexusBuildProfile.LOCAL_DEV)) {
                "development enrollment must be local_dev only"
            }
        } else {
            require(NexusBuildProfile.NEXUS in allowedProfiles) {
                "production enrollment must allow nexus profile"
            }
        }
    }

    companion object {
        fun of(
            enrollmentId: String,
            nodePrincipal: String,
            principalStableId: String,
            acceptedEndpointIdentities: Collection<PinnedEndpointIdentity>,
            allowedProfiles: Collection<NexusBuildProfile>,
            maximumCapabilities: Collection<Capability>,
            developmentOnly: Boolean,
        ): TailnetTrustEnrollment = TailnetTrustEnrollment(
            enrollmentId = enrollmentId,
            nodePrincipal = nodePrincipal,
            principalStableId = principalStableId,
            acceptedEndpointIdentities = FrozenSet.copyOf(acceptedEndpointIdentities),
            allowedProfiles = FrozenSet.copyOf(allowedProfiles),
            maximumCapabilities = FrozenSet.copyOf(maximumCapabilities),
            developmentOnly = developmentOnly,
        )
    }

    fun accepts(proof: TailnetPeerProof, explicitDevelopmentEnrollmentId: String?): Boolean {
        if (proof.nodePrincipal != nodePrincipal || proof.principalStableId != principalStableId) return false
        if (proof.pinnedEndpointIdentity !in acceptedEndpointIdentities) return false
        if (proof.profile !in allowedProfiles || proof.protocol != PHOSPHOR_NEXUS_PROTOCOL) return false
        return if (developmentOnly) explicitDevelopmentEnrollmentId == enrollmentId else explicitDevelopmentEnrollmentId == null
    }
}

enum class NexusPreAuthRefusalCode(val wireName: String) {
    ENTRY_UNAVAILABLE("entry_unavailable"),
    TRUST_TUPLE_REJECTED("trust_tuple_rejected"),
    SIGNER_MIGRATION_REQUIRED("signer_migration_required"),
    PROTOCOL_UNSUPPORTED("protocol_unsupported"),
    CHALLENGE_INVALID("challenge_invalid"),
    TOKEN_INVALID("token_invalid"),
    TOKEN_REVOKED("token_revoked"),
    CAPABILITY_DENIED("capability_denied"),
}

/** Deliberately cannot carry snapshot, revision, audit, or capability-ledger data. */
data class NexusPreAuthRefusal(
    val code: NexusPreAuthRefusalCode,
    val fix: String,
) {
    init {
        fix.requireNexusToken("pre-auth fix", maxLength = 512)
    }
}

/**
 * Read-only view of a server-owned challenge. Phase 05a exposes no constructor or factory for
 * peers. A private server issuer creates it from raw server-selected values and binds it to a
 * [NexusChallengeIssuance]. Phase 05a intentionally exposes no production mint seam.
 */
sealed interface NexusChallenge {
    val id: NexusChallengeId
    val reachPath: NexusReachPath
    val sessionId: NexusSessionId
    val serverNonce: NexusNonce
    val issuedMonotonicMillis: Long
    val expiresMonotonicMillis: Long
}

/** The updated server ledger and the exact challenge it issued as one opaque value. */
sealed interface NexusChallengeIssuance {
    val ledger: NexusNonceLedger
    val challenge: NexusChallenge
}

data class NexusAuthRequest(
    val challengeId: NexusChallengeId,
    val reachPath: NexusReachPath,
    val serverNonce: NexusNonce,
    val clientNonce: NexusNonce,
    val sessionId: NexusSessionId,
    val tokenId: NexusTokenId,
    val proof: NexusPeerProof,
    val requestedCapabilities: FrozenSet<Capability>,
    val heartbeatIntervalMillis: Long = NEXUS_DEFAULT_HEARTBEAT_MILLIS,
    val explicitDevelopmentEnrollmentId: String? = null,
) {
    init {
        require(serverNonce != clientNonce) { "client and server nonces must differ" }
        require(proof.reachPath == reachPath) { "peer proof reach path does not match request" }
        require(requestedCapabilities.isNotEmpty()) { "requested capabilities must be explicit" }
        require(Capability.OBSERVE_STATE in requestedCapabilities) {
            "every authenticated Nexus session must request observe.state"
        }
        require(heartbeatIntervalMillis in NEXUS_MIN_HEARTBEAT_MILLIS..NEXUS_MAX_HEARTBEAT_MILLIS) {
            "heartbeat interval must be from 1 through 5 seconds"
        }
        explicitDevelopmentEnrollmentId?.requireNexusToken("development enrollment id")
        if (proof.profile == NexusBuildProfile.LOCAL_DEV) {
            requireNotNull(explicitDevelopmentEnrollmentId) { "local_dev requires explicit development enrollment" }
        } else {
            require(explicitDevelopmentEnrollmentId == null) {
                "production profile cannot claim a development enrollment"
            }
        }
    }

    companion object {
        fun of(
            challengeId: NexusChallengeId,
            reachPath: NexusReachPath,
            serverNonce: NexusNonce,
            clientNonce: NexusNonce,
            sessionId: NexusSessionId,
            tokenId: NexusTokenId,
            proof: NexusPeerProof,
            requestedCapabilities: Collection<Capability>,
            heartbeatIntervalMillis: Long = NEXUS_DEFAULT_HEARTBEAT_MILLIS,
            explicitDevelopmentEnrollmentId: String? = null,
        ): NexusAuthRequest = NexusAuthRequest(
            challengeId = challengeId,
            reachPath = reachPath,
            serverNonce = serverNonce,
            clientNonce = clientNonce,
            sessionId = sessionId,
            tokenId = tokenId,
            proof = proof,
            requestedCapabilities = FrozenSet.copyOf(requestedCapabilities),
            heartbeatIntervalMillis = heartbeatIntervalMillis,
            explicitDevelopmentEnrollmentId = explicitDevelopmentEnrollmentId,
        )
    }
}

data class NexusTokenGrant(
    val id: NexusTokenId,
    val trustKey: NexusTrustKey,
    val capabilities: FrozenSet<Capability>,
    val issuedMonotonicMillis: Long,
    val expiresMonotonicMillis: Long,
    val revokedAtMonotonicMillis: Long? = null,
) {
    init {
        issuedMonotonicMillis.requireNexusNonNegative("token issue time")
        require(expiresMonotonicMillis > issuedMonotonicMillis) { "token expiry must follow issue time" }
        require(capabilities.isNotEmpty()) { "token must contain at least one capability" }
        revokedAtMonotonicMillis?.let {
            require(it >= issuedMonotonicMillis) { "token revocation cannot precede issue" }
            require(it < expiresMonotonicMillis) { "token revocation must precede token expiry" }
        }
    }

    companion object {
        fun of(
            id: NexusTokenId,
            trustKey: NexusTrustKey,
            capabilities: Collection<Capability>,
            issuedMonotonicMillis: Long,
            expiresMonotonicMillis: Long,
        ): NexusTokenGrant = NexusTokenGrant(
            id = id,
            trustKey = trustKey,
            capabilities = FrozenSet.copyOf(capabilities),
            issuedMonotonicMillis = issuedMonotonicMillis,
            expiresMonotonicMillis = expiresMonotonicMillis,
        )
    }

    val revoked: Boolean get() = revokedAtMonotonicMillis != null

    fun revoke(nowMonotonicMillis: Long): NexusTokenGrant {
        nowMonotonicMillis.requireNexusNonNegative("token revocation time")
        require(!revoked) { "token revocation is immutable once recorded" }
        require(nowMonotonicMillis < expiresMonotonicMillis) { "expired token cannot be newly revoked" }
        return copy(revokedAtMonotonicMillis = nowMonotonicMillis)
    }

    fun validate(
        presentedId: NexusTokenId,
        presentedTrustKey: NexusTrustKey,
        requestedCapabilities: FrozenSet<Capability>,
        nowMonotonicMillis: Long,
    ): NexusPreAuthRefusal? {
        if (presentedId != id || presentedTrustKey != trustKey) {
            return NexusPreAuthRefusal(
                NexusPreAuthRefusalCode.TOKEN_INVALID,
                "Use the token issued for this exact trusted principal tuple.",
            )
        }
        if (revoked) {
            return NexusPreAuthRefusal(
                NexusPreAuthRefusalCode.TOKEN_REVOKED,
                "Ask the user to issue a new token after reviewing capability grants.",
            )
        }
        if (nowMonotonicMillis !in issuedMonotonicMillis until expiresMonotonicMillis) {
            return NexusPreAuthRefusal(
                NexusPreAuthRefusalCode.TOKEN_INVALID,
                "Establish a fresh authenticated session with a non-expired token.",
            )
        }
        if (!capabilities.containsAll(requestedCapabilities)) {
            return NexusPreAuthRefusal(
                NexusPreAuthRefusalCode.CAPABILITY_DENIED,
                "Request only capabilities carried by the presented token.",
            )
        }
        return null
    }
}

class NexusNonceLedger private constructor(
    private val issuedChallenges: FrozenSet<NexusChallenge>,
    private val consumedChallengeIds: FrozenSet<NexusChallengeId>,
    private val consumedNonces: FrozenSet<NexusNonce>,
    private val consumedSessionIds: FrozenSet<NexusSessionId>,
) {
    /** Private Phase 05a placeholder. Phase 05b will add the runtime owner, not expose this. */
    private class ServerIssuer private constructor(
        private val currentLedger: NexusNonceLedger,
    ) {
        private fun issue(
            id: NexusChallengeId,
            reachPath: NexusReachPath,
            sessionId: NexusSessionId,
            serverNonce: NexusNonce,
            issuedMonotonicMillis: Long,
            expiresMonotonicMillis: Long,
        ): NexusChallengeIssuance {
            val challenge = IssuedChallenge(
                id = id,
                reachPath = reachPath,
                sessionId = sessionId,
                serverNonce = serverNonce,
                issuedMonotonicMillis = issuedMonotonicMillis,
                expiresMonotonicMillis = expiresMonotonicMillis,
            )
            require(id !in currentLedger.consumedChallengeIds) { "consumed challenge cannot be re-issued" }
            require(sessionId !in currentLedger.consumedSessionIds) { "consumed session cannot be re-issued" }
            require(serverNonce !in currentLedger.consumedNonces) { "consumed server nonce cannot be re-issued" }
            require(
                currentLedger.issuedChallenges.none {
                    it.id == id || it.sessionId == sessionId || it.serverNonce == serverNonce
                },
            ) { "issued challenge tuple must be globally unique" }
            val updatedLedger = NexusNonceLedger(
                FrozenSet.copyOf(currentLedger.issuedChallenges + challenge),
                currentLedger.consumedChallengeIds,
                currentLedger.consumedNonces,
                currentLedger.consumedSessionIds,
            )
            return BoundChallengeIssuance(updatedLedger, challenge)
        }
    }

    private class IssuedChallenge(
        override val id: NexusChallengeId,
        override val reachPath: NexusReachPath,
        override val sessionId: NexusSessionId,
        override val serverNonce: NexusNonce,
        override val issuedMonotonicMillis: Long,
        override val expiresMonotonicMillis: Long,
    ) : NexusChallenge {
        init {
            issuedMonotonicMillis.requireNexusNonNegative("challenge issue time")
            require(expiresMonotonicMillis > issuedMonotonicMillis) {
                "challenge expiry must follow issue time"
            }
        }
    }

    private class BoundChallengeIssuance(
        override val ledger: NexusNonceLedger,
        override val challenge: NexusChallenge,
    ) : NexusChallengeIssuance

    fun consume(
        issuance: NexusChallengeIssuance,
        request: NexusAuthRequest,
        nowMonotonicMillis: Long,
    ): NexusNonceLedger {
        nowMonotonicMillis.requireNexusNonNegative("authentication time")
        require(issuance is BoundChallengeIssuance) { "challenge issuance was not produced by this server ledger" }
        val challenge = issuance.challenge
        require(challenge in issuedChallenges) { "challenge was not issued by this server ledger" }
        require(challenge.id !in consumedChallengeIds) { "challenge replay" }
        require(challenge.sessionId !in consumedSessionIds) { "session identity replay" }
        require(challenge.serverNonce !in consumedNonces) { "server nonce replay" }
        require(request.clientNonce !in consumedNonces) { "client nonce replay" }
        require(request.challengeId == challenge.id) { "challenge id mismatch" }
        require(request.sessionId == challenge.sessionId) { "server-issued session id mismatch" }
        require(request.reachPath == challenge.reachPath) { "challenge reach path mismatch" }
        require(request.serverNonce == challenge.serverNonce) { "server nonce mismatch" }
        require(nowMonotonicMillis in challenge.issuedMonotonicMillis until challenge.expiresMonotonicMillis) {
            "challenge is expired or not yet valid"
        }
        return NexusNonceLedger(
            issuedChallenges = FrozenSet.copyOf(issuedChallenges - challenge),
            consumedChallengeIds = FrozenSet.copyOf(consumedChallengeIds + challenge.id),
            consumedNonces = FrozenSet.copyOf(consumedNonces + challenge.serverNonce + request.clientNonce),
            consumedSessionIds = FrozenSet.copyOf(consumedSessionIds + challenge.sessionId),
        )
    }

}

class NexusTrustPolicy private constructor(
    private val androidEnrollments: FrozenSet<AndroidTrustEnrollment>,
    private val tailnetEnrollments: FrozenSet<TailnetTrustEnrollment>,
    private val revokedTokenIds: FrozenSet<NexusTokenId>,
) {
    init {
        require(androidEnrollments.map { it.enrollmentId }.distinct().size == androidEnrollments.size) {
            "Android enrollment ids must be unique"
        }
        require(
            androidEnrollments.map { it.packageName to it.developmentOnly }
                .distinct().size == androidEnrollments.size,
        ) { "an Android package cannot select among multiple production or development principals" }
        require(tailnetEnrollments.map { it.enrollmentId }.distinct().size == tailnetEnrollments.size) {
            "tailnet enrollment ids must be unique"
        }
        require(
            tailnetEnrollments.map { it.nodePrincipal to it.developmentOnly }
                .distinct().size == tailnetEnrollments.size,
        ) { "a tailnet node cannot select among multiple production or development principals" }
        require(
            (androidEnrollments.map { it.enrollmentId } + tailnetEnrollments.map { it.enrollmentId }).distinct().size ==
                androidEnrollments.size + tailnetEnrollments.size,
        ) { "enrollment ids must be globally unique" }
    }

    companion object {
        fun of(
            androidEnrollments: Collection<AndroidTrustEnrollment> = emptySet(),
            tailnetEnrollments: Collection<TailnetTrustEnrollment> = emptySet(),
            revokedTokenIds: Collection<NexusTokenId> = emptySet(),
        ): NexusTrustPolicy = NexusTrustPolicy(
            androidEnrollments = FrozenSet.copyOf(androidEnrollments),
            tailnetEnrollments = FrozenSet.copyOf(tailnetEnrollments),
            revokedTokenIds = FrozenSet.copyOf(revokedTokenIds),
        )
    }

    fun revokeToken(
        tokenId: NexusTokenId,
        currentSession: NexusSession,
        currentGrants: NexusGrantLedger,
    ): NexusTokenRevocation {
        val updatedPolicy = NexusTrustPolicy(
            androidEnrollments = androidEnrollments,
            tailnetEnrollments = tailnetEnrollments,
            revokedTokenIds = FrozenSet.copyOf(revokedTokenIds + tokenId),
        )
        if (currentSession.lifecycle != NexusLifecycle.ABSENT && currentSession.tokenId == tokenId) {
            val closed = currentSession.disconnect(NexusClosureCause.TOKEN_REVOKED, currentGrants)
            return PolicyTokenRevocation(
                previousPolicy = this,
                policy = updatedPolicy,
                previousSession = currentSession,
                session = closed.session,
                grants = closed.grants,
                revokedTokenId = tokenId,
                closedSessionId = closed.previousSessionId,
                previousGeneration = currentSession.generation,
            )
        }
        return PolicyTokenRevocation(
            previousPolicy = this,
            policy = updatedPolicy,
            previousSession = currentSession,
            session = currentSession,
            grants = currentGrants,
            revokedTokenId = tokenId,
            closedSessionId = null,
            previousGeneration = currentSession.generation,
        )
    }

    fun isTokenRevoked(tokenId: NexusTokenId): Boolean = tokenId in revokedTokenIds

    fun authenticate(
        issuance: NexusChallengeIssuance,
        request: NexusAuthRequest,
        token: NexusTokenGrant,
        nonceLedger: NexusNonceLedger,
        nowMonotonicMillis: Long,
        entryPolicy: NexusAgentEntryPolicy,
    ): NexusAuthenticationResult {
        if (!entryPolicy.enabled || request.reachPath !in entryPolicy.allowedReachPaths) {
            return NexusAuthenticationResult.Refused(
                NexusPreAuthRefusal(
                    NexusPreAuthRefusalCode.ENTRY_UNAVAILABLE,
                    entryPolicy.fix,
                ),
            )
        }
        if (request.proof.protocol != PHOSPHOR_NEXUS_PROTOCOL) {
            return NexusAuthenticationResult.Refused(
                NexusPreAuthRefusal(
                    NexusPreAuthRefusalCode.PROTOCOL_UNSUPPORTED,
                    "Upgrade both peers to $PHOSPHOR_NEXUS_PROTOCOL.",
                ),
            )
        }
        val resolvedTrust = when (val proof = request.proof) {
            is AndroidPeerProof -> androidEnrollments.singleOrNull {
                it.accepts(proof, request.explicitDevelopmentEnrollmentId)
            }?.let { enrollment ->
                enrollment.maximumCapabilities to enrollment.canonicalTrustKey(proof)
            }
            is TailnetPeerProof -> tailnetEnrollments.singleOrNull {
                it.accepts(proof, request.explicitDevelopmentEnrollmentId)
            }?.let { enrollment -> enrollment.maximumCapabilities to proof.trustKey }
        } ?: return NexusAuthenticationResult.Refused(
            NexusPreAuthRefusal(
                if (request.proof is AndroidPeerProof && request.proof.profile == NexusBuildProfile.NEXUS) {
                    NexusPreAuthRefusalCode.SIGNER_MIGRATION_REQUIRED
                } else {
                    NexusPreAuthRefusalCode.TRUST_TUPLE_REJECTED
                },
                if (request.proof is AndroidPeerProof && request.proof.profile == NexusBuildProfile.NEXUS) {
                    "Back up Nexus, migrate it to an accepted estate signing lineage, reinstall, restore, and retry."
                } else {
                    "Enroll this exact package or node identity, signer or pinned endpoint, profile, and protocol before retrying."
                },
            ),
        )
        val (enrollmentCapabilities, canonicalTrustKey) = resolvedTrust
        if (!enrollmentCapabilities.containsAll(request.requestedCapabilities)) {
            return NexusAuthenticationResult.Refused(
                NexusPreAuthRefusal(
                    NexusPreAuthRefusalCode.CAPABILITY_DENIED,
                    "Ask the user to grant only capabilities permitted for this trusted identity.",
                ),
            )
        }
        if (token.id in revokedTokenIds) {
            return NexusAuthenticationResult.Refused(
                NexusPreAuthRefusal(
                    NexusPreAuthRefusalCode.TOKEN_REVOKED,
                    "Ask the user to issue a replacement token after reviewing grants.",
                ),
            )
        }
        token.validate(request.tokenId, canonicalTrustKey, request.requestedCapabilities, nowMonotonicMillis)?.let {
            return NexusAuthenticationResult.Refused(it)
        }
        val consumedLedger = try {
            nonceLedger.consume(issuance, request, nowMonotonicMillis)
        } catch (_: IllegalArgumentException) {
            return NexusAuthenticationResult.Refused(
                NexusPreAuthRefusal(
                    NexusPreAuthRefusalCode.CHALLENGE_INVALID,
                    "Request a fresh nonce-bound challenge and retry once.",
                ),
            )
        }
        return NexusAuthenticationResult.Accepted(
            authentication = PolicyAuthenticatedIdentity(
                trustKey = canonicalTrustKey,
                reachPath = request.reachPath,
                sessionId = request.sessionId,
                tokenId = request.tokenId,
                grantedCapabilities = request.requestedCapabilities,
                heartbeatIntervalMillis = request.heartbeatIntervalMillis,
            ),
            nonceLedger = consumedLedger,
        )
    }
}

/** Opaque result produced only by [NexusTrustPolicy.revokeToken]. */
sealed interface NexusTokenRevocation {
    val previousPolicy: NexusTrustPolicy
    val policy: NexusTrustPolicy
    val previousSession: NexusSession
    val session: NexusSession
    val grants: NexusGrantLedger
    val revokedTokenId: NexusTokenId
    val closedSessionId: NexusSessionId?
    val previousGeneration: NexusGeneration
}

private class PolicyTokenRevocation(
    override val previousPolicy: NexusTrustPolicy,
    override val policy: NexusTrustPolicy,
    override val previousSession: NexusSession,
    override val session: NexusSession,
    override val grants: NexusGrantLedger,
    override val revokedTokenId: NexusTokenId,
    override val closedSessionId: NexusSessionId?,
    override val previousGeneration: NexusGeneration,
) : NexusTokenRevocation {
    init {
        require(previousPolicy !== policy) { "revocation result must advance the trust policy" }
        require(policy.isTokenRevoked(revokedTokenId)) { "revocation result must include the revoked token" }
        require(previousSession.generation == previousGeneration) {
            "revocation result must retain the exact previous generation"
        }
        session.tokenId?.let { activeTokenId ->
            require(!policy.isTokenRevoked(activeTokenId)) {
                "a token-revocation result cannot retain a session using the revoked token"
            }
        }
        closedSessionId?.let { retired ->
            require(previousSession.lifecycle != NexusLifecycle.ABSENT && previousSession.tokenId == revokedTokenId) {
                "token revocation may close only the exact session using the revoked token"
            }
            require(previousSession.id == retired) { "token revocation must name the exact previous session" }
            require(session.lifecycle == NexusLifecycle.ABSENT) {
                "token revocation must close the matching active session"
            }
            require(!grants.hasTransient(retired)) {
                "token revocation must clear matching transient grants"
            }
            require(session.closureCause == NexusClosureCause.TOKEN_REVOKED) {
                "token revocation must record the token-revoked closure cause"
            }
            require(session.generation == previousGeneration.next()) {
                "token revocation closure must advance the exact previous generation"
            }
        } ?: run {
            require(session === previousSession) { "unmatched token revocation cannot replace an unrelated session" }
            require(session.generation == previousGeneration) {
                "unmatched token revocation cannot change an unrelated session generation"
            }
        }
    }
}

/** Opaque identity produced only by successful [NexusTrustPolicy.authenticate]. */
sealed interface NexusAuthenticatedIdentity {
    val trustKey: NexusTrustKey
    val reachPath: NexusReachPath
    val sessionId: NexusSessionId
    val tokenId: NexusTokenId
    val grantedCapabilities: FrozenSet<Capability>
    val heartbeatIntervalMillis: Long
}

private class PolicyAuthenticatedIdentity(
    override val trustKey: NexusTrustKey,
    override val reachPath: NexusReachPath,
    override val sessionId: NexusSessionId,
    override val tokenId: NexusTokenId,
    override val grantedCapabilities: FrozenSet<Capability>,
    override val heartbeatIntervalMillis: Long,
) : NexusAuthenticatedIdentity {
    init {
        require(grantedCapabilities.isNotEmpty()) { "authenticated identity requires capabilities" }
        require(heartbeatIntervalMillis in NEXUS_MIN_HEARTBEAT_MILLIS..NEXUS_MAX_HEARTBEAT_MILLIS) {
            "authenticated heartbeat interval must be from 1 through 5 seconds"
        }
    }
}

sealed interface NexusAuthenticationResult {
    data class Accepted(
        val authentication: NexusAuthenticatedIdentity,
        val nonceLedger: NexusNonceLedger,
    ) : NexusAuthenticationResult

    data class Refused(val refusal: NexusPreAuthRefusal) : NexusAuthenticationResult
}

@ConsistentCopyVisibility
data class NexusAgentEntryPolicy private constructor(
    val distribution: dev.phosphor.mobil3.state.Distribution,
    val enabled: Boolean,
    val allowedReachPaths: FrozenSet<NexusReachPath>,
    val fix: String,
) {
    init {
        fix.requireNexusToken("entry policy fix", maxLength = 512)
        if (!enabled) require(allowedReachPaths.isEmpty()) { "disabled entry cannot allow a reach path" }
        if (distribution == dev.phosphor.mobil3.state.Distribution.PLAY) {
            require(!enabled && allowedReachPaths.isEmpty()) {
                "the first Play release cannot expose an agent entry"
            }
        }
    }

    companion object {
        fun playFirstRelease(): NexusAgentEntryPolicy = NexusAgentEntryPolicy(
            distribution = dev.phosphor.mobil3.state.Distribution.PLAY,
            enabled = false,
            allowedReachPaths = frozenSetOf(),
            fix = "Install the Fortress distribution or wait for a separately approved Play agent-entry decision.",
        )

        fun fortressInert(): NexusAgentEntryPolicy = NexusAgentEntryPolicy(
            distribution = dev.phosphor.mobil3.state.Distribution.FORTRESS,
            enabled = false,
            allowedReachPaths = frozenSetOf(),
            fix = "Complete signing, authorization, revocation, receipt, and transport activation gates before retrying.",
        )

        fun fortressActivated(allowedReachPaths: Collection<NexusReachPath>): NexusAgentEntryPolicy =
            NexusAgentEntryPolicy(
                distribution = dev.phosphor.mobil3.state.Distribution.FORTRESS,
                enabled = true,
                allowedReachPaths = FrozenSet.copyOf(allowedReachPaths).also {
                    require(it.isNotEmpty()) { "activated Fortress entry requires a reach path" }
                },
                fix = "No repair is required while the selected Fortress reach path is enabled.",
            )

        fun localDevelopment(allowedReachPaths: Collection<NexusReachPath>): NexusAgentEntryPolicy =
            NexusAgentEntryPolicy(
                distribution = dev.phosphor.mobil3.state.Distribution.LOCAL_DEV,
                enabled = true,
                allowedReachPaths = FrozenSet.copyOf(allowedReachPaths).also {
                    require(it.isNotEmpty()) { "local development entry requires a reach path" }
                },
                fix = "Use an explicitly enrolled local-development identity for this reach path.",
            )
    }
}
