package dev.phosphor.mobil3.ui

import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.TextUnit
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class Sheet { NONE, SOURCE, MODE, LIGHT, INSTRUMENT, SETTINGS, ROOM, MANUAL, SIGNAL_CHECK }

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
            if (roomRadius > 0f) quadraticTo(0f, 0f, inset, 0f)
            else lineTo(0f, 0f)
        } else when (style.character) {
            ChromeCharacter.Glass -> quadraticTo(0f, 0f, inset, 0f)
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
            if (roomRadius > 0f) quadraticTo(width, 0f, width, shoulder)
            else lineTo(width, 0f)
        } else when (style.character) {
            ChromeCharacter.Glass -> quadraticTo(width, 0f, width, shoulder)
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
            quadraticTo(width, height, width - bottomRadius, height)
        } else lineTo(width, height)
        lineTo(bottomRadius, height)
        if (bottomRadius > 0f) {
            quadraticTo(0f, height, 0f, height - bottomRadius)
        } else lineTo(0f, height)
        close()
    }
}

// ── Sheet mechanics (shared): a safe-area floating card over the live scope;
// fill-driven top curl; drag-down / scrim-tap / close / Back to dismiss. ──
/**
 * The one pull-to-dismiss rule for every sheet (design/REDESIGN.md §5). Pure numbers in px.
 * Travel is the finger's; the card shows it with soft resistance.
 */
internal object SheetPullPolicy {
    const val CLOSE_DP = 96f
    const val FLICK_MIN_DP = 32f
    const val FLICK_DP_PER_SECOND = 700f
    private const val VELOCITY_WINDOW_MS = 100L
    const val RESISTANCE_DP = 240f

    fun offset(rawPx: Float, resistancePx: Float): Float {
        val raw = rawPx.coerceAtLeast(0f)
        return if (resistancePx <= 0f) raw else resistancePx * raw / (resistancePx + raw)
    }

    fun closes(rawPx: Float, velocityPxPerSecond: Float, closePx: Float, flickMinPx: Float, flickPx: Float,
        cancelled: Boolean = false): Boolean =
        !cancelled && (rawPx >= closePx || (rawPx >= flickMinPx && velocityPxPerSecond >= flickPx))



    /**
     * Finger speed from accumulated travel samples (time ms, travel px) over the last
     * 100 ms. Local pointer positions drift with the moving card, so they are not used.
     */
    fun velocity(samples: List<Pair<Long, Float>>): Float {
        val last = samples.lastOrNull() ?: return 0f
        val first = samples.firstOrNull { last.first - it.first <= VELOCITY_WINDOW_MS } ?: return 0f
        val dt = last.first - first.first
        return if (dt <= 0L) 0f else (last.second - first.second) * 1000f / dt
    }
}

/**
 * One gesture's history inside a sheet. A pointer sequence that scrolled the content or
 * moved the card is never also a tap: rows, keys and slider taps ask [allows] first.
 * The DOWN of each new gesture resets it (observed on the stationary scrim, Initial pass).
 */
internal class SheetGestureGuard {
    var moved = false
        private set

    fun down() { moved = false }
    fun markMoved() { moved = true }

    /**
     * The pointer sequence ended (all fingers up or cancelled), after its children handled it.
     * Suppression belongs to that sequence only: a later keyboard or TalkBack activation,
     * which has no DOWN of its own, is never blocked by an earlier scroll.
     */
    fun up() { moved = false }
    fun allows(): Boolean = !moved

    /** Wraps a tap action so it is dropped when this gesture already scrolled or pulled. */
    fun tap(action: () -> Unit): () -> Unit = { if (allows()) action() }
}

internal val LocalSheetGestureGuard = staticCompositionLocalOf<SheetGestureGuard?> { null }

/** True unless this gesture already scrolled the sheet or moved it. */
internal fun SheetGestureGuard?.allowsTap(): Boolean = this?.allows() != false

/** A tap action that ignores gestures which scrolled the sheet or moved it. */
@Composable
internal fun sheetTap(action: () -> Unit): () -> Unit {
    val guard = LocalSheetGestureGuard.current ?: return action
    val current by rememberUpdatedState(action)
    return remember(guard) { { if (guard.allows()) current() } }
}

