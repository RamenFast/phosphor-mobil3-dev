package dev.phosphor.mobil3

import java.io.File
import org.junit.Test
import kotlin.test.*

/** Source boundaries only. Device receipts prove actual logging and presentation. */
class AcceptanceTraceTest {
    @Test fun releaseHasNoLoggingOrFieldEvaluation() {
        val release = File("src/release/kotlin/dev/phosphor/mobil3/AcceptanceTrace.kt").readText()
        assertTrue(release.contains("inline fun record(event: String, fields: () -> String) = Unit"))
        assertFalse(release.contains("android."))
        assertFalse(release.contains("fields()"))
    }

    @Test fun debugFieldsAreEvaluatedOnlyAfterExplicitLogOptIn() {
        val debug = File("src/debug/kotlin/dev/phosphor/mobil3/AcceptanceTrace.kt").readText()
        assertTrue(debug.indexOf("Log.isLoggable") < debug.indexOf("fields()"))
        assertTrue(debug.contains("SystemClock.uptimeMillis()"))
        assertFalse(debug.contains("SystemClock.elapsedRealtime()"))
    }

    @Test fun glyphObservationUsesTheSameSnapshotAsTheDrawnLabel() {
        val console = File("src/main/kotlin/dev/phosphor/mobil3/ui/Console.kt").readText()
        val key = console.substringAfter("val drawnPlaying = state.playing").substringBefore("if (hasTransport && (!capture || state.captureCanNext))")
        assertTrue(key.contains("PauseDisplayPolicy.controlLabel(displayOnly, drawnPlaying, drawnPaused)"))
        assertTrue(key.contains("StoneKey(drawnLabel"))
        assertTrue(key.contains("if (capture && !displayOnly) AcceptanceTrace.record(\"capture_glyph_draw\")"))
        assertTrue(key.contains("if (displayOnly) AcceptanceTrace.record(\"display_pause_draw\")"))
        assertTrue(key.contains("playing=\$drawnPlaying"))
        assertTrue(key.contains("paused=\$drawnPaused label=\$drawnLabel"))
        assertTrue(key.indexOf("drawContent()") < key.indexOf("capture_glyph_draw"))
    }
}
