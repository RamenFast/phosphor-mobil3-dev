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
internal class SheetDismissState(private val scope: CoroutineScope) {
    data class Commitment(val edge: SheetEntry, val offsetPx: Float)

    val animation = Animatable(0f)
    var rawPx by mutableFloatStateOf(0f)
        private set
    var committed by mutableStateOf<Commitment?>(null)
        private set
    val offsetPx: Float get() = committed?.offsetPx ?: animation.value.coerceAtLeast(0f)

    fun begin() {
        if (committed != null) return
        rawPx = animation.value.coerceAtLeast(0f)
        scope.launch { if (committed == null) animation.stop() }
    }

    fun dragBy(delta: Float) {
        if (committed != null) return
        rawPx = (rawPx + delta).coerceAtLeast(0f)
        val target = rawPx
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
    ) {
        if (committed != null) return
        if (rawPx >= distancePx || velocityY >= flickPx) {
            commitDrag()
        } else {
            rawPx = 0f
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
                    else -> animation.animateTo(
                        0f,
                        styleSpec(false, style, Motion.settle, Motion.decelerate),
                    )
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
    settingsScroll: ScrollState? = null,
    settingsSourceKey: Any? = null,
    onSettingsInput: () -> Unit = {},
    onClosing: () -> Unit = {},
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
    val dismissal = remember { SheetDismissState(scope) }
    val settingsDismiss = if (settingsScroll != null) remember { SettingsSheetDismiss(scope) } else null
    val currentSettingsInput by rememberUpdatedState(onSettingsInput)
    val currentClosing by rememberUpdatedState(onClosing)
    DisposableEffect(settingsDismiss) {
        onDispose { settingsDismiss?.retire() }
    }
    val commitDismiss: (Boolean) -> Unit = { fromDrag ->
        if (openState.targetState && (if (settingsDismiss == null) dismissal.commit(currentEntry, fromDrag)
            else dismissal.commit(currentEntry, fromDrag, settingsDismiss.offsetPx))) {
            settingsDismiss?.retire()
            currentClosing()
            if (settingsDismiss != null) currentSettingsInput()
            openState.targetState = false
        }
    }
    val dismiss = { commitDismiss(false) }
    val exit = dismissal.committed?.edge ?: entry
    val exitSign = if (exit == SheetEntry.FROM_TOP) -1 else 1
    val dismissOffset = dismissal.animation
    val dismissDistancePx = with(density) { Dim.sheetDismissDistance.toPx() }
    val dismissFlickPx = with(density) { Dim.chromeFlickVelocity.toPx() }
    val beginDismiss = { dismissal.begin() }
    val dragDismissBy: (Float) -> Unit = { delta -> dismissal.dragBy(delta) }
    val settleDismiss: (Float) -> Unit = { velocityY ->
        dismissal.settle(velocityY, dismissDistancePx, dismissFlickPx, reduced, style) {
            commitDismiss(true)
        }
    }
    // A scroll child first consumes every ordinary scroll delta. Only its unconsumed
    // downward remainder at TOP reaches this parent, becoming the sheet pull.
    val dismissNestedScroll = remember(style.motion, reduced, dismissDistancePx, dismissFlickPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (dismissal.committed != null) return Offset.Zero
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
                if (available.y <= 0f) return Offset.Zero
                if (dismissal.rawPx == 0f) beginDismiss()
                dragDismissBy(available.y)
                return Offset(0f, available.y)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (dismissal.committed != null) return Velocity.Zero
                if (dismissal.rawPx <= 0f) return Velocity.Zero
                settleDismiss(available.y.coerceAtLeast(0f))
                return if (available.y > 0f) Velocity(0f, available.y) else Velocity.Zero
            }
        }
    }
    val settingsNestedScroll = if (settingsDismiss != null && settingsScroll != null) {
        remember(settingsDismiss, settingsScroll, density.density) {
            settingsDismiss.nestedScroll(settingsScroll, density.density) { currentSettingsInput() }
        }
    } else null
    val currentSettingsClose by rememberUpdatedState({ commitDismiss(true) })
    val settingsGeometry = listOf(entry, landscape, uiLocked, density.density, density.fontScale,
        availableHeightPx, sheetWidthPx, sheetHeightPx, settingsSourceKey)
    LaunchedEffect(openState.targetState, openState.isIdle) {
        if (!openState.targetState && openState.isIdle) onDismiss()
    }
    BackHandler { dismiss() }
    Box(
        Modifier
            .fillMaxSize()
            .then(if (settingsDismiss != null) Modifier.settingsPointerObserver(
                settingsDismiss, density.density, settingsGeometry, reduced,
                onInput = { currentSettingsInput() }, onClose = { currentSettingsClose() },
            ) else Modifier)
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
                                if (settingsDismiss == null) dismissal.offsetPx.roundToInt()
                                else (dismissal.committed?.offsetPx ?: settingsDismiss.offsetPx).roundToInt(),
                            )
                        }
                        .then(
                            // Centred landscape matches the console's own max width, so
                            // the card reads as the same object growing out of the bar
                            // rather than a differently-sized panel arriving beside it.
                            if (slidesSideways) Modifier.widthIn(max = Dim.landscapeSheetMaxWidth)
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
                            !openState.isIdle || dismissOffset.isRunning || settingsDismiss?.returning == true ||
                                entryReveal?.animation?.isRunning == true ||
                                (entryReveal != null && entryReveal.progress < 1f),
                            entryReveal?.progress,
                        )
                        .nestedScroll(settingsNestedScroll ?: dismissNestedScroll)
                        .clip(sheetShape)
                        .background(
                            if (style.character == ChromeCharacter.Glass)
                                p.surface.copy(alpha = Dim.sheetAlpha * style.panelAlphaScale)
                            else p.surface,
                        )
                        .border(Dim.hairline, p.lineStrong, sheetShape)
                        .padding(Dim.sheetPad)
                        .then(if (style.character == ChromeCharacter.Glass) Modifier.background(p.surface) else Modifier)
                        // Swallow taps so only the surrounding scrim dismisses.
                        .pointerInput(Unit) { detectTapGestures(onTap = {}) },
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .then(if (settingsDismiss != null) Modifier
                                .heightIn(min = 48.dp)
                                .settingsHeaderDrag(settingsDismiss, density.density, reduced)
                            else Modifier.pointerInput(style.motion, reduced) {
                                detectVerticalDragGestures(
                                    onDragStart = { beginDismiss() },
                                    onDragEnd = { settleDismiss(0f) },
                                    onDragCancel = { settleDismiss(0f) },
                                ) { change, delta ->
                                    if (delta > 0f || dismissal.rawPx > 0f) {
                                        change.consume()
                                        dragDismissBy(delta)
                                    }
                                }
                            }),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // A sheet is one coherent pinned document: the header stays with
                        // the body (a lone rotated title read as "messed up" — Ben).
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            glyph?.let {
                                SettingsGlyphIcon(it, p, 15.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Mono(title, p.ink2, Type.data)
                        }
                        Box(
                            (if (settingsDismiss != null) Modifier.size(48.dp)
                                .semantics { contentDescription = "Close settings" }
                                .clickable(onClick = dismiss)
                            else Modifier.size(48.dp).semantics { contentDescription = "Close $title" }
                                .clickable(onClick = dismiss)),
                            contentAlignment = Alignment.Center,
                        ) {
                            SheetChromeMark(SheetChromeVector.Close, p.ink2, size = 18.dp)
                        }
                    }
                    Spacer(Modifier.height(Dim.gapLg))
                    if (style.character == ChromeCharacter.Glass) {
                        Prose("Glass text uses an opaque readability surface. The outer material stays translucent.", p.ink)
                    }
                    if (settingsDismiss?.interrupted == true) {
                        Prose("Drag paused after delayed input. Use Close or Back, then reopen Settings to retry.", p.ink)
                    }
                    CompositionLocalProvider(LocalSettingsGestureOwner provides settingsDismiss) { body() }
                }
            }
        }
    }
}

