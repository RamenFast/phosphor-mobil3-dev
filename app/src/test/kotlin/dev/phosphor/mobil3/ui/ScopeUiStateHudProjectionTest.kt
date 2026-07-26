package dev.phosphor.mobil3.ui

import dev.phosphor.mobil3.state.ActionRequest
import dev.phosphor.mobil3.state.Capability
import dev.phosphor.mobil3.state.FrozenMap
import dev.phosphor.mobil3.state.InitialSnapshots
import dev.phosphor.mobil3.state.PrincipalId
import dev.phosphor.mobil3.state.PrincipalKind
import dev.phosphor.mobil3.state.Refusal
import dev.phosphor.mobil3.state.RefusalCode
import dev.phosphor.mobil3.state.SetDisplayHud
import dev.phosphor.mobil3.state.Transport
import dev.phosphor.mobil3.state.frozenSetOf
import dev.phosphor.mobil3.store.LOCAL_HUMAN_PRINCIPAL_ID
import dev.phosphor.mobil3.store.PhosphorDispatchResult
import dev.phosphor.mobil3.store.PhosphorStatePersistencePort
import dev.phosphor.mobil3.store.PhosphorStateStore
import dev.phosphor.mobil3.store.PhosphorStoreImage
import dev.phosphor.mobil3.store.PhosphorStoreLoadResult
import dev.phosphor.mobil3.store.PhosphorStorePersistenceResult
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Test

class ScopeUiStateHudProjectionTest {
    private val human = PrincipalId(PrincipalKind.HUMAN, LOCAL_HUMAN_PRINCIPAL_ID)

    private fun base() = InitialSnapshots.localDevelopment(10L).let { base ->
        base.copy(
            desired = base.desired.copy(displayHud = "off"),
            effective = base.effective.copy(displayHud = "off"),
            capabilities = FrozenMap.copyOf(mapOf(human to frozenSetOf(Capability.CONTROL_DISPLAY))),
        )
    }

    @Test fun hudModeIsAStoreProjectionAndUpdatesFromAcceptedSnapshot() {
        val port = ProjectionPort(PhosphorStoreLoadResult.CausalImage(PhosphorStoreImage(base())))
        val store = PhosphorStateStore(port, base())
        val ui = ScopeUiState(store)
        assertEquals(2, ui.hudMode)

        val result = store.dispatch(
            SetDisplayHud("on"),
            ActionRequest(human, "ui-projection", 0L, "test projection", Capability.CONTROL_DISPLAY, Transport.UI),
            1L,
            20L,
        )

        assertIs<PhosphorDispatchResult.Accepted>(result)
        assertEquals(0, ui.hudMode)
        assertSame(store.snapshot, port.saved.single().snapshot)
    }

    @Test fun persistenceFailureKeepsProjectedHudAndDisablesControlWithFix() {
        val refusal = Refusal(RefusalCode.SYSTEM_UNAVAILABLE, "Make local storage writable.", 0L)
        val port = ProjectionPort(
            PhosphorStoreLoadResult.CausalImage(PhosphorStoreImage(base())),
            failure = refusal,
        )
        val store = PhosphorStateStore(port, base())
        val ui = ScopeUiState(store)

        assertIs<PhosphorDispatchResult.Failed>(
            store.dispatch(
                SetDisplayHud("on"),
                ActionRequest(human, "ui-failure", 0L, "test failure", Capability.CONTROL_DISPLAY, Transport.UI),
                1L,
                20L,
            ),
        )

        assertEquals(2, ui.hudMode)
        assertFalse(ui.hudControlWritable)
        assertTrue(ui.hudControlFix.contains("storage"))
    }
}

private class ProjectionPort(
    private val loadResult: PhosphorStoreLoadResult,
    private val failure: Refusal? = null,
) : PhosphorStatePersistencePort {
    val saved = mutableListOf<PhosphorStoreImage>()

    override fun load(): PhosphorStoreLoadResult = loadResult

    override fun save(image: PhosphorStoreImage): PhosphorStorePersistenceResult {
        failure?.let { return PhosphorStorePersistenceResult.Failed(it) }
        saved += image
        return PhosphorStorePersistenceResult.Saved
    }
}
