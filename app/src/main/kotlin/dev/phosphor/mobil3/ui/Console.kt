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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
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
fun StatusBand(state: ScopeUiState, p: Palette, reduced: Boolean, hudVisible: Boolean) {
    val landscape = LocalChromeLandscape.current
    Box(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(chromeSafeDrawingInsets(16.dp, 6.dp))
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .burnInWalk(reduced),
    ) {
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .then(
                    if (landscape) Modifier.widthIn(max = Dim.landscapeBandMaxWidth)
                        .fillMaxWidth()
                    else Modifier.fillMaxWidth()
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val left = buildString {
                append("src · ")
                append(state.sourceLabel)
                if (state.noSignal) append("   ·   no signal")
            }
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                Mono(left, p.ink2.copy(alpha = 0.70f), Type.dataSm)
                if (state.gridData) {
                    Mono(GridData.line(state.gridReading, left = true), p.ink2, Type.dataXs)
                    Mono(GridData.line(state.gridReading, left = false), p.ink2, Type.dataXs)
                }
                if (hudVisible && state.hudLine.isNotBlank()) {
                    Mono(state.hudLine, p.muted.copy(alpha = 0.8f), Type.dataXs)
                }
                if (hudVisible && state.hudLine2.isNotBlank()) {
                    Mono(state.hudLine2, p.muted.copy(alpha = 0.8f), Type.dataXs)
                }
            }
            // Remote geometry reports the desktop mode and measured gain, not the idle local renderer.
            val rolledMark = if (state.randomModeArmed) " ⚄" else ""
            val right = state.remoteScopeLine?.let { remoteTruth ->
                if (rolledMark.isEmpty()) remoteTruth
                else remoteTruth.replaceFirst(" ·", "$rolledMark ·")
            } ?: run {
                val gainTag = "×" + String.format(Locale.ROOT, "%.2f", state.gain) +
                    if (state.localAutoGain) "·a" else ""
                "${state.modeTag}$rolledMark · $gainTag"
            }
            Mono(
                right, p.ink2.copy(alpha = 0.70f), Type.dataSm,
                Modifier.padding(start = Dim.gapLg).widthIn(max = 280.dp),
            )
        }
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
                .height(SliderGeometry.HIT_LANE_DP.dp)
                .consoleSeekGesture(
                    durationMs = durationMs,
                    onScrub = { scrub = it },
                    onCommit = {
                        if (durationMs > 0) onSeek((it * durationMs).toLong())
                        scrub = -1f
                    },
                    onCancel = { scrub = -1f },
                )
                .sliderTrack(p, frac),
        )
        Mono(if (durationMs > 0) fmt(durationMs) else "–:––", p.muted, Type.dataXs)
    }
}

// ── Console card — summoned chrome, floated clear of the panel's curved glass. ──
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
    onSettingsSwipe: () -> Unit,
    settingsPullHost: PullGestureHost,
    moreActive: Boolean,
    overflowPullHost: PullGestureHost,
    onHeightChanged: (Int) -> Unit,
    chromeMoving: Boolean = false,
) {
    val view = LocalView.current
    val hasTransport = state.trackTitle != null || state.remote
    val capture = state.sourceLabel == "capture"
    val style = LocalRoomStyle.current
    val landscape = LocalChromeLandscape.current
    val cardShape = RoundedCornerShape(style.cornerRadius)
    Box(
        Modifier
            .fillMaxWidth()
            // The popout anchors above the full occupied height, including safe padding.
            .onSizeChanged { onHeightChanged(it.height) }
            .windowInsetsPadding(
                chromeSafeDrawingInsets(Dim.cardMarginH, Dim.cardMarginBottom)
            )
            .padding(start = Dim.cardMarginH, end = Dim.cardMarginH, bottom = Dim.cardMarginBottom)
            .burnInWalk(reduced),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .then(
                    if (landscape) Modifier.widthIn(max = Dim.landscapeConsoleMaxWidth)
                        .fillMaxWidth()
                    else Modifier.fillMaxWidth()
                )
                .stageChromeBounds(StageChromeBounds.Card.Console, chromeMoving)
                .clip(cardShape)
                .background(
                    p.surface.copy(alpha = Dim.consoleAlpha * style.panelAlphaScale)
                )
                .border(Dim.hairline, p.line, cardShape)
                .padding(horizontal = Dim.consolePadH, vertical = Dim.consolePadV)
                // The play bar owns this deliberate upward reveal. Stage drags remain
                // gain/orbit gestures, and horizontal seek scrubs keep their lane.
                .playBarSwipeUp(onSettingsSwipe, settingsPullHost),
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
                    Modifier.basicMarquee(
                        iterations = Int.MAX_VALUE,
                        initialDelayMillis = 2200,
                        velocity = 24.dp,
                    ),
                )
                Spacer(Modifier.height(Dim.gap))
            }
            if (state.seekable && state.durationMs > 0) {
                SeekRule(p, state.positionMs, state.durationMs, onSeek)
                Spacer(Modifier.height(Dim.gap))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasTransport && (!capture || state.captureCanPrevious)) {
                    FlatKey("◂◂", p) { Haptics.light(view); onPrev() }
                    Spacer(Modifier.width(Dim.gap))
                }
                if (!capture || state.captureCanPlay) {
                    StoneKey(if (state.playing) "❚❚" else "▶", p, reduced = reduced, designator = "S1") {
                        Haptics.light(view); onPlay()
                    }
                }
                if (hasTransport && (!capture || state.captureCanNext)) {
                    Spacer(Modifier.width(Dim.gap))
                    FlatKey("▸▸", p) { Haptics.light(view); onNext() }
                }
                Spacer(Modifier.width(Dim.gapLg))
                // Reference-designator conventions, honestly applied: V = the tube
                // (the mode IS the displayed figure), J = input jack, S = switch.
                FlatKey("MODE", p, designator = "V2") {
                    if (state.randomModeArmed) state.requestRandomMode()
                    onMode()
                }
                Spacer(Modifier.width(Dim.gap))
                FlatKey("SRC", p, designator = "J1", onClick = onSrc)
                Spacer(Modifier.weight(1f))
                OverflowHandleKey(
                    p = p,
                    active = moreActive,
                    pullHost = overflowPullHost,
                    onTap = onMore,
                )
            }
        }
    }
}

