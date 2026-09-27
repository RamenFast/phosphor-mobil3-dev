package dev.phosphor.mobil3.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import dev.phosphor.mobil3.settings.SettingsWriteOwner
import dev.phosphor.mobil3.settings.appearance.*
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Actual production adapters and policies. Source checks below do not execute Android or Compose. */
class AppearancePresentationTest {
    private class Rig(initial: Map<String, Any>) {
        val values = initial.toMutableMap()
        val owner = AppearanceWorkflow(AppearancePreferences(SettingsWriteOwner(), { values.toMap() }, {
            values[AppearancePreferences.KEY] = it
            true
        }, { error("Unexpected rollback") }), AppearanceMigration::initial)
        init { owner.load() }
        fun edit(edit: StyleOverride) {
            owner.apply(AppearancePalette.editCurrentStyle(owner.effective!!, edit), owner.committed!!.activeId)
        }
        fun stored() = AppearanceDocumentCodec.decode(values[AppearancePreferences.KEY] as String)
    }

    @Test fun migratedAnnotatedThenAppliedAmoledCornersPreservesEveryOtherAuthoredField() {
        val r = Rig(mapOf("room" to "glass", "ov_char" to 2))
        assertEquals(AppearanceCharacter.ANNOTATED, r.owner.effective!!.character)
        r.owner.apply(CuratedAppearances.amoled, "curated:amoled")
        val before = r.owner.effective!!
        val displayed = AppearancePalette.style(before)
        assertEquals(ChromeCharacter.Engraved, displayed.character)
        assertEquals(MotionFeel.Cut, displayed.motion)
        assertEquals(0.dp, displayed.cornerRadius)
        val action = displayed.nextCorners()
        assertEquals(StyleOverride(radiusDp = 8), action)
        r.edit(action)
        assertEquals(before.copy(radiusDp = 8), r.owner.effective)
        assertEquals(before.copy(radiusDp = 8), r.stored().active)
        assertEquals("curated:amoled", r.stored().activeId)
        assertEquals("glass", r.values["room"])
        assertEquals(2, r.values["ov_char"])
    }

    @Test fun modifiedCuratedAssociationNeverRestoresSavedStyleOnCorners() {
        val r = Rig(emptyMap())
        val authored = CuratedAppearances.dark.copy(motion = AppearanceMotion.SPRINGY,
            durationScale = 1.7f, densityScale = 1.25f, radiusDp = 41,
            monoProse = true, designators = true, panelAlphaScale = .25f)
        r.owner.apply(authored, "curated:dark")
        assertNotEquals(CuratedAppearances.dark, r.owner.effective)
        r.edit(AppearancePalette.style(r.owner.effective!!).nextCorners())
        assertEquals(authored.copy(radiusDp = 0), r.owner.effective)
        assertEquals(authored.copy(radiusDp = 0), r.stored().active)
        assertEquals("curated:dark", r.stored().activeId)
    }

    @Test fun eachFieldActionContainsOneFieldAndPreservesAllOtherAuthoredFields() {
        val before = CuratedAppearances.light.copy(durationScale = 1.7f, densityScale = .85f,
            radiusDp = 64, monoProse = false, designators = false, panelAlphaScale = .2f)
        val style = AppearancePalette.style(before)
        assertEquals(before.copy(designators = true), AppearancePalette.editCurrentStyle(before, style.nextLabels()))
        assertEquals(before.copy(radiusDp = 0), AppearancePalette.editCurrentStyle(before, style.nextCorners()))
        val nextMotion = style.nextMotion()
        assertEquals(StyleOverride(motion = MotionFeel.Cut), nextMotion)
        assertEquals(before.copy(motion = AppearanceMotion.CUT, durationScale = .5f),
            AppearancePalette.editCurrentStyle(before, nextMotion))
        for (chosen in listOf(CarvedStyle, VoidStyle, BenchStyle, GlassStyle)) {
            val changed = AppearancePalette.editCurrentStyle(before, StyleOverride(character = chosen.character))
            assertEquals(chosen, AppearancePalette.style(changed))
            assertEquals(before.colors, changed.colors)
            assertEquals(before.dark, changed.dark)
            assertEquals(before.accentFollowsBeam, changed.accentFollowsBeam)
        }
        assertEquals(StyleOverride(character = ChromeCharacter.Glass), style.nextCharacter())
    }

    @Test fun staleTuplesCannotReenterTheCurrentStyleAdapter() {
        for (tuple in listOf(StyleOverride(), StyleOverride(character = ChromeCharacter.Annotated, radiusDp = 0))) {
            try {
                AppearancePalette.editCurrentStyle(CuratedAppearances.amoled, tuple)
                fail("ROOM edits must carry exactly one field")
            } catch (_: IllegalArgumentException) { }
        }
    }

