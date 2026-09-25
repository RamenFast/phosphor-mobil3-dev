package dev.phosphor.mobil3.settings.instrument

import dev.phosphor.mobil3.ui.LightCycleGuard
import dev.phosphor.mobil3.ui.LightRgb
import dev.phosphor.mobil3.ui.LightSettings
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

internal fun reject(block: () -> Unit): InstrumentPresetException {
    try { block() } catch (error: InstrumentPresetException) {
        assertTrue(error.error.isNotBlank())
        assertTrue(error.fix.isNotBlank())
        return error
    }
    throw AssertionError("Expected a typed preset error")
}

object InstrumentPresetFixtures {
    val sixSlotSetup: InstrumentSetup = CuratedInstrumentPresets.cleanXy.setup.copy(
        mode = 10, randomModeArmed = true, randomBanModes = listOf(0, 2, 4, 6, 8, 10),
        geomFx = 4, geomAmount = 1f, gain = 7f, autoGain = false, focus = 3f,
        beamEnergy = 30f, glow = 0.98f, beamRandomArmed = true, beamRandomMin = 1f, beamRandomMax = 30f,
        glowRandomArmed = true, glowRandomMin = 0f, glowRandomMax = 0.98f, grid = false, gridData = true,
        oversample = 4, light = LightSettings(
            listOf(LightRgb(1f, 0f, 0f), LightRgb(0f, 1f, 0f), LightRgb(0f, 0f, 1f),
                LightRgb(1f, 1f, 0f), LightRgb(0.1f, 0.2f, 0.3f), LightRgb(1f, 1f, 1f)),
            45, 8, 0.1f, false, true, true, true, 0.1f, 60f))

    @JvmStatic fun main(args: Array<String>) {
        val output = Path.of(args.single())
        Files.createDirectories(output)
        listOf("clean-xy" to CuratedInstrumentPresets.cleanXy.setup,
            "spectral-bench" to CuratedInstrumentPresets.spectralBench.setup,
            "ambient" to CuratedInstrumentPresets.ambient.setup,
            "six-slot" to sixSlotSetup).forEach { (name, setup) ->
            Files.write(output.resolve("$name.setup.json"), InstrumentPresetCodec.encodeSetup(setup).toByteArray(Charsets.UTF_8))
        }
    }
}

class InstrumentSetupTest {
    private val baseline = CuratedInstrumentPresets.cleanXy.setup

