package dev.phosphor.mobil3.ui

import java.io.File
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

/** Behavior of the manual: reachable chapters, current labels, the developer split, Back. */
class ManualContentTest {
    private val everyday = ManualContent.visible(developer = false)
    private val all = ManualContent.visible(developer = true)

    @Test fun chaptersAreDistinctReadableAndKeepTheTurtle() {
        val chapters = ManualContent.chapters
        assertEquals(chapters.size, chapters.map { it.id }.toSet().size)
        assertEquals(chapters.size, chapters.map { it.response }.toSet().size)
        chapters.forEach {
            assertTrue(it.id.matches(Regex("[a-z-]+")))
            assertTrue(it.title.isNotBlank())
            assertTrue(it.text.length >= 80)
            assertTrue(it.response.isNotBlank())
            assertSame(it, ManualContent.chapter(it.id))
        }
        assertTrue(chapters.count { it.response.contains("turtle") } >= 2)
        assertTrue(everyday.size >= 12)
    }

    @Test fun everyChapterIsReachableByNavigationAndByItsTitle() {
        for (chapter in all) {
            assertEquals(chapter.id, ManualNavigation().open(chapter.id).chapterId)
            val found = ManualContent.search(chapter.title, developer = chapter.developer)
            assertTrue("title search reaches ${chapter.id}", found.any { it.id == chapter.id })
        }
    }

    @Test fun developerChaptersStayBehindTheDeveloperView() {
        val developer = ManualContent.chapters.filter { it.developer }
        assertTrue(developer.isNotEmpty())
        assertTrue(everyday.none { it.developer })
        assertEquals(ManualContent.chapters, all)
        for (chapter in developer) {
            assertFalse(ManualContent.search(chapter.title).any { it.id == chapter.id })
            assertTrue(ManualContent.search(chapter.title, developer = true).any { it.id == chapter.id })
        }
        // Engineering numbers live with developer chapters, not everyday help.
        listOf("dBFS", "ARGB32", "PCM", "kHz reconstruction").forEach { word ->
            assertTrue(everyday.none { it.text.contains(word) })
        }
    }

    @Test fun searchFindsEveryCurrentControlByItsVisibleLabel() {
        val labels = listOf(
            "auto size", "size", "view lock", "focus", "beam", "glow", "vary beam per track", "vary glow per track",
            "frame rate", "fps line", "keep screen bright", "HDR", "fullscreen", "keys always visible",
            "double tap to play", "when paused", "lock scope rotation", "lock key placement",
            "auto PiP", "floating HUD", "HUD background", "keep playing in background",
            "on launch", "ask for permission at launch", "relay latency",
            "save current setup", "settings file", "setups file", "apply",
            "open file", "open folder", "everything playing", "include mic", "playback level", "mic level",
            "track names", "microphone", "add relay", "desktop visualizer", "desktop sources", "desktop library",
            "disconnect", "random", "skip on", "geometry", "amount", "COLORS", "SAVED", "CYCLE", "each track",
            "order", "roll", "auto color", "allow faster", "feel", "motion", "corners", "labels", "CLASSIC",
        )
        for (label in labels) {
            assertTrue("search finds '$label'", ManualContent.search(label).isNotEmpty())
        }
    }

    @Test fun everydayHelpNeverNamesRetiredControls() {
        val retired = listOf("RECALL INSTRUMENT", "INSTRUMENT PRESETS", "SAVE CURRENT AS", "INCLUDE MIC", "BAN FACES",
            "LEG seconds", "SIGNAL & STARTUP", "DISPLAY & HUD", "BEAM & LIGHT", "MOTION & PERFORMANCE",
            "ABOUT & MANUAL", "SRC > LIVE", "Le random order", "ADD CURRENT PRESET COLOR", "expandable")
        for (chapter in everyday) for (name in retired) {
            assertFalse("${chapter.id} still names '$name'", chapter.text.contains(name))
        }
    }

    @Test fun emptyAndWhitespaceQueriesListTheVisibleChapters() {
        assertEquals(everyday, ManualContent.search(""))
        assertEquals(everyday, ManualContent.search("  \t\n "))
        assertEquals(all, ManualContent.search("", developer = true))
    }

    @Test fun searchMatchesEveryWordWithoutLocaleDependence() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertTrue(ManualContent.search("TAILSCALE RELAY").any { it.id == "remote" })
            assertTrue(ManualContent.search("GLOW PHOSPHOR").any { it.id == "inside" })
            assertTrue(ManualContent.search("glow impossiblewordzzz").isEmpty())
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
        val first = index.open("sources")
        val second = first.open("remote")
        assertEquals("sources", second.back().chapterId)
        assertNull(second.back().back().chapterId)
        assertEquals("source", second.back().back().query)
        assertEquals(index, second.index())
        assertEquals(index, index.back())
    }

    @Test fun searchReplacesHistoryAndNavigationRejectsUnknownIds() {
        val nav = ManualNavigation().open("start").open("sources")
        assertEquals(ManualNavigation().search("gain"), nav.search("gain"))
        assertThrows(IllegalArgumentException::class.java) { nav.open("not-a-chapter") }
        assertThrows(IllegalArgumentException::class.java) { ManualContent.chapter("") }
    }

    @Test fun longNavigationIsBoundedAndPublishedListsAreImmutable() {
        var nav = ManualNavigation()
        repeat(200) { nav = nav.open(if (it % 2 == 0) "sources" else "remote") }
        assertEquals(32, nav.history.size)
        val frozen = nav
        assertThrows(UnsupportedOperationException::class.java) { (frozen.history as MutableList<String>).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (ManualContent.chapters as MutableList<ManualChapter>).clear() }
        repeat(32) { nav = nav.back() }
        assertTrue(nav.history.isEmpty())
        assertNull(nav.back().chapterId)
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
}
