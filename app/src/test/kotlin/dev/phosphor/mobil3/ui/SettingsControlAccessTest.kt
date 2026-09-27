package dev.phosphor.mobil3.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsProperties
import org.junit.Assert.*
import org.junit.Test

class SettingsControlAccessTest {
    private fun semantics(modifier: Modifier): List<SemanticsConfiguration> {
        val configurations = mutableListOf<SemanticsConfiguration>()
        modifier.foldIn(Unit) { _, element ->
            if (element is ModifierNodeElement<*>) {
                val node = element.create()
                if (node is SemanticsModifierNode) {
                    configurations.add(SemanticsConfiguration().also { config ->
                        with(node) { config.applySemantics() }
                    })
                }
            }
        }
        return configurations
    }

    @Test fun selectedStateIsExposedByActualChoiceSemantics() {
        for (active in listOf(false, true)) {
            val config = semantics(Modifier.settingsChoice(active)).single()
            assertEquals(active, config[SemanticsProperties.Selected])
            assertEquals(androidx.compose.ui.semantics.Role.Button, config[SemanticsProperties.Role])
        }
    }
    @Test fun finiteRangeActionsClampAndRejectWithoutExtraPublications() {
        val calls = mutableListOf<Float>()
        val action = SettingsRangeAction(2f, 1f, 3f, calls::add)
        assertFalse(action.set(Float.NaN))
        assertFalse(action.set(Float.POSITIVE_INFINITY))
        assertFalse(action.set(Float.NEGATIVE_INFINITY))
        assertFalse(action.set(2f))
        assertTrue(calls.isEmpty())
        assertTrue(action.set(-100f))
        assertTrue(action.set(100f))
        assertEquals(listOf(1f, 3f), calls)
    }

    @Test fun endpointActionsCannotCrossOrChangeTheOtherEndpoint() {
        val calls = mutableListOf<Pair<Float, Float>>()
        SettingsRangeAction(2f, 1f, 4f) { calls.add(it to 4f) }.set(5f)
        SettingsRangeAction(4f, 2f, 7f) { calls.add(2f to it) }.set(0f)
        assertEquals(listOf(4f to 4f, 2f to 2f), calls)
        val fixed = SettingsRangeAction(2f, 2f, 2f) { error("fixed interval must not publish") }
        assertFalse(fixed.step(true))
        assertFalse(fixed.step(false))
        assertFalse(fixed.set(4f))
    }

    @Test fun keyboardStepUsesOnePercentOfCurrentAllowedInterval() {
        val calls = mutableListOf<Float>()
        val action = SettingsRangeAction(2f, 1f, 3f, calls::add)
        assertTrue(action.step(true))
        assertTrue(action.step(false))
        assertEquals(2.02f, calls[0], 0.00001f)
        assertEquals(1.98f, calls[1], 0.00001f)
    }

    @Test fun malformedObservedRangesNeverConstructActions() {
        for ((value, minimum, maximum) in listOf(
            Triple(Float.NaN, 0f, 1f), Triple(0f, Float.NEGATIVE_INFINITY, 1f),
            Triple(0f, 0f, Float.POSITIVE_INFINITY), Triple(0f, 1f, 0f), Triple(2f, 0f, 1f),
        )) {
            try {
                SettingsRangeAction(value, minimum, maximum) {}
                fail("invalid range admitted")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun actualModifierExposesLabelObservedRangeAndTheProductionSetter() {
        val calls = mutableListOf<Float>()
        val modifier = Modifier.settingsRange("FOCUS", "2.00 px", SettingsRangeAction(2f, 1f, 3f, calls::add))
        val config = semantics(modifier).single { it.contains(SemanticsActions.SetProgress) }
        assertEquals(listOf("FOCUS"), config[SemanticsProperties.ContentDescription])
        assertEquals("2.00 px", config[SemanticsProperties.StateDescription])
        val range = config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(2f, range.current, 0f)
        assertEquals(1f..3f, range.range)
        val set = checkNotNull(config[SemanticsActions.SetProgress].action)
        assertFalse(set(Float.NaN))
        assertTrue(set(2.5f))
        assertEquals(listOf(2.5f), calls)
    }

    @Test fun primarySettingsActionsInstallFocusDrawingBeforeTheClickableTarget() {
        fun source(name: String): String = listOf(
            java.io.File("src/main/kotlin/dev/phosphor/mobil3/ui/$name.kt"),
            java.io.File("app/src/main/kotlin/dev/phosphor/mobil3/ui/$name.kt"),
        ).first { it.isFile }.readText()
        val primary = source("Controls").substringAfter("fun FlatKey(").substringBefore("fun SheetRow(")
        val focus = "if (accessible) Modifier.settingsFocusBorder(p) else Modifier"
        assertTrue(primary.indexOf(focus) >= 0)
        assertTrue(primary.indexOf(focus) < primary.indexOf(".clickable("))
        assertTrue(primary.contains("val accessible = LocalSettingsControlAccess.current"))
        val drawing = source("SettingsControlAccess").substringAfter("fun Modifier.settingsFocusBorder(")
            .substringBefore("internal class SettingsRangeAction")
        assertTrue(drawing.contains("onFocusChanged { focused = it.isFocused }"))
        assertTrue(drawing.contains("if (focused) drawRect(p.accent, style = Stroke(2.dp.toPx()))"))
        // This checks actual source wiring. Attached key delivery and focus pixels need Android.
    }

}
