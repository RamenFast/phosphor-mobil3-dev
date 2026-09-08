package dev.phosphor.mobil3.settings.instrument

/** The exact preference allowlist for a committed instrument, separate from app archives. */
fun InstrumentSetup.preferenceValues(): Map<String, Any> = mapOf(
    "mode" to mode, "random_mode_armed" to randomModeArmed,
    "random_ban_modes" to randomBanModes.joinToString(","),
    "geom_fx" to geomFx, "geom_amount" to geomAmount,
    "gain" to gain, "auto_gain" to autoGain, "focus" to focus,
    "beam_energy" to beamEnergy, "glow" to glow,
    "beam_random_armed" to beamRandomArmed, "beam_random_range" to "$beamRandomMin,$beamRandomMax",
    "glow_random_armed" to glowRandomArmed, "glow_random_range" to "$glowRandomMin,$glowRandomMax",
    "grid" to grid, "grid_data" to gridData, "oversample" to oversample,
) + light.values()
