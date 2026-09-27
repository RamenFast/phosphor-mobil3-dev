package dev.phosphor.mobil3.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.semantics.heading
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

// One link row of the built-in viewer: title left, `open` right, the user's own browser.
@Composable
fun LinkCard(title: String, address: String, p: Palette, onOpen: () -> Unit) {
    SettingAction(title, p, hint = address, value = "open", onClick = onOpen)
}

// ── MANUAL: everyday help in the words on screen; developer chapters after the unlock.
//    Links leave through the user's preferred browser; nothing renders web content here. ──
@Composable
fun ManualSheet(
    p: Palette,
    reduced: Boolean,
    developer: Boolean = false,
    bestiaryFound: Boolean = false,
    onBestiaryFound: () -> Unit = {},
    onOpenLink: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = p.sheetText()
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
    CompositionLocalProvider(LocalSettingsControlAccess provides true) {
    SheetHost(p, "MANUAL", reduced, onDismiss, glyph = SettingsGlyph.About) {
        // System Back peels one layer: chapter → previous chapter or index → the sheet
        // the manual came from. Registered inside the sheet, so it wins over its close.
        BackHandler(enabled = navigation.chapterId != null) { navigation = navigation.back() }
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
                        .padding(top = 4.dp, bottom = 12.dp)
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
                    SettingAction("bestiary", p, value = if (showBestiary) "close" else "open") { showBestiary = !showBestiary }
                }
                if (bestiaryFound && showBestiary) Bestiary(p, rootDisclosure) { rootDisclosure = it }
                SearchField(navigation.query, p) { navigation = navigation.search(it) }
                val matches = ManualContent.search(navigation.query, developer)
                if (matches.isEmpty()) SettingNote("No chapter matches all those words. Try fewer, or a control's name.", p)
                matches.forEach { chapter ->
                    SettingAction(chapter.title, p) { navigation = navigation.open(chapter.id) }
                }
                GroupHeading("LINKS", p)
                LinkCard("privacy policy", "…/phosphor-mobil3/blob/master/PRIVACY.md", p) {
                    onOpenLink("https://github.com/RamenFast/phosphor-mobil3/blob/master/PRIVACY.md")
                }
                LinkCard("the source", "github.com/RamenFast/phosphor-mobil3", p) {
                    onOpenLink("https://github.com/RamenFast/phosphor-mobil3")
                }
                LinkCard("releases", "…/phosphor-mobil3/releases", p) {
                    onOpenLink("https://github.com/RamenFast/phosphor-mobil3/releases")
                }
                LinkCard("desktop phosphor", "github.com/RamenFast/phosphor", p) {
                    onOpenLink("https://github.com/RamenFast/phosphor")
                }
                LinkCard("license · GPL-3.0-or-later", "gnu.org/licenses/gpl-3.0", p) {
                    onOpenLink("https://www.gnu.org/licenses/gpl-3.0.html")
                }
                Prose("the beam remembers ∿", p.ink2, size = Type.hint, modifier = Modifier.padding(top = 12.dp))
            } else {
                KeyRow {
                    SheetKey("back", p) { navigation = navigation.back() }
                    SheetKey("index", p) { navigation = navigation.index() }
                }
                Mono(selected.title, p.ink, Type.label, Modifier.padding(top = 12.dp, bottom = 8.dp).semantics { heading() })
                Prose(selected.text, p.ink, size = Type.label, modifier = Modifier.padding(bottom = 12.dp))
                if (selected.id == "reading") ScopeFigures(p)
                Prose(selected.response, p.ink2, size = Type.hint, modifier = Modifier.padding(bottom = 12.dp))
                RowDivider(p)
                val list = ManualContent.visible(developer)
                val index = list.indexOf(selected)
                if (index > 0) SettingAction("previous · ${list[index - 1].title}", p) {
                    navigation = navigation.open(list[index - 1].id)
                }
                if (index in 0 until list.lastIndex) SettingAction("next · ${list[index + 1].title}", p) {
                    navigation = navigation.open(list[index + 1].id)
                }
            }
        }
    }
    }
}

@Composable
private fun SearchField(query: String, p: Palette, onChange: (String) -> Unit) {
    BasicTextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).heightIn(min = 48.dp)
            .border(Dim.hairline, p.lineStrong)
            .settingsFocusBorder(p)
            .semantics { contentDescription = "Search manual chapters" },
        singleLine = true,
        textStyle = TextStyle(color = p.ink, fontFamily = MonoFace, fontSize = Type.label),
        cursorBrush = SolidColor(p.accent),
        decorationBox = { inner ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(12.dp))
                SheetChromeMark(SheetChromeVector.Search, p.ink2, size = 16.dp)
                Box(Modifier.weight(1f).padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Mono("search", p.muted, Type.label, maxLines = 1)
                    inner()
                }
                if (query.isNotEmpty()) {
                    Box(
                        Modifier.size(48.dp)
                            .semantics { contentDescription = "Clear search" }
                            .clickable { onChange("") },
                        contentAlignment = Alignment.Center,
                    ) { SheetChromeMark(SheetChromeVector.Close, p.ink2, size = 14.dp) }
                }
            }
        },
    )
}

@Composable
private fun Bestiary(p: Palette, rootDisclosure: Boolean, onRoot: (Boolean) -> Unit) {
    GroupHeading("THE BESTIARY", p)
    Prose("four load-bearing beasts from a dead OS. little characters, big implications.",
        p.ink2, size = Type.hint, modifier = Modifier.padding(bottom = 8.dp))
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
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Mono(beast.title, p.ink, Type.label, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Mono(beast.tag, p.ink2, Type.value)
        }
        Prose(beast.line, p.ink2, size = Type.hint, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
    }
    // A little door to a future beam. It requests nothing and starts nothing.
    SettingAction("root capture · coming later", p, value = if (rootDisclosure) "close" else "peek") { onRoot(!rootDisclosure) }
    if (rootDisclosure) {
        SettingNote("A preview, not a switch. For now use SRC › everything playing, a file or the microphone.", p)
    }
    Prose("under the umbrella, everything is kind ☂", p.ink2, size = Type.hint, modifier = Modifier.padding(vertical = 12.dp))
}
