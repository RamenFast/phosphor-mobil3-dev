package dev.phosphor.mobil3

import java.io.File
import org.junit.Test
import kotlin.test.*

/** Independent source guards. Real deposited-energy arithmetic is tested in Rust engine::tests. */
class BrightnessRetirementTest {
    private val root = sequenceOf(File("."), File(".."))
        .first { File(it, "rust/src/render.rs").isFile }

    @Test fun retiredBrightnessDriverHasNoProductionSymbolsOrGestureOwners() {
        val retired = Regex("BloomPull|bloom_pull|bloom_energy_multiplier|bottomBloomOverscroll|beam.breath", RegexOption.IGNORE_CASE)
        val hits = listOf("app/src/main", "rust/src").flatMap { path ->
            File(root, path).walkTopDown().filter { it.isFile && it.extension in listOf("kt", "rs") }
                .filter { retired.containsMatchIn(it.readText()) }.map { it.relativeTo(root).path }.toList()
        }
        assertTrue(hits.isEmpty(), "Retired brightness ownership remains in $hits")
    }

    @Test fun fixedModalScrimAndRealPullOwnersRemainIndependentOfBeamEnergy() {
        val ui = File(root, "app/src/main/kotlin/dev/phosphor/mobil3/ui")
        assertTrue(File(ui, "Dimens.kt").readText().contains("const val scrimAlpha = 0.40f"))
        assertTrue(File(ui, "Sheets.kt").readText().contains("alpha = Dim.scrimAlpha * (entryReveal?.progress ?: 1f)"))
        assertTrue(File(ui, "Motion.kt").readText().contains("class PullRevealState("))
        val screen = File(ui, "PhosphorScreen.kt").readText()
        assertTrue(screen.contains("val settingsReveal = remember(revealScope) { PullRevealState(revealScope) }"))
        assertTrue(screen.contains("val overflowReveal = remember(revealScope) { PullRevealState(revealScope) }"))
        assertTrue(File(ui, "Gestures.kt").readText().contains("host.beginBottomChromePull()"))
    }

    @Test fun rendererUsesTheHostExercisedDepositTransformAndRetainsOrdinaryBeamTuning() {
        val render = File(root, "rust/src/render.rs").readText()
        assertTrue(render.contains("crate::engine::map_deposit_segment(s, w, h, scale_xy, scale_y, brightness)"))
        assertTrue(render.contains("r.advance(&mapped)"))
        assertTrue(render.contains("Cmd::SetBeamEnergy(e)"))
        assertTrue(render.contains("Cmd::SetFocus(f)"))
        assertTrue(render.contains("let mut brightness = 1.0_f32"))
        assertFalse(render.contains("brightness *="))
    }
}
