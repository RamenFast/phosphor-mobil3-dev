package dev.phosphor.mobil3

/** Portable policy uses framework type numbers, not Android objects or device names as authority. */
data class MicrophoneChoice(val id: Int, val type: Int, val name: String, val address: String = "",
    val displayTag: String = "") {
    val bluetooth: Boolean get() = type == 7 || type == 26
    val key: String get() = "$type\n$address\n$name"
    val label: String get() {
        val kind = when (type) { 15 -> "built-in"; 3 -> "wired"; 7 -> "Bluetooth SCO"; 26 -> "Bluetooth LE"; else -> "USB" }
        val base = "$name · $kind"
        return if (displayTag.isEmpty()) base else "$base ($displayTag)"
    }
}
internal object MicrophoneRoutePolicy {
    const val SELECTED = "microphone_selected_input"
    const val BLUETOOTH_ACK = "microphone_bluetooth_explained"
    fun supported(source: Boolean, type: Int, api: Int): Boolean = source &&
        (type in setOf(15, 3, 11, 12, 22, 7) || api >= 31 && type == 26)
    fun select(choices: List<MicrophoneChoice>, key: String?): MicrophoneChoice? =
        if (key.isNullOrEmpty()) choices.filter { it.type == 15 }.minByOrNull { it.id }
        else choices.filter { it.key == key }.singleOrNull()
    fun disambiguate(choices: List<MicrophoneChoice>): List<MicrophoneChoice> {
        val counts = choices.groupingBy { it.label }.eachCount()
        return choices.map { choice ->
            if ((counts[choice.label] ?: 0) > 1) choice.copy(displayTag = choice.id.toString())
            else choice
        }
    }
    fun routed(selected: Int, actual: List<Int>): Boolean = actual.isNotEmpty() && actual.all { it == selected }
    data class Format(val rate: Int, val channels: Int, val floating: Boolean)
    fun candidates(rates: IntArray, channels: IntArray, encodings: IntArray): List<Format> {
        val rr = (listOf(48_000, 44_100, 16_000, 8_000) + rates.toList()).distinct()
            .filter { it in 8_000..192_000 && (rates.isEmpty() || it in rates) }
        val cc = listOf(2, 1).filter { channels.isEmpty() || it in channels }
        val ee = listOf(true, false).filter { encodings.isEmpty() || (if (it) 4 else 2) in encodings }
        // Mono PCM16 gets an early trial, even when the first stereo tuple fails.
        return rr.flatMap { rate -> listOf(false, true).flatMap { monoFirst ->
            cc.filter { (it == 1) == !monoFirst }.flatMap { count -> ee.map { Format(rate, count, it) } }
        } }.take(12)
    }
}