internal class SheetDismissState(private val scope: CoroutineScope, private val resistancePx: Float = 0f) {
    data class Commitment(val edge: SheetEntry, val offsetPx: Float)

    val animation = Animatable(0f)
    var rawPx by mutableFloatStateOf(0f)
        private set
    var committed by mutableStateOf<Commitment?>(null)
        private set
    val offsetPx: Float get() = committed?.offsetPx ?: animation.value.coerceAtLeast(0f)

    private val samples = ArrayDeque<Pair<Long, Float>>()

    fun begin() {
        if (committed != null) return
        rawPx = rawFor(animation.value.coerceAtLeast(0f))
        samples.clear()
        scope.launch { if (committed == null) animation.stop() }
    }

    /**
     * Finger speed at release, in px/s, from accumulated travel. A release sample at
     * [nowMs] ages the history, so flick → hold still → release reads as still.
     */
    fun releaseVelocity(nowMs: Long): Float {
        samples.addLast(nowMs to rawPx)
        return SheetPullPolicy.velocity(samples.toList())
    }

    /**
     * Pointer and nested-scroll deltas are already finger motion (Compose maps both
     * positions through the current layout), so travel is their plain sum.
     */
    fun dragByFinger(delta: Float, nowMs: Long) {
        if (committed != null) return
        dragBy(delta)
        samples.addLast(nowMs to rawPx)
        while (samples.size > 2 && nowMs - samples.first().first > 200L) samples.removeFirst()
    }

    /** Inverse of the resistance curve, so a new grab continues from where the card sits. */
    private fun rawFor(offset: Float): Float =
        if (resistancePx <= 0f) offset
        else if (offset >= resistancePx) offset else offset * resistancePx / (resistancePx - offset)

    fun dragBy(delta: Float) {
        if (committed != null) return
        rawPx = (rawPx + delta).coerceAtLeast(0f)
        val target = SheetPullPolicy.offset(rawPx, resistancePx)
        scope.launch { if (committed == null) animation.snapTo(target) }
    }

    fun commit(entry: SheetEntry, fromDrag: Boolean, offsetPx: Float = animation.value.coerceAtLeast(0f)): Boolean {
        if (committed != null) return false
        committed = Commitment(
            SheetEntryPolicy.exit(entry, fromDrag, null),
            offsetPx,
        )
        scope.launch { animation.stop() }
        return true
    }

    fun settle(
        velocityY: Float,
        distancePx: Float,
        flickPx: Float,
        reduced: Boolean,
        style: RoomStyle,
        commitDrag: () -> Unit,
    ): Unit = settle(velocityY, distancePx, flickPx, reduced, style, 0f, false, commitDrag)

    fun settle(
        velocityY: Float,
        distancePx: Float,
        flickPx: Float,
        reduced: Boolean,
        style: RoomStyle,
        flickMinPx: Float,
        cancelled: Boolean = false,
        commitDrag: () -> Unit,
    ) {
        if (committed != null) return
        if (SheetPullPolicy.closes(rawPx, velocityY, distancePx, flickMinPx, flickPx, cancelled)) {
            commitDrag()
        } else {
            rawPx = 0f
            samples.clear()
            scope.launch {
                if (committed != null) return@launch
                animation.stop()
                if (committed != null) return@launch
                when {
                    reduced || style.motion == MotionFeel.Cut -> animation.snapTo(0f)
                    style.motion == MotionFeel.Springy -> animation.animateTo(
                        0f,
                        spring(dampingRatio = 0.72f, stiffness = 380f, visibilityThreshold = 0.5f),
                    )
                    else -> animation.animateTo(0f, tween(Motion.settle, easing = Motion.decelerate))
                }
            }
        }
    }
}

internal val LocalSheetEntryQuadrant = staticCompositionLocalOf { 0 }

