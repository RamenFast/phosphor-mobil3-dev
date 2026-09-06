package dev.phosphor.mobil3

import org.json.JSONObject
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The rules that stop the app over-reporting the health of a relay link.
 *
 * Consumer regressions cover the original remote-truth audit and B11 media wake.
 * Native bridge_core remote_media_* tests exercise the actual media producer helpers.
 */
class RemoteLinkTruthTest {

    private fun status(json: String) = JSONObject(json)

    @Test
    fun priorSessionCountersCannotMakeWelcomeOrHeartbeatsLive() {
        val reading = RemoteLinkTruth.read(status(
            """{"state":"streaming","media_received":false,"media_live":false,"rx_a":4294967296,"rx_g":2147483647,"remote_rms":0.0}""",
        ))
        assertEquals(RemoteLinkState.GREETED, reading.state)
        assertFalse(SourceWakePolicy.remote(owned = true, state = reading.state))
    }

    @Test
    fun staleMediaReleasesWakeEvenBeforeNativeStateTickAndWithReportedSilence() {
        val reading = RemoteLinkTruth.read(status(
            """{"state":"streaming","media_received":true,"media_live":false,"rx_a":97,"rx_g":17,"remote_rms":0.0}""",
        ))
        assertEquals(RemoteLinkState.STALLED, reading.state)
        assertFalse(SourceWakePolicy.remote(owned = true, state = reading.state))
    }

    @Test
    fun absentOrInconsistentNativeFactsFailClosedWithoutCounterFallback() {
        for ((json, expected) in listOf(
            """{"state":"streaming","rx_a":97,"rx_g":17}""" to RemoteLinkState.GREETED,
            """{"state":"streaming","media_live":true,"rx_a":97}""" to RemoteLinkState.GREETED,
            """{"state":"streaming","media_received":true,"rx_a":97}""" to RemoteLinkState.STALLED,
            """{"state":"streaming","media_received":false,"media_live":true}""" to RemoteLinkState.GREETED,
        )) {
            val reading = RemoteLinkTruth.read(status(json))
            assertEquals(expected, reading.state, json)
            assertFalse(SourceWakePolicy.remote(owned = true, state = reading.state), json)
        }
    }

    @Test
    fun currentMediaTruthDoesNotDependOnDiagnosticCountersOrSampleLevel() {
        for ((rms, expected) in listOf("null" to RemoteLinkState.STREAMING, "0.0" to RemoteLinkState.SILENT)) {
            val reading = RemoteLinkTruth.read(status(
                """{"state":"streaming","media_received":true,"media_live":true,"rx_a":0,"rx_g":0,"remote_rms":$rms}""",
            ))
            assertEquals(expected, reading.state)
            assertTrue(SourceWakePolicy.remote(owned = true, state = reading.state))
            assertFalse(SourceWakePolicy.remote(owned = false, state = reading.state))
        }
    }

    @Test
    fun mediaRecoveryThenDisconnectFollowExistingWakeConsumer() {
        for ((json, expected) in listOf(
            """{"state":"streaming","media_received":true,"media_live":true}""" to true,
            """{"state":"stalled","media_received":true,"media_live":false}""" to false,
            """{"state":"streaming","media_received":true,"media_live":true,"remote_rms":0.0}""" to true,
            """{"state":"reconnecting","media_received":false,"media_live":false,"rx_a":97}""" to false,
            """{"state":"streaming","media_received":false,"media_live":false,"rx_a":97}""" to false,
            """{"state":"idle","media_received":false,"media_live":false,"rx_a":97}""" to false,
        )) {
            assertEquals(expected, SourceWakePolicy.remote(true, RemoteLinkTruth.read(status(json)).state), json)
        }
    }

    @Test
    fun welcomeWithoutFramesIsGreetedNotStreaming() {
        // The engine sets ST_STREAMING on the welcome frame, before any media. Calling
        // that "connected" claims a live link during a window where nothing has flowed.
        val reading = RemoteLinkTruth.read(
            status("""{"state":"streaming","media_received":false,"media_live":false,"rx_a":0,"rx_g":0}"""),
        )
        assertEquals(RemoteLinkState.GREETED, reading.state)
    }

    @Test
    fun audioFramesProveALiveLink() {
        val reading = RemoteLinkTruth.read(
            status("""{"state":"streaming","media_received":true,"media_live":true,"rx_a":42,"rx_g":0}"""),
        )
        assertEquals(RemoteLinkState.STREAMING, reading.state)
    }

    @Test
    fun geometryOnlySessionIsAlsoALiveLink() {
        // VISUALIZER-only is a real way to use the bridge: the desktop owns the beam and
        // no audio is requested. Demanding audio frames would call it dead.
        val reading = RemoteLinkTruth.read(
            status("""{"state":"streaming","media_received":true,"media_live":true,"rx_a":0,"rx_g":17}"""),
        )
        assertEquals(RemoteLinkState.STREAMING, reading.state)
    }

    @Test
    fun stalledIsNotReportedAsReconnecting() {
        // A frozen socket is not a reconnect. Acceptance H-04 forbids presenting one as a
        // live-but-frozen trace, and the old code collapsed both into Conn.LOST.
        val reading = RemoteLinkTruth.read(status("""{"state":"stalled"}"""))
        assertEquals(RemoteLinkState.STALLED, reading.state)
    }

