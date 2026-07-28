package dev.phosphor.mobil3.nexus.tailnet

import dev.phosphor.mobil3.store.PhosphorStateStore
import java.util.concurrent.atomic.AtomicReference

/**
 * Single later-integration hook for Phosphor Fortress tailnet runtime.
 *
 * Safe defaults are inert: install(null) and shutdown() are no-ops. Integration
 * should construct config only from seededRemoteHosts/user preferences and pass
 * the one process-wide [SharedNexusRuntimeAuthority] plus a thin dispatcher adapter.
 * Tailnet must never allocate a separate NexusObservationDispatcher/session/grant
 * authority from this bootstrap.
 */
object PhosphorTailnetRuntimeBootstrap {
    private val runtime = AtomicReference<PhosphorTailnetRuntime?>()

    fun install(
        config: TailnetRuntimeConfig?,
        sharedAuthority: SharedNexusRuntimeAuthority?,
        logger: TailnetLogger = NoopTailnetLogger,
    ) {
        shutdown()
        if (config == null || sharedAuthority == null) return
        runtime.set(PhosphorTailnetRuntime(config, sharedAuthority, logger))
    }

    fun installProduction(
        config: TailnetRuntimeConfig?,
        store: PhosphorStateStore?,
        logger: TailnetLogger = NoopTailnetLogger,
    ) {
        install(config, store?.let { TailnetNexusAuthorityAdapter.production(it) }, logger)
    }

    fun start() {
        runtime.get()?.start()
    }

    fun shutdown() {
        runtime.getAndSet(null)?.shutdown()
    }
}
