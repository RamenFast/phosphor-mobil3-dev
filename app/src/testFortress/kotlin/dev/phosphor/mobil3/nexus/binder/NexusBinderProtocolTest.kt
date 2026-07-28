package dev.phosphor.mobil3.nexus.binder

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.os.RemoteException
import dev.phosphor.mobil3.nexus.NexusClosureCause
import dev.phosphor.mobil3.nexus.NexusBuildProfile
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.json.JSONObject
import org.junit.Test

class NexusBinderProtocolTest {
    private val lineage = listOf("0".repeat(64), "a".repeat(64))
    private val caller = NexusBinderCaller(42, "dev.nexus.mobile", "a".repeat(64), lineage)
    private val owner = MinimalNexusBinderRuntimeOwner(
        NexusBinderCallerPolicy("dev.nexus.mobile", "a".repeat(64), lineage),
    )

    @Test fun currentSignerIsExplicitAndCannotBeRedefinedByLineageOrder() {
        val old = "0".repeat(64)
        val current = "1".repeat(64)
        val lineage = listOf(old, current)
        val policy = NexusBinderCallerPolicy("dev.nexus.mobile", current, lineage)

        assertTrue(policy.accepts(NexusBinderCaller(7, "dev.nexus.mobile", current, lineage)))
        assertFalse(policy.accepts(NexusBinderCaller(7, "dev.nexus.mobile", old, lineage)))
        assertFalse(policy.accepts(NexusBinderCaller(7, "dev.nexus.mobile", current, listOf(current, old))))
    }

    @Test fun binderCallerPolicyRejectsMalformedDuplicateAndIncoherentLineage() {
        val old = "0".repeat(64)
        val current = "1".repeat(64)
        assertFailsWith<IllegalArgumentException> {
            NexusBinderCallerPolicy("dev.nexus.mobile", "A".repeat(64), listOf(old, current))
        }
        assertFailsWith<IllegalArgumentException> {
            NexusBinderCallerPolicy("dev.nexus.mobile", current, listOf(old, current, current))
        }
        assertFailsWith<IllegalArgumentException> {
            NexusBinderCallerPolicy("dev.nexus.mobile", current, listOf(current, old))
        }
        assertFailsWith<IllegalArgumentException> {
            NexusBinderCallerPolicy("dev.nexus.mobile", old, listOf(old, current))
        }
    }

    @Test fun binderAuthProfileIsBuildTypedAndRejectsCrossProfileClaims() {
        assertEquals(NexusBuildProfile.LOCAL_DEV, requiredBinderAuthProfile(debug = true))
        assertEquals(NexusBuildProfile.NEXUS, requiredBinderAuthProfile(debug = false))
        assertFalse(requiredBinderAuthProfile(debug = true) == NexusBuildProfile.NEXUS, "debug Binder auth must not accept nexus profile")
        assertFalse(requiredBinderAuthProfile(debug = false) == NexusBuildProfile.LOCAL_DEV, "release Binder auth must not accept local_dev profile")
    }

    @Test fun binderAuthProfileParsingIsStrictForMissingMalformedAndWrongBuildProfile() {
        assertEquals(NexusBuildProfile.LOCAL_DEV, parseBinderAuthProfile(JSONObject().put("profile", "local_dev"), NexusBuildProfile.LOCAL_DEV))
        assertEquals(NexusBuildProfile.NEXUS, parseBinderAuthProfile(JSONObject().put("profile", "nexus"), NexusBuildProfile.NEXUS))
        assertFailsWith<IllegalArgumentException> { parseBinderAuthProfile(JSONObject(), NexusBuildProfile.NEXUS) }
        assertFailsWith<IllegalArgumentException> { parseBinderAuthProfile(JSONObject().put("profile", "garbage"), NexusBuildProfile.NEXUS) }
        assertFailsWith<IllegalArgumentException> { parseBinderAuthProfile(JSONObject().put("profile", "nexus"), NexusBuildProfile.LOCAL_DEV) }
        assertFailsWith<IllegalArgumentException> { parseBinderAuthProfile(JSONObject().put("profile", "local_dev"), NexusBuildProfile.NEXUS) }
    }

