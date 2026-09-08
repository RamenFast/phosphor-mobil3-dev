package dev.phosphor.mobil3.ui

/** Portable light state. Runtime rolls, clock, RNG and acknowledgement are not settings. */
data class LightRgb(val red: Float, val green: Float, val blue: Float) {
    init { require(listOf(red, green, blue).all { it.isFinite() && it in 0f..1f }) { "RGB must contain finite components in 0..1" } }
    fun components() = listOf(red, green, blue)
}

class LightSettings(
    slots: List<LightRgb> = emptyList(),
    val selectedMask: Int = 0,
    val preset: Int = 7,
    val seconds: Float = 3f,
    val perTrack: Boolean = false,
    val generatedAuto: Boolean = false,
    val shuffle: Boolean = false,
    val randomInterval: Boolean = false,
    val intervalMin: Float = 3f,
    val intervalMax: Float = 6f,
) {
    val slots: List<LightRgb> = java.util.Collections.unmodifiableList(slots.toList())
    init {
        require(slots.size <= 6) { "Keep at most six saved colors" }
        require(selectedMask >= 0 && selectedMask ushr slots.size == 0) { "Select only existing saved slots" }
        require(preset in 0..8) { "Choose an existing preset" }
        require(seconds.isFinite() && seconds in 0.1f..60f) { "Set LEG within 0.1..60 seconds" }
        require(intervalMin.isFinite() && intervalMax.isFinite() && intervalMin in 0.1f..60f && intervalMax in intervalMin..60f) { "Set interval minimum <= maximum within 0.1..60 seconds" }
    }
    fun copy(slots: List<LightRgb> = this.slots, selectedMask: Int = this.selectedMask,
        preset: Int = this.preset, seconds: Float = this.seconds, perTrack: Boolean = this.perTrack,
        generatedAuto: Boolean = this.generatedAuto, shuffle: Boolean = this.shuffle,
        randomInterval: Boolean = this.randomInterval, intervalMin: Float = this.intervalMin,
        intervalMax: Float = this.intervalMax) = LightSettings(slots, selectedMask, preset, seconds,
            perTrack, generatedAuto, shuffle, randomInterval, intervalMin, intervalMax)
    override fun equals(other: Any?): Boolean = other is LightSettings && values() == other.values()
    override fun hashCode(): Int = values().hashCode()
    override fun toString(): String = "LightSettings(${values()})"
    val selected get()
 = slots.indices.filter { selectedMask and (1 shl it) != 0 }
    fun toggle(index: Int): LightSettings {
        require(index in slots.indices)
        return copy(selectedMask = selectedMask xor (1 shl index))
    }
    fun add(rgb: LightRgb): LightSettings = copy(slots = slots + rgb, selectedMask = selectedMask or (1 shl slots.size))
    fun edit(index: Int, rgb: LightRgb): LightSettings {
        require(index in slots.indices)
        return copy(slots = slots.toMutableList().also { it[index] = rgb }.toList())
    }
    fun delete(index: Int): LightSettings {
        require(index in slots.indices)
        val lower = selectedMask and ((1 shl index) - 1)
        val upper = (selectedMask ushr (index + 1)) shl index
        return copy(slots = slots.filterIndexed { i, _ -> i != index }, selectedMask = lower or upper)
    }
    fun preset(index: Int) = copy(preset = index, selectedMask = 0, generatedAuto = false)
    fun values(): Map<String, Any> = linkedMapOf<String, Any>(
        "beam" to preset, "custom_slot_count" to slots.size, "custom_selected_mask" to selectedMask,
        "custom_rgb" to slots.flatMap { it.components() }.joinToString(","),
        "cycle_seconds" to seconds, "cycle_per_track" to perTrack,
        "color_generated_auto" to generatedAuto, "color_shuffle" to shuffle,
        "cycle_random_interval" to randomInterval, "cycle_interval_min" to intervalMin,
        "cycle_interval_max" to intervalMax,
    )
    companion object {
        val keys = setOf("beam", "custom_slot_count", "custom_selected_mask", "custom_rgb", "custom_count",
            "cycle_seconds", "cycle_per_track", "color_generated_auto", "color_shuffle", "cycle_random_interval",
            "cycle_interval_min", "cycle_interval_max")
        fun read(values: Map<String, *>): LightSettings = merge(LightSettings(), values, !values.containsKey("custom_slot_count"))
        fun merge(current: LightSettings, values: Map<String, *>, legacy: Boolean): LightSettings {
            fun int(key: String, fallback: Int) = if (key in values) values[key] as? Int ?: error("$key must be an Int") else fallback
            fun float(key: String, fallback: Float) = if (key in values) values[key] as? Float ?: error("$key must be a Float") else fallback
            fun bool(key: String, fallback: Boolean) = if (key in values) values[key] as? Boolean ?: error("$key must be Boolean") else fallback
            val rgb = if ("custom_rgb" in values) {
                val text = values["custom_rgb"] as? String ?: error("custom_rgb must be a scalar string")
                val parts = if (text.isEmpty()) emptyList() else text.split(',').map { it.toFloatOrNull() ?: error("RGB contains a nonnumeric component") }
                require(parts.size % 3 == 0 && parts.size <= 18 && (!legacy || parts.size == 9)) { "Supply all RGB triples for the source schema" }
                parts.chunked(3).map { LightRgb(it[0], it[1], it[2]) }
            } else current.slots
            val count = if (legacy) rgb.size else int("custom_slot_count", current.slots.size)
            require(count == rgb.size) { "Supply exactly three RGB components per saved slot" }
            val mask = if (legacy && "custom_count" in values) {
                val n = int("custom_count", 0)
                require(n in 0..3 && n <= count) { "Legacy count needs valid saved RGB. Include the RGB bank or reduce count" }
                (1 shl n) - 1
            } else int("custom_selected_mask", current.selectedMask)
            return LightSettings(rgb, mask, int("beam", current.preset), float("cycle_seconds", current.seconds),
                bool("cycle_per_track", current.perTrack), bool("color_generated_auto", current.generatedAuto),
                bool("color_shuffle", current.shuffle), bool("cycle_random_interval", current.randomInterval),
                float("cycle_interval_min", current.intervalMin), float("cycle_interval_max", current.intervalMax))
        }
    }
}

object LightCycleGuard {
    data class Result(val safe: LightSettings, val pending: LightSettings? = null)
    fun evaluate(requested: LightSettings, acknowledged: Boolean): Result {
        val minimum = if (requested.randomInterval) requested.intervalMin else requested.seconds
        if (acknowledged || requested.perTrack || minimum >= 1f) return Result(requested)
        return Result(requested.copy(seconds = requested.seconds.coerceAtLeast(1f),
            intervalMin = requested.intervalMin.coerceAtLeast(1f), intervalMax = requested.intervalMax.coerceAtLeast(1f)), requested)
    }
}

/** Explicit preset selection retains its existing remote-theme callback only after acceptance. */
internal fun applyPresetLight(current: LightSettings, index: Int,
    publish: (LightSettings) -> Boolean, afterAccepted: () -> Unit) {
    if (publish(current.preset(index))) afterAccepted()
}
