package dev.phosphor.mobil3.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.phosphor.mobil3.settings.instrument.CuratedInstrumentPresets
import dev.phosphor.mobil3.settings.instrument.InstrumentImportChoice

interface InstrumentPresetActions {
    fun openInstrumentPresets() {}
    fun applyInstrument(key: String) {}
    fun saveInstrument(name: String) {}
    fun updateInstrument(id: String) {}
    fun renameInstrument(id: String, name: String) {}
    fun duplicateInstrument(key: String, name: String) {}
    fun deleteInstrument(id: String) {}
    fun undoInstrument() {}
    fun cancelInstrumentApply() {}
    fun retryInstrumentSave() {}
    fun keepInstrumentSafe() {}
    fun allowInstrumentRapid() {}
    fun importInstrumentPresets() {}
    fun exportInstrumentPresets(id: String? = null) {}
    fun chooseInstrumentImport(id: String, choice: InstrumentImportChoice) {}
    fun commitInstrumentImport() {}
    fun cancelInstrumentImport() {}
    fun instrumentSourceControls() {}
}

/**
 * The one key (design/REDESIGN.md §4): hairline rectangle, 48dp, sharp corners.
 * Pressed = 10% accent tint, instant. Active = accent rim + accent text.
 */
@Composable
internal fun SheetKey(label: String, p: Palette, modifier: Modifier = Modifier, active: Boolean = false,
    enabled: Boolean = true, description: String? = null, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier.heightIn(min = 48.dp).widthIn(min = 48.dp)
            .background(if (pressed) p.accent.copy(alpha = 0.10f) else p.surface)
            .border(Dim.hairline, if (active && enabled) p.accent else p.line)
            .settingsFocusBorder(p)
            .semantics { selected = active; description?.let { contentDescription = it } }
            .clickable(interactionSource = interaction, indication = null, enabled = enabled,
                role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.45f)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Mono(label, if (active && enabled) p.accent else p.ink, Type.label)
    }
}

/** A wrapping row of keys with the one 8dp gap. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun KeyRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
internal fun NameField(value: String, label: String, p: Palette, changed: (String) -> Unit) {
    BasicTextField(value, changed, singleLine = true,
        textStyle = TextStyle(color = p.ink, fontFamily = MonoFace, fontSize = Type.label),
        cursorBrush = SolidColor(p.accent),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).border(Dim.hairline, p.lineStrong)
            .settingsFocusBorder(p).padding(horizontal = 12.dp, vertical = 13.dp)
            .semantics { contentDescription = label },
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Mono(label, p.muted, Type.label)
                inner()
            }
        })
}

/** One pick-able row: name left, chosen mark right. */
@Composable
private fun SetupRow(name: String, chosen: Boolean, trailing: String?, p: Palette, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { selected = chosen }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Mono(name, if (chosen) p.accent else p.ink, Type.label, Modifier.weight(1f))
        trailing?.let {
            Spacer(Modifier.width(12.dp))
            Mono(it, p.ink2, Type.value)
        }
        // A selection, not a switch: a filled mark only when chosen, nothing otherwise.
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(10.dp).background(if (chosen) p.accent else androidx.compose.ui.graphics.Color.Transparent))
    }
    RowDivider(p)
}

private enum class SetupEdit { NONE, SAVE, RENAME, COPY, DELETE }

