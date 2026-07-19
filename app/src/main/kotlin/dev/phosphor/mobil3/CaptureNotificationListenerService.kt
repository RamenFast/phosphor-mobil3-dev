package dev.phosphor.mobil3

import android.content.Intent
import android.service.notification.NotificationListenerService

/**
 * Android's explicit notification-listener access gate for active media sessions.
 * Notification contents stay with Android; PlaybackService reads only MediaSession state.
 */
class CaptureNotificationListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        notifyPlaybackService(true)
    }

    override fun onListenerDisconnected() {
        notifyPlaybackService(false)
        super.onListenerDisconnected()
    }

    private fun notifyPlaybackService(granted: Boolean) {
        runCatching {
            startService(
                Intent(this, PlaybackService::class.java)
                    .setAction(PlaybackService.ACTION_CAPTURE_ACCESS_CHANGED)
                    .putExtra(PlaybackService.EXTRA_CAPTURE_ACCESS_GRANTED, granted)
            )
        }
    }
}