// Labels sit above the lane so narrow sheets keep the full usable track width.
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
    val unit = remember { SliderGeometry(1f, 0f) }
    val accessible = LocalSettingsControlAccess.current
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        if (accessible) {
            Mono(label, p.ink2, Type.data)
            Mono(format(value), p.ink, Type.data)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Mono(label, p.ink2, Type.data)
                Mono(format(value), p.ink, Type.data)
            }
        }
        val range = if (accessible) Modifier.settingsFocusBorder(p).settingsRange(label, format(value),
            SettingsRangeAction(value, min, max, onChange)) else Modifier
        SliderLane(p, unit.fraction(value, min, max), Modifier.fillMaxWidth().then(range)) {
            onChange(unit.valueAt(it, min, max))
        }
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
    val grab = remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val unit = remember { SliderGeometry(1f, 0f) }
    val accessible = LocalSettingsControlAccess.current
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            Modifier.fillMaxWidth().then(
                if (onLabelTap != null && accessible) Modifier.heightIn(min = 48.dp)
                    .toggleable(armed, role = Role.Checkbox) { onLabelTap() }
                else if (onLabelTap != null) Modifier.clickable { onLabelTap() } else Modifier
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
            if (accessible) {
                Column(Modifier.weight(1f)) {
                    Mono(label, if (armed) p.accent else p.ink2, Type.data)
                    Mono("${format(lo)}–${format(hi)}", p.ink, Type.dataXs)
                }
            } else {
                Mono(label, if (armed) p.accent else p.ink2, Type.data)
                Spacer(Modifier.weight(1f))
                Mono("${format(lo)}–${format(hi)}", p.ink, Type.dataXs)
            }
        }
        SliderLane(
            p, unit.fraction(lo, min, max), Modifier.fillMaxWidth(),
            highFraction = unit.fraction(hi, min, max),
            onStart = { grab.intValue = unit.nearestThumb(it, lo, hi, min, max) },
        ) {
            val moved = unit.moveThumb(grab.intValue, it, lo, hi, min, max)
            onChange(moved.first, moved.second)
        }
        if (accessible) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f).heightIn(min = 48.dp)
                    .settingsFocusBorder(p)
                    .settingsRange("$label lower", format(lo), SettingsRangeAction(lo, min, hi) { onChange(it, hi) })
                    .padding(6.dp)) {
                    Mono("LOWER", p.muted, Type.dataXs)
                    Mono(format(lo), p.ink)
                }
                Column(Modifier.weight(1f).heightIn(min = 48.dp)
                    .settingsFocusBorder(p)
                    .settingsRange("$label upper", format(hi), SettingsRangeAction(hi, lo, max) { onChange(lo, it) })
                    .padding(6.dp)) {
                    Mono("UPPER", p.muted, Type.dataXs)
                    Mono(format(hi), p.ink)
                }
            }
        }
    }
}

// Trailing action keys sit beside prose only when the sheet column is wide
// and type is not enlarged. Narrow columns or large fontScale drop the key
// below so the prose keeps a full-width line and the key stays a ≥48dp target.
private val TrailingActionRailMinWidth = 340.dp
private const val TrailingActionRailMaxFontScale = 1.3f

private fun trailingActionKeepsRail(columnWidth: Dp, fontScale: Float): Boolean =
    columnWidth >= TrailingActionRailMinWidth && fontScale < TrailingActionRailMaxFontScale

