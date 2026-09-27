package dev.phosphor.mobil3.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.phosphor.mobil3.settings.appearance.*
import java.util.Locale

interface AppearanceActions {
    fun previewAppearance(value: AppearanceValue) {}
    fun applyAppearance(value: AppearanceValue, id: String = "") {}
    fun selectAppearance(id: String) {}
    fun cancelAppearancePreview() {}
    fun saveAppearance(name: String, value: AppearanceValue, id: String? = null) {}
    fun renameAppearance(id: String, name: String) {}
    fun deleteAppearance(id: String) {}
    fun resetAppearance() {}
    fun repairAppearance() {}
    fun recoverAppearance() {}
}

@Composable
internal fun AppearanceEditor(state: ScopeUiState, actions: AppearanceActions) {
    val currentActions = rememberUpdatedState(actions)
    DisposableEffect(Unit) { onDispose { currentActions.value.cancelAppearancePreview() } }
    // The repair surface is deliberately opaque and separate from the authored chrome palette.
    val p = remember { AppearancePalette.palette(CuratedAppearances.dark, "editor:readable", "Readable editor") }
    val style = LocalRoomStyle.current
    val plane = state.appearanceValue?.colors?.plane?.let { androidx.compose.ui.graphics.Color(it or 0xff000000.toInt()) }
        ?: p.plane
    val committed = state.appearanceDocument
    val seed = committed?.active ?: CuratedAppearances.amoled
    var draft by remember(committed) { mutableStateOf(seed) }
    var colorFields by remember(committed) { mutableStateOf(AppearanceEditorValues.colors(seed)) }
    var duration by remember(committed) { mutableStateOf(seed.durationScale.toString()) }
    var density by remember(committed) { mutableStateOf(seed.densityScale.toString()) }
    var radius by remember(committed) { mutableStateOf(seed.radiusDp.toString()) }
    var alpha by remember(committed) { mutableStateOf(seed.panelAlphaScale.toString()) }
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var selected by remember(committed) { mutableStateOf(committed?.activeId ?: "") }
    var legacyExpanded by remember { mutableStateOf(false) }
    fun resolved() = AppearanceEditorValues.value(draft, colorFields, duration, density, radius, alpha)
    fun runDraft(action: (AppearanceValue) -> Unit) {
        runCatching { resolved() }.onSuccess { error = ""; action(it) }
            .onFailure { error = "Draft not applied. ${it.message}. Correct the labeled field." }
    }
    fun replaceDraft(value: AppearanceValue) {
        draft = value
        colorFields = AppearanceEditorValues.colors(value)
        duration = value.durationScale.toString()
        density = value.densityScale.toString()
        radius = value.radiusDp.toString()
        alpha = value.panelAlphaScale.toString()
        error = ""
    }
    Column(Modifier.fillMaxWidth().background(plane).padding(style.space(3.dp))
        .background(p.surface).padding(style.space(8.dp))
        .onPreviewKeyEvent {
            if (it.type == KeyEventType.KeyUp && it.key == Key.Escape) {
                actions.cancelAppearancePreview()
                replaceDraft(seed)
                true
            } else false
        }, verticalArrangement = Arrangement.spacedBy(style.space(6.dp))) {
        EditorText(state.appearanceSummary, p)
        EditorText("Readable editor surface. Status text and control labels/outlines use contrast-safe presentation colors over opaque interiors. Stored colors stay exact. The outer frame previews plane. Glass means translucent app chrome, not compositor blur or HUD transparency.", p)
        if (state.appearanceStatus.isNotBlank()) EditorText(state.appearanceStatus, p)
        if (state.appearanceRepairRequired) {
            EditorText("Original appearance bytes remain untouched. Complete replacement discards the unavailable appearance document only.", p)
            AppearanceButton("REPLACE UNAVAILABLE APPEARANCE WITH AMOLED", p) { actions.repairAppearance() }
        }
        if (state.appearanceRecoveryRequired) {
            AppearanceButton("RETRY AUTHORITATIVE APPEARANCE, THEN INSTRUMENT SAVE", p) { actions.recoverAppearance() }
        }
        val enabled = !state.appearanceBlocked
        AppearanceDocument.CURATED.forEach { record ->
            AppearanceButton("LOAD ${record.name.uppercase(Locale.ROOT)} DRAFT", p, enabled) {
                selected = record.id
                replaceDraft(record.value)
            }
        }
        EditorText("Draft selection does not change the look. PREVIEW is temporary. APPLY persists. SAVE also creates a named record.", p)
        AppearanceEditorValues.colorNames.forEach { key ->
            val argb = key == "line" || key == "lineStrong"
            AppearanceField("$key · ${if (argb) "ARGB32 AARRGGBB" else "RGB24 RRGGBB"}",
                colorFields.getValue(key), p, enabled) { colorFields = colorFields + (key to it) }
        }
        AppearanceButton("CONSOLE KEYS · ${if (draft.lookVersion == 2) "TACTILE" else "LEGACY"}", p, enabled) {
            draft = draft.copy(lookVersion = if (draft.lookVersion == 2) 1 else 2)
        }
        EditorText("Tactile keys change only the no-track console. PREVIEW to try them. APPLY or SAVE to keep this look.", p)
        AppearanceButton("DARK PALETTE · ${draft.dark}", p, enabled) { draft = draft.copy(dark = !draft.dark) }
        AppearanceButton("ACCENT FOLLOWS MEASURED BEAM · ${draft.accentFollowsBeam}", p, enabled) {
            draft = draft.copy(accentFollowsBeam = !draft.accentFollowsBeam)
        }
        AppearanceCharacter.entries.forEach { character ->
            AppearanceButton("CHARACTER ${character.name}${if (draft.character == character) " · selected" else ""}", p, enabled) {
                draft = draft.copy(character = character)
            }
        }
        AppearanceMotion.entries.forEach { motion ->
            AppearanceButton("MOTION ${motion.name}${if (draft.motion == motion) " · selected" else ""}", p, enabled) {
                draft = draft.copy(motion = motion)
            }
        }
        AppearanceField("Duration scale · 0.25 to 2.0", duration, p, enabled) { duration = it }
        AppearanceField("Density scale · 0.85 to 1.25, never smaller touch targets", density, p, enabled) { density = it }
        AppearanceField("Corner radius dp · integer 0 to 64", radius, p, enabled) { radius = it }
        AppearanceField("Panel opacity scale · 0.2 to 1.0", alpha, p, enabled) { alpha = it }
        AppearanceButton("MONOSPACE PROSE · ${draft.monoProse}", p, enabled) { draft = draft.copy(monoProse = !draft.monoProse) }
        AppearanceButton("PART DESIGNATORS · ${draft.designators}", p, enabled) { draft = draft.copy(designators = !draft.designators) }
        val checks = runCatching { AppearanceContrast.textBackplateChecks(resolved()) }.getOrDefault(emptyList())
        checks.filterNot { it.passes }.forEach {
            EditorText("READABILITY WARNING · ${it.role}: ${String.format(Locale.ROOT, "%.2f", it.ratio)}:1, needs ${it.minimum}:1. Stored values remain exact.", p)
        }
        if (draft.accentFollowsBeam) EditorText("Measured beam contrast varies. Control text uses a readable presentation fallback when needed. Stored and native beam colors stay exact.", p)
        if (checks.any { !it.passes }) AppearanceButton("PROPOSE READABLE COLORS IN DRAFT · replaces all color roles", p, enabled) {
            runDraft { replaceDraft(AppearanceEditorValues.readable(it)) }
        }
        if (error.isNotBlank()) EditorText(error, p)
        AppearanceButton("PREVIEW · temporary", p, enabled) { runDraft(actions::previewAppearance) }
        AppearanceButton("APPLY · persist draft", p, enabled) { runDraft { actions.applyAppearance(it, selected) } }
        AppearanceButton("CANCEL · restore committed appearance", p) {
            actions.cancelAppearancePreview()
            selected = committed?.activeId ?: ""
            replaceDraft(seed)
        }
        AppearanceField("Appearance name · 1 to 64 characters", name, p, enabled, ascii = false) { name = it }
        AppearanceButton("SAVE AS NEW NAMED APPEARANCE", p, enabled) { runDraft { actions.saveAppearance(name, it) } }
        val selectedUser = committed?.users?.find { it.id == selected }
        if (selectedUser != null) {
            AppearanceButton("SAVE DRAFT OVER ${selectedUser.name}", p, enabled) { runDraft { actions.saveAppearance(selectedUser.name, it, selectedUser.id) } }
            AppearanceButton("RENAME ${selectedUser.name} TO ENTERED NAME", p, enabled) { actions.renameAppearance(selectedUser.id, name) }
            AppearanceButton("DELETE ${selectedUser.name} · retain active colors", p, enabled) { actions.deleteAppearance(selectedUser.id) }
        }
        committed?.users?.forEach { record ->
            AppearanceButton("LOAD SAVED DRAFT · ${record.name}", p, enabled) {
                selected = record.id
                name = record.name
                replaceDraft(record.value)
            }
        }
        AppearanceButton("LEGACY SNAPSHOTS · ${if (legacyExpanded) "collapse" else "expand"}", p) {
            legacyExpanded = !legacyExpanded
        }
        if (legacyExpanded) committed?.legacy?.forEach { record ->
            AppearanceButton("LOAD IMMUTABLE DRAFT · ${record.name}", p, enabled) {
                selected = record.id
                replaceDraft(record.value)
            }
        }
        AppearanceButton("RESET TO AMOLED · keep saved appearances", p, enabled) { actions.resetAppearance() }
    }
}

