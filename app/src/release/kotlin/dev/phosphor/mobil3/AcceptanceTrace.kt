package dev.phosphor.mobil3

internal object AcceptanceTrace {
    @Suppress("UNUSED_PARAMETER")
    inline fun record(event: String, fields: () -> String) = Unit
}
