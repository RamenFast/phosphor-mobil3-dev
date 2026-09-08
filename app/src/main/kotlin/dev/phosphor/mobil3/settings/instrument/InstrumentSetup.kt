package dev.phosphor.mobil3.settings.instrument

import dev.phosphor.mobil3.ui.LightCycleGuard
import dev.phosphor.mobil3.ui.LightRgb
import dev.phosphor.mobil3.ui.LightSettings
import java.util.Collections

class InstrumentPresetException(
    val error: String,
    override val message: String,
    val fix: String,
) : IllegalArgumentException(message)

internal fun presetRequire(condition: Boolean, error: String, message: String,
    fix: String = "Correct the instrument preset and try again") {
    if (!condition) throw InstrumentPresetException(error, message, fix)
}

/** Authored local tuning only. Gain is the saved manual value, never measured or mirrored gain. */
class InstrumentSetup(
    val mode: Int,
    val randomModeArmed: Boolean,
    randomBanModes: List<Int>,
    val geomFx: Int,
    val geomAmount: Float,
    val gain: Float,
    val autoGain: Boolean,
    val focus: Float,
    val beamEnergy: Float,
    val glow: Float,
    val beamRandomArmed: Boolean,
    val beamRandomMin: Float,
    val beamRandomMax: Float,
    val glowRandomArmed: Boolean,
    val glowRandomMin: Float,
    val glowRandomMax: Float,
    val grid: Boolean,
    val gridData: Boolean,
    val oversample: Int,
    val light: LightSettings,
) {
    val randomBanModes: List<Int> = Collections.unmodifiableList(randomBanModes.toList())

    init {
        presetRequire(mode in 0..10, "invalid_setup", "Mode must be within 0..10")
        presetRequire(this.randomBanModes == this.randomBanModes.distinct().sorted() &&
            this.randomBanModes.all { it in 0..10 }, "invalid_setup", "Banned modes must be unique sorted integers within 0..10")
        presetRequire(!randomModeArmed || this.randomBanModes.size <= 9,
            "invalid_setup", "Random mode needs at least two eligible modes")
        presetRequire(geomFx in 0..4, "invalid_setup", "Geometry effect must be within 0..4")
        finiteRange("geom_amount", geomAmount, 0f, 1f)
        finiteRange("gain", gain, 0.1f, 7f)
        finiteRange("focus", focus, 0.3f, 3f)
        finiteRange("beam_energy", beamEnergy, 1f, 30f)
        finiteRange("glow", glow, 0f, 0.98f)
        finiteRange("beam_random_min", beamRandomMin, 1f, 30f)
        finiteRange("beam_random_max", beamRandomMax, beamRandomMin, 30f)
        finiteRange("glow_random_min", glowRandomMin, 0f, 0.98f)
        finiteRange("glow_random_max", glowRandomMax, glowRandomMin, 0.98f)
        presetRequire(oversample in setOf(1, 2, 4), "invalid_setup", "DSP reconstruction must be 1, 2 or 4")
    }

    fun copy(mode: Int = this.mode, randomModeArmed: Boolean = this.randomModeArmed,
        randomBanModes: List<Int> = this.randomBanModes, geomFx: Int = this.geomFx,
        geomAmount: Float = this.geomAmount, gain: Float = this.gain, autoGain: Boolean = this.autoGain,
        focus: Float = this.focus, beamEnergy: Float = this.beamEnergy, glow: Float = this.glow,
        beamRandomArmed: Boolean = this.beamRandomArmed, beamRandomMin: Float = this.beamRandomMin,
        beamRandomMax: Float = this.beamRandomMax, glowRandomArmed: Boolean = this.glowRandomArmed,
        glowRandomMin: Float = this.glowRandomMin, glowRandomMax: Float = this.glowRandomMax,
        grid: Boolean = this.grid, gridData: Boolean = this.gridData, oversample: Int = this.oversample,
        light: LightSettings = this.light) = InstrumentSetup(mode, randomModeArmed, randomBanModes,
            geomFx, geomAmount, gain, autoGain, focus, beamEnergy, glow, beamRandomArmed,
            beamRandomMin, beamRandomMax, glowRandomArmed, glowRandomMin, glowRandomMax,
            grid, gridData, oversample, light)

    data class Guarded(val safe: InstrumentSetup, val pending: InstrumentSetup?)

    /** Application-only seam. Decoding and collection operations never invoke or acknowledge it. */
    fun guardLight(acknowledged: Boolean): Guarded {
        val result = LightCycleGuard.evaluate(light, acknowledged)
        return Guarded(copy(light = result.safe), if (result.pending != null) this else null)
    }

    private fun values(): List<Any> = listOf(mode, randomModeArmed, randomBanModes, geomFx,
        geomAmount, gain, autoGain, focus, beamEnergy, glow, beamRandomArmed, beamRandomMin,
        beamRandomMax, glowRandomArmed, glowRandomMin, glowRandomMax, grid, gridData, oversample, light)
    override fun equals(other: Any?): Boolean = other is InstrumentSetup && values() == other.values()
    override fun hashCode(): Int = values().hashCode()
    override fun toString(): String = "InstrumentSetup(${values()})"

    private fun finiteRange(name: String, value: Float, min: Float, max: Float) {
        presetRequire(value.isFinite() && value in min..max, "invalid_setup", "$name must be finite within $min..$max")
    }
}

object CuratedInstrumentPresets {
    data class Entry(val name: String, val purpose: String, val setup: InstrumentSetup)

    private fun setup(mode: Int, preset: Int, beam: Float, glow: Float, grid: Boolean,
        light: LightSettings = LightSettings(preset = preset)) = InstrumentSetup(
        mode = mode, randomModeArmed = false, randomBanModes = emptyList(), geomFx = 0,
        geomAmount = 0.6f, gain = 1.8332275f, autoGain = true, focus = 0.3f,
        beamEnergy = beam, glow = glow, beamRandomArmed = false, beamRandomMin = 6f,
        beamRandomMax = 20f, glowRandomArmed = false, glowRandomMin = 0.30f,
        glowRandomMax = 0.90f, grid = grid, gridData = false, oversample = 1, light = light)

    val cleanXy = Entry("Clean XY", "Clear left/right geometry with short trails",
        setup(0, 0, 8f, 0.20f, true))
    val spectralBench = Entry("Spectral bench", "Separate spectral motion from trails, without calibration claims",
        setup(8, 2, 6f, 0.15f, true))
    val ambient = Entry("Ambient", "Slow color interpolation and longer visual persistence",
        setup(7, 0, 6f, 0.70f, false, LightSettings(
            slots = listOf(LightRgb(0.42f, 1f, 0.55f), LightRgb(0.35f, 0.75f, 1f)),
            selectedMask = 3, preset = 0, seconds = 10f)))
    val all: List<Entry> = Collections.unmodifiableList(listOf(cleanXy, spectralBench, ambient))
}
