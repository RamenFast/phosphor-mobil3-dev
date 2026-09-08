package dev.phosphor.mobil3

import java.io.File
import org.junit.Test
import kotlin.test.assertTrue

class HoldRenderBoundaryTest {
    @Test fun hudLiveIsIndependentOfControllableTransportAndKeepsMinimumTargets() {
        val hud = File("src/main/kotlin/dev/phosphor/mobil3/FloatingHudService.kt").readText()
        val action = hud.substringAfter("private fun returnDisplayToLive() {").substringBefore("private fun syncTransport()")
        assertTrue("PhosphorNative.setDisplayPaused(false)" in action)
        assertTrue("syncTransport()" in action)
        assertTrue("controller" !in action && "seek" !in action && ".play(" !in action && ".pause(" !in action)
        assertTrue("displayLive?.isEnabled = held and 1 != 0" in hud)
        val inspection = hud.substringAfter("val inspection = row()").substringBefore("val transport = row()")
        assertTrue("returnDisplayToLive()" in inspection && "panel.addView(inspection)" in inspection)
        assertTrue("LinearLayout.LayoutParams(0, dp(48), 1f)" in inspection)
        val transport = hud.substringAfter("val transport = row()").substringBefore("panel.addView(transport)")
        assertTrue("FIT" !in transport && "c.pause()" in transport && "c.play()" in transport)
    }

    @Test fun visualFreshBoundaryClearsGpuBeforeConsumingNewSourceSamples() {
        val render = File("../rust/src/render.rs").readText()
        val fresh = render.substringAfter("if energy_epoch.needs_clear(frame_token)")
            .substringBefore("let (source_active, samples, raw_peak)")
        assertTrue("r.clear_energy();" in fresh)
        assertTrue(fresh.indexOf("r.clear_energy();") < fresh.indexOf("computer.reset();"))
        assertTrue(fresh.indexOf("r.clear_energy();") < fresh.indexOf("energy_epoch.cleared(frame_token)"))
        assertTrue("s.frame_token()" in render && ".commit(frame_token, image)" in render)
        assertTrue("VISUAL_FRESH" !in render)
        val shared = File("../../phosphor/crates/phosphor-render-gpu/src/lib.rs").readText()
            .substringAfter("pub fn clear_energy(&mut self)").substringBefore("pub fn resize(")
        assertTrue("for view in &self.energy_views" in shared)
        assertTrue("LoadOp::Clear" in shared)
        assertTrue("self.queue.submit" in shared)
        assertTrue("self.current = 0" in shared)
    }

    @Test fun remoteObservationRetiresOldIntentBeforeResetAndUsesTheNativeOwner() {
        val remote = File("../rust/src/remote.rs").readText()
        for (name in listOf("connect(", "disconnect()")) {
            val body = remote.substringAfter("pub fn $name").substringBefore("\npub fn ")
            assertTrue(body.indexOf("generation.fetch_add") in 0 until body.indexOf("pause::invalidate"))
        }
        assertTrue("observe_transport_from(display_generation,!playing,||shared.scope_live()" in remote.replace(Regex("\\s+"), ""))
        val ui = File("src/main/kotlin/dev/phosphor/mobil3/RemotePlayer.kt").readText()
        assertTrue("observeTransportPaused" !in ui)
        val pause = File("../rust/src/pause.rs").readText()
        val transition = pause.substringAfter("fn apply_transition(").substringBefore("pub fn invalidate()")
        assertTrue(transition.indexOf("with_stereo_window") < transition.indexOf("DISPLAY.lock"))
        assertTrue("VISUAL_EPOCH.store(s.visual_revision" in transition && "ring.clear_pending()" in transition)
        assertTrue("finish_visual_reset" !in pause && "fresh_visual_ingress" !in pause)
    }

    @Test fun localRetirementClearsAfterProducerJoinAndInactivePublication() {
        val deck = File("../rust/src/deck.rs").readText()
        val close = deck.substringAfter("fn close_inner(invalidate: bool)")
        assertTrue(close.indexOf("close_session(") < close.indexOf("set_ring_state(false)"))
        assertTrue(close.indexOf("set_ring_state(false)") < close.indexOf("crate::pause::invalidate()"))
        assertTrue("close_inner(true)" in deck.substringAfter("pub fn close()").substringBefore("fn close_inner"))
    }
}
