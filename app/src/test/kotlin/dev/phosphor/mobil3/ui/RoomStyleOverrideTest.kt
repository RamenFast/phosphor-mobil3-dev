package dev.phosphor.mobil3.ui

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.ui.unit.dp
import org.junit.Test
import kotlin.test.*

class RoomStyleOverrideTest {
    private val styles = listOf(CarvedStyle, VoidStyle, BenchStyle, GlassStyle)

    private fun sourceBodyAfter(text: String, marker: String): String {
        val start = text.indexOf(marker)
        assertTrue(start >= 0, "Missing source block: $marker")
        val bodyStart = start + marker.length
        var depth = 1
        for (index in bodyStart until text.length) {
            when (text[index]) {
                '{' -> depth++
                '}' -> depth--
            }
            if (depth == 0) return text.substring(bodyStart, index)
        }
        error("Unclosed source block: $marker")
    }

    @Test fun nullOverridesFollowEachRoomAndUnknownRoomsKeepFallback() {
        for (room in Rooms) assertEquals(room.style, room.style.overridden(StyleOverride()))
        val custom = RoomStyle(durationScale = 1.7f, densityScale = .8f, panelAlphaScale = .7f)
        assertEquals(custom, custom.overridden(StyleOverride()))
        assertEquals(CarvedStyle, BlossomDark.copy(id = "unknown").style)
    }

    @Test fun feelReplacesAllCoupledFieldsInsteadOfLeavingPreviousRoomResidue() {
        for (old in styles) for (chosen in styles) {
            assertEquals(chosen, old.overridden(StyleOverride(character = chosen.character)))
        }
        val bench = GlassStyle.overridden(StyleOverride(character = ChromeCharacter.Annotated))
        assertEquals(.9f, bench.densityScale)
        assertTrue(bench.monoProse)
        assertTrue(bench.designators)
        assertEquals(1f, bench.panelAlphaScale)
    }

    @Test fun explicitMotionCornersAndLabelsWinOverFeelWithoutChangingOtherCoupling() {
        for (old in styles) for (feel in ChromeCharacter.entries) for (motion in MotionFeel.entries) {
            val overrides = StyleOverride(feel, motion, 0, false)
            val effective = old.overridden(overrides)
            assertEquals(feel, effective.character)
            assertEquals(motion, effective.motion)
            assertEquals(if (motion == MotionFeel.Cut) .5f else 1f, effective.durationScale)
            assertEquals(0.dp, effective.cornerRadius)
            assertFalse(effective.designators)
            val canonical = styles.first { it.character == feel }
            assertEquals(canonical.densityScale, effective.densityScale)
            assertEquals(canonical.monoProse, effective.monoProse)
            assertEquals(canonical.panelAlphaScale, effective.panelAlphaScale)
        }
    }

    @Test fun clearingAnOverrideRestoresEffectiveFeelThenRoom() {
        val explicit = StyleOverride(ChromeCharacter.Glass, MotionFeel.Cut, 0, true)
        assertEquals(GlassStyle, BenchStyle.overridden(explicit.copy(motion = null, radiusDp = null, designators = null)))
        assertEquals(BenchStyle, BenchStyle.overridden(StyleOverride()))
        assertEquals(1f, VoidStyle.overridden(StyleOverride(motion = MotionFeel.Eased)).durationScale)
    }

    @Test fun actualStateCallbacksCycleEachChoiceWithoutLosingOtherOverrides() {
        val state = ScopeUiState()
        state.styleOverride = StyleOverride(radiusDp = 12, designators = false)
        repeat(ChromeCharacter.entries.size) { i ->
            state.styleOverride = state.styleOverride.nextCharacter()
            assertEquals(ChromeCharacter.entries[i], state.styleOverride.character)
            assertEquals(12, state.styleOverride.radiusDp)
            assertEquals(false, state.styleOverride.designators)
            assertEquals(ChromeCharacter.entries[i], state.room.style.overridden(state.styleOverride).character)
        }
        state.styleOverride = state.styleOverride.nextCharacter()
        assertNull(state.styleOverride.character)
        for (expected in MotionFeel.entries + listOf(null)) {
            state.styleOverride = state.styleOverride.nextMotion()
            assertEquals(expected, state.styleOverride.motion)
        }
        state.styleOverride = StyleOverride()
        for (expected in listOf(0, 8, 12, null)) {
            state.styleOverride = state.styleOverride.nextCorners()
            assertEquals(expected, state.styleOverride.radiusDp)
        }
        for (expected in listOf(true, false, null)) {
            state.styleOverride = state.styleOverride.nextLabels()
            assertEquals(expected, state.styleOverride.designators)
        }
    }

    @Test fun motionSpecUsesEffectiveFeelAndReducedMotionAlwaysWins() {
        assertTrue(styleSpec<Float>(false, VoidStyle, 160) is SnapSpec<*>)
        assertTrue(styleSpec<Float>(false, GlassStyle, 160) is SpringSpec<*>)
        val bench = styleSpec<Float>(false, BenchStyle, 160)
        assertTrue(bench is TweenSpec<*>)
        assertEquals(160, bench.durationMillis)
        assertEquals(0f, bench.easing.transform(.1f))
        for (base in styles) for (motion in MotionFeel.entries) {
            val effective = base.overridden(StyleOverride(motion = motion))
            assertTrue(styleSpec<Float>(true, effective, 160) is SnapSpec<*>)
        }
    }

}
