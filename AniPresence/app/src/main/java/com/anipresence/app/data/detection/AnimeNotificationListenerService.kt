package com.anipresence.app.data.detection

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.anipresence.app.AniPresenceApplication

class AnimeNotificationListenerService : NotificationListenerService() {
    private val repository: AndroidMediaDetectionRepository
        get() = (application as AniPresenceApplication).detectionRepository

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        repository.onListenerConnected()
        activeNotifications?.forEach(repository::onMediaNotification)
    }

    override fun onListenerDisconnected() {
        repository.onListenerDisconnected()
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        repository.onMediaNotification(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        repository.onNotificationRemoved(sbn)
    }

    private fun publishSnapshot() {
        runCatching { activeNotifications?.forEach(repository::onMediaNotification) }
    }

    companion object {
        @Volatile private var instance: AnimeNotificationListenerService? = null

        fun requestSnapshot() {
            instance?.publishSnapshot()
        }

        fun requestReconnect(componentName: ComponentName) {
            requestRebind(componentName)
        }
    }
}