@Composable
fun SheetHost(
    p: Palette,
    title: String,
    reduced: Boolean,
    onDismiss: () -> Unit,
    entryReveal: PullRevealState? = null,
    glyph: SettingsGlyph? = null,
    onClosing: () -> Unit = {},
    /** Two-column sheets may grow wider in landscape; reading sheets keep the console width. */
    wide: Boolean = false,
    body: @Composable () -> Unit,
) {
    val style = LocalRoomStyle.current
    val landscape = LocalChromeLandscape.current
    val uiLocked = LocalUiPlacementLocked.current
    val entry = SheetEntryPolicy.entry(landscape, uiLocked, LocalSheetEntryQuadrant.current)
    val currentEntry by rememberUpdatedState(entry)
    val slidesSideways = entry == SheetEntry.FROM_EDGE
    val entrySign = if (entry == SheetEntry.FROM_TOP) -1 else 1
    val sheetAlignment = when (entry) {
        SheetEntry.FROM_EDGE -> AbsoluteAlignment.BottomRight
        SheetEntry.FROM_TOP -> Alignment.TopCenter
        SheetEntry.FROM_BOTTOM -> Alignment.BottomCenter
    }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var availableHeightPx by remember { mutableIntStateOf(0) }
    var sheetHeightPx by remember { mutableIntStateOf(0) }
    var sheetWidthPx by remember { mutableIntStateOf(0) }
    val fillFraction = if (availableHeightPx > 0) {
        sheetHeightPx.toFloat() / availableHeightPx
    } else 0f
    val curl = sheetCurlProgress(fillFraction, style, reduced)
    val sheetShape = remember(style, curl, density) {
        sheetCardShape(style, curl, density)
    }
    // Keep the sheet composed until its close animation finishes.
    val openState = remember {
        MutableTransitionState(entryReveal != null).apply { targetState = true }
    }
    val gestureGuard = remember { SheetGestureGuard() }
    val resistancePx = with(density) { SheetPullPolicy.RESISTANCE_DP.dp.toPx() }
    val dismissal = remember(resistancePx) { SheetDismissState(scope, resistancePx) }
    val currentClosing by rememberUpdatedState(onClosing)
    val commitDismiss: (Boolean) -> Unit = { fromDrag ->
        if (openState.targetState && dismissal.commit(currentEntry, fromDrag)) {
            currentClosing()
            openState.targetState = false
        }
    }
    val dismiss = { commitDismiss(false) }
    val exit = dismissal.committed?.edge ?: entry
    val exitSign = if (exit == SheetEntry.FROM_TOP) -1 else 1
    val dismissOffset = dismissal.animation
    val dismissDistancePx = with(density) { SheetPullPolicy.CLOSE_DP.dp.toPx() }
    val dismissFlickMinPx = with(density) { SheetPullPolicy.FLICK_MIN_DP.dp.toPx() }
    val dismissFlickPx = with(density) { SheetPullPolicy.FLICK_DP_PER_SECOND.dp.toPx() }
    val beginDismiss = { dismissal.begin() }
    val dragDismissBy: (Float) -> Unit = { delta ->
        dismissal.dragByFinger(delta, android.os.SystemClock.uptimeMillis())
    }
    val settleDismiss: (Float) -> Unit = { reported ->
        val velocityY = maxOf(reported, dismissal.releaseVelocity(android.os.SystemClock.uptimeMillis()))
        dismissal.settle(velocityY, dismissDistancePx, dismissFlickPx, reduced, style, dismissFlickMinPx) {
            commitDismiss(true)
        }
    }
    // A cancelled pull (another pointer, the system) always goes home, whatever its travel.
    val cancelDismiss: () -> Unit = {
        dismissal.settle(0f, dismissDistancePx, dismissFlickPx, reduced, style, dismissFlickMinPx, cancelled = true) {}
    }
    // One mechanism for every sheet (REDESIGN §5). Content scrolls first; only the finger's
    // unconsumed downward remainder at the top pulls the card. A fling never pulls it.
    val dismissNestedScroll = remember(style.motion, reduced, dismissDistancePx, dismissFlickPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (dismissal.committed != null) return Offset.Zero
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (available.y != 0f) gestureGuard.markMoved()
                if (dismissal.rawPx <= 0f || available.y >= 0f) return Offset.Zero
                // A reversal first pushes the displaced sheet home; only the remainder
                // scrolls content away from its top edge.
                val consumedY = available.y.coerceAtLeast(-dismissal.rawPx)
                dragDismissBy(consumedY)
                return Offset(0f, consumedY)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (dismissal.committed != null) return Offset.Zero
                if (source == NestedScrollSource.UserInput && (consumed.y != 0f || available.y != 0f)) gestureGuard.markMoved()
                if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                if (dismissal.rawPx == 0f) beginDismiss()
                dragDismissBy(available.y)
                return Offset(0f, available.y)
            }

            // Release: a displaced card decides here and takes the whole fling, so content
            // never scrolls under a card that is on its way out or home.
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (dismissal.committed != null || dismissal.rawPx <= 0f) return Velocity.Zero
                settleDismiss(available.y.coerceAtLeast(0f))
                return available
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (dismissal.committed == null && dismissal.rawPx > 0f) settleDismiss(0f)
                return Velocity.Zero
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
            // The scrim never moves: each new DOWN resets the gesture guard here, before children.
            .pointerInput(gestureGuard) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.any { it.pressed && !it.previousPressed } &&
                            event.changes.count { it.pressed } == 1) gestureGuard.down()
                        // The same event's Final pass comes after every child: clear on the last UP.
                        val final = awaitPointerEvent(PointerEventPass.Final)
                        if (final.changes.none { it.pressed }) gestureGuard.up()
                    }
                }
            }
            .pointerInput(Unit) { detectTapGestures(onTap = { dismiss() }) },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    chromeSafeDrawingInsets(Dim.cardMarginH, Dim.cardMarginBottom)
                )
                .padding(
                    start = Dim.cardMarginH, end = Dim.cardMarginH,
                    top = if (entry == SheetEntry.FROM_TOP) Dim.cardMarginBottom else 0.dp,
                    bottom = if (entry == SheetEntry.FROM_TOP) 0.dp else Dim.cardMarginBottom,
                )
                .onSizeChanged { availableHeightPx = it.height },
            contentAlignment = sheetAlignment,
        ) {
            AnimatedVisibility(
                visibleState = openState,
                enter = if (reduced) fadeIn() else if (slidesSideways)
                // Edge-anchored: enter along the edge, not up the screen.
                    slideInHorizontally(styleSpec(false, style, Motion.sheet, Motion.decelerate)) { it / 3 } +
                        fadeIn(styleSpec(false, style, Motion.sheet))
                else if (style.motion == MotionFeel.Springy)
                // The one room that bounces (glass): a gentle spring settle.
                    slideInVertically(
                        spring(dampingRatio = 0.72f, stiffness = 380f,
                            visibilityThreshold = IntOffset.VisibilityThreshold)
                    ) { entrySign * (it / 3) } + fadeIn(styleSpec(false, style, Motion.sheet))
                else
                    slideInVertically(styleSpec(false, style, Motion.sheet, Motion.decelerate)) { entrySign * (it / 3) } +
                        fadeIn(styleSpec(false, style, Motion.sheet)),
                exit = if (reduced) fadeOut() else if (exit == SheetEntry.FROM_EDGE)
                    slideOutHorizontally(styleSpec(false, style, Motion.settle, Motion.accelerate)) { it / 2 } +
                        fadeOut(styleSpec(false, style, Motion.settle, Motion.accelerate))
                else
                    slideOutVertically(styleSpec(false, style, Motion.settle, Motion.accelerate)) { exitSign * (it / 2) } +
                        fadeOut(styleSpec(false, style, Motion.settle, Motion.accelerate)),
            ) {
                Column(
                    Modifier
                        .offset {
                            IntOffset(
                                0,
                                dismissal.offsetPx.roundToInt(),
                            )
                        }
                        .then(
                            // Centred landscape matches the console's own max width, so
                            // the card reads as the same object growing out of the bar
                            // rather than a differently-sized panel arriving beside it.
                            if (landscape && wide) Modifier.widthIn(max = Dim.landscapeWideSheetMaxWidth)
                                .fillMaxWidth()
                            else if (slidesSideways) Modifier.widthIn(max = Dim.landscapeSheetMaxWidth)
                                .fillMaxWidth()
                            else if (landscape) Modifier.widthIn(max = Dim.landscapeConsoleMaxWidth)
                                .fillMaxWidth()
                            else Modifier.fillMaxWidth()
                        )
                        .onSizeChanged {
                            sheetHeightPx = it.height
                            sheetWidthPx = it.width
                            // The reveal travels along whichever axis the card enters
                            // on, so a sideways card is not asked to cover a screen's
                            // height before it arrives.
                            entryReveal?.setTravelPx(
                                SheetEntryPolicy.travelPx(
                                    landscape, uiLocked, it.width, it.height,
                                ).toFloat(),
                            )
                        }
                        .graphicsLayer {
                            translationX = 0f
                            translationY = 0f
                            alpha = 1f
                            entryReveal?.let { reveal ->
                                val progress = reveal.progress
                                alpha = progress
                                if (slidesSideways) {
                                    translationX = (1f - progress) * sheetWidthPx
                                } else {
                                    translationY = entrySign * (1f - progress) * sheetHeightPx
                                }
                            }
                        }
                        .stageChromeBounds(
                            StageChromeBounds.Card.Sheet,
                            !openState.isIdle || dismissOffset.isRunning ||
                                entryReveal?.animation?.isRunning == true ||
                                (entryReveal != null && entryReveal.progress < 1f),
                            entryReveal?.progress,
                        )
                        .nestedScroll(dismissNestedScroll)
                        .clip(sheetShape)
                        .background(
                            // Glass keeps a hint of the beam through the whole card, but
                            // text needs a steady plane: one 90% pane, no inset plate.
                            if (style.character == ChromeCharacter.Glass) p.surface.copy(alpha = 0.90f)
                            else p.surface,
                        )
                        .border(Dim.hairline, p.lineStrong, sheetShape)
                        // One plane edge to edge: no inset plate inside the card (REDESIGN §4).
                        .padding(start = Dim.sheetPadH, end = Dim.sheetPadH, top = 6.dp, bottom = 12.dp)
                        // Swallow taps so only the surrounding scrim dismisses.
                        .pointerInput(Unit) { detectTapGestures(onTap = {}) },
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .pointerInput(style.motion, reduced) {
                                // The header always pulls; release speed counts as a flick
                                // (measured from finger travel, see SheetDismissState).
                                detectVerticalDragGestures(
                                    onDragStart = { beginDismiss() },
                                    onDragEnd = { settleDismiss(0f) },
                                    onDragCancel = { cancelDismiss() },
                                ) { change, delta ->
                                    gestureGuard.markMoved()
                                    if (delta > 0f || dismissal.rawPx > 0f) {
                                        change.consume()
                                        dragDismissBy(delta)
                                    }
                                }
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // A sheet is one coherent pinned document: the header stays with
                        // the body (a lone rotated title read as "messed up" — Ben).
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            glyph?.let {
                                SettingsGlyphIcon(it, p, 16.dp)
                                Spacer(Modifier.width(10.dp))
                            }
                            Mono(title, p.ink, Type.title, letterSpacing = 0.06.em)
                        }
                        Box(
                            // The ✕ glyph's right edge lines up with the content edge.
                            Modifier.offset(x = 15.dp).size(48.dp)
                                .semantics { contentDescription = "Close ${title.lowercase()}" }
                                .clickable(onClick = dismiss),
                            contentAlignment = Alignment.Center,
                        ) {
                            SheetChromeMark(SheetChromeVector.Close, p.ink2, size = 18.dp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    CompositionLocalProvider(LocalSheetGestureGuard provides gestureGuard) { body() }
                }
            }
        }
    }
}

