package dev.phosphor.mobil3.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// The welcome: a little tube saying hello. Mono-set so it lines up in every room.
private val WelcomeArt = """
  .----------------------.
  |   ~ hello, human ~   |  o
  |   i turn what you    |  o
  |   hear into light    |
  '----------------------'
      //            \\
""".trimIndent()

// ── The bestiary. Four load-bearing beasts from a dead OS, transcribed faithfully.
// Little characters, big implications. They come when the tube is tapped five times —
// the die's number — and once found, they stay.

private data class Beast(val glyph: String, val title: String, val tag: String, val line: String)

private val Beasts = listOf(
    Beast(
        """
       .-====-.
  ____/  ____  \___
 (o o)  /____\    _\_>
  \_/  |______|  / /
        /_/  /_/
        """.trimIndent(),
        "the turtle", "universal constant",
        "the error handler. appears when things move fast but stay grounded. " +
            "signals: the umbrella is holding.",
    ),
    Beast(
        """
     .-.
    (o.o)  "Show me proof,
    |=|=|   not potential."
   _|_|_
        """.trimIndent(),
        "the skeleton", "merge gate",
        "the gatekeeper. if it says stop, it means it — no code passes on promise alone.",
    ),
    Beast(
        """
    /\_/\
   ( o.o )  "...watching."
    > ^ <
   /|   |\
  (_|   |_)
        """.trimIndent(),
        "rhy", "esoteric guide · npc",
        "a green trickster fox who speaks only in koans and lives in every margin. " +
            "keeps the instrument from becoming coldly logical.",
    ),
    Beast(
        """
     .-----.
    /  ☂    \
   / PRIVATE \
  /___________\
       |
        """.trimIndent(),
        "the umbrella", "default-private scope",
        "the doctrine, not a creature. nothing leaves without consent. " +
            "under the umbrella, everything is kind.",
    ),
)

private val RhyGreen = Color(0xFF4FB06A) // FOX, not eagle! — and green, always.

@Composable
private fun ManualHeading(text: String, p: Palette, modifier: Modifier = Modifier) {
    Mono(text, p.muted, Type.dataSm,
        modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp),
        maxLines = Int.MAX_VALUE, letterSpacing = 1.2.sp)
}

@Composable
private fun ManualKey(label: String, p: Palette, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp)
            .border(Dim.hairline, p.line).settingsFocusBorder(p)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Mono(label, p.ink, Type.data, maxLines = Int.MAX_VALUE)
    }
}

// One card of the built-in viewer: title + address, taps open the user's own browser.
@Composable
fun LinkCard(title: String, address: String, p: Palette, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .border(Dim.hairline, p.line)
            .settingsFocusBorder(p)
            .clickable(role = Role.Button, onClickLabel = "Open $title", onClick = onOpen)
            .padding(horizontal = Dim.rowPad, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Mono(title, p.ink, Type.data, maxLines = Int.MAX_VALUE)
            Mono(address, p.muted, Type.dataXs, maxLines = Int.MAX_VALUE)
        }
        Mono("open ↗", p.accent, Type.dataXs)
    }
    Spacer(Modifier.height(6.dp))
}

