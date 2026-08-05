package dev.phosphor.mobil3

import android.content.Context
import android.content.SharedPreferences

data class RemoteHost(
    val label: String,
    val host: String,
    val port: Int,
)

/** A narrow persistence seam keeps host-list rules testable without an Android runtime. */
interface HostPrefs {
    fun read(key: String): String?
    fun write(values: Map<String, String>): Boolean
}

sealed interface RemoteHostOutcome {
    data class Saved(val hosts: List<RemoteHost>) : RemoteHostOutcome
    data class Refused(val message: String, val fix: String) : RemoteHostOutcome
    data class Failed(val message: String, val fix: String) : RemoteHostOutcome
}

/**
 * Durable relay endpoints owned by the user.
 *
 * The seed is only a bootstrap input. A separate marker is committed with the first image so
 * deleting a compiled endpoint remains a durable user decision. Mutations publish their new
 * in-memory snapshot only after the complete preference edit commits.
 */
class RemoteHostStore(
    private val preferences: HostPrefs,
    seed: String,
) {
    constructor(context: Context, seed: String) : this(
        SharedPreferencesHostPrefs(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
        ),
        seed,
    )

    @Volatile
    private var snapshot: List<RemoteHost> = emptyList()

    val initialization: RemoteHostOutcome

    init {
        val alreadySeeded = preferences.read(KEY_SEEDED) == TRUE
        val initialHosts = if (alreadySeeded) {
            parseSeed(preferences.read(KEY_HOSTS).orEmpty())
        } else {
            parseSeed(seed)
        }
        initialization = if (alreadySeeded) {
            snapshot = initialHosts
            RemoteHostOutcome.Saved(initialHosts)
        } else if (commit(initialHosts)) {
            snapshot = initialHosts
            RemoteHostOutcome.Saved(initialHosts)
        } else {
            RemoteHostOutcome.Failed(
                message = "Relay hosts could not be initialized.",
                fix = "Retry after Android storage is available. No relay host state was published.",
            )
        }
    }

    fun hosts(): List<RemoteHost> = snapshot

    @Synchronized
    fun add(label: String, host: String, port: Int): RemoteHostOutcome {
        val candidate = when (val validation = validate(label, host, port, snapshot)) {
            is Validation.Valid -> validation.host
            is Validation.Invalid -> return validation.refusal
        }
        return save(snapshot + candidate)
    }

    @Synchronized
    fun update(
        existingHost: String,
        existingPort: Int,
        label: String,
        host: String,
        port: Int,
    ): RemoteHostOutcome {
        val index = snapshot.indexOfFirst { it.host == existingHost && it.port == existingPort }
        if (index < 0) {
            return RemoteHostOutcome.Refused(
                message = "The relay host to update no longer exists.",
                fix = "Refresh the host list, then edit an endpoint that is still present.",
            )
        }
        val otherHosts = snapshot.filterIndexed { candidateIndex, _ -> candidateIndex != index }
        val candidate = when (val validation = validate(label, host, port, otherHosts)) {
            is Validation.Valid -> validation.host
            is Validation.Invalid -> return validation.refusal
        }
        val updated = snapshot.toMutableList().apply { set(index, candidate) }.toList()
        return save(updated)
    }

    @Synchronized
    fun remove(host: String, port: Int): RemoteHostOutcome {
        val index = snapshot.indexOfFirst { it.host == host && it.port == port }
        if (index < 0) {
            return RemoteHostOutcome.Refused(
                message = "The relay host to remove no longer exists.",
                fix = "Refresh the host list, then remove an endpoint that is still present.",
            )
        }
        return save(snapshot.filterIndexed { candidateIndex, _ -> candidateIndex != index })
    }

    /** Invalid seed records are inert, which keeps an empty Play seed a valid first-run image. */
    fun parseSeed(seed: String): List<RemoteHost> {
        if (seed.isBlank()) return emptyList()
        val parsed = mutableListOf<RemoteHost>()
        seed.split(',').forEach { record ->
            val fields = record.split(':')
            if (fields.size != 3) return@forEach
            val port = fields[2].toIntOrNull() ?: return@forEach
            when (val validation = validate(fields[0], fields[1], port, parsed)) {
                is Validation.Valid -> parsed += validation.host
                is Validation.Invalid -> Unit
            }
        }
        return parsed.toList()
    }

    private fun save(candidate: List<RemoteHost>): RemoteHostOutcome {
        val durable = candidate.toList()
        if (!commit(durable)) {
            return RemoteHostOutcome.Failed(
                message = "Relay host changes could not be saved.",
                fix = "Retry after Android storage is available. The previous relay host list is still active.",
            )
        }
        snapshot = durable
        return RemoteHostOutcome.Saved(durable)
    }

    private fun commit(hosts: List<RemoteHost>): Boolean = preferences.write(
        mapOf(
            KEY_HOSTS to serialize(hosts),
            KEY_SEEDED to TRUE,
        ),
    )

    private fun serialize(hosts: List<RemoteHost>): String = hosts.joinToString(",") { host ->
        "${host.label}:${host.host}:${host.port}"
    }

    private fun validate(
        rawLabel: String,
        rawHost: String,
        port: Int,
        existing: List<RemoteHost>,
    ): Validation {
        // Trim before every other rule. A label typed as "  Studio  " and one typed as
        // "Studio" are the same endpoint to a human, so storing the padding would create
        // near-duplicate rows that look identical in the sheet. Hosts are trimmed for the
        // same reason, and because a stray space would make the address fail to resolve.
        val label = rawLabel.trim()
        val host = rawHost.trim().lowercase()
        if (label.isBlank()) {
            return invalid(
                "The relay label is blank.",
                "Enter a label with 1 to 32 characters.",
            )
        }
        if (label.length > MAX_LABEL_LENGTH) {
            return invalid(
                "The relay label is longer than 32 characters.",
                "Shorten the label to 32 characters or fewer.",
            )
        }
        if (label.any { it == ':' || it == ',' }) {
            return invalid(
                "The relay label contains a reserved separator.",
                "Remove ':' and ',' from the label. These characters separate stored host fields.",
            )
        }
        if (host.isBlank()) {
            return invalid(
                "The relay host is blank.",
                "Enter a hostname, IPv4 address, or tailnet MagicDNS name.",
            )
        }
        if (host.any(Char::isWhitespace)) {
            return invalid(
                "The relay host contains whitespace.",
                "Remove all whitespace from inside the hostname or address.",
            )
        }
        if (':' in host) {
            return invalid(
                "The relay host contains ':'.",
                "Use a hostname, IPv4 address, or MagicDNS name. Plain IPv6 literals are not supported because ':' separates stored fields.",
            )
        }
        if (',' in host) {
            return invalid(
                "The relay host contains ','.",
                "Remove ',' from the hostname or address. This character separates stored hosts.",
            )
        }
        if (!isTailscaleHost(host)) {
            return invalid(
                "The relay host is outside the supported Tailscale address space.",
                "Use a MagicDNS name, a name ending in .ts.net, or a 100.64.0.0/10 Tailscale IPv4 address.",
            )
        }
        if (port !in MIN_PORT..MAX_PORT) {
            return invalid(
                "The relay port is outside the valid TCP range.",
                "Enter a port from 1 through 65535.",
            )
        }
        val duplicate = existing.firstOrNull { it.host == host && it.port == port }
        if (duplicate != null) {
            return invalid(
                "The relay endpoint is already saved as '${duplicate.label}'.",
                "Edit '${duplicate.label}' or enter a different host and port.",
            )
        }
        return Validation.Valid(RemoteHost(label, host, port))
    }

    private fun invalid(message: String, fix: String): Validation.Invalid =
        Validation.Invalid(RemoteHostOutcome.Refused(message, fix))

    private fun isTailscaleHost(host: String): Boolean {
        if (host.endsWith(".ts.net") || host.endsWith(".tailnet")) return isDnsName(host)
        if ('.' !in host) return isDnsLabel(host)
        val rawOctets = host.split('.')
        if (rawOctets.size != 4 || rawOctets.any { it.isEmpty() || it.any { char -> !char.isDigit() } }) {
            return false
        }
        if (rawOctets.any { it.length > 1 && it.startsWith('0') }) return false
        val octets = rawOctets.map { it.toIntOrNull() ?: return false }
        return octets.size == 4 &&
            octets.all { it in 0..255 } &&
            octets[0] == 100 &&
            octets[1] in 64..127
    }

    private fun isDnsName(host: String): Boolean =
        host.length <= MAX_DNS_NAME_LENGTH && host.split('.').all(::isDnsLabel)

    private fun isDnsLabel(label: String): Boolean = DNS_LABEL.matches(label)

    private sealed interface Validation {
        data class Valid(val host: RemoteHost) : Validation
        data class Invalid(val refusal: RemoteHostOutcome.Refused) : Validation
    }

    companion object {
        const val PREFERENCES_NAME = "remote_hosts"
        private const val KEY_HOSTS = "hosts"
        private const val KEY_SEEDED = "seeded"
        private const val TRUE = "true"
        private const val MAX_LABEL_LENGTH = 32
        private const val MIN_PORT = 1
        private const val MAX_PORT = 65535
        private const val MAX_DNS_NAME_LENGTH = 253
        private val DNS_LABEL = Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")
    }
}

private class SharedPreferencesHostPrefs(
    private val preferences: SharedPreferences,
) : HostPrefs {
    override fun read(key: String): String? = preferences.getString(key, null)

    override fun write(values: Map<String, String>): Boolean {
        val editor = preferences.edit()
        values.forEach { (key, value) -> editor.putString(key, value) }
        return editor.commit()
    }
}
