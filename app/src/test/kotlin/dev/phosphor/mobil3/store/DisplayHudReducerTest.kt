package dev.phosphor.mobil3.store

import dev.phosphor.mobil3.state.ActionRequest
import dev.phosphor.mobil3.state.AuditRetentionPolicy
import dev.phosphor.mobil3.state.Capability
import dev.phosphor.mobil3.state.FrozenList
import dev.phosphor.mobil3.state.FrozenMap
import dev.phosphor.mobil3.state.IDEMPOTENCY_TTL_MILLIS
import dev.phosphor.mobil3.state.IdempotencyIndexPolicy
import dev.phosphor.mobil3.state.InitialSnapshots
import dev.phosphor.mobil3.state.PhosphorAction
import dev.phosphor.mobil3.state.PrincipalId
import dev.phosphor.mobil3.state.PrincipalKind
import dev.phosphor.mobil3.state.Refusal
import dev.phosphor.mobil3.state.RefusalCode
import dev.phosphor.mobil3.state.RequestSettingsExport
import dev.phosphor.mobil3.state.SetDisplayHud
import dev.phosphor.mobil3.state.SetFullscreen
import dev.phosphor.mobil3.state.StateField
import dev.phosphor.mobil3.state.Transport
import dev.phosphor.mobil3.state.frozenSetOf
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Test

class DisplayHudReducerTest {
    private val human = PrincipalId(PrincipalKind.HUMAN, LOCAL_HUMAN_PRINCIPAL_ID)
    private val migration = PrincipalId(PrincipalKind.MIGRATION, LOCAL_HUD_MIGRATION_PRINCIPAL_ID)
    private val nexus = PrincipalId(PrincipalKind.NEXUS, "nexus")

    private fun snapshot(mode: String = "auto") = InitialSnapshots.play(1_000L).copy(
        desired = InitialSnapshots.play(1_000L).desired.copy(displayHud = mode, fullscreen = true, sourceKind = "file"),
        effective = InitialSnapshots.play(1_000L).effective.copy(displayHud = mode, fullscreen = true, sourceKind = "file"),
        capabilities = FrozenMap.copyOf(mapOf(human to frozenSetOf(Capability.CONTROL_DISPLAY), migration to frozenSetOf(Capability.CONTROL_DISPLAY))),
    )

    private fun request(
        principal: PrincipalId = human,
        key: String = "k",
        expected: Long = 0L,
        capability: Capability = Capability.CONTROL_DISPLAY,
        transport: Transport = Transport.UI,
        sessionId: String? = null,
    ) = ActionRequest(principal, key, expected, "test change", capability, transport, sessionId)

    private fun reduce(action: PhosphorAction, image: PhosphorStoreImage = PhosphorStoreImage(snapshot()), request: ActionRequest = request(), receipt: String = "r1", wall: Long = 2_000L): DisplayHudReduction =
        DisplayHudReducer(DisplayHudReducerConfig(AuditRetentionPolicy(3, IDEMPOTENCY_TTL_MILLIS), IdempotencyIndexPolicy(maximumRecords = 2)))
            .reduce(action, request, image, 10L, wall, receipt, 1L)

    @Test fun acceptedChangeReusesOneStampAndKeepsNonHudFields() {
        val previous = snapshot("auto")
        val result = assertIs<DisplayHudReduction.Accepted>(reduce(SetDisplayHud("on"), PhosphorStoreImage(previous)))
        assertEquals("on", result.snapshot.effective.displayHud)
        assertEquals(previous.effective.copy(displayHud = "on"), result.snapshot.effective)
        assertEquals(previous.desired.copy(displayHud = "on"), result.snapshot.desired)
        assertEquals(previous.effective.fullscreen, result.snapshot.effective.fullscreen)
        assertEquals(previous.effective.sourceKind, result.snapshot.effective.sourceKind)
        val stamp = result.acknowledgement.provenance
        assertSame(stamp, result.snapshot.provenance[StateField.DISPLAY_HUD])
        assertSame(stamp, result.audit.provenance)
        assertSame(stamp, result.acknowledgement.delta!!.provenance)
        assertSame(stamp, result.acknowledgement.delta!!.changes.single().provenance)
        assertEquals(1L, result.snapshot.revision)
        assertEquals(1L, result.snapshot.sequence)
    }

