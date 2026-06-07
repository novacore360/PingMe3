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
        startForeground(BUBBLE_NOTIF_ID, buildForegroundNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        listenForMessages()
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
            msgRepo.listenToAllMessages(userId).collect { message ->
                if (message.senderId != userId) {
                    unreadCount++
                    withContext(Dispatchers.Main) {
                        if (!isVisible) showBubble()
                        else updateBadge()
                    }
                }
            }
        }
    }

    private fun showBubble() {
        if (bubbleView != null) {
            updateBadge()
            return
        }

        // Check overlay permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(this)) {
            return
        }

        val layout = FrameLayout(this)

        // Bubble circle
        val bubble = TextView(this).apply {
            text = if (unreadCount > 99) "99+" else unreadCount.toString()
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            val size = dpToPx(58)
            layoutParams = FrameLayout.LayoutParams(size, size)
        }

        // Gradient background for bubble
        val gradient = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#6C9EFF"), Color.parseColor("#9B6DFF"))
        ).apply {
            shape = GradientDrawable.OVAL
        }
        bubble.background = gradient

        // Badge for count
        val badge = TextView(this).apply {
            text = if (unreadCount > 99) "99+" else unreadCount.toString()
            textSize = 10f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            val badgeSize = dpToPx(20)
            val params = FrameLayout.LayoutParams(badgeSize, badgeSize).apply {
                gravity = Gravity.TOP or Gravity.END
                setMargins(0, dpToPx(-4), dpToPx(-4), 0)
            }
            layoutParams = params
            val badgeBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FF3B30"))
            }
            background = badgeBg
            visibility = if (unreadCount > 0) View.VISIBLE else View.GONE
        }

        // Chat icon text (emoji substitute)
        bubble.text = "💬"
        badge.text = if (unreadCount > 99) "99+" else unreadCount.toString()

        layout.addView(bubble)
        layout.addView(badge)

        val size = dpToPx(66)
        val params = WindowManager.LayoutParams(
            size, size,
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

        layout.setOnClickListener {
            openApp()
            hideBubble()
        }

        // Drag behavior
        var initialX = 0; var initialY = 0
        var initialTouchX = 0f; var initialTouchY = 0f
        var isDragging = false

        layout.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x; initialY = params.y
                    initialTouchX = event.rawX; initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) isDragging = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager?.updateViewLayout(layout, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        v.performClick()
                    } else {
                        // Snap to edge
                        val display = windowManager!!.defaultDisplay
                        val point = Point()
                        display.getSize(point)
                        val midX = point.x / 2
                        params.x = if (params.x + size / 2 < midX) dpToPx(16) else point.x - size - dpToPx(16)
                        windowManager?.updateViewLayout(layout, params)
                    }
                    true
                }
                else -> false
            }
        }

        // Entry animation
        layout.scaleX = 0f
        layout.scaleY = 0f
        layout.alpha = 0f

        bubbleView = layout
        windowManager?.addView(layout, params)
        isVisible = true

        layout.animate()
            .scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(300)
            .setInterpolator(OvershootInterpolator())
            .start()
    }

    private fun updateBadge() {
        val layout = bubbleView as? FrameLayout ?: return
        val badge = layout.getChildAt(1) as? TextView ?: return
        badge.text = if (unreadCount > 99) "99+" else unreadCount.toString()
        badge.visibility = if (unreadCount > 0) View.VISIBLE else View.GONE
    }

    private fun hideBubble() {
        bubbleView?.let {
            it.animate().scaleX(0f).scaleY(0f).alpha(0f).setDuration(200)
                .withEndAction {
                    runCatching { windowManager?.removeView(it) }
                    bubbleView = null
                    isVisible = false
                    unreadCount = 0
                }
                .start()
        }
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE_BUBBLE -> hideBubble()
            ACTION_SHOW_BUBBLE -> {
                unreadCount = intent.getIntExtra(EXTRA_UNREAD_COUNT, 0)
                if (!isVisible) showBubble() else updateBadge()
            }
        }
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
