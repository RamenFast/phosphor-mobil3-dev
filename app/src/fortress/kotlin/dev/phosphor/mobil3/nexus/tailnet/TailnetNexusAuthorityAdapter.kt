package dev.phosphor.mobil3.nexus.tailnet

import dev.phosphor.mobil3.nexus.NexusAuthorityCodec
import dev.phosphor.mobil3.nexus.NexusAuthorityDecodeResult
import dev.phosphor.mobil3.nexus.NexusClosureCause
import dev.phosphor.mobil3.nexus.NexusDispatchProjection
import dev.phosphor.mobil3.nexus.NexusGeneration
import dev.phosphor.mobil3.nexus.NexusGrantLedger
import dev.phosphor.mobil3.nexus.NexusGrantScope
import dev.phosphor.mobil3.nexus.NexusObservationProjection
import dev.phosphor.mobil3.nexus.NexusObservationResult
import dev.phosphor.mobil3.nexus.NexusReachPath
import dev.phosphor.mobil3.nexus.NexusRuntimeManager
import dev.phosphor.mobil3.nexus.NexusRuntimeManagers
import dev.phosphor.mobil3.nexus.NexusSession
import dev.phosphor.mobil3.nexus.NexusSessionId
import dev.phosphor.mobil3.nexus.NexusTokenId
import dev.phosphor.mobil3.nexus.NexusTrustKey
import dev.phosphor.mobil3.nexus.NexusTrustPolicy
import dev.phosphor.mobil3.nexus.PHOSPHOR_NEXUS_PROTOCOL
import dev.phosphor.mobil3.nexus.PinnedEndpointIdentity
import dev.phosphor.mobil3.state.ActionRequest
import dev.phosphor.mobil3.state.Capability
import dev.phosphor.mobil3.state.PhosphorAction
import dev.phosphor.mobil3.state.PhosphorStateSnapshot
import dev.phosphor.mobil3.state.PrincipalId
import dev.phosphor.mobil3.state.PrincipalKind
import dev.phosphor.mobil3.state.SetDisplayHud
import dev.phosphor.mobil3.state.Transport
import dev.phosphor.mobil3.store.PhosphorDispatchResult
import dev.phosphor.mobil3.store.PhosphorStateStore
import dev.phosphor.mobil3.store.StoreHealth
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Tailnet-owned bridge from the wire client into the one shared Nexus runtime authority.
 *
 * The concrete manager seam is intentionally injected because NexusRuntimeManager is owned outside
 * this package. A production caller must back [TailnetSharedRuntimePort] with
 * NexusRuntimeManagers.forStore(store) and the same durable trust/grant revalidation path as Binder.
 */
internal class TailnetNexusAuthorityAdapter(
    private val port: TailnetSharedRuntimePort,
) : SharedNexusRuntimeAuthority {
    private var active: TailnetAuthenticationRequest? = null

    override fun authenticate(request: TailnetAuthenticationRequest): TailnetSessionAttachment {
        require(request.protocol == PHOSPHOR_TAILNET_RPC_PROTOCOL) { "tailnet protocol mismatch" }
        require(request.profile == "nexus" || request.profile == "local_dev") { "tailnet profile must be nexus or local_dev" }
        val capabilities = request.requestedPhosphorCapabilities.map { it.toPhosphorCapability() }.toSet()
        require(Capability.OBSERVE_STATE in capabilities) { "tailnet auth requires observe.state" }
        val attachment = port.attachTailnet(request, capabilities)
        require(attachment.session.generation == request.identity.generation) { "tailnet attachment generation mismatch" }
        active = request
        return attachment
    }

    override fun observe(identity: TailnetIdentity): JSONObject {
        requireActive(identity)
        return port.observeState(identity)
    }

    override fun audit(identity: TailnetIdentity, args: JSONObject): JSONObject {
        requireActive(identity)
        return port.observeAudit(identity, args)
    }

    override fun dispatch(identity: TailnetIdentity, operation: TailnetOperation): TailnetDispatchResult {
        requireActive(identity)
        if (operation.op != "display.hud.set") {
            return TailnetDispatchResult.refused(TailnetNexusAuthorityAdapter.refusal("unsupported_operation", "Only display.hud.set is accepted on the Phosphor tailnet lane."))
        }
        return port.dispatchDisplayHud(identity, operation.args)
    }

    override fun heartbeat(identity: TailnetIdentity, sequence: Long, monotonicMillis: Long) {
        if (isActive(identity)) port.recordHeartbeat(identity, sequence, monotonicMillis)
    }

    override fun closing(identity: TailnetIdentity, reason: String) {
        if (isActive(identity)) port.markClosing(identity, reason)
    }

    override fun absent(identity: TailnetIdentity, reason: String) {
        if (isActive(identity) && port.markAbsent(identity, reason)) {
            active = null
        }
    }

    override fun disconnect(identity: TailnetIdentity, reason: String) {
        if (isActive(identity) && port.disconnect(identity, reason)) {
            active = null
        }
    }

    private fun requireActive(identity: TailnetIdentity) {
        require(isActive(identity)) { "tailnet shared authority is not authenticated for this identity" }
    }

    private fun isActive(identity: TailnetIdentity): Boolean = active?.identity == identity

    private fun String.toPhosphorCapability(): Capability = Capability.entries.firstOrNull { it.wireName == this || it.name == this }
        ?: throw IllegalArgumentException("unknown Phosphor capability $this")

	companion object {
		fun production(store: PhosphorStateStore): TailnetNexusAuthorityAdapter =
			TailnetNexusAuthorityAdapter(ProductionTailnetSharedRuntimePort(store, NexusRuntimeManagers.forStore(store)))

		fun refusal(code: String, fix: String): JSONObject = JSONObject()
			.put("status", "refused")
			.put("refusal", JSONObject().put("code", code).put("fix", fix))
	}
}

