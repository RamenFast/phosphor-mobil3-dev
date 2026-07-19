package dev.phosphor.mobil3.ui

import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.TextUnit
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class Sheet { NONE, SOURCE, MODE, LIGHT, SETTINGS, ROOM, DECK }

private fun sheetCurlProgress(
    fillFraction: Float,
    style: RoomStyle,
    reduced: Boolean,
): Float {
    val continuous = (
        (fillFraction - Dim.sheetCurlStartFraction) /
            (Dim.sheetCurlFullFraction - Dim.sheetCurlStartFraction)
        ).coerceIn(0f, 1f)
    if (reduced) return if (continuous >= 0.5f) 1f else 0f
    return when (style.motion) {
        MotionFeel.Cut -> if (continuous > 0f) 1f else 0f
        MotionFeel.Detented -> (continuous * 4f).roundToInt() / 4f
        MotionFeel.Springy -> continuous * continuous * (3f - 2f * continuous)
        MotionFeel.Eased -> continuous
    }
}

/**
 * A geometry-only page curl: tall sheets pinch inward at the top while their
 * lower corners retain the room's ordinary card shape. The clipped fill and
 * hairline share this exact path, so there is no shadow/elevation language.
 */
private fun sheetCardShape(style: RoomStyle, curl: Float, density: androidx.compose.ui.unit.Density): Shape {
    val roomRadius = with(density) { style.cornerRadius.toPx() }
    val curlInset = with(density) { Dim.sheetCurlInset.toPx() }
    val curlDepth = with(density) { Dim.sheetCurlDepth.toPx() }
    return GenericShape { size, _ ->
        val width = size.width
        val height = size.height
        val inset = (roomRadius + (curlInset - roomRadius) * curl)
            .coerceAtMost(width * 0.24f)
        val shoulder = (roomRadius + (curlDepth - roomRadius) * curl)
            .coerceAtMost(height * 0.24f)
        val bottomRadius = roomRadius.coerceAtMost(minOf(width, height) * 0.24f)

        moveTo(0f, shoulder)
        if (curl <= 0f) {
            if (roomRadius > 0f) quadraticBezierTo(0f, 0f, inset, 0f)
            else lineTo(0f, 0f)
        } else when (style.character) {
            ChromeCharacter.Glass -> quadraticBezierTo(0f, 0f, inset, 0f)
            ChromeCharacter.Annotated -> {
                lineTo(inset * 0.42f, shoulder)
                lineTo(inset * 0.42f, shoulder * 0.48f)
                lineTo(inset, shoulder * 0.48f)
                lineTo(inset, 0f)
            }
            ChromeCharacter.Carved -> {
                lineTo(inset * 0.32f, shoulder)
                lineTo(inset, 0f)
            }
            ChromeCharacter.Engraved -> lineTo(inset, 0f)
        }

        lineTo(width - inset, 0f)
        if (curl <= 0f) {
            if (roomRadius > 0f) quadraticBezierTo(width, 0f, width, shoulder)
            else lineTo(width, 0f)
        } else when (style.character) {
            ChromeCharacter.Glass -> quadraticBezierTo(width, 0f, width, shoulder)
            ChromeCharacter.Annotated -> {
                lineTo(width - inset, shoulder * 0.48f)
                lineTo(width - inset * 0.42f, shoulder * 0.48f)
                lineTo(width - inset * 0.42f, shoulder)
                lineTo(width, shoulder)
            }
            ChromeCharacter.Carved -> {
                lineTo(width - inset * 0.32f, shoulder)
                lineTo(width, shoulder)
            }
            ChromeCharacter.Engraved -> lineTo(width, shoulder)
        }

        lineTo(width, height - bottomRadius)
        if (bottomRadius > 0f) {
            quadraticBezierTo(width, height, width - bottomRadius, height)
        } else lineTo(width, height)
        lineTo(bottomRadius, height)
        if (bottomRadius > 0f) {
            quadraticBezierTo(0f, height, 0f, height - bottomRadius)
        } else lineTo(0f, height)
        close()
    }
}