    private fun rgb(c: Color) = c.toArgb() and 0xffffff
    private fun palette(value: AppearanceValue) = AppearancePalette.palette(value, "test", "test")
    private fun contrast(foreground: Color, background: Color, minimum: Double) {
        assertEquals(1f, background.alpha, 0f)
        val actual = AppearanceContrast.over(foreground.toArgb(), rgb(background))
        assertTrue("${AppearanceContrast.ratio(actual, rgb(background))} < $minimum",
            AppearanceContrast.ratio(actual, rgb(background)) >= minimum)
    }

    @Test fun lightStatusTextHasOpaquePlaneBackplateOverBlackAndBrightScope() {
        val authored = CuratedAppearances.light
        val p = palette(authored)
        val status = p.readableOn(p.plane)
        for (scope in listOf(0, 0xffffff)) {
            assertEquals(authored.colors.plane, AppearanceContrast.over(status.plane.toArgb(), scope))
            for (ink in listOf(status.ink, status.ink2, status.muted)) {
                assertEquals(1f, ink.alpha, 0f)
                contrast(ink, status.plane, 4.5)
            }
        }
        assertEquals(authored, CuratedAppearances.light)
        assertEquals(authored.colors.ink2, rgb(p.ink2))
        assertEquals(authored.colors.surface, rgb(p.surface))
    }

    @Test fun blackBeamGlassControlsResolveActualSelectedTextAndUnselectedBoundary() {
        val authored = CuratedAppearances.glass
        val encoded = AppearanceDocumentCodec.encode(AppearanceDocument.of(active = authored))
        val p = palette(authored)
        val beam = p.withBeam(floatArrayOf(0f, 0f, 0f))
        assertEquals(0x304038, rgb(beam.accent))
        assertTrue(AppearanceContrast.ratio(rgb(beam.accent), rgb(beam.surface)) < 4.5)
        for (pressed in listOf(false, true)) {
            val backplate = beam.controlBackplate(pressed)
            val controls = beam.readableOn(backplate)
            assertEquals(1f, controls.accent.alpha, 0f)
            for (scope in listOf(0, 0xffffff)) assertEquals(rgb(backplate), AppearanceContrast.over(controls.surface.toArgb(), scope))
            contrast(controls.accent, controls.surface, 4.5)
            contrast(controls.ink2, controls.surface, 4.5)
            contrast(controls.line, controls.surface, 3.0)
            contrast(controls.lineStrong, controls.surface, 3.0)
        }
        assertEquals(0x304038, rgb(beam.accent))
        assertEquals(authored.colors.accent, rgb(p.accent))
        assertEquals(encoded, AppearanceDocumentCodec.encode(AppearanceDocument.of(active = authored)))
    }

    @Test fun pauseLabelRemainsReadableOverBlackAndBrightHeldContentWithoutChangingAuthoredBytes() {
        val document = AppearanceDocument.of(active = CuratedAppearances.light)
        val before = AppearanceDocumentCodec.encode(document)
        val p = palette(document.active)
        val pausePalette = p.readableOn(p.plane)
        for (held in listOf(0, 0xffffff)) {
            assertEquals(document.active.colors.plane, AppearanceContrast.over(pausePalette.plane.toArgb(), held))
            contrast(pausePalette.muted, pausePalette.plane, 4.5)
        }
        assertEquals(before, AppearanceDocumentCodec.encode(document))
    }

    @Test fun passingPresentationColorsAreKeptAndLowContrastCustomColorsRemainAuthored() {
        val low = CuratedAppearances.light.copy(colors = CuratedAppearances.light.colors.copy(
            ink = 0x777777, surface = 0x777777, plane = 0x777777, accent = 0x777777))
        val p = palette(low)
        val rendered = p.readableOn()
        contrast(rendered.ink, rendered.surface, 4.5)
        contrast(rendered.accent, rendered.surface, 4.5)
        assertEquals(0x777777, rgb(p.ink))
        assertEquals(0x777777, low.colors.ink)
        val dark = palette(CuratedAppearances.dark)
        assertEquals(dark.ink, dark.readableOn().ink)
        assertEquals(dark.accent, dark.readableOn().accent)
    }

