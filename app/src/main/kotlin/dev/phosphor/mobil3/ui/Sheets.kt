package dev.phosphor.mobil3.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.TextUnit
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

enum class Sheet { NONE, SOURCE, MODE, LIGHT, SETTINGS, ROOM, DECK }

// ── Sheet mechanics (shared): translucent surface over the live scope, hairline top
// rule, drag-down / scrim-tap / ✕ / Back to dismiss, 200 ms decelerate, no bounce. ──
@Composable
fun SheetHost(
    p: Palette,
    title: String,
    reduced: Boolean,
    onDismiss: () -> Unit,
    body: @Composable () -> Unit,
) {
    var dragPx by remember { mutableFloatStateOf(0f) }
    val style = LocalRoomStyle.current
    // Expressive dismiss (Ben's ask): the old `visible = true` meant the exit
    // could NEVER play — dismissal was an instant removal. Now the sheet owns a
    // real open/close state: ✕/scrim/drag/Back play the departure (accelerating
    // slide DOWN + fade — the motion says where it went) and the composition
    // leaves only after the choreography finishes.
    val openState = remember { MutableTransitionState(false).apply { targetState = true } }
    val dismiss = {
        if (openState.targetState) openState.targetState = false
    }
    LaunchedEffect(openState.targetState, openState.isIdle) {
        if (!openState.targetState && openState.isIdle) onDismiss()
    }
    BackHandler { dismiss() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = Dim.scrimAlpha))
            .pointerInput(Unit) { detectTapGestures(onTap = { dismiss() }) },
        contentAlignment = Alignment.BottomCenter,
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
            val sheetShape = RoundedCornerShape(
                topStart = style.cornerRadius, topEnd = style.cornerRadius
            )
            Column(
                Modifier
                    .offset { IntOffset(0, dragPx.coerceAtLeast(0f).roundToInt()) }
                    .fillMaxWidth()
                    .clip(sheetShape)
                    .background(p.surface.copy(alpha = Dim.sheetAlpha * style.panelAlphaScale))
                    .border(Dim.hairline, p.lineStrong, sheetShape)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(Dim.sheetPad)
                    // Swallow taps; own vertical drags for the pull-down dismiss.
                    .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (dragPx > 140f) dismiss()
                                dragPx = 0f
                            },
                            onDragCancel = { dragPx = 0f },
                        ) { change, delta ->
                            change.consume()
                            dragPx = (dragPx + delta).coerceAtLeast(0f)
                        }
                    },
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Mono(title, p.ink2, Type.data)
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
    SheetHost(p, "SOURCE", reduced, onDismiss) {
      Column(Modifier.verticalScroll(rememberScrollState())) {
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
        SectionHeading("MICROPHONE", p)
        SheetRow("built-in mic", p, checked = state.sourceLabel == "mic") {
            actions.startMic(); onDismiss()
        }
        SectionHeading("REMOTE", p)
        RemoteFlow(state, p, actions, onDismiss)
        Prose(
            "Remote scopes another machine's audio over Tailscale — it plays here and " +
                "the transport drives that machine. Local capture can't see Spotify or " +
                "DRM apps; games, browsers and local players work.",
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
    onDismiss: () -> Unit,
) {
    val view = LocalView.current
    val groups = listOf(
        "XY" to listOf(0, 1, 2, 3),
        "3D" to listOf(4, 5),
        "TIME" to listOf(6, 7),
        "SPECTRUM" to listOf(8, 9, 10),
    )
    SheetHost(p, "MODE", reduced, onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            groups.forEachIndexed { gi, (heading, indices) ->
                SectionHeading(heading, p, if (gi == 0) Modifier.padding(top = 0.dp) else Modifier)
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
    SheetHost(p, "ROOM", reduced, onDismiss) {
        // Breathing pulse for follows-beam tiles (one clock for all).
        val breath by rememberInfiniteTransition(label = "breath").animateFloat(
            initialValue = 0.35f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(1400, easing = Motion.standard), RepeatMode.Reverse
            ),
            label = "breathA",
        )
        LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.heightIn(max = 340.dp)) {
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
fun SettingsSheet(
    state: ScopeUiState,
    p: Palette,
    reduced: Boolean,
    actions: SheetActions,
    focusValue: Float,
    onFocus: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    SheetHost(p, "SETTINGS", reduced, onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SectionHeading("SIGNAL", p, Modifier.padding(top = 0.dp))
            DragRule("FOCUS", focusValue, 0.3f, 3.0f, p, { "%.2f px".format(it) }, onFocus)
            DragRule(
                "GAIN", state.gain, 0.1f, 6.0f, p, { "×%.2f".format(it) },
            ) { actions.setGainAbsolute(it) }
            DragRule(
                "BEAM", state.beamEnergy, 1.0f, 30.0f, p, { "×%.0f".format(it) },
            ) { actions.setBeamEnergy(it) }
            DragRule(
                "GLOW", state.glow, 0.0f, 0.98f, p, { "%.0f %%".format(it * 100) },
            ) { actions.setGlow(it) }

            SectionHeading("DISPLAY", p)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.weight(1f)) {
                    ChipCell("GRID · " + (if (state.grid) "on" else "off"), active = state.grid, p = p, small = true) {
                        actions.setGrid(!state.grid)
                    }
                }
                Spacer(Modifier.weight(2f))
            }
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

            SectionHeading("PERFORMANCE", p)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.weight(1f)) {
                    ChipCell(
                        "NERD HUD · " + (if (state.nerdHud) "on" else "off"),
                        active = state.nerdHud, p = p, small = true,
                    ) { state.nerdHud = !state.nerdHud }
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

            SectionHeading("ROOM & LIGHT", p)
            SheetRow("room · ${state.room.label}", p) { actions.openRoom() }
            SheetRow("light · beam color", p) { actions.openLight() }

            SectionHeading("ABOUT", p)
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
    }
}

// What the sheets may ask of the host (grows per act).
interface SheetActions {
    fun openFile()
    fun startMic()
    fun startCapture()
    fun startRemote()
    fun stopLive()
    fun captureConsentNeeded(): Boolean
    fun setFps(value: Int)
    fun setOversample(n: Int)
    fun setGainAbsolute(g: Float)
    fun setBeamEnergy(e: Float)
    fun setGlow(g: Float)
    fun setGrid(on: Boolean)
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