// ── Sheet mechanics (shared): a safe-area floating card over the live scope;
// fill-driven top curl; drag-down / scrim-tap / ✕ / Back to dismiss. ──
@Composable
fun SheetHost(
    p: Palette,
    title: String,
    reduced: Boolean,
    onDismiss: () -> Unit,
    entryReveal: PullRevealState? = null,
    glyph: SettingsGlyph? = null,
    body: @Composable () -> Unit,
) {
    val style = LocalRoomStyle.current
    val landscape = LocalChromeLandscape.current
    // "For each respective side" (Ben's ask): in landscape the card anchors to the
    // edge nearest the user's reach — the phone's chin. ROTATION_90 puts the chin on
    // the right, ROTATION_270 on the left; ROTATION_0/180/null keep the historical
    // right so the common landscape is unchanged.
    val landscapeSide = when (LocalView.current.display?.rotation) {
        Surface.ROTATION_270 -> Alignment.BottomStart
        else -> Alignment.BottomEnd
    }
    val bloom = LocalBloomPull.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val bloomTravelPx = with(density) { 28.dp.toPx() }
    val bloomOffsetPx = -(bloom?.visualPull ?: 0f) * bloomTravelPx
    var availableHeightPx by remember { mutableIntStateOf(0) }
    var sheetHeightPx by remember { mutableIntStateOf(0) }
    val fillFraction = if (availableHeightPx > 0) {
        sheetHeightPx.toFloat() / availableHeightPx
    } else 0f
    val curl = sheetCurlProgress(fillFraction, style, reduced)
    val sheetShape = remember(style, curl, density) {
        sheetCardShape(style, curl, density)
    }
    // Expressive dismiss (Ben's ask): the old `visible = true` meant the exit
    // could NEVER play — dismissal was an instant removal. Now the sheet owns a
    // real open/close state: ✕/scrim/drag/Back play the departure (accelerating
    // slide DOWN + fade — the motion says where it went) and the composition
    // leaves only after the choreography finishes.
    val openState = remember {
        MutableTransitionState(entryReveal != null).apply { targetState = true }
    }
    val dismiss = {
        if (openState.targetState) openState.targetState = false
    }
    val dismissOffset = remember { Animatable(0f) }
    var rawDismissPx by remember { mutableFloatStateOf(0f) }
    val dismissDistancePx = with(density) { Dim.sheetDismissDistance.toPx() }
    val dismissFlickPx = with(density) { Dim.chromeFlickVelocity.toPx() }
    val beginDismiss = {
        rawDismissPx = dismissOffset.value.coerceAtLeast(0f)
        scope.launch { dismissOffset.stop() }
    }
    val dragDismissBy: (Float) -> Unit = { delta ->
        rawDismissPx = (rawDismissPx + delta).coerceAtLeast(0f)
        val target = rawDismissPx
        scope.launch { dismissOffset.snapTo(target) }
    }
    val settleDismiss: (Float) -> Unit = { velocityY ->
        if (rawDismissPx >= dismissDistancePx || velocityY >= dismissFlickPx) {
            dismiss()
        } else {
            rawDismissPx = 0f
            scope.launch {
                dismissOffset.stop()
                when {
                    reduced || style.motion == MotionFeel.Cut -> dismissOffset.snapTo(0f)
                    style.motion == MotionFeel.Springy -> dismissOffset.animateTo(
                        0f,
                        spring(dampingRatio = 0.72f, stiffness = 380f, visibilityThreshold = 0.5f),
                    )
                    else -> dismissOffset.animateTo(
                        0f,
                        styleSpec(false, style, Motion.settle, Motion.decelerate),
                    )
                }
            }
        }
    }
    // A scroll child first consumes every ordinary scroll delta. Only its unconsumed
    // downward remainder at TOP reaches this parent, becoming the sheet pull. The
    // opposite (upward-at-bottom) remainder is still exclusively bloom's lane.
    val dismissNestedScroll = remember(style.motion, reduced, dismissDistancePx, dismissFlickPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (rawDismissPx <= 0f || available.y >= 0f) return Offset.Zero
                // A reversal first pushes the displaced sheet home; only the remainder
                // scrolls content away from its top edge.
                val consumedY = available.y.coerceAtLeast(-rawDismissPx)
                dragDismissBy(consumedY)
                return Offset(0f, consumedY)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (available.y <= 0f) return Offset.Zero
                if (rawDismissPx == 0f) beginDismiss()
                dragDismissBy(available.y)
                return Offset(0f, available.y)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (rawDismissPx <= 0f) return Velocity.Zero
                settleDismiss(available.y.coerceAtLeast(0f))
                return if (available.y > 0f) Velocity(0f, available.y) else Velocity.Zero
            }
        }
    }
    LaunchedEffect(openState.targetState, openState.isIdle) {
        if (!openState.targetState && openState.isIdle) onDismiss()
    }
    BackHandler { dismiss() }
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Color.Black.copy(
                    alpha = Dim.scrimAlpha * (entryReveal?.progress ?: 1f)
                )
            )
            .pointerInput(Unit) { detectTapGestures(onTap = { dismiss() }) },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    chromeSafeDrawingInsets(Dim.cardMarginH, Dim.cardMarginBottom)
                )
                .padding(start = Dim.cardMarginH, end = Dim.cardMarginH, bottom = Dim.cardMarginBottom)
                .onSizeChanged { availableHeightPx = it.height },
            contentAlignment = if (landscape) landscapeSide else Alignment.BottomCenter,
        ) {
            AnimatedVisibility(
                visibleState = openState,
                enter = if (reduced) fadeIn() else if (style.motion == MotionFeel.Springy)
                // The one room that bounces (glass): a gentle spring settle.
                    slideInVertically(
                        spring(dampingRatio = 0.72f, stiffness = 380f,
                            visibilityThreshold = IntOffset.VisibilityThreshold)
                    ) { it / 3 } + fadeIn(styleSpec(false, style, Motion.sheet))
                else
                    slideInVertically(styleSpec(false, style, Motion.sheet, Motion.decelerate)) { it / 3 } +
                        fadeIn(styleSpec(false, style, Motion.sheet)),
                exit = if (reduced) fadeOut() else
                    slideOutVertically(styleSpec(false, style, Motion.settle, Motion.accelerate)) { it / 2 } +
                        fadeOut(styleSpec(false, style, Motion.settle, Motion.accelerate)),
            ) {
                Column(
                    Modifier
                        .offset {
                            IntOffset(
                                0,
                                (dismissOffset.value.coerceAtLeast(0f) + bloomOffsetPx).roundToInt(),
                            )
                        }
                        .then(
                            if (landscape) Modifier.widthIn(max = Dim.landscapeSheetMaxWidth)
                                .fillMaxWidth()
                            else Modifier.fillMaxWidth()
                        )
                        .onSizeChanged {
                            sheetHeightPx = it.height
                            entryReveal?.setTravelPx(it.height.toFloat())
                        }
                        .graphicsLayer {
                            entryReveal?.let { reveal ->
                                val progress = reveal.progress
                                alpha = progress
                                translationY = (1f - progress) * sheetHeightPx
                            }
                        }
                        .nestedScroll(dismissNestedScroll)
                        .clip(sheetShape)
                        .background(p.surface.copy(alpha = Dim.sheetAlpha * style.panelAlphaScale))
                        .border(Dim.hairline, p.lineStrong, sheetShape)
                        .padding(Dim.sheetPad)
                        // Swallow taps so only the surrounding scrim dismisses.
                        .pointerInput(Unit) { detectTapGestures(onTap = {}) },
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .pointerInput(style.motion, reduced) {
                                detectVerticalDragGestures(
                                    onDragStart = { beginDismiss() },
                                    onDragEnd = { settleDismiss(0f) },
                                    onDragCancel = { settleDismiss(0f) },
                                ) { change, delta ->
                                    if (delta > 0f || rawDismissPx > 0f) {
                                        change.consume()
                                        dragDismissBy(delta)
                                    }
                                }
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // Header glyph+title upright to the viewing edge (UI-locked).
                        UprightCell {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                glyph?.let {
                                    SettingsGlyphIcon(it, p, 15.dp)
                                    Spacer(Modifier.width(8.dp))
                                }
                                Mono(title, p.ink2, Type.data)
                            }
                        }
                        Mono(
                            "✕", p.ink2, Type.dataXl,
                            Modifier.clickable(onClick = dismiss).padding(horizontal = 6.dp),
                        )
                    }
                    Spacer(Modifier.height(Dim.gapLg))
                    body()
                }
            }
        }
    }
}

