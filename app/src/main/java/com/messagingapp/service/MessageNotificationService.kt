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

class MessageNotificationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val authRepo = AuthRepository()
    private val msgRepo = MessageRepository()

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildForegroundNotification())
        // Mark user online when service starts
        scope.launch { runCatching { authRepo.setOnlineStatus(true) } }
        listenForMessages()
    }

    private fun buildForegroundNotification(): Notification =
        NotificationCompat.Builder(this, MessagingApp.SERVICE_CHANNEL_ID)
            .setContentTitle("PingMe")
            .setContentText("Ready to receive messages")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setOngoing(true)
            .build()

    private fun listenForMessages() {
        val userId = authRepo.currentUserId() ?: return
        scope.launch {
            runCatching {
                msgRepo.listenToAllIncomingMessages(userId).collect { message ->
                    val senderName = authRepo.getProfile(message.senderId)
                        .getOrNull()?.nickname ?: "Someone"
                    showMessageNotification(senderName, message.content)
                }
            }
        }
    }

    private fun showMessageNotification(sender: String, content: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    this, android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) return
        }
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        val person = Person.Builder().setName(sender).build()
        val notif = NotificationCompat.Builder(this, MessagingApp.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(sender)
            .setContentText(content)
            .setStyle(NotificationCompat.MessagingStyle(person)
                .addMessage(content, System.currentTimeMillis(), person))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pi)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()
        NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), notif)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.launch { runCatching { authRepo.setOnlineStatus(false) } }
        scope.cancel()
    }

    companion object {
        const val NOTIF_ID = 1001
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val svcIntent = Intent(context, MessageNotificationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(svcIntent)
            else
                context.startService(svcIntent)
        }
    }
}
