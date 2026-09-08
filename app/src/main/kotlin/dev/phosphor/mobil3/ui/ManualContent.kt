package dev.phosphor.mobil3.ui

import java.util.Collections
import java.util.Locale

internal data class ManualChapter(
    val id: String,
    val title: String,
    val availability: String,
    val text: String,
    val response: String,
)

/** Offline help only. Search text is never a command or a network request. */
internal object ManualContent {
    const val QUERY_LIMIT = 256
    val chapters: List<ManualChapter> = Collections.unmodifiableList(listOf(
        ManualChapter("start", "START HERE · the beam owns the screen", "Guide",
            "Tap the glass to reveal the console. SRC chooses the signal, MODE chooses its representation, and LIGHT chooses its phosphor color. Open Settings from the play bar. No configured source means no automatic recording or relay connection. A dark screen can be correct when there is no input.",
            "The turtle has booted. The turtle has not invented a signal."),
        ManualChapter("local", "LOCAL · files and folders", "Available",
            "Choose a file or a folder through Android's picker. A folder becomes a queue. Canceling the picker must leave the current source alone. Local playback feeds the scope from the playback path. Use transport pause to stop the music, not display HOLD. If a saved file is missing, choose it again rather than treating silence as corruption.",
            "Localhost? More like localmost. The music stayed in the room."),
        ManualChapter("relay", "REMOTE · your desktop relay", "Available",
            "Select an explicitly saved Tailscale host. Remote audio and remote geometry are different modes. Geometry belongs to the remote instrument, so local beam controls may be unavailable. SOURCE CONTROLS explains that owner. A disconnected host is not a silent microphone. Reconnect explicitly and verify the source identity in Signal Check.",
            "The skeleton checked the host. It did not dial a random IP for the plot."),
        ManualChapter("capture", "EVERYTHING PLAYING · standard capture", "Available with Android consent",
            "Standard playback capture needs recording permission and a fresh MediaProjection consent. Android may call this screen sharing. Phosphor uses the audio path, not a video recording. The system recording indicator remains meaningful. Start playback in the source app, then inspect actual samples. Silence alone cannot identify an opt-out, DRM, routing failure or a stalled reader.",
            "Android said share screen. The scope brought ears, not a camera crew."),
        ManualChapter("root", "ROOT · authorization is not stereo proof", "Experimental",
            "The hidden bestiary contains the opt-in root entry. It checks the supported existing KernelSU authorization and inherited profile. Normal app startup does not request root when disabled. The fixed helper has demonstrated 16 kHz mono feasibility. Duplicating mono into two channels does not recover stereo. Original stereo, SoundCloud capture and added acoustic latency are not yet accepted. Standard capture remains the explicit alternative.",
            "Root acquired. Omniscience not included. There is still a beam worth building."),
        ManualChapter("mic", "MIC · recording and privacy", "Available for the current input path",
            "Microphone input needs Android recording permission. The selected input and an actually routed device are not the same fact. Signal Check shows measured format or says unavailable. Privacy mute, a missing route and measured silence need different recovery. Stop the source to retire its reader. Activity-owned mic transfer to the floating HUD is currently refused rather than silently replaced.",
            "The microphone is not judging your humming. It is counting samples."),
        ManualChapter("mix", "INPUT MIX · accessories and Bluetooth", "Planned",
            "Accessory selection and optional playback-plus-mic mixing are still pending. Their intended mixer affects visualization, never speaker output. Bluetooth communication routes may reduce bandwidth or change output. A device name alone will not prove the actual route. Until that work is accepted, do not infer a working two-input mix from two visible labels.",
            "Two readers in a trench coat are not a synchronized mixer."),
        ManualChapter("gesture", "GESTURES · deliberate, not surprising", "Available",
            "Pinch adjusts the live view when its current owner permits it. Settings opening keeps its existing gesture. Ordinary child scrolling, sliders and short top-edge motion should not dismiss it. A deliberate slow pull or qualified downward flick can close Settings. Reverse to repay drag. If delayed input interrupts dismissal, use Close or Back and reopen. Other sheet policies remain separate.",
            "Yeet prevention is enabled. The settings deserve a proper goodbye."),
        ManualChapter("sections", "SETTINGS · six expandable groups", "Available",
            "Signal, Beam, Display, Motion, Appearance and About expand independently. A header changes presentation only, not the setting it summarizes. Scroll and expansion state belong to the screen. A collapsed control should not remain in accessibility focus. Keyboard users can focus controls and use arrows or Home/End on supported ranges. Close and Back remain explicit exits.",
            "Nested drawers, not a boss fight. Open the one with your screwdriver."),
        ManualChapter("mode", "MODE · geometry and representation", "Available",
            "Choose a face for XY figures, the 3D view, waveforms or spectra. A different face changes the representation, not the source. Track-randomized mode respects its ban list. Geometry effects act after the face. If remote geometry owns the frame, use the remote instrument or switch source explicitly before applying local setups.",
            "Same samples. Different hat. The hat has Fourier opinions."),
        ManualChapter("gain", "GAIN · measured versus authored", "Available",
            "Gain scales the visual signal. Auto-gain is a live owner, not a saved measurement. Instrument presets save authored local gain and the auto-gain setting, never a sampled peer value. VIEW LOCK and current source ownership can block gestures. Check those owners before increasing gain to compensate for missing input.",
            "Turning zero up to eleven still makes zero. The umbrella knows math."),
        ManualChapter("beam", "FOCUS, BEAM and GLOW", "Available",
            "Focus changes the beam's shape. Beam energy changes emission, while glow controls persistence. Their randomized ranges are authored settings, not audio amplitude or window brightness. A held image keeps its captured appearance. Changing a control while holding must not pretend the frozen pixels were newly rendered from live input.",
            "More glow is a preference, not a packet-loss repair strategy."),
        ManualChapter("light", "LIGHT · six saved colors", "Available",
            "Keep up to six RGB slots. Select the slots that participate in cycling. Editing an unselected slot should not step a selected TRACK cycle. Delete changes membership without silently changing another saved color. Empty selection uses the documented fallback. A single selected color stays that color. Appearance colors are separate from these beam colors.",
            "Six colors entered the bank. None were rounded down to three."),
        ManualChapter("random", "RANDOM · color, interval and shuffle", "Available",
            "Generated random color, random timer interval and shuffle are independent controls. TIMER advances by duration; TRACK advances on track identity. A redundant TRACK apply is not a new track. The rapid-cycle guard evaluates the whole candidate, including imported settings. A temporary manual roll is not a rewrite of your saved palette.",
            "RNGesus can pick a color. The ownership ledger still picks the clock."),
        ManualChapter("presets", "INSTRUMENT PRESETS · complete beam setups", "Available",
            "Open the instrument browser from Settings or LIGHT. Save, duplicate, rename, delete and recall named setups. Import is inert until an explicit apply. Presets include beam geometry, authored gain and the full six-color policy. They exclude sources, appearance, root grants, HDR and window brightness. Modified status compares authored settings, not the dancing signal. One-level undo is a separate explicit action.",
            "A preset remembers your bench, not the last thing your microphone heard."),
        ManualChapter("preset-recovery", "PRESET RECOVERY · current is not always saved", "Available",
            "A native apply needs its matching receipt. A late or canceled receipt cannot overwrite a newer edit. If saving fails, the active setup and saved setup may differ. Read the displayed recovery state. RETRY SAVE CURRENT is an explicit durability action, not a transport restart. A failed rollback blocks edits until authoritative state can be saved. SOURCE CONTROLS explains remote-owned refusal.",
            "The skeleton accepts receipts. Screenshots of confidence are not receipts."),
        ManualChapter("signal", "SIGNAL CHECK · ask why the beam is dark", "Available",
            "Open Signal Check on demand. Selected source, current owner, requested format and observed format have different meanings. A fresh sample window may show signal or measured silence. Stale measurements do not prove a running reader. Unavailable is not zero. Scope-consumed peaks and source-input peaks are different observations, so they must retain their own provenance.",
            "The detective found no samples. The detective did not immediately arrest DRM."),
        ManualChapter("rails", "SIGNAL CHECK · peaks, rails and age", "Available",
            "A PCM full-scale rail means a digital sample reached that encoding's endpoint. It does not prove analog microphone clipping. Mono duplication remains mono provenance even when the ring has two channels. Root received PCM/progress observations are not a count of completed AudioRecord reads. Hidden diagnostics do not manufacture fresh timestamps when reopened.",
            "A rail is a sample fact, not a certificate that the singer was too loud."),
        ManualChapter("hold", "HOLD and BLACK · display versus transport", "Available with acceptance limits",
            "HOLD is the default pause presentation. It keeps the last committed image for inspection rather than letting it decay. BLACK is the alternative. For a controllable source, transport pause affects music; display-only HOLD does not. Live sources can keep arriving while the display is held. Returning LIVE waits for a matching application presentation acknowledgement. That is not a physical scanout or source-age guarantee.",
            "Freeze frame. Record scratch optional, because display pause did not touch the record."),
        ManualChapter("inspect", "INSPECTION · pan, zoom and return LIVE", "Available",
            "Inspect a held image without rewriting live gain, geometry or source settings. RESET INSPECTION restores its inspection transform. LIVE resumes the current timeline rather than intentionally replaying a backlog. Source replacement retires held ownership. Pending presentation, unknown capture-buffer age and actual display latency must not be collapsed into the word realtime.",
            "Enhance. Enhance. Still the same captured phosphor, not new evidence."),
        ManualChapter("hud", "FLOATING HUD and PiP", "Available with device acceptance pending",
            "HUD requires Android overlay access and has explicit show, move, resize and close controls. Solid black and transparent backgrounds are distinct. A window above another app does not become a second capture owner. PiP and HUD must hand off one surface. Unsupported mic transfer is refused. Theme Glass is not proof of a transparent HUD compositor.",
            "One source, one surface owner. The tiny window did not clone the orchestra."),
        ManualChapter("appearance", "APPEARANCE · looks are not beam presets", "In development",
            "The shared appearance model has Light, Dark, Glass and AMOLED, with legacy room snapshots preserved exactly. AMOLED uses a true-black base. Glass means translucent app chrome with readable text, not promised blur. Appearance previews, named saves and instrument presets have different ownership. Runtime editor integration is in development; do not treat pure archive tests as device appearance acceptance.",
            "The room changed its jacket. The beam kept its job."),
        ManualChapter("hdr", "HDR · real headroom, not a brighter slider", "Planned",
            "Genuine HDR scope output needs an actual suitable surface, linear rendering and compositor/display support. A brighter SDR beam is not HDR. Requested and active modes must be reported separately, including fallback reasons. HDR and transparent HUD need their own combined proof. This build has not accepted that path; ordinary SDR remains the working output.",
            "We added the letters H D R. The panel asked for actual floating-point receipts."),
        ManualChapter("brightness", "SCREEN BRIGHTNESS · a foreground window", "Planned",
            "The planned brightness pin is a window-only override while the full app is foreground, including paused/no-source use. It is separate from beam energy, auto-gain and HDR. Leaving the app or disabling it must restore the default window override. No global brightness setting is written. Panel, thermal, battery and accessibility dimming can still impose limits.",
            "The sun has entered the chat. The thermal governor may politely remove it."),
        ManualChapter("startup", "STARTUP · one intentional launch", "Planned",
            "The planned default-source coordinator distinguishes a fresh user launch from resume, rotation and permission return. No default means no automatic source or permission chain. Missing consent must use the real Android flow once, never a fake grant. Imported startup choices need local confirmation before activation. Private local/relay targets and permission tokens do not belong in portable archives.",
            "Once per launch. Not once per blink. The permission dialog can rest."),
        ManualChapter("privacy", "PRIVACY, PERMISSIONS and RECOVERY", "Available",
            "Phosphor has no account, ads, analytics or remote meme feed. Local audio is processed in memory, not recorded or uploaded. Relay audio is an explicitly chosen network source. Notification listener access supplies optional metadata and differs from notification permission. Overlay, microphone, projection and root grants are separate. Denial is not a reason to loop prompts or switch sources silently. Links below open only when you choose them.",
            "Under the umbrella, everything is kind. Including a perfectly valid no."),
    ))

