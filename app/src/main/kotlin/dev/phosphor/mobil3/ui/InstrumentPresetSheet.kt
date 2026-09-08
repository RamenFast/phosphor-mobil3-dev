package dev.phosphor.mobil3.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.phosphor.mobil3.settings.instrument.*

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

@Composable
fun InstrumentPresetSheet(state: ScopeUiState, p: Palette, reduced: Boolean,
    actions: InstrumentPresetActions, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf<String?>(null) }
    var operation by remember { mutableStateOf("save") }
    var name by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<String?>(null) }
    val collection = state.instrumentCollection
    val selectedUser = collection?.records?.find { it.id == selected }
    val selectedCurated = CuratedInstrumentPresets.all.find { "curated:${it.name}" == selected }
    val selectedName = selectedUser?.name ?: selectedCurated?.name
    SheetHost(p, "INSTRUMENT PRESETS", reduced, onDismiss, glyph = SettingsGlyph.Display) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Prose(state.instrumentRecall, p.ink)
            if (state.instrumentApplyStatus.isNotBlank()) Prose(state.instrumentApplyStatus, p.ink)
            if (state.instrumentStatus.isNotBlank()) Prose(state.instrumentStatus, p.ink)
            Prose(if (state.displayPaused) "Display is paused. Applied tuning is ready for LIVE without changing the current pause presentation."
                else "Local authored tuning only. Source, audio, camera and inspection stay unchanged.", p.muted)
            if (state.remote && state.remoteGeometry) {
                Prose("Desktop geometry cannot accept a complete local setup. Saving local authored tuning is still available.", p.ink)
            }
            if ((state.remote && state.remoteGeometry) || state.instrumentSourceRequired) {
                PresetKey("SOURCE CONTROLS", p) { actions.instrumentSourceControls() }
            }
            if (state.instrumentPending) PresetKey("CANCEL PENDING APPLY", p) { actions.cancelInstrumentApply() }
            if (state.instrumentUndo) PresetKey("UNDO LAST APPLY", p) { actions.undoInstrument() }
            if (state.instrumentUnsaved) PresetKey(
                if (state.instrumentRestoreRequired) "RESTORE DISPLAYED SETUP"
                else "RETRY SAVE CURRENT · TUNING UNCHANGED", p,
            ) { actions.retryInstrumentSave() }
            if (state.instrumentRapid) {
                Prose("Below one second, full-screen color changes can trigger photosensitive seizures. Safe timing remains active. " +
                    "Only allow faster timing if it is safe for everyone watching.", p.ink)
                PresetKey("KEEP SAFE TIMING", p) { actions.keepInstrumentSafe() }
                PresetKey("I understand: allow faster and APPLY", p) { actions.allowInstrumentRapid() }
            }
            SectionHeading("CURATED STARTING POINTS", p)
            CuratedInstrumentPresets.all.forEach { entry ->
                val key = "curated:${entry.name}"
                PresetKey(entry.name, p, selected == key) { selected = key; deleting = null; operation = "save" }
                Prose(entry.purpose, p.muted)
            }
            SectionHeading("SAVED SETUPS ${collection?.records?.size ?: "?"}/64", p)
            if (collection?.records?.isEmpty() == true) Prose("No saved setups. Name the current authored setup below.", p.muted)
            collection?.records?.forEach { record ->
                PresetKey(record.name, p, selected == record.id) { selected = record.id; deleting = null; operation = "save" }
            }
            if (selected != null && selectedName != null) {
                SectionHeading("SELECTED · $selectedName", p)
                PresetKey("APPLY $selectedName", p) { actions.applyInstrument(selected!!) }
                PresetKey("DUPLICATE AS…", p, enabled = collection != null) {
                    operation = "duplicate"
                    name = collection?.proposeDuplicateName(selectedName) ?: ""
                }
                if (selectedUser != null) {
                    PresetKey("UPDATE WITH CURRENT AUTHORED SETUP", p) { actions.updateInstrument(selectedUser.id) }
                    PresetKey("RENAME…", p) { operation = "rename"; name = selectedUser.name }
                    PresetKey("EXPORT SELECTED", p, enabled = !state.instrumentDocumentBusy) { actions.exportInstrumentPresets(selectedUser.id) }
                    PresetKey("DELETE…", p) { deleting = selectedUser.id }
                    if (deleting == selectedUser.id) {
                        Prose("Delete ${selectedUser.name}? Current tuning stays unchanged.", p.ink)
                        PresetKey("DELETE ${selectedUser.name}", p) { actions.deleteInstrument(selectedUser.id); deleting = null; selected = null }
                        PresetKey("KEEP RECORD", p) { deleting = null }
                    }
                }
            }
            SectionHeading("NAME AND SAVE", p)
            if (state.lightTemporary) Prose("The temporary color roll is not saved. This records the underlying light setup.", p.muted)
            PresetKey("SAVE CURRENT AS…", p, operation == "save", enabled = collection != null) { operation = "save"; name = "" }
            PresetName(name, "Preset name", p) { name = it }
            val label = when (operation) { "rename" -> "SAVE RENAME"; "duplicate" -> "SAVE DUPLICATE"; else -> "SAVE NEW SETUP" }
            PresetKey(label, p, enabled = collection != null && (operation == "save" || selectedName != null)) {
                when (operation) {
                    "rename" -> selectedUser?.let { actions.renameInstrument(it.id, name) }
                    "duplicate" -> selected?.let { actions.duplicateInstrument(it, name) }
                    else -> actions.saveInstrument(name)
                }
            }
            SectionHeading("PORTABLE INSTRUMENT DOCUMENT", p)
            Prose("Import only previews saved records. It never applies tuning or grants permissions.", p.muted)
            PresetKey("IMPORT PREVIEW", p, enabled = collection != null && !state.instrumentDocumentBusy) { actions.importInstrumentPresets() }
            PresetKey("EXPORT ALL SAVED", p, enabled = collection != null && !state.instrumentDocumentBusy) { actions.exportInstrumentPresets() }
            if (state.instrumentDocumentBusy) PresetKey("CANCEL DOCUMENT OPERATION", p) { actions.cancelInstrumentImport() }
            state.instrumentPreview?.let { preview ->
                SectionHeading("INERT IMPORT PREVIEW", p)
                preview.incoming.records.forEach { record ->
                    val conflicts = preview.conflictsFor(record.id)
                    val choice = state.instrumentChoices[record.id]
                    var copyName by remember(preview, record.id) { mutableStateOf(preview.base.proposeDuplicateName(record.name)) }
                    Prose(record.name, p.ink)
                    if (conflicts.isEmpty()) PresetKey("ADD ${record.name}", p, choice == InstrumentImportChoice.Add) {
                        actions.chooseInstrumentImport(record.id, InstrumentImportChoice.Add)
                    }
                    PresetKey("KEEP EXISTING / SKIP ${record.name}", p, choice == InstrumentImportChoice.KeepExisting) {
                        actions.chooseInstrumentImport(record.id, InstrumentImportChoice.KeepExisting)
                    }
                    conflicts.forEach { target ->
                        PresetKey("REPLACE ${preview.base.record(target).name} · ${target.take(8)}", p,
                            choice == InstrumentImportChoice.Replace(target)) {
                            actions.chooseInstrumentImport(record.id, InstrumentImportChoice.Replace(target))
                        }
                    }
                    PresetName(copyName, "Copy name for ${record.name}", p) { copyName = it }
                    PresetKey("IMPORT NAMED COPY", p, choice is InstrumentImportChoice.SaveCopy) {
                        actions.chooseInstrumentImport(record.id, InstrumentImportChoice.SaveCopy(copyName))
                    }
                    Prose(if (choice == null) "Choose an action for this record." else "Choice: $choice", p.muted)
                }
                PresetKey("SAVE RESOLVED IMPORT · DOES NOT APPLY", p,
                    enabled = state.instrumentChoices.size == preview.incoming.records.size) { actions.commitInstrumentImport() }
                PresetKey("CANCEL PREVIEW", p) { actions.cancelInstrumentImport() }
            }
        }
    }
}

@Composable
private fun PresetKey(label: String, p: Palette, active: Boolean = false, enabled: Boolean = true, action: () -> Unit) {
    Mono(label, if (!enabled) p.muted else if (active) p.accent else p.ink, Type.data,
        Modifier.fillMaxWidth().padding(vertical = 3.dp).heightIn(min = 48.dp)
            .border(Dim.hairline, if (active) p.accent else p.line)
            .clickable(enabled = enabled, role = Role.Button, onClick = action)
            .padding(12.dp), maxLines = Int.MAX_VALUE)
}

@Composable
private fun PresetName(value: String, label: String, p: Palette, changed: (String) -> Unit) {
    Prose(label, p.muted)
    BasicTextField(value, changed, singleLine = true,
        textStyle = TextStyle(color = p.ink, fontFamily = MonoFace, fontSize = 16.sp),
        cursorBrush = SolidColor(p.accent),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).border(Dim.hairline, p.line)
            .padding(12.dp).semantics { contentDescription = label })
}
