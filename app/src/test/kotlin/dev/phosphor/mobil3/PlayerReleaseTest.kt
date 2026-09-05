package dev.phosphor.mobil3

import com.google.common.util.concurrent.ListenableFuture
import org.junit.Test
import sun.misc.Unsafe
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Calls production handlers only. Android construction, Looper and SimpleBasePlayer.release are not exercised. */
class PlayerReleaseTest {
    @Test fun remoteProductionHandleReleaseIsImmediateRepeatedAndDoesNotDisconnectTheOwner() {
        val player = withoutAndroidConstructor(RemotePlayer::class.java)
        var effects = 0
        player.onStopRequested = { effects++ }
        player.onTransportIntent = { effects++ }
        assertImmediateHandler(player)
        assertEquals(0, effects)
    }

    @Test fun localProductionHandleReleaseIsImmediateRepeatedAndDoesNotStopTheOwner() {
        val player = withoutAndroidConstructor(PhosphorPlayer::class.java)
        var effects = 0
        player.onStopRequested = { effects++ }
        player.onSwitchTrack = { effects++ }
        player.onSeek = { _, _ -> effects++ }
        assertImmediateHandler(player)
        assertEquals(0, effects)
    }

    @Test fun captureProductionHandleReleaseIsImmediateRepeatedAndDoesNotSendExternalTransport() {
        val player = withoutAndroidConstructor(CaptureMirrorPlayer::class.java)
        var effects = 0
        player.playPauseRouter = { effects++ }
        player.nextRouter = { effects++ }
        player.previousRouter = { effects++ }
        player.seekRouter = { effects++ }
        assertImmediateHandler(player)
        assertEquals(0, effects)
    }

    private fun assertImmediateHandler(player: Any) {
        val handler = player.javaClass.getDeclaredMethod("handleRelease").apply { isAccessible = true }
        repeat(3) {
            val future = handler.invoke(player) as ListenableFuture<*>
            assertTrue(future.isDone)
            assertFalse(future.isCancelled)
            assertNull(future.get(0, TimeUnit.NANOSECONDS))
        }
    }

    private fun <T : Any> withoutAndroidConstructor(type: Class<T>): T {
        val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
        return requireNotNull(type.cast((field.get(null) as Unsafe).allocateInstance(type)))
    }
}
