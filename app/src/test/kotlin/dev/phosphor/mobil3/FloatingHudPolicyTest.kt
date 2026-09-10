package dev.phosphor.mobil3

import dev.phosphor.mobil3.ui.phase9Source
import org.junit.Test
import kotlin.test.*

class FloatingHudPolicyTest {
    @Test fun trackCallbacksAdvanceOnceAcrossOwnerHandoffAndIgnoreStaleOwners() {
        val owner = SurfaceOwner {}
        val tracks = PresentationTrackGate()
        var advances = 0
        val firstTrack = listOf("item-a", "0", "local", "First", "Artist", "Album")
        val secondTrack = listOf("item-b", "1", "local", "Second", "Artist", "Album")
        val app = owner.claim("app")
        owner.change(app) { if (tracks.changed(firstTrack)) advances++ }
        val hud = owner.claim("hud")
        owner.change(app) { if (tracks.changed(secondTrack)) advances++ }
        owner.change(hud) { if (tracks.changed(firstTrack)) advances++ }
        assertEquals(1, advances)
        owner.change(hud) { if (tracks.changed(secondTrack)) advances++ }
        val returned = owner.claim("app")
        owner.change(returned) { if (tracks.changed(secondTrack)) advances++ }
        assertEquals(2, advances)
        owner.release(returned)
        owner.change(returned) { if (tracks.changed(firstTrack)) advances++ }
        assertEquals(2, advances)
    }

    @Test fun stableItemsIgnoreEnrichmentArtworkAndRepeatedMetadataAcrossHandoff() {
        val gate = PresentationTrackGate()
        val owner = SurfaceOwner {}
        var advances = 0
        fun publish(lease: SurfaceOwner.Lease, item: String, title: String, artist: String? = null, artwork: ByteArray? = null) {
            // Artwork is intentionally absent from the production identity input.
            artwork?.size
            owner.change(lease) {
                if (gate.changed(gate.identity(item, 1, null, null, title, artist, null))) advances++
            }
        }
        val app = owner.claim("app")
        publish(app, "local:7:1", "file.flac")
        publish(app, "local:7:1", "Song", "Artist")
        publish(app, "local:7:1", "Song", "Artist", byteArrayOf(1))
        repeat(3) { publish(app, "local:7:1", "Song", "Artist") }
        val hud = owner.claim("hud")
        publish(hud, "local:7:1", "Song", "Artist")
        publish(app, "local:7:2", "Song", "Artist")
        assertEquals(1, advances)
        publish(hud, "local:7:2", "Song", "Artist")
        val returned = owner.claim("app")
        publish(returned, "local:7:2", "Song", "Artist")
        assertEquals(2, advances)
        publish(returned, "local:8:2", "Song", "Artist")
        assertEquals(3, advances, "Replacing the queue creates a distinct local item")
    }

    @Test fun placeholderRemoteAndCaptureIdsKeepUsefulLabelFallback() {
        for (source in listOf("remote", "capture")) {
            val gate = PresentationTrackGate()
            fun identity(title: String, endpoint: String = "source-a") =
                gate.identity("$source:now", 1, source, endpoint, title, "Artist", "Album")
            assertTrue(gate.changed(identity("First")))
            assertFalse(gate.changed(identity("First")))
            assertTrue(gate.changed(identity("Second")))
            assertTrue(gate.changed(identity("Second", "source-b")))
            assertFalse(gate.changed(identity("Second", "source-b")), "No claim to distinguish identical external labels")
        }
    }

    @Test fun failedRetirementNeverGrantsOrAcceptsASuccessor() {
        val owner = SurfaceOwner { error("ack disconnected") }
        val app = owner.claim("app")
        assertFailsWith<IllegalStateException> { owner.release(app) }
        assertFalse(owner.accepts(app))
        assertFailsWith<IllegalStateException> { owner.claim("hud") }
        assertFalse(owner.change(app) { fail("unconfirmed owner used") })
    }

    @Test fun productionCallbackAdapterContainsDisconnectAndStillRunsEveryCleanup() {
        for (entry in listOf("destroy", "failed attach", "activity hidden", "HUD hidden", "close")) {
            val owner = SurfaceOwner { error("ack disconnected") }
            val lease = owner.claim(entry)
            val statuses = mutableListOf<String>()
            var failures = 0
            val callbacks = SurfaceCallbacks({ statuses += it }, { failures++ })
            assertFalse(callbacks.run { owner.release(lease) }, entry)
            assertFalse(owner.accepts(lease))
            assertFalse(callbacks.run { owner.claim("successor") })
            assertFalse(owner.change(lease) { fail("stale native presentation") })
            val cleanup = mutableListOf<String>()
            for (step in listOf("listener", "window", "controller", "foreground service")) {
                assertTrue(callbacks.run { cleanup += step })
            }
            assertEquals(4, cleanup.size)
            assertEquals(1, failures)
            assertEquals(listOf("Surface unavailable. Restart Phosphor before retrying"), statuses)
            assertEquals(statuses.single(), callbacks.failure)
        }
    }

