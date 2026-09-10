package dev.phosphor.mobil3.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.sp
import dev.phosphor.mobil3.AcceptanceTrace

/** No timers, transient palette, transport dispatch or pointer ownership changes. */
@Composable
internal fun TactileConsoleKeybed(
    state: ScopeUiState, p: Palette, reduced: Boolean,
    onMode: () -> Unit, onSrc: () -> Unit, onPlay: () -> Unit,
    onPrev: () -> Unit, onNext: () -> Unit, onSeek: (Long) -> Unit,
    onMore: () -> Unit, moreActive: Boolean, overflowPullHost: PullGestureHost,
) {
    val inheritedUnits = LocalDensity.current
    val displayDensity = LocalView.current.resources.displayMetrics.density
    val displayUnits = remember(inheritedUnits, displayDensity) {
        object : androidx.compose.ui.unit.Density {
            override val density = displayDensity
            override val fontScale = inheritedUnits.fontScale
            override fun androidx.compose.ui.unit.TextUnit.toDp() = with(inheritedUnits) { this@toDp.toDp() }
            override fun androidx.compose.ui.unit.Dp.toSp() = with(inheritedUnits) { this@toSp.toSp() }
        }
    }
    CompositionLocalProvider(LocalDensity provides displayUnits) {
        DisplayDensityKeybed(state, p, reduced, onMode, onSrc, onPlay, onPrev, onNext, onSeek, onMore, moreActive, overflowPullHost)
    }
}

