package dev.phosphor.mobil3

import java.util.Locale

internal object SignalPresentation {
    const val FRESH_MS = 1500L

    fun primary(selected: SignalKind, input: SignalInput?, now: Long, current: Boolean = true, consent: Boolean = false): String {
        if (!current || (input != null && input.kind != selected)) return "Observation unavailable · source changing"
        if (selected == SignalKind.NONE && input == null) return "No source"
        if (input?.life == SignalLife.FAILED) return input.reason.ifBlank { "Input unavailable" }
        if (input?.life == SignalLife.PERMISSION) return input.reason.ifBlank { "Permission needed" }
        if (consent) return "Waiting for consent"
        when (input?.life) {
            SignalLife.STOPPING -> return "Stopping"
            SignalLife.CLEANUP_UNCONFIRMED -> return "Cleanup unconfirmed"
            SignalLife.DISCONNECTED -> return "Disconnected"
            SignalLife.RECONNECTING -> return "Reconnecting"
            SignalLife.ENDED -> return "Input ended"
            else -> Unit
        }
        val w = input?.window?.takeIf { it.owner == input.session }
        val positiveAt = input?.receiptAt ?: w?.lastPositiveAt
        val positiveAge = signalAge(now, positiveAt)
        if (input?.life == SignalLife.STALLED) return "Reader / link stalled"
        if (positiveAt == null) {
            if (input == null) return "Waiting for current owner"
            val readAt = input.progressAt ?: w?.lastReadAt ?: return "Starting · waiting for input"
            val readAge = signalAge(now, readAt)
            return if (readAge != null && readAge <= FRESH_MS) "No samples observed · read loop progresses"
                else "Stale measurement · reader health unavailable"
        }
        if (positiveAge == null || positiveAge > FRESH_MS) {
            val progress = signalAge(now, input?.progressAt ?: w?.lastReadAt)
            return if (progress != null && progress <= FRESH_MS) "No recent input · read loop progresses" else "Stale measurement · reader health unavailable"
        }
        val measuredAge = signalAge(now, w?.measuredAt)
        if (w == null || w.channels.isEmpty() || measuredAge == null || measuredAge > FRESH_MS) return "Samples arriving · level unavailable"
        if (w.channels.all { it.peak == 0.0 }) return "Measured silence · samples arriving"
        if (w.channels.any { it.fullScale > 0 }) return "Signal flowing · full-scale samples"
        return "Signal flowing"
    }

    fun display(d: SignalDisplay): String = when {
        d.paused && d.black -> "Display black-on-pause"
        d.paused && d.held -> "Display held"
        d.paused -> "No held frame"
        d.pending -> "Waiting for new frame"
        else -> "Live presentation"
    }