    @Test fun failedStatusDeliveryCannotSuppressTheIndependentCleanupRequest() {
        var cleanups = 0
        val callbacks = SurfaceCallbacks({ error("observer unavailable") }, { cleanups++; error("cleanup observer failed") })
        assertFalse(callbacks.run { error("native disconnect") })
        assertEquals(1, cleanups)
        assertTrue(callbacks.run {})
        callbacks.fail("must not overwrite the first failure")
        assertEquals("Surface unavailable. Restart Phosphor before retrying", callbacks.failure)
        assertEquals(1, cleanups)
    }

    @Test fun pendingSuccessFailureAndCloseUseOneProductionConnectionOwner() {
        for (outcome in listOf("success", "failure", "pending")) for (closeFirst in listOf(false, true)) {
            val future = java.util.concurrent.CompletableFuture<String>()
            var disposals = 0
            var publications = 0
            val owner = HudConnection<java.util.concurrent.CompletableFuture<String>> {
                assertSame(future, it)
                disposals++
                it.cancel(false)
            }
            owner.retain(future)
            future.whenComplete { value, _ -> if (owner.accepts(future) && value != null) publications++ }
            if (closeFirst) owner.close()
            when (outcome) {
                "success" -> future.complete("controller")
                "failure" -> future.completeExceptionally(IllegalStateException("connection failed"))
            }
            assertEquals(if (!closeFirst && outcome == "success") 1 else 0, publications)
            owner.close()
            owner.close()
            assertEquals(1, disposals)
            assertFalse(owner.accepts(future))
            assertTrue(future.isDone)
        }
        val absent = HudConnection<Any> { fail("no future to release") }
        absent.close()
    }

    @Test fun everyCleanupStepRunsAfterAnyEarlierCleanupThrows() {
        val names = listOf("screen listener", "permission listener", "surface", "window", "media controller", "foreground notification")
        for (failed in names) {
            val cleanup = HudCleanup()
            val attempted = mutableListOf<String>()
            names.forEach { name -> cleanup.run(name) { attempted += name; if (name == failed) error("fixture failure") } }
            assertEquals(names, attempted)
            assertEquals(listOf(failed), cleanup.failures)
        }
    }

    @Test fun ownerTransfersFenceOldChangeAndDestroyIncludingSameOwnerRecreation() {
        var detaches = 0
        var attached = "none"
        val owner = SurfaceOwner { detaches++; attached = "none" }
        val activity = Any()
        val app = owner.claim(activity)
        assertTrue(owner.change(app) { attached = "app" })
        val hud = owner.claim(Any())
        assertEquals(1, detaches)
        assertTrue(owner.change(hud) { attached = "hud" })
        assertFalse(owner.change(app) { attached = "stale app" })
        assertFalse(owner.release(app))
        assertEquals("hud", attached)
        val returning = owner.claim(activity)
        assertTrue(returning.generation > app.generation)
        assertFalse(owner.release(hud))
        assertFalse(owner.change(app) { fail("Old holder reclaimed replacement") })
        assertTrue(owner.change(returning) { attached = "app restored" })
        assertTrue(owner.release(returning))
        assertFalse(owner.release(returning))
        assertNull(owner.current)
        assertEquals(3, detaches)
    }

    @Test fun failedWindowAdditionDoesNotClaimPresentationAndNativeFailureRestoresNewLease() {
        var detaches = 0
        val owner = SurfaceOwner { detaches++ }
        val app = owner.claim("app")
        val session = HudSession()
        val failed = assertNotNull(session.request())
        session.closing(failed)
        session.destroyed(failed)
        assertTrue(owner.accepts(app))
        assertEquals(0, detaches)
        val next = assertNotNull(session.request())
        assertTrue(session.shown(next))
        val hud = owner.claim("hud")
        assertTrue(owner.release(hud))
        val restored = owner.claim("app")
        assertTrue(owner.accepts(restored))
        assertFalse(owner.release(app))
    }

    @Test fun revocationLockCloseAndServiceDeathRequireANewExplicitGeneration() {
        for (reason in listOf("revocation", "lock", "close", "task removal", "service death")) {
            val session = HudSession()
            val old = assertNotNull(session.request(), reason)
            assertTrue(session.active)
            assertTrue(session.shown(old))
            assertTrue(session.closing(old))
            assertFalse(session.active)
            assertFalse(session.shown(old))
            assertNull(session.request(), "Cannot reuse a closing Android service")
            session.destroyed(old)
            assertEquals(HudSession.Stage.IDLE, session.stage)
            val new = assertNotNull(session.request())
            assertTrue(new > old)
            session.destroyed(old)
            assertTrue(session.accepts(new))
            assertFalse(session.closing(old))
        }
    }

