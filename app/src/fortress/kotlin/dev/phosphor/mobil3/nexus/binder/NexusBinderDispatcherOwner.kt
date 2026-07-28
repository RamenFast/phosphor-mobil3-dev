package dev.phosphor.mobil3.nexus.binder

import android.os.SystemClock
import dev.phosphor.mobil3.nexus.NexusClosureCause
import dev.phosphor.mobil3.nexus.NexusDispatchProjection
import dev.phosphor.mobil3.nexus.NexusGrantLedger
import dev.phosphor.mobil3.nexus.NexusObservationProjection
import dev.phosphor.mobil3.nexus.NexusObservationResult
import dev.phosphor.mobil3.nexus.NexusPreAuthRefusal
import dev.phosphor.mobil3.nexus.NexusRuntimeDispatcher
import dev.phosphor.mobil3.nexus.NexusSession
import dev.phosphor.mobil3.state.ActionAcknowledgement
import dev.phosphor.mobil3.state.AuditRecord
import dev.phosphor.mobil3.state.Authority
import dev.phosphor.mobil3.state.Availability
import dev.phosphor.mobil3.state.BeamGradientStateValue
import dev.phosphor.mobil3.state.BooleanStateValue
import dev.phosphor.mobil3.state.FloatRangeStateValue
import dev.phosphor.mobil3.state.FloatStateValue
import dev.phosphor.mobil3.state.InertEffectDescription
import dev.phosphor.mobil3.state.InstrumentSettings
import dev.phosphor.mobil3.state.IntStateValue
import dev.phosphor.mobil3.state.LongStateValue
import dev.phosphor.mobil3.state.NullableStringStateValue
import dev.phosphor.mobil3.state.PhosphorStateSnapshot
import dev.phosphor.mobil3.state.PrincipalId
import dev.phosphor.mobil3.state.ProvenanceStamp
import dev.phosphor.mobil3.state.Refusal
import dev.phosphor.mobil3.state.RgbaColor
import dev.phosphor.mobil3.state.StateChange
import dev.phosphor.mobil3.state.StateDelta
import dev.phosphor.mobil3.state.StateField
import dev.phosphor.mobil3.state.StateValue
import dev.phosphor.mobil3.state.StringStateValue
import dev.phosphor.mobil3.store.PhosphorDispatchResult
import dev.phosphor.mobil3.store.PhosphorStateStore
import dev.phosphor.mobil3.store.StoreHealth
import org.json.JSONArray
import org.json.JSONObject

/**
 * Production bootstrap seam for MainActivity/Application wiring.
 *
 * The Binder service never creates sessions, grants, projections, or store authority. This owner is
 * constructed with the already-owned [NexusRuntimeDispatcher] and the one shared [PhosphorStateStore]
 * so Binder stays a bounded transport and all observe/dispatch decisions route through the causal
 * runtime dispatcher. MainActivity can install this later through [NexusBinderRuntimeRegistry].
 */
