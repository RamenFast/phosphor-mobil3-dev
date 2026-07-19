package dev.phosphor.mobil3.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

// ── DECK (spec §2.2): now-playing + the queue. The carved play stone that lives here
//    is the SAME control as the console's — one identity, travelled up. ──
@Composable
fun DeckSheet(
    state: ScopeUiState,
    p: Palette,
    reduced: Boolean,
    onPlay: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onJump: (Int) -> Unit,
    volumeFrac: () -> Float,
    onVolume: (Float) -> Unit,
    onOpenFolder: () -> Unit,
    onDismiss: () -> Unit,
) {
    val view = LocalView.current
    SheetHost(p, "DECK · " + (if (state.remote) "remote" else "phosphor"), reduced, onDismiss, glyph = SettingsGlyph.Deck) {
        // ── Half: now playing ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            val art = state.artwork
            val bmp = remember(art) {
                art?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull() }
            }
            if (bmp != null) {
                Image(
                    bmp.asImageBitmap(), contentDescription = null,
                    modifier = Modifier.size(88.dp).border(Dim.hairline, p.lineStrong),
                )
            } else {
                Box(
                    Modifier.size(88.dp).border(Dim.hairline, p.line),
                    contentAlignment = Alignment.Center,
                ) { ModeGlyph(state.modeIndex, p.muted) }
            }
            Spacer(Modifier.width(Dim.gapLg))
            Column(Modifier.weight(1f)) {
                Mono(state.trackTitle ?: "nothing loaded", p.ink, Type.dataLg, maxLines = 2)
                state.trackArtist?.takeIf { it.isNotBlank() && it != "null" }?.let {
                    Mono(it, p.ink2, Type.data, Modifier.padding(top = 2.dp))
                }
                if (state.queueTitles.isNotEmpty()) {
                    Mono(
                        "${state.queueIndex + 1} / ${state.queueTitles.size}",
                        p.muted, Type.dataXs, Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(Dim.gapLg))
        if (state.seekable && state.durationMs > 0) {
            SeekRule(p, state.positionMs, state.durationMs, onSeek)
            Spacer(Modifier.height(Dim.gap))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            FlatKey("◂◂", p) { Haptics.light(view); onPrev() }
            Spacer(Modifier.width(Dim.gap))
            StoneKey(if (state.playing) "❚❚" else "▶", p, reduced = reduced) {
                Haptics.light(view); onPlay()
            }
            Spacer(Modifier.width(Dim.gap))
            FlatKey("▸▸", p) { Haptics.light(view); onNext() }
            Spacer(Modifier.width(Dim.gapLg))
            // Volume rule — cubic taper (spec): the readout is honest system volume.
            DragRuleInline(
                value = volumeFrac(), p = p,
                format = { "${(it * 100).toInt()} %" },
                modifier = Modifier.weight(1f),
            ) { onVolume(it) }
        }

        // ── Full: the queue ──
        Spacer(Modifier.height(Dim.gapLg))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SectionHeading("QUEUE", p, Modifier.padding(top = 0.dp))
            FlatKey("OPEN…", p) { onOpenFolder() }
        }
        if (state.queueTitles.isEmpty()) {
            Prose(
                "open a folder — it becomes a gapless queue, in album order",
                p.muted, modifier = Modifier.padding(top = Dim.gap),
            )
        } else {
            LazyColumn(Modifier.height(260.dp)) {
                itemsIndexed(state.queueTitles) { i, title ->
                    val active = i == state.queueIndex
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .border(Dim.hairline, if (active) p.accent else p.line)
                            .clickable { onJump(i) }
                            .padding(horizontal = Dim.rowPad, vertical = 9.dp),
                    ) {
                        Mono("%02d".format(i + 1), if (active) p.accent else p.muted, Type.dataXs)
                        Spacer(Modifier.width(Dim.gapLg))
                        Mono(title, if (active) p.accent else p.ink, Type.data)
                    }
                    Spacer(Modifier.height(5.dp))
                }
            }
        }
    }
}

// A compact inline drag rule (no label column) for tight rows like the volume.
@Composable
fun DragRuleInline(
    value: Float,
    p: Palette,
    format: (Float) -> String,
    modifier: Modifier = Modifier,
    onChange: (Float) -> Unit,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            DragRule("♪", value, 0f, 1f, p, format, onChange)
        }
    }
}