// A draggable mono numeric: horizontal drag scrubs the value along a hairline rule.
@Composable
fun DragRule(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    p: Palette,
    format: (Float) -> String = { "%.2f".format(it) },
    onChange: (Float) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Mono(label, p.ink2, Type.data, Modifier.width(96.dp))
        Box(
            Modifier
                .weight(1f)
                .height(28.dp)
                .pointerInput(min, max) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        val frac = (change.position.x / size.width).coerceIn(0f, 1f)
                        onChange(min + frac * (max - min))
                    }
                }
                .drawBehind {
                    val midY = size.height / 2f
                    drawLine(p.line, Offset(0f, midY), Offset(size.width, midY), 1.dp.toPx())
                    val x = ((value - min) / (max - min)).coerceIn(0f, 1f) * size.width
                    drawLine(p.accent, Offset(0f, midY), Offset(x, midY), 1.dp.toPx())
                    val half = 4.dp.toPx()
                    drawRect(
                        p.ink,
                        topLeft = Offset(x - half, midY - half),
                        size = androidx.compose.ui.geometry.Size(half * 2, half * 2),
                    )
                },
        )
        Mono(format(value), p.ink, Type.data, Modifier.padding(start = 10.dp).width(56.dp))
    }
}

// Two-thumb sibling of DragRule: drag scrubs the NEAREST square handle, the accent
// hairline spans the kept [lo, hi] sub-range. A real checkbox leads the row — the
// visible arm/disarm toggle for the ⚄ behavior (checked = armed, re-rolls per track).
@Composable
fun RangeDragRule(
    label: String,
    lo: Float,
    hi: Float,
    min: Float,
    max: Float,
    p: Palette,
    armed: Boolean = false,
    onLabelTap: (() -> Unit)? = null,
    format: (Float) -> String = { "%.2f".format(it) },
    onChange: (Float, Float) -> Unit,
) {
    val grab = remember { androidx.compose.runtime.mutableIntStateOf(-1) }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.width(96.dp).then(
                if (onLabelTap != null) Modifier.clickable { onLabelTap() } else Modifier
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(16.dp)
                    .border(Dim.hairline, if (armed) p.accent else p.line)
                    .padding(4.dp)
                    .then(Modifier.drawBehind {
                        if (armed) drawRect(p.accent)
                    }),
            )
            Spacer(Modifier.width(8.dp))
            Mono(label, if (armed) p.accent else p.ink2, Type.data)
        }
        Box(
            Modifier
                .weight(1f)
                .height(28.dp)
                .pointerInput(min, max) {
                    detectHorizontalDragGestures(
                        onDragStart = { down ->
                            val span = (max - min).takeIf { it > 0f } ?: 1f
                            val xLo = ((lo - min) / span).coerceIn(0f, 1f) * size.width
                            val xHi = ((hi - min) / span).coerceIn(0f, 1f) * size.width
                            grab.intValue =
                                if (kotlin.math.abs(down.x - xLo) <= kotlin.math.abs(down.x - xHi)) 0 else 1
                        },
                        onDragEnd = { grab.intValue = -1 },
                        onDragCancel = { grab.intValue = -1 },
                    ) { change, _ ->
                        change.consume()
                        val frac = (change.position.x / size.width).coerceIn(0f, 1f)
                        val v = min + frac * (max - min)
                        if (grab.intValue == 0) onChange(v.coerceIn(min, hi), hi)
                        else onChange(lo, v.coerceIn(lo, max))
                    }
                }
                .drawBehind {
                    val midY = size.height / 2f
                    drawLine(p.line, Offset(0f, midY), Offset(size.width, midY), 1.dp.toPx())
                    val span = (max - min).takeIf { it > 0f } ?: 1f
                    val xLo = ((lo - min) / span).coerceIn(0f, 1f) * size.width
                    val xHi = ((hi - min) / span).coerceIn(0f, 1f) * size.width
                    drawLine(p.accent, Offset(xLo, midY), Offset(xHi, midY), 1.dp.toPx())
                    val half = 4.dp.toPx()
                    for (x in floatArrayOf(xLo, xHi)) {
                        drawRect(
                            p.ink,
                            topLeft = Offset(x - half, midY - half),
                            size = androidx.compose.ui.geometry.Size(half * 2, half * 2),
                        )
                    }
                },
        )
        Mono(
            "${format(lo)}–${format(hi)}",
            p.ink,
            Type.dataXs,
            Modifier.padding(start = 10.dp).width(72.dp),
        )
    }
}

