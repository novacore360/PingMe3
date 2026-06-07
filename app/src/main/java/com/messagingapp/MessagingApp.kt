package com.messagingapp

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class MessagingApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            // Silent foreground-service channel
            val serviceChannel = NotificationChannel(
                SERVICE_CHANNEL_ID,
                "Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps message delivery running in background"
                setShowBadge(false)
            }

            // High-priority channel for incoming messages — badge enabled
            val msgChannel = NotificationChannel(
                CHANNEL_ID,
                "New Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for new messages"
                enableVibration(true)
                enableLights(true)
                setShowBadge(true) // ← enables launcher icon badge
            }

            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(msgChannel)
        }
    }

    companion object {
        const val CHANNEL_ID = "messages_channel"
        const val SERVICE_CHANNEL_ID = "service_channel"
    }
}
