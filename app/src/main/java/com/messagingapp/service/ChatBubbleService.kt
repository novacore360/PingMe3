package com.messagingapp.service

import android.app.*
import android.content.*
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.view.*
import android.view.animation.OvershootInterpolator
import android.widget.*
import androidx.core.app.NotificationCompat
import com.messagingapp.MainActivity
import com.messagingapp.MessagingApp
import com.messagingapp.data.repository.AuthRepository
import com.messagingapp.data.repository.MessageRepository
import kotlinx.coroutines.*

class ChatBubbleService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val authRepo = AuthRepository()
    private val msgRepo = MessageRepository()

    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    private var unreadCount = 0
    private var isVisible = false

    companion object {
        const val ACTION_SHOW_BUBBLE = "com.messagingapp.SHOW_BUBBLE"
        const val ACTION_HIDE_BUBBLE = "com.messagingapp.HIDE_BUBBLE"
        const val EXTRA_UNREAD_COUNT = "unread_count"
        const val BUBBLE_NOTIF_ID = 2001
    }

    override fun onCreate() {
        super.onCreate()
        try {
            startForeground(BUBBLE_NOTIF_ID, buildForegroundNotification())
            windowManager = getSystemService(WINDOW_SERVICE) as? WindowManager
            listenForMessages()
        } catch (e: Exception) {
            stopSelf()
        }
    }

    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, MessagingApp.SERVICE_CHANNEL_ID)
            .setContentTitle("PingMe")
            .setContentText("Chat bubble active")
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
                    if (message.senderId != userId) {
                        unreadCount++
                        withContext(Dispatchers.Main) {
                            try {
                                if (!isVisible) showBubble()
                                else updateBadge()
                            } catch (e: Exception) { /* ignore view errors */ }
                        }
                    }
                }
            } catch (e: Exception) { /* stop silently */ }
        }
    }

    private fun showBubble() {
        if (bubbleView != null) { updateBadge(); return }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !android.provider.Settings.canDrawOverlays(this)) return

        val wm = windowManager ?: return

        val layout = FrameLayout(this)

        val bubble = TextView(this).apply {
            text = "💬"
            textSize = 22f
            gravity = Gravity.CENTER
            val sz = dpToPx(58)
            layoutParams = FrameLayout.LayoutParams(sz, sz)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#6C9EFF"), Color.parseColor("#9B6DFF"))
            ).apply { shape = GradientDrawable.OVAL }
        }

        val badgeSz = dpToPx(20)
        val badge = TextView(this).apply {
            text = if (unreadCount > 99) "99+" else unreadCount.toString()
            textSize = 9f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = FrameLayout.LayoutParams(badgeSz, badgeSz).apply {
                gravity = Gravity.TOP or Gravity.END
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#E53935"))
            }
            visibility = if (unreadCount > 0) View.VISIBLE else View.GONE
        }

        layout.addView(bubble)
        layout.addView(badge)

        val sz = dpToPx(66)
        val wlp = WindowManager.LayoutParams(
            sz, sz,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dpToPx(16)
            y = dpToPx(200)
        }

        layout.setOnClickListener { openApp(); hideBubble() }

        var initX = 0; var initY = 0
        var initTX = 0f; var initTY = 0f
        var dragging = false

        layout.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = wlp.x; initY = wlp.y
                    initTX = event.rawX; initTY = event.rawY
                    dragging = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initTX).toInt()
                    val dy = (event.rawY - initTY).toInt()
                    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) dragging = true
                    wlp.x = initX + dx; wlp.y = initY + dy
                    runCatching { wm.updateViewLayout(layout, wlp) }; true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) v.performClick()
                    else {
                        val dm = resources.displayMetrics
                        val mid = dm.widthPixels / 2
                        wlp.x = if (wlp.x + sz / 2 < mid) dpToPx(16) else dm.widthPixels - sz - dpToPx(16)
                        runCatching { wm.updateViewLayout(layout, wlp) }
                    }
                    true
                }
                else -> false
            }
        }

        layout.scaleX = 0f; layout.scaleY = 0f; layout.alpha = 0f
        bubbleView = layout

        try {
            wm.addView(layout, wlp)
            isVisible = true
            layout.animate().scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(300).setInterpolator(OvershootInterpolator()).start()
        } catch (e: Exception) {
            bubbleView = null
        }
    }

    private fun updateBadge() {
        val layout = bubbleView as? FrameLayout ?: return
        val badge = layout.getChildAt(1) as? TextView ?: return
        badge.text = if (unreadCount > 99) "99+" else unreadCount.toString()
        badge.visibility = if (unreadCount > 0) View.VISIBLE else View.GONE
    }

    private fun hideBubble() {
        bubbleView?.let { v ->
            v.animate().scaleX(0f).scaleY(0f).alpha(0f).setDuration(200)
                .withEndAction {
                    runCatching { windowManager?.removeView(v) }
                    bubbleView = null
                    isVisible = false
                    unreadCount = 0
                }.start()
        }
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        runCatching { startActivity(intent) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            when (intent?.action) {
                ACTION_HIDE_BUBBLE -> hideBubble()
                ACTION_SHOW_BUBBLE -> {
                    unreadCount = intent.getIntExtra(EXTRA_UNREAD_COUNT, 0)
                    if (!isVisible) showBubble() else updateBadge()
                }
            }
        } catch (e: Exception) { /* ignore */ }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        runCatching { bubbleView?.let { windowManager?.removeView(it) } }
        scope.cancel()
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()
}
