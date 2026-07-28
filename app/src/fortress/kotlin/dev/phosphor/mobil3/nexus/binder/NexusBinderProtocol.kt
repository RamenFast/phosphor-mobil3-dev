package dev.phosphor.mobil3.nexus.binder

import org.json.JSONArray
import org.json.JSONObject

internal const val NEXUS_BINDER_PROTOCOL = "phosphor.nexus/2"
internal const val NEXUS_BINDER_ACTION = "dev.phosphor.mobil3.fortress.NEXUS_BINDER"
internal const val NEXUS_BINDER_PERMISSION = "dev.phosphor.mobil3.fortress.permission.NEXUS_BINDER"
internal const val NEXUS_BINDER_SERVICE = "dev.phosphor.mobil3.nexus.binder.NexusBinderService"
internal const val MAX_BINDER_JSON_BYTES = 16 * 1024
internal const val MAX_BINDER_RESPONSE_BYTES = 256 * 1024
internal const val MAX_BINDER_STRING_CHARS = 4096
internal const val MAX_BINDER_ARRAY_ITEMS = 64
internal const val REPLAY_WINDOW_MILLIS = 30_000L

internal enum class NexusBinderVerb(val wireName: String) {
    CHALLENGE("challenge"), AUTH("auth"), OBSERVE("observe"), AUDIT("audit"), DISPATCH("dispatch"),
    HEARTBEAT("heartbeat"), DISCONNECT("disconnect");

    companion object {
        fun parse(value: String?): NexusBinderVerb? = entries.firstOrNull { it.wireName == value }
    }
}

internal enum class NexusBinderErrorCode(val wireName: String, val fix: String) {
    OVERSIZED("oversized", "Send a JSON object no larger than 16 KiB with strings under 4096 characters and arrays under 64 items."),
    MALFORMED("malformed", "Send a strict JSON object with protocol, verb, request_id, nonce, issued_at_millis, and payload."),
    UNKNOWN_PROTOCOL("unknown_protocol", "Retry with protocol phosphor.nexus/2 and a supported Binder verb."),
    UNAUTHORIZED_CALLER("unauthorized_caller", "Call from the enrolled Nexus package signed by the accepted signing lineage and hold the signature permission."),
    REPLAY("replay", "Retry once with a fresh request_id, nonce, and issued_at_millis."),
    REVOKED("revoked", "Re-authenticate after restoring non-revoked package signing and grants."),
    STALE("stale", "Retry with a fresh request inside the Binder replay window."),
    RESPONSE_OVERSIZED("response_oversized", "Narrow the observation request; the Binder response exceeded the 256 KiB client limit."),
    AUTHORITY_UNAVAILABLE("authority_unavailable", "Bootstrap the Fortress Nexus runtime owner before binding."),
    UNSUPPORTED("unsupported", "Use one of challenge, auth, observe, audit, dispatch, heartbeat, or disconnect."),
}

internal data class NexusBinderRequest(
    val protocol: String,
    val verb: NexusBinderVerb,
    val requestId: String,
    val nonce: String,
    val issuedAtMillis: Long,
    val payload: JSONObject,
)

internal fun successJson(requestId: String?, verb: String?, payload: JSONObject = JSONObject()): String = JSONObject()
    .put("ok", true)
    .put("protocol", NEXUS_BINDER_PROTOCOL)
    .put("verb", verb ?: JSONObject.NULL)
    .put("request_id", requestId ?: JSONObject.NULL)
    .put("payload", payload)
    .toString()

internal fun errorJson(code: NexusBinderErrorCode, requestId: String? = null, detail: String? = null): String = JSONObject()
    .put("ok", false)
    .put("protocol", NEXUS_BINDER_PROTOCOL)
    .put("request_id", requestId ?: JSONObject.NULL)
    .put("error", JSONObject().put("code", code.wireName).put("fix", code.fix).put("detail", detail ?: JSONObject.NULL))
    .toString()

internal fun parseBoundedBinderRequest(raw: String?): EitherError<NexusBinderRequest> {
    if (raw == null) return EitherError.Error(NexusBinderErrorCode.MALFORMED, null, "requestJson was null")
    if (raw.toByteArray(Charsets.UTF_8).size > MAX_BINDER_JSON_BYTES) return EitherError.Error(NexusBinderErrorCode.OVERSIZED)
    val root = try { JSONObject(raw) } catch (e: Exception) { return EitherError.Error(NexusBinderErrorCode.MALFORMED, null, e.message) }
    validateJsonBounds(root)?.let { return EitherError.Error(it, root.optString("request_id", null)) }
    val requestId = root.optString("request_id", "")
    val protocol = root.optString("protocol", "")
    val verbText = root.optString("verb", "")
    if (protocol != NEXUS_BINDER_PROTOCOL) return EitherError.Error(NexusBinderErrorCode.UNKNOWN_PROTOCOL, requestId)
    val verb = NexusBinderVerb.parse(verbText) ?: return EitherError.Error(NexusBinderErrorCode.UNKNOWN_PROTOCOL, requestId, verbText)
    val nonce = root.optString("nonce", "")
    val issuedAt = root.optLong("issued_at_millis", Long.MIN_VALUE)
    if (requestId.isBlank() || nonce.isBlank() || issuedAt == Long.MIN_VALUE) return EitherError.Error(NexusBinderErrorCode.MALFORMED, requestId)
    return EitherError.Value(NexusBinderRequest(protocol, verb, requestId, nonce, issuedAt, root.optJSONObject("payload") ?: JSONObject()))
}

private fun validateJsonBounds(value: Any?): NexusBinderErrorCode? = when (value) {
    is JSONObject -> {
        if (value.length() > MAX_BINDER_ARRAY_ITEMS) NexusBinderErrorCode.OVERSIZED else value.keys().asSequence().firstNotNullOfOrNull { key ->
            if (key.length > MAX_BINDER_STRING_CHARS) NexusBinderErrorCode.OVERSIZED else validateJsonBounds(value.opt(key))
        }
    }
    is JSONArray -> if (value.length() > MAX_BINDER_ARRAY_ITEMS) NexusBinderErrorCode.OVERSIZED else (0 until value.length()).firstNotNullOfOrNull { validateJsonBounds(value.opt(it)) }
    is String -> if (value.length > MAX_BINDER_STRING_CHARS) NexusBinderErrorCode.OVERSIZED else null
    else -> null
}

internal sealed interface EitherError<out T> {
    data class Value<T>(val value: T) : EitherError<T>
    data class Error(val code: NexusBinderErrorCode, val requestId: String? = null, val detail: String? = null) : EitherError<Nothing>
}