internal interface TailnetSharedRuntimePort {
    fun attachTailnet(request: TailnetAuthenticationRequest, capabilities: Set<Capability>): TailnetSessionAttachment
    fun observeState(identity: TailnetIdentity): JSONObject
    fun observeAudit(identity: TailnetIdentity, args: JSONObject): JSONObject
    fun dispatchDisplayHud(identity: TailnetIdentity, args: JSONObject): TailnetDispatchResult
    fun recordHeartbeat(identity: TailnetIdentity, sequence: Long, monotonicMillis: Long)
    fun markClosing(identity: TailnetIdentity, reason: String): Boolean
    fun markAbsent(identity: TailnetIdentity, reason: String): Boolean
    fun disconnect(identity: TailnetIdentity, reason: String): Boolean
}

internal class ProductionTailnetSharedRuntimePort(
    private val store: PhosphorStateStore,
    private val manager: NexusRuntimeManager,
    private val nowMonotonicMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    private val nowWallTimeMillis: () -> Long = { System.currentTimeMillis() },
) : TailnetSharedRuntimePort {
    private val monitor = Any()
    private var session: NexusSession? = null
    private var token: dev.phosphor.mobil3.nexus.NexusTokenGrant? = null
    private var grants: NexusGrantLedger = NexusGrantLedger.of()
    private var trustPolicy: NexusTrustPolicy = NexusTrustPolicy.of()
    private var activeIdentity: TailnetIdentity? = null

    override fun attachTailnet(request: TailnetAuthenticationRequest, capabilities: Set<Capability>): TailnetSessionAttachment = synchronized(monitor) {
        val profile = dev.phosphor.mobil3.nexus.NexusBuildProfile.entries.singleOrNull { it.wireName == request.profile }
            ?: throw IllegalArgumentException("tailnet profile must be nexus or local_dev")
        val trustKey = NexusTrustKey.tailnet(
            nodePrincipal = request.identity.principal,
            pinnedEndpointIdentity = PinnedEndpointIdentity(request.identity.pinnedEndpointIdentity),
            principalStableId = request.identity.principal,
            profile = profile,
            protocol = PHOSPHOR_NEXUS_PROTOCOL,
        )
        val durable = when (val decoded = NexusAuthorityCodec.decode(store.authorityPlane)) {
            NexusAuthorityDecodeResult.Absent -> throw IllegalStateException("Tailnet requires durable persistent grants before authentication.")
            is NexusAuthorityDecodeResult.Corrupt -> throw IllegalStateException(decoded.refusal.fix)
            is NexusAuthorityDecodeResult.Loaded -> decoded.image.withoutTransientGrants()
        }
        val durableGranted = durable.grants.grants.filter { it.scope == NexusGrantScope.PERSISTENT && it.trustKey == trustKey }.map { it.capability }.toSet()
        require(durableGranted.containsAll(capabilities)) { "Requested capabilities are not durably granted for this exact tailnet trust tuple." }
        val sessionId = NexusSessionId("tailnet-session-${UUID.randomUUID()}")
        val tokenId = NexusTokenId(request.identity.tokenId)
        val now = nowMonotonicMillis()
        val attachment = manager.attachTailnet(
            trustKey = trustKey,
            sessionId = sessionId,
            tokenId = tokenId,
            generation = NexusGeneration(request.identity.generation),
            capabilities = capabilities,
            durableGrants = durable.grants,
            trustPolicy = trustPolicy,
            heartbeatIntervalMillis = request.heartbeatMillis,
            leaseMillis = request.leaseMillis,
            nowMonotonicMillis = now,
        )
        session = attachment.session
        token = attachment.token
        grants = attachment.grants
        activeIdentity = request.identity
        TailnetSessionAttachment(TailnetSession(sessionId.value, request.identity.generation, request.endpoint))
    }

    override fun observeState(identity: TailnetIdentity): JSONObject = observe(identity, Capability.OBSERVE_STATE)

    override fun observeAudit(identity: TailnetIdentity, args: JSONObject): JSONObject = observe(identity, Capability.OBSERVE_AUDIT)

    private fun observe(identity: TailnetIdentity, capability: Capability): JSONObject = synchronized(monitor) {
        val projection = NexusObservationProjection.authorize(requireSession(identity), requireNotNull(token), grants, trustPolicy, capability, nowMonotonicMillis())
        when (val result = manager.dispatcher().observe(projection, nowMonotonicMillis())) {
            is NexusObservationResult.State -> JSONObject().put("status", "ok").put("snapshot", result.snapshot.toTailnetJson()).put("health", result.health.toTailnetJson())
            is NexusObservationResult.Audit -> JSONObject().put("status", "ok").put("audit", JSONArray().also { array -> result.auditRecords.forEach { array.put(JSONObject().put("kind", it.kind.wireName).put("wall_time_millis", it.wallTimeMillis).put("receipt_id", it.receiptId).put("action_type", it.actionType?.wireName ?: JSONObject.NULL).put("fields", JSONArray().also { fields -> it.fields.forEach { field -> fields.put(field.wireName) } })) } }).put("health", result.health.toTailnetJson())
            is NexusObservationResult.GeometryUnavailable -> JSONObject().put("status", "refused").put("fix", result.fix)
            is NexusObservationResult.Refused -> JSONObject().put("status", "refused").put("refusal", JSONObject().put("code", result.refusal.code.wireName).put("fix", result.refusal.fix))
        }
    }

    override fun dispatchDisplayHud(identity: TailnetIdentity, args: JSONObject): TailnetDispatchResult = synchronized(monitor) {
        val action: PhosphorAction = SetDisplayHud(args.optString("mode"))
        val current = requireSession(identity)
        val request = ActionRequest(
            principal = PrincipalId(PrincipalKind.NEXUS, requireNotNull(current.trustKey).principalStableId),
            idempotencyKey = args.optString("idempotency_key").ifBlank { "tailnet-${identity.generation}-${UUID.randomUUID()}" },
            expectedRevision = args.optLong("expected_revision", 0L),
            reason = args.optString("reason").ifBlank { "Nexus tailnet dispatch" },
            requestedCapability = action.type.requiredCapability,
            transport = Transport.TAILNET,
            sessionId = requireNotNull(current.id).value,
        )
        val projection = NexusDispatchProjection.authorize(current, requireNotNull(token), grants, trustPolicy, action, request, nowMonotonicMillis())
        when (val result = manager.dispatcher().dispatch(projection, nowMonotonicMillis(), nowWallTimeMillis())) {
            is PhosphorDispatchResult.Accepted -> TailnetDispatchResult.accepted(JSONObject().put("status", "ok").put("revision", result.acknowledgement.revision))
            is PhosphorDispatchResult.Replayed -> TailnetDispatchResult.accepted(JSONObject().put("status", "ok").put("replayed", true).put("revision", result.acknowledgement.revision))
            is PhosphorDispatchResult.Refused -> TailnetDispatchResult.refused(TailnetNexusAuthorityAdapter.refusal(result.refusal.code.wireName, result.refusal.fix))
            is PhosphorDispatchResult.Failed -> TailnetDispatchResult.refused(TailnetNexusAuthorityAdapter.refusal(result.refusal.code.wireName, result.refusal.fix))
        }
    }

    override fun recordHeartbeat(identity: TailnetIdentity, sequence: Long, monotonicMillis: Long) {
        synchronized(monitor) {
            val current = session?.takeIf { matches(identity) } ?: return@synchronized
            val attachment = manager.recordTailnetHeartbeat(requireNotNull(current.id), current.generation, monotonicMillis)
                ?: return@synchronized
            session = attachment.session
            token = attachment.token
            grants = attachment.grants
        }
    }

    override fun markClosing(identity: TailnetIdentity, reason: String): Boolean = synchronized(monitor) {
        transitionRemoteLiveness(identity, absent = false)
    }

    override fun markAbsent(identity: TailnetIdentity, reason: String): Boolean = synchronized(monitor) {
        transitionRemoteLiveness(identity, absent = true)
    }

    override fun disconnect(identity: TailnetIdentity, reason: String): Boolean = synchronized(monitor) {
        closeIfMatched(identity, NexusClosureCause.EXPLICIT_DISCONNECT)
    }

    private fun transitionRemoteLiveness(identity: TailnetIdentity, absent: Boolean): Boolean {
        val current = session?.takeIf { matches(identity) } ?: return false
        val policy = requireNotNull(current.heartbeatPolicy)
        val last = requireNotNull(current.lastHeartbeatMonotonicMillis)
        val at = last + if (absent) policy.absentAfterMillis else policy.closingAfterMillis
        val transition = manager.evaluateTailnetLiveness(requireNotNull(current.id), at) ?: return false
        session = transition.session
        grants = transition.grants
        if (transition.closed) {
            token = null
            session = null
            grants = NexusGrantLedger.of()
            activeIdentity = null
        }
        return transition.closed == absent
    }

    private fun closeIfMatched(identity: TailnetIdentity, cause: NexusClosureCause): Boolean {
        if (!matches(identity)) return false
        if (!manager.close(cause)) return false
        session = null
        token = null
        grants = NexusGrantLedger.of()
        activeIdentity = null
        return true
    }

    private fun requireSession(identity: TailnetIdentity): NexusSession = requireNotNull(session?.takeIf { matches(identity) }) { "tailnet shared authority is not authenticated for this identity" }
    private fun matches(identity: TailnetIdentity): Boolean = activeIdentity == identity && session?.let {
        it.reachPath == NexusReachPath.TAILNET &&
            it.tokenId?.value == identity.tokenId &&
            it.trustKey?.subject == identity.principal &&
            it.trustKey.pinnedEndpointIdentity?.value == identity.pinnedEndpointIdentity
    } == true
}

