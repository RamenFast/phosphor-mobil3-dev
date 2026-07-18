package dev.phosphor.mobil3.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.delay

// What the chrome can ask the host to do. Keeps Compose free of Android service plumbing.
interface ScopeActions {
    fun togglePlay()
    fun openFile()
    fun startMic()
    fun startCapture()
    fun setMode(index: Int)
    fun setBeam(index: Int)
    fun setFps(value: Int)
    fun setOversample(n: Int)
    fun setRoom(room: Palette)
    fun makeSurface(): SurfaceView
}

private enum class Sheet { NONE, SOURCE, MODE, LIGHT, SETTINGS }

@Composable
fun PhosphorScreen(state: ScopeUiState, actions: ScopeActions) {
    val p = state.room
    var consoleVisible by remember { mutableStateOf(true) }
    var sheet by remember { mutableStateOf(Sheet.NONE) }

    // Predictive back peels one layer at a time (UX law): sheet → console → system exit.
    BackHandler(enabled = sheet != Sheet.NONE) { sheet = Sheet.NONE }
    BackHandler(enabled = sheet == Sheet.NONE && consoleVisible) { consoleVisible = false }

    // Console auto-hides after 4 s of no interaction (burn-in + clean stage).
    LaunchedEffect(consoleVisible, sheet) {
        if (consoleVisible && sheet == Sheet.NONE) {
            delay(4000)
            consoleVisible = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Layer 0: the scope, full-bleed under the chrome.
        AndroidView(factory = { actions.makeSurface() }, modifier = Modifier.fillMaxSize())

        // Layer 0.5: transparent stage tap-catcher. Sits BELOW the console (drawn earlier),
        // so console buttons — drawn later, hit-tested first — win taps in their bounds and
        // only taps on the bare scope reach here.
        if (sheet == Sheet.NONE) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { consoleVisible = !consoleVisible },
                            onDoubleTap = { actions.togglePlay() },
                        )
                    }
            )
        }

        // Layer 1a: read-only status band, top, flanking the punch-hole.
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Mono("src · ${state.sourceLabel}", p.ink2, 12.sp)
            Mono("${state.modeTag}", p.ink2, 12.sp)
        }

        // Layer 1b: console strip, bottom, auto-hiding.
        AnimatedVisibility(
            visible = consoleVisible && sheet == Sheet.NONE,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Console(state, p, onMode = { sheet = Sheet.MODE }, onSrc = { sheet = Sheet.SOURCE },
                onMore = { sheet = Sheet.SETTINGS }, onLight = { sheet = Sheet.LIGHT },
                onPlay = { actions.togglePlay() })
        }

        // Layer 2: sheets.
        when (sheet) {
            Sheet.SOURCE -> SheetScrim(p, "SOURCE", { sheet = Sheet.NONE }) {
                SheetRow("open file / folder…", p) { actions.openFile(); sheet = Sheet.NONE }
                SheetRow("microphone · room", p) { actions.startMic(); sheet = Sheet.NONE }
                SheetRow("other apps · capture", p) { actions.startCapture(); sheet = Sheet.NONE }
                Mono(
                    "Spotify & DRM apps opt out of capture — they arrive silent. Games, browsers and local players work.",
                    p.muted, 11.sp, FontFamily.SansSerif,
                    Modifier.padding(top = 8.dp),
                )
            }
            Sheet.MODE -> SheetScrim(p, "MODE", { sheet = Sheet.NONE }) {
                LazyVerticalGrid(columns = GridCells.Fixed(2)) {
                    itemsIndexed(ModeLabels) { i, label ->
                        ChipCell(label, active = i == state.modeIndex, p = p) {
                            actions.setMode(i)
                        }
                    }
                }
            }
            Sheet.LIGHT -> SheetScrim(p, "LIGHT", { sheet = Sheet.NONE }) {
                LazyVerticalGrid(columns = GridCells.Fixed(3)) {
                    itemsIndexed(BeamColors) { i, sw ->
                        SwatchCell(sw, active = i == state.beamIndex, p = p) {
                            actions.setBeam(i)
                        }
                    }
                }
            }
            Sheet.SETTINGS -> SheetScrim(p, "SETTINGS", { sheet = Sheet.NONE }) {
                Mono("FRAME RATE", p.muted, 11.sp, Modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FpsOptions.forEach { opt ->
                        Box(Modifier.weight(1f)) {
                            ChipCell(opt.label, active = opt.value == state.fpsValue, p = p, small = true) {
                                actions.setFps(opt.value)
                            }
                        }
                    }
                }
                Mono(FpsNote, p.muted, 10.sp, FontFamily.SansSerif, Modifier.padding(top = 6.dp), maxLines = 3)

                Mono("BEAM RATE", p.muted, 11.sp, Modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BeamRates.forEach { opt ->
                        Box(Modifier.weight(1f)) {
                            ChipCell(opt.label, active = opt.oversample == state.oversample, p = p, small = true) {
                                actions.setOversample(opt.oversample)
                            }
                        }
                    }
                }
                Mono(BeamRateNote, p.muted, 10.sp, FontFamily.SansSerif, Modifier.padding(top = 6.dp), maxLines = 3)
                Mono("ROOM", p.muted, 11.sp, Modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Rooms.forEach { room ->
                        Box(Modifier.weight(1f)) {
                            ChipCell(room.label, active = room.id == p.id, p = p, small = true) {
                                actions.setRoom(room)
                            }
                        }
                    }
                }
                Mono("BEAM", p.muted, 11.sp, Modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                LazyVerticalGrid(columns = GridCells.Fixed(3)) {
                    itemsIndexed(BeamColors) { i, sw ->
                        SwatchCell(sw, active = i == state.beamIndex, p = p) { actions.setBeam(i) }
                    }
                }
            }
            Sheet.NONE -> {}
        }
    }
}

