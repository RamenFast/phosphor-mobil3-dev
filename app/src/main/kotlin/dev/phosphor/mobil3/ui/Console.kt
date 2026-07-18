package dev.phosphor.mobil3.ui

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// ── Burn-in walk: persistent chrome drifts ±1 px on a slow orbit (60 s period) ──
@Composable
fun Modifier.burnInWalk(reduced: Boolean): Modifier {
    if (reduced) return this
    val t = rememberInfiniteTransition(label = "burnin")
    val phase by t.animateFloat(
        0f, 1f,
        InfiniteRepeatableSpec(tween(60_000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    val a = phase * 2f * Math.PI.toFloat()
    return this.offset {
        IntOffset(
            (Math.sin(a.toDouble()) * 1.5).roundToInt(),
            (Math.cos(a.toDouble() * 0.7) * 1.5).roundToInt(),
        )
    }
}

// ── Status band — read-only, mono, flanking the punch-hole. Never a tap target. ──
@Composable
fun StatusBand(state: ScopeUiState, p: Palette, reduced: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .burnInWalk(reduced),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val left = buildString {
            append("src · ")
            append(state.sourceLabel)
            if (state.noSignal) append("   ·   no signal")
        }
        androidx.compose.foundation.layout.Column {
            Mono(left, p.ink2.copy(alpha = 0.70f), Type.dataSm)
            if (state.nerdHud && state.hudLine.isNotBlank()) {
                Mono(state.hudLine, p.muted.copy(alpha = 0.8f), Type.dataXs)
            }
        }
        val gainTag = "×" + String.format("%.2f", state.gain) + if (state.autoGain) "·a" else ""
        Mono("${state.modeTag} · $gainTag", p.ink2.copy(alpha = 0.70f), Type.dataSm)
    }
}

// ── The seek rule: hairline track, square thumb, mono timestamps at the ends. ──
@Composable
fun SeekRule(
    p: Palette,
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
) {
    fun fmt(ms: Long): String {
        val s = ms / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }
    var scrub by remember { mutableFloatStateOf(-1f) }
    val frac =
        if (scrub >= 0f) scrub
        else if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Mono(fmt(if (scrub >= 0f) (frac * durationMs).toLong() else positionMs), p.muted, Type.dataXs)
        Box(
            Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .height(20.dp)
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> scrub = (o.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = {
                            if (scrub >= 0f && durationMs > 0) onSeek((scrub * durationMs).toLong())
                            scrub = -1f
                        },
                        onDragCancel = { scrub = -1f },
                    ) { change, _ ->
                        scrub = (change.position.x / size.width).coerceIn(0f, 1f)
                    }
                }
                .drawBehind {
                    val midY = size.height / 2f
                    drawLine(p.line, Offset(0f, midY), Offset(size.width, midY), 1.dp.toPx())
                    val x = frac.coerceIn(0f, 1f) * size.width
                    drawLine(p.accent, Offset(0f, midY), Offset(x, midY), 1.dp.toPx())
                    val half = 4.dp.toPx()
                    drawRect(
                        p.ink,
                        topLeft = Offset(x - half, midY - half),
                        size = androidx.compose.ui.geometry.Size(half * 2, half * 2),
                    )
                },
        )
        Mono(if (durationMs > 0) fmt(durationMs) else "–:––", p.muted, Type.dataXs)
    }
}

// ── Console strip — the summoned chrome band at the thumb. ──
@Composable
fun Console(
    state: ScopeUiState,
    p: Palette,
    reduced: Boolean,
    onMode: () -> Unit,
    onSrc: () -> Unit,
    onMore: () -> Unit,
    onPlay: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    val view = LocalView.current
    val hasTransport = state.trackTitle != null || state.remote
    Column(
        Modifier
            .fillMaxWidth()
            .background(p.surface.copy(alpha = Dim.consoleAlpha))
            .border(Dim.hairline, p.line)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = Dim.consolePadH, vertical = Dim.consolePadV)
            .burnInWalk(reduced),
    ) {
        state.trackTitle?.let { title ->
            Mono(
                buildString {
                    append(title)
                    state.trackArtist?.let {
                        if (it.isNotBlank() && it != "null") append("  —  $it")
                    }
                },
                p.ink, Type.dataLg,
                Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2200, velocity = 24.dp),
            )
            Spacer(Modifier.height(Dim.gap))
        }
        if (state.seekable && state.durationMs > 0) {
            SeekRule(p, state.positionMs, state.durationMs, onSeek)
            Spacer(Modifier.height(Dim.gap))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (hasTransport) {
                FlatKey("◂◂", p) { Haptics.light(view); onPrev() }
                Spacer(Modifier.width(Dim.gap))
            }
            StoneKey(if (state.playing) "❚❚" else "▶", p, reduced = reduced) {
                Haptics.light(view); onPlay()
            }
            if (hasTransport) {
                Spacer(Modifier.width(Dim.gap))
                FlatKey("▸▸", p) { Haptics.light(view); onNext() }
            }
            Spacer(Modifier.width(Dim.gapLg))
            FlatKey("MODE", p, onClick = onMode)
            Spacer(Modifier.width(Dim.gap))
            FlatKey("SRC", p, onClick = onSrc)
            Spacer(Modifier.weight(1f))
            FlatKey("⋯", p, onClick = onMore)
        }
    }
}

// ── The ⋯ overflow — an anchored popout, Obsidian-persistent. Rows only exist
//    once their feature is real (no dead chrome, honesty law). ──
@Composable
fun OverflowPopout(
    p: Palette,
    onDeck: () -> Unit,
    onLight: () -> Unit,
    onRoom: () -> Unit,
    onSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent()
                    }
                }
            },
    ) {}
    Column(
        Modifier
            .padding(horizontal = Dim.consolePadH)
            .background(p.surface.copy(alpha = Dim.sheetAlpha))
            .border(Dim.hairline, p.lineStrong)
            .padding(6.dp)
            .width(200.dp),
    ) {
        listOf(
            "deck" to onDeck,
            "light" to onLight,
            "room" to onRoom,
            "settings" to onSettings,
        ).forEach { (label, action) ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .clickable { action(); onDismiss() }
                    .padding(horizontal = 10.dp, vertical = 11.dp),
            ) { Mono(label, p.ink, Type.data) }
        }
    }
}