    @Test fun parserRejectsOversizedMalformedAndUnknownProtocolWithFix() {
        assertError("oversized", parseBoundedBinderRequest("{\"x\":\"${"x".repeat(MAX_BINDER_JSON_BYTES)}\"}"))
        assertError("malformed", parseBoundedBinderRequest("not-json"))
        val wrong = request(protocol = "phosphor.nexus/1")
        assertError("unknown_protocol", parseBoundedBinderRequest(wrong))
    }

    @Test fun handlerVerifiesCallerBeforeDisclosureAndAcceptsEveryVerb() {
        val denied = handler(callerResult = null).handle(request(), Binder())
        assertFalse(JSONObject(denied).getBoolean("ok"))
        assertEquals("unauthorized_caller", JSONObject(denied).getJSONObject("error").getString("code"))

        NexusBinderVerb.entries.forEachIndexed { index, verb ->
            val body = handler().handle(request(verb = verb.wireName, requestId = "r$index", nonce = "n$index"), null)
            val json = JSONObject(body)
            if (verb in setOf(NexusBinderVerb.AUTH, NexusBinderVerb.HEARTBEAT)) {
                assertFalse(json.getBoolean("ok"), body)
                assertEquals(if (verb == NexusBinderVerb.AUTH) "revoked" else "stale", json.getJSONObject("error").getString("code"))
                return@forEachIndexed
            }
            assertTrue(json.getBoolean("ok"), body)
            assertEquals(verb.wireName, json.getString("verb"))
            assertNotNull(json.getJSONObject("payload"))
        }
    }

    @Test fun challengeThenFreshOuterAuthSucceedsAndReplayFails() {
        val handler = handler(clock = { 10_000L })
        val challengeResponse = JSONObject(handler.handle(request(verb = "challenge", requestId = "challenge-r", nonce = "client-n", issuedAtMillis = 10_000L), null))
        assertTrue(challengeResponse.getBoolean("ok"), challengeResponse.toString())
        val challenge = challengeResponse.getJSONObject("payload")
        assertEquals(setOf("challenge_id", "server_nonce", "protocol", "expires_at_millis"), challenge.keys().asSequence().toSet())

        val authPayload = JSONObject()
            .put("challenge_id", challenge.getString("challenge_id"))
            .put("challenge_request_id", "challenge-r")
            .put("client_nonce", "client-n")
            .put("server_nonce", challenge.getString("server_nonce"))
        val auth = request(verb = "auth", requestId = "auth-r", nonce = "auth-n", issuedAtMillis = 10_001L, payload = authPayload)
        val authResponse = JSONObject(handler.handle(auth, RecordingBinder()))
        assertTrue(authResponse.getBoolean("ok"), authResponse.toString())
        assertTrue(authResponse.getJSONObject("payload").getBoolean("authenticated"))

        val replay = JSONObject(handler.handle(auth, Binder()))
        assertFalse(replay.getBoolean("ok"))
        assertEquals("replay", replay.getJSONObject("error").getString("code"))
    }

    @Test fun handlerRejectsReplayAndStaleRequestsWithFix() {
        val handler = handler(clock = { 10_000L })
        val first = handler.handle(request(requestId = "same", nonce = "nonce", issuedAtMillis = 10_000L), Binder())
        assertTrue(JSONObject(first).getBoolean("ok"))
        val replay = JSONObject(handler.handle(request(requestId = "same", nonce = "nonce", issuedAtMillis = 10_000L), Binder()))
        assertEquals("replay", replay.getJSONObject("error").getString("code"))
        assertTrue(replay.getJSONObject("error").getString("fix").isNotBlank())

        val stale = JSONObject(handler().handle(request(issuedAtMillis = 1L), Binder()))
        assertEquals("stale", stale.getJSONObject("error").getString("code"))

        val future = JSONObject(handler(clock = { 10_000L }).handle(request(requestId = "future", nonce = "future", issuedAtMillis = 41_001L), Binder()))
        assertEquals("stale", future.getJSONObject("error").getString("code"))
    }