@Composable
private fun Console(
    state: ScopeUiState, p: Palette,
    onMode: () -> Unit, onSrc: () -> Unit, onMore: () -> Unit, onLight: () -> Unit,
    onPlay: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(p.surface.copy(alpha = 0.86f))
            .border(1.dp, p.line)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        state.trackTitle?.let { title ->
            Mono(
                buildString {
                    append(title)
                    state.trackArtist?.let { if (it.isNotBlank() && it != "null") append("  —  $it") }
                },
                p.ink, 13.sp, maxLines = 1,
            )
            Spacer(Modifier.height(8.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            StoneKey(if (state.playing) "❚❚" else "▶", p, onPlay)
            Spacer(Modifier.width(14.dp))
            FlatKey("MODE", p, onMode)
            Spacer(Modifier.width(8.dp))
            FlatKey("SRC", p, onSrc)
            Spacer(Modifier.width(8.dp))
            FlatKey("LIGHT", p, onLight)
            Spacer(Modifier.weight(1f))
            FlatKey("⋯", p, onMore)
        }
    }
}

// The one dimensional control on this surface (the carved play/pause stone).
@Composable
private fun StoneKey(label: String, p: Palette, onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .background(p.stone)
            .border(1.dp, p.stoneHi)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Mono(label, p.ink, 18.sp) }
}

@Composable
private fun FlatKey(label: String, p: Palette, onClick: () -> Unit) {
    Box(
        Modifier
            .height(44.dp)
            .border(1.dp, p.line)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Mono(label, p.ink2, 13.sp) }
}

@Composable
private fun SheetScrim(p: Palette, title: String, onDismiss: () -> Unit, body: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x66000000))
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(p.surface.copy(alpha = 0.94f))
                .border(1.dp, p.lineStrong)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp)
                // Swallow taps inside the sheet so they don't dismiss it.
                .pointerInput(Unit) { detectTapGestures(onTap = {}) },
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Mono(title, p.ink2, 13.sp)
                Mono("✕", p.ink2, 16.sp, Modifier = Modifier.clickable(onClick = onDismiss))
            }
            Spacer(Modifier.height(12.dp))
            body()
        }
    }
}

@Composable
private fun SheetRow(label: String, p: Palette, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(1.dp, p.line)
            .padding(14.dp),
    ) { Mono(label, p.ink, 14.sp) }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ChipCell(label: String, active: Boolean, p: Palette, small: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(3.dp)
            .fillMaxWidth()
            .height(if (small) 40.dp else 48.dp)
            .border(1.dp, if (active) p.accent else p.line)
            .clickable(onClick = onClick)
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) { Mono(label, if (active) p.accent else p.ink2, if (small) 11.sp else 12.sp, maxLines = 1) }
}

@Composable
private fun SwatchCell(sw: BeamSwatch, active: Boolean, p: Palette, onClick: () -> Unit) {
    Column(
        Modifier.padding(4.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(38.dp)
                .background(sw.color)
                .border(if (active) 2.dp else 1.dp, if (active) p.accent else p.line),
        )
        Mono(sw.label, if (active) p.accent else p.muted, 10.sp, Modifier = Modifier.padding(top = 3.dp), maxLines = 1)
    }
}

@Composable
private fun Mono(
    text: String, color: Color, size: androidx.compose.ui.unit.TextUnit,
    family: FontFamily = FontFamily.Monospace, Modifier: Modifier = androidx.compose.ui.Modifier,
    maxLines: Int = 1,
) {
    BasicText(
        text = text,
        style = TextStyle(color = color, fontFamily = family, fontSize = size),
        maxLines = maxLines, overflow = TextOverflow.Ellipsis, modifier = Modifier,
    )
}
