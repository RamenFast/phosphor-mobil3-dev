package dev.phosphor.mobil3

/**
 * In-memory [HostPrefs] for tests.
 *
 * There is no Robolectric or androidx.test in this project, so a real SharedPreferences
 * is unavailable on the host. This fake stands in for it and can be told to fail writes,
 * which is how the "storage failed" paths get proven rather than assumed.
 */
class InMemoryHostPrefs(seed: Map<String, String> = emptyMap()) : HostPrefs {
    private val values = linkedMapOf<String, String>().apply { putAll(seed) }

    /** Fails one write, then clears itself. */
    var failNextWrite: Boolean = false

    /** Fails every write until set back to false. */
    var failWrites: Boolean = false

    override fun read(key: String): String? = values[key]

    override fun write(values: Map<String, String>): Boolean {
        if (failWrites) return false
        if (failNextWrite) {
            failNextWrite = false
            return false
        }
        this.values.putAll(values)
        return true
    }
}
