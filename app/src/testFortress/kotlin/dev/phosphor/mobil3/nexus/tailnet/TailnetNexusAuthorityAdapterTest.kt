package dev.phosphor.mobil3.nexus.tailnet

import dev.phosphor.mobil3.state.Capability
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.json.JSONObject
import org.junit.Test

class TailnetNexusAuthorityAdapterTest {
    private val identity = TailnetIdentity("principal", "endpoint", "token", 7)

    @Test fun authenticateConvertsActualPhosphorCapabilitiesAndRejectsLaneCapability() {
        val port = RecordingPort()
        val adapter = TailnetNexusAuthorityAdapter(port)
        val request = TailnetAuthenticationRequest(
            identity = identity,
            endpoint = TailnetEndpoint("relay.example", 7443, identity.pinnedEndpointIdentity),
            protocol = PHOSPHOR_TAILNET_RPC_PROTOCOL,
            profile = "local_dev",
            requestedPhosphorCapabilities = setOf("observe.state", "observe.audit", "control.display"),
            heartbeatMillis = 1_000,
            leaseMillis = 4_000,
        )
        adapter.authenticate(request)
        assertEquals(setOf(Capability.OBSERVE_STATE, Capability.OBSERVE_AUDIT, Capability.CONTROL_DISPLAY), port.attachedCapabilities)

        assertFailsWith<IllegalArgumentException> {
            adapter.authenticate(request.copy(requestedPhosphorCapabilities = setOf("phosphor_rpc")))
        }
    }

    @Test fun dispatchPreservesTypedAcceptedAndRefusalResults() {
        val port = RecordingPort(dispatchResult = TailnetDispatchResult.refused(TailnetNexusAuthorityAdapter.refusal("capability_denied", "Grant control.display persistently.")))
        val adapter = TailnetNexusAuthorityAdapter(port)
        adapter.authenticate(request())
        val result = adapter.dispatch(identity, TailnetOperation("display.hud.set", JSONObject().put("mode", "compact")))
        assertFalse(result.accepted)
        assertEquals("capability_denied", result.result.getJSONObject("refusal").getString("code"))

        val unsupported = adapter.dispatch(identity, TailnetOperation("display.bad", JSONObject()))
        assertFalse(unsupported.accepted)
        assertEquals("unsupported_operation", unsupported.result.getJSONObject("refusal").getString("code"))
    }

    @Test fun lifecycleCallsOnlyAffectActiveIdentityAndDisconnectClearsActive() {
        val port = RecordingPort()
        val adapter = TailnetNexusAuthorityAdapter(port)
        adapter.authenticate(request())
        adapter.heartbeat(identity.copy(generation = 8), 1, 100)
        assertEquals(0, port.heartbeats)
        adapter.heartbeat(identity, 1, 100)
        assertEquals(1, port.heartbeats)
        adapter.disconnect(identity, "done")
        assertEquals(1, port.disconnects)
        assertFailsWith<IllegalArgumentException> { adapter.observe(identity) }
    }

    private fun request() = TailnetAuthenticationRequest(
        identity = identity,
        endpoint = TailnetEndpoint("relay.example", 7443, identity.pinnedEndpointIdentity),
        protocol = PHOSPHOR_TAILNET_RPC_PROTOCOL,
        profile = "local_dev",
        requestedPhosphorCapabilities = setOf("observe.state", "observe.audit", "control.display"),
        heartbeatMillis = 1_000,
        leaseMillis = 4_000,
    )

    private class RecordingPort(
        private val dispatchResult: TailnetDispatchResult = TailnetDispatchResult.accepted(JSONObject().put("status", "ok")),
    ) : TailnetSharedRuntimePort {
        var attachedCapabilities: Set<Capability> = emptySet()
        var heartbeats = 0
        var disconnects = 0
        override fun attachTailnet(request: TailnetAuthenticationRequest, capabilities: Set<Capability>): TailnetSessionAttachment {
            attachedCapabilities = capabilities
            return TailnetSessionAttachment(TailnetSession("s", request.identity.generation, TailnetEndpoint("relay", 1, request.identity.pinnedEndpointIdentity)))
        }
        override fun observeState(identity: TailnetIdentity): JSONObject = JSONObject().put("state", true)
        override fun observeAudit(identity: TailnetIdentity, args: JSONObject): JSONObject = JSONObject().put("audit", true)
        override fun dispatchDisplayHud(identity: TailnetIdentity, args: JSONObject): TailnetDispatchResult = dispatchResult
        override fun recordHeartbeat(identity: TailnetIdentity, sequence: Long, monotonicMillis: Long) { heartbeats += 1 }
        override fun markClosing(identity: TailnetIdentity, reason: String): Boolean = true
        override fun markAbsent(identity: TailnetIdentity, reason: String): Boolean = true
        override fun disconnect(identity: TailnetIdentity, reason: String): Boolean { disconnects += 1; return true }
    }
}