    fun chapter(id: String): ManualChapter = chapters.firstOrNull { it.id == id }
        ?: throw IllegalArgumentException("Unknown manual chapter")

    fun search(query: String): List<ManualChapter> {
        val words = query.take(QUERY_LIMIT).lowercase(Locale.ROOT).trim().split(Regex("\\s+")).filter(String::isNotEmpty)
        return chapters.filter { chapter ->
            val text = "${chapter.id} ${chapter.title} ${chapter.availability} ${chapter.text} ${chapter.response}".lowercase(Locale.ROOT)
            words.all(text::contains)
        }
    }
}

internal data class ManualNavigation private constructor(
    val query: String,
    val chapterId: String?,
    val history: List<String>,
) {
    constructor() : this("", null, emptyList())
    fun search(value: String) = ManualNavigation(value.take(ManualContent.QUERY_LIMIT), null, emptyList())
    fun open(id: String): ManualNavigation {
        ManualContent.chapter(id)
        return copy(chapterId = id, history = Collections.unmodifiableList((history + (chapterId ?: "")).takeLast(32)))
    }
    fun index() = copy(chapterId = null, history = emptyList())
    fun back(): ManualNavigation = if (history.isEmpty()) index() else
        copy(chapterId = history.last().ifEmpty { null }, history = Collections.unmodifiableList(history.dropLast(1)))
}
