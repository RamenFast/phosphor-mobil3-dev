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
            "brightness", "startup", "local", "relay", "capture", "mic", "privacy", "gain", "beam", "mode", "start")
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
        listOf("mix", "hdr", "brightness", "startup").forEach {
            assertEquals("Planned", ManualContent.chapter(it).availability)
        }
        val root = ManualContent.chapter("root")
        assertEquals("Experimental", root.availability)
        assertTrue(root.text.contains("16 kHz mono"))
        assertTrue(root.text.contains("does not recover stereo"))
        assertTrue(root.text.contains("not yet accepted"))
        assertTrue(ManualContent.chapter("signal").text.contains("Unavailable is not zero"))
    }

    @Test fun actualSheetKeepsDiscoveryAndExplicitRootCallbacks() {
        val source = sheet()
        assertTrue(source.contains("!bestiaryFound && ++tubeTaps >= 5"))
        listOf("onBestiaryFound()", "onRootCapture(true)", "onRootCapture(false)", "onRootManager()").forEach {
            assertTrue(it, source.contains(it))
        }
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
        val relative = "src/main/kotlin/dev/phosphor/mobil3/ui/ManualSheet.kt"
        return listOf(File(relative), File("app/$relative")).first { it.isFile }.readText()
    }
}