// S9 is deliberately its own key: tap and pull share one hit target and therefore
// one gesture owner. Its surface remains the same flat hairline tier as MODE/SRC.
@Composable
private fun OverflowHandleKey(
    p: Palette,
    active: Boolean,
    pullHost: PullGestureHost,
    onTap: () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val style = LocalRoomStyle.current
    Box(
        Modifier
            .width(52.dp)
            .height(Dim.flatKey)
            .background(
                if (pressed) p.accent.copy(alpha = 0.10f) else Color.Transparent
            )
            .border(Dim.hairline, if (active || pressed) p.accent else p.line)
            .overflowHandleGesture(pullHost, onTap) { pressed = it }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        UprightCell { OverflowHandleGlyph(p, active || pressed) }
        if (style.designators) {
            Mono(
                "S9", p.muted, Type.dataXs,
                Modifier.align(Alignment.TopStart).padding(top = 1.dp),
                letterSpacing = 1.2.sp,
            )
        }
    }
}

// ── The bench POST: a cold-start self-test readout (Annotated rooms only),
//    stepped in like a line printer under the thermionic bloom. Every line is
//    REAL state — no theatre (honesty law). ──
@Composable
fun BenchPost(state: ScopeUiState, p: Palette) {
    var lines by remember { mutableIntStateOf(0) }
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        repeat(3) {
            kotlinx.coroutines.delay(160)
            lines = it + 1
        }
        kotlinx.coroutines.delay(900)
        visible = false
    }
    if (!visible) return
    val checks = listOf(
        "V1 ENGINE" to "OK",
        "J1 RELAY" to if (state.remote) "OK" else "—",
        "V2 HEATER" to if (state.remote && state.remoteAudio) "OK" else "—",
    )
    Column {
        checks.take(lines).forEach { (k, v) ->
            Mono("$k · $v", p.muted, Type.dataXs, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(2.dp))
        }
    }
}

// ── The ⋯ overflow — an anchored popout, Obsidian-persistent. Rows only exist
//    once their feature is real (no dead chrome, honesty law). ──
internal object OverflowPopoutPolicy {
    fun viewportHeight(safeFrameHeight: Float, consoleHeight: Float, gap: Float): Float =
        (safeFrameHeight - consoleHeight - gap).coerceAtLeast(0f)

    // Left, top, right, bottom in the CCW-rotated chrome's coordinates.
    fun rotatedInsets(physical: List<Int>, quadrant: Int): List<Int> =
        ChromeInsetPolicy.rotatedInsets(physical, quadrant)

    fun reverseDelta(delta: Float, dismissPx: Float): Float =
        if (dismissPx <= 0f) 0f else delta.coerceIn(-dismissPx, 0f)

    fun shouldClose(dismissPx: Float, heightPx: Float): Boolean =
        heightPx > 0f && dismissPx / heightPx > 0.4f
}

