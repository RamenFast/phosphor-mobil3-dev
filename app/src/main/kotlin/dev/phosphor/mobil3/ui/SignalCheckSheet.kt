package dev.phosphor.mobil3.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.phosphor.mobil3.SignalCheckView

@Composable
internal fun SignalCheckEntry(state: ScopeUiState, p: Palette) {
    SignalCheckAction(if (state.signalCheckExpanded) "SIGNAL CHECK  ▴" else "SIGNAL CHECK  ▾", p) {
        state.signalCheckExpanded = !state.signalCheckExpanded
    }
    if (state.signalCheckExpanded) SignalCheckContent(state, p) { state.showSourcePicker = true }
}

@Composable
internal fun SignalCheckSheet(state: ScopeUiState, p: Palette, reduced: Boolean, onDismiss: () -> Unit) {
    SheetHost(p, "SIGNAL CHECK", reduced, onDismiss, glyph = SettingsGlyph.Signal) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            SignalCheckContent(state, p) { state.showSourcePicker = true }
        }
    }
}

@Composable
private fun SignalCheckContent(state: ScopeUiState, p: Palette, onSources: () -> Unit) {
    DisposableEffect(state) {
        state.signalCheck = SignalCheckView()
        onDispose { state.signalCheckVisible = false }
    }
    Column(Modifier.fillMaxWidth().border(1.dp, p.muted, RectangleShape)
        .background(p.surface).padding(12.dp).onGloballyPositioned { position ->
            val bounds = position.boundsInWindow()
            state.signalCheckVisible = position.isAttached && bounds.width > 0f && bounds.height > 0f
        }) {
        Mono(state.signalCheck.status, p.ink, maxLines = Int.MAX_VALUE)
        Prose("One current input. Reading does not start, grant access, connect or change playback.", p.ink,
            modifier = Modifier.padding(vertical = 8.dp))
        state.signalCheck.rows.forEach { (label, value) ->
            Mono(label.uppercase(), p.ink, modifier = Modifier.padding(top = 12.dp), maxLines = Int.MAX_VALUE)
            Prose(value, p.ink, modifier = Modifier.padding(top = 4.dp))
        }
        SignalCheckAction("OPEN SOURCES", p, onSources)
    }
}

@Composable
internal fun SignalCheckAction(label: String, p: Palette, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .clickable(role = Role.Button, onClick = sheetTap(onClick)).padding(vertical = 14.dp, horizontal = 8.dp)) {
        Mono(label, p.ink)
    }
}