    @Test fun noOpRetainsSnapshotAndRevision() {
        val previous = snapshot("auto")
        val result = assertIs<DisplayHudReduction.Accepted>(reduce(SetDisplayHud("auto"), PhosphorStoreImage(previous)))
        assertSame(previous, result.snapshot)
        assertEquals(false, result.acknowledgement.changed)
        assertEquals(0L, result.acknowledgement.revision)
        assertEquals(null, result.acknowledgement.delta)
    }

    @Test fun staleRevisionIsFixBearingRefusal() {
        val result = assertIs<DisplayHudReduction.Refused>(reduce(SetDisplayHud("on"), request = request(expected = 9L)))
        assertEquals(RefusalCode.REVISION_CONFLICT, result.refusal.code)
        assertTrue(result.refusal.fix.contains("revision 0"))
    }

    @Test fun invalidActionAndInvalidModeAreRefused() {
        assertEquals(RefusalCode.INVALID_REQUEST, assertIs<DisplayHudReduction.Refused>(reduce(SetFullscreen(true))).refusal.code)
        val invalid = assertIs<DisplayHudReduction.Refused>(reduce(SetDisplayHud("yes")))
        assertEquals(RefusalCode.INVALID_VALUE, invalid.refusal.code)
        assertTrue(invalid.refusal.fix.contains("auto, on, or off"))
    }

    @Test fun capabilityAndPrincipalAreChecked() {
        val withoutGrant = snapshot().copy(capabilities = FrozenMap.copyOf(emptyMap()))
        assertEquals(
            RefusalCode.CAPABILITY_NOT_GRANTED,
            assertIs<DisplayHudReduction.Refused>(
                reduce(SetDisplayHud("on"), image = PhosphorStoreImage(withoutGrant), request = request(principal = human)),
            ).refusal.code,
        )
        assertEquals(RefusalCode.SESSION_UNAVAILABLE, assertIs<DisplayHudReduction.Refused>(reduce(SetDisplayHud("on"), request = request(principal = nexus, transport = Transport.TAILNET))).refusal.code)
        assertIs<DisplayHudReduction.Accepted>(reduce(SetDisplayHud("on"), request = request(principal = migration, transport = Transport.MIGRATION)))
    }

    @Test fun adversarialPrincipalTransportPairingMustBeExact() {
        val humanMigration = assertIs<DisplayHudReduction.Refused>(reduce(SetDisplayHud("on"), request = request(principal = human, transport = Transport.MIGRATION)))
        assertEquals(RefusalCode.INVALID_REQUEST, humanMigration.refusal.code)
        assertTrue(humanMigration.refusal.fix.contains("HUMAN"))

        val migrationUi = assertIs<DisplayHudReduction.Refused>(reduce(SetDisplayHud("on"), request = request(principal = migration, transport = Transport.UI)))
        assertEquals(RefusalCode.INVALID_REQUEST, migrationUi.refusal.code)
        assertTrue(migrationUi.refusal.fix.contains("MIGRATION"))
    }

    @Test fun localHudSliceRejectsSessionBearingRequests() {
        val result = assertIs<DisplayHudReduction.Refused>(
            reduce(SetDisplayHud("on"), request = request(sessionId = "nexus-session")),
        )
        assertEquals(RefusalCode.INVALID_REQUEST, result.refusal.code)
        assertTrue(result.refusal.fix.contains("no session id"))
    }