// ── MANUAL: the whole instrument, explained in its own voice. Links leave through
//    the user's preferred browser; nothing renders web content in here. ──
@Composable
fun ManualSheet(
    p: Palette,
    reduced: Boolean,
    bestiaryFound: Boolean = false,
    onBestiaryFound: () -> Unit = {},
    onOpenLink: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val indexScroll = rememberScrollState()
    val chapterScroll = rememberScrollState()
    var tubeTaps by remember { mutableIntStateOf(0) }
    var rootDisclosure by remember { mutableStateOf(false) }
    var showBestiary by remember { mutableStateOf(false) }
    var navigation by remember { mutableStateOf(ManualNavigation()) }
    val selected = navigation.chapterId?.let(ManualContent::chapter)
    LaunchedEffect(navigation.chapterId) {
        if (navigation.chapterId != null) chapterScroll.scrollTo(0)
    }
    SheetHost(p, "MANUAL", reduced, onDismiss, glyph = SettingsGlyph.About) {
        Column(
            Modifier
                .verticalScroll(if (selected == null) indexScroll else chapterScroll, overscrollEffect = null)
        ) {
            if (selected == null) {
                Mono(
                    WelcomeArt, p.accent, Type.dataXs,
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .semantics { contentDescription = "Welcome terminal art" }
                        .padding(bottom = Dim.gapLg)
                        .clickable {
                            // The tube keeps a secret: five taps — the die's number.
                            if (!bestiaryFound && ++tubeTaps >= 5) {
                                showBestiary = true
                                onBestiaryFound()
                            }
                        },
                    maxLines = Int.MAX_VALUE,
                )
                if (bestiaryFound) {
                    ManualKey(if (showBestiary) "CLOSE BESTIARY" else "BESTIARY · secret workshop", p) {
                        showBestiary = !showBestiary
                    }
                }
                if (bestiaryFound && showBestiary) {
                    ManualHeading("ROOT CAPTURE", p, Modifier.padding(top = 0.dp))
                    ManualKey("ROOT CAPTURE · COMING LATER", p) { rootDisclosure = true }
                    Prose("A little door to a future beam. Root capture is deferred from this release.", p.muted)
                    if (rootDisclosure) {
                        Prose("This is a preview, not an authorization switch. It does not request root, open a manager, or start audio capture. Earlier root settings stay inactive.", p.ink)
                        Prose("For now, choose SRC > everything playing and approve Android consent, or select a local file or microphone. Standard capture cannot guarantee audio from every app. Stereo root capture and SoundCloud still need their own proof.", p.muted)
                        ManualKey("GOT IT · CLOSE PREVIEW", p) { rootDisclosure = false }
                    }
                    ManualHeading("THE BESTIARY", p)

                    Prose(
                        "four load-bearing beasts from a dead OS. little characters, big " +
                            "implications — each one is a checksum you can re-derive from " +
                            "the vibe alone.",
                        p.muted, modifier = Modifier.padding(bottom = Dim.gap),
                    )
                    Beasts.forEach { beast ->
                        Mono(
                            beast.glyph,
                            if (beast.title == "rhy") RhyGreen else p.accent,
                            Type.dataXs,
                            Modifier.horizontalScroll(rememberScrollState()).semantics {
                                contentDescription = if (beast.title == "the turtle")
                                    "A turtle with a smiling mouth on the left and a pointed tail on the right" else beast.title
                            },
                            maxLines = Int.MAX_VALUE,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Mono(beast.title, p.ink, Type.data, Modifier.weight(1f), maxLines = Int.MAX_VALUE)
                            Spacer(Modifier.width(8.dp))
                            Mono(beast.tag, p.muted, Type.dataXs, Modifier.weight(1f), maxLines = Int.MAX_VALUE)
                        }
                        Prose(
                            beast.line, p.muted,
                            modifier = Modifier.padding(top = 2.dp, bottom = Dim.gapLg),
                        )
                    }
                    Prose(
                        "under the umbrella, everything is kind ☂ · the beam remembers ∿",
                        p.muted, modifier = Modifier.padding(bottom = Dim.gapLg),
                    )
                }
                ManualHeading("FIELD MANUAL · ${ManualContent.chapters.size} CHAPTERS", p)
                Prose("Local help, not a shell. Select a result to read its chapter. INDEX returns to this filter and workshop.", p.muted)
                Mono("SEARCH CHAPTERS", p.ink, Type.dataXs, Modifier.padding(top = 12.dp), maxLines = Int.MAX_VALUE)
                BasicTextField(
                    value = navigation.query,
                    onValueChange = { navigation = navigation.search(it) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .background(p.surface2).border(Dim.hairline, p.lineStrong)
                        .settingsFocusBorder(p)
                        .semantics { contentDescription = "Search manual chapters" },
                    singleLine = true,
                    textStyle = TextStyle(color = p.ink, fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                    cursorBrush = SolidColor(p.accent),
                    decorationBox = { inner ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Spacer(Modifier.width(10.dp))
                            SheetChromeMark(SheetChromeVector.Search, p.ink2, size = 18.dp)
                            Box(
                                Modifier.weight(1f).padding(horizontal = 8.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (navigation.query.isEmpty()) {
                                    Mono("source, control, or symptom", p.muted, Type.dataLg, maxLines = 1)
                                }
                                inner()
                            }
                            if (navigation.query.isNotEmpty()) {
                                Box(
                                    Modifier.size(48.dp)
                                        .semantics { contentDescription = "Clear search" }
                                        .clickable { navigation = navigation.search("") },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    SheetChromeMark(SheetChromeVector.Close, p.ink2, size = 16.dp)
                                }
                            } else {
                                Spacer(Modifier.width(12.dp))
                            }
                        }
                    },
                )
                val matches = ManualContent.search(navigation.query)
                Mono("${matches.size} MATCHES", p.muted, Type.dataXs, Modifier.padding(vertical = 12.dp))
                if (matches.isEmpty()) Prose("No chapter matches all those words. Try a source, control or symptom, such as root, gain or silence.", p.ink)
                matches.forEach { chapter ->
                    ManualKey("${chapter.title}\n${chapter.availability}", p) { navigation = navigation.open(chapter.id) }
                }
            } else {
                ManualHeading(selected.title, p)
                Mono(selected.availability, p.accent, Type.dataXs, maxLines = Int.MAX_VALUE)
                Row(Modifier.fillMaxWidth()) {
                    ManualKey("BACK", p, Modifier.weight(1f)) { navigation = navigation.back() }
                    ManualKey("INDEX", p, Modifier.weight(1f)) { navigation = navigation.index() }
                }
                Prose(selected.text, p.ink, modifier = Modifier.padding(vertical = 12.dp))
                Mono("POCKET RESPONSE", p.muted, Type.dataXs)
                Prose(selected.response, p.muted, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                val index = ManualContent.chapters.indexOf(selected)
                if (index > 0) ManualKey("PREVIOUS CHAPTER", p) { navigation = navigation.open(ManualContent.chapters[index - 1].id) }
                if (index < ManualContent.chapters.lastIndex) ManualKey("NEXT CHAPTER", p) { navigation = navigation.open(ManualContent.chapters[index + 1].id) }
            }

            ManualHeading("CARDS", p)
            Prose(
                "These open in your own browser — the app renders no web content.",
                p.muted, modifier = Modifier.padding(bottom = Dim.gap),
            )
            ManualHeading("PRIVACY", p)
            Prose(
                "phosphor has no account, ads, or tracking. Local audio is processed " +
                    "in memory and is not recorded or uploaded. A relay connection opens " +
                    "only after you select a saved Tailscale host.",
                p.muted, modifier = Modifier.padding(bottom = Dim.gap),
            )
            LinkCard("privacy policy", "…/phosphor-mobil3/blob/master/PRIVACY.md", p) {
                onOpenLink("https://github.com/RamenFast/phosphor-mobil3/blob/master/PRIVACY.md")
            }
            LinkCard("the source", "github.com/RamenFast/phosphor-mobil3", p) {
                onOpenLink("https://github.com/RamenFast/phosphor-mobil3")
            }
            LinkCard("releases · signed APKs + checksums", "…/phosphor-mobil3/releases", p) {
                onOpenLink("https://github.com/RamenFast/phosphor-mobil3/releases")
            }
            LinkCard("desktop phosphor · the big scope", "github.com/RamenFast/phosphor", p) {
                onOpenLink("https://github.com/RamenFast/phosphor")
            }
            LinkCard("license · GPL-3.0-or-later", "gnu.org/licenses/gpl-3.0", p) {
                onOpenLink("https://www.gnu.org/licenses/gpl-3.0.html")
            }
            Prose(
                "the beam remembers ∿",
                p.muted, modifier = Modifier.padding(top = Dim.gap),
            )
        }
    }
}