internal class NexusBinderDispatcherOwner(
    override val callerPolicy: NexusBinderCallerPolicy,
    private val dispatcherProvider: () -> NexusRuntimeDispatcher,
    @Suppress("unused") private val store: PhosphorStateStore,
    private val projectionFactory: NexusBinderProjectionFactory,
    private val sessionCloser: NexusBinderSessionCloser,
) : NexusBinderRuntimeOwner {
    override fun handleVerified(request: NexusBinderRequest, caller: NexusBinderCaller): String = when (request.verb) {
        NexusBinderVerb.CHALLENGE -> successJson(request.requestId, request.verb.wireName, projectionFactory.challenge(request, caller))
        NexusBinderVerb.AUTH -> successJson(request.requestId, request.verb.wireName, projectionFactory.authenticate(request, caller))
        NexusBinderVerb.OBSERVE -> observe(request, caller)
        NexusBinderVerb.AUDIT -> observe(request, caller)
        NexusBinderVerb.DISPATCH -> dispatch(request, caller)
        NexusBinderVerb.HEARTBEAT -> successJson(request.requestId, request.verb.wireName, projectionFactory.heartbeat(request, caller))
        NexusBinderVerb.DISCONNECT -> {
            sessionCloser.close(request, caller, NexusClosureCause.EXPLICIT_DISCONNECT)
            successJson(request.requestId, request.verb.wireName, JSONObject().put("closed", true))
        }
    }

    override fun closeFromBinderDeath(caller: NexusBinderCaller, cause: NexusClosureCause) {
        sessionCloser.closeFromDeath(caller, cause)
    }

    private fun observe(request: NexusBinderRequest, caller: NexusBinderCaller): String {
        val projection = projectionFactory.observation(request, caller)
        return when (val result = dispatcherProvider().observe(projection, SystemClock.elapsedRealtime())) {
            is NexusObservationResult.State -> successJson(request.requestId, request.verb.wireName, JSONObject()
                .put("snapshot", result.snapshot.toBinderJson())
                .put("health", result.health.toBinderJson()))
            is NexusObservationResult.Audit -> successJson(request.requestId, request.verb.wireName, JSONObject()
                .put("records", JSONArray().also { array -> result.auditRecords.forEach { array.put(it.toBinderJson()) } })
                .put("count", result.auditRecords.size)
                .put("health", result.health.toBinderJson()))
            is NexusObservationResult.GeometryUnavailable -> errorJson(NexusBinderErrorCode.UNSUPPORTED, request.requestId, result.fix)
            is NexusObservationResult.Refused -> preAuthRefusalJson(request.requestId, request.verb.wireName, result.refusal)
        }
    }

    private fun dispatch(request: NexusBinderRequest, caller: NexusBinderCaller): String {
        val projection = projectionFactory.dispatch(request, caller)
        return when (val result = dispatcherProvider().dispatch(projection, SystemClock.elapsedRealtime(), System.currentTimeMillis())) {
            is PhosphorDispatchResult.Accepted -> successJson(request.requestId, request.verb.wireName, result.acknowledgement.toDispatchJson("accepted"))
            is PhosphorDispatchResult.Replayed -> successJson(request.requestId, request.verb.wireName, result.acknowledgement.toDispatchJson("replayed"))
            is PhosphorDispatchResult.Refused -> refusalJson(request.requestId, request.verb.wireName, result.refusal)
            is PhosphorDispatchResult.Failed -> refusalJson(request.requestId, request.verb.wireName, result.refusal)
        }
    }

    private fun refusalJson(requestId: String, verb: String, refusal: Refusal): String = JSONObject()
        .put("ok", false)
        .put("protocol", NEXUS_BINDER_PROTOCOL)
        .put("verb", verb)
        .put("request_id", requestId)
        .put("error", refusal.toBinderJson())
        .toString()
    private fun preAuthRefusalJson(requestId: String, verb: String, refusal: NexusPreAuthRefusal): String = JSONObject()
        .put("ok", false)
        .put("protocol", NEXUS_BINDER_PROTOCOL)
        .put("verb", verb)
        .put("request_id", requestId)
        .put("error", JSONObject()
            .put("code", refusal.code.wireName)
            .put("fix", refusal.fix)
            .put("current_revision", JSONObject.NULL)
            .put("original_receipt_id", JSONObject.NULL))
        .toString()
}