    @Test fun adversarialPrincipalStableIdsMustBeTheDeclaredLocalIdentities() {
        val otherHuman = PrincipalId(PrincipalKind.HUMAN, "other-human")
        val otherMigration = PrincipalId(PrincipalKind.MIGRATION, "other-migration")
        val granted = snapshot().copy(
            capabilities = FrozenMap.copyOf(
                mapOf(
                    otherHuman to frozenSetOf(Capability.CONTROL_DISPLAY),
                    otherMigration to frozenSetOf(Capability.CONTROL_DISPLAY),
                ),
            ),
        )

        val humanResult = assertIs<DisplayHudReduction.Refused>(
            reduce(SetDisplayHud("on"), image = PhosphorStoreImage(granted), request = request(principal = otherHuman)),
        )
        val migrationResult = assertIs<DisplayHudReduction.Refused>(
            reduce(
                SetDisplayHud("on"),
                image = PhosphorStoreImage(granted),
                request = request(principal = otherMigration, transport = Transport.MIGRATION),
            ),
        )
        assertEquals(RefusalCode.INVALID_REQUEST, humanResult.refusal.code)
        assertEquals(RefusalCode.INVALID_REQUEST, migrationResult.refusal.code)
        assertTrue(humanResult.refusal.fix.contains("stable IDs"))
    }

    @Test fun adversarialDesiredEffectiveDivergenceIsFixBearingRefusal() {
        val divergent = snapshot("off").copy(desired = snapshot("off").desired.copy(displayHud = "on"))
        val result = assertIs<DisplayHudReduction.Refused>(reduce(SetDisplayHud("auto"), image = PhosphorStoreImage(divergent)))
        assertEquals(RefusalCode.SYSTEM_UNAVAILABLE, result.refusal.code)
        assertTrue(result.refusal.fix.contains("desired/effective divergence"))
    }

    @Test fun adversarialReplayCannotBypassRevokedCapability() {
        val first = assertIs<DisplayHudReduction.Accepted>(reduce(SetDisplayHud("on")))
        val index = DisplayHudReducer().appendIdempotency(emptyList(), first.idempotencyRecord, 2_000L)
        val revoked = snapshot("on").copy(capabilities = FrozenMap.copyOf(emptyMap()))
        val replay = assertIs<DisplayHudReduction.Refused>(reduce(SetDisplayHud("on"), image = PhosphorStoreImage(revoked, idempotency = index, nextIdempotencyOrdinal = 2L)))
        assertEquals(RefusalCode.CAPABILITY_NOT_GRANTED, replay.refusal.code)
    }

    @Test fun replayConflictAndFullIdempotencyAreBounded() {
        val first = assertIs<DisplayHudReduction.Accepted>(reduce(SetDisplayHud("on")))
        val image = PhosphorStoreImage(snapshot("on"), idempotency = DisplayHudReducer(DisplayHudReducerConfig(idempotencyPolicy = IdempotencyIndexPolicy(maximumRecords = 2))).appendIdempotency(emptyList(), first.idempotencyRecord, 2_000L), nextIdempotencyOrdinal = 2L)
        val replay = assertIs<DisplayHudReduction.Replayed>(reduce(SetDisplayHud("on"), image = image))
        assertSame(first.acknowledgement, replay.acknowledgement)
        val conflict = assertIs<DisplayHudReduction.Refused>(reduce(SetDisplayHud("off"), image = image))
        assertEquals(RefusalCode.IDEMPOTENCY_CONFLICT, conflict.refusal.code)
        assertEquals(first.acknowledgement.receiptId, conflict.refusal.originalReceiptId)

        val second = assertIs<DisplayHudReduction.Accepted>(reduce(SetDisplayHud("off"), request = request(key = "k2"), receipt = "r2"))
        val fullIndex = DisplayHudReducer(DisplayHudReducerConfig(idempotencyPolicy = IdempotencyIndexPolicy(maximumRecords = 2)))
            .appendIdempotency(listOf(first.idempotencyRecord), second.idempotencyRecord, 2_000L)
        val fullReplay = assertIs<DisplayHudReduction.Replayed>(
            reduce(
                SetDisplayHud("on"),
                image = PhosphorStoreImage(snapshot("off"), idempotency = fullIndex, nextIdempotencyOrdinal = 3L),
            ),
        )
        assertSame(first.acknowledgement, fullReplay.acknowledgement)
        val full = assertIs<DisplayHudReduction.Refused>(reduce(SetDisplayHud("auto"), image = PhosphorStoreImage(snapshot("off"), idempotency = fullIndex, nextIdempotencyOrdinal = 3L), request = request(key = "k3")))
        assertEquals(RefusalCode.IDEMPOTENCY_INDEX_FULL, full.refusal.code)
    }

