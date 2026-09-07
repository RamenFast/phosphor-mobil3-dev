package dev.phosphor.mobil3

import android.media.session.PlaybackState
import org.junit.Test
import java.io.File
import kotlin.test.*

class CaptureMirrorPolicyTest {
    @Test fun playingPausedBufferingAndEveryActiveTransitionMatchTheCaptureGlyph() {
        val active = listOf(PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING,
            PlaybackState.STATE_CONNECTING, PlaybackState.STATE_FAST_FORWARDING,
            PlaybackState.STATE_REWINDING, PlaybackState.STATE_SKIPPING_TO_NEXT,
            PlaybackState.STATE_SKIPPING_TO_PREVIOUS, PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM)
        for (state in active) {
            assertTrue(CaptureMirrorPolicy.playing(state))
            assertTrue(CaptureMirrorPolicy.displayedPlaying(true, false, state))
        }
        for (state in listOf(PlaybackState.STATE_PAUSED, PlaybackState.STATE_STOPPED,
            PlaybackState.STATE_NONE, PlaybackState.STATE_ERROR)) assertFalse(CaptureMirrorPolicy.playing(state))
        assertFalse(CaptureMirrorPolicy.displayedPlaying(true, false, PlaybackState.STATE_PAUSED))
        assertTrue(CaptureMirrorPolicy.displayedPlaying(true, true, PlaybackState.STATE_PLAYING))
    }

    @Test fun missingNotificationAccessCannotReproduceOptimisticInvertedToggle() {
        val routed = mutableListOf<Boolean>()
        val noControllerState = PlaybackState.STATE_NONE
        repeat(3) {
            CaptureMirrorPolicy.routePlayPause(noControllerState, 0L, true) { routed.add(it) }
        }
        assertTrue(routed.isEmpty())
        assertFalse(CaptureMirrorPolicy.available(noControllerState))
        assertFalse(CaptureMirrorPolicy.playing(noControllerState))
        assertFalse(CaptureMirrorPolicy.seekable(noControllerState, PlaybackState.ACTION_SEEK_TO, 1000))
    }

    @Test fun routeWaitsForObservedStateAndBufferingCanPause() {
        val routed = mutableListOf<Boolean>()
        val state = PlaybackState.STATE_BUFFERING
        CaptureMirrorPolicy.routePlayPause(state, PlaybackState.ACTION_PAUSE, false) { routed.add(it) }
        assertEquals(listOf(false), routed)
        assertTrue(CaptureMirrorPolicy.playing(state))
        assertFalse(CaptureMirrorPolicy.playing(PlaybackState.STATE_PAUSED))
        CaptureMirrorPolicy.routePlayPause(PlaybackState.STATE_PAUSED, PlaybackState.ACTION_PLAY, true) { routed.add(it) }
        assertEquals(listOf(false, true), routed)
        CaptureMirrorPolicy.routePlayPause(PlaybackState.STATE_ERROR, Long.MAX_VALUE, true) { routed.add(it) }
        CaptureMirrorPolicy.routePlayPause(PlaybackState.STATE_PLAYING, PlaybackState.ACTION_PLAY_PAUSE, true) { routed.add(it) }
        assertEquals(2, routed.size)
    }

    @Test fun seekRequiresActualDurationAndAdvertisedActionAtBothBoundaries() {
        for (state in listOf(PlaybackState.STATE_PLAYING, PlaybackState.STATE_PAUSED, PlaybackState.STATE_BUFFERING)) {
            assertTrue(CaptureMirrorPolicy.seekable(state, PlaybackState.ACTION_SEEK_TO, 120000))
            assertFalse(CaptureMirrorPolicy.seekable(state, PlaybackState.ACTION_PLAY_PAUSE, 120000))
            for (duration in listOf(-9223372036854775807L, -1L, 0L, Long.MAX_VALUE)) {
                assertFalse(CaptureMirrorPolicy.seekable(state, PlaybackState.ACTION_SEEK_TO, duration))
            }
        }
        assertFalse(CaptureMirrorPolicy.seekable(PlaybackState.STATE_ERROR, PlaybackState.ACTION_SEEK_TO, 120000))
    }

    @Test fun currentGlyphRequiresItsActualTransportAction() {
        assertTrue(CaptureMirrorPolicy.canPlayPause(PlaybackState.STATE_PLAYING, PlaybackState.ACTION_PAUSE))
        assertTrue(CaptureMirrorPolicy.canPlayPause(PlaybackState.STATE_BUFFERING, PlaybackState.ACTION_PLAY_PAUSE))
        assertFalse(CaptureMirrorPolicy.canPlayPause(PlaybackState.STATE_PLAYING, PlaybackState.ACTION_PLAY))
        assertTrue(CaptureMirrorPolicy.canPlayPause(PlaybackState.STATE_PAUSED, PlaybackState.ACTION_PLAY))
        assertFalse(CaptureMirrorPolicy.canPlayPause(PlaybackState.STATE_PAUSED, PlaybackState.ACTION_PAUSE))
        assertFalse(CaptureMirrorPolicy.canPlayPause(PlaybackState.STATE_NONE, Long.MAX_VALUE))
        assertFalse(CaptureMirrorPolicy.canPlayPause(PlaybackState.STATE_ERROR, Long.MAX_VALUE))
    }

