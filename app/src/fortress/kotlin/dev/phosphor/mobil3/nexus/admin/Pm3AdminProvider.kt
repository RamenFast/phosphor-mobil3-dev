package dev.phosphor.mobil3.nexus.admin

import android.content.ContentProvider
import android.content.ContentValues
import android.content.SharedPreferences
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import android.util.Base64
import dev.phosphor.mobil3.BuildConfig
import dev.phosphor.mobil3.PhosphorApplication
import dev.phosphor.mobil3.nexus.AndroidSigningEvidence
import dev.phosphor.mobil3.nexus.CertificateSha256
import dev.phosphor.mobil3.nexus.NexusAuthorityCodec
import dev.phosphor.mobil3.nexus.NexusAuthorityDecodeResult
import dev.phosphor.mobil3.nexus.NexusAuthorityStoreTransaction
import dev.phosphor.mobil3.nexus.NexusAuthorityTransactionResult
import dev.phosphor.mobil3.nexus.NexusCapabilityGrant
import dev.phosphor.mobil3.nexus.NexusClosureCause
import dev.phosphor.mobil3.nexus.NexusGrantScope
import dev.phosphor.mobil3.nexus.NexusAuthorityImage
import dev.phosphor.mobil3.nexus.NexusBuildProfile
import dev.phosphor.mobil3.nexus.NexusRuntimeManagers
import dev.phosphor.mobil3.nexus.NexusTrustKey
import dev.phosphor.mobil3.nexus.tailnet.PhosphorTailnetRuntimeBootstrap
import dev.phosphor.mobil3.nexus.tailnet.TailnetNexusAuthorityAdapter
import dev.phosphor.mobil3.nexus.tailnet.TailnetRuntimeConfig
import dev.phosphor.mobil3.nexus.PinnedEndpointIdentity
import dev.phosphor.mobil3.state.ActionAcknowledgement
import dev.phosphor.mobil3.state.ActionRequest
import dev.phosphor.mobil3.state.AuditRecord
import dev.phosphor.mobil3.state.Capability
import dev.phosphor.mobil3.state.PrincipalId
import dev.phosphor.mobil3.state.PrincipalKind
import dev.phosphor.mobil3.state.Refusal
import dev.phosphor.mobil3.state.SetDisplayHud
import dev.phosphor.mobil3.state.Transport
import dev.phosphor.mobil3.store.LOCAL_HUMAN_PRINCIPAL_ID
import dev.phosphor.mobil3.store.PhosphorDispatchResult
import dev.phosphor.mobil3.store.PhosphorStateStore
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Fortress-only pm3 operator provider. Control uses call; tailnet-secret uses write-only openFile. */
class Pm3AdminProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        Handler(Looper.getMainLooper()).post { runCatching { restartTailnetIfConfigured() } }
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val response = try {
            enforceCaller()
            require(method == METHOD) { "unsupported method" }
            val request = decodeRequest(extras)
            handle(request)
        } catch (error: Throwable) {
            JSONObject()
                .put("ok", false)
                .put("error", error::class.java.simpleName.ifBlank { "error" })
                .put("message", error.message ?: "pm3 provider refused the request")
                .put("fix", "Retry with adb shell/root, android.permission.DUMP, method $METHOD, and a valid base64url JSON request.")
        }
        return Bundle().apply { putString(KEY_RESPONSE, encodeJson(response)) }
    }

    private fun handle(request: JSONObject): JSONObject {
        val verb = request.getString("verb")
        val args = request.optJSONObject("args") ?: JSONObject()
        val store = store()
        val data = when (verb) {
            "state-get" -> stateJson(store)
            "state-watch" -> watchJson(store, args)
            "nexus-status" -> nexusStatusJson(store)
            "nexus-grant" -> grantJson(store, args)
            "nexus-revoke" -> revokeJson(store, args)
            "tailnet-status" -> tailnetStatusJson()
            "tailnet-configure" -> tailnetConfigureJson(args)
            "tailnet-clear" -> tailnetClearJson()
            "tailnet-start" -> tailnetStartJson()
            "tailnet-stop" -> tailnetStopJson()
            "action-run" -> actionRunJson(store, args)
            "audit-list" -> auditListJson(store, args)
            "audit-export" -> auditExportJson(store, args)
            else -> throw IllegalArgumentException("unsupported pm3 verb: $verb")
        }
        return JSONObject()
            .put("ok", true)
            .put("protocol", PROTOCOL_VERSION)
            .put("verb", verb)
            .put("data", data)
    }

    private fun store(): PhosphorStateStore {
        val app = context?.applicationContext as? PhosphorApplication
            ?: throw IllegalStateException("PhosphorApplication unavailable")
        return app.causalStore
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        enforceCaller()
        require(mode == "w" || mode == "wt") { "tailnet-secret is write-only" }
        require(uri.path == "/tailnet-secret") { "unsupported pm3 file target" }
        val pipe = ParcelFileDescriptor.createPipe()
        Thread({
            pipe[0].use { readFd ->
                val bytes = ParcelFileDescriptor.AutoCloseInputStream(readFd).use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    var total = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_SECRET_BYTES) throw IllegalArgumentException("tailnet secret exceeds $MAX_SECRET_BYTES bytes")
                        out.write(buffer, 0, read)
                    }
                    out.toByteArray()
                }
                require(bytes.isNotEmpty()) { "tailnet secret is empty" }
                try {
                    saveEncryptedSecret(bytes)
                } finally {
                    bytes.fill(0)
                }
            }
        }, "pm3-tailnet-secret-writer").start()
        return pipe[1]
    }

    private fun stateJson(store: PhosphorStateStore): JSONObject = store.observe().let { obs ->
        JSONObject()
            .put("schema", obs.snapshot.schema)
            .put("revision", obs.snapshot.revision)
            .put("sequence", obs.snapshot.sequence)
            .put("wall_time_millis", obs.snapshot.wallTimeMillis)
            .put("distribution", obs.snapshot.distribution.wireName)
            .put("build_profile", obs.snapshot.buildProfile.wireName)
            .put("display", JSONObject().put("hud", obs.snapshot.effective.displayHud))
            .put("desired", JSONObject().put("display_hud", obs.snapshot.desired.displayHud))
            .put("health", JSONObject().put("readable", obs.health.readable).put("writable", obs.health.writable))
            .put("audit_count", obs.auditRecords.size)
    }

    private fun watchJson(store: PhosphorStateStore, args: JSONObject): JSONObject {
        val polls = args.optInt("polls", 1).coerceIn(1, 64)
        val intervalMillis = args.optLong("interval_millis", 0L).coerceIn(0L, 1_000L)
        val events = JSONArray()
        var lastRevision = -1L
        repeat(polls) { index ->
            val snapshot = store.snapshot
            if (snapshot.revision != lastRevision) {
                events.put(JSONObject().put("event", "state").put("poll", index).put("state", stateJson(store)))
                lastRevision = snapshot.revision
            }
            if (intervalMillis > 0L && index + 1 < polls) SystemClock.sleep(intervalMillis)
        }
        return JSONObject().put("events", events).put("bounded", true).put("polls", polls)
    }

    private fun nexusStatusJson(store: PhosphorStateStore): JSONObject {
        val authority = when (val decoded = NexusAuthorityCodec.decode(store.authorityPlane)) {
            NexusAuthorityDecodeResult.Absent -> JSONObject().put("status", "absent")
            is NexusAuthorityDecodeResult.Corrupt -> JSONObject().put("status", "corrupt").put("refusal", refusalJson(decoded.refusal))
            is NexusAuthorityDecodeResult.Loaded -> authorityImageJson(decoded.image)
        }
        val snap = store.snapshot
        return JSONObject()
            .put("session_id", snap.session ?: JSONObject.NULL)
            .put("lifecycle", snap.liveness.state.wireName)
            .put("authority", authority)
    }

    private fun grantJson(store: PhosphorStateStore, args: JSONObject): JSONObject {
        val trustKey = trustKey(args.getJSONObject("trust"))
        val capability = capability(args.getString("capability"))
        val receiptId = args.optString("receipt_id").ifBlank { "pm3-grant-${System.currentTimeMillis()}" }
        val grant = NexusCapabilityGrant(trustKey, capability, NexusGrantScope.PERSISTENT, null, receiptId)
        return when (val result = NexusAuthorityStoreTransaction.grantPersistent(store, grant, System.currentTimeMillis())) {
            is NexusAuthorityTransactionResult.Committed -> JSONObject().put("committed", true).put("authority", authorityImageJson(result.image))
            is NexusAuthorityTransactionResult.Refused -> JSONObject().put("committed", false).put("refusal", refusalJson(result.refusal))
        }
    }

    private fun revokeJson(store: PhosphorStateStore, args: JSONObject): JSONObject {
        val trustKey = trustKey(args.getJSONObject("trust"))
        val capability = capability(args.getString("capability"))
        return when (val result = NexusAuthorityStoreTransaction.revokePersistent(store, trustKey, capability, System.currentTimeMillis())) {
            is NexusAuthorityTransactionResult.Refused -> JSONObject().put("committed", false).put("refusal", refusalJson(result.refusal))
            is NexusAuthorityTransactionResult.Committed -> {
                val closedActiveSession = NexusRuntimeManagers.forStore(store).revokeActiveIfMatches(trustKey, capability, NexusClosureCause.EXPLICIT_REVOKE)
                JSONObject().put("committed", true).put("closed_active_session", closedActiveSession).put("authority", authorityImageJson(result.image))
            }
        }
    }

    private fun tailnetStatusJson(): JSONObject {
        val prefs = tailnetPrefs()
        return JSONObject()
            .put("configured", prefs.contains("host") && prefs.contains("port") && prefs.contains("endpoint_identity") && prefs.contains("token_id") && hasEncryptedSecret())
            .put("host", prefs.getString("host", null) ?: JSONObject.NULL)
            .put("port", if (prefs.contains("port")) prefs.getInt("port", 0) else JSONObject.NULL)
            .put("endpoint_identity", prefs.getString("endpoint_identity", null) ?: JSONObject.NULL)
            .put("token_id", prefs.getString("token_id", null) ?: JSONObject.NULL)
            .put("profile", prefs.getString("profile", null) ?: JSONObject.NULL)
            .put("secret_at_rest", hasEncryptedSecret())
    }

    private fun tailnetConfigureJson(args: JSONObject): JSONObject {
        val host = args.getString("host").also { require(it.isNotBlank()) { "tailnet host is required" } }
        val port = args.getInt("port").also { require(it in 1..65535) { "tailnet port must be from 1 through 65535" } }
        val endpointIdentity = args.getString("endpoint_identity").also { require(it.isNotBlank()) { "tailnet endpoint identity is required" } }
        val tokenId = args.getString("token_id").also { require(it.isNotBlank()) { "tailnet token id is required" } }
        val principal = args.optString("principal", "phosphor-mobil3-fortress").also { require(it.isNotBlank()) { "tailnet principal is required" } }
        val requiredProfile = requiredTailnetProfile()
        val profile = args.optString("profile", requiredProfile)
        require(profile == requiredProfile) { "tailnet profile must match this build: $requiredProfile" }
        commitTailnetPreferences("tailnet config persistence failed") { editor ->
            editor
                .putString("host", host)
                .putInt("port", port)
                .putString("endpoint_identity", endpointIdentity)
                .putString("token_id", tokenId)
                .putString("principal", principal)
                .putString("profile", profile)
        }
        restartTailnetIfConfigured()
        return tailnetStatusJson().put("restarted", true)
    }

    private fun tailnetClearJson(): JSONObject {
        PhosphorTailnetRuntimeBootstrap.shutdown()
        commitTailnetPreferences("tailnet clear persistence failed") { it.clear() }
        return JSONObject().put("configured", false).put("shutdown", true)
    }

    private fun tailnetStartJson(): JSONObject { restartTailnetIfConfigured(); return tailnetStatusJson().put("started", true) }
    private fun tailnetStopJson(): JSONObject { PhosphorTailnetRuntimeBootstrap.shutdown(); return tailnetStatusJson().put("stopped", true) }

    private fun restartTailnetIfConfigured() {
        val app = context?.applicationContext as? PhosphorApplication ?: return
        val config = loadTailnetConfig() ?: return
        PhosphorTailnetRuntimeBootstrap.install(config, TailnetNexusAuthorityAdapter.production(app.causalStore))
        PhosphorTailnetRuntimeBootstrap.start()
    }

    private fun loadTailnetConfig(): TailnetRuntimeConfig? {
        val prefs = tailnetPrefs()
        val secret = loadEncryptedSecret() ?: return null
        return TailnetRuntimeConfig(
            host = prefs.getString("host", null) ?: return null,
            port = if (prefs.contains("port")) prefs.getInt("port", 0) else return null,
            endpointIdentity = prefs.getString("endpoint_identity", null) ?: return null,
            tokenId = prefs.getString("token_id", null) ?: return null,
            tokenSecret = secret,
            principal = prefs.getString("principal", "phosphor-mobil3-fortress") ?: "phosphor-mobil3-fortress",
            profile = prefs.getString("profile", requiredTailnetProfile()) ?: requiredTailnetProfile(),
        )
    }

    private fun tailnetPrefs() = requireNotNull(context).getSharedPreferences("phosphor.pm3.tailnet", android.content.Context.MODE_PRIVATE)
    private fun hasEncryptedSecret(): Boolean = tailnetPrefs().contains("secret_iv") && tailnetPrefs().contains("secret_ciphertext")
    private fun saveEncryptedSecret(bytes: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(bytes)
        try {
            val iv = encodeBytes(cipher.iv)
            val encodedCiphertext = encodeBytes(ciphertext)
            commitTailnetPreferences("tailnet secret persistence failed") { editor ->
                editor.putString("secret_iv", iv).putString("secret_ciphertext", encodedCiphertext)
            }
        } finally {
            ciphertext.fill(0)
        }
        restartTailnetIfConfigured()
    }
    private fun loadEncryptedSecret(): String? {
        val prefs = tailnetPrefs()
        val iv = prefs.getString("secret_iv", null) ?: return null
        val ciphertext = prefs.getString("secret_ciphertext", null) ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(iv, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)))
        return cipher.doFinal(Base64.decode(ciphertext, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)).toString(Charsets.UTF_8)
    }

    private fun requiredTailnetProfile(): String = if (BuildConfig.DEBUG) "local_dev" else "nexus"

    /**
     * SharedPreferences may expose editor values in memory even when commit() reports false. Restore
     * the complete prior image on failure so a refused admin write cannot become process authority.
     */
    private fun commitTailnetPreferences(
        failureMessage: String,
        update: (SharedPreferences.Editor) -> SharedPreferences.Editor,
    ) {
        val prefs = tailnetPrefs()
        val before = prefs.all.toMap()
        if (update(prefs.edit()).commit()) return
        val rollback = prefs.edit().clear()
        before.forEach { (key, value) ->
            when (value) {
                is String -> rollback.putString(key, value)
                is Int -> rollback.putInt(key, value)
                is Long -> rollback.putLong(key, value)
                is Float -> rollback.putFloat(key, value)
                is Boolean -> rollback.putBoolean(key, value)
                is Set<*> -> @Suppress("UNCHECKED_CAST") rollback.putStringSet(key, value as Set<String>)
            }
        }
        rollback.commit()
        throw IllegalStateException(failureMessage)
    }
    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(SECRET_KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance("AES", "AndroidKeyStore").apply { init(android.security.keystore.KeyGenParameterSpec.Builder(SECRET_KEY_ALIAS, android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE).build()) }.generateKey()
    }

    private fun actionRunJson(store: PhosphorStateStore, args: JSONObject): JSONObject {
        val action = args.getString("action")
        require(action == "display.hud.set") { "only display.hud.set is supported" }
        val value = args.getString("value")
        val request = ActionRequest(
            principal = PrincipalId(PrincipalKind.HUMAN, LOCAL_HUMAN_PRINCIPAL_ID),
            idempotencyKey = args.optString("idempotency_key").ifBlank { "pm3-cli-${System.currentTimeMillis()}" },
            expectedRevision = args.optLong("expected_revision", store.snapshot.revision),
            reason = args.optString("reason").ifBlank { "pm3 local human CLI action-run" },
            requestedCapability = Capability.CONTROL_DISPLAY,
            transport = Transport.CLI,
            sessionId = null,
        )
        return when (val result = store.dispatch(SetDisplayHud(value), request, SystemClock.elapsedRealtime(), System.currentTimeMillis())) {
            is PhosphorDispatchResult.Accepted -> ackJson(result.acknowledgement).put("accepted", true).put("replayed", false)
            is PhosphorDispatchResult.Replayed -> ackJson(result.acknowledgement).put("accepted", true).put("replayed", true)
            is PhosphorDispatchResult.Refused -> JSONObject().put("accepted", false).put("refusal", refusalJson(result.refusal))
            is PhosphorDispatchResult.Failed -> JSONObject().put("accepted", false).put("refusal", refusalJson(result.refusal))
        }
    }

    private fun auditListJson(store: PhosphorStateStore, args: JSONObject): JSONObject {
        val limit = args.optInt("limit", 50).coerceIn(1, 512)
        val records = store.auditRecords.takeLast(limit)
        return JSONObject().put("records", JSONArray(records.map(::auditJson))).put("count", records.size)
    }

    private fun auditExportJson(store: PhosphorStateStore, args: JSONObject): JSONObject {
        val from = args.optLong("from_revision", 0L).coerceAtLeast(0L)
        val obs = store.observe()
        val records = obs.auditRecords
        val payload = JSONObject()
            .put("schema", "phosphor.pm3.audit-export/1")
            .put("from_revision", from)
            .put("snapshot_revision", obs.snapshot.revision)
            .put("records", JSONArray(records.map(::auditJson)))
        val canonical = payload.toString()
        return JSONObject()
            .put("transactional", true)
            .put("bytes_base64url", encodeBytes(canonical.toByteArray(Charsets.UTF_8)))
            .put("sha256", sha256(canonical.toByteArray(Charsets.UTF_8)))
            .put("record_count", records.size)
    }

    private fun trustKey(json: JSONObject): NexusTrustKey = when (json.getString("kind")) {
        "android" -> NexusTrustKey.android(
            packageName = json.getString("package_name"),
            signing = AndroidSigningEvidence.of(
                current = CertificateSha256(json.getString("current_certificate_sha256")),
                history = json.getJSONArray("signing_history_sha256").strings().map(::CertificateSha256),
            ),
            principalStableId = json.getString("principal_stable_id"),
            profile = nexusBuildProfile(json.optString("profile", "local_dev")),
        )
        "tailnet" -> NexusTrustKey.tailnet(
            nodePrincipal = json.getString("node_principal"),
            pinnedEndpointIdentity = PinnedEndpointIdentity(json.getString("pinned_endpoint_identity")),
            principalStableId = json.getString("principal_stable_id"),
            profile = nexusBuildProfile(json.optString("profile", "local_dev")),
        )
        else -> throw IllegalArgumentException("trust.kind must be android or tailnet")
    }

    private fun nexusBuildProfile(wire: String): NexusBuildProfile = NexusBuildProfile.entries.firstOrNull { it.wireName == wire || it.name == wire }
        ?: throw IllegalArgumentException("unknown Nexus profile: $wire")

    private fun capability(wire: String): Capability = Capability.entries.firstOrNull { it.wireName == wire }
        ?: throw IllegalArgumentException("unknown capability: $wire")

    private fun authorityImageJson(image: NexusAuthorityImage): JSONObject = JSONObject()
        .put("status", "loaded")
        .put("grants", JSONArray(image.grants.grants.map { grant ->
            JSONObject()
                .put("capability", grant.capability.wireName)
                .put("scope", grant.scope.wireName)
                .put("session_id", grant.sessionId?.value ?: JSONObject.NULL)
                .put("receipt_id", grant.receiptId)
                .put("trust", trustJson(grant.trustKey))
        }))
        .put("revoked_token_ids", JSONArray(image.revokedTokenIds.map { it.value }.sorted()))

    private fun trustJson(trust: NexusTrustKey): JSONObject = JSONObject()
        .put("kind", trust.kind.wireName)
        .put("subject", trust.subject)
        .put("principal_stable_id", trust.principalStableId)
        .put("profile", trust.profile.wireName)
        .put("protocol", trust.protocol)
        .put("current_certificate_sha256", trust.currentCertificate?.value ?: JSONObject.NULL)
        .put("signing_history_sha256", JSONArray(trust.signingHistory.map { it.value }))
        .put("pinned_endpoint_identity", trust.pinnedEndpointIdentity?.value ?: JSONObject.NULL)

    private fun auditJson(record: AuditRecord): JSONObject = JSONObject()
        .put("receipt_id", record.receiptId)
        .put("kind", record.kind.wireName)
        .put("wall_time_millis", record.wallTimeMillis)

    private fun ackJson(ack: ActionAcknowledgement): JSONObject = JSONObject()
        .put("receipt_id", ack.receiptId)
        .put("action_type", ack.actionType.wireName)
        .put("changed", ack.changed)
        .put("revision", ack.revision)
        .put("sequence", ack.sequence)
        .put("effective_value", ack.effectiveValue?.toString() ?: JSONObject.NULL)

    private fun refusalJson(refusal: Refusal): JSONObject = JSONObject()
        .put("code", refusal.code.wireName)
        .put("message", refusal.fix)
        .put("revision", refusal.currentRevision ?: JSONObject.NULL)

    private fun decodeRequest(extras: Bundle?): JSONObject {
        val encoded = extras?.getString(KEY_REQUEST) ?: throw IllegalArgumentException("missing $KEY_REQUEST")
        val bytes = try { Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING) } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("request is not valid base64url")
        }
        require(bytes.size <= MAX_REQUEST_BYTES) { "request exceeds $MAX_REQUEST_BYTES bytes" }
        return JSONObject(bytes.toString(Charsets.UTF_8))
    }

    private fun enforceCaller() {
        val uid = Binder.getCallingUid()
        require(uid == Process.SHELL_UID || uid == Process.ROOT_UID) { "pm3 provider is shell/root only" }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        const val METHOD = "pm3"
        const val KEY_REQUEST = "request"
        const val KEY_RESPONSE = "response"
        const val PROTOCOL_VERSION = 3
        private const val MAX_REQUEST_BYTES = 64 * 1024
        private const val MAX_SECRET_BYTES = 4096
        private const val SECRET_KEY_ALIAS = "phosphor.pm3.tailnet-secret.v1"
        fun encodeJson(json: JSONObject): String = encodeBytes(json.toString().toByteArray(Charsets.UTF_8))
        private fun encodeBytes(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}

private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