@Composable
private fun EditorText(text: String, p: Palette) {
    BasicText(text, style = TextStyle(color = p.ink, fontSize = 14.sp, fontFamily = FontFamily.Monospace))
}

@Composable
internal fun AppearanceButton(label: String, p: Palette, enabled: Boolean = true, action: () -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).settingsFocusBorder(p)
        .border(1.dp, p.lineStrong).clickable(enabled = enabled, role = Role.Button, onClick = sheetTap(action))
        .padding(LocalRoomStyle.current.space(10.dp)), verticalArrangement = Arrangement.Center) {
        EditorText(if (enabled) label else "$label · unavailable", p)
    }
}

@Composable
private fun AppearanceField(label: String, value: String, p: Palette, enabled: Boolean,
    ascii: Boolean = true, change: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        EditorText(label, p)
        BasicTextField(value = value, onValueChange = change, enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).settingsFocusBorder(p)
                .border(1.dp, p.lineStrong).semantics { contentDescription = label }.padding(LocalRoomStyle.current.space(10.dp)),
            textStyle = TextStyle(color = p.ink, fontSize = 16.sp, fontFamily = FontFamily.Monospace),
            cursorBrush = SolidColor(p.accent), singleLine = false,
            keyboardOptions = KeyboardOptions(keyboardType = if (ascii) KeyboardType.Ascii else KeyboardType.Text,
                imeAction = ImeAction.Next))
    }
}