// ── SOURCE (spec §2.3): hierarchy headings, the LIVE stone, the consent moment. ──
@Composable
fun SourceSheet(
    state: ScopeUiState,
    p: Palette,
    reduced: Boolean,
    actions: SheetActions,
    onDismiss: () -> Unit,
) {
    var consentCard by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    SheetHost(p, "SOURCE", reduced, onDismiss, glyph = SettingsGlyph.Signal) {
      Column(
          Modifier
              .bottomBloomOverscroll { !scroll.canScrollForward }
              .verticalScroll(scroll, overscrollEffect = null)
      ) {
        if (consentCard) {
            Prose(
                "Android will ask you to let Phosphor see what's playing. Phosphor turns " +
                    "that sound into light on this screen — nothing is recorded, nothing " +
                    "leaves your phone. You can turn it off any time with the LIVE key.",
                p.ink, modifier = Modifier.padding(bottom = Dim.gapLg),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Dim.gap)) {
                FlatKey("CONTINUE", p, active = true) {
                    consentCard = false
                    actions.startCapture()
                    onDismiss()
                }
                FlatKey("NOT NOW", p) { consentCard = false }
            }
        } else {
        SectionHeading("MY LIBRARY", p, Modifier.padding(top = 0.dp))
        SheetRow("open file…", p, checked = state.sourceLabel == "deck" && state.queueTitles.size <= 1) {
            actions.openFile(); onDismiss()
        }
        SheetRow(
            "open folder → queue", p,
            checked = state.sourceLabel == "deck" && state.queueTitles.size > 1,
        ) { actions.openFolder(); onDismiss() }
        SectionHeading("OTHER APPS", p)
        SheetRow(
            "everything playing", p,
            checked = state.live && state.sourceLabel == "capture",
        ) {
            if (actions.captureConsentNeeded()) consentCard = true
            else { actions.startCapture(); onDismiss() }
        }
        if (!state.captureMetadataAccess) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = Dim.gap),
                horizontalArrangement = Arrangement.spacedBy(Dim.gap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Prose(
                    "Track names and cover art need NOTIFICATION ACCESS — a different " +
                        "switch than “allow notifications”. grant… opens the right one. " +
                        "Sound capture works without it.",
                    p.muted,
                    modifier = Modifier.weight(1f),
                )
                FlatKey("grant…", p) { actions.openCaptureMetadataSettings() }
            }
        }
        SectionHeading("MICROPHONE", p)
        SheetRow("built-in mic", p, checked = state.sourceLabel == "mic") {
            actions.startMic(); onDismiss()
        }
        SectionHeading("REMOTE", p)
        RemoteFlow(state, p, actions, onDismiss)
        Prose(
            "Remote scopes another machine's audio over Tailscale — it plays here and " +
                "the transport drives that machine. Local capture hears whatever apps " +
                "allow it (Spotify currently does; some DRM apps stay silent).",
            p.muted, modifier = Modifier.padding(top = Dim.gap, bottom = Dim.gapLg),
        )
        StoneToggle(
            if (state.live) "⏻ LIVE" else "⏻ LIVE · off",
            engaged = state.live, p = p,
            modifier = Modifier.fillMaxWidth(), reduced = reduced,
        ) {
            if (state.live) actions.stopLive() else if (actions.captureConsentNeeded()) {
                consentCard = true
            } else actions.startCapture()
        }
        }
      }
    }
}