    @Test fun densityChangesProductionSpacingButNeverTargetsOrTextInputs() {
        val value = CuratedAppearances.dark
        val compact = AppearancePalette.style(value.copy(densityScale = .85f))
        val roomy = AppearancePalette.style(value.copy(densityScale = 1.25f))
        for (base in listOf(2.dp, 3.dp, 4.dp, 6.dp, 8.dp, 10.dp, 12.dp, 14.dp)) {
            assertEquals(base.value * .85f, compact.space(base).value, 0f)
            assertEquals(base.value * 1.25f, roomy.space(base).value, 0f)
            assertTrue(compact.space(base) < roomy.space(base))
        }
        for (requested in listOf(0f, 32f, 40f, 48f, 52f, 64f)) {
            assertEquals(maxOf(48f, requested), AppearancePresentationPolicy.target(requested), 0f)
        }
        assertEquals(compact.copy(densityScale = roomy.densityScale), roomy)
    }

    @Test fun planeOnlyChangeReachesChromeWithoutChangingSignalOrOtherAuthoredRoles() {
        val before = CuratedAppearances.amoled
        val after = before.copy(colors = before.colors.copy(plane = 0x5b2147))
        val a = palette(before)
        val b = palette(after)
        assertEquals(a.copy(plane = b.plane), b)
        assertEquals(0x5b2147, rgb(b.readableOn(b.plane).plane))
        assertEquals(a.surface, b.surface)
        assertEquals(a.beamAccent, b.beamAccent)
        assertEquals(a.withBeam(floatArrayOf(.2f, .7f, 1f)).beamAccent,
            b.withBeam(floatArrayOf(.2f, .7f, 1f)).beamAccent)
    }

    @Test fun postPolicyRequiresActualVisibilityAndCurrentReducedMotion() = runBlocking {
        for (activity in listOf(false, true)) for (surface in listOf(false, true))
            for (component in listOf(false, true)) for (reduced in listOf(false, true))
                for (motion in AppearanceMotion.entries) for (retired in listOf(false, true)) {
                    val policy = AppearancePresentationPolicy.post(activity, surface, component, reduced, motion, retired)
                    val expected = when {
                        retired || !activity || !surface || !component -> AppearancePresentationPolicy.Post.HIDDEN
                        reduced || motion == AppearanceMotion.CUT -> AppearancePresentationPolicy.Post.STATIC
                        else -> AppearancePresentationPolicy.Post.REVEAL
                    }
                    assertEquals(expected, policy)
                    if (policy != AppearancePresentationPolicy.Post.REVEAL) {
                        val calls = mutableListOf<String>()
                        AppearancePresentationPolicy.revealPost(policy, { calls += "line" }, { calls += "retire" }) { calls += "delay" }
                        assertTrue(calls.isEmpty())
                    }
                }
    }

    @Test fun visiblePostUsesExactlyThreeRevealsThenRetires() = runBlocking {
        val calls = mutableListOf<String>()
        AppearancePresentationPolicy.revealPost(AppearancePresentationPolicy.Post.REVEAL,
            { calls += "line:$it" }, { calls += "retire" }) { calls += "delay:$it" }
        assertEquals(listOf("line:0", "delay:160", "line:1", "delay:160", "line:2", "delay:160", "line:3", "delay:900", "retire"), calls)
    }

    @Test fun cancellingVisiblePostPreventsRemainingRevealsAndRetirement() = runBlocking {
        val paused = CompletableDeferred<Unit>()
        val wait = CompletableDeferred<Unit>()
        val calls = mutableListOf<String>()
        val job = launch {
            AppearancePresentationPolicy.revealPost(AppearancePresentationPolicy.Post.REVEAL,
                { calls += "line:$it" }, { calls += "retire" }) {
                calls += "delay:$it"
                paused.complete(Unit)
                wait.await()
            }
        }
        paused.await()
        job.cancelAndJoin()
        wait.complete(Unit)
        assertEquals(listOf("line:0", "delay:160"), calls)
    }

    @Test fun glyphPolicyUsesFiniteMeaningfulEndpointsAndReducedOrCutWins() {
        assertEquals(0f, AppearancePresentationPolicy.sectionAngle(false), 0f)
        assertEquals(180f, AppearancePresentationPolicy.sectionAngle(true), 0f)
        assertEquals(0f, AppearancePresentationPolicy.overflowIndex(false), 0f)
        assertEquals(1f, AppearancePresentationPolicy.overflowIndex(true), 0f)
        for (motion in AppearanceMotion.entries) {
            assertFalse(AppearanceMotionPolicy.stateChange(true, true, true, true, motion))
            assertEquals(motion != AppearanceMotion.CUT, AppearanceMotionPolicy.stateChange(true, true, true, false, motion))
        }
    }

    private fun source(path: String): String = listOf(File("src/main/kotlin/dev/phosphor/mobil3", path),
        File("app/src/main/kotlin/dev/phosphor/mobil3", path)).first { it.isFile }.readText()

}