@Composable
private fun DisplayDensityKeybed(
    state: ScopeUiState, p: Palette, reduced: Boolean,
    onMode: () -> Unit, onSrc: () -> Unit, onPlay: () -> Unit,
    onPrev: () -> Unit, onNext: () -> Unit, onSeek: (Long) -> Unit,
    onMore: () -> Unit, moreActive: Boolean, overflowPullHost: PullGestureHost,
) {
    val value = state.appearanceValue ?: return
    val tokens = remember(value) { ConsoleTactileTokens.from(value) }
    val view = LocalView.current
    val capture = state.sourceLabel == "capture"
    val showPlay = !capture || state.captureCanPlay || state.live
    val displayOnly = PauseDisplayPolicy.displayOnly(state.live, state.captureCanPlay)
    val drawnPlaying = state.playing
    val drawnPaused = state.displayPaused
    val drawnLabel = PauseDisplayPolicy.controlLabel(displayOnly, drawnPlaying, drawnPaused)
    val style = LocalRoomStyle.current
    val fontScale = LocalDensity.current.fontScale
    val hasTransport = state.trackTitle != null || state.remote
    val showPrev = hasTransport && (!capture || state.captureCanPrevious)
    val showNext = hasTransport && (!capture || state.captureCanNext)
    val edgeFlash = remember { Animatable(0f) }
    val pulseGen = remember { mutableIntStateOf(0) }
    val flashAllowed = !reduced && state.presentationVisible && !state.pip
    val allowedNow = rememberUpdatedState(flashAllowed)
    val pulse = remember {
        {
            if (allowedNow.value) pulseGen.intValue += 1
        }
    }
    LaunchedEffect(pulseGen.intValue) {
        if (pulseGen.intValue == 0 || !allowedNow.value) {
            edgeFlash.snapTo(0f)
            return@LaunchedEffect
        }
        edgeFlash.snapTo(1f)
        edgeFlash.animateTo(0f, tween(Motion.press, easing = Motion.standard))
    }
    LaunchedEffect(flashAllowed) { if (!flashAllowed) edgeFlash.snapTo(0f) }
    var eventsPrimed by remember { mutableStateOf(false) }
    LaunchedEffect(state.modeIndex, state.sourceLabel, state.room.id) {
        if (!eventsPrimed) { eventsPrimed = true; return@LaunchedEffect }
        pulse()
    }
    CompositionLocalProvider(LocalEdgeFlash provides EdgeFlash(edgeFlash.value, flashAllowed, pulse)) {
    BoxWithConstraints(Modifier.fillMaxWidth().background(rgb(tokens.well))
        .border(1.dp, rgb(tokens.edgeQuiet)).padding(5.dp)) {
        val layout = ConsoleKeybedPolicy.layout(maxWidth.value, fontScale, displayOnly, hasTransport)
        val primary: @Composable (Modifier) -> Unit = { modifier ->
            TactileConsoleKey(
                label = if (displayOnly) drawnLabel else "",
                description = if (displayOnly) "$drawnLabel · display only" else if (drawnPlaying) "Pause playback" else "Play playback",
                tokens = tokens, primary = true,
                selected = if (displayOnly) drawnPaused else drawnPlaying,
                enabled = showPlay,
                disabledReason = "Play is unavailable until this source can play",
                designator = if (style.designators) "S1" else "",
                glyph = if (displayOnly) null else if (drawnPlaying) ConsoleVector.PAUSE else ConsoleVector.PLAY,
                modifier = modifier.drawWithContent {
                    drawContent()
                    if (capture && !displayOnly) AcceptanceTrace.record("capture_glyph_draw") { "playing=$drawnPlaying" }
                    if (displayOnly) AcceptanceTrace.record("display_pause_draw") { "paused=$drawnPaused label=$drawnLabel" }
                },
            ) { Haptics.light(view); onPlay() }
        }
        val mode: @Composable (Modifier) -> Unit = { modifier ->
            TactileConsoleKey("MODE", "Choose scope mode", tokens, modifier = modifier,
                designator = if (style.designators) "V2" else "") {
                if (state.randomModeArmed) state.requestRandomMode()
                onMode()
            }
        }
        val source: @Composable (Modifier) -> Unit = { modifier ->
            TactileConsoleKey("SRC", "Choose source", tokens, modifier = modifier,
                designator = if (style.designators) "J1" else "", onClick = onSrc)
        }
        val overflow: @Composable (Modifier) -> Unit = { modifier ->
            TactileOverflowKey(tokens, moreActive, overflowPullHost, modifier, onMore)
        }
        val prev: @Composable (Modifier) -> Unit = { modifier ->
            TactileConsoleKey("", "Previous track", tokens, modifier = modifier,
                enabled = showPrev, disabledReason = "Previous is unavailable for this source",
                glyph = ConsoleVector.PREV) { Haptics.light(view); onPrev() }
        }
        val next: @Composable (Modifier) -> Unit = { modifier ->
            TactileConsoleKey("", "Next track", tokens, modifier = modifier,
                enabled = showNext, disabledReason = "Next is unavailable for this source",
                glyph = ConsoleVector.NEXT) { Haptics.light(view); onNext() }
        }
        val secondary: @Composable () -> Unit = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                mode(Modifier.width(layout.modeWidth.dp))
                Spacer(Modifier.width(8.dp))
                source(Modifier.width(layout.sourceWidth.dp))
                Spacer(Modifier.width(8.dp))
                Spacer(Modifier.weight(1f))
                overflow(Modifier.width(layout.overflowWidth.dp))
            }
        }
        val keys: @Composable () -> Unit = {
            when (layout.rows) {
                4 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (hasTransport) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        prev(Modifier.width(48.dp)); Spacer(Modifier.width(8.dp))
                        primary(Modifier.weight(1f)); Spacer(Modifier.width(8.dp))
                        next(Modifier.width(48.dp))
                    } else primary(Modifier.width(layout.primaryWidth.dp))
                    mode(Modifier.width(layout.modeWidth.dp)); source(Modifier.width(layout.sourceWidth.dp))
                    overflow(Modifier.width(layout.overflowWidth.dp))
                }
                2 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (hasTransport) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        prev(Modifier.width(48.dp)); Spacer(Modifier.width(8.dp))
                        primary(Modifier.weight(1f)); Spacer(Modifier.width(8.dp))
                        next(Modifier.width(48.dp))
                    } else primary(Modifier.width(layout.primaryWidth.dp))
                    secondary()
                }
                else -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (hasTransport) {
                        prev(Modifier.width(48.dp)); Spacer(Modifier.width(8.dp))
                    }
                    primary(Modifier.width(layout.primaryWidth.dp))
                    if (hasTransport) {
                        Spacer(Modifier.width(8.dp)); next(Modifier.width(48.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    mode(Modifier.width(layout.modeWidth.dp))
                    Spacer(Modifier.width(8.dp))
                    source(Modifier.width(layout.sourceWidth.dp))
                    Spacer(Modifier.width(8.dp))
                    Spacer(Modifier.weight(1f))
                    overflow(Modifier.width(layout.overflowWidth.dp))
                }
            }
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.trackTitle?.let { title ->
                Box(Modifier.fillMaxWidth().height(20.dp), contentAlignment = Alignment.CenterStart) {
                    val line = buildString {
                        append(title)
                        state.trackArtist?.let {
                            if (it.isNotBlank() && it != "null") append("  —  $it")
                        }
                    }
                    Mono(line, p.ink, Type.dataLg,
                        if (!reduced && state.presentationVisible && !state.pip) Modifier.basicMarquee(
                            iterations = Int.MAX_VALUE, initialDelayMillis = 2200, velocity = 24.dp,
                        ) else Modifier)
                }
            }
            if (state.seekable && state.durationMs > 0) SeekRule(p, state.positionMs, state.durationMs, onSeek)
            keys()
        }
    }
    }
}

