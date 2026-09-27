package dev.phosphor.mobil3.ui

import kotlin.math.ln
import kotlin.math.pow

/**
 * What a tap means on the LIGHT surface (design/REDESIGN.md §6). Pure: the sheet
 * publishes the returned snapshot through the existing LightSettings owner.
 */
internal object LightChoices {
    enum class Cycle { OFF, TIMER, TRACK }

    fun cycle(l: LightSettings): Cycle = when {
        !l.generatedAuto && l.selected.size < 2 -> Cycle.OFF
        l.perTrack -> Cycle.TRACK
        else -> Cycle.TIMER
    }

    /** The CYCLE group exists when there is something to cycle. */
    fun cycleVisible(l: LightSettings): Boolean = l.slots.size >= 2 || l.generatedAuto

    /** Cycle choices on offer. Generated colour always moves, so it has no "off". */
    fun cycleOptions(l: LightSettings): List<Cycle> =
        if (l.generatedAuto) listOf(Cycle.TIMER, Cycle.TRACK) else Cycle.entries

    fun presetWorn(l: LightSettings, index: Int, temporary: Boolean): Boolean =
        !temporary && !l.generatedAuto && l.selectedMask == 0 && l.preset == index

    fun savedWorn(l: LightSettings, index: Int): Boolean = !l.generatedAuto && index in l.selected

    /** Cycle off: wear this colour alone. Cycling: add or remove it; the ring keeps two. */
    fun tapSaved(l: LightSettings, index: Int): LightSettings {
        require(index in l.slots.indices)
        if (l.generatedAuto || cycle(l) == Cycle.OFF) {
            return l.copy(selectedMask = 1 shl index, generatedAuto = false)
        }
        val next = l.selectedMask xor (1 shl index)
        return if (Integer.bitCount(next) < 2) l else l.copy(selectedMask = next)
    }

    fun setCycle(l: LightSettings, mode: Cycle): LightSettings = when (mode) {
        Cycle.OFF -> {
            val keep = l.selected.firstOrNull()
            l.copy(selectedMask = if (keep == null) 0 else 1 shl keep, generatedAuto = false)
        }
        Cycle.TIMER, Cycle.TRACK -> {
            val ring = if (l.generatedAuto || l.selected.size >= 2) l.selectedMask
                else (1 shl l.slots.size) - 1
            l.copy(selectedMask = ring, perTrack = mode == Cycle.TRACK)
        }
    }

    /** `+` saves the colour the beam wears now and wears it (or joins the ring). */
    fun addCurrent(l: LightSettings): LightSettings {
        require(l.slots.size < 6)
        val worn = l.selected.singleOrNull()?.let { l.slots[it] }
            ?: BeamColors[l.preset].color.let { LightRgb(it.red, it.green, it.blue) }
        val added = l.add(worn)
        val newBit = 1 shl l.slots.size
        return if (cycle(l) == Cycle.OFF) added.copy(selectedMask = newBit, generatedAuto = false) else added
    }
}

/** Seconds on a log rail: fine steps near a second, still reaching a minute. */
internal object LightTime {
    const val MIN = 0.1f
    const val MAX = 60f
    private val span = ln(MAX / MIN)
    fun toSlider(seconds: Float): Float = (ln(seconds.coerceIn(MIN, MAX) / MIN) / span).coerceIn(0f, 1f)
    fun fromSlider(position: Float): Float =
        (MIN * (MAX / MIN).pow(position.coerceIn(0f, 1f))).coerceIn(MIN, MAX).let {
            if (it < 10f) (it * 10f).toInt() / 10f else it.toInt().toFloat()
        }.coerceIn(MIN, MAX)
    fun words(seconds: Float): String =
        if (seconds < 10f) String.format(java.util.Locale.ROOT, "%.1f s", seconds)
        else String.format(java.util.Locale.ROOT, "%.0f s", seconds)
}

/** A plain colour name for TalkBack, so a swatch never speaks as "button". */
internal object ColorWords {
    fun name(red: Float, green: Float, blue: Float): String {
        val max = maxOf(red, green, blue)
        val min = minOf(red, green, blue)
        val v = max
        val s = if (max <= 0f) 0f else (max - min) / max
        val h = when {
            max == min -> 0f
            max == red -> 60f * (((green - blue) / (max - min)).mod(6f))
            max == green -> 60f * (((blue - red) / (max - min)) + 2f)
            else -> 60f * (((red - green) / (max - min)) + 4f)
        }
        if (v < 0.15f) return "black"
        if (s < 0.15f) return if (v > 0.85f) "white" else "grey"
        val hue = when {
            h < 15f || h >= 345f -> "red"
            h < 40f -> "orange"
            h < 70f -> "yellow"
            h < 160f -> "green"
            h < 200f -> "cyan"
            h < 255f -> "blue"
            h < 290f -> "violet"
            else -> "pink"
        }
        return when {
            v < 0.5f -> "deep $hue"
            s < 0.45f -> "pale $hue"
            else -> hue
        }
    }
}
