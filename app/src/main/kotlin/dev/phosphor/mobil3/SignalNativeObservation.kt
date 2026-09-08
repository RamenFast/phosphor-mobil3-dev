package dev.phosphor.mobil3

import org.json.JSONObject

/** Foreign ages are already computed by Rust. Never subtract a raw Rust timestamp from Kotlin time. */
internal class SignalNativeObservation {
    private var localOwner: Long? = null
    private var localFrames: Long? = null
    private var localProgress: Long? = null

    fun local(native: JSONObject?, playback: SignalPlayback?, now: Long): SignalInput? {
        if (playback?.kind != SignalKind.LOCAL) return null
        val data = native?.optJSONObject("local")?.takeIf { it.long("open_id") == playback.localOpen }
        val owner = data?.long("open_id")?.takeIf { it > 0 }
        val count = data?.optJSONObject("output")?.long("popped_stereo_frames")
        if (owner != localOwner || count == null || localFrames?.let { count < it } == true) localProgress = null
        else if (localFrames?.let { count > it } == true) localProgress = now
        localOwner = owner
        localFrames = count
        return SignalInput(SignalKind.LOCAL, owner ?: 0, life = playback.life, reason = playback.reason,
            descriptor = SignalDescriptor(unavailable = "Unavailable · local decoder exposes no original format getter"),
            contributing = owner != null && playback.life == SignalLife.RUNNING,
            receiptAt = localProgress, receiptCount = count, receiptUnit = "popped normalized output stereo frames (not recorder ingress)")
    }

    fun relay(native: JSONObject?, playback: SignalPlayback?, now: Long): SignalInput? {
        if (playback?.kind != SignalKind.RELAY) return null
        val data = native?.optJSONObject("relay")
        val session = data?.long("session")?.takeIf { it > 0 }
        val linkCurrent = session != null && session == playback.relaySession &&
            signalAge(now, playback.linkAt)?.let { it <= 2500 } == true
        val linkLife = when (playback.link?.state) {
            RemoteLinkState.FAILED -> SignalLife.FAILED
            RemoteLinkState.RECONNECTING -> SignalLife.RECONNECTING
            RemoteLinkState.CONNECTING, RemoteLinkState.GREETED -> SignalLife.STARTING
            RemoteLinkState.STALLED -> SignalLife.STALLED
            RemoteLinkState.STREAMING, RemoteLinkState.SILENT -> SignalLife.RUNNING
            null -> SignalLife.STARTING
        }
        val life = if (playback.life in setOf(SignalLife.FAILED, SignalLife.STOPPING, SignalLife.ENDED, SignalLife.DISCONNECTED)) playback.life
            else if (linkCurrent || session == null) linkLife else SignalLife.STARTING
        val input = data?.optJSONObject("input")
        val channels = input?.optJSONArray("channels")?.let { array ->
            if (array.length() != 2) emptyList() else (0..1).mapNotNull { i ->
                val c = array.optJSONObject(i) ?: return@mapNotNull null
                val n = c.long("samples") ?: return@mapNotNull null
                val rms = c.finite("rms") ?: return@mapNotNull null
                val peak = c.finite("peak") ?: return@mapNotNull null
                val rails = c.long("full_scale") ?: return@mapNotNull null
                SignalChannel(n, rms, peak, rails)
            }.takeIf { it.size == 2 } ?: emptyList()
        } ?: emptyList()
        val window = if (session != null && input != null && input.long("input_stereo_frames") != null &&
            input.long("valid_frames") != null && input.long("invalid_samples") != null) SignalWindow(session, null,
            input.ageAt("level_age_ms", now), null, input.ageAt("positive_age_ms", now), 0,
            input.long("input_stereo_frames")!!, input.long("valid_frames")!!,
            input.long("invalid_samples")!!, channels) else null
        val pcmAt = window?.lastPositiveAt
        val geometryAt = data?.ageAt("geometry_age_ms", now)
        val latest = listOfNotNull(pcmAt, geometryAt).maxOrNull()
        return SignalInput(SignalKind.RELAY, session ?: 0, life = life,
            reason = playback.reason.ifBlank { if (!linkCurrent && session != null) "Current-session classified link observation unavailable" else playback.link?.failure.orEmpty() },
            descriptor = SignalDescriptor(unavailable = "Unavailable · relay does not publish a validated original recorder format"),
            window = window, contributing = linkCurrent && life == SignalLife.RUNNING,
            normalized = "48,000 Hz · stereo float relay transport (not original recorder negotiation)",
            receiptAt = latest, receiptCount = if (geometryAt != null && (pcmAt == null || geometryAt > pcmAt)) data?.long("geometry_points") else window?.ingressFrames,
            receiptUnit = if (geometryAt != null && (pcmAt == null || geometryAt > pcmAt)) "observed valid geometry points (not PCM samples)" else "observed valid relay PCM stereo frames")
    }

