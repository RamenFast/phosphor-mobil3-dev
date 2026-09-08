package dev.phosphor.mobil3.settings

import dev.phosphor.mobil3.ui.LightSettings
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class SettingsWriteOwnerTest {
    @Test fun decodedPartialImportMergesAfterAnInterveningOwnerEdit() {
        val owner = SettingsWriteOwner()
        var saved = LightSettings().values()
        var native = LightSettings.read(saved)
        var ui = native
        val decoded = AtomicReference<SettingsArchive.ImportResult>()
        val decodedReady = CountDownLatch(1)
        val allowDelivery = CountDownLatch(1)
        val worker = Thread {
            val full = SettingsArchive.export("dev.phosphor.mobil3", "2", "debug", "2026-09-08T00:00:00Z",
                mapOf("cycle_seconds" to 8f))
            decoded.set(SettingsArchive.decode(full.json))
            decodedReady.countDown()
            check(allowDelivery.await(2, TimeUnit.SECONDS))
        }
        worker.start()
        assertTrue(decodedReady.await(2, TimeUnit.SECONDS))
        owner.write {
            val edit = LightSettings(perTrack = true)
            assertNull(owner.commit({ saved = edit.values(); true }, { native = edit; true }, { false }))
            ui = edit
        }
        allowDelivery.countDown()
        worker.join(2000)
        assertFalse(worker.isAlive)
        owner.write {
            val imported = SettingsArchive.merge(decoded.get(), saved)
            val accepted = LightSettings.read(saved + imported)
            assertNull(owner.commit({ saved = saved + imported; true }, { native = accepted; true }, { false }))
            ui = accepted
        }
        assertTrue(ui.perTrack)
        assertEquals(8f, ui.seconds)
        assertEquals(ui, native)
        assertEquals(ui, LightSettings.read(saved))
    }

    @Test fun foreignAndNestedWritesCannotInterleaveTheCommitPublicationBoundary() {
        val owner = SettingsWriteOwner()
        val attempted = CountDownLatch(1)
        val failure = AtomicReference<Throwable>()
        var mutation = false
        owner.write {
            val worker = Thread {
                try { owner.write { mutation = true } } catch (error: Throwable) { failure.set(error) }
                attempted.countDown()
            }
            worker.start()
            assertTrue(attempted.await(2, TimeUnit.SECONDS))
            worker.join(2000)
            assertTrue(failure.get() is IllegalStateException)
            assertThrows(IllegalStateException::class.java) { owner.write { mutation = true } }
        }
        assertFalse(mutation)
        owner.write { mutation = true }
        assertTrue(mutation)
    }

    @Test fun nativeRejectionReportsBothActualRollbackOutcomes() {
        for (restored in listOf(false, true)) {
            val owner = SettingsWriteOwner()
            var commits = 0
            var publishes = 0
            var rollbacks = 0
            val failure = owner.write {
                owner.commit({ commits++; true }, { publishes++; false }, { rollbacks++; restored })
            }!!
            assertEquals(1, commits)
            assertEquals(1, publishes)
            assertEquals(1, rollbacks)
            assertEquals(restored, failure.restored)
            assertEquals(restored, failure.message().contains("Previous settings restored"))
            assertEquals(!restored, failure.message().contains("uncertain"))
        }
    }

    @Test fun failedSaveDoesNotPublishAndExceptionsStillAttemptRollback() {
        val owner = SettingsWriteOwner()
        var publishes = 0
        var rollbacks = 0
        val result = owner.write {
            owner.commit({ throw IllegalStateException("storage") }, { publishes++; true }, { rollbacks++; false })
        }!!
        assertEquals(0, publishes)
        assertEquals(1, rollbacks)
        assertFalse(result.restored)
        owner.write { assertNull(owner.commit({ true }, { true }, { error("rollback must not run") })) }
    }

    @Test fun activityWiringKeepsDecodeOffOwnerAndAcceptedWritesOnOwner() {
        val file = listOf(java.io.File("src/main/kotlin/dev/phosphor/mobil3/MainActivity.kt"),
            java.io.File("app/src/main/kotlin/dev/phosphor/mobil3/MainActivity.kt")).first { it.isFile }
        val source = file.readText()
        val decoded = source.substringAfter("private val openSettingsArchive").substringBefore("override fun onCreate")
        assertTrue(decoded.contains("SettingsArchive.decode(text)"))
        assertTrue(decoded.contains("owner.finishSettingsImport(ticket) { acceptSettingsArchive(decoded) }"))
        assertTrue(decoded.indexOf("runOnUiThread") < decoded.indexOf("owner.finishSettingsImport"))
        assertFalse(decoded.contains("SettingsArchive.merge("))
        assertFalse(decoded.contains("editor.commit()"))
        val accepted = source.substringAfter("private fun acceptSettingsArchive(").substringBefore("private val openSettingsArchive")
        assertTrue(accepted.contains("if (isFinishing || isDestroyed) return"))
        assertTrue(accepted.contains("settingsWriteOwner.write"))
        assertTrue(accepted.indexOf("SettingsArchive.merge") < accepted.indexOf("editor.commit()"))
        assertTrue(accepted.contains("rollback = { restorePreferenceSnapshots(priorValues) }"))
        val edit = source.substringAfter("private fun applyLight(").substringBefore("override fun rollLight()")
        assertTrue(edit.contains("settingsWriteOwner.write"))
        assertTrue(edit.contains("reportTuningWriteFailure(failure)"))
        assertTrue(edit.contains("rollback = { restorePreferenceSnapshots(prior) }"))
    }
}
