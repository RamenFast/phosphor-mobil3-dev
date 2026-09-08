package dev.phosphor.mobil3

import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RootAuthorizationCommandTest {
    private open class FinishedProcess(
        stdout: String = "0\n",
        stderr: String = "",
        private val code: Int = 0,
        private val broken: Boolean = false,
    ) : Process() {
        private val out = ByteArrayInputStream(stdout.toByteArray())
        private val err = ByteArrayInputStream(stderr.toByteArray())
        override fun getInputStream(): InputStream = if (!broken) out else object : InputStream() {
            override fun read(): Int = throw IOException("fixture pipe failure")
        }
        override fun getErrorStream() = err
        override fun getOutputStream() = ByteArrayOutputStream()
        override fun waitFor() = code
        override fun waitFor(timeout: Long, unit: TimeUnit) = true
        override fun exitValue() = code
        override fun destroy() = Unit
        override fun isAlive() = false
    }

    @Test fun onlyFixedIdentityCommandCanBeLaunched() {
        val calls = mutableListOf<List<String>>()
        val result = RootAuthorizationCommand.run { calls += it; FinishedProcess() }
        assertEquals(listOf(listOf("su", "-c", "/system/bin/id -u")), calls)
        assertTrue(result.granted)
        assertTrue(result.cleanupConfirmed)
    }

    @Test fun unavailableCandidatesAreBoundedAndExplicit() {
        val calls = mutableListOf<List<String>>()
        val result = RootAuthorizationCommand.run { calls += it; throw IOException("error=2, not found") }
        assertEquals("su_unavailable", result.outcome)
        assertEquals(listOf("su", "/system/bin/su", "/system/xbin/su", "/sbin/su", "/debug_ramdisk/su"), result.attempts)
        assertTrue(calls.all { it.drop(1) == listOf("-c", "/system/bin/id -u") })
    }

    @Test fun missingFirstCandidateCanReachFixedAbsoluteLocation() {
        val result = RootAuthorizationCommand.run {
            if (it.first() == "su") throw IOException("error=2, not found") else FinishedProcess()
        }
        assertTrue(result.granted)
        assertEquals(listOf("su", "/system/bin/su"), result.attempts)
    }

    @Test fun denialDoesNotTryAnotherEntryPoint() {
        var calls = 0
        val result = RootAuthorizationCommand.run { calls++; FinishedProcess(stderr = "denied", code = 1) }
        assertEquals(1, calls)
        assertEquals("authorization_failed", result.outcome)
        assertFalse(result.granted)
    }

    @Test fun processSecurityDenialIsNotMissingExecutable() {
        val result = RootAuthorizationCommand.run { throw SecurityException("denied") }
        assertEquals("launch_denied", result.outcome)
        assertEquals(listOf("su"), result.attempts)
    }

    @Test fun permissionIoFailureDoesNotAdvanceToAnotherExecutable() {
        val result = RootAuthorizationCommand.run { throw IOException("error=13, Permission denied") }
        assertEquals("launch_failed", result.outcome)
        assertEquals(listOf("su"), result.attempts)
        assertEquals(listOf("error=13, Permission denied"), result.launchErrors)
    }

    @Test fun nonRootMalformedOrNonzeroExitNeverProvesRoot() {
        for (identity in listOf("", "2000\n", "10401\n", "00\n", "uid=0(root)", "0\nextra")) {
            assertFalse(RootAuthorizationCommand.run { FinishedProcess(stdout = identity) }.granted, identity)
        }
        assertFalse(RootAuthorizationCommand.run { FinishedProcess(code = 1) }.granted)
    }

    @Test fun outputOverflowCannotMasqueradeAsIdentity() {
        val result = RootAuthorizationCommand.run { FinishedProcess(stdout = "0" + " ".repeat(8_192)) }
        assertEquals("output_limit", result.outcome)
        assertEquals(4_096, result.stdout.length)
        assertFalse(result.granted)
        val error = RootAuthorizationCommand.run { FinishedProcess(stderr = "x".repeat(8_192)) }
        assertEquals("output_limit", error.outcome)
        assertEquals(4_096, error.stderr.length)
    }

    @Test fun brokenPipeCannotProveIdentity() {
        assertEquals("read_failed", RootAuthorizationCommand.run { FinishedProcess(broken = true) }.outcome)
    }

    @Test fun actualProcessPipesJoinBeforeSuccess() {
        // Only a host test fixture, never an app-supplied command or root invocation.
        val child = ProcessBuilder("/bin/sh", "-c", "printf '0\\n'").start()
        val result = RootAuthorizationCommand.observe(child)
        assertTrue(result.granted)
        assertTrue(result.cleanupConfirmed)
        assertFalse(child.isAlive)
    }

    @Test fun realBlockedChildIsTerminatedWithinTheBound() {
        val child = ProcessBuilder("/bin/sh", "-c", "exec sleep 10").start()
        val start = System.nanoTime()
        val result = RootAuthorizationCommand.observe(child, timeoutMs = 30)
        assertEquals("authorization_timeout", result.outcome)
        assertTrue(result.cleanupConfirmed)
        assertFalse(child.isAlive)
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 2_000)
    }

    @Test fun receiverRejectsMissingOrUnknownActions() {
        assertTrue(SelfTestReceiver.accepts("dev.phosphor.mobil3.SELFTEST"))
        assertTrue(SelfTestReceiver.accepts("dev.phosphor.mobil3.ROOT_AUTH_PROBE"))
        assertFalse(SelfTestReceiver.accepts(null))
        assertFalse(SelfTestReceiver.accepts("su"))
    }

    @Test fun exitedChildWithUnfinishedPipeCannotProveRoot() {
        val unblock = CountDownLatch(1)
        val readerDone = CountDownLatch(1)
        val child = object : FinishedProcess() {
            override fun getInputStream() = object : InputStream() {
                override fun read(): Int {
                    try { unblock.await(); return -1 } finally { readerDone.countDown() }
                }
            }
        }
        try {
            val result = RootAuthorizationCommand.observe(child)
            assertEquals("cleanup_unconfirmed", result.outcome)
            assertFalse(result.granted)
            assertFalse(result.cleanupConfirmed)
        } finally {
            unblock.countDown()
            assertTrue(readerDone.await(1, TimeUnit.SECONDS))
        }
    }
}