// Grouped scope modes and geometry controls (REDESIGN §2 MODE). No prose.
@Composable
fun ModeSheet(
    state: ScopeUiState,
    p: Palette,
    reduced: Boolean,
    onPick: (Int) -> Unit,
    onGeomFx: (Int) -> Unit,
    onGeomAmount: (Float) -> Unit,
    onBanModes: (Set<Int>) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val p = p.sheetText()
    val view = LocalView.current
    val scroll = rememberScrollState()
    CompositionLocalProvider(LocalSettingsControlAccess provides true) {
    SheetHost(p, "MODE", reduced, onDismiss, glyph = SettingsGlyph.Display, wide = true) {
        Column(Modifier.verticalScroll(scroll, overscrollEffect = null)) {
          SheetColumns(left = {
            GroupHeading("AUTOMATIC", p, first = true)
            val randomActive = state.randomModeArmed
            ModeRow(
                lead = { Mono("⚄", if (randomActive) p.accent else p.ink2, Type.dataXl, Modifier.width(24.dp)) },
                label = "random", trailing = if (randomActive) state.modeTag else "new face",
                active = randomActive, p = p,
            ) { Haptics.medium(view); state.requestRandomMode() }
            ModeGroups.forEach { (heading, indices) ->
                GroupHeading(heading, p)
                indices.forEach { i ->
                    ModeRow(
                        lead = { Box(Modifier.width(24.dp)) { ModeGlyph(i, if (state.modeIndex == i) p.accent else p.ink2) } },
                        label = ModeLabels[i], trailing = ModeTags[i], active = state.modeIndex == i, p = p,
                    ) { Haptics.medium(view); onPick(i) }
                }
            }
          }, right = { starts ->
            GroupHeading("GEOMETRY", p, first = starts)
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                ChoiceCells(GeomFxLabels.mapIndexed { i, label -> i to label }, state.geomFx, p) {
                    Haptics.medium(view); onGeomFx(it)
                }
            }
            RowDivider(p)
            if (state.geomFx != 0) {
                SettingSlider("amount", state.geomAmount, 0f, 1f, p, { "%.0f %%".format(it * 100) }) { onGeomAmount(it) }
            }
            // Always here, so faces can be skipped before the first roll (Auditor L4).
            GroupHeading("SKIP ON ⚄", p)
            val banned = state.randomBanModes
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeTags.indices.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { i ->
                            SkipCell(ModeTags[i], ModeLabels[i], i in banned, p, Modifier.weight(1f)) {
                                onBanModes(ModeBans.toggle(banned, i, ModeLabels.size))
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
          })
            Spacer(Modifier.height(12.dp))
        }
    }
    }
}

internal val ModeGroups = listOf(
    "XY" to listOf(0, 1, 2, 3),
    "3D" to listOf(4, 5),
    "TIME" to listOf(6, 7),
    "SPECTRUM" to listOf(8, 9, 10),
)

/** Faces struck from ⚄. At least two must stay in play: the tap that would starve the die is refused. */
internal object ModeBans {
    fun toggle(banned: Set<Int>, mode: Int, total: Int): Set<Int> = when {
        mode in banned -> banned - mode
        total - banned.size > 2 -> banned + mode
        else -> banned
    }
}

@Composable
private fun ModeRow(lead: @Composable () -> Unit, label: String, trailing: String, active: Boolean, p: Palette,
    onClick: () -> Unit) {
    val tap = sheetTap(onClick)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .semantics { selected = active }
            .clickable(role = Role.Button, onClick = tap)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        lead()
        Spacer(Modifier.width(12.dp))
        Mono(label, if (active) p.accent else p.ink, Type.label, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Mono(trailing, if (active) p.accent else p.ink2, Type.value)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(10.dp).background(if (active) p.accent else Color.Transparent))
    }
    RowDivider(p)
}

