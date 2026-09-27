package dev.phosphor.mobil3.ui

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.phosphor.mobil3.MicrophoneChoice
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Failure inventory: vertical input can activate a control; fling can dismiss a sheet;
 * consumed scroll can prevent pull dismissal; a short pull can stick; rows can disappear;
 * state marks can lie; mic selection can need two taps; bans can starve random mode;
 * large text can clip; Back can skip the manual index or Settings.
 *
 * These tests inject real pointer events and inspect real Compose semantics/layout results.
 * They do not test source strings. The supplied host replaces only external activity actions.
 */
@RunWith(AndroidJUnit4::class)
class TransposeUiTest {
    @get:Rule val compose = createComposeRule()
    private val state = ScopeUiState().apply {
        instrumentCollection = dev.phosphor.mobil3.settings.instrument.InstrumentPresetCollection.empty()
    }
    private lateinit var back: OnBackPressedDispatcher
    private lateinit var density: Density
    private lateinit var inputMode: InputModeManager
    private lateinit var settingsScroll: ScrollState
    private var destination by mutableStateOf(Sheet.SETTINGS)
    private val actions = RecordingSheetActions(state) { destination = Sheet.MANUAL }

    private fun mount(fontScale: Float = 1f, content: @Composable () -> Unit) {
        compose.setContent {
            density = Density(LocalDensity.current.density, fontScale)
            inputMode = LocalInputModeManager.current
            back = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            CompositionLocalProvider(
                LocalDensity provides density,
                LocalRoomStyle provides CarvedStyle,
                LocalChromeLandscape provides false,
            ) {
                Box(Modifier.widthIn(max = 360.dp).fillMaxSize()) { content() }
            }
        }
        compose.waitForIdle()
    }

    private fun mountSettings(fontScale: Float = 1f) = mount(fontScale) {
        val presentation = rememberSettingsPresentationState()
        settingsScroll = presentation.scroll
        when (destination) {
            Sheet.SETTINGS -> SettingsSheet(state, Amoled, false, actions, state.focus,
                { state.focus = it }, presentation, object : InstrumentPresetActions {},
                onDismiss = { destination = Sheet.NONE })
            Sheet.MANUAL -> ManualSheet(Amoled, false, onOpenLink = { error("No external links in this test") },
                onDismiss = { destination = Sheet.SETTINGS })
            else -> Unit
        }
    }

    private fun text(label: String) = compose.onNodeWithText(label)
    private fun row(label: String) = compose.onNode(hasText(label) and hasClickAction())
    private fun reveal(label: String): SemanticsNodeInteraction = text(label).performScrollTo().assertIsDisplayed()
    private fun tap(node: SemanticsNodeInteraction) { node.performTouchInput { click() }; compose.waitForIdle() }
    private fun px(dp: Float) = with(density) { dp.dp.toPx() }

    @Test fun settingsGroupsAndKeyRowsAreReachable() {
        state.floatingHudEnabled = true
        mountSettings()
        val groups = listOf("SOUND & VIEW", "SCREEN", "PiP & BACKGROUND", "SOURCES", "SETUPS", "ABOUT")
        groups.forEach { label ->
            reveal(label).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        }
        listOf("auto size", "size", "view lock", "focus", "beam", "glow", "vary beam per track",
            "vary glow per track", "frame rate", "fps line", "keep screen bright", "HDR", "fullscreen",
            "keys always visible", "double tap to play", "when paused", "lock scope rotation",
            "lock key placement", "auto PiP", "floating HUD", "HUD background", "floating HUD now",
            "keep playing in background", "on launch", "ask for permission at launch",
            "save current setup", "settings file", "setups file", "manual", "version")
            .forEach { reveal(it) }
        listOf("solid", "clear", "nothing", "microphone", "everything playing").forEach {
            reveal(it).assertHasClickAction()
        }
        row("floating HUD now").performScrollTo().assert(hasText("show"))
        compose.runOnIdle { assertTrue("Must actually scroll the Settings body", settingsScroll.value > 0) }
    }

