package dev.phosphor.mobil3.nexus.binder

import android.app.Service
import android.content.Intent
import android.os.IBinder

class NexusBinderService : Service() {
    private val binder by lazy {
        object : IPhosphorNexusBinder.Stub() {
            private val handler by lazy {
                NexusBinderRequestHandler(
                    ownerProvider = {
                        NexusBinderRuntimeRegistry.owner(this@NexusBinderService)
                            ?: NexusBinderBootstrap.owner(this@NexusBinderService)
                    },
                    callerVerifier = NexusBinderCallerVerifier(this@NexusBinderService),
                )
            }
            override fun transact(requestJson: String?, clientToken: IBinder?): String {
                if (checkCallingPermission(NEXUS_BINDER_PERMISSION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    return errorJson(NexusBinderErrorCode.UNAUTHORIZED_CALLER, detail = "Caller does not hold the Fortress signature Binder permission.")
                }
                return handler.handle(requestJson, clientToken)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        if (intent?.action != NEXUS_BINDER_ACTION) return null
        return binder
    }
}