    @Test fun authenticatedClientTokenIsBoundOnceAndReplacementIsRejected() {
        val handler = handler(clock = { 10_000L })
        val challenge = JSONObject(handler.handle(request(verb = "challenge", requestId = "c-token", nonce = "n-token", issuedAtMillis = 10_000L), null)).getJSONObject("payload")
        val authPayload = JSONObject()
            .put("challenge_id", challenge.getString("challenge_id"))
            .put("challenge_request_id", "c-token")
            .put("client_nonce", "n-token")
            .put("server_nonce", challenge.getString("server_nonce"))
        val token = RecordingBinder()
        assertTrue(JSONObject(handler.handle(request(verb = "auth", requestId = "a-token", nonce = "a-nonce", issuedAtMillis = 10_001L, payload = authPayload), token)).getBoolean("ok"))
        val replacementChallenge = JSONObject(handler.handle(request(verb = "challenge", requestId = "c-token-2", nonce = "n-token-2", issuedAtMillis = 10_002L), null)).getJSONObject("payload")
        val replacementPayload = JSONObject()
            .put("challenge_id", replacementChallenge.getString("challenge_id"))
            .put("challenge_request_id", "c-token-2")
            .put("client_nonce", "n-token-2")
            .put("server_nonce", replacementChallenge.getString("server_nonce"))
        val replacement = JSONObject(handler.handle(request(verb = "auth", requestId = "a-token-2", nonce = "a-nonce-2", issuedAtMillis = 10_003L, payload = replacementPayload), RecordingBinder()))
        assertFalse(replacement.getBoolean("ok"))
        assertEquals("revoked", replacement.getJSONObject("error").getString("code"))
    }

    @Test fun setupCancellationBeforeAuthCompletionClosesLateAuthorityForExactClientToken() {
        val handler = handler(clock = { 10_000L })
        val token = RecordingBinder()
        val challenge = JSONObject(
            handler.handle(
                request(verb = "challenge", requestId = "c-cancel", nonce = "n-cancel", issuedAtMillis = 10_000L),
                null,
            ),
        ).getJSONObject("payload")
        val cancellation = JSONObject(
            handler.handle(
                request(verb = "disconnect", requestId = "d-cancel", nonce = "d-nonce", issuedAtMillis = 10_001L),
                token,
            ),
        )
        assertTrue(cancellation.getBoolean("ok"), cancellation.toString())
        assertTrue(cancellation.getJSONObject("payload").getBoolean("setup_cancellation_pending"))

        val authPayload = JSONObject()
            .put("challenge_id", challenge.getString("challenge_id"))
            .put("challenge_request_id", "c-cancel")
            .put("client_nonce", "n-cancel")
            .put("server_nonce", challenge.getString("server_nonce"))
        val lateAuth = JSONObject(
            handler.handle(
                request(verb = "auth", requestId = "a-cancel", nonce = "a-nonce", issuedAtMillis = 10_002L, payload = authPayload),
                token,
            ),
        )
        assertFalse(lateAuth.getBoolean("ok"), lateAuth.toString())
        assertEquals("revoked", lateAuth.getJSONObject("error").getString("code"))
        assertEquals(NexusClosureCause.EXPLICIT_DISCONNECT, owner.lastClosureCause)
        assertEquals(null, token.linked)
    }