@Composable
fun OverflowPopout(
    p: Palette,
    state: ScopeUiState,
    reduced: Boolean,
    reveal: PullRevealState,
    maxHeight: Dp,
    onPictureInPicture: () -> Unit,
    onPipAutoEnter: () -> Unit,
    onLight: () -> Unit,
    onRoom: () -> Unit,
    onSettings: () -> Unit,
    onFps: () -> Unit,
    onHud: () -> Unit,
    onGrid: () -> Unit,
    onRequestClose: () -> Unit,
) {
    val style = LocalRoomStyle.current
    val shape = RoundedCornerShape(style.cornerRadius)
    var heightPx by remember { mutableIntStateOf(0) }
    val scroll = rememberScrollState()
    val currentStyle by rememberUpdatedState(style)
    val currentReduced by rememberUpdatedState(reduced)
    val currentClose by rememberUpdatedState(onRequestClose)
    val dismissScroll = remember(reveal, scroll) {
        object : NestedScrollConnection {
            private var dragging = false
            private var dismissPx = 0f

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || !dragging) return Offset.Zero
                val delta = OverflowPopoutPolicy.reverseDelta(available.y, dismissPx)
                dismissPx += delta
                if (delta != 0f) reveal.dragBy(-delta)
                return Offset(0f, delta)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // Content gets first refusal. A fling cannot start a dismissal.
                if (source != NestedScrollSource.UserInput || available.y <= 0f || scroll.canScrollBackward) {
                    return Offset.Zero
                }
                if (!dragging) {
                    dragging = true
                    dismissPx = (1f - reveal.progress) * heightPx
                    reveal.begin(resetClosed = false)
                }
                dismissPx = (dismissPx + available.y).coerceAtMost(heightPx.toFloat())
                reveal.dragBy(-available.y)
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!dragging) return Velocity.Zero
                dragging = false
                if (OverflowPopoutPolicy.shouldClose(dismissPx, heightPx.toFloat())) currentClose()
                else reveal.settleTo(true, currentStyle, currentReduced)
                val consumed = if (dismissPx > 0f) Velocity(0f, available.y) else Velocity.Zero
                dismissPx = 0f
                return consumed
            }
        }
    }
    val progress = reveal.progress
    Column(
        Modifier
            .width(Dim.popoutWidth)
            .heightIn(max = maxHeight)
            .onSizeChanged {
                heightPx = it.height
                reveal.setTravelPx(it.height.toFloat())
            }
            .graphicsLayer {
                alpha = progress
                translationY = if (reduced) 0f else (1f - progress) * heightPx
            }
            .stageChromeBounds(
                StageChromeBounds.Card.Overflow,
                reveal.animation.isRunning || progress < 1f,
                progress,
            )
            .clip(shape)
            .background(p.surface.copy(alpha = Dim.sheetAlpha * style.panelAlphaScale))
            .border(Dim.hairline, p.lineStrong, shape)
            .nestedScroll(dismissScroll)
            .verticalScroll(scroll)
            .padding(Dim.popoutPad)
    ) {
        // The 2×2 destination grid keeps each glyph and label upright as one unit.
        val cells = listOf(
            Triple("PiP", SettingsGlyph.Display, onPictureInPicture),
            Triple("light", SettingsGlyph.BeamColor, onLight),
            Triple("room", SettingsGlyph.Room, onRoom),
            Triple("settings", SettingsGlyph.Knob, onSettings),
        )
        cells.chunked(2).forEachIndexed { index, gridRow ->
            if (index > 0) Spacer(Modifier.height(Dim.popoutGridGap))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dim.popoutGridGap),
            ) {
                gridRow.forEach { (label, glyph, action) ->
                    PopoutNavCell(glyph, label, p, Modifier.weight(1f), action)
                }
            }
        }
        // A hairline rule divides the destinations from the live quick toggles.
        Spacer(Modifier.height(Dim.gap))
        Box(Modifier.fillMaxWidth().height(Dim.hairline).background(p.line))
        Spacer(Modifier.height(Dim.gap))
        // Live quick settings share the available width and show state beneath each icon.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            QuickToggle(
                SettingsGlyph.Fps,
                when (state.fpsValue) { 0 -> "120"; -1 -> "unc"; else -> "${state.fpsValue}" },
                active = true, p = p, onTap = onFps,
            )
            // HUD and status-band visibility are independent display controls.
            QuickToggle(
                SettingsGlyph.Hud,
                when (state.hudMode) { 0 -> "on"; 1 -> "auto"; else -> "off" },
                active = state.hudMode != 2,
                p = p,
                onTap = onHud,
            )
            QuickToggle(
                SettingsGlyph.Grid,
                if (state.grid) "on" else "off",
                active = state.grid, p = p, onTap = onGrid,
            )
        }
        ChipCell(
            "AUTO PiP · " + if (state.pipAutoEnter) "on" else "off",
            active = state.pipAutoEnter, p = p, small = true,
            onClick = onPipAutoEnter,
        )
    }
}

// One quick-settings cell: room-aware glyph over a terse mono state.
@Composable
private fun QuickToggle(
    glyph: SettingsGlyph,
    value: String,
    active: Boolean,
    enabled: Boolean = true,
    p: Palette,
    onTap: () -> Unit,
) {
    Column(
        Modifier
            .clickable(enabled = enabled, onClick = onTap)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The whole glyph+state cell rotates as a unit through the correct primitive.
        UprightCell {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SettingsGlyphIcon(glyph, p, 18.dp)
                Spacer(Modifier.height(3.dp))
                Mono(value, if (enabled && active) p.accent else p.muted, Type.dataXs)
            }
        }
    }
}

// One destination cell of the 2×2 grid: room-aware glyph over a small mono label.
// The whole weighted cell is the hit target; icon+label upright as one unit.
@Composable
private fun PopoutNavCell(
    glyph: SettingsGlyph,
    label: String,
    p: Palette,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
) {
    Box(
        modifier
            .clickable(onClick = onTap)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        UprightCell {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SettingsGlyphIcon(glyph, p, 20.dp)
                Spacer(Modifier.height(4.dp))
                Mono(label, p.ink, Type.dataXs)
            }
        }
    }
}