@Composable
private fun TrailingActionRow(
    prose: String,
    keyLabel: String,
    p: Palette,
    onClick: () -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = Dim.gap)) {
        val keepRail = trailingActionKeepsRail(maxWidth, fontScale)
        if (keepRail) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dim.gap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Prose(prose, p.muted, modifier = Modifier.weight(1f))
                FlatKey(keyLabel, p, onClick = onClick)
            }
        } else {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Dim.gap),
            ) {
                Prose(prose, p.muted, modifier = Modifier.fillMaxWidth())
                FlatKey(keyLabel, p, onClick = onClick)
            }
        }
    }
}

// Source selection, capture disclosure, microphone, and PC relay controls.
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
              .verticalScroll(scroll, overscrollEffect = null)
      ) {
        if (consentCard) {
            Prose(
                "Android is about to say “start recording or casting” and ask you to " +
                    "SHARE YOUR SCREEN. That's the system's blanket wording for media " +
                    "capture — what phosphor actually takes is the AUDIO: the sound " +
                    "other apps play (Spotify works today) becomes light on this glass.",
                p.ink, modifier = Modifier.padding(bottom = Dim.gap),
            )
            Prose(
                "Nothing is recorded, nothing leaves your phone, no data is taken — " +
                    "ever. The source is public, so you don't have to take our word " +
                    "for it. LIVE turns it off any time.",
                p.muted, modifier = Modifier.padding(bottom = Dim.gap),
            )
            LinkCard(
                "read the source", "github.com/RamenFast/phosphor-mobil3", p,
            ) { actions.openLink("https://github.com/RamenFast/phosphor-mobil3") }
            Spacer(Modifier.height(Dim.gapLg))
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
        SheetRow(
            "open file…", p,
            checked = state.sourceLabel == "deck" && state.queueTitles.size <= 1,
            glyph = SettingsGlyph.File,
        ) { actions.openFile(); onDismiss() }
        SheetRow(
            "open folder → queue", p,
            checked = state.sourceLabel == "deck" && state.queueTitles.size > 1,
            glyph = SettingsGlyph.Folder,
        ) { actions.openFolder(); onDismiss() }
        SectionHeading("QUEUE", p)
        if (state.queueTitles.isEmpty()) {
            Prose("Open a folder to load its supported audio as a queue.", p.muted)
        } else {
            LazyColumn(Modifier.height(260.dp)) {
                itemsIndexed(state.queueTitles) { i, title ->
                    val active = i == state.queueIndex
                    Row(
                        Modifier.fillMaxWidth()
                            .border(Dim.hairline, if (active) p.accent else p.line)
                            .clickable { actions.jumpToQueue(i) }
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
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeading("OTHER APPS · LOCAL MUSIC PLAYBACK", p)
            Spacer(Modifier.weight(1f))
            // Compatibility marks, not buttons: the apps known to feed the beam.
            // SoundCloud is deliberately NOT here. It sets ALLOW_AUDIO_CAPTURE_BY_NONE,
            // so its audio arrives as silence no matter what we do, and showing its mark
            // beside apps that work would promise something the app cannot deliver.
            listOf(
                SettingsGlyph.Spotify, SettingsGlyph.AppleMusic,
                SettingsGlyph.Vlc, SettingsGlyph.Mpv,
            ).forEach { mark ->
                SettingsGlyphIcon(mark, p, 14.dp)
                Spacer(Modifier.width(5.dp))
            }
            Mono("…", p.muted, Type.dataXs)
        }
        SheetRow(
            "everything playing", p,
            checked = state.live && state.sourceLabel == "capture",
            glyph = SettingsGlyph.Capture,
        ) {
            if (actions.captureConsentNeeded()) consentCard = true
            else { actions.startCapture(); onDismiss() }
        }
        MicrophoneMixControls(state, p, actions)
        if (dev.phosphor.mobil3.RootCapturePolicy.PRODUCT_AVAILABLE && (state.rootCaptureEnabled || state.captureRoot)) {
            Prose("Root input: 16 kHz mono · duplicated mono. Protected or NO_SYSTEM_CAPTURE sources remain excluded.", p.muted)
            SheetRow("use standard capture · Android consent", p, checked = state.live && state.sourceLabel == "capture" && !state.captureRoot) {
                actions.startStandardCapture(); onDismiss()
            }
            if (!state.live) {
                Row(horizontalArrangement = Arrangement.spacedBy(Dim.gap)) {
                    if (state.rootCaptureEnabled) FlatKey("RETRY ROOT", p) { actions.startCapture() }
                    FlatKey("ROOT MANAGER", p) { actions.openRootManager() }
                }
            }
        }
        if (state.captureStatus.isNotBlank()) {

            Prose(
                buildString {
                    append(state.captureStatus)
                    if (state.captureFix.isNotBlank()) append(" · fix: ${state.captureFix}")
                },
                if (state.live) p.accent else p.muted,
                modifier = Modifier.padding(bottom = Dim.gap),
            )
        }
        TrailingActionRow(
            "Standard capture uses Android's “share your screen” consent for audio. " +
                "Root capture is deferred from this release. Nothing is recorded or sent away.",
            "manual…",
            p,
        ) { actions.openManual() }
        if (!state.captureMetadataAccess) {
            TrailingActionRow(
                "Track names and cover art need NOTIFICATION ACCESS — a different " +
                    "switch than “allow notifications”. grant… opens the right one. " +
                    "Sound capture works without it.",
                "grant…",
                p,
            ) { actions.openCaptureMetadataSettings() }
        }
        SectionHeading("MICROPHONE", p)
        SheetRow(
            "start selected microphone", p,
            checked = state.sourceLabel == "mic" && state.live,
            glyph = SettingsGlyph.Mic,
        ) { actions.startMic() }
        MicrophoneInputControls(state, p, actions)
        SectionHeading("REMOTE", p)
        RemoteFlow(state, p, actions, onDismiss)
        Prose(
            "Remote scopes another machine's audio over Tailscale. It plays here and " +
                "the transport drives that machine. Standard capture respects app playback-capture opt-outs. " +
                "The hidden manual workshop has a root-capture preview, not another capture option.",
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

// Grouped scope modes and geometry controls.
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
                                    onBanModes(when {
                                        i in banned -> banned - i
                                        ModeLabels.size - banned.size > 2 -> banned + i
                                        else -> banned
                                    })
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
    onStyle: (StyleOverride) -> Unit,
    onDismiss: () -> Unit,
) {
    SheetHost(p, "ROOM", reduced, onDismiss, glyph = SettingsGlyph.Room) {
        CompositionLocalProvider(LocalSettingsControlAccess provides true) {
        val gridState = rememberLazyGridState()
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier
                .heightIn(max = 340.dp),
            overscrollEffect = null,
        ) {
            itemsIndexed(Rooms) { _, room ->
                val active = "legacy:${room.id}" == state.appearanceDocument?.activeId || room.id == state.room.id
                val rs = room.style
                Column(
                    Modifier
                        .padding(4.dp)
                        .heightIn(min = 48.dp)
                        .settingsFocusBorder(p)
                        .semantics { contentDescription = "Apply legacy room ${room.label}" }
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
                            .heightIn(min = 34.dp)
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
                                    .background(room.accent)
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
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth()) {
                    SectionHeading("STYLE", p)
                    val currentStyle = state.appearanceStyle
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.weight(1f)) {
                            ChipCell(
                                "FEEL · " + currentStyle.character.name.lowercase(),
                                active = true, p = p, small = true,
                            ) {
                                onStyle(state.appearanceStyle.nextCharacter())
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            ChipCell(
                                "MOTION · " + currentStyle.motion.name.lowercase(),
                                active = true, p = p, small = true,
                            ) {
                                onStyle(state.appearanceStyle.nextMotion())
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.weight(1f)) {
                            ChipCell(
                                "CORNERS · ${currentStyle.cornerRadius.value.toInt()}dp",
                                active = true, p = p, small = true,
                            ) {
                                onStyle(state.appearanceStyle.nextCorners())
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            ChipCell(
                                "LABELS · " + if (currentStyle.designators) "part-nos" else "plain",
                                active = true, p = p, small = true,
                            ) {
                                onStyle(state.appearanceStyle.nextLabels())
                            }
                        }
                    }
                    LiveStyleSample(p, reduced, currentStyle.choices())
                    Prose(
                        "FEEL selects coupled chrome defaults. MOTION sets timing. CORNERS and LABELS change only their displayed field.",
                        p.muted, modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
        }
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
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .border(Dim.hairline, p.line)
            .padding(Dim.rowPad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsGlyphIcon(glyph, p, glyphSize)
        Spacer(Modifier.width(Dim.gap))
        Mono(label, p.ink, Type.dataLg, Modifier.weight(1f), maxLines = Int.MAX_VALUE)
    }
    Spacer(Modifier.height(Dim.gap))
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
    entryReveal: PullRevealState? = null,
    onDismiss: () -> Unit,
) {
    val scroll = presentation.scroll
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val quadrant = LocalSheetEntryQuadrant.current
    LaunchedEffect(presentation, configuration.screenWidthDp, configuration.screenHeightDp,
        density.density, density.fontScale, quadrant) {
        presentation.cancelAnchor()
    }
    DisposableEffect(presentation) {
        onDispose { presentation.retire() }
    }
    CompositionLocalProvider(LocalSettingsControlAccess provides true) {
    SheetHost(p, "SETTINGS", reduced, onDismiss, entryReveal, glyph = SettingsGlyph.Knob,
        settingsScroll = scroll,
        settingsSourceKey = listOf(state.sourceLabel, state.live, state.remote, state.captureRoot),
        onSettingsInput = presentation::cancelAnchor,
        onClosing = actions::cancelAppearancePreview,
    ) {
        val signal: @Composable () -> Unit = {
            SettingsGlyphRow("source · ${state.sourceLabel}", SettingsGlyph.Signal, p) {
                state.showSourcePicker = true
            }
            Prose("DEFAULT SOURCE on a fresh launch. None keeps SRC manual. Last-used is not a default.", p.muted)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("none" to "none", "mic" to "mic", "capture" to "capture").forEach { (id, label) ->
                    ChipCell("DEFAULT · $label", active = state.defaultSource == id, p = p, small = true) {
                        actions.setDefaultSource(id)
                    }
                }
            }
            ChipCell("AUTOMATIC PERMISSION POPUP · " + if (state.automaticPermissionPopup) "on" else "off",
                active = state.automaticPermissionPopup, p = p, small = true,
            ) { actions.setAutomaticPermissionPopup(!state.automaticPermissionPopup) }
            SignalCheckEntry(state, p)
            val remoteGeometry = state.remote && state.remoteGeometry
            DragRule(
                "SIZE",
                GainWords.toSlider(if (remoteGeometry) state.gain else state.manualGain),
                0f, 1f, p, { GainWords.multiplier(GainWords.fromSlider(it)) },
            ) { actions.setGainAbsolute(GainWords.fromSlider(it)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.weight(1f)) {
                    ChipCell(
                        (if (remoteGeometry) "DESKTOP AUTO · " else "AUTO SIZE · ") +
                            if (state.autoGain) "on" else "off",
                        active = state.autoGain, p = p, small = true,
                    ) { actions.setGainAuto(!state.autoGain) }
                }
                Box(Modifier.weight(1f)) {
                    ChipCell(
                        "VIEW LOCK · " + if (state.viewLock) "on" else "off",
                        active = state.viewLock, p = p, small = true,
                    ) { actions.setViewLock(!state.viewLock) }
                }
            }
            FlatKey("RESET SIZE", p) { actions.resetAutoFrameScale() }
            if (state.autoFrameSaveStatus.isNotEmpty()) {
                Prose(state.autoFrameSaveStatus, p.muted)
                if (state.autoFrameSaveStatus.contains("failed")) {
                    FlatKey("RETRY FRAMING SAVE", p) { actions.finishAutoFrameScale() }
                }
            }

        }
        val beam: @Composable () -> Unit = {
            DragRule("FOCUS", focusValue, 0.3f, 3.0f, p, { "%.2f px".format(it) }, onFocus)
            DragRule(
                "BEAM", state.beamEnergy, 1.0f, 30.0f, p, { "×%.0f".format(it) },
            ) { actions.setBeamEnergy(it) }
            RangeDragRule(
                "BEAM RANGE", state.beamRandomLo, state.beamRandomHi, 1.0f, 30.0f, p,
                armed = state.beamRandomArmed, onLabelTap = { actions.tapBeamRandom() },
                format = { "×%.0f".format(it) },
            ) { lo, hi -> actions.setBeamRandomRange(lo, hi) }
            DragRule(
                "GLOW", state.glow, 0.0f, 0.98f, p, { "%.0f %%".format(it * 100) },
            ) { actions.setGlow(it) }
            RangeDragRule(
                "GLOW RANGE", state.glowRandomLo, state.glowRandomHi, 0.0f, 0.98f, p,
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
            ChipCell("PIN SCREEN BRIGHTNESS · " + if (state.pinScreenBrightness) "on" else "off",
                active = state.pinScreenBrightness, p = p, small = true,
            ) { actions.setPinScreenBrightness(!state.pinScreenBrightness) }
            ChipCell("REQUEST HDR · " + if (state.hdrRequested) "on" else "off",
                active = state.hdrRequested, p = p, small = true,
            ) { actions.setHdrRequested(!state.hdrRequested) }
            Prose(state.hdrStatus, p.muted, modifier = Modifier.padding(top = 6.dp))
            Prose(if (state.brightnessPinActive) "Full window brightness requested · keeps this window awake."
                else if (state.pinScreenBrightness) "Pin selected · waiting for the focused full app."
                else "Android controls screen brightness.", p.muted)
            Prose("Sustained brightness and static images use more battery and can wear the panel. " +
                "Thermal, panel and accessibility limits still apply. This is not HDR or measured luminance. " +
                "The pin releases in PiP, HUD, background or focus loss.", p.muted)
            if (state.brightnessPinError.isNotBlank()) Prose(state.brightnessPinError, p.ink)
            ChipCell("PAUSE DISPLAY · " + if (state.pauseBlack) "BLACK" else "HOLD FRAME",
                active = !state.pauseBlack, p = p, small = true,
            ) { actions.setPauseBlack(!state.pauseBlack) }
            FlatKey(if (state.displayPaused) "RETURN DISPLAY TO LIVE" else "PAUSE DISPLAY ONLY", p) {
                actions.toggleDisplayPause()
            }
            if (state.displayPaused) {
                Prose(state.pauseLabel + ". Pan or pinch the image. Live tuning applies on resume.", p.muted)
                FlatKey("RESET INSPECTION", p) { actions.resetInspection() }
            }

            ChipCell(
                "CONTROLS ALWAYS VISIBLE · " + if (state.controlsAlwaysVisible) "on" else "off",
                active = state.controlsAlwaysVisible, p = p, small = true,
            ) { actions.setControlsAlwaysVisible(!state.controlsAlwaysVisible) }
            ChipCell(
                "AUTO PiP · " + if (state.pipAutoEnter) "on" else "off",
                active = state.pipAutoEnter, p = p, small = true,
            ) { actions.setPipAutoEnter(!state.pipAutoEnter) }
            FlatKey("ENTER PiP", p) { actions.enterPictureInPicture() }
            ChipCell("FLOATING HUD · " + if (state.floatingHudEnabled) "enabled" else "off",
                active = state.floatingHudEnabled, p = p, small = true,
            ) { actions.setFloatingHudEnabled(!state.floatingHudEnabled) }
            ChipCell("HUD BACKGROUND · " + if (state.floatingHudTransparent) "TRANSPARENT" else "SOLID",
                active = state.floatingHudTransparent, p = p, small = true,
            ) { actions.setFloatingHudTransparent(!state.floatingHudTransparent) }
            FlatKey(if (state.floatingHudActive) "HIDE FLOATING HUD" else "SHOW FLOATING HUD", p) {
                if (state.floatingHudActive) actions.hideFloatingHud() else actions.showFloatingHud()
            }
            Prose(state.floatingHudStatus + ". Show requests overlay access only when needed. Imported preferences never start a HUD.", p.muted)
            ChipCell(
                "DOUBLE TAP PLAYBACK · " + if (state.doubleTapPlayback) "on" else "off",
                active = state.doubleTapPlayback, p = p, small = true,
            ) { actions.setDoubleTapPlayback(!state.doubleTapPlayback) }
            ChipCell(
                "BACKGROUND LINGER · " + if (state.lingerBackground) "on" else "off",
                active = state.lingerBackground, p = p, small = true,
            ) { actions.setLingerBackground(!state.lingerBackground) }
            Prose(
                "After removal from recents, keep only existing service-owned local or relay playback and capture. " +
                    "Established service-owned microphone input can continue too. Off stops sources when the task is removed.",
                p.muted, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
            )
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
                        enabled = !state.systemRotationLocked,
                    ) {
                        actions.setScopeRotationLocked(!actions.isScopeRotationLocked())
                    }
                }
                val uiPlacement: @Composable () -> Unit = {
                    ChipCell(
                        "UI PLACEMENT · " +
                            (if (actions.isUiPlacementLocked()) "locked" else "follow"),
                        active = actions.isUiPlacementLocked(), p = p, small = true,
                        enabled = !state.systemRotationLocked,
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
                if (state.systemRotationLocked) {
                    "Android rotation lock is on. Enable system auto-rotate to change these controls. " +
                        "Your app choices are saved; the current orientation stays put."
                } else {
                    "Scope lock pins the current orientation. When both app locks are off, " +
                        "rotation follows the gravity detent. UI placement lock holds the Activity " +
                        "while labels face the viewer. Layout fits the actual window if Android " +
                        "ignores an orientation request."
                },
                p.muted, modifier = Modifier.padding(top = 6.dp),
            )
        }
        val performance: @Composable () -> Unit = {
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.weight(1f)) {
                    ChipCell(
                        "STATS HUD · " + when (state.hudMode) {
                            0 -> "on"; 1 -> "auto"; else -> "off"
                        },
                        active = state.hudMode == 0,
                        p = p,
                        small = true,
                    ) { actions.setHudMode((state.hudMode + 1) % 3) }
                }
                Box(Modifier.weight(1f)) {
                    // The status band can stay visible, follow the console timer, or remain hidden.
                    ChipCell(
                        "BAND · " + when (state.bandMode) {
                            1 -> "auto"; 2 -> "off"; else -> "on"
                        },
                        active = state.bandMode == 0, p = p, small = true,
                    ) { state.bandMode = (state.bandMode + 1) % 3 }
                }
                Spacer(Modifier.weight(1f))
            }
            if (state.hudControlStatus.isNotBlank()) {
                Prose(state.hudControlStatus, p.muted, modifier = Modifier.padding(top = 6.dp))
            }
        }
        val remote: @Composable () -> Unit = {
            Mono("RELAY ONLY · LATENCY", p.muted, Type.dataXs, Modifier.padding(top = Dim.gap, bottom = 4.dp))
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
                "Tight is for a low-latency Tailscale path. The bridge widens automatically on underruns; " +
                    "safe is today's ear-verified behavior.",
                p.muted, modifier = Modifier.padding(top = 6.dp),
            )
            Prose(
                "Relay traffic follows Android and Tailscale routing. Phosphor never forces the " +
                    "whole app onto Wi-Fi or mobile data.",
                p.muted, modifier = Modifier.padding(top = Dim.gapLg),
            )
        }
        val appearance: @Composable () -> Unit = {
            AppearanceEditor(state, actions)
            SettingsGlyphRow("room · ${state.room.label}", SettingsGlyph.Room, p) {
                actions.openRoom()
            }
        }
        val beamNavigation: @Composable () -> Unit = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.weight(1f)) {
                    ChipCell("GRID · " + (if (state.grid) "on" else "off"), active = state.grid, p = p, small = true) {
                        actions.setGrid(!state.grid)
                    }
                }
                Box(Modifier.weight(2f)) {
                    ChipCell("GRID DATA · " + if (state.gridData) "on" else "off",
                        active = state.gridData, p = p, small = true,
                    ) { actions.setGridData(!state.gridData) }
                }
            }
            SettingsGlyphRow("light · beam color", SettingsGlyph.BeamColor, p) {
                actions.openLight()
            }
            SettingsGlyphRow("instrument presets · recall / save", SettingsGlyph.Display, p) {
                actions.openInstrument()
            }
        }
        val migration: @Composable () -> Unit = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.weight(1f)) {
                    ChipCell("EXPORT SETTINGS", active = false, p = p, small = true) {
                        actions.exportSettings()
                    }
                }
                Box(Modifier.weight(1f)) {
                    ChipCell("IMPORT SETTINGS", active = false, p = p, small = true) {
                        actions.importSettings()
                    }
                }
            }
            Prose(
                "Portable .phossettings archives carry only allowlisted instrument settings " +
                    "and room/light state. They exclude media paths, hosts, consent tokens, " +
                    "purchase data, and agent authorization.",
                p.muted, modifier = Modifier.padding(top = 6.dp),
            )
            if (state.settingsTransferStatus.isNotBlank()) {
                Mono(
                    state.settingsTransferStatus, p.accent, Type.dataXs,
                    Modifier.padding(top = 6.dp), maxLines = Int.MAX_VALUE,
                )
            }
        }
        val about: @Composable () -> Unit = {
            Prose(
                "Phosphor draws sound as light — a CRT oscilloscope in your pocket, " +
                    "sample-locked to what you hear. GPL-3.0. The beam remembers.",
                p.muted, modifier = Modifier.padding(bottom = Dim.gap),
            )
            SettingsGlyphRow("manual · how it all works", SettingsGlyph.About, p) {
                actions.openManual()
            }
            // Seven taps on the version open the developer view (engineering readouts).
            var versionTaps by remember { mutableIntStateOf(0) }
            Mono(
                "version ${dev.phosphor.mobil3.BuildConfig.VERSION_NAME}" +
                    if (state.developerView) " · developer view on" else "",
                p.muted, Type.dataXs,
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .clickable {
                        versionTaps += 1
                        if (versionTaps >= 7) {
                            versionTaps = 0
                            actions.setDeveloperView(!state.developerView)
                        }
                    }
                    .padding(vertical = 14.dp),
            )
            // The bench keeps a service stamp: the REAL date the knobs were last
            // saved (Annotated rooms only — a calibration sticker, typeset).
            if (LocalRoomStyle.current.designators && state.calDate.isNotBlank()) {
                Mono(
                    "CAL · ${state.calDate}   S/N 003" +
                        if (state.bestiaryFound) "   ☂ holding" else "",
                    p.muted, Type.dataXs,
                    Modifier.padding(bottom = Dim.gap), letterSpacing = 1.2.sp,
                )
            }
        }
        // One reading order and one screen-owned scroll in either orientation.
        val anchorRequest = presentation.request
        Column(
            Modifier.fillMaxWidth()
                .onGloballyPositioned { presentation.viewport = it }
                .verticalScroll(scroll, overscrollEffect = null)
                .settingsAnchorLayout(presentation, anchorRequest, scope)
        ) {
            SettingsExpandableSection(SettingsSectionId.SIGNAL, "SIGNAL & STARTUP",
                if (state.remote && state.remoteGeometry) {
                    "${state.sourceLabel} · desktop gain ×${"%.2f".format(state.gain)} · auto ${if (state.autoGain) "on" else "off"}"
                } else {
                    "${state.sourceLabel} · manual ×${"%.2f".format(state.manualGain)} · auto frame ×${"%.3f".format(state.autoFrameScale)}"
                },
                SettingsGlyph.Signal, p, presentation) {
                signal()
                remote()
            }
            SettingsExpandableSection(SettingsSectionId.BEAM, "BEAM & LIGHT",
                "Focus ${"%.2f".format(focusValue)} px · beam ×${"%.0f".format(state.beamEnergy)} · glow ${"%.0f".format(state.glow * 100)}%",
                SettingsGlyph.BeamColor, p, presentation) {
                beam()
                beamNavigation()
            }
            SettingsExpandableSection(SettingsSectionId.DISPLAY, "DISPLAY & HUD",
                "${if (state.pauseBlack) "Black" else "Hold"} on pause · floating HUD ${if (state.floatingHudActive) "showing" else "hidden"} · fullscreen ${if (state.fullscreen) "on" else "off"}",
                SettingsGlyph.Display, p, presentation) {
                display()
            }
            SettingsExpandableSection(SettingsSectionId.MOTION, "MOTION & PERFORMANCE",
                "Frame rate ${FpsOptions.firstOrNull { it.value == state.fpsValue }?.label ?: state.fpsValue} · beam reconstruction ${BeamRates.firstOrNull { it.oversample == state.oversample }?.label ?: state.oversample}",
                SettingsGlyph.Performance, p, presentation) {
                performance()
            }
            SettingsExpandableSection(SettingsSectionId.APPEARANCE, "APPEARANCE",
                state.appearanceSummary, SettingsGlyph.Room, p, presentation) {
                appearance()
            }
            SettingsExpandableSection(SettingsSectionId.ABOUT, "ABOUT & MANUAL",
                state.settingsTransferStatus.ifBlank { "GPL-3.0 · manual · settings import / export" },
                SettingsGlyph.About, p, presentation) {
                migration()
                about()
            }
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
    var browseRequest by remember { mutableStateOf<RemoteBrowseRequest?>(null) }
    var selectedPeer by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var sourcesJson by remember { mutableStateOf("") }
    var listingJson by remember { mutableStateOf("") }
    var listingGeneration by remember { mutableIntStateOf(0) }
    fun currentPeer(): Pair<String, Int>? {
        if (!state.remote) return null
        val status = runCatching {
            org.json.JSONObject(dev.phosphor.mobil3.PhosphorNative.remoteStatus())
        }.getOrNull() ?: return null
        if (status.optString("state") !in listOf("streaming", "stalled")) return null
        val peer = status.optString("host") to status.optInt("port")
        if (peer.first.isBlank() || peer.second !in 1..65535) return null
        return peer.takeIf { selectedPeer == null || selectedPeer == it }
    }
    fun clearBrowse() {
        browseRequest?.retire()
        browseRequest = null
        listingJson = ""
        browsing = false
    }
    fun requestBrowse(root: String, path: String) {
        val peer = currentPeer() ?: run { clearBrowse(); return }
        browseRequest?.retire()
        listingJson = ""
        browseRequest = RemoteBrowseRequest(
            root, path, peer, dev.phosphor.mobil3.PhosphorNative.remoteListingGeneration(),
        )
        dev.phosphor.mobil3.PhosphorNative.remoteBrowse(root, path)
    }
    DisposableEffect(Unit) {
        onDispose { browseRequest?.retire() }
    }
    // Host-editing state. editTarget null while editing means "adding a new relay";
    // refusal holds the store's fix-bearing text until the user changes something.
    var editing by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<dev.phosphor.mobil3.RemoteHost?>(null) }
    var refusal by remember { mutableStateOf<String?>(null) }
    // The store lives outside Compose, so reading it is not observable on its own.
    // Bumping this after every accepted mutation is what re-reads the list; without it
    // a saved relay would not appear until some unrelated state happened to recompose.
    var hostRevision by remember { mutableIntStateOf(0) }

    // Gentle wire poll while the remote panels are open (generation-gated on the JNI side).
    LaunchedEffect(state.remote, showSources, browsing, selectedPeer) {
        if (!state.remote) clearBrowse()
        while (state.remote && (showSources || browsing)) {
            val peer = currentPeer()
            if (peer == null || (browseRequest != null && browseRequest?.peer != peer)) {
                clearBrowse()
                sourcesJson = ""
            } else {
                sourcesJson = dev.phosphor.mobil3.PhosphorNative.remoteSources()
                readRemoteListing(
                    { dev.phosphor.mobil3.PhosphorNative.remoteListingGeneration() },
                    { dev.phosphor.mobil3.PhosphorNative.remoteListing() },
                )?.let { (generation, listing) ->
                    listingJson = listing
                    listingGeneration = generation
                }
            }
            kotlinx.coroutines.delay(400)
        }
    }

    // The saved relays. Each row connects on tap; EDIT opens the same editor the ADD
    // key uses, so there is one way to reason about a host rather than two.
    val hosts = remember(hostRevision) { actions.remoteHosts() }
    if (hosts.isEmpty() && !editing) {
        RemoteEmptyState(p)
    }
    // The last failure, with the engine's own fix. Shown above the host rows because it
    // explains why the row you just tapped did not work.
    if (state.remoteFailure.isNotBlank()) {
        Box(
            Modifier
                .fillMaxWidth()
                .border(Dim.hairline, p.accent)
                .padding(Dim.rowPad),
        ) {
            Mono(state.remoteFailure, p.ink, Type.data, maxLines = 6)
        }
        Spacer(Modifier.height(Dim.gap))
    }
    hosts.forEach { (label, hostPort) ->
        val connected = state.remote && state.sourceLabel.contains(label)
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dim.gap),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                SheetRow(label, p, checked = connected, glyph = SettingsGlyph.Remote) {
                    if (!connected) {
                        clearBrowse()
                        showSources = false
                        sourcesJson = ""
                        selectedPeer = hostPort
                        actions.startRemoteHost(label, hostPort.first, hostPort.second)
                    }
                }
            }
            FlatKey("EDIT", p) {
                refusal = null
                editTarget = dev.phosphor.mobil3.RemoteHost(label, hostPort.first, hostPort.second)
                editing = true
            }
        }
    }

    if (editing) {
        RemoteHostEditor(
            p = p,
            existing = editTarget,
            refusal = refusal,
            onSubmit = { label, host, port ->
                val outcome = actions.saveRemoteHost(
                    editTarget?.host.orEmpty(),
                    editTarget?.port ?: 0,
                    label,
                    host,
                    port,
                )
                refusal = outcome
                if (outcome == null) {
                    hostRevision++
                    editing = false
                    editTarget = null
                }
            },
            onRemove = editTarget?.let { target ->
                {
                    val outcome = actions.removeRemoteHost(target.host, target.port)
                    refusal = outcome
                    if (outcome == null) {
                        hostRevision++
                        editing = false
                        editTarget = null
                    }
                }
            },
            onCancel = {
                editing = false
                editTarget = null
                refusal = null
            },
        )
    } else {
        FlatKey("+ ADD RELAY", p, modifier = Modifier.fillMaxWidth()) {
            refusal = null
            editTarget = null
            editing = true
        }
        Spacer(Modifier.height(Dim.gap))
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
            if (browsing) {
                clearBrowse()
            } else if (currentPeer() != null) {
                browsing = true
                // Roots come from the relay's welcome; default to the first.
                val st = runCatching {
                    org.json.JSONObject(dev.phosphor.mobil3.PhosphorNative.remoteStatus())
                }.getOrNull()
                val libs = st?.optJSONObject("welcome")?.optJSONArray("libraries")
                if (libs != null && libs.length() > 0) {
                    requestBrowse(libs.getJSONObject(0).optString("id", "music0"), "")
                }
            }
        }
        if (browsing && listingJson.isNotBlank()) {
            // The relay may serve several roots (Music, another drive, a cloud remote).
            // Only the first was ever reachable, so a second drive configured on the
            // desktop was invisible from the phone. Show them when there is a choice.
            val roots = remember(browseRequest?.peer, state.remote, browsing) {
                runCatching {
                    org.json.JSONObject(dev.phosphor.mobil3.PhosphorNative.remoteStatus())
                        .optJSONObject("welcome")?.optJSONArray("libraries")
                }.getOrNull()?.let { arr ->
                    (0 until arr.length()).mapNotNull { i ->
                        arr.optJSONObject(i)?.let { obj ->
                            val id = obj.optString("id")
                            if (id.isBlank()) null
                            else id to obj.optString("label").ifBlank { id }
                        }
                    }
                } ?: emptyList()
            }
            if (roots.size > 1) {
                val rootRequest = browseRequest
                Row(
                    Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    roots.forEach { (id, label) ->
                        FlatKey(
                            label, p, modifier = Modifier.weight(1f),
                            active = id == browseRequest?.root,
                        ) {
                            rootRequest?.selectRoot(id, browseRequest, currentPeer(), browsing, ::requestBrowse)
                        }
                    }
                }
            }
            runCatching { org.json.JSONObject(listingJson) }.getOrNull()?.let { l ->
                val path = l.optString("path")
                val request = browseRequest ?: return@let
                val folder = RemoteFolderAction(
                    root = l.optString("root"),
                    path = path,
                    generation = listingGeneration,
                    request = request,
                    currentRequest = { browseRequest },
                    currentPeer = ::currentPeer,
                    browse = ::requestBrowse,
                    play = { root, target ->
                        dev.phosphor.mobil3.PhosphorNative.remotePlayFile(root, target)
                    },
                    dismiss = { clearBrowse(); onDismiss() },
                )
                if (!folder.accepted) return@let
                Mono(
                    "library › " + (path.ifBlank { "(root)" }), p.muted, Type.dataXs,
                    Modifier.padding(vertical = 4.dp),
                )
                FlatKey("PLAY FOLDER -> QUEUE", p) { folder.playFolder() }
                if (path.isNotBlank()) {
                    SheetRow("‹ up", p) { folder.up() }
                }
                val dirs = l.optJSONArray("dirs")
                if (dirs != null) for (i in 0 until dirs.length()) {
                    val d = dirs.getString(i)
                    SheetRow("$d /", p) { folder.directory(d) }
                }
                val files = l.optJSONArray("files")
                if (files != null) for (i in 0 until files.length()) {
                    val f = files.getJSONObject(i)
                    val name = f.optString("name")
                    SheetRow(name, p) { folder.file(name) }
                }
            }
        }
        SheetRow("disconnect", p) {
            clearBrowse()
            showSources = false
            sourcesJson = ""
            actions.disconnectRemote()
            onDismiss()
        }
    }
}