internal enum class ConsoleVector { PLAY, PAUSE, OVERFLOW, PREV, NEXT }

@Composable
internal fun TactileConsoleKey(
    label: String, description: String, tokens: ConsoleTactileTokens,
    modifier: Modifier = Modifier, primary: Boolean = false, selected: Boolean = false,
    enabled: Boolean = true, disabledReason: String = "Unavailable for this source",
    designator: String = "", glyph: ConsoleVector? = null, onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    TactileKeyFace(tokens, primary, pressed && enabled, selected, focused, enabled,
        modifier.semantics(mergeDescendants = true) {
            contentDescription = description
            this.selected = selected
            if (!enabled) stateDescription = disabledReason
        }.clickable(enabled = enabled, interactionSource = interaction, indication = null,
            role = Role.Button, onClick = onClick), label, designator, glyph)
}

@Composable
private fun TactileOverflowKey(tokens: ConsoleTactileTokens, active: Boolean, pullHost: PullGestureHost,
    modifier: Modifier, onTap: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    // Keep the existing tap/pull pointer owner. Keyboard and accessibility do not add pointer input.
    val target = modifier.semantics(mergeDescendants = true) {
        role = Role.Button
        contentDescription = "More controls"
        selected = active
        onClick { onTap(); true }
    }.onKeyEvent {
        if (it.key == Key.Enter || it.key == Key.NumPadEnter || it.key == Key.Spacebar) {
            if (it.type == KeyEventType.KeyUp) onTap()
            true
        } else false
    }.focusable(interactionSource = interaction).overflowHandleGesture(pullHost, onTap) { pressed = it }
    TactileKeyFace(tokens, false, pressed, active, focused, true, target, "",
        if (LocalRoomStyle.current.designators) "S9" else "", ConsoleVector.OVERFLOW)
}

