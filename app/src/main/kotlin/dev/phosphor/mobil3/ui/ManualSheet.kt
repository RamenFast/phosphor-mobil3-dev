package dev.phosphor.mobil3.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
    rootEnabled: Boolean = false,
    rootBusy: Boolean = false,
    rootStatus: String = "",
    onRootCapture: (Boolean) -> Unit = {},
    onRootManager: () -> Unit = {},
    onOpenLink: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scroll = rememberScrollState()
    var tubeTaps by remember { mutableIntStateOf(0) }
    var rootDisclosure by remember { mutableStateOf(false) }
    var showBestiary by remember { mutableStateOf(false) }
    var navigation by remember { mutableStateOf(ManualNavigation()) }
    LaunchedEffect(navigation.chapterId, navigation.query) { scroll.scrollTo(0) }
    SheetHost(p, "MANUAL", reduced, onDismiss, glyph = SettingsGlyph.About) {
        Column(
            Modifier
                .verticalScroll(scroll, overscrollEffect = null)
        ) {
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
                SheetRow("ROOT CAPTURE", p, checked = rootEnabled) {
                    if (rootEnabled || rootBusy) onRootCapture(false) else rootDisclosure = true
                }
                Prose(rootStatus, p.muted)
                if (rootDisclosure) {
                    Prose("This uses your existing KernelSU authorization, not an Android recording prompt. The app stays unprivileged. Only KernelSU 32525 / UAPI 2 / flags 5 with an already verified Default/inherited profile is supported. Grant applies that profile. Phosphor does not change it.", p.ink)
                    Prose("Confirm that existing profile before checking authorization. Capture stays private at 16 kHz mono, duplicated into the scope channels. This does not recover stereo or bypass NO_SYSTEM_CAPTURE. Denial requires an existing manager grant, then an explicit retry.", p.muted)
                    ManualKey("PROFILE CONFIRMED · CHECK AUTHORIZATION", p) {
                        rootDisclosure = false
                        onRootCapture(true)
                    }
                    ManualKey("NOT NOW", p) { rootDisclosure = false }
                }
                ManualKey("OPEN ROOT MANAGER", p) { onRootManager() }
                if (rootBusy) ManualKey("CANCEL CHECK", p) { onRootCapture(false) }
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
            Prose("Local help, not a shell. Practical instructions first; pocket creatures at the foot of each chapter.", p.muted)
            Mono("SEARCH CHAPTERS", p.ink, Type.dataXs, Modifier.padding(top = 12.dp), maxLines = Int.MAX_VALUE)
            BasicTextField(
                value = navigation.query,
                onValueChange = { navigation = navigation.search(it) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .background(p.surface2).border(Dim.hairline, p.lineStrong)
                    .settingsFocusBorder(p).padding(12.dp)
                    .semantics { contentDescription = "Search manual chapters" },
                singleLine = true,
                textStyle = TextStyle(color = p.ink, fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                cursorBrush = SolidColor(p.accent),
            )
            if (navigation.query.isNotEmpty()) ManualKey("CLEAR SEARCH", p) { navigation = navigation.search("") }
            val selected = navigation.chapterId?.let(ManualContent::chapter)
            if (selected == null) {
                val matches = ManualContent.search(navigation.query)
                Mono("${matches.size} MATCHES", p.muted, Type.dataXs, Modifier.padding(vertical = 12.dp))
                if (matches.isEmpty()) Prose("No chapter matches all those words. Try a source, control or symptom, such as root, gain or silence.", p.ink)
                matches.forEach { chapter ->
                    ManualKey("${chapter.title}\n${chapter.availability}", p) { navigation = navigation.open(chapter.id) }
                }
            } else {
                Row(Modifier.fillMaxWidth()) {
                    ManualKey("BACK", p, Modifier.weight(1f)) { navigation = navigation.back() }
                    ManualKey("INDEX", p, Modifier.weight(1f)) { navigation = navigation.index() }
                }
                ManualHeading(selected.title, p)
                Mono(selected.availability, p.accent, Type.dataXs, maxLines = Int.MAX_VALUE)
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
