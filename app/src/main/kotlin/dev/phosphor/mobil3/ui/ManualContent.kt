package dev.phosphor.mobil3.ui

import java.util.Collections
import java.util.Locale

/** One chapter. Developer chapters appear only in the developer view (REC08/09). */
internal data class ManualChapter(
    val id: String,
    val title: String,
    val text: String,
    val response: String,
    val developer: Boolean = false,
)

/** Offline help only. Search text is never a command or a network request. */
internal object ManualContent {
    const val QUERY_LIMIT = 256
    private fun everyday(id: String, title: String, text: String, response: String) = ManualChapter(id, title, text, response)
    private fun developer(id: String, title: String, text: String, response: String) = ManualChapter(id, title, text, response, true)

    val chapters: List<ManualChapter> = Collections.unmodifiableList(listOf(
        // ── Everyday: using and reading the scope, in the words on screen. ──
        everyday("start", "the first minute",
            "Phosphor draws sound as light, like an oscilloscope tube. The beam owns the screen.\n\n" +
                "Tap the glass to show the keys. Play and pause sit on the left. MODE chooses how sound is drawn. " +
                "SRC chooses where the sound comes from. ⋯ holds LIGHT, LOOK, PiP, SETTINGS and the grid.\n\n" +
                "Swipe up on the keys to open settings. Pull any sheet down to close it, or use ✕ or Back. " +
                "Nothing plays until you choose a sound in SRC.",
            "The turtle has booted. The turtle has not invented a signal."),
        everyday("sources", "SRC · where the sound comes from",
            "LIBRARY: open file plays one file. open folder plays a folder as a queue; tap a title to jump to it.\n\n" +
                "OTHER APPS: everything playing draws the sound other apps play, such as Spotify.\n\n" +
                "MICROPHONE: draws what the phone hears.\n\n" +
                "REMOTE: draws a desktop's sound over your own Tailscale network.\n\n" +
                "The active source is marked and says stop. Tap it to stop.",
            "Localhost? More like localmost. The music stayed in the room."),
        everyday("everything-playing", "everything playing · other apps",
            "Tap everything playing in SRC. The first time, Phosphor explains what Android will ask. " +
                "Android calls it sharing your screen; Phosphor takes only the sound. Choose continue, then allow it.\n\n" +
                "include mic adds the microphone to the picture, with playback level and mic level. " +
                "These change the drawing only, never the speaker volume.\n\n" +
                "track names · allow lets Phosphor show titles and cover art. Sound works without it.\n\n" +
                "Some apps block capture, so their sound arrives as silence.",
            "Android said share screen. The scope brought ears, not a camera crew."),
        everyday("microphone", "microphone",
            "Each input has its own row: built-in, built-in · second, USB, headset or Bluetooth. " +
                "With only one input the row just says microphone.\n\n" +
                "Tap an input to start it; Android asks for permission the first time. The running input says stop; " +
                "tap it to stop. Tap another input while one runs and the microphone moves to it.\n\n" +
                "A Bluetooth mic can lower playback quality while it runs. Phosphor asks first. " +
                "Nothing is recorded or saved.",
            "The microphone is not judging your humming. It is counting samples."),
        everyday("remote", "REMOTE · your desktop",
            "Run phosphor-relay on a desktop, join both devices to your Tailscale tailnet, then tap add relay " +
                "and enter its address. Tap the relay to connect.\n\n" +
                "When connected: music plays the desktop's sound here; desktop visualizer draws the desktop's own " +
                "picture; desktop sources and desktop library pick what it plays. disconnect ends it.\n\n" +
                "If the sound stutters, set settings › relay latency to safe.",
            "The skeleton checked the host. It did not dial a random IP for the plot."),
        everyday("reading", "reading the scope",
            "In the XY faces the left channel moves the beam sideways and the right channel moves it up and down.\n\n" +
                "Mono sound, the same on both sides, draws a straight diagonal line. Wide stereo opens it into a cloud. " +
                "Two tones a quarter turn apart draw a circle; simple ratios between tones draw knots and flowers. " +
                "This is how oscilloscope music is written.\n\n" +
                "waveform and ring draw sound over time. The spectrum faces draw how much of each pitch is present.",
            "Same samples. Different hat. The hat has Fourier opinions."),
        everyday("mode", "MODE · faces and geometry",
            "Tap a face to try it live; the sheet stays open. XY faces: scope art, goniometer, swirl, dots. " +
                "3D: attractor, time helix. TIME: waveform, ring. SPECTRUM: spectrum, radial, tunnel.\n\n" +
                "random (⚄) picks a new face for each track. skip on ⚄ keeps chosen faces out of the roll; " +
                "at least two stay in play.\n\n" +
                "GEOMETRY bends the drawing: off, kaleido, spin, tunnel or pulse, with an amount.",
            "Rolling the die is allowed. Rolling a face you banned is not."),
        everyday("size", "closer and farther",
            "auto size keeps sound comfortably in view. Quiet sound grows back gently; loud sound settles quickly.\n\n" +
                "Pinch, or drag one finger up or down on the glass, to move closer or farther. A thin rail on the edge " +
                "shows where you are, with the size as ×. With auto size on you move around its choice; " +
                "with it off you set the size yourself.\n\n" +
                "settings › size does the same. reset appears when you have moved. view lock stops pinch and drag zoom.",
            "Turning zero up to eleven still makes zero. The umbrella knows math."),
        everyday("light", "LIGHT · beam colour",
            "COLORS: tap a swatch to wear it.\n\n" +
                "SAVED keeps up to six colours. + saves the colour the beam shows now. Tap a saved colour to wear it. " +
                "Long-press it to edit its hue, saturation and brightness, or to delete it.\n\n" +
                "CYCLE appears with two saved colours or auto color. timer changes colour every few seconds; " +
                "each track changes it when the track changes. While a cycle runs, tap saved colours to add or remove " +
                "them; at least two stay. order can be saved or shuffled.\n\n" +
                "RANDOM: ⚄ roll wears a surprise colour now; + keeps it. auto color keeps inventing colours.\n\n" +
                "Fast colour changes can trigger seizures. Below one second Phosphor keeps safe timing until you choose allow faster.",
            "Six colours entered the bank. None were rounded down to three."),
        everyday("look", "LOOK · the room around the beam",
            "Each tile shows a look in its own colours. Tap one to wear it. Glass, AMOLED, Dark and Light come first; " +
                "CLASSIC holds their older versions.\n\n" +
                "STYLE changes one thing at a time: feel (carved, engraved, bench, glass), motion (eased, cut, steps, " +
                "spring), corners (sharp, soft, round) and labels (plain or part numbers).\n\n" +
                "A look changes the keys and sheets, never the beam.",
            "The room changed its jacket. The beam kept its job."),
        everyday("settings", "settings",
            "One scroll, top to bottom:\n\n" +
                "SOUND & VIEW: auto size, size, view lock, focus, beam, glow, vary beam per track, vary glow per track.\n\n" +
                "SCREEN: frame rate, fps line, keep screen bright, HDR, fullscreen, keys always visible, double tap to play, " +
                "when paused, lock scope rotation, lock key placement.\n\n" +
                "PiP & BACKGROUND: auto PiP, floating HUD, keep playing in background.\n\n" +
                "SOURCES: on launch, ask for permission at launch, relay latency.\n\n" +
                "SETUPS and ABOUT follow.",
            "Nested drawers, not a boss fight. This one is a single drawer now."),
        everyday("beam", "focus, beam and glow",
            "focus sharpens or softens the beam. beam sets how bright it burns. glow sets how long the phosphor " +
                "keeps shining after the beam moves on.\n\n" +
                "vary beam per track and vary glow per track roll a new value inside a range each time the track changes.\n\n" +
                "If the trace smears, lower glow.",
            "More glow is a preference, not a packet-loss repair strategy."),
        everyday("setups", "SETUPS · whole instruments",
            "A setup remembers the beam: mode, geometry, colours, focus, beam, glow and size. It leaves sources, " +
                "the look and permissions alone.\n\n" +
                "save current setup names what you have now. Tap a setup, then apply. update, rename, copy, export " +
                "and delete act on the chosen one. The starters are a place to begin.\n\n" +
                "settings file exports or imports all settings. setups file exports or imports setups; an import " +
                "asks what to do with each one and never applies it by itself.",
            "A setup remembers your bench, not the last thing your microphone heard."),
        everyday("gestures", "gestures",
            "Tap the glass: show or hide the keys. Double tap: play or pause (settings › double tap to play).\n\n" +
                "Pinch or one-finger drag: closer or farther. Two fingers sideways: next or previous face. " +
                "Two fingers up or down: more or less glow.\n\n" +
                "In the 3D faces one finger turns the view and a pinch moves through it. While the picture is held, " +
                "pan and pinch look around the held image.\n\n" +
                "Swipe up on the keys: settings. Pull a sheet down, even after scrolling, to close it. " +
                "A scroll or a pull never presses a control.",
            "Yeet prevention is enabled. The settings deserve a proper goodbye."),
        everyday("hold", "pause and hold",
            "For files and folders, play and pause stop the music.\n\n" +
                "For live sound (other apps, microphone) the play key holds the picture instead: the last image stays " +
                "on the glass while sound keeps arriving. Press it again to return to live.\n\n" +
                "settings › when paused chooses hold frame or black.",
            "Freeze frame. Record scratch optional, because the record kept spinning."),
        everyday("pip", "PiP and the floating HUD",
            "⋯ › PiP shrinks Phosphor into a small window. auto PiP does that when you leave the app; turn it off " +
                "if you prefer.\n\n" +
                "floating HUD is a small scope over other apps, with its own keys, a move handle and a resize corner. " +
                "HUD background can be solid or clear. Android asks once for permission to draw over apps.\n\n" +
                "keep playing in background keeps a source running after you swipe Phosphor away.",
            "One source, one surface owner. The tiny window did not clone the orchestra."),
        everyday("inside", "inside the tube",
            "Sound arrives as 48,000 stereo samples a second. A small engine written in Rust draws them with the " +
                "GPU, up to 120 times a second on this panel.\n\n" +
                "Each frame the beam deposits light along the path of the sound, and the old light fades a little: " +
                "that fading is glow, the same persistence a real phosphor screen has.\n\n" +
                "auto size watches how loud the sound is and eases the size so the shape stays in view.",
            "Rust, a GPU and a very patient turtle walk into a tube."),
        everyday("privacy", "privacy",
            "No account, no ads, no tracking. Sound is processed in memory and is never recorded or uploaded. " +
                "A relay connects only to an address you saved, inside your own Tailscale network.\n\n" +
                "Microphone, other-app sound, track names and drawing over apps are separate Android permissions. " +
                "Saying no is fine; Phosphor does not ask in a loop.",
            "Under the umbrella, everything is kind. Including a perfectly valid no."),
        everyday("manual", "this manual",
            "Search with any words from a control or a problem, such as glow or no sound. " +
                "Tap a chapter to read it. back and system Back return one step: to the previous chapter, then to the " +
                "list, then out of the manual. previous and next walk through every chapter.\n\n" +
                "The little tube at the top keeps a secret. Five taps.",
            "The terminal accepts questions. It has politely misplaced its shell interpreter."),

        // ── Developer: measurement, recovery and internals (after 7 taps on version). ──
        developer("signal", "signal check",
            "settings › DEVELOPER › signal check reads the selected source, its owner, requested and observed " +
                "format, and fresh sample windows. Reading starts nothing and grants nothing. Unavailable is not zero; " +
                "stale measurements do not prove a running reader. For missing access use OPEN SOURCES and that " +
                "source's own grant, retry or picker. For measured silence check the source app and Android's " +
                "microphone privacy switch before touching size.",
            "The detective found no samples. The detective did not immediately arrest DRM."),
        developer("rails", "peaks, rails and age",
            "A PCM full-scale rail means a sample reached its encoding's endpoint; it does not prove analog clipping. " +
                "Duplicated mono stays mono provenance even in a two-channel ring. Hidden diagnostics never invent fresh timestamps.",
            "A rail is a sample fact, not a certificate that the singer was too loud."),
        developer("performance", "frame rate, beam rate, status band and grid data",
            "frame rate: 60, 90, 120 (panel max) or max. Max removes the software limit, not the panel's 120 Hz, " +
                "and costs heat. fps line shows fps and segments on, with keys, or off.\n\n" +
                "beam rate (DEVELOPER): 120 · 48 kHz, 240 · 96 kHz or 480 · 192 kHz reconstruction points per audio " +
                "window, not panel refresh. status band on, auto or off carries the numeric readouts. grid data " +
                "shows raw L and R peaks and dBFS, independent of display size.",
            "Four hundred eighty on the beam dial did not summon a four-hundred-eighty-hertz panel."),
        developer("inspect", "pause display only and inspection",
            "pause display only freezes the picture without pausing music; return display to live resumes the " +
                "current timeline rather than replaying a backlog. While held, pan and pinch inspect without " +
                "changing live size, geometry or source; reset inspection undoes them. Returning live waits for an " +
                "application presentation acknowledgement, not a physical scanout guarantee.",
            "Enhance. Enhance. Still the same captured phosphor, not new evidence."),
        developer("mix", "input mix internals",
            "include mic runs one bounded stereo timeline with clipping protection. Signal check reports both " +
                "inputs separately, with estimated timing when capture timestamps are missing. If the microphone " +
                "fails, healthy playback continues. A missing accessory never silently becomes the built-in mic; " +
                "Phosphor releases only its own Bluetooth route and mode requests.",
            "Two readers in a trench coat are not a synchronized mixer."),
        developer("hdr", "HDR and screen brightness",
            "HDR requests an HDR surface and falls back to SDR truthfully; the developer view shows the active " +
                "mode and the reason. keep screen bright requests full window brightness only while the focused full " +
                "app owns the foreground; PiP, HUD, background and focus loss release it. Global and automatic " +
                "brightness are never changed. A requested override is not measured luminance.",
            "We added the letters H D R. The panel asked for actual floating-point receipts."),
        developer("setup-recovery", "setup recovery",
            "An apply needs its matching receipt; a late or cancelled one cannot overwrite a newer edit. " +
                "cancel apply stops a pending apply. undo apply is one level. retry save retries durability; " +
                "restore appears when the shown setup must be restored. keep safe rejects a pending fast colour cycle. " +
                "sources explains a remote-owned refusal. Import decisions are add, skip, replace or as copy.",
            "The skeleton accepts receipts. Screenshots of confidence are not receipts."),
        developer("appearance-values", "appearance values",
            "DEVELOPER › APPEARANCE VALUES edits the raw look: RGB24 colour fields as RRGGBB, line and lineStrong as " +
                "ARGB32 AARRGGBB, duration 0.25 to 2.0, density 0.85 to 1.25, corner radius 0 to 64, panel opacity " +
                "0.2 to 1.0. PREVIEW is temporary, APPLY persists, SAVE also names a record. A readability warning " +
                "reports contrast without rewriting colours. Recovery can replace an unreadable document with AMOLED " +
                "or retry the authoritative save.",
            "A draft is a dressing room. APPLY is when the jacket actually leaves the shop."),
        developer("archives", "settings files",
            "A settings file is a portable archive of allowlisted instrument and appearance state. It carries no " +
                "media paths, relay hosts, consent tokens or root grants, starts nothing and shows no HUD. Imported " +
                "fast colour timing stays safe until confirmed; imported setups stay inert until applied.",
            "The suitcase holds the knobs. It leaves your house keys and permission tokens at home."),
        developer("root", "root capture · a future beam",
            "Root capture is deferred from this release. The bestiary's preview button requests nothing and " +
                "starts nothing; earlier root settings stay inactive. Research showed 16 kHz mono feasibility, and " +
                "duplicating mono does not recover stereo. Original stereo, SoundCloud and added latency are not yet accepted.",
            "The turtle saved a door for a future beam. It has not secretly started a root orchestra."),
        developer("startup", "startup",
            "on launch starts nothing, the microphone or everything playing once per fresh launch, never on resume, " +
                "rotation or permission return. ask for permission at launch lets that start use the real Android " +
                "flow once. Imported startup choices wait for local confirmation.",
            "Once per launch. Not once per blink. The permission dialog can rest."),
    ))

    fun chapter(id: String): ManualChapter = chapters.firstOrNull { it.id == id }
        ?: throw IllegalArgumentException("Unknown manual chapter")

    /** Chapters shown in this view: everyday always, developer only after the unlock. */
    fun visible(developer: Boolean): List<ManualChapter> = chapters.filter { developer || !it.developer }

    fun search(query: String, developer: Boolean = false): List<ManualChapter> {
        val words = query.take(QUERY_LIMIT).lowercase(Locale.ROOT).trim().split(Regex("\\s+")).filter(String::isNotEmpty)
        return visible(developer).filter { chapter ->
            val text = "${chapter.id} ${chapter.title} ${chapter.text} ${chapter.response}".lowercase(Locale.ROOT)
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
