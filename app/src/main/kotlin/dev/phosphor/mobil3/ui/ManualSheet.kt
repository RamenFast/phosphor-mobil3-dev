package dev.phosphor.mobil3.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
    ___
   / o \   "this is fine"
  |  ._. |
   \___/
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

// One card of the built-in viewer: title + address, taps open the user's own browser.
@Composable
fun LinkCard(title: String, address: String, p: Palette, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .border(Dim.hairline, p.line)
            .clickable { onOpen() }
            .padding(horizontal = Dim.rowPad, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Mono(title, p.ink, Type.data)
            Mono(address, p.muted, Type.dataXs)
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
    val scroll = rememberScrollState()
    var tubeTaps by remember { mutableIntStateOf(0) }
    SheetHost(p, "MANUAL", reduced, onDismiss, glyph = SettingsGlyph.About) {
        Column(
            Modifier
                .bottomBloomOverscroll { !scroll.canScrollForward }
                .verticalScroll(scroll, overscrollEffect = null)
        ) {
            Mono(
                WelcomeArt, p.accent, Type.dataXs,
                Modifier
                    .padding(bottom = Dim.gapLg)
                    .clickable {
                        // The tube keeps a secret: five taps — the die's number.
                        if (!bestiaryFound && ++tubeTaps >= 5) onBestiaryFound()
                    },
                maxLines = Int.MAX_VALUE,
            )
            if (bestiaryFound) {
                SectionHeading("THE BESTIARY", p, Modifier.padding(top = 0.dp))
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
                        maxLines = Int.MAX_VALUE,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Mono(beast.title, p.ink, Type.data)
                        Spacer(Modifier.width(8.dp))
                        Mono(beast.tag, p.muted, Type.dataXs)
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
            Prose(
                "phosphor is a CRT oscilloscope in your pocket: the same beam physics as " +
                    "the desktop instrument, sample-locked to what you hear. Tap the glass " +
                    "to summon the console; everything below lives one or two taps deep.",
                p.ink, modifier = Modifier.padding(bottom = Dim.gapLg),
            )

            SectionHeading("SOURCES", p, Modifier.padding(top = 0.dp))
            Prose(
                "SRC picks what the beam listens to: your own files (one, or a folder as " +
                    "a queue), the microphone, another machine over your own network " +
                    "(REMOTE), or EVERYTHING PLAYING — the sound other apps make.",
                p.muted, modifier = Modifier.padding(bottom = Dim.gap),
            )

            SectionHeading("THE SCREEN-SHARE QUESTION", p)
            Prose(
                "When you pick “everything playing”, Android asks you to SHARE YOUR " +
                    "SCREEN. That's the system's blanket wording for media capture — " +
                    "phosphor takes only the AUDIO stream and turns it into light. " +
                    "Nothing is recorded, nothing leaves your phone, no data is taken. " +
                    "Some DRM apps opt out and arrive as silence; Spotify currently " +
                    "works. Track names and cover art additionally want NOTIFICATION " +
                    "ACCESS (a different switch than “allow notifications” — SOURCE " +
                    "has a grant… key that opens the right one).",
                p.muted, modifier = Modifier.padding(bottom = Dim.gap),
            )

            SectionHeading("THE GLASS", p)
            Prose(
                "MODE holds 11 faces — XY figures, a 3D attractor, waveforms, spectra. " +
                    "The ⚄ die rolls a new face on every track; BAN FACES strikes the " +
                    "ones you're tired of (at least two stay in play). GEOMETRY bends " +
                    "the beam after any face draws it: kaleido, spin, tunnel, pulse — " +
                    "spin and pulse ride the loudness of the music.",
                p.muted, modifier = Modifier.padding(bottom = Dim.gap),
            )

            SectionHeading("THE BENCH", p)
            Prose(
                "SETTINGS is the instrument bench: FOCUS, GAIN (auto-gain glides it, " +
                    "VIEW LOCK pins the zoom), BEAM brightness and GLOW persistence. " +
                    "The ⚄ checkboxes under BEAM and GLOW arm dice that re-roll inside " +
                    "your kept range on every track — uncheck to disarm, the last roll " +
                    "stays. Pinch zooms the figure; the grid rides along.",
                p.muted, modifier = Modifier.padding(bottom = Dim.gap),
            )

            SectionHeading("LIGHT & ROOMS", p)
            Prose(
                "LIGHT picks the phosphor color — presets or up to three custom slots " +
                    "that can cycle on a timer or once per track. ROOM changes the whole " +
                    "chrome character; thirteen rooms, each with its own temperament.",
                p.muted, modifier = Modifier.padding(bottom = Dim.gap),
            )

            SectionHeading("HOLDING IT", p)
            Prose(
                "Portrait and landscape both work. SCOPE ROTATION · locked pins the " +
                    "figure's orientation; UI PLACEMENT · locked pins the chrome where " +
                    "it is and the icons stay upright to gravity. Swipe up on the play " +
                    "bar for SETTINGS; swipe down closes any sheet.",
                p.muted, modifier = Modifier.padding(bottom = Dim.gapLg),
            )

            SectionHeading("CARDS", p)
            Prose(
                "These open in your own browser — the app renders no web content.",
                p.muted, modifier = Modifier.padding(bottom = Dim.gap),
            )
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