// ── MODE (spec §2.4): grouped hierarchy list, engraved glyphs, live behind glass. ──
@Composable
fun ModeSheet(
    state: ScopeUiState,
    p: Palette,
    reduced: Boolean,
    onPick: (Int) -> Unit,
    onGeomFx: (Int) -> Unit,
    onGeomAmount: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val view = LocalView.current
    val scroll = rememberScrollState()
    val groups = listOf(
        "XY" to listOf(0, 1, 2, 3),
        "3D" to listOf(4, 5),
        "TIME" to listOf(6, 7),
        "SPECTRUM" to listOf(8, 9, 10),
    )
    SheetHost(p, "MODE", reduced, onDismiss, glyph = SettingsGlyph.Display) {
        Column(
            Modifier
                .bottomBloomOverscroll { !scroll.canScrollForward }
                .verticalScroll(scroll, overscrollEffect = null)
        ) {
            SectionHeading("AUTOMATIC", p, Modifier.padding(top = 0.dp))
            val randomActive = state.randomModeArmed
            Row(
                Modifier
                    .fillMaxWidth()
                    .border(Dim.hairline, if (randomActive) p.accent else p.line)
                    .clickable { Haptics.medium(view); state.requestRandomMode() }
                    .padding(horizontal = Dim.rowPad, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Mono("⚄", if (randomActive) p.accent else p.ink2, Type.dataXl, Modifier.width(22.dp))
                Spacer(Modifier.width(Dim.gapLg))
                Mono("random", if (randomActive) p.accent else p.ink, Type.dataLg)
                Spacer(Modifier.weight(1f))
                Mono(if (randomActive) state.modeTag else "new face", p.muted, Type.dataXs)
            }
            Spacer(Modifier.height(6.dp))
            // Ban editor: faces struck here never come up on ⚄. At least two must stay
            // in play — the guard simply refuses the tap that would starve the die.
            var banEditing by remember { mutableStateOf(false) }
            val banned = state.randomBanModes
            ChipCell(
                "BAN FACES · " + if (banned.isEmpty()) "none" else "${banned.size}",
                banned.isNotEmpty(), p, small = true,
            ) { banEditing = !banEditing }
            if (banEditing) {
                Spacer(Modifier.height(6.dp))
                ModeTags.indices.chunked(4).forEach { rowIdx ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        rowIdx.forEach { i ->
                            Box(Modifier.weight(1f)) {
                                ChipCell(ModeTags[i], i in banned, p, small = true) {
                                    state.randomBanModes = when {
                                        i in banned -> banned - i
                                        ModeLabels.size - banned.size > 2 -> banned + i
                                        else -> banned
                                    }
                                }
                            }
                        }
                        repeat(4 - rowIdx.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Prose(
                    "banned faces never come up on ⚄ — at least two must stay in play",
                    p.muted, modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            groups.forEach { (heading, indices) ->
                SectionHeading(heading, p)
                indices.forEach { i ->
                    val active = state.modeIndex == i
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .border(Dim.hairline, if (active) p.accent else p.line)
                            .clickable { Haptics.medium(view); onPick(i) }
                            .padding(horizontal = Dim.rowPad, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ModeGlyph(i, if (active) p.accent else p.ink2.copy(alpha = 0.8f))
                        Spacer(Modifier.width(Dim.gapLg))
                        Mono(ModeLabels[i], if (active) p.accent else p.ink, Type.dataLg)
                        Spacer(Modifier.weight(1f))
                        Mono(ModeTags[i], p.muted, Type.dataXs)
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
            SectionHeading("GEOMETRY", p)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                GeomFxLabels.forEachIndexed { i, label ->
                    Box(Modifier.weight(1f)) {
                        ChipCell(label, state.geomFx == i, p, small = true) {
                            Haptics.medium(view)
                            onGeomFx(i)
                        }
                    }
                }
            }
            if (state.geomFx != 0) {
                DragRule("AMOUNT", state.geomAmount, 0f, 1f, p, { "%.0f %%".format(it * 100) }) {
                    onGeomAmount(it)
                }
            }
            Prose(
                "geometry bends the beam after the mode draws it — it rides every face, " +
                    "⚄ included. phone-local: a remote desktop beam is untouched",
                p.muted, modifier = Modifier.padding(top = 4.dp),
            )
            Prose(
                "the sheet stays open — tap modes to try them live behind the glass",
                p.muted, modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

// ── ROOM: the 12 chrome rooms (self-portrait tiles arrive with the three souls). ──
@Composable
fun RoomSheet(
    state: ScopeUiState,
    p: Palette,
    reduced: Boolean,
    onPick: (Palette) -> Unit,
    onDismiss: () -> Unit,
) {
    SheetHost(p, "ROOM", reduced, onDismiss, glyph = SettingsGlyph.Room) {
        // Breathing pulse for follows-beam tiles (one clock for all).
        val breath by rememberInfiniteTransition(label = "breath").animateFloat(
            initialValue = 0.35f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(1400, easing = Motion.standard), RepeatMode.Reverse
            ),
            label = "breathA",
        )
        val gridState = rememberLazyGridState()
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier
                .heightIn(max = 340.dp)
                .bottomBloomOverscroll { !gridState.canScrollForward },
            overscrollEffect = null,
        ) {
            itemsIndexed(Rooms) { _, room ->
                val active = room.id == state.room.id
                val rs = room.style
                Column(
                    Modifier
                        .padding(4.dp)
                        .background(room.plane)
                        .border(Dim.hairline, if (active) p.accent else room.lineStrong)
                        .clickable {
                            if (room.id == "amoled") state.amoledCaptionSeen = true
                            onPick(room)
                        }
                        .padding(10.dp),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .clip(RoundedCornerShape(rs.cornerRadius))
                            .background(
                                if (rs.character == ChromeCharacter.Glass)
                                    room.surface.copy(alpha = 0.6f)
                                else room.surface
                            )
                            .border(Dim.hairline, room.line, RoundedCornerShape(rs.cornerRadius)),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Mono(
                            "  " + room.label, room.ink, Type.dataXs,
                            letterSpacing = if (rs.designators) 1.2.sp else TextUnit.Unspecified,
                        )
                        if (rs.designators) {
                            Mono(
                                "A2 ", room.muted, Type.dataXs,
                                Modifier.align(Alignment.CenterEnd),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Its own beam, tracing: the room's self-portrait glyph.
                        ModeGlyph(2, room.accent)
                        Spacer(Modifier.width(6.dp))
                        // A sample of the room's control character:
                        StyleSampleChip(room)
                        if (room.accentFollowsBeam) {
                            Spacer(Modifier.width(6.dp))
                            Box(
                                Modifier.size(6.dp)
                                    .background(room.accent.copy(alpha = breath))
                            )
                        }
                    }
                    if (room.id == "amoled" && !state.amoledCaptionSeen) {
                        Mono(
                            "true black · made for this panel",
                            room.muted, Type.dataXs, Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }

        // ── CUSTOM STYLE (Ben's ask: customizable UX/UI elements): per-user
        // overrides on top of the active room's personality. `match` = none. ──
        SectionHeading("STYLE", p)
        val ov = state.styleOverride
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.weight(1f)) {
                ChipCell(
                    "FEEL · " + (ov.character?.name?.lowercase() ?: "match"),
                    active = ov.character != null, p = p, small = true,
                ) {
                    val all = listOf(null) + ChromeCharacter.entries
                    state.styleOverride = ov.copy(
                        character = all[(all.indexOf(ov.character) + 1) % all.size]
                    )
                }
            }
            Box(Modifier.weight(1f)) {
                ChipCell(
                    "MOTION · " + (ov.motion?.name?.lowercase() ?: "match"),
                    active = ov.motion != null, p = p, small = true,
                ) {
                    val all = listOf(null) + MotionFeel.entries
                    state.styleOverride = ov.copy(
                        motion = all[(all.indexOf(ov.motion) + 1) % all.size]
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.weight(1f)) {
                ChipCell(
                    "CORNERS · " + (ov.radiusDp?.let { "${it}dp" } ?: "match"),
                    active = ov.radiusDp != null, p = p, small = true,
                ) {
                    val all = listOf(null, 0, 8, 12)
                    state.styleOverride = ov.copy(
                        radiusDp = all[(all.indexOf(ov.radiusDp) + 1) % all.size]
                    )
                }
            }
            Box(Modifier.weight(1f)) {
                ChipCell(
                    "LABELS · " + (ov.designators?.let { if (it) "part-nos" else "plain" } ?: "match"),
                    active = ov.designators != null, p = p, small = true,
                ) {
                    val all = listOf(null, true, false)
                    state.styleOverride = ov.copy(
                        designators = all[(all.indexOf(ov.designators) + 1) % all.size]
                    )
                }
            }
        }
        Prose(
            "overrides ride on top of whichever room you are in — match hands the choice back",
            p.muted, modifier = Modifier.padding(top = 6.dp),
        )
    }
}

// A tiny swatch of a room's control character — carved bevel, engraved outline,
// annotated bevel, or a glass slab.
@Composable
private fun StyleSampleChip(room: Palette) {
    val rs = room.style
    val shape = RoundedCornerShape(if (rs.character == ChromeCharacter.Glass) 4.dp else 0.dp)
    Box(
        Modifier
            .width(26.dp)
            .height(12.dp)
            .clip(shape)
            .background(
                when (rs.character) {
                    ChromeCharacter.Engraved -> Color.Transparent
                    ChromeCharacter.Glass -> room.stone.copy(alpha = 0.55f)
                    else -> room.stone
                }
            )
            .border(
                Dim.hairline,
                when (rs.character) {
                    ChromeCharacter.Engraved -> room.lineStrong
                    ChromeCharacter.Glass -> room.stoneHi.copy(alpha = 0.9f)
                    else -> room.stoneHi
                },
                shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (rs.character == ChromeCharacter.Annotated) {
            Mono("A2", room.muted, 7.sp)
        }
    }
}

// ── SETTINGS (pass 1 structure; the full desktop port grows into these groups). ──
@Composable
private fun SettingsSectionHeading(
    text: String,
    glyph: SettingsGlyph,
    p: Palette,
    modifier: Modifier = Modifier,
) {
    val glyphSize = with(LocalDensity.current) { Type.dataSm.toDp() } + 5.dp
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsGlyphIcon(glyph, p, glyphSize)
        Spacer(Modifier.width(Dim.gap))
        SectionHeading(text, p, Modifier.weight(1f))
    }
}

@Composable
private fun SettingsGlyphRow(
    label: String,
    glyph: SettingsGlyph,
    p: Palette,
    onClick: () -> Unit,
) {
    val glyphSize = with(LocalDensity.current) { Type.dataLg.toDp() } + 3.dp
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(Dim.hairline, p.line)
            .padding(Dim.rowPad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsGlyphIcon(glyph, p, glyphSize)
        Spacer(Modifier.width(Dim.gap))
        Mono(label, p.ink, Type.dataLg)
    }
    Spacer(Modifier.height(Dim.gap))
}

@Composable
fun SettingsSheet(
    state: ScopeUiState,
    p: Palette,
    reduced: Boolean,
    actions: SheetActions,
    focusValue: Float,
    onFocus: (Float) -> Unit,
    entryReveal: PullRevealState? = null,
    onDismiss: () -> Unit,
) {
    val scroll = rememberScrollState()
    val landscape = LocalChromeLandscape.current
    SheetHost(p, "SETTINGS", reduced, onDismiss, entryReveal, glyph = SettingsGlyph.Knob) {
        // Each settings group is a self-contained block so the same content lays out
        // as one portrait column or, in landscape, two side-by-side columns (Ben's
        // ask: settings is the full view — a real landscape split, not a narrow
        // portrait column). One shared vertical scroll keeps the fill-driven top-curl
        // and the nested-scroll dismiss working exactly as before.
        val signal: @Composable () -> Unit = {
            SettingsSectionHeading(
                "SIGNAL", SettingsGlyph.Signal, p, Modifier.padding(top = 0.dp),
            )
            DragRule("FOCUS", focusValue, 0.3f, 3.0f, p, { "%.2f px".format(it) }, onFocus)
            DragRule(
                "GAIN", state.gain, 0.1f, 7.0f, p, { "×%.2f".format(it) },
            ) { actions.setGainAbsolute(it) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.weight(1f)) {
                    ChipCell(
                        "AUTO-GAIN · " + if (state.autoGain) "on" else "off",
                        active = state.autoGain, p = p, small = true,
                    ) { actions.setGainAuto(!state.autoGain) }
                }
                Box(Modifier.weight(1f)) {
                    // Ben's ask: pin a chosen zoom level — gestures inform, never move.
                    ChipCell(
                        "VIEW LOCK · " + if (state.viewLock) "on" else "off",
                        active = state.viewLock, p = p, small = true,
                    ) { actions.setViewLock(!state.viewLock) }
                }
                Spacer(Modifier.weight(1f))
            }
            Prose(
                "Local light glides with the desktop autosize law. Remote sends the same " +
                    "gain command to the source machine. Auto or VIEW LOCK pin the " +
                    "viewport — gain gestures just say so; this GAIN rule is the manual takeover.",
                p.muted, modifier = Modifier.padding(top = 6.dp),
            )
            DragRule(
                "BEAM", state.beamEnergy, 1.0f, 30.0f, p, { "×%.0f".format(it) },
            ) { actions.setBeamEnergy(it) }
            RangeDragRule(
                "⚄ RANGE", state.beamRandomLo, state.beamRandomHi, 1.0f, 30.0f, p,
                armed = state.beamRandomArmed, onLabelTap = { actions.tapBeamRandom() },
                format = { "×%.0f".format(it) },
            ) { lo, hi -> actions.setBeamRandomRange(lo, hi) }
            DragRule(
                "GLOW", state.glow, 0.0f, 0.98f, p, { "%.0f %%".format(it * 100) },
            ) { actions.setGlow(it) }
            RangeDragRule(
                "⚄ RANGE", state.glowRandomLo, state.glowRandomHi, 0.0f, 0.98f, p,
                armed = state.glowRandomArmed, onLabelTap = { actions.tapGlowRandom() },
                format = { "%.0f %%".format(it * 100) },
            ) { lo, hi -> actions.setGlowRandomRange(lo, hi) }
            Prose(
                "check a ⚄ box to arm the die: it rolls inside the kept range now and " +
                    "re-rolls on every track, like the mode die. Uncheck to disarm (the " +
                    "last roll stays put); dragging the plain rule also takes over.",
                p.muted, modifier = Modifier.padding(top = 6.dp),
            )
        }
        val display: @Composable () -> Unit = {
            SettingsSectionHeading("DISPLAY", SettingsGlyph.Display, p)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.weight(1f)) {
                    ChipCell("GRID · " + (if (state.grid) "on" else "off"), active = state.grid, p = p, small = true) {
                        actions.setGrid(!state.grid)
                    }
                }
                Spacer(Modifier.weight(2f))
            }
            Spacer(Modifier.height(6.dp))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val wideEnoughForOneRow = maxWidth >= 480.dp
                val fullscreen: @Composable () -> Unit = {
                    // Immersive is a choice, not a law. Off shows the bars; the
                    // corner-clearance helper reads real insets either way.
                    ChipCell(
                        "FULLSCREEN · " + (if (state.fullscreen) "on" else "off"),
                        active = state.fullscreen, p = p, small = true,
                    ) { actions.setFullscreen(!state.fullscreen) }
                }
                val scopeRotation: @Composable () -> Unit = {
                    ChipCell(
                        "SCOPE ROTATION · " +
                            (if (actions.isScopeRotationLocked()) "locked" else "free"),
                        active = actions.isScopeRotationLocked(), p = p, small = true,
                    ) {
                        actions.setScopeRotationLocked(!actions.isScopeRotationLocked())
                    }
                }
                val uiPlacement: @Composable () -> Unit = {
                    ChipCell(
                        "UI PLACEMENT · " +
                            (if (actions.isUiPlacementLocked()) "locked" else "follow"),
                        active = actions.isUiPlacementLocked(), p = p, small = true,
                    ) {
                        actions.setUiPlacementLocked(!actions.isUiPlacementLocked())
                    }
                }
                if (wideEnoughForOneRow) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(Modifier.weight(1f)) { fullscreen() }
                        Box(Modifier.weight(1f)) { scopeRotation() }
                        Box(Modifier.weight(1f)) { uiPlacement() }
                    }
                } else {
                    Column(Modifier.fillMaxWidth()) {
                        fullscreen()
                        scopeRotation()
                        uiPlacement()
                    }
                }
            }
            Prose(
                "Scope lock pins the exact current orientation; free returns rotation " +
                    "to Android. UI placement lock freezes the portrait/landscape card " +
                    "layout while the scope keeps resizing underneath. Text stays upright " +
                    "and safety caps may tighten a card: one Activity cannot hold chrome " +
                    "on an absolute handset edge while rotating only its native surface " +
                    "without counter-rotating the labels.",
                p.muted, modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(Dim.gap))
            Mono("FRAME RATE", p.muted, Type.dataXs, Modifier.padding(bottom = 4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FpsOptions.forEach { opt ->
                    Box(Modifier.weight(1f)) {
                        ChipCell(opt.label, active = opt.value == state.fpsValue, p = p, small = true) {
                            actions.setFps(opt.value)
                        }
                    }
                }
            }
            Prose(FpsNote, p.muted, modifier = Modifier.padding(top = 6.dp))
            Mono("BEAM RATE", p.muted, Type.dataXs, Modifier.padding(top = Dim.gapLg, bottom = 4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BeamRates.forEach { opt ->
                    Box(Modifier.weight(1f)) {
                        ChipCell(opt.label, active = opt.oversample == state.oversample, p = p, small = true) {
                            actions.setOversample(opt.oversample)
                        }
                    }
                }
            }
            Prose(BeamRateNote, p.muted, modifier = Modifier.padding(top = 6.dp))
        }
        val performance: @Composable (Boolean) -> Unit = { head ->
            SettingsSectionHeading(
                "PERFORMANCE", SettingsGlyph.Performance, p,
                if (head) Modifier.padding(top = 0.dp) else Modifier,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.weight(1f)) {
                    ChipCell(
                        "HUD · " + when (state.hudMode) {
                            0 -> "on"; 1 -> "auto"; else -> "off"
                        },
                        active = state.hudMode == 0, p = p, small = true,
                    ) { actions.setHudMode((state.hudMode + 1) % 3) }
                }
                Box(Modifier.weight(1f)) {
                    // Status band (Ben's ask): always · rides the console timer · off.
                    ChipCell(
                        "BAND · " + when (state.bandMode) {
                            1 -> "auto"; 2 -> "off"; else -> "on"
                        },
                        active = state.bandMode == 0, p = p, small = true,
                    ) { state.bandMode = (state.bandMode + 1) % 3 }
                }
                Spacer(Modifier.weight(1f))
            }
        }
        val remote: @Composable () -> Unit = {
            SettingsSectionHeading("REMOTE", SettingsGlyph.Remote, p)
            Mono("LATENCY", p.muted, Type.dataXs, Modifier.padding(bottom = 4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("tight" to 0, "balanced" to 1, "safe" to 2).forEach { (label, mode) ->
                    Box(Modifier.weight(1f)) {
                        ChipCell(
                            label, active = state.latencyMode == mode, p = p, small = true,
                        ) { actions.setRemoteLatencyMode(mode) }
                    }
                }
            }
            Prose(
                "Tight is for tether or LAN. The bridge widens automatically on underruns; " +
                    "safe is today's ear-verified behavior.",
                p.muted, modifier = Modifier.padding(top = 6.dp),
            )
            Mono(
                "NETWORK", p.muted, Type.dataXs,
                Modifier.padding(top = Dim.gapLg, bottom = 4.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("auto" to 0, "wifi" to 1, "mobile" to 2).forEach { (label, mode) ->
                    Box(Modifier.weight(1f)) {
                        ChipCell(
                            label, active = state.networkMode == mode, p = p, small = true,
                        ) { actions.setRemoteNetworkMode(mode) }
                    }
                }
            }
            Prose(
                "Auto follows Android's default route. Wi-Fi or mobile binds the remote " +
                    "session process-wide because the bridge socket lives in Rust, not Java. " +
                    "There is no both: one TCP stream cannot multipath.",
                p.muted, modifier = Modifier.padding(top = 6.dp),
            )
        }
        val roomLight: @Composable () -> Unit = {
            SettingsSectionHeading("ROOM & LIGHT", SettingsGlyph.RoomLight, p)
            SettingsGlyphRow("room · ${state.room.label}", SettingsGlyph.Room, p) {
                actions.openRoom()
            }
            SettingsGlyphRow("light · beam color", SettingsGlyph.BeamColor, p) {
                actions.openLight()
            }
        }
        val about: @Composable () -> Unit = {
            SettingsSectionHeading("ABOUT", SettingsGlyph.About, p)
            Prose(
                "Phosphor draws sound as light — a CRT oscilloscope in your pocket, " +
                    "sample-locked to what you hear. GPL-3.0. The beam remembers.",
                p.muted, modifier = Modifier.padding(bottom = Dim.gap),
            )
            // The bench keeps a service stamp: the REAL date the knobs were last
            // saved (Annotated rooms only — a calibration sticker, typeset).
            if (LocalRoomStyle.current.designators && state.calDate.isNotBlank()) {
                Mono(
                    "CAL · ${state.calDate}   S/N 003", p.muted, Type.dataXs,
                    Modifier.padding(bottom = Dim.gap), letterSpacing = 1.2.sp,
                )
            }
        }
        // One shared vertical scroll drives the sheet's fill (top-curl) and the
        // nested-scroll dismiss. Landscape lays the groups into two weighted columns;
        // portrait stacks them. Left column carries the tall SIGNAL + DISPLAY; the
        // right carries PERFORMANCE + REMOTE + ROOM & LIGHT + ABOUT (balanced by eye).
        Column(
            Modifier
                .bottomBloomOverscroll { !scroll.canScrollForward }
                .verticalScroll(scroll, overscrollEffect = null)
        ) {
            if (landscape) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dim.gapLg),
                ) {
                    Column(Modifier.weight(1f)) {
                        signal()
                        display()
                    }
                    Column(Modifier.weight(1f)) {
                        performance(true)
                        remote()
                        roomLight()
                        about()
                    }
                }
            } else {
                signal()
                display()
                performance(false)
                remote()
                roomLight()
                about()
            }
        }
    }
}

// What the sheets may ask of the host (grows per act).
interface SheetActions {
    fun setFullscreen(on: Boolean)
    fun setViewLock(on: Boolean)
    fun isScopeRotationLocked(): Boolean
    fun setScopeRotationLocked(locked: Boolean)
    fun isUiPlacementLocked(): Boolean
    fun setUiPlacementLocked(locked: Boolean)
    fun openFile()
    fun startMic()
    fun startCapture()
    fun startRemote()
    fun stopLive()
    fun captureConsentNeeded(): Boolean
    fun openCaptureMetadataSettings()
    fun setFps(value: Int)
    fun setOversample(n: Int)
    fun setGainAbsolute(g: Float)
    fun setGainAuto(on: Boolean)
    fun setBeamEnergy(e: Float)
    fun setGlow(g: Float)
    fun tapBeamRandom()
    fun setBeamRandomRange(lo: Float, hi: Float)
    fun tapGlowRandom()
    fun setGlowRandomRange(lo: Float, hi: Float)
    fun setGrid(on: Boolean)
    fun setHudMode(mode: Int)
    fun setRemoteLatencyMode(mode: Int)
    fun setRemoteNetworkMode(mode: Int)
    fun openRoom()
    fun openLight()
    fun remoteHosts(): List<Pair<String, Pair<String, Int>>>
    fun startRemoteHost(label: String, host: String, port: Int)
    fun setRemoteStreams(audio: Boolean, geometry: Boolean)
    fun disconnectRemote()
    fun openFolder()
}

// ── The remote flow (SOURCE ▸ REMOTE): hosts → toggles → desktop sources → library.
//    Read-only wire data (status/sources/listing) comes straight from PhosphorNative;
//    lifecycle actions go through the host interface. ──
@Composable
fun RemoteFlow(
    state: ScopeUiState,
    p: Palette,
    actions: SheetActions,
    onDismiss: () -> Unit,
) {
    var showSources by remember { mutableStateOf(false) }
    var browsing by remember { mutableStateOf(false) }
    var browseRoot by remember { mutableStateOf("") }
    var browsePath by remember { mutableStateOf("") }
    var sourcesJson by remember { mutableStateOf("") }
    var listingJson by remember { mutableStateOf("") }

    // Gentle wire poll while the remote panels are open (generation-gated on the JNI side).
    LaunchedEffect(state.remote, showSources, browsing) {
        while (state.remote && (showSources || browsing)) {
            sourcesJson = dev.phosphor.mobil3.PhosphorNative.remoteSources()
            listingJson = dev.phosphor.mobil3.PhosphorNative.remoteListing()
            kotlinx.coroutines.delay(400)
        }
    }

    actions.remoteHosts().forEach { (label, hostPort) ->
        val connected = state.remote && state.sourceLabel.contains(label)
        SheetRow("$label (Tailscale)", p, checked = connected) {
            if (!connected) {
                actions.startRemoteHost(label, hostPort.first, hostPort.second)
            }
        }
    }

    if (state.remote) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dim.gap)) {
            FlatKey("MUSIC " + if (state.remoteAudio) "· on" else "· off", p, active = state.remoteAudio) {
                actions.setRemoteStreams(!state.remoteAudio, state.remoteGeometry)
            }
            FlatKey("VISUALIZER " + if (state.remoteGeometry) "· on" else "· off", p, active = state.remoteGeometry) {
                actions.setRemoteStreams(state.remoteAudio, !state.remoteGeometry)
            }
        }
        Spacer(Modifier.height(Dim.gap))
        SheetRow("desktop sources…", p) {
            showSources = !showSources
            if (showSources) dev.phosphor.mobil3.PhosphorNative.remoteRequestSources()
        }
        if (showSources && sourcesJson.isNotBlank()) {
            runCatching { org.json.JSONObject(sourcesJson) }.getOrNull()?.let { s ->
                val arr = s.optJSONArray("sources")
                val selected = s.optString("selected")
                if (arr != null) for (i in 0 until arr.length()) {
                    val src = arr.getJSONObject(i)
                    val id = src.optString("id")
                    SheetRow(
                        src.optString("label", id), p,
                        checked = id == selected,
                    ) { dev.phosphor.mobil3.PhosphorNative.remoteChooseSource(id) }
                }
            }
        }
        SheetRow("browse library…", p) {
            browsing = !browsing
            if (browsing) {
                // Roots come from the relay's welcome; default to the first.
                val st = runCatching {
                    org.json.JSONObject(dev.phosphor.mobil3.PhosphorNative.remoteStatus())
                }.getOrNull()
                val libs = st?.optJSONObject("welcome")?.optJSONArray("libraries")
                if (libs != null && libs.length() > 0) {
                    browseRoot = libs.getJSONObject(0).optString("id", "music0")
                    browsePath = ""
                    dev.phosphor.mobil3.PhosphorNative.remoteBrowse(browseRoot, "")
                }
            }
        }
        if (browsing && listingJson.isNotBlank()) {
            runCatching { org.json.JSONObject(listingJson) }.getOrNull()?.let { l ->
                val path = l.optString("path")
                Mono(
                    "library › " + (path.ifBlank { "(root)" }), p.muted, Type.dataXs,
                    Modifier.padding(vertical = 4.dp),
                )
                if (path.isNotBlank()) {
                    SheetRow("‹ up", p) {
                        val parent = path.substringBeforeLast('/', "")
                        browsePath = parent
                        dev.phosphor.mobil3.PhosphorNative.remoteBrowse(browseRoot, parent)
                    }
                }
                val dirs = l.optJSONArray("dirs")
                if (dirs != null) for (i in 0 until dirs.length()) {
                    val d = dirs.getString(i)
                    SheetRow("$d /", p) {
                        browsePath = if (path.isBlank()) d else "$path/$d"
                        dev.phosphor.mobil3.PhosphorNative.remoteBrowse(browseRoot, browsePath)
                    }
                }
                val files = l.optJSONArray("files")
                if (files != null) for (i in 0 until files.length()) {
                    val f = files.getJSONObject(i)
                    val name = f.optString("name")
                    SheetRow(name, p) {
                        val full = if (path.isBlank()) name else "$path/$name"
                        dev.phosphor.mobil3.PhosphorNative.remotePlayFile(browseRoot, full)
                        onDismiss()
                    }
                }
            }
        }
        SheetRow("disconnect", p) { actions.disconnectRemote(); onDismiss() }
    }
}
