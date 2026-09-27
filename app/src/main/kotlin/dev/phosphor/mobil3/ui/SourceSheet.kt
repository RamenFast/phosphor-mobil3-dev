package dev.phosphor.mobil3.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import dev.phosphor.mobil3.MicrophoneChoice

/** Human microphone names: kind words, and an ordinal only when a kind repeats. No ids. */
internal object MicNames {
    fun kind(type: Int): String = when (type) {
        15 -> "built-in"
        3 -> "headset"
        22 -> "USB headset"
        11, 12 -> "USB"
        7, 26 -> "Bluetooth"
        else -> "external"
    }

    private val ordinals = listOf("", "second", "third")

    fun labels(inputs: List<MicrophoneChoice>): Map<Int, String> {
        val seen = mutableMapOf<String, Int>()
        val total = inputs.groupingBy { kind(it.type) }.eachCount()
        return inputs.associate { input ->
            val k = kind(input.type)
            val n = seen.merge(k, 1, Int::plus)!!
            input.id to when {
                (total[k] ?: 1) == 1 || n == 1 -> k
                n - 1 <= ordinals.lastIndex -> "$k · ${ordinals[n - 1]}"
                else -> "$k · $n"
            }
        }
    }
}

/** A source you can pick: glyph · label · trailing state word. Active = accent + mark. */
@Composable
internal fun SourceRow(label: String, p: Palette, glyph: SettingsGlyph? = null, active: Boolean = false,
    trailing: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .semantics { selected = active; if (active) stateDescription = "on" }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        glyph?.let {
            SettingsGlyphIcon(it, p, 20.dp)
            Spacer(Modifier.width(12.dp))
        }
        Mono(label, if (active) p.accent else p.ink, Type.label, Modifier.weight(1f))
        trailing?.let {
            Spacer(Modifier.width(12.dp))
            Mono(it, if (active) p.accent else p.ink2, Type.value)
        }
        if (active) {
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size10().background(p.accent))
        }
    }
    RowDivider(p)
}

private fun Modifier.size10() = this.then(Modifier.width(10.dp).height(10.dp))