internal fun Collection<String>.toJsonArray(): JSONArray = JSONArray().also { out -> forEach(out::put) }

private fun PhosphorStateSnapshot.toTailnetJson(): JSONObject = JSONObject()
    .put("schema", schema)
    .put("session", session ?: JSONObject.NULL)
    .put("revision", revision)
    .put("sequence", sequence)
    .put("wall_time_millis", wallTimeMillis)
    .put("distribution", distribution.wireName)
    .put("build_profile", buildProfile.wireName)
    .put("desired", desired.toTailnetJson())
    .put("effective", effective.toTailnetJson())
    .put("contested_fields", JSONArray().also { array -> contestedFields.forEach { array.put(it.wireName) } })

private fun dev.phosphor.mobil3.state.InstrumentSettings.toTailnetJson(): JSONObject = JSONObject()
    .put("display_hud", displayHud)
    .put("fullscreen", fullscreen)
    .put("scope_rotation_locked", scopeRotationLocked)
    .put("ui_placement_locked", uiPlacementLocked)
    .put("frame_rate", frameRate)
    .put("active_theme_id", activeThemeId)
    .put("remote_latency_mode", remoteLatencyMode)
    .put("remote_network_mode", remoteNetworkMode)

private fun StoreHealth.toTailnetJson(): JSONObject = JSONObject()
    .put("readable", readable)
    .put("writable", writable)
    .put("fix", fix?.let { JSONObject().put("code", it.code.wireName).put("fix", it.fix) } ?: JSONObject.NULL)
