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
import androidx.compose.runtime.CompositionLocalProvider
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
    SettingAction("signal check", p, value = if (state.signalCheckExpanded) "hide" else "show") {
        state.signalCheckExpanded = !state.signalCheckExpanded
    }
    if (state.signalCheckExpanded) SignalCheckContent(state, p) { state.showSourcePicker = true }
}

@Composable
internal fun SignalCheckSheet(state: ScopeUiState, p: Palette, reduced: Boolean, onDismiss: () -> Unit) {
    val p = p.sheetText()
    CompositionLocalProvider(LocalSettingsControlAccess provides true) {
    SheetHost(p, "SIGNAL CHECK", reduced, onDismiss, glyph = SettingsGlyph.Signal) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState(), overscrollEffect = null)) {
            SignalCheckContent(state, p) { state.showSourcePicker = true }
        }
    }
    }
}

/** Measurements only; reading starts nothing. Visible bounds drive the native observation. */
@Composable
private fun SignalCheckContent(state: ScopeUiState, p: Palette, onSources: () -> Unit) {
    DisposableEffect(state) {
        state.signalCheck = SignalCheckView()
        onDispose { state.signalCheckVisible = false }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).onGloballyPositioned { position ->
        val bounds = position.boundsInWindow()
        state.signalCheckVisible = position.isAttached && bounds.width > 0f && bounds.height > 0f
    }) {
        Mono(state.signalCheck.status, p.ink, Type.label, maxLines = Int.MAX_VALUE)
        state.signalCheck.rows.forEach { (label, value) ->
            Mono(label.lowercase(), p.ink2, Type.value, Modifier.padding(top = 12.dp), maxLines = Int.MAX_VALUE)
            Prose(value, p.ink, size = Type.hint, modifier = Modifier.padding(top = 2.dp))
        }
        KeyRow { SheetKey("open sources", p, onClick = onSources) }
        RowDivider(p)
    }
}