// Source selection, capture consent, microphone and PC relay (REDESIGN §2). No prose.
@Composable
fun SourceSheet(
    state: ScopeUiState,
    p: Palette,
    reduced: Boolean,
    actions: SheetActions,
    onDismiss: () -> Unit,
) {
    val p = p.sheetText()
    var consentCard by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    CompositionLocalProvider(LocalSettingsControlAccess provides true) {
    SheetHost(p, "SRC", reduced, onDismiss, glyph = SettingsGlyph.Signal) {
      Column(Modifier.verticalScroll(scroll, overscrollEffect = null)) {
        if (consentCard) {
            // Consent logic unchanged: one plain explanation, the source link, two keys.
            GroupHeading("EVERYTHING PLAYING", p, first = true)
            Prose(
                "Android will ask to share your screen. That is its wording for media capture. " +
                    "Phosphor takes only the sound other apps play and turns it into light.",
                p.ink, size = Type.hint, modifier = Modifier.padding(bottom = 8.dp),
            )
            Prose(
                "Nothing is recorded and nothing leaves your phone. The source is public. " +
                    "Tap the active row to stop any time.",
                p.ink2, size = Type.hint, modifier = Modifier.padding(bottom = 8.dp),
            )
            SettingAction("read the source", p, value = "open") {
                actions.openLink("https://github.com/RamenFast/phosphor-mobil3")
            }
            KeyRow {
                SheetKey("continue", p, active = true) {
                    consentCard = false
                    actions.startCapture()
                    onDismiss()
                }
                SheetKey("not now", p) { consentCard = false }
            }
        } else {
        GroupHeading("LIBRARY", p, first = true)
        SourceRow("open file", p, SettingsGlyph.File,
            active = state.sourceLabel == "deck" && state.queueTitles.size <= 1) { actions.openFile(); onDismiss() }
        SourceRow("open folder", p, SettingsGlyph.Folder,
            active = state.sourceLabel == "deck" && state.queueTitles.size > 1) { actions.openFolder(); onDismiss() }
        if (state.queueTitles.isNotEmpty()) {
            LazyColumn(Modifier.heightIn(max = 260.dp)) {
                itemsIndexed(state.queueTitles) { i, title ->
                    val current = i == state.queueIndex
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .semantics { selected = current }
                            .clickable { actions.jumpToQueue(i) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Mono("%02d".format(i + 1), if (current) p.accent else p.ink2, Type.value, Modifier.width(36.dp))
                        Mono(title, if (current) p.accent else p.ink, Type.label, Modifier.weight(1f), maxLines = 2)
                    }
                }
            }
            RowDivider(p)
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            GroupHeading("OTHER APPS", p, modifier = Modifier.weight(1f))
            // Compatibility marks, not buttons: the apps known to feed the beam.
            // SoundCloud is deliberately NOT here (ALLOW_AUDIO_CAPTURE_BY_NONE: always silence).
            Row(Modifier.padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(SettingsGlyph.Spotify, SettingsGlyph.AppleMusic, SettingsGlyph.Vlc, SettingsGlyph.Mpv).forEach {
                    SettingsGlyphIcon(it, p, 14.dp)
                    Spacer(Modifier.width(6.dp))
                }
            }
        }
        val capturing = state.live && state.sourceLabel == "capture"
        SourceRow("everything playing", p, SettingsGlyph.Capture, active = capturing,
            trailing = if (capturing) "stop" else null) {
            when {
                capturing -> actions.stopLive()
                actions.captureConsentNeeded() -> consentCard = true
                else -> { actions.startCapture(); onDismiss() }
            }
        }
        if (state.captureStatus.isNotBlank()) {
            SettingNote(state.captureStatus + if (state.captureFix.isNotBlank()) " · ${state.captureFix}" else "", p)
        }
        SettingToggle("include mic", state.includeMicrophone, p) { actions.setIncludeMicrophone(it) }
        if (state.includeMicrophone) {
            SettingSlider("playback level", state.playbackMixLevel, 0f, 1f, p, { "%.0f %%".format(it * 100) }) {
                actions.setMicrophoneMixLevel(false, it)
            }
            SettingSlider("mic level", state.microphoneMixLevel, 0f, 1f, p, { "%.0f %%".format(it * 100) }) {
                actions.setMicrophoneMixLevel(true, it)
            }
        }
        if (!state.captureMetadataAccess) {
            SettingAction("track names", p, value = "allow") { actions.openCaptureMetadataSettings() }
        }
        if (dev.phosphor.mobil3.RootCapturePolicy.PRODUCT_AVAILABLE && (state.rootCaptureEnabled || state.captureRoot)) {
            SourceRow("standard capture", p, active = state.live && state.sourceLabel == "capture" && !state.captureRoot) {
                actions.startStandardCapture(); onDismiss()
            }
            if (!state.live) KeyRow {
                if (state.rootCaptureEnabled) SheetKey("retry root", p) { actions.startCapture() }
                SheetKey("root manager", p) { actions.openRootManager() }
            }
        }

        GroupHeading("MICROPHONE", p)
        MicrophoneRows(state, p, actions)

        GroupHeading("REMOTE", p)
        RemoteFlow(state, p, actions, onDismiss)
        }
      }
    }
    }
}

/**
 * One row per input. The active row stops. The chosen idle row starts. Another row
 * chooses that input (and moves a running microphone to it).
 */