    @Test fun expiredIdempotencyKeyCanBeReusedAfter24Hours() {
        val first = assertIs<DisplayHudReduction.Accepted>(reduce(SetDisplayHud("on")))
        val idx = DisplayHudReducer(DisplayHudReducerConfig(idempotencyPolicy = IdempotencyIndexPolicy(maximumRecords = 1))).appendIdempotency(emptyList(), first.idempotencyRecord, 2_000L)
        val reused = reduce(SetDisplayHud("off"), image = PhosphorStoreImage(snapshot("on"), idempotency = idx, nextIdempotencyOrdinal = 2L), wall = 2_000L + IDEMPOTENCY_TTL_MILLIS + 1L)
        assertIs<DisplayHudReduction.Accepted>(reused)
    }

    @Test fun adversarialIdempotencyTtlBoundaryIsExclusiveAtExpiry() {
        val first = assertIs<DisplayHudReduction.Accepted>(reduce(SetDisplayHud("on")))
        val idx = DisplayHudReducer(DisplayHudReducerConfig(idempotencyPolicy = IdempotencyIndexPolicy(maximumRecords = 1))).appendIdempotency(emptyList(), first.idempotencyRecord, 2_000L)
        val justBeforeExpiry = assertIs<DisplayHudReduction.Replayed>(reduce(SetDisplayHud("on"), image = PhosphorStoreImage(snapshot("on"), idempotency = idx, nextIdempotencyOrdinal = 2L), wall = 2_000L + IDEMPOTENCY_TTL_MILLIS - 1L))
        assertSame(first.acknowledgement, justBeforeExpiry.acknowledgement)
        assertIs<DisplayHudReduction.Accepted>(reduce(SetDisplayHud("off"), image = PhosphorStoreImage(snapshot("on"), idempotency = idx, nextIdempotencyOrdinal = 2L), wall = 2_000L + IDEMPOTENCY_TTL_MILLIS))
    }

    @Test fun adversarialCausalCounterRewindIsRejectedByImage() {
        val first = assertIs<DisplayHudReduction.Accepted>(reduce(SetDisplayHud("on"), receipt = "hud-1"))
        val audit = DisplayHudReducer().appendAudit(emptyList(), first.audit, 2_000L)
        assertFailsWith<IllegalArgumentException> {
            PhosphorStoreImage(snapshot("on"), audit = audit, nextReceiptOrdinal = 1L)
        }
        val index = DisplayHudReducer().appendIdempotency(emptyList(), first.idempotencyRecord, 2_000L)
        assertFailsWith<IllegalArgumentException> {
            PhosphorStoreImage(snapshot("on"), idempotency = index, nextIdempotencyOrdinal = 1L)
        }
    }

    @Test fun auditOverflowKeepsNewestBoundedRecords() {
        val reducer = DisplayHudReducer(DisplayHudReducerConfig(auditPolicy = AuditRetentionPolicy(2, IDEMPOTENCY_TTL_MILLIS)))
        val one = assertIs<DisplayHudReduction.Accepted>(reducer.reduce(SetDisplayHud("on"), request(), PhosphorStoreImage(snapshot()), 1L, 2_000L, "r1", 1L)).audit
        val two = assertIs<DisplayHudReduction.Accepted>(reducer.reduce(SetDisplayHud("off"), request(key = "k2"), PhosphorStoreImage(snapshot("on")), 2L, 2_001L, "r2", 2L)).audit
        val three = assertIs<DisplayHudReduction.Accepted>(reducer.reduce(SetDisplayHud("auto"), request(key = "k3"), PhosphorStoreImage(snapshot("off")), 3L, 2_002L, "r3", 3L)).audit
        val audit = reducer.appendAudit(listOf(one, two), three, 2_002L)
        assertEquals(listOf("r2", "r3"), audit.records.map { it.receiptId })
    }
}