internal fun PhosphorStateSnapshot.toBinderJson(): JSONObject = JSONObject()
    .put("schema", schema)
    .put("session", session ?: JSONObject.NULL)
    .put("revision", revision)
    .put("sequence", sequence)
    .put("wall_time_millis", wallTimeMillis)
    .put("distribution", distribution.wireName)
    .put("profile", buildProfile.wireName)
    .put("desired", desired.toBinderJson())
    .put("effective", effective.toBinderJson())
    .put("liveness", liveness.let { JSONObject()
        .put("session_id", it.sessionId ?: JSONObject.NULL)
        .put("state", it.state.wireName)
        .put("last_heartbeat_monotonic_millis", it.lastHeartbeatMonotonicMillis ?: JSONObject.NULL)
    })
    .put("capabilities", JSONObject().also { root -> capabilities.entries.sortedBy { it.key.kind.wireName + ":" + it.key.stableId }.forEach { (principal, caps) -> root.put(principal.toBinderKey(), JSONArray(caps.map { it.wireName }.sorted())) } })
    .put("provenance", JSONObject().also { root -> provenance.entries.sortedBy { it.key.wireName }.forEach { (field, stamp) -> root.put(field.wireName, stamp.toBinderJson()) } })
    .put("availability", JSONObject().also { root -> availability.entries.sortedBy { it.key.wireName }.forEach { (field, value) -> root.put(field.wireName, value.toBinderJson()) } })
    .put("authority", JSONObject().also { root -> authority.entries.sortedBy { it.key.wireName }.forEach { (field, value) -> root.put(field.wireName, value.toBinderJson()) } })
    .put("fixes", JSONObject().also { root -> fixes.entries.sortedBy { it.key.wireName }.forEach { (field, fix) -> root.put(field.wireName, JSONObject().put("code", fix.code.wireName).put("message", fix.message)) } })

private fun InstrumentSettings.toBinderJson(): JSONObject = JSONObject()
    .put("display_hud", displayHud)

private fun StoreHealth.toBinderJson(): JSONObject = JSONObject()
    .put("readable", readable)
    .put("writable", writable)
    .put("fix", fix?.toBinderJson() ?: JSONObject.NULL)

private fun Availability.toBinderJson(): JSONObject = JSONObject()
    .put("state", state.wireName)
    .put("fix", fix?.let { JSONObject().put("code", it.code.wireName).put("message", it.message) } ?: JSONObject.NULL)

private fun Authority.toBinderJson(): JSONObject = JSONObject()
    .put("state", state.wireName)
    .put("fix", fix?.let { JSONObject().put("code", it.code.wireName).put("message", it.message) } ?: JSONObject.NULL)

private fun AuditRecord.toBinderJson(): JSONObject = JSONObject()
    .put("receipt_id", receiptId)
    .put("kind", kind.wireName)
    .put("wall_time_millis", wallTimeMillis)
    .put("action_type", actionType?.wireName ?: JSONObject.NULL)
    .put("fields", JSONArray(fields.map { it.wireName }.sorted()))
    .put("provenance", provenance?.toBinderJson() ?: JSONObject.NULL)
    .put("refusal", refusal?.toBinderJson() ?: JSONObject.NULL)

private fun ActionAcknowledgement.toDispatchJson(disposition: String): JSONObject = JSONObject()
    .put("disposition", disposition)
    .put("changed", changed)
    .put("revision", revision)
    .put("sequence", sequence)
    .put("effective_value", effectiveValue?.toBinderJson() ?: JSONObject.NULL)
    .put("receipt_id", receiptId)
    .put("provenance", provenance.toBinderJson())
    .put("delta", delta?.toBinderJson() ?: JSONObject.NULL)
    .put("acknowledgement", toBinderJson())

private fun ActionAcknowledgement.toBinderJson(): JSONObject = JSONObject()
    .put("receipt_id", receiptId)
    .put("action_type", actionType.wireName)
    .put("changed", changed)
    .put("revision", revision)
    .put("sequence", sequence)
    .put("effective_value", effectiveValue?.toBinderJson() ?: JSONObject.NULL)
    .put("provenance", provenance.toBinderJson())
    .put("delta", delta?.toBinderJson() ?: JSONObject.NULL)
    .put("effects", JSONArray().also { array -> effects.forEach { array.put(it.toBinderJson()) } })

private fun StateDelta.toBinderJson(): JSONObject = JSONObject()
    .put("event", event)
    .put("revision", revision)
    .put("sequence", sequence)
    .put("changes", JSONArray().also { array -> changes.forEach { array.put(it.toBinderJson()) } })
    .put("provenance", provenance.toBinderJson())