    @Test fun authenticatedBinderHeartbeatSucceedsAndUnauthenticatedHeartbeatFails() {
        val handler = handler(clock = { 10_000L })
        val challenge = JSONObject(handler.handle(request(verb = "challenge", requestId = "c-heart", nonce = "n-heart", issuedAtMillis = 10_000L), null)).getJSONObject("payload")
        val authPayload = JSONObject()
            .put("challenge_id", challenge.getString("challenge_id"))
            .put("challenge_request_id", "c-heart")
            .put("client_nonce", "n-heart")
            .put("server_nonce", challenge.getString("server_nonce"))
        val token = RecordingBinder()
        assertTrue(JSONObject(handler.handle(request(verb = "auth", requestId = "a-heart", nonce = "a-heart-n", issuedAtMillis = 10_001L, payload = authPayload), token)).getBoolean("ok"))
        val heartbeat = JSONObject(handler.handle(request(verb = "heartbeat", requestId = "h-heart", nonce = "h-heart-n", issuedAtMillis = 10_002L), token))
        assertTrue(heartbeat.getBoolean("ok"), heartbeat.toString())
        assertTrue(heartbeat.getJSONObject("payload").getBoolean("alive"))

        val staleOwner = MinimalNexusBinderRuntimeOwner(owner.callerPolicy)
        val stale = NexusBinderRequestHandler(ownerProvider = { staleOwner }, callerVerifier = { caller }, replayGuard = NexusBinderReplayGuard(clock = { 10_000L }))
        val staleHeartbeat = JSONObject(stale.handle(request(verb = "heartbeat", requestId = "h-stale", nonce = "h-stale-n", issuedAtMillis = 10_000L), RecordingBinder()))
        assertFalse(staleHeartbeat.getBoolean("ok"))
        assertEquals("stale", staleHeartbeat.getJSONObject("error").getString("code"))
    }

    @Test fun binderDeathClosesAuthorityImmediatelyWithBinderDeath() {
        val token = Binder()
        val body = handler().handle(request(), token)
        assertTrue(JSONObject(body).getBoolean("ok"))
        owner.closeFromBinderDeath(caller)
        assertEquals(NexusClosureCause.BINDER_DEATH, owner.lastClosureCause)
    }

    private fun handler(
        clock: () -> Long = { System.currentTimeMillis() },
        callerResult: NexusBinderCaller? = caller,
    ) = NexusBinderRequestHandler(
        ownerProvider = { owner },
        callerVerifier = { callerResult },
        replayGuard = NexusBinderReplayGuard(clock),
    )

    private fun request(
        protocol: String = NEXUS_BINDER_PROTOCOL,
        verb: String = "challenge",
        requestId: String = "r1",
        nonce: String = "n1",
        issuedAtMillis: Long = System.currentTimeMillis(),
        payload: JSONObject = JSONObject(),
    ) = JSONObject()
        .put("protocol", protocol)
        .put("verb", verb)
        .put("request_id", requestId)
        .put("nonce", nonce)
        .put("issued_at_millis", issuedAtMillis)
        .put("payload", payload)
        .toString()

    private fun assertError(code: String, result: EitherError<NexusBinderRequest>) {
        val error = result as EitherError.Error
        assertEquals(code, error.code.wireName)
        assertTrue(error.code.fix.isNotBlank())
    }

    private class RecordingBinder(private val failLink: Boolean = false) : IBinder {
        var linked: IBinder.DeathRecipient? = null
            private set
        var unlinkCount: Int = 0
            private set

        override fun getInterfaceDescriptor(): String = "test"
        override fun pingBinder(): Boolean = true
        override fun isBinderAlive(): Boolean = true
        override fun queryLocalInterface(descriptor: String): IInterface? = null
        override fun dump(fd: java.io.FileDescriptor, args: Array<out String>?) = Unit
        override fun dumpAsync(fd: java.io.FileDescriptor, args: Array<out String>?) = Unit
        override fun transact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean = false
        override fun linkToDeath(recipient: IBinder.DeathRecipient, flags: Int) {
            if (failLink) throw RemoteException("link failed")
            linked = recipient
        }
        override fun unlinkToDeath(recipient: IBinder.DeathRecipient, flags: Int): Boolean {
            unlinkCount += 1
            return linked === recipient
        }
    }
}
