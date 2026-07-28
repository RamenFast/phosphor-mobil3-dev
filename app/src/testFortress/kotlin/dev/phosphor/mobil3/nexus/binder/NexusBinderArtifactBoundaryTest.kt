package dev.phosphor.mobil3.nexus.binder

import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class NexusBinderArtifactBoundaryTest {
    @Test fun fortressManifestDeclaresSignatureProtectedExportedService() {
        val manifest = File("src/fortress/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:protectionLevel=\"signature\""))
        assertTrue(manifest.contains("android:name=\"$NEXUS_BINDER_PERMISSION\""))
        assertTrue(manifest.contains("android:name=\"$NEXUS_BINDER_SERVICE\""))
        assertTrue(manifest.contains("android:exported=\"true\""))
        assertTrue(manifest.contains("android:permission=\"$NEXUS_BINDER_PERMISSION\""))
        assertTrue(manifest.contains("android:name=\"$NEXUS_BINDER_ACTION\""))
    }

    @Test fun playSourcesDoNotContainBinderSurface() {
        val playRoot = File("src/play")
        val haystack = playRoot.walkTopDown().filter { it.isFile }.joinToString("\n") { it.readText() }
        assertFalse(haystack.contains("NexusBinder"))
        assertFalse(haystack.contains(NEXUS_BINDER_PERMISSION))
        assertFalse(haystack.contains(NEXUS_BINDER_ACTION))
    }

    @Test fun aidlSurfaceStaysOneJsonMethod() {
        val aidl = File("src/fortress/aidl/dev/phosphor/mobil3/nexus/binder/IPhosphorNexusBinder.aidl").readText()
        assertTrue(aidl.contains("String transact(String requestJson, IBinder clientToken);"))
        assertFalse(aidl.contains("challenge("))
        assertFalse(aidl.contains("authenticate("))
        assertFalse(aidl.contains("observe("))
        assertFalse(aidl.contains("dispatch("))
    }

    @Test fun fortressBinderReleaseSignerDefaultsToEstateCertNotDebug() {
        val build = File("build.gradle.kts").readText()
        assertTrue(build.contains("e4d14ce2d62983acd393f012cbce759b6c97bdcca979feeb04a97afb279d9b00"))
        val defaultLine = build.lineSequence().dropWhile { !it.contains("NEXUS_BINDER_SIGNING_LINEAGE") }.take(3).joinToString("\n")
        assertFalse(defaultLine.contains("f8dfcf73312022dfe8096c8e4c28b1d81199e0c6ce9c73c4394789fe9614632d"))
    }

    @Test fun binderHeartbeatDoesNotMutateTailnetHeartbeatFields() {
        val manager = File("src/fortress/kotlin/dev/phosphor/mobil3/nexus/NexusRuntimeManager.kt").readText()
        val bootstrap = File("src/fortress/kotlin/dev/phosphor/mobil3/nexus/binder/NexusBinderBootstrap.kt").readText()
        assertTrue(manager.contains("fun confirmBinderAlive"))
        assertFalse(manager.contains("recordHeartbeat("))
        assertFalse(manager.contains(".heartbeat(now"))
        assertTrue(bootstrap.contains("confirmBinderAlive"))
        assertFalse(bootstrap.contains("session.heartbeat"))
        assertFalse(bootstrap.contains("recordHeartbeat"))
    }

    @Test fun activeBinderVerbsRequireSessionTokenGenerationProof() {
        val bootstrap = File("src/fortress/kotlin/dev/phosphor/mobil3/nexus/binder/NexusBinderBootstrap.kt").readText()
        assertTrue(bootstrap.contains("request.payload.optString(\"session_id\")"))
        assertTrue(bootstrap.contains("request.payload.optString(\"token_id\")"))
        assertTrue(bootstrap.contains("request.payload.optLong(\"generation\""))
        assertTrue(bootstrap.contains("Retry with the current Binder session_id, token_id, and generation."))
    }

    @Test fun binderResponsesAreBoundedToClientLimit() {
        val protocol = File("src/fortress/kotlin/dev/phosphor/mobil3/nexus/binder/NexusBinderProtocol.kt").readText()
        val runtime = File("src/fortress/kotlin/dev/phosphor/mobil3/nexus/binder/NexusBinderRuntime.kt").readText()
        assertTrue(protocol.contains("MAX_BINDER_RESPONSE_BYTES = 256 * 1024"))
        assertTrue(protocol.contains("RESPONSE_OVERSIZED"))
        assertTrue(runtime.contains("boundResponse"))
        assertTrue(runtime.contains("MAX_BINDER_RESPONSE_BYTES"))
    }

    @Test fun attachBinderRefusesWhenPreviousCloseCannotPersist() {
        val manager = File("src/fortress/kotlin/dev/phosphor/mobil3/nexus/NexusRuntimeManager.kt").readText()
        val bootstrap = File("src/fortress/kotlin/dev/phosphor/mobil3/nexus/binder/NexusBinderBootstrap.kt").readText()
        assertTrue(manager.contains("session.id != null && !closeCurrentLocked"))
        assertTrue(manager.contains("throw IllegalStateException"))
        assertTrue(bootstrap.indexOf("manager.attachBinder") < bootstrap.indexOf("session = established"))
        assertTrue(bootstrap.indexOf("manager.attachBinder") < bootstrap.indexOf("token = nextToken"))
        assertTrue(bootstrap.indexOf("manager.attachBinder") < bootstrap.indexOf("grants = nextGrants"))
    }

    @Test fun challengeStorageIsBoundedAndPruned() {
        val bootstrap = File("src/fortress/kotlin/dev/phosphor/mobil3/nexus/binder/NexusBinderBootstrap.kt").readText()
        assertTrue(bootstrap.contains("MAX_BINDER_CHALLENGES = 64"))
        assertTrue(bootstrap.contains("pruneChallenges(nowWall)"))
        assertTrue(bootstrap.contains("while (challenges.size > MAX_BINDER_CHALLENGES)"))
        assertTrue(bootstrap.contains("expiresAtMillis <= nowWallTimeMillis"))
    }

    @Test fun binderAuthProfileGateRunsBeforeNonceConsumption() {
        val bootstrap = File("src/fortress/kotlin/dev/phosphor/mobil3/nexus/binder/NexusBinderBootstrap.kt").readText()
        assertTrue(bootstrap.contains("requiredBinderAuthProfile(BuildConfig.DEBUG)"))
        assertTrue(bootstrap.contains("parseBinderAuthProfile(payload, requiredProfile)"))
        assertTrue(bootstrap.contains("payload.has(\"profile\")"))
        assertTrue(bootstrap.contains("Unsupported Binder auth profile"))
        assertTrue(bootstrap.contains("Binder auth profile must be"))
        assertTrue(bootstrap.indexOf("requiredBinderAuthProfile(BuildConfig.DEBUG)") < bootstrap.indexOf("consumeChallengeNonce"))
        assertTrue(bootstrap.indexOf("parseBinderAuthProfile(payload, requiredProfile)") < bootstrap.indexOf("consumeChallengeNonce"))
        assertTrue(bootstrap.indexOf("profile = requiredProfile") < bootstrap.indexOf("consumeChallengeNonce"))
        assertFalse(bootstrap.contains("?: dev.phosphor.mobil3.nexus.NexusBuildProfile.NEXUS"))
    }
}