private fun StateChange.toBinderJson(): JSONObject = JSONObject()
    .put("field", field.wireName)
    .put("old_value", oldValue.toBinderJson())
    .put("new_value", newValue.toBinderJson())
    .put("provenance", provenance.toBinderJson())

private fun InertEffectDescription.toBinderJson(): JSONObject = JSONObject()
    .put("kind", kind.wireName)
    .put("description", description)
    .put("provenance", provenance.toBinderJson())

private fun Refusal.toBinderJson(): JSONObject = JSONObject()
    .put("code", code.wireName)
    .put("fix", fix)
    .put("current_revision", currentRevision ?: JSONObject.NULL)
    .put("original_receipt_id", originalReceiptId ?: JSONObject.NULL)

private fun ProvenanceStamp.toBinderJson(): JSONObject = JSONObject()
    .put("receipt_id", receiptId)
    .put("writer", writer.toBinderJson())
    .put("reason", reason)
    .put("transport", transport.wireName)
    .put("session_id", sessionId ?: JSONObject.NULL)
    .put("monotonic_millis", monotonicMillis)
    .put("wall_time_millis", wallTimeMillis)

private fun PrincipalId.toBinderJson(): JSONObject = JSONObject().put("kind", kind.wireName).put("stable_id", stableId)
private fun PrincipalId.toBinderKey(): String = "${kind.wireName}:$stableId"

private fun StateValue.toBinderJson(): JSONObject = when (this) {
    is BooleanStateValue -> JSONObject().put("kind", kind.name.lowercase()).put("value", value)
    is FloatStateValue -> JSONObject().put("kind", kind.name.lowercase()).put("value", value.toDouble())
    is IntStateValue -> JSONObject().put("kind", kind.name.lowercase()).put("value", value)
    is LongStateValue -> JSONObject().put("kind", kind.name.lowercase()).put("value", value)
    is StringStateValue -> JSONObject().put("kind", kind.name.lowercase()).put("value", value)
    is NullableStringStateValue -> JSONObject().put("kind", kind.name.lowercase()).put("value", value ?: JSONObject.NULL)
    is BeamGradientStateValue -> JSONObject().put("kind", kind.name.lowercase()).put("value", JSONObject()
        .put("active_count", value.activeCount)
        .put("colors", JSONArray().also { array -> value.colors.forEach { array.put(it.toBinderJson()) } }))
    is FloatRangeStateValue -> JSONObject().put("kind", kind.name.lowercase()).put("value", JSONObject().put("minimum", value.minimum.toDouble()).put("maximum", value.maximum.toDouble()))
}

private fun RgbaColor.toBinderJson(): JSONObject = JSONObject()
    .put("red", red.toInt())
    .put("green", green.toInt())
    .put("blue", blue.toInt())
    .put("alpha", alpha.toInt())

internal interface NexusBinderProjectionFactory {
    fun challenge(request: NexusBinderRequest, caller: NexusBinderCaller): JSONObject
    fun authenticate(request: NexusBinderRequest, caller: NexusBinderCaller): JSONObject
    fun heartbeat(request: NexusBinderRequest, caller: NexusBinderCaller): JSONObject
    fun observation(request: NexusBinderRequest, caller: NexusBinderCaller): NexusObservationProjection
    fun dispatch(request: NexusBinderRequest, caller: NexusBinderCaller): NexusDispatchProjection
}

internal fun interface NexusBinderSessionCloser {
    fun close(request: NexusBinderRequest, caller: NexusBinderCaller, cause: NexusClosureCause)
    fun closeFromDeath(caller: NexusBinderCaller, cause: NexusClosureCause) = close(
        NexusBinderRequest(NEXUS_BINDER_PROTOCOL, NexusBinderVerb.DISCONNECT, "binder-death", "binder-death", System.currentTimeMillis(), JSONObject()),
        caller,
        cause,
    )
}