    fun present(selected: SignalKind, input: SignalInput?, now: Long, d: SignalDisplay,
        current: Boolean = true, consent: Boolean = false, extra: List<Pair<String, String>> = emptyList()): SignalCheckView {
        val accepted = current && (input == null || input.kind == selected)
        val source = input?.takeIf { accepted }
        val descriptor = source?.descriptor?.takeIf { source.life == SignalLife.RUNNING || source.life == SignalLife.STARTING }
        val w = source?.window?.takeIf { it.owner == source.session }
        val fresh = signalAge(now, w?.measuredAt)?.let { it <= FRESH_MS } == true && source?.life == SignalLife.RUNNING
        val rows = mutableListOf(
            "Selected" to selected.label,
            "Actual owner" to (source?.let { if (it.owner > 0) "${it.kind.label} · owner ${it.owner} · session ${it.session}" else "Unavailable · selection has no installed native/recorder owner" } ?: "Unavailable · no matched current owner"),
            "Input health" to (source?.let { "${it.life.name.lowercase()}${if (it.reason.isBlank()) "" else " · ${it.reason}"}" } ?: "Unavailable"),
            "Contribution" to (source?.let { if (it.contributing) "Single installed input · not R09 mixing" else "Not currently admitted as an active input" } ?: "Unavailable"),
            (if (selected == SignalKind.MIC || selected == SignalKind.CAPTURE) "Recorder client format" else "Observed input") to
                (descriptor?.format?.label() ?: descriptor?.unavailable ?: "Unavailable · no current recorder observation"),
            "Normalized transport" to (source?.normalized ?: "Unavailable · no owner"),
        )
        if (selected == SignalKind.MIC) {
            rows += "Requested mic route" to (descriptor?.requestedRoute ?: "Unavailable · no current selected-route observation")
            rows += "Actual mic route" to (descriptor?.route ?: "Unavailable · current recorder has not reported a routed device")
            rows += "Route observation age" to age(now, descriptor?.observedAt)
        }
        if (selected == SignalKind.MIC || selected == SignalKind.CAPTURE) {
            rows += "Platform device format" to (descriptor?.deviceFormat?.label() ?: "Unavailable · Android has not reported the device format")
            rows += "Channel origin" to when {
                descriptor?.format?.channels == 1 -> "Mono client · duplicated to L/R by Phosphor"
                descriptor?.format?.channels == 2 && descriptor.deviceFormat?.channels == 1 ->
                    "Two-channel client from a mono device format · platform duplication possible"
                descriptor?.format?.channels == 2 -> "Two-channel client · physical L/R independence unproven"
                else -> "Unavailable · no current client format"
            }
            if (selected == SignalKind.MIC) rows += "Measured L/R" to if (fresh && w?.stereo != null) {
                val pair = w.stereo
                if (pair.nonSilentPairs == 0L) "Digital silence · no channel relationship established"
                else "${pair.identicalPairs}/${pair.nonSilentPairs} identical non-silent pairs · difference RMS ${number(pair.differenceRms)} · not physical independence proof"
            } else "Unavailable · no fresh paired window"
        }
        rows += "Input receipt" to (source?.receiptCount?.let { "$it ${source.receiptUnit}" }
            ?: w?.let { "${it.ingressFrames} input frames · ${it.reads} ${source.readUnit}" } ?: "Unavailable · no owner input counter")
        rows += (if (source?.kind == SignalKind.ROOT) "Last received PCM / progress observation" else "Last completed read / progress") to age(now, source?.progressAt ?: w?.lastReadAt)
        rows += (if (source?.receiptAgeIsUpperBound == true) "Positive receipt age upper bound" else "Last positive receipt") to
            age(now, source?.receiptAt ?: w?.lastPositiveAt)
        rows += "Input window" to (w?.let { "At most 500 ms · ${it.validFrames} valid frames · ${it.invalidSamples} invalid samples" } ?: "Unavailable")
        rows += "Raw input level" to if (fresh && !w!!.channels.isEmpty()) w.channels.mapIndexed { i, c ->
            val db = if (c.peak == 0.0) "−∞ (digital silence)" else String.format(Locale.ROOT, "%.1f", 20 * kotlin.math.log10(c.peak))
            "${if (w.channels.size == 1) "Mono" else if (i == 0) "L" else "R"} RMS ${number(c.rms)} · peak ${number(c.peak)} · $db dBFS · ${c.fullScale} full-scale samples"
        }.joinToString("\n") else "Unavailable · no fresh finite input window for this owner"
        rows += "Full scale" to if (fresh && w!!.channels.isNotEmpty()) {
            if (w.channels.any { it.fullScale > 0 }) "Warning: representation reached a full-scale boundary. Not proof of analog clipping."
            else "No full-scale samples in this measured window"
        } else "Unavailable · no clean/clipping claim without measured samples"
        rows += "Display" to display(d)
        if (accepted && source != null) rows += extra
        rows += "Recovery" to "Open SOURCES to use the existing source, grant, retry or route controls. Reading this view changes no source."
        return SignalCheckView(primary(selected, input, now, current, consent), rows)
    }

    private fun number(value: Double) = String.format(Locale.ROOT, "%.5f", value)
    private fun age(now: Long, at: Long?) = signalAge(now, at)?.let { "$it ms ago (owner monotonic clock)" } ?: "Unavailable"
}

/** No action callbacks exist here. Visibility only permits a read on the existing UI tick. */
internal class SignalRefreshOwner {
    private var last: Long? = null
    fun take(now: Long, expanded: Boolean, foreground: Boolean, focused: Boolean, unobscured: Boolean, pip: Boolean): Boolean {
        if (!expanded || !foreground || !focused || !unobscured || pip) { last = null; return false }
        val previous = last
        if (previous != null && now >= previous && now - previous < 500) return false
        last = now
        return true
    }
}
