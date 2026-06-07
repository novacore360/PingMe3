package com.messagingapp.service

import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import com.messagingapp.MainActivity
import com.messagingapp.MessagingApp
import com.messagingapp.data.repository.AuthRepository
import com.messagingapp.data.repository.MessageRepository
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger

class MessageNotificationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val authRepo = AuthRepository()
    private val msgRepo = MessageRepository()
    private val notifCounter = AtomicInteger(1001)
    // Track unread per sender for badge grouping
    private val senderUnread = mutableMapOf<String, Int>()

    override fun onCreate() {
        super.onCreate()
        startForeground(FOREGROUND_ID, buildForegroundNotification())
        listenForMessages()
    }

    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, MessagingApp.SERVICE_CHANNEL_ID)
            .setContentTitle("PingMe")
            .setContentText("Ready to receive messages")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setOngoing(true)
            .build()
    }

    private fun listenForMessages() {
        val userId = authRepo.currentUserId() ?: return
        scope.launch {
            try {
                msgRepo.listenToAllMessages(userId).collect { message ->
                    val senderProfile = runCatching {
                        authRepo.getProfile(message.senderId).getOrNull()
                    }.getOrNull()
                    val senderName = senderProfile?.nickname ?: "Someone"
                    val senderId = message.senderId

                    // Increment per-sender unread
                    senderUnread[senderId] = (senderUnread[senderId] ?: 0) + 1
                    val totalUnread = senderUnread.values.sum()

                    showMessageNotification(
                        sender = senderName,
                        content = message.content,
                        notifId = senderId.hashCode(), // one notification per sender
                        unreadCount = totalUnread
                    )
                }
            } catch (e: Exception) {
                // Don't crash the service; just stop listening
            }
        }
    }

    private fun showMessageNotification(
        sender: String,
        content: String,
        notifId: Int,
        unreadCount: Int
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    this, android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) return
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT

        val pendingIntent = PendingIntent.getActivity(this, notifId, intent, pendingIntentFlags)
        val person = Person.Builder().setName(sender).build()

        val notification = NotificationCompat.Builder(this, MessagingApp.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(sender)
            .setContentText(content)
            .setStyle(
                NotificationCompat.MessagingStyle(person)
                    .addMessage(content, System.currentTimeMillis(), person)
            )
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setNumber(unreadCount) // ← sets the badge count on app icon
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(notifId, notification)
        } catch (e: Exception) {
            // SecurityException if permission revoked at runtime
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        private const val FOREGROUND_ID = 1
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, MessageNotificationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