    @Test fun freshUnavailableControllerRejectsLingeringSkipBits() {
        for (action in listOf(PlaybackState.ACTION_SKIP_TO_NEXT, PlaybackState.ACTION_SKIP_TO_PREVIOUS)) {
            for (state in listOf(PlaybackState.STATE_NONE, PlaybackState.STATE_ERROR)) {
                assertFalse(CaptureMirrorPolicy.supports(state, action, action))
            }
            for (state in listOf(PlaybackState.STATE_PLAYING, PlaybackState.STATE_PAUSED)) {
                assertTrue(CaptureMirrorPolicy.supports(state, action, action))
                assertFalse(CaptureMirrorPolicy.supports(state, 0L, action))
            }
        }
    }

    @Test fun staleCallbacksLoseAuthorityIncludingSameControllerReattachment() {
        val binding = CaptureControllerBinding<Any>()
        val first = Any()
        val second = Any()
        val firstCallback = binding.bind(first)
        assertTrue(firstCallback())
        val secondCallback = binding.bind(second)
        assertFalse(firstCallback())
        assertTrue(secondCallback())
        binding.bind(null)
        assertFalse(secondCallback())
        assertNull(binding.current)
        val reattached = binding.bind(first)
        assertTrue(reattached())
        assertFalse(firstCallback())
        binding.bind(null) // lost notification access or destroyed session
        assertFalse(reattached())
    }

    @Test fun activeMirrorReattachesItsExactPlayerWithoutResettingIt() {
        val capture = Any()
        val local = Any()
        var player: Any = local
        var attachments = 0
        CaptureMirrorPolicy.attach(player, capture) { player = it; attachments++ }
        assertSame(capture, player)
        CaptureMirrorPolicy.attach(player, capture) { player = it; attachments++ }
        assertEquals(1, attachments)
        player = local
        CaptureMirrorPolicy.attach(player, capture) { player = it; attachments++ }
        assertSame(capture, player)
        assertEquals(2, attachments)
    }

    @Test fun captureBufferingToPausedLocalOrRemoteUsesUnchangedNoncaptureTruth() {
        assertTrue(CaptureMirrorPolicy.displayedPlaying(true, false, PlaybackState.STATE_BUFFERING))
        for (source in listOf("local", "remote")) {
            assertFalse(CaptureMirrorPolicy.displayedPlaying(source == "capture", false, PlaybackState.STATE_PLAYING))
            assertTrue(CaptureMirrorPolicy.displayedPlaying(source == "capture", true, PlaybackState.STATE_PAUSED))
        }
    }

    @Test fun optimisticControllerPredictionsCannotFlipTheCaptureGlyph() {
        // Actual ASUS sequence: prediction, rollback, then the Spotify callback.
        val pauseRequest = listOf(
            false to PlaybackState.STATE_PLAYING,
            true to PlaybackState.STATE_PLAYING,
            false to PlaybackState.STATE_PAUSED,
        )
        assertEquals(listOf(true, true, false), pauseRequest.map { (predicted, observed) ->
            CaptureMirrorPolicy.displayedPlaying(true, predicted, observed)
        })
        val playRequest = listOf(
            true to PlaybackState.STATE_PAUSED,
            false to PlaybackState.STATE_PAUSED,
            true to PlaybackState.STATE_PLAYING,
        )
        assertEquals(listOf(false, false, true), playRequest.map { (predicted, observed) ->
            CaptureMirrorPolicy.displayedPlaying(true, predicted, observed)
        })
        assertFalse(CaptureMirrorPolicy.displayedPlaying(true, true, PlaybackState.STATE_NONE))
        assertFalse(CaptureMirrorPolicy.displayedPlaying(true, true, PlaybackState.STATE_ERROR))
    }