    @Test fun missingAndImportedPreferencesAreInertAndKeepOldHudSeparate() {
        assertEquals(HudPolicy.Preferences(), HudPolicy.read(emptyMap<String, Any>()))
        val session = HudSession()
        val imported = HudPolicy.read(mapOf(HudPolicy.ENABLED to true, HudPolicy.BACKGROUND to "TRANSPARENT", "hud_mode" to 0))
        assertTrue(imported.enabled)
        assertEquals(HudPolicy.Background.TRANSPARENT, imported.background)
        assertFalse(session.active)
        assertNotNull(HudPolicy.refusal(false, true, true, false, false))
        assertNotNull(HudPolicy.refusal(true, false, true, false, false))
        assertNotNull(HudPolicy.refusal(true, true, false, false, false))
        assertNotNull(HudPolicy.refusal(true, true, true, true, false))
        assertTrue(assertNotNull(HudPolicy.refusal(true, true, true, false, true)).contains("Microphone"))
        assertNull(HudPolicy.refusal(true, true, true, false, false))
    }

    @Test fun hiddenLockedOrRevokedPresentationNeverRuns() {
        for (visible in listOf(false, true)) for (interactive in listOf(false, true))
            for (unlocked in listOf(false, true)) for (access in listOf(false, true)) {
                assertEquals(visible && interactive && unlocked && access,
                    HudPolicy.presentationAllowed(visible, interactive, unlocked, access))
            }
    }

    @Test fun resizeAndDragRemainInsideEveryDisplayIncludingSmallerThanMinimum() {
        for (aw in listOf(1, 180, 360, 1440)) for (ah in listOf(1, 180, 800))
            for (x in listOf(-900, 0, 2000)) for (y in listOf(-900, 0, 2000))
                for (size in listOf(-1, 200, 320, 5000)) {
                    val b = HudPolicy.bounds(x, y, size, size, aw, ah, 240)
                    assertTrue(b.x >= 0 && b.y >= 0)
                    assertTrue(b.width in minOf(240, aw)..aw)
                    assertTrue(b.height in minOf(240, ah)..ah)
                    assertTrue(b.x + b.width <= aw && b.y + b.height <= ah)
                }
    }

    @Test fun actualWiringKeepsImportSourceAndNativeOwnershipSeparate() {
        val activity = phase9Source("MainActivity.kt")
        val service = phase9Source("FloatingHudService.kt")
        val host = phase9Source("SurfaceHost.kt")
        val restore = activity.substringAfter("private fun restoreTuning()").substringBefore("override fun")
        assertFalse("FloatingHudService.show" in restore)
        assertFalse("ACTION_MANAGE_OVERLAY_PERMISSION" in restore)
        assertTrue("(mic.ownsSource() && !mic.established()) || micHandoff.isPending" in activity)
        assertFalse("PhosphorNative.setRenderPaused" in activity)
        assertFalse("PhosphorNative.surfaceDestroyed" in activity)
        assertTrue("SurfaceHost.activityVisible(false)" in activity)
        assertTrue("!FloatingHudService.active && PictureInPicturePolicy" in activity)
        assertTrue("if (!state.presentationVisible) return" in phase9Source("ui/PhosphorScreen.kt"))
        for (forbidden in listOf("startMic(", "startCapture(", "pushCaptureSamples", "deckOpen(", "remoteConnect(")) assertFalse(forbidden in service)
        assertTrue("CaptureMirrorPolicy.displayedPlaying" in service)
        assertTrue("isCommandAvailable(Player.COMMAND_PLAY_PAUSE)" in service)
        assertTrue("START_NOT_STICKY" in service)
        assertTrue("MediaController.releaseFuture(it)" in service)
        assertTrue("connection.retain(future)" in service)
        assertTrue("!connection.accepts(future)" in service)
        assertFalse("c?.release()" in service)
        assertTrue("override fun onEvents" in host)
        assertTrue("player.addListener(itemListener)" in host)
        assertTrue("removeListener(itemListener)" in host)
        assertTrue("wm.addView(panel, params)" in service)
        assertTrue("wm.removeViewImmediate(view)" in service)
        assertTrue("stopWatchingMode(appOps)" in service)
        assertTrue("PixelFormat.TRANSLUCENT" in host)
        assertTrue("holder.setFormat(PixelFormat.OPAQUE)" in host)
        assertTrue("owner.change(lease)" in host)
        assertTrue("callbacks.run { owner.release(retiring) }" in host)
        assertTrue("if (!visible) activity?.retire()" in host)
        assertTrue("if (!visible) host.retire()" in host)
        assertFalse("getOrThrow()" in host)
    }
}