@Composable
private fun TactileKeyFace(tokens: ConsoleTactileTokens, primary: Boolean, pressed: Boolean,
    selected: Boolean, focused: Boolean, enabled: Boolean, modifier: Modifier,
    label: String, designator: String, glyph: ConsoleVector?) {
    val sunk = enabled && (pressed || selected)
    val colors = if (sunk) tokens.sunk else tokens.raised
    val radius = LocalRoomStyle.current.cornerRadius
    val flash = LocalEdgeFlash.current
    val onPulse = rememberUpdatedState(flash.pulse)
    LaunchedEffect(pressed) { if (pressed) onPulse.value() }
    Box(modifier.heightIn(min = if (primary) 56.dp else 48.dp).widthIn(min = 48.dp)
        .drawWithContent {
            val gap = 0f
            val origin = Offset(gap, gap)
            val extent = Size((size.width - 2 * gap).coerceAtLeast(0f), (size.height - 2 * gap).coerceAtLeast(0f))
            val corner = CornerRadius(radius.toPx().coerceAtMost(minOf(extent.width, extent.height) / 2f))
            drawRoundRect(rgb(tokens.edgeQuiet), origin, extent, corner)
            val inset = 1.dp.toPx()
            val faceOrigin = origin + Offset(inset, inset)
            val faceSize = Size((extent.width - 2 * inset).coerceAtLeast(0f), (extent.height - 2 * inset).coerceAtLeast(0f))
            drawRoundRect(rgb(colors.face), faceOrigin, faceSize, corner)
            if (!enabled) drawRoundRect(rgb(colors.disabledInk), faceOrigin, faceSize, corner,
                style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))))
            if (enabled) {
                val depth = (if (primary) 2.dp else 1.dp).toPx()
                val bevelInset = 1.dp.toPx() + depth / 2f
                // Short of curved corners. Authored shape remains unchanged in every state.
                val left = gap + corner.x + 2.dp.toPx()
                val right = size.width - left
                val high = if (!sunk && flash.allowed && flash.amount > 0f)
                    flashedEdge(colors.high, flash.amount)
                else rgb(if (sunk) colors.low else colors.high)
                val low = rgb(if (sunk) colors.high else colors.low)
                if (right > left) {
                    drawLine(high, Offset(left, gap + bevelInset), Offset(right, gap + bevelInset), depth)
                    drawLine(low, Offset(left, size.height - gap - bevelInset), Offset(right, size.height - gap - bevelInset), depth)
                }
                val top = gap + corner.y + 2.dp.toPx()
                val bottom = size.height - top
                if (bottom > top) {
                    drawLine(high, Offset(gap + bevelInset, top), Offset(gap + bevelInset, bottom), depth)
                    drawLine(low, Offset(size.width - gap - bevelInset, top), Offset(size.width - gap - bevelInset, bottom), depth)
                }
            }
            if (selected && enabled) {
                drawLine(rgb(colors.accent), Offset(gap + 6.dp.toPx(), size.height - gap - 3.dp.toPx()),
                    Offset(size.width - gap - 6.dp.toPx(), size.height - gap - 3.dp.toPx()), 2.dp.toPx())
                drawLine(rgb(colors.accent), Offset(gap + 3.dp.toPx(), gap + 8.dp.toPx()),
                    Offset(gap + 3.dp.toPx(), gap + 16.dp.toPx()), 1.dp.toPx())
            }
            drawContent()
            if (glyph != null) {
                val box = 26.dp.toPx()
                val origin = Offset((size.width - box) / 2f,
                    (size.height - box) / 2f + if (sunk) 1.dp.toPx() else 0f)
                drawConsoleVector(glyph, rgb(if (enabled) colors.ink else colors.disabledInk), origin, box)
            }
            if (focused) drawRoundRect(rgb(tokens.focusRing), Offset(-2.dp.toPx(), -2.dp.toPx()),
                Size(size.width + 4.dp.toPx(), size.height + 4.dp.toPx()), corner, style = Stroke(2.dp.toPx()))
        }, contentAlignment = Alignment.Center, propagateMinConstraints = true) {
        val ink = rgb(if (enabled) colors.ink else colors.disabledInk)
        androidx.compose.ui.layout.Layout(content = {
            UprightCell {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (label.isNotEmpty()) KeyText(label, ink)
                }
            }
            if (designator.isNotEmpty()) KeyText(designator, ink, small = true)
        }) { measurables, constraints ->
            val padding = if (glyph != null && label.isEmpty()) 4.dp.roundToPx() else 10.dp.roundToPx()
            val inset = 3.dp.roundToPx()
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val main = measurables[0].measure(loose.copy(maxWidth = (constraints.maxWidth - 2 * padding).coerceAtLeast(0)))
            val tag = measurables.getOrNull(1)?.measure(loose.copy(maxWidth = (constraints.maxWidth - 2 * inset).coerceAtLeast(0)))
            val width = constraints.maxWidth
            val height = constraints.constrainHeight(StageReadability.legendHeight(constraints.minHeight, width,
                main.width, main.height, tag?.width ?: 0, tag?.height ?: 0, inset, 2.dp.roundToPx(), padding))
            layout(width, height) {
                main.placeRelative((width - main.width) / 2, (height - main.height) / 2 + if (sunk) 1.dp.roundToPx() else 0)
                tag?.placeRelative(inset, inset)
            }
        }
    }
}