@Composable
private fun MicrophoneRows(state: ScopeUiState, p: Palette, actions: SheetActions) {
    val names = remember(state.microphoneInputs) { MicNames.labels(state.microphoneInputs) }
    val micLive = state.sourceLabel == "mic" && state.live
    if (state.microphoneInputs.isEmpty()) SettingNote("no microphone found", p)
    state.microphoneInputs.forEach { input ->
        val chosen = state.selectedMicrophone == input.id
        val active = chosen && micLive
        SourceRow(
            if (state.microphoneInputs.size == 1) "microphone" else names[input.id] ?: "microphone",
            p, SettingsGlyph.Mic, active = active,
            trailing = when { active -> "stop"; chosen -> "start"; else -> null },
        ) {
            when {
                active -> actions.stopMicrophone()
                chosen -> actions.startMic()
                else -> actions.chooseMicrophone(input.id)
            }
        }
    }
    val idle = state.microphoneStatus.isBlank() || state.microphoneStatus == "Microphone off"
    if (!micLive && !idle && !state.micBluetoothExplain) {
        SettingNote(state.microphoneStatus, p)
        KeyRow { SheetKey("retry", p) { actions.retryMicrophone() } }
    }
    if (state.micBluetoothExplain) {
        SettingNote("a Bluetooth mic can lower playback quality until it stops", p)
        KeyRow {
            SheetKey("continue", p, active = true) { actions.confirmMicrophoneBluetooth(true) }
            SheetKey("not now", p) { actions.confirmMicrophoneBluetooth(false) }
        }
    }
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
    if (hosts.isEmpty() && !editing) SettingNote(RemoteEmptyStateText, p)
    // The last failure, with the engine's own fix. Shown above the host rows because it
    // explains why the row you just tapped did not work.
    if (state.remoteFailure.isNotBlank()) SettingNote(state.remoteFailure, p)
    hosts.forEach { (label, hostPort) ->
        val connected = state.remote && state.sourceLabel.contains(label)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                SourceRow(label, p, SettingsGlyph.Remote, active = connected) {
                    if (!connected) {
                        clearBrowse()
                        showSources = false
                        sourcesJson = ""
                        selectedPeer = hostPort
                        actions.startRemoteHost(label, hostPort.first, hostPort.second)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            SheetKey("edit", p, description = "edit $label") {
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
        SettingAction("add relay", p, value = "+") {
            refusal = null
            editTarget = null
            editing = true
        }
    }

    if (state.remote) {
        SettingToggle("music", state.remoteAudio, p) { actions.setRemoteStreams(it, state.remoteGeometry) }
        SettingToggle("desktop visualizer", state.remoteGeometry, p) { actions.setRemoteStreams(state.remoteAudio, it) }
        SettingAction("desktop sources", p, value = if (showSources) "hide" else "show") {
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
                    SourceRow(src.optString("label", id), p, active = id == selected) {
                        dev.phosphor.mobil3.PhosphorNative.remoteChooseSource(id)
                    }
                }
            }
        }
        SettingAction("desktop library", p, value = if (browsing) "hide" else "browse") {
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
                KeyRow {
                    roots.forEach { (id, label) ->
                        SheetKey(label, p, active = id == browseRequest?.root) {
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
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Mono("library › " + path.ifBlank { "top" }, p.ink2, Type.value, Modifier.weight(1f), maxLines = 2)
                    Spacer(Modifier.width(8.dp))
                    SheetKey("play folder", p) { folder.playFolder() }
                }
                if (path.isNotBlank()) SourceRow("‹ up", p) { folder.up() }
                val dirs = l.optJSONArray("dirs")
                if (dirs != null) for (i in 0 until dirs.length()) {
                    val d = dirs.getString(i)
                    SourceRow("$d /", p, SettingsGlyph.Folder) { folder.directory(d) }
                }
                val files = l.optJSONArray("files")
                if (files != null) for (i in 0 until files.length()) {
                    val f = files.getJSONObject(i)
                    val name = f.optString("name")
                    SourceRow(name, p, SettingsGlyph.File) { folder.file(name) }
                }
            }
        }
        SettingAction("disconnect", p, value = "") {
            clearBrowse()
            showSources = false
            sourcesJson = ""
            actions.disconnectRemote()
            onDismiss()
        }
    }
}