@Composable
private fun SkipCell(tag: String, name: String, skipped: Boolean, p: Palette, modifier: Modifier, onClick: () -> Unit) {
    val tap = sheetTap(onClick)
    Row(
        modifier.heightIn(min = 48.dp)
            .border(Dim.hairline, if (skipped) p.accent else p.line)
            .toggleable(skipped, role = Role.Checkbox) { tap() }
            .semantics { contentDescription = "skip $name on random" }
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (skipped) {
            Box(Modifier.size(8.dp).background(p.accent))
            Spacer(Modifier.width(6.dp))
        }
        Mono(tag, if (skipped) p.accent else p.ink2, Type.value, maxLines = 1)
    }
}

@Composable
internal fun SettingsSheet(
    state: ScopeUiState,
    p: Palette,
    reduced: Boolean,
    actions: SheetActions,
    focusValue: Float,
    onFocus: (Float) -> Unit,
    presentation: SettingsPresentationState,
    setups: InstrumentPresetActions,
    entryReveal: PullRevealState? = null,
    onDismiss: () -> Unit,
) {
    // The scroll position outlives one opening, so Settings reopens where it was left.
    val scroll = presentation.scroll
    val p = p.sheetText()
    CompositionLocalProvider(LocalSettingsControlAccess provides true) {
    SheetHost(p, "SETTINGS", reduced, onDismiss, entryReveal, glyph = SettingsGlyph.Knob,
        onClosing = actions::cancelAppearancePreview, wide = true,
    ) {
        // One flat scroll of headed groups (REDESIGN §2). No index, no pages.
        LaunchedEffect(Unit) {
            // Setups live inline here, so their collection loads with Settings.
            if (state.instrumentCollection == null) setups.openInstrumentPresets()
        }
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(scroll, overscrollEffect = null)
        ) {
            SettingsBody(state, p, actions, setups, focusValue, onFocus)
            Spacer(Modifier.height(24.dp))
        }
    }
}

}