@Composable
private fun KeyText(label: String, ink: Color, small: Boolean = false) {
    BasicText(label, style = TextStyle(color = ink, fontFamily = FontFamily.Monospace,
        fontSize = if (small) 10.sp else 14.sp), softWrap = true)
}

/** Closed transport silhouettes and square overflow contacts, independent of installed fonts. */
private fun DrawScope.drawConsoleVector(glyph: ConsoleVector, ink: Color, origin: Offset, box: Float) {
    fun px(x: Float, y: Float) = Offset(origin.x + box * x, origin.y + box * y)
    when (glyph) {
        ConsoleVector.PLAY -> drawPath(Path().apply {
            moveTo(origin.x + box * .28f, origin.y + box * .18f)
            lineTo(origin.x + box * .78f, origin.y + box * .5f)
            lineTo(origin.x + box * .28f, origin.y + box * .82f); close()
        }, ink)
        ConsoleVector.PAUSE -> listOf(.25f, .58f).forEach { x ->
            drawRect(ink, px(x, .2f), Size(box * .17f, box * .6f))
        }
        ConsoleVector.OVERFLOW -> {
            drawLine(ink, px(.24f, .34f), px(.76f, .34f), 1.dp.toPx())
            drawLine(ink, px(.5f, .34f), px(.5f, .17f), 1.dp.toPx())
            listOf(.31f, .5f, .69f).forEach { x ->
                drawRect(ink, px(x - .045f, .615f), Size(box * .09f, box * .09f))
            }
        }
        ConsoleVector.PREV -> {
            drawPath(Path().apply {
                moveTo(origin.x + box * .47f, origin.y + box * .18f)
                lineTo(origin.x + box * .22f, origin.y + box * .5f)
                lineTo(origin.x + box * .47f, origin.y + box * .82f); close()
            }, ink)
            drawPath(Path().apply {
                moveTo(origin.x + box * .78f, origin.y + box * .18f)
                lineTo(origin.x + box * .53f, origin.y + box * .5f)
                lineTo(origin.x + box * .78f, origin.y + box * .82f); close()
            }, ink)
        }
        ConsoleVector.NEXT -> {
            drawPath(Path().apply {
                moveTo(origin.x + box * .22f, origin.y + box * .18f)
                lineTo(origin.x + box * .47f, origin.y + box * .5f)
                lineTo(origin.x + box * .22f, origin.y + box * .82f); close()
            }, ink)
            drawPath(Path().apply {
                moveTo(origin.x + box * .53f, origin.y + box * .18f)
                lineTo(origin.x + box * .78f, origin.y + box * .5f)
                lineTo(origin.x + box * .53f, origin.y + box * .82f); close()
            }, ink)
        }
    }
}

private fun rgb(value: Int) = Color(value or 0xff000000.toInt())

private class EdgeFlash(val amount: Float = 0f, val allowed: Boolean = false, val pulse: () -> Unit = {})
private val LocalEdgeFlash = compositionLocalOf { EdgeFlash() }

/** Mix the raised high bevel toward white, then keep relative luminance under the chrome cap. */
private fun flashedEdge(high: Int, flash: Float): Color {
    val amount = flash.coerceIn(0f, 1f)
    if (amount <= 0f) return rgb(high)
    val cap = Dim.chromeLuminanceCap
    if (relativeLuminance(high) >= cap) return rgb(high)
    var lo = 0f
    var hi = amount
    var best = high
    repeat(8) {
        val mid = (lo + hi) * 0.5f
        val mixed = mixTowardWhite(high, mid)
        if (relativeLuminance(mixed) <= cap) {
            best = mixed
            lo = mid
        } else hi = mid
    }
    return rgb(best)
}

private fun mixTowardWhite(packed: Int, t: Float): Int {
    fun ch(shift: Int): Int {
        val c = (packed ushr shift) and 255
        return (c + (255 - c) * t).toInt().coerceIn(0, 255)
    }
    return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}

private fun relativeLuminance(packed: Int): Float = Color(packed or 0xff000000.toInt()).luminance()
