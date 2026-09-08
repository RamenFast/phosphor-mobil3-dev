package dev.phosphor.mobil3.settings.instrument

/** One collection value. Failed Android commits can still mutate the in-memory map. */
class InstrumentPresetStore(private val read: () -> String?, private val write: (String?) -> Boolean) {
    private var bytes: String? = null
    private var loaded = false
    private var uncertain = false
    var collection: InstrumentPresetCollection? = null
        private set
    var error = ""
        private set

    fun load(): InstrumentPresetCollection? {
        if (uncertain) return null
        return try {
            val raw = read()
            val decoded = if (raw == null) InstrumentPresetCollection.empty() else InstrumentPresetCodec.decode(raw)
            bytes = raw
            loaded = true
            collection = decoded
            error = ""
            decoded
        } catch (failure: Exception) {
            collection = null
            loaded = false
            error = "Saved preset bytes are unreadable and remain preserved. Recover the original collection before saving. ${failure.message.orEmpty()}"
            null
        }
    }

    fun change(transform: (InstrumentPresetCollection) -> InstrumentPresetCollection): Boolean {
        if (!loaded || uncertain) {
            if (error.isEmpty()) error = "Preset storage is unavailable. Reopen Phosphor before saving."
            return false
        }
        return try {
            val base = collection ?: return false
            check(read() == bytes) { "Preset storage changed. Reopen the browser and repeat the edit." }
            val proposed = transform(base)
            val encoded = InstrumentPresetCodec.encode(proposed)
            val prior = bytes
            val saved = try { write(encoded) } catch (_: Exception) { false }
            if (!saved) {
                val restored = try { write(prior) && read() == prior } catch (_: Exception) { false }
                uncertain = !restored
                if (uncertain) collection = null
                error = if (restored) "Saving presets failed. Previous bytes restored. Free storage and retry."
                    else "Saving presets and rollback failed. Stored bytes are uncertain. Recover storage before another save."
                false
            } else {
                bytes = encoded
                collection = proposed
                error = ""
                true
            }
        } catch (failure: Exception) {
            error = when (failure) {
                is InstrumentPresetException -> "${failure.message}. ${failure.fix}."
                else -> failure.message ?: "Preset edit failed. Reopen the browser and retry."
            }
            false
        }
    }
}

/** Reads one byte beyond the limit to reject oversize without loading the rest. */
object InstrumentDocuments {
    fun read(input: java.io.InputStream): InstrumentPresetCollection {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var remaining = InstrumentPresetCodec.MAX_BYTES + 1
        while (remaining > 0) {
            val count = input.read(buffer, 0, minOf(buffer.size, remaining))
            if (count < 0) break
            if (count == 0) {
                val one = input.read()
                if (one < 0) break
                output.write(one)
                remaining--
            } else {
                output.write(buffer, 0, count)
                remaining -= count
            }
        }
        return InstrumentPresetCodec.decode(output.toByteArray())
    }
}