/** Saved setups inline in Settings: name rows, one actions row under the chosen one. */
@Composable
internal fun SetupsGroup(state: ScopeUiState, p: Palette, actions: SheetActions, setups: InstrumentPresetActions) {
    var chosen by remember { mutableStateOf<String?>(null) }
    var edit by remember { mutableStateOf(SetupEdit.NONE) }
    var name by remember { mutableStateOf("") }
    val collection = state.instrumentCollection
    val ready = collection != null

    // Live results and recovery only. No help prose. The newest result wins.
    var status by remember { mutableStateOf(state.instrumentApplyStatus.ifBlank { state.instrumentStatus }) }
    LaunchedEffect(state.instrumentStatus) { if (state.instrumentStatus.isNotBlank()) status = state.instrumentStatus }
    LaunchedEffect(state.instrumentApplyStatus) { if (state.instrumentApplyStatus.isNotBlank()) status = state.instrumentApplyStatus }
    if (status.isNotBlank()) SettingNote(status, p)
    if (state.instrumentRapid) {
        SettingNote("fast color changes can trigger seizures", p)
        KeyRow {
            SheetKey("keep safe", p) { setups.keepInstrumentSafe() }
            SheetKey("allow faster", p) { setups.allowInstrumentRapid() }
        }
    }
    val recovery = state.instrumentPending || state.instrumentUndo || state.instrumentUnsaved ||
        (state.remote && state.remoteGeometry) || state.instrumentSourceRequired
    if (recovery) KeyRow {
        if (state.instrumentPending) SheetKey("cancel apply", p) { setups.cancelInstrumentApply() }
        if (state.instrumentUndo) SheetKey("undo apply", p) { setups.undoInstrument() }
        if (state.instrumentUnsaved) SheetKey(if (state.instrumentRestoreRequired) "restore" else "retry save", p) {
            setups.retryInstrumentSave()
        }
        if ((state.remote && state.remoteGeometry) || state.instrumentSourceRequired) {
            SheetKey("sources", p) { setups.instrumentSourceControls() }
        }
    }

    val entries = (collection?.records?.map { Triple(it.id, it.name, false) } ?: emptyList()) +
        CuratedInstrumentPresets.all.map { Triple("curated:${it.name}", it.name, true) }
    entries.forEach { (key, label, curated) ->
        val isChosen = chosen == key
        SetupRow(label, isChosen, if (curated) "starter" else null, p, enabled = ready || curated) {
            chosen = if (isChosen) null else key
            edit = SetupEdit.NONE
        }
        if (isChosen) {
            when (edit) {
                SetupEdit.RENAME, SetupEdit.COPY -> {
                    Spacer(Modifier.height(8.dp))
                    NameField(name, "name", p) { name = it }
                    KeyRow {
                        SheetKey("save", p, active = true, enabled = ready && name.isNotBlank()) {
                            if (edit == SetupEdit.RENAME) setups.renameInstrument(key, name)
                            else setups.duplicateInstrument(key, name)
                            edit = SetupEdit.NONE
                        }
                        SheetKey("cancel", p) { edit = SetupEdit.NONE }
                    }
                }
                SetupEdit.DELETE -> KeyRow {
                    SheetKey("delete $label", p, active = true) {
                        setups.deleteInstrument(key); chosen = null; edit = SetupEdit.NONE
                    }
                    SheetKey("keep", p) { edit = SetupEdit.NONE }
                }
                else -> KeyRow {
                    SheetKey("apply", p, active = true) { setups.applyInstrument(key) }
                    if (!curated) SheetKey("update", p, description = "update $label with the current setup") {
                        setups.updateInstrument(key)
                    }
                    if (!curated) SheetKey("rename", p) { name = label; edit = SetupEdit.RENAME }
                    SheetKey("copy", p, enabled = ready) {
                        name = collection?.proposeDuplicateName(label) ?: label
                        edit = SetupEdit.COPY
                    }
                    if (!curated) SheetKey("export", p, enabled = !state.instrumentDocumentBusy) {
                        setups.exportInstrumentPresets(key)
                    }
                    if (!curated) SheetKey("delete", p) { edit = SetupEdit.DELETE }
                }
            }
            RowDivider(p)
        }
    }

    if (edit == SetupEdit.SAVE) {
        Spacer(Modifier.height(8.dp))
        NameField(name, "name this setup", p) { name = it }
        KeyRow {
            SheetKey("save", p, active = true, enabled = ready && name.isNotBlank()) {
                setups.saveInstrument(name); edit = SetupEdit.NONE; name = ""
            }
            SheetKey("cancel", p) { edit = SetupEdit.NONE }
        }
        RowDivider(p)
    } else {
        SettingAction("save current setup", p, value = "+", enabled = ready) {
            chosen = null; name = ""; edit = SetupEdit.SAVE
        }
    }
    SettingKeys("settings file", p, listOf(
        Triple("export", true) { actions.exportSettings() },
        Triple("import", true) { actions.importSettings() },
    ))
    if (state.settingsTransferStatus.isNotBlank()) SettingNote(state.settingsTransferStatus, p)
    val files = ready && !state.instrumentDocumentBusy
    SettingKeys("setups file", p, listOf(
        Triple("export", files) { setups.exportInstrumentPresets() },
        Triple("import", files) { setups.importInstrumentPresets() },
    ))
    if (state.instrumentDocumentBusy) KeyRow { SheetKey("cancel file", p) { setups.cancelInstrumentImport() } }
    state.instrumentPreview?.let { preview -> ImportPreview(state, preview, p, setups) }
}

/** Only while an import is pending: one line per incoming setup with its choices. */
@Composable
private fun ImportPreview(
    state: ScopeUiState,
    preview: dev.phosphor.mobil3.settings.instrument.InstrumentImportPreview,
    p: Palette,
    setups: InstrumentPresetActions,
) {
    GroupHeading("IMPORT", p)
    preview.incoming.records.forEach { record ->
        val conflicts = preview.conflictsFor(record.id)
        val choice = state.instrumentChoices[record.id]
        var copyName by remember(preview, record.id) { mutableStateOf(preview.base.proposeDuplicateName(record.name)) }
        Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Mono(record.name, p.ink, Type.label)
            KeyRow {
                if (conflicts.isEmpty()) SheetKey("add", p, active = choice == InstrumentImportChoice.Add) {
                    setups.chooseInstrumentImport(record.id, InstrumentImportChoice.Add)
                }
                SheetKey("skip", p, active = choice == InstrumentImportChoice.KeepExisting) {
                    setups.chooseInstrumentImport(record.id, InstrumentImportChoice.KeepExisting)
                }
                conflicts.forEach { target ->
                    SheetKey("replace ${preview.base.record(target).name}", p,
                        active = choice == InstrumentImportChoice.Replace(target)) {
                        setups.chooseInstrumentImport(record.id, InstrumentImportChoice.Replace(target))
                    }
                }
                SheetKey("as copy", p, active = choice is InstrumentImportChoice.SaveCopy) {
                    setups.chooseInstrumentImport(record.id, InstrumentImportChoice.SaveCopy(copyName))
                }
            }
            if (choice is InstrumentImportChoice.SaveCopy) NameField(copyName, "copy name", p) {
                copyName = it
                setups.chooseInstrumentImport(record.id, InstrumentImportChoice.SaveCopy(it))
            }
        }
        RowDivider(p)
    }
    KeyRow {
        SheetKey("save import", p, active = true,
            enabled = state.instrumentChoices.size == preview.incoming.records.size) { setups.commitInstrumentImport() }
        SheetKey("cancel", p) { setups.cancelInstrumentImport() }
    }
}