    @Test fun settingsToggleReportsItsStateAndDeveloperUnlockExposesItsGroup() {
        mountSettings()
        row("auto size").assertIsOn().assert(SemanticsMatcher.expectValue(
            SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Switch))
        tap(row("auto size"))
        row("auto size").assertIsOff().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "off"))
        compose.runOnIdle { assertEquals(listOf("auto:false"), actions.calls) }
        tap(row("auto size"))
        row("auto size").assertIsOn()
        reveal("version")
        repeat(7) { tap(row("version")) }
        reveal("DEVELOPER").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        listOf("status band", "beam rate", "grid data", "pause display only", "APPEARANCE VALUES").forEach { reveal(it) }
    }

    @Test fun lightPresetAndSavedSwatchMarkTheWornSelection() {
        state.light = LightSettings(slots = listOf(LightRgb(1f, 0f, 0f), LightRgb(0f, 1f, 0f)), selectedMask = 1)
        val presetCalls = mutableListOf<Int>()
        mount {
            LightSheetV2(state, Amoled, false,
                onPickPreset = { index ->
                    presetCalls += index
                    state.light = state.light.copy(preset = index, selectedMask = 0, generatedAuto = false)
                },
                onLightChange = { state.light = it }, onDeleteSlot = { error("Unexpected delete") },
                onRoll = { error("Unexpected roll") }, epilepsyAcknowledged = { false }, ackEpilepsy = {}, onDismiss = {})
        }
        val amber = compose.onNodeWithContentDescription("Amber")
        tap(amber.performScrollTo())
        amber.assertIsSelected()
        compose.onNodeWithContentDescription("saved 1 · red").assertIsNotSelected()
        compose.runOnIdle { assertEquals(listOf(1), presetCalls) }
        val saved = compose.onNodeWithContentDescription("saved 2 · green")
        tap(saved.performScrollTo())
        saved.assertIsSelected()
        amber.assertIsNotSelected()
        compose.onNodeWithContentDescription("saved 1 · red").assertIsNotSelected()
        compose.runOnIdle { assertEquals(2, state.light.selectedMask) }
    }

    @Test fun sourceIdleNonSelectedMicrophoneStartsWithOneTapAndStopsWithOneTap() {
        state.microphoneInputs = listOf(MicrophoneChoice(10, 15, "internal"), MicrophoneChoice(20, 22, "external"))
        state.selectedMicrophone = 10
        mount { SourceSheet(state, Amoled, false, actions, onDismiss = {}) }
        val usb = row("USB headset").performScrollTo()
        usb.assertIsNotSelected()
        tap(usb)
        usb.assertIsSelected().assert(hasText("stop"))
        compose.runOnIdle {
            assertEquals(listOf("chooseAndStart:20"), actions.calls)
            assertTrue(state.live)
            assertEquals(20, state.selectedMicrophone)
        }
        tap(usb)
        usb.assertIsNotSelected()
        compose.runOnIdle { assertEquals(listOf("chooseAndStart:20", "stop"), actions.calls) }
    }

    @Test fun modeSkipCellsRemainAvailableBeforeRandomAndKeepTwoFaces() {
        val published = mutableListOf<Set<Int>>()
        mount {
            ModeSheet(state, Amoled, false, onPick = { state.modeIndex = it }, onGeomFx = { state.geomFx = it },
                onGeomAmount = { state.geomAmount = it }, onBanModes = { published += it; state.randomBanModes = it }, onDismiss = {})
        }
        reveal("SKIP ON ⚄")
        ModeLabels.forEachIndexed { index, name ->
            val cell = compose.onNodeWithContentDescription("skip $name on random").performScrollTo()
            tap(cell)
            if (index < ModeLabels.size - 2) cell.assertIsOn() else cell.assertIsOff()
        }
        compose.runOnIdle {
            assertFalse(state.randomModeArmed)
            assertEquals(ModeLabels.size - 2, state.randomBanModes.size)
            assertEquals(ModeLabels.size, published.size)
            assertTrue(published.all { ModeLabels.size - it.size >= 2 })
        }
        val first = compose.onNodeWithContentDescription("skip ${ModeLabels.first()} on random").performScrollTo()
        tap(first)
        first.assertIsOff()
    }

    @Test fun manualSystemBackReturnsChapterThenIndexThenSettings() {
        mountSettings()
        tap(row("manual").performScrollTo())
        compose.onNodeWithContentDescription("Search manual chapters").assertExists()
        val chapter = ManualContent.visible(false).first().title
        tap(row(chapter).performScrollTo())
        compose.onNodeWithContentDescription("Search manual chapters").assertDoesNotExist()
        text(chapter).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithContentDescription("Search manual chapters").assertExists()
        text("MANUAL").assertIsDisplayed()
        compose.runOnIdle { back.onBackPressed() }
        text("SETTINGS").assertIsDisplayed()
        compose.onNodeWithContentDescription("Search manual chapters").assertDoesNotExist()
        row("manual").assertIsDisplayed()
    }

    /** Query actual BasicText layout semantics, not just existence of a full text string. */
    private fun assertTextFits(label: String) {
        reveal(label)
        val node = compose.onNode(hasText(label) and SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
            useUnmergedTree = true)
        val results = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { get -> assertTrue(get(results)) }
        assertTrue("No layout result for $label", results.isNotEmpty())
        results.forEach { layout ->
            assertFalse("Overflow: $label", layout.hasVisualOverflow)
            repeat(layout.lineCount) { line -> assertFalse("Ellipsis: $label", layout.isLineEllipsized(line)) }
        }
        node.assertIsDisplayed()
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val rootBounds = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("Outside viewport: $label", bounds.left >= rootBounds.left && bounds.right <= rootBounds.right)
    }

    @Test fun fontScaleTwoSettingsHeadingsRowsAndChoicesDoNotTruncate() {
        mountSettings(fontScale = 2f)
        listOf("SOUND & VIEW", "SCREEN", "PiP & BACKGROUND", "SOURCES", "SETUPS", "ABOUT",
            "auto size", "keep screen bright", "keys always visible", "double tap to play",
            "lock scope rotation", "lock key placement", "keep playing in background",
            "ask for permission at launch", "everything playing", "manual", "version").forEach { assertTextFits(it) }
        val toggle = row("keep playing in background").performScrollTo()
        toggle.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState))
        val height = toggle.fetchSemanticsNode().boundsInRoot.height
        assertTrue("Touch target is less than 48dp", height >= px(48f))
    }

    private lateinit var fixtureScroll: ScrollState
    private var checked by mutableStateOf(false)
    private var slider by mutableFloatStateOf(.2f)
    private var toggleChanges = 0
    private var sliderChanges = 0
    private var dismissed by mutableStateOf(false)

    private fun mountGestureFixture() = mount {
        fixtureScroll = rememberScrollState()
        if (!dismissed) {
            CompositionLocalProvider(LocalSettingsControlAccess provides true) {
                SheetHost(Amoled, "GESTURE FIXTURE", false, onDismiss = { dismissed = true }) {
                    Column(Modifier.verticalScroll(fixtureScroll, overscrollEffect = null)) {
                        SettingToggle("guarded toggle", checked, Amoled) { checked = it; toggleChanges++ }
                        SettingSlider("guarded slider", slider, 0f, 1f, Amoled, { "%.2f".format(it) }) {
                            slider = it; sliderChanges++
                        }
                        Spacer(Modifier.height(1800.dp))
                        Mono("scroll end", Amoled.ink)
                    }
                }
            }
        }
    }

    private fun control(sliderControl: Boolean): SemanticsNodeInteraction = if (sliderControl)
        compose.onNodeWithContentDescription("guarded slider") else row("guarded toggle")

    private fun guardedGesture(sliderControl: Boolean, dyDp: Float, duration: Long) {
        mountGestureFixture()
        val node = control(sliderControl)
        if (dyDp > 0) {
            val top = text("GESTURE FIXTURE").fetchSemanticsNode().boundsInRoot.top
            dragAndHold(node, dyDp, duration)
            val pulledTop = text("GESTURE FIXTURE").fetchSemanticsNode().boundsInRoot.top
            assertTrue("Precondition: finger must actually pull the sheet", pulledTop > top + px(8f))
            releaseAfterHold(node)
        } else {
            node.performTouchInput {
                // Start away from the thumb: a leaked tap would visibly change the value.
                val start = Offset(width * .8f, center.y)
                swipe(start, start + Offset(0f, px(dyDp)), durationMillis = duration)
            }
            compose.waitForIdle()
            compose.runOnIdle { assertTrue("Precondition: gesture must scroll content", fixtureScroll.value > 0) }
        }
        compose.runOnIdle {
            assertFalse("Vertical gesture dismissed fixture", dismissed)
            assertEquals("Toggle changed during vertical input", 0, toggleChanges)
            assertEquals("Slider changed during vertical input", 0, sliderChanges)
        }
        node.performScrollTo().performTouchInput { click(Offset(width * .8f, center.y)) }
        compose.waitForIdle()
        compose.runOnIdle {
            if (sliderControl) {
                assertTrue("Next real slider tap was suppressed", slider > .6f)
                assertTrue(sliderChanges > 0)
            } else {
                assertTrue("Next real toggle tap was suppressed", checked)
                assertEquals(1, toggleChanges)
            }
        }
    }

    @Test fun scrollStartingOnToggleDoesNotToggleAndNextTapWorks() = guardedGesture(false, -40f, 600)
    @Test fun flingStartingOnToggleDoesNotToggleAndNextTapWorks() = guardedGesture(false, -40f, 64)
    @Test fun pullStartingOnToggleDoesNotToggleAndNextTapWorks() = guardedGesture(false, 56f, 600)
    @Test fun scrollStartingOnSliderDoesNotSetValueAndNextTapWorks() = guardedGesture(true, -64f, 600)
    @Test fun flingStartingOnSliderDoesNotSetValueAndNextTapWorks() = guardedGesture(true, -64f, 64)
    @Test fun pullStartingOnSliderDoesNotSetValueAndNextTapWorks() = guardedGesture(true, 56f, 600)

    private fun scrollToggleWithoutAnotherDown(): SemanticsNodeInteraction {
        mountGestureFixture()
        val toggle = row("guarded toggle")
        toggle.performTouchInput {
            swipe(center, center - Offset(0f, px(40f)), durationMillis = 600)
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue("Precondition: pointer must scroll content", fixtureScroll.value > 0)
            assertFalse(checked)
            assertEquals(0, toggleChanges)
        }
        // Bring it fully back with semantics. This emits no pointer DOWN to reset the guard.
        return toggle.performScrollTo().assertIsOff()
    }

    @Test fun endedPointerScrollDoesNotBlockSemanticsToggleClick() {
        val toggle = scrollToggleWithoutAnotherDown()
        // Invoke the accessibility OnClick action, not a convenience action that can inject touch.
        toggle.performSemanticsAction(SemanticsActions.OnClick) { click -> assertTrue(click()) }
        toggle.assertIsOn()
        compose.runOnIdle { assertTrue(checked); assertEquals(1, toggleChanges) }
    }

    @Test fun endedPointerScrollDoesNotBlockFocusedEnterToggleClick() {
        val toggle = scrollToggleWithoutAnotherDown()
        compose.runOnIdle { assertTrue(inputMode.requestInputMode(InputMode.Keyboard)) }
        toggle.performSemanticsAction(SemanticsActions.RequestFocus) { focus -> assertTrue(focus()) }
        toggle.assertIsFocused()
        toggle.performKeyInput { pressKey(Key.Enter) }
        toggle.assertIsOn()
        compose.runOnIdle { assertTrue(checked); assertEquals(1, toggleChanges) }
    }

    private fun scrollBody() = compose.onNode(hasScrollAction())

    private fun dragAndHold(node: SemanticsNodeInteraction, dyDp: Float, duration: Long) {
        node.performTouchInput {
            val start = Offset(width * .8f, center.y)
            down(start)
            repeat(12) { step ->
                moveTo(start + Offset(0f, px(dyDp) * (step + 1) / 12), delayMillis = duration / 12)
            }
        }
        compose.waitForIdle()
    }

    private fun releaseAfterHold(node: SemanticsNodeInteraction) {
        // Sheet velocity uses real uptime as well as pointer time. Age both clocks explicitly.
        val heldAt = android.os.SystemClock.uptimeMillis()
        compose.waitUntil(timeoutMillis = 2_000) { android.os.SystemClock.uptimeMillis() - heldAt >= 160 }
        node.performTouchInput { advanceEventTime(160); up() }
        compose.waitForIdle()
    }

    @Test fun contentScrollThenContinuousPullClosesSheet() {
        mountGestureFixture()
        scrollBody().performTouchInput {
            swipe(center, center - Offset(0f, px(140f)), durationMillis = 600)
        }
        val before = compose.runOnIdle { fixtureScroll.value }
        assertTrue("Precondition: content must scroll", before > px(60f))
        scrollBody().performTouchInput {
            val start = Offset(center.x, px(40f))
            swipe(start, start + Offset(0f, before + px(150f)), durationMillis = 900)
        }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue("Pull after content reached top did not dismiss", dismissed) }
        text("GESTURE FIXTURE").assertDoesNotExist()
    }

    @Test fun downwardFlingThatReachesTopDoesNotDismissSheet() {
        mountGestureFixture()
        scrollBody().performTouchInput { swipe(center, center - Offset(0f, px(240f)), durationMillis = 700) }
        val before = compose.runOnIdle { fixtureScroll.value }
        assertTrue("Precondition: finger must release before the scroll reaches top", before > px(160f))
        scrollBody().performTouchInput {
            val start = Offset(center.x, px(40f))
            swipe(start, start + Offset(0f, px(100f)), durationMillis = 64)
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("Precondition: fling must actually reach top", 0, fixtureScroll.value)
            assertFalse("Fling dismissed the sheet", dismissed)
        }
        text("GESTURE FIXTURE").assertIsDisplayed()
    }

    @Test fun shortSlowPullSpringsBackWithoutClosing() {
        mountGestureFixture()
        val originalTop = text("GESTURE FIXTURE").fetchSemanticsNode().boundsInRoot.top
        val toggle = row("guarded toggle")
        dragAndHold(toggle, 56f, 700)
        val pulledTop = text("GESTURE FIXTURE").fetchSemanticsNode().boundsInRoot.top
        assertTrue("Precondition: short pull must displace sheet", pulledTop > originalTop + px(8f))
        releaseAfterHold(toggle)
        compose.runOnIdle { assertFalse(dismissed); assertEquals(0, toggleChanges) }
        val settledTop = text("GESTURE FIXTURE").fetchSemanticsNode().boundsInRoot.top
        assertEquals("Short pull did not return home", originalTop, settledTop, px(1f))
    }
}