    @Test
    fun reconnectingAndFailedKeepTheirOwnMeanings() {
        assertEquals(
            RemoteLinkState.RECONNECTING,
            RemoteLinkTruth.read(status("""{"state":"reconnecting"}""")).state,
        )
        assertEquals(
            RemoteLinkState.FAILED,
            RemoteLinkTruth.read(status("""{"state":"failed"}""")).state,
        )
    }

    @Test
    fun unknownOrAbsentStateFallsBackToConnectingRatherThanClaimingHealth() {
        assertEquals(
            RemoteLinkState.CONNECTING,
            RemoteLinkTruth.read(status("""{"state":"wat"}""")).state,
        )
        assertEquals(
            RemoteLinkState.CONNECTING,
            RemoteLinkTruth.read(status("{}")).state,
        )
    }

    @Test
    fun aRelayReportingSilenceIsSilentNotJustStreaming() {
        // The case that made this work necessary: 97 frames/sec arriving at rms 0.0 while
        // the beam drew nothing, indistinguishable from a dead link.
        val reading = RemoteLinkTruth.read(
            status("""{"state":"streaming","media_received":true,"media_live":true,"rx_a":97,"rx_g":0,"remote_rms":0.0}"""),
        )
        assertEquals(RemoteLinkState.SILENT, reading.state)
    }

    @Test
    fun audibleSoundIsStreamingRatherThanSilent() {
        val reading = RemoteLinkTruth.read(
            status("""{"state":"streaming","media_received":true,"media_live":true,"rx_a":97,"rx_g":0,"remote_rms":0.565686}"""),
        )
        assertEquals(RemoteLinkState.STREAMING, reading.state)
    }

    @Test
    fun aVeryQuietPassageIsStillSoundNotSilence() {
        // Quiet music must not be declared silent, or the state would flicker through
        // every fade and rest in a track.
        val reading = RemoteLinkTruth.read(
            status("""{"state":"streaming","media_received":true,"media_live":true,"rx_a":97,"rx_g":0,"remote_rms":0.004}"""),
        )
        assertEquals(RemoteLinkState.STREAMING, reading.state)
    }

    @Test
    fun anOlderRelayThatCannotReportLoudnessIsNeverCalledSilent() {
        // Absence of the field means "cannot tell", which must not be dressed up as a
        // measurement of silence. Older relays simply keep the previous behaviour.
        val reading = RemoteLinkTruth.read(
            status("""{"state":"streaming","media_received":true,"media_live":true,"rx_a":97,"rx_g":0}"""),
        )
        assertEquals(RemoteLinkState.STREAMING, reading.state)
    }

    @Test
    fun anExplicitNullLoudnessIsAlsoTreatedAsCannotTell() {
        // The engine emits null rather than 0.0 when the relay never reported loudness.
        val reading = RemoteLinkTruth.read(
            status("""{"state":"streaming","media_received":true,"media_live":true,"rx_a":97,"rx_g":0,"remote_rms":null}"""),
        )
        assertEquals(RemoteLinkState.STREAMING, reading.state)
    }

    @Test
    fun silenceIsOnlyClaimedOnceMediaIsActuallyFlowing() {
        // Before any frame arrives there is nothing to be silent about; that window is
        // GREETED, and calling it silent would imply a working link too early.
        val reading = RemoteLinkTruth.read(
            status("""{"state":"streaming","media_received":false,"media_live":false,"rx_a":0,"rx_g":0,"remote_rms":0.0}"""),
        )
        assertEquals(RemoteLinkState.GREETED, reading.state)
    }

    @Test
    fun failureCarriesBothTheErrorAndItsFix() {
        // Every engine and relay error ships a fix. Before this, the fix was built and
        // then discarded, so the user got a dead end.
        val reading = RemoteLinkTruth.read(
            status(
                """{"state":"failed","last_error":{"error":"relay speaks protocol v1",""" +
                    """"fix":"upgrade it: scripts/relay-install.sh --host <h>"}}""",
            ),
        )
        assertEquals(RemoteLinkState.FAILED, reading.state)
        assertEquals(
            "relay speaks protocol v1 — upgrade it: scripts/relay-install.sh --host <h>",
            reading.failure,
        )
    }

    @Test
    fun failureTextDegradesCleanlyWhenAPartIsMissing() {
        assertEquals(
            "only an error",
            RemoteLinkTruth.failureText(status("""{"last_error":{"error":"only an error"}}""")),
        )
        assertEquals(
            "only a fix",
            RemoteLinkTruth.failureText(status("""{"last_error":{"fix":"only a fix"}}""")),
        )
        assertEquals("", RemoteLinkTruth.failureText(status("""{"last_error":{}}""")))
        assertEquals("", RemoteLinkTruth.failureText(status("{}")))
    }

    @Test
    fun healthyLinkCarriesNoFailureText() {
        val reading = RemoteLinkTruth.read(
            status("""{"state":"streaming","media_received":true,"media_live":true,"rx_a":100,"rx_g":0}"""),
        )
        assertEquals("", reading.failure)
    }
}