    @Test fun everyAuthoredFloatBoundaryAndNonfiniteValueIsValidated() {
        data class Field(val min: Float, val max: Float, val build: (Float) -> InstrumentSetup)
        val fields = listOf(
            Field(0f, 1f) { baseline.copy(geomAmount = it) },
            Field(0.1f, 64f) { baseline.copy(gain = it) },
            Field(0.3f, 3f) { baseline.copy(focus = it) },
            Field(1f, 30f) { baseline.copy(beamEnergy = it) },
            Field(0f, 0.98f) { baseline.copy(glow = it) },
            Field(1f, 30f) { baseline.copy(beamRandomMin = it, beamRandomMax = 30f) },
            Field(1f, 30f) { baseline.copy(beamRandomMin = 1f, beamRandomMax = it) },
            Field(0f, 0.98f) { baseline.copy(glowRandomMin = it, glowRandomMax = 0.98f) },
            Field(0f, 0.98f) { baseline.copy(glowRandomMin = 0f, glowRandomMax = it) })
        fields.forEachIndexed { index, field ->
            field.build(field.min)
            field.build(field.max)
            listOf(Math.nextDown(field.min), Math.nextUp(field.max), Float.NaN,
                Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { value ->
                try { reject { field.build(value) } } catch (error: AssertionError) {
                    throw AssertionError("Field $index accepted $value outside ${field.min}..${field.max}", error)
                }
            }
        }
        reject { baseline.copy(beamRandomMin = 20f, beamRandomMax = 6f) }
        reject { baseline.copy(glowRandomMin = 0.9f, glowRandomMax = 0.3f) }
    }

    @Test fun discreteFieldsAndRandomEligibilityAreExact() {
        (0..10).forEach { baseline.copy(mode = it) }
        listOf(-1, 11, Int.MAX_VALUE).forEach { reject { baseline.copy(mode = it) } }
        (0..4).forEach { baseline.copy(geomFx = it) }
        listOf(-1, 5).forEach { reject { baseline.copy(geomFx = it) } }
        listOf(1, 2, 4).forEach { baseline.copy(oversample = it) }
        listOf(0, 3, 5, -1).forEach { reject { baseline.copy(oversample = it) } }
        baseline.copy(randomModeArmed = true, randomBanModes = (0..8).toList())
        baseline.copy(randomModeArmed = false, randomBanModes = (0..10).toList())
        reject { baseline.copy(randomModeArmed = true, randomBanModes = (0..9).toList()) }
        listOf(listOf(0, 0), listOf(2, 1), listOf(-1), listOf(11)).forEach {
            reject { baseline.copy(randomBanModes = it) }
        }
    }

    @Test fun setupAndLightAreDefensivelyImmutable() {
        val bans = mutableListOf(1, 3)
        val setup = baseline.copy(randomBanModes = bans)
        bans.clear()
        assertEquals(listOf(1, 3), setup.randomBanModes)
        assertThrows(UnsupportedOperationException::class.java) { (setup.randomBanModes as MutableList<Int>).clear() }
        val slots = mutableListOf(LightRgb(1f, 0f, 0f))
        val light = LightSettings(slots, selectedMask = 1)
        slots.clear()
        assertEquals(1, light.slots.size)
        assertThrows(UnsupportedOperationException::class.java) { (light.slots as MutableList<LightRgb>).clear() }
        assertEquals(setup, setup.copy())
        assertEquals(setup.hashCode(), setup.copy().hashCode())
    }

    @Test fun sixSlotTupleAndAllBooleanChoicesRoundTrip() {
        val setup = InstrumentPresetFixtures.sixSlotSetup
        assertEquals(setup, InstrumentPresetCodec.decodeSetup(InstrumentPresetCodec.encodeSetup(setup)))
        assertEquals(setup.copy(light = setup.light.copy(perTrack = true)),
            InstrumentPresetCodec.decodeSetup(InstrumentPresetCodec.encodeSetup(setup.copy(light = setup.light.copy(perTrack = true)))))
        assertEquals(45, setup.light.selectedMask)
        assertEquals(6, setup.light.slots.size)
    }

    @Test fun exactLightGuardIsReusedWithoutChangingSavedSetup() {
        for (perTrack in listOf(false, true)) for (random in listOf(false, true)) for (ack in listOf(false, true)) {
            val setup = InstrumentPresetFixtures.sixSlotSetup.copy(light = InstrumentPresetFixtures.sixSlotSetup.light.copy(
                perTrack = perTrack, randomInterval = random))
            val expected = LightCycleGuard.evaluate(setup.light, ack)
            val actual = setup.guardLight(ack)
            assertEquals(expected.safe, actual.safe.light)
            assertEquals(expected.pending, actual.pending?.light)
            assertEquals(setup.copy(light = expected.safe), actual.safe)
            assertEquals(setup, InstrumentPresetCodec.decodeSetup(InstrumentPresetCodec.encodeSetup(setup)))
        }
    }

    @Test fun allCuratedTuplesMatchTheContract() {
        val entries = CuratedInstrumentPresets.all
        assertEquals(listOf("Clean XY", "Spectral bench", "Ambient"), entries.map { it.name })
        assertEquals(listOf(0, 8, 7), entries.map { it.setup.mode })
        assertEquals(listOf(0, 2, 0), entries.map { it.setup.light.preset })
        assertEquals(listOf(8f, 6f, 6f), entries.map { it.setup.beamEnergy })
        assertEquals(listOf(0.20f, 0.15f, 0.70f), entries.map { it.setup.glow })
        assertEquals(listOf(true, true, false), entries.map { it.setup.grid })
        entries.forEach { (_, _, s) ->
            assertEquals(0.3f, s.focus)
            assertEquals(1.8332275f, s.gain)
            assertTrue(s.autoGain)
            assertEquals(0, s.geomFx)
            assertEquals(0.6f, s.geomAmount)
            assertEquals(1, s.oversample)
            assertFalse(s.gridData)
            assertFalse(s.randomModeArmed)
            assertTrue(s.randomBanModes.isEmpty())
            assertFalse(s.beamRandomArmed)
            assertEquals(6f, s.beamRandomMin)
            assertEquals(20f, s.beamRandomMax)
            assertFalse(s.glowRandomArmed)
            assertEquals(0.30f, s.glowRandomMin)
            assertEquals(0.90f, s.glowRandomMax)
            assertFalse(s.light.perTrack)
            assertFalse(s.light.generatedAuto)
            assertFalse(s.light.shuffle)
            assertFalse(s.light.randomInterval)
            assertEquals(3f, s.light.intervalMin)
            assertEquals(6f, s.light.intervalMax)
            assertEquals(s, InstrumentPresetCodec.decodeSetup(InstrumentPresetCodec.encodeSetup(s)))
        }
        entries.take(2).forEach { assertEquals(LightSettings(preset = it.setup.light.preset), it.setup.light) }
        assertEquals(listOf(LightRgb(0.42f, 1f, 0.55f), LightRgb(0.35f, 0.75f, 1f)), entries[2].setup.light.slots)
        assertEquals(3, entries[2].setup.light.selectedMask)
        assertEquals(10f, entries[2].setup.light.seconds)
        assertTrue(InstrumentPresetCollection.empty().records.isEmpty())
        assertThrows(UnsupportedOperationException::class.java) { (entries as MutableList<*>).clear() }
    }

    @Test fun wireRequiresEveryFieldAndRejectsWrongScalarTypes() {
        val encoded = InstrumentPresetCodec.encodeSetup(InstrumentPresetFixtures.sixSlotSetup)
        val original = JSONObject(encoded)
        val booleanFields = listOf("random_mode_armed", "auto_gain", "beam_random_armed", "glow_random_armed", "grid", "grid_data")
        val floatFields = listOf("geom_amount", "gain", "focus", "beam_energy", "glow", "beam_random_min", "beam_random_max", "glow_random_min", "glow_random_max")
        original.keys().asSequence().toList().forEach { field ->
            reject { InstrumentPresetCodec.decodeSetup(JSONObject(encoded).also { it.remove(field) }.toString()) }
            listOf(JSONObject.NULL, JSONObject(), JSONArray()).forEach { bad ->
                if (field != "random_ban_modes" || bad !is JSONArray) {
                    reject { InstrumentPresetCodec.decodeSetup(JSONObject(encoded).put(field, bad).toString()) }
                }
            }
        }
        booleanFields.forEach { key -> listOf("true", 0, 1).forEach { bad ->
            reject { InstrumentPresetCodec.decodeSetup(JSONObject(encoded).put(key, bad).toString()) }
        } }
        floatFields.forEach { key -> listOf("1", true, "NaN", "Infinity").forEach { bad ->
            reject { InstrumentPresetCodec.decodeSetup(JSONObject(encoded).put(key, bad).toString()) }
        } }
        listOf("mode", "geom_fx", "oversample").forEach { key ->
            listOf("1", true, 1.5).forEach { bad -> reject { InstrumentPresetCodec.decodeSetup(JSONObject(encoded).put(key, bad).toString()) } }
            reject { InstrumentPresetCodec.decodeSetup(encoded.replace(Regex("\"$key\":[0-9]+"), "\"$key\":1.0")) }
        }
    }
}