    fun details(native: JSONObject?, input: SignalInput?, playback: SignalPlayback?, now: Long): List<Pair<String, String>> {
        val rows = mutableListOf<Pair<String, String>>()
        rows += "Transport intent" to playback?.intent.text("requested playing", "requested paused")
        rows += "Observed external transport" to if (playback?.kind in setOf(SignalKind.CAPTURE, SignalKind.ROOT)) {
            "${playback?.transport.text("playing", "paused")} · ${signalAge(now, playback?.transportAt)?.let { "$it ms old" } ?: "age unavailable"} · chosen external controller only"
        } else "Unavailable · not inferred from the Play/Pause button"
        if (native == null) { rows += "Native path" to "Unavailable · snapshot read failed"; return rows }
        val scope = native.optJSONObject("scope")
        val matched = input?.nativeOwner?.let { it > 0 && it == scope?.long("capture_owner") } == true ||
            (input?.kind == SignalKind.LOCAL && input.owner > 0 && input.owner == scope?.long("local_owner"))
        rows += "Native admission owner" to (input?.nativeOwner?.toString() ?: "Unavailable / not a capture reader")
        if (matched && input?.kind != SignalKind.LOCAL) rows += "Visual admission" to count(scope, "admitted_stereo_frames", "normalized stereo frames admitted")
        if (matched) {
            rows += "Visual consumption" to count(scope, "consumed_stereo_frames", "normalized stereo frames consumed")
            if (input?.kind != SignalKind.LOCAL) rows += "Visual epoch rejection" to count(scope, "rejected_epoch_stereo_frames", "normalized stereo frames rejected (ingress may still be real)")
        } else rows += "Visual path counters" to "Unavailable · native owner does not match this input"
        if (input?.kind == SignalKind.LOCAL) {
            val local = native.optJSONObject("local")?.takeIf { it.long("open_id") == input.owner }
            rows += "Local output" to output(local?.optJSONObject("output"))
            rows += "Local raw input meter" to "Unavailable · decoder ingress has no bounded owner getter. Output progress is separate."
        }
        if (input?.kind == SignalKind.RELAY) {
            val relay = native.optJSONObject("relay")?.takeIf { it.long("session") == input.session }
            rows += "Relay PCM observation" to "Received valid media before phone mute / zero-fill. Geometry has no PCM level."
            rows += "Link classification age" to (signalAge(now, playback?.linkAt)?.let { "$it ms old · existing service classifier" } ?: "Unavailable")
            rows += "Relay observation coverage" to count(relay, "diagnostic_blocks_skipped", "diagnostic blocks skipped without delaying the receiver. Receipt counters count observed blocks only.")
            rows += "Relay-reported RMS" to reported(relay?.optJSONObject("relay_rms"))
            rows += "Relay-reported RMS peak" to "${reported(relay?.optJSONObject("relay_rms_peak"))} · not sample peak / clipping"
            rows += "Relay phone output" to output(relay?.optJSONObject("output"))
            rows += "Relay output policy" to (relay?.let { "Mute ${it.boolean("muted").text("on", "off")} · audio ${it.boolean("audio_enabled").text("enabled", "disabled")} · geometry ${it.boolean("geometry_enabled").text("enabled", "disabled")}" } ?: "Unavailable")
            rows += "Relay observed transport" to (relay?.let {
                val value = if (it.isNull("transport_playing")) null else it.optBoolean("transport_playing")
                "${value.text("playing", "paused")} · ${it.long("transport_age_ms")?.let { age -> "$age ms old" } ?: "age unavailable"} · metadata, not command acknowledgement"
            } ?: "Unavailable")
            val relayScope = relay?.optJSONObject("scope")
            rows += "Relay visual admission" to count(relayScope, "admitted_stereo_frames", "post-policy stereo frames admitted")
            rows += "Relay visual consumption" to count(relayScope, "consumed_stereo_frames", "post-policy stereo frames consumed")
        }
        return rows
    }

    private fun reported(value: JSONObject?): String {
        val level = value?.finite("value") ?: return "Unavailable · not reported by this session"
        val age = value.long("age_ms") ?: return "Unavailable · age missing"
        return if (age > SignalPresentation.FRESH_MS) "Unavailable · stale relay report ($age ms old)" else "$level · $age ms old · relay-reported"
    }
    private fun count(value: JSONObject?, key: String, unit: String) = value?.long(key)?.let { "$it $unit" } ?: "Unavailable"
    private fun output(value: JSONObject?) = listOf(
        count(value, "popped_stereo_frames", "positive popped stereo frames"),
        count(value, "finalized_stereo_frames", "finalized stereo frames"),
        count(value, "zero_filled_stereo_frames", "zero-filled stereo frames"),
    ).joinToString(" · ")
    private fun Boolean?.text(yes: String, no: String) = when (this) { true -> yes; false -> no; null -> "Unavailable" }
    private fun JSONObject.long(key: String): Long? = if (isNull(key)) null else optLong(key, -1).takeIf { it >= 0 }
    private fun JSONObject.boolean(key: String): Boolean? = if (isNull(key)) null else optBoolean(key)
    private fun JSONObject.finite(key: String): Double? = if (isNull(key)) null else optDouble(key, Double.NaN).takeIf { it.isFinite() && it >= 0 }
    private fun JSONObject.ageAt(key: String, now: Long): Long? = long(key)?.takeIf { it <= now }?.let { now - it }
}
