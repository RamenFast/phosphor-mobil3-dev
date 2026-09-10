package dev.phosphor.mobil3.ui

import java.io.File
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class ManualContentTest {
    @Test fun chaptersAndResponsesAreDistinctAndSubstantial() {
        val chapters = ManualContent.chapters
        assertTrue(chapters.size >= 24)
        assertEquals(chapters.size, chapters.map { it.id }.toSet().size)
        assertEquals(chapters.size, chapters.map { it.response }.toSet().size)
        chapters.forEach {
            assertTrue(it.id.matches(Regex("[a-z-]+")))
            assertTrue(it.title.isNotBlank())
            assertTrue(it.availability.isNotBlank())
            assertTrue(it.text.length >= 150)
            assertTrue(it.response.length >= 25)
            assertSame(it, ManualContent.chapter(it.id))
        }
    }

    @Test fun indexCoversAllApprovedWorkstreamsAndSourceRecovery() {
        val expected = setOf("root", "mix", "hud", "hold", "inspect", "light", "random", "presets",
            "preset-recovery", "signal", "rails", "gesture", "sections", "appearance", "hdr",
            "brightness", "startup", "local", "relay", "capture", "mic", "privacy", "gain", "beam", "mode", "start",
            "manual", "grid", "rotation", "background", "performance", "motion", "appearance-edit",
            "appearance-recovery", "archives")
        assertEquals(expected, ManualContent.chapters.map { it.id }.toSet())
    }

    @Test fun emptyAndWhitespaceQueriesReturnTheWholeIndex() {
        assertEquals(ManualContent.chapters, ManualContent.search(""))
        assertEquals(ManualContent.chapters, ManualContent.search("  \t\n "))
    }

    @Test fun searchMatchesEveryWordAcrossBodyAndTitleWithoutLocaleDependence() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals(listOf("root"), ManualContent.search("SOUNDCLOUD stereo").map { it.id })
            assertEquals(listOf("rails"), ManualContent.search("RAILS analog").map { it.id })
            assertTrue(ManualContent.search("stereo impossiblewordzzz").isEmpty())
        } finally { Locale.setDefault(previous) }
    }

    @Test fun searchInputIsBoundedAndNeverEvaluated() {
        val query = "z".repeat(ManualContent.QUERY_LIMIT)
        assertEquals(query, ManualNavigation().search(query + " root").query)
        assertEquals(ManualContent.search(query), ManualContent.search(query + " root"))
        assertTrue(ManualContent.search("\$(rm -rf /); https://example.invalid/").isEmpty())
    }

    @Test fun backRestoresPriorChapterThenTheFilteredIndex() {
        val index = ManualNavigation().search("source")
        val first = index.open("local")
        val second = first.open("relay")
        assertEquals("local", second.back().chapterId)
        assertNull(second.back().back().chapterId)
        assertEquals("source", second.back().back().query)
        assertEquals(index, second.index())
        assertEquals(index, index.back())
    }

    @Test fun searchReplacesHistoryAndNavigationRejectsUnknownIds() {
        val nav = ManualNavigation().open("root").open("local")
        assertEquals(ManualNavigation().search("gain"), nav.search("gain"))
        assertThrows(IllegalArgumentException::class.java) { nav.open("not-a-chapter") }
        assertThrows(IllegalArgumentException::class.java) { ManualContent.chapter("") }
    }

    @Test fun longNavigationIsBoundedAndPublishedListsAreImmutable() {
        var nav = ManualNavigation()
        repeat(200) { nav = nav.open(if (it % 2 == 0) "local" else "relay") }
        assertEquals(32, nav.history.size)
        val frozen = nav
        assertThrows(UnsupportedOperationException::class.java) { (frozen.history as MutableList<String>).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (ManualContent.chapters as MutableList<ManualChapter>).clear() }
        repeat(32) { nav = nav.back() }
        assertTrue(nav.history.isEmpty())
        assertNull(nav.back().chapterId)
    }

    @Test fun pendingFeaturesAndRootEvidenceStayExplicit() {
        assertEquals("Route-dependent", ManualContent.chapter("mix").availability)
        listOf("hdr", "startup").forEach {
            assertEquals("Planned", ManualContent.chapter(it).availability)
        }
        val root = ManualContent.chapter("root")
        assertEquals("Deferred from this release", root.availability)
        assertTrue(root.text.contains("16 kHz mono"))
        assertTrue(root.text.contains("does not recover stereo"))
        assertTrue(root.text.contains("not yet accepted"))
        assertTrue(ManualContent.chapter("signal").text.contains("Unavailable is not zero"))
    }

    @Test fun operatingQuestionsReturnActionsAndRecoveryNotOnlyTopicIds() {
        answer("swipe up", "gesture", "from the play bar", "Close or system Back")
        answer("double tap", "gesture", "Settings > DISPLAY & HUD", "DOUBLE TAP PLAYBACK", "Disable it")
        answer("gain lock", "gain", "Settings > SIGNAL & STARTUP", "×0.1 to ×7.0", "manual control", "VIEW LOCK blocks gain gestures", "OPEN SOURCES")
        answer("focus range", "beam", "0.3 to 3.0 px", "×1 to ×30", "0 to 98 percent", "Uncheck", "plain BEAM or GLOW")
        answer("ban faces", "mode", "at least two", "Pick a face manually", "AMOUNT", "0 to 100 percent", "Select off")
        answer("grid data", "grid", "Settings > BEAM & LIGHT", "Turn off GRID DATA", "raw-channel peaks and dBFS", "set BAND to on", "SIGNAL CHECK")
        answer("frame rate", "performance", "60, 90, 120 · panel max and uncapped", "not the panel limit", "Choose 60 or 90")
        answer("beam rate", "performance", "120 · 48 kHz, 240 · 96 kHz and 480 · 192 kHz", "not panel refresh", "reduce that cost")
        answer("stats hud", "performance", "on, auto and off", "BAND off hides both", "set both BAND and STATS HUD to on", "console visibility", "do not show a FLOATING HUD")
        answer("fullscreen", "rotation", "Settings > DISPLAY & HUD", "Turn it off", "system bars")
        answer("rotation lock", "rotation", "UI PLACEMENT", "takes precedence", "Enable system auto-rotate")
        answer("auto pip", "hud", "ENTER PiP", "Turn it off", "without removing ENTER PiP")
        answer("floating hud", "hud", "SHOW FLOATING HUD", "HIDE FLOATING HUD", "TRANSPARENT or SOLID", "overlay access")
        answer("linger", "background", "removal from recents", "existing service-owned", "Established service-owned microphone", "SRC > LIVE")
        answer("pause display", "hold", "Settings > DISPLAY & HUD", "HOLD FRAME", "BLACK", "PAUSE DISPLAY ONLY", "RETURN DISPLAY TO LIVE")
        answer("reset inspection", "inspect", "pan or pinch", "Settings > DISPLAY & HUD > RESET INSPECTION", "no held frame", "return LIVE")
        answer("signal check", "signal", "OPEN SOURCES", "grant, retry or picker", "Unavailable is not zero")
        answer("reduced motion", "motion", "Android Remove animations", "MOTION CUT", "APPLY · persist draft", "takes precedence", "does not pause audio")
        answer("meaningful motion", "motion", "ongoing work", "hidden", "not a measured frame-pacing claim")
        answer("import settings", "archives", "ABOUT & MANUAL", "Read that status", "readable input or writable destination")
    }

    @Test fun lightAnswersNameRealPrecedenceBoundsAndSafeRecovery() {
        answer("saved colors", "light", "ADD CURRENT PRESET COLOR", "six RGB slots", "Select slot", "Edit slot", "Delete slot", "0 to 1")
        answer("one selected slot", "light", "Automatic generated color owns", "even with one selected slot", "Otherwise selected slots own",
            "fallback is the selected color under PRESETS", "P7 Green", "only when generated color and a temporary roll are inactive")
        answer("random interval", "random", "ROLL NOW", "next light edit", "Le random order", "Equal saved RGB values",
            "Minimum seconds", "Maximum seconds", "0.1 to 60", "other bound too")
        answer("track interval", "random", "TRACK holds color until track identity changes", "inactive", "values remain stored", "not a new track")
        answer("photosensitive", "random", "Below one second", "seizures", "Safe timing is already active", "KEEP SAFE",
            "I understand: allow faster", "Closing LIGHT also discards")
    }

    @Test fun plannedFeaturesProvideExistingAlternatives() {
        answer("accessory mix", "mix", "INCLUDE MIC", "visualization", "not speaker volume", "Bluetooth")
        answer("hdr", "hdr", "ordinary SDR remains the working output", "not HDR", "own combined proof")
        answer("screen brightness", "brightness", "DISPLAY & HUD > PIN SCREEN BRIGHTNESS", "off by default",
            "use Android's brightness control", "thermal", "focused full app", "checked on ASUS Zenfone 9", "physical luminance remain unverified")
        assertEquals("ASUS foreground and archive checks passed", ManualContent.chapter("brightness").availability)
        answer("startup", "startup", "open SRC", "choose the source manually", "not accepted controls")
    }

    @Test fun appearanceAnswersDescribeCurrentEditorWithoutPhoneAcceptanceClaims() {
        listOf("appearance", "appearance-edit", "appearance-recovery", "motion").forEach {
            assertEquals("Host-integrated, device acceptance pending", ManualContent.chapter(it).availability)
        }
        answer("appearance preview", "appearance", "Settings > APPEARANCE", "LOAD LIGHT DRAFT", "LOAD DARK DRAFT",
            "LOAD GLASS DRAFT", "LOAD AMOLED DRAFT", "without changing the look", "PREVIEW · temporary",
            "APPLY · persist draft", "CANCEL · restore committed appearance", "Leaving the editor cancels", "not compositor blur")
        answer("appearance named", "appearance-edit", "RRGGBB", "AARRGGBB", "0.25 to 2.0", "0.85 to 1.25", "integers 0 to 64",
            "0.2 to 1.0", "1 to 64 characters", "SAVE AS NEW NAMED APPEARANCE", "LOAD SAVED DRAFT", "SAVE DRAFT OVER",
            "RENAME", "DELETE retains active colors", "LOAD IMMUTABLE DRAFT", "A successful SAVE also applies and persists the draft")
        answer("appearance recovery", "appearance-recovery", "PROPOSE READABLE COLORS IN DRAFT · replaces all color roles",
            "draft only", "RESET TO AMOLED · keep saved appearances", "REPLACE UNAVAILABLE APPEARANCE WITH AMOLED",
            "RETRY AUTHORITATIVE APPEARANCE, THEN INSTRUMENT SAVE", "Device acceptance remains pending")
        assertFalse(ManualContent.chapter("appearance").text.contains("integration is in development"))
    }

    @Test fun helpLabelsAndRangesAreLinkedToCurrentControlSource() {
        val settings = source("ui/Sheets.kt")
        listOf("SIGNAL & STARTUP", "BEAM & LIGHT", "DISPLAY & HUD", "MOTION & PERFORMANCE", "ABOUT & MANUAL",
            "DOUBLE TAP PLAYBACK", "CONTROLS ALWAYS VISIBLE", "BACKGROUND LINGER", "SCOPE ROTATION", "UI PLACEMENT",
            "PAUSE DISPLAY ONLY", "RETURN DISPLAY TO LIVE", "RESET INSPECTION", "STATS HUD", "BAND", "GRID DATA").forEach {
            assertTrue("Current Settings label: $it", settings.contains(it))
            assertTrue("Searchable Settings label: $it", ManualContent.search(it).isNotEmpty())
        }
        listOf("\"GAIN\", state.gain, 0.1f, 7.0f", "\"FOCUS\", focusValue, 0.3f, 3.0f",
            "\"BEAM\", state.beamEnergy, 1.0f, 30.0f", "\"GLOW\", state.glow, 0.0f, 0.98f").forEach {
            assertTrue("Current range: $it", settings.contains(it))
        }
        val light = source("ui/LightSheet.kt")
        listOf("ADD CURRENT PRESET COLOR", "Automatic generated color", "Le random order", "ROLL NOW",
            "Random interval", "Minimum seconds", "Maximum seconds", "LEG seconds", "KEEP SAFE", "I understand: allow faster").forEach {
            assertTrue("Current LIGHT label: $it", light.contains(it))
            assertTrue("Searchable LIGHT label: $it", ManualContent.search(it).isNotEmpty())
        }
        assertTrue(light.contains("light.intervalMin, 0.1f, 60f"))
        assertTrue(light.contains("light.intervalMax, 0.1f, 60f"))
        assertTrue(light.contains("light.seconds, 0.1f, 60f"))
        val owner = light.substringAfter("val owner = when {").substringBefore("Prose(owner")
        assertTrue(owner.indexOf("light.generatedAuto") < owner.indexOf("light.selectedMask != 0"))
        assertTrue(owner.indexOf("light.selectedMask != 0") < owner.indexOf("else ->"))
        assertTrue(source("ui/SignalCheckSheet.kt").contains("SignalCheckAction(\"OPEN SOURCES\""))
        val editor = source("ui/AppearanceEditor.kt")
        listOf("PREVIEW · temporary", "APPLY · persist draft", "CANCEL · restore committed appearance",
            "SAVE AS NEW NAMED APPEARANCE", "LEGACY SNAPSHOTS", "LOAD IMMUTABLE DRAFT",
            "PROPOSE READABLE COLORS IN DRAFT · replaces all color roles", "RESET TO AMOLED · keep saved appearances",
            "REPLACE UNAVAILABLE APPEARANCE WITH AMOLED", "RETRY AUTHORITATIVE APPEARANCE, THEN INSTRUMENT SAVE").forEach {
            assertTrue("Current appearance label: $it", editor.contains(it))
            assertTrue("Searchable appearance label: $it", ManualContent.search(it).isNotEmpty())
        }
    }

    @Test fun chapterViewportStartsAtHeadingAndQueryDoesNotOwnScrolling() {
        val source = sheet()
        assertTrue(source.contains("val indexScroll = rememberScrollState()"))
        assertTrue(source.contains("val chapterScroll = rememberScrollState()"))
        assertTrue(source.contains(".verticalScroll(if (selected == null) indexScroll else chapterScroll"))
        val effect = source.substringAfter("LaunchedEffect(").substringBefore("SheetHost(")
        assertTrue(effect.startsWith("navigation.chapterId)"))
        assertTrue(effect.contains("if (navigation.chapterId != null) chapterScroll.scrollTo(0)"))
        assertFalse(effect.contains("query"))
        assertFalse(source.contains("indexScroll.scrollTo"))
        assertEquals(1, Regex("scrollTo\\(").findAll(source).count())
        assertEquals(1, Regex("LaunchedEffect\\(").findAll(source).count())
        assertTrue(source.contains("if (selected == null) {\n                Mono("))
        assertTrue(source.contains("} else {\n                ManualHeading(selected.title, p)"))
        listOf("delay(", "while (", "BringIntoViewRequester", "animateScrollTo").forEach { assertFalse(source.contains(it)) }
    }

    @Test fun gestureDiagnosticRateAndPresetAnswersMatchImplementedOwners() {
        val screen = source("ui/PhosphorScreen.kt")
        assertTrue(screen.contains("override fun gainLocked() = state.viewLock"))
        assertTrue(screen.contains("val bandShown = state.bandMode == 0 || (state.bandMode == 1 && consoleShown)"))
        assertTrue(screen.contains("if (bandShown && sheet == Sheet.NONE)"))
        assertTrue(screen.contains("onHeightChanged = { statusBandHeightPx = it }"))
        assertTrue(screen.contains("hudVisible = state.hudMode == 0 ||"))
        assertTrue(source("ui/Console.kt").contains("if (state.gridData)"))
        val gestures = source("ui/Gestures.kt")
        assertTrue(gestures.contains("host.modeStep(if (travel.x < 0f) 1 else -1)"))
        assertTrue(gestures.contains("host.setGlowAbsolute(glow)"))
        assertTrue(gestures.contains("host.orbitBy(d.x * 0.006f, d.y * 0.006f)"))
        answer("two-finger", "gesture", "left or right", "next or previous mode", "increase or decrease GLOW")
        answer("3d pinch", "gesture", "one-finger drag orbits", "pinch dollies")
        val rates = source("ui/ScopeModel.kt")
        listOf("60", "90", "120 · panel max", "uncapped", "120 · 48 kHz", "240 · 96 kHz", "480 · 192 kHz").forEach {
            assertTrue("Current rate choice: $it", rates.contains("\"$it\""))
            assertTrue("Documented rate choice: $it", ManualContent.chapter("performance").text.contains(it))
        }
        val motion = source("ui/Motion.kt")
        assertTrue(motion.contains("Settings.Global.TRANSITION_ANIMATION_SCALE"))
        assertTrue(motion.contains("Settings.Global.ANIMATOR_DURATION_SCALE"))
        val presets = source("ui/InstrumentPresetSheet.kt")
        listOf("RECALL INSTRUMENT", "SAVE CURRENT AS…", "SAVE NEW SETUP", "DUPLICATE AS…", "UPDATE WITH CURRENT AUTHORED SETUP",
            "KEEP RECORD", "UNDO LAST APPLY", "IMPORT PREVIEW", "SAVE RESOLVED IMPORT · DOES NOT APPLY", "CANCEL PREVIEW",
            "CANCEL PENDING APPLY", "RETRY SAVE CURRENT · TUNING UNCHANGED", "RESTORE DISPLAYED SETUP", "KEEP SAFE TIMING").forEach {
            val actual = if (it == "RECALL INSTRUMENT") source("ui/LightSheet.kt") else presets
            assertTrue("Current preset action: $it", actual.contains(it))
            assertTrue("Searchable preset action: $it", ManualContent.search(it).isNotEmpty())
        }
        answer("preset import", "presets", "does not apply a setup", "CANCEL PREVIEW", "KEEP RECORD", "UNDO LAST APPLY")
        answer("preset recovery", "preset-recovery", "CANCEL PENDING APPLY", "RESTORE DISPLAYED SETUP", "not transport")
    }

    @Test fun rootPreviewIsLocalDismissibleAndHasNoOperationalCallbacks() {
        val source = sheet()
        val button = source.substringAfter("private fun ManualKey(").substringBefore("// One card")
        listOf("heightIn(min = 48.dp)", "settingsFocusBorder(p)", "maxLines = Int.MAX_VALUE",
            "role = Role.Button").forEach { assertTrue(button.contains(it)) }
        assertTrue(source.contains("ManualKey(\"ROOT CAPTURE · COMING LATER\", p) { rootDisclosure = true }"))
        assertTrue(source.contains("ManualKey(\"GOT IT · CLOSE PREVIEW\", p) { rootDisclosure = false }"))
        assertFalse(source.contains("onRootCapture(true)"))
        assertFalse(source.contains("onRootCapture(false)"))
        assertFalse(source.contains("onRootManager()"))
        assertFalse(source.contains("ManualRootToggle"))
        val root = ManualContent.chapter("root")
        assertTrue(root.text.contains("Earlier root-enabled settings stay inactive"))
        assertTrue(root.text.contains("does not request root"))
        assertTrue(root.text.contains("approve Android consent"))
    }

    @Test fun browserFailureHasVisibleExactUrlRecoveryWithoutRetry() {
        val adapter = source("MainActivity.kt").substringAfter("override fun openLink(url: String) {")
            .substringBefore("private fun refreshCaptureMetadataAccess")
        assertEquals(1, Regex("startActivity\\(").findAll(adapter).count())
        listOf("Intent.ACTION_VIEW, url.toUri()", ".onFailure {", "android.app.AlertDialog.Builder(this)",
            "Browser could not open", "Enable a browser in Android Settings > Apps", "Open this URL in that browser:",
            "\\n\\n\$url", ".setPositiveButton(\"CLOSE\", null)", ".show()").forEach {
            assertTrue("Browser recovery: $it", adapter.contains(it))
        }
        listOf("WebView", "HttpClient", "postDelayed", "retry", "openLink(url)").forEach { assertFalse(adapter.contains(it)) }
    }

    @Test fun actualSheetKeepsDiscoveryAndBestiaryArt() {
        val source = sheet()
        assertTrue(source.contains("!bestiaryFound && ++tubeTaps >= 5"))
        assertTrue(source.contains("onBestiaryFound()"))
        assertTrue(source.contains("bestiaryFound && showBestiary"))
        assertTrue(source.contains("A turtle with a smiling mouth on the left and a pointed tail on the right"))
        assertTrue(source.contains("\\_/  |______|"))
        assertTrue(source.contains("_\\_>"))
    }

    @Test fun actualSheetUsesNonExecutableWrappingSearchAndNavigation() {
        val source = sheet()
        listOf("navigation.search(it)", "navigation.back()", "navigation.index()", "ManualContent.search(navigation.query)",
            "Search manual chapters", "No chapter matches", "heightIn(min = 48.dp)", "settingsFocusBorder(p)",
            "horizontalScroll(rememberScrollState())", "maxLines = Int.MAX_VALUE", "role = Role.Button").forEach {
            assertTrue(it, source.contains(it))
        }
        listOf("ProcessBuilder", "Runtime.getRuntime", "WebView", "HttpClient").forEach { assertFalse(source.contains(it)) }
        assertTrue(source.contains("ManualHeading(selected.title, p)"))
        assertFalse(source.contains("SectionHeading("))
        assertTrue(source.substringAfter("private fun ManualHeading(").substringBefore("private fun ManualKey(")
            .contains("maxLines = Int.MAX_VALUE"))
        assertTrue(source.indexOf("Prose(selected.text") < source.indexOf("Prose(selected.response"))
    }

    @Test fun existingExternalDestinationsRemainExplicitCallbacksOnly() {
        val expected = setOf("https://github.com/RamenFast/phosphor-mobil3/blob/master/PRIVACY.md",
            "https://github.com/RamenFast/phosphor-mobil3", "https://github.com/RamenFast/phosphor-mobil3/releases",
            "https://github.com/RamenFast/phosphor", "https://www.gnu.org/licenses/gpl-3.0.html")
        val actual = Regex("onOpenLink\\(\"([^\"]+)\"\\)").findAll(sheet()).map { it.groupValues[1] }.toSet()
        assertEquals(expected, actual)
    }

    private fun sheet(): String {
        return source("ui/ManualSheet.kt")
    }

    private fun source(path: String): String {
        val relative = "src/main/kotlin/dev/phosphor/mobil3/$path"
        return listOf(File(relative), File("app/$relative")).first { it.isFile }.readText()
    }

    private fun answer(query: String, id: String, vararg content: String) {
        val chapter = ManualContent.search(query).firstOrNull { it.id == id }
        assertNotNull("Search '$query' must return $id", chapter)
        content.forEach { assertTrue("Answer '$query' must explain '$it'", chapter!!.text.contains(it)) }
    }
}