// What the sheets may ask of the host (grows per act).
interface SheetActions : AppearanceActions {
    fun toggleDisplayPause() {}
    fun resetInspection() {}
    fun setPauseBlack(black: Boolean) {}
    fun showFloatingHud() {}
    fun hideFloatingHud() {}
    fun setFloatingHudEnabled(on: Boolean) {}
    fun setFloatingHudTransparent(on: Boolean) {}
    fun jumpToQueue(index: Int)
    fun setControlsAlwaysVisible(on: Boolean)
    fun setPinScreenBrightness(on: Boolean)
    fun setHdrRequested(on: Boolean)
    fun setDefaultSource(kind: String)
    fun setAutomaticPermissionPopup(on: Boolean)
    fun setPipAutoEnter(on: Boolean)
    fun enterPictureInPicture()
    fun setDoubleTapPlayback(on: Boolean)
    fun setFullscreen(on: Boolean)
    fun setLingerBackground(on: Boolean)
    fun setViewLock(on: Boolean)
    fun setDeveloperView(on: Boolean)
    fun isScopeRotationLocked(): Boolean
    fun setScopeRotationLocked(locked: Boolean)
    fun isUiPlacementLocked(): Boolean
    fun setUiPlacementLocked(locked: Boolean)
    fun openFile()
    fun exportSettings()
    fun importSettings()
    fun startMic()
    fun chooseMicrophone(id: Int)
    /** Choose an idle input and start it (one tap in SRC). */
    fun chooseAndStartMicrophone(id: Int) = chooseMicrophone(id)
    fun confirmMicrophoneBluetooth(accept: Boolean)
    fun setIncludeMicrophone(on: Boolean)
    fun setMicrophoneMixLevel(microphone: Boolean, value: Float)
    fun retryMicrophone()
    fun stopMicrophone()
    fun startCapture()
    fun startStandardCapture() {}
    fun setRootCapture(enabled: Boolean) {}
    fun openRootManager() {}
    fun startRemote()
    fun stopLive()
    fun captureConsentNeeded(): Boolean
    fun openCaptureMetadataSettings()
    fun setFps(value: Int)
    fun setOversample(n: Int)
    fun setGainAbsolute(g: Float)
    fun setGainAuto(on: Boolean)
    fun setAutoFrameScale(scale: Float)
    fun resetAutoFrameScale()
    fun finishAutoFrameScale()
    fun setBeamEnergy(e: Float)
    fun setGlow(g: Float)
    fun tapBeamRandom()
    fun setBeamRandomRange(lo: Float, hi: Float)
    fun tapGlowRandom()
    fun setGlowRandomRange(lo: Float, hi: Float)
    fun setGrid(on: Boolean)
    fun setGridData(on: Boolean)
    fun setHudMode(mode: Int)
    fun setRemoteLatencyMode(mode: Int)
    fun openRoom()
    fun openLight()
    fun openInstrument() {}
    fun openManual()
    fun openLink(url: String)
    fun remoteHosts(): List<Pair<String, Pair<String, Int>>>
    fun saveRemoteHost(
        existingHost: String,
        existingPort: Int,
        label: String,
        host: String,
        port: String,
    ): String?
    fun removeRemoteHost(host: String, port: Int): String?
    fun startRemoteHost(label: String, host: String, port: Int)
    fun setRemoteStreams(audio: Boolean, geometry: Boolean)
    fun disconnectRemote()
    fun openFolder()
}