    @Test fun observedStateTravelsThroughSessionExtrasWithActivityOwnershipChecks() {
        val base = File("src/main/kotlin/dev/phosphor/mobil3")
        val service = File(base, "PlaybackService.kt").readText()
        val activity = File(base, "MainActivity.kt").readText()
        assertTrue(service.contains("publishObservedCaptureState(state?.state ?: PlatformPlaybackState.STATE_NONE)"))
        val leave = service.substringAfter("private fun leaveCaptureMirror()").substringBefore("private fun unregisterCaptureSessionsListener")
        assertTrue(leave.contains("publishObservedCaptureState(PlatformPlaybackState.STATE_NONE)"))
        assertTrue(service.contains("current.setSessionExtras(Bundle(extras)"))
        val callback = activity.substringAfter("override fun onExtrasChanged(").substringBefore("}).buildAsync()")
        assertTrue(callback.contains("taskIsCurrent() && controllerBinding.accepts(bindingRevision) && controller === current"))
        assertTrue(callback.contains("ui.playing = sessionPlaying(current)"))
        val display = activity.substringAfter("private fun sessionPlaying(").substringBefore("override fun onDestroy()")
        assertTrue(display.contains("observedState = player.sessionExtras.getInt(CaptureMirrorPolicy.OBSERVED_STATE)"))
        assertFalse(display.contains("playWhenReady"))
        val toggle = activity.substringAfter("override fun togglePlay()").substringBefore("override fun next()")
        assertTrue(toggle.contains("sessionPlaying(c) else c.playWhenReady"))
    }

    @Test fun productionCallbacksObserverAndSeekRouterUseTheTestedPolicies() {
        val base = File("src/main/kotlin/dev/phosphor/mobil3")
        val service = File(base, "PlaybackService.kt").readText()
        val activity = File(base, "MainActivity.kt").readText()
        val begin = service.substringAfter("private fun beginCaptureMirror()").substringBefore("private fun endCaptureMirror")
        assertTrue(begin.indexOf("CaptureMirrorPolicy.attach") < begin.indexOf("if (captureActive)"))
        assertTrue(service.contains("captureBinding.bind(chosen)"))
        assertTrue(service.contains("captureActive && isCurrent()"))
        assertTrue(service.contains("playing = CaptureMirrorPolicy.playing(state)"))
        assertTrue(service.contains("CaptureMirrorPolicy.routePlayPause(platformState, actions, playWhenReady)"))
        assertEquals(2, Regex("CaptureMirrorPolicy.seekable\\(").findAll(service).count())
        assertTrue(service.contains(".setIsSeekable(seekable())"))
        assertTrue(service.contains("if (seekable())"))
        assertFalse(service.contains("dispatchSystemMediaKey"))
        val skip = service.substringAfter("private fun routeCaptureSkip(").substringBefore("// Queue documents")
        assertEquals(1, Regex("controller.playbackState").findAll(skip).count())
        assertTrue(skip.contains("CaptureMirrorPolicy.supports(state.state, state.actions, action)"))
        assertTrue(service.contains("nextRouter = { routeCaptureSkip(next = true) }"))
        assertTrue(service.contains("previousRouter = { routeCaptureSkip(next = false) }"))
        val play = service.substringAfter("private fun routeCapturePlayPause(").substringBefore("private fun routeCaptureSkip")
        assertEquals(1, Regex("controller.playbackState").findAll(play).count())
        assertTrue(play.contains("if (play == CaptureMirrorPolicy.playing(state.state)) return"))
        val mirror = service.substringAfter("internal class CaptureMirrorPlayer")
        assertFalse(mirror.contains("playing = playWhenReady"))
        assertTrue(activity.contains("CaptureMirrorPolicy.displayedPlaying("))
        val timeline = activity.substringAfter("override fun onTimelineChanged(").substringBefore("override fun onMediaItemTransition")
        assertTrue(timeline.contains("ui.playing = sessionPlaying(c)"))
        val metadata = activity.substringAfter("override fun onMediaMetadataChanged(").substringBefore("// Initial sync")
        assertTrue(metadata.contains("ui.playing = sessionPlaying(c)"))
        assertTrue(activity.contains("override fun onAvailableCommandsChanged("))
        assertTrue(activity.contains("ui.captureCanPlay = capture && player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)"))
        assertTrue(activity.contains("ui.captureCanNext = capture && player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)"))
        assertTrue(activity.contains("ui.captureCanPrevious = capture && player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)"))
        val uiState = File(base, "ui/ScopeUiState.kt").readText()
        for (capability in listOf("captureCanPlay", "captureCanNext", "captureCanPrevious")) {
            assertTrue(uiState.contains("var $capability by mutableStateOf(false)"))
        }
        val console = File(base, "ui/Console.kt").readText()
        assertTrue(console.contains("if (!capture || state.captureCanPlay)"))
        assertTrue(console.contains("if (hasTransport && (!capture || state.captureCanNext))"))
        assertTrue(console.contains("if (hasTransport && (!capture || state.captureCanPrevious))"))
        assertTrue(console.contains("FlatKey(\"SRC\", p, designator = \"J1\", onClick = onSrc)"))
    }
}
