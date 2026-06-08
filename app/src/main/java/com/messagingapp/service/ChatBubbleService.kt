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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val authRepo = AuthRepository()
    private val msgRepo = MessageRepository()

    private var windowManager: WindowManager? = null
    private var bubbleRoot: FrameLayout? = null
    private var wlp: WindowManager.LayoutParams? = null
    private var unreadCount = 0

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildForegroundNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        listenForMessages()
    }

    private fun buildForegroundNotification(): Notification =
        NotificationCompat.Builder(this, MessagingApp.SERVICE_CHANNEL_ID)
            .setContentTitle("PingMe")
            .setContentText("Bubble ready")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setOngoing(true)
            .build()

    private fun listenForMessages() {
        val userId = authRepo.currentUserId() ?: return
        ioScope.launch {
            runCatching {
                msgRepo.listenToAllIncomingMessages(userId).collect { msg ->
                    if (msg.senderId != userId) {
                        unreadCount++
                        withContext(Dispatchers.Main) {
                            if (bubbleRoot == null) showBubble()
                            else updateBadge()
                        }
                    }
                }
            }
        }
    }

    private fun showBubble() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !android.provider.Settings.canDrawOverlays(this)
        ) return

        val ctx = this
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val bubbleSize = dp(60)
        val badgeSize = dp(20)

        // Root container
        val root = FrameLayout(ctx)

        // Bubble button
        val bubbleBtn = TextView(ctx).apply {
            text = "💬"
            textSize = 26f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(bubbleSize, bubbleSize)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#6C9EFF"), Color.parseColor("#9B6DFF"))
            ).apply {
                shape = GradientDrawable.OVAL
                // Elevation shadow effect
                setStroke(dp(2), Color.parseColor("#4DFFFFFF"))
            }
            elevation = dp(8).toFloat()
        }

        // Badge
        val badge = TextView(ctx).apply {
            text = formatCount(unreadCount)
            textSize = 9f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = FrameLayout.LayoutParams(badgeSize, badgeSize).apply {
                gravity = Gravity.TOP or Gravity.END
                setMargins(0, dp(-2), dp(-2), 0)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FF3B30"))
                setStroke(dp(2), Color.parseColor("#0A0E1A"))
            }
            visibility = if (unreadCount > 0) View.VISIBLE else View.GONE
        }

        root.addView(bubbleBtn)
        root.addView(badge)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val lp = WindowManager.LayoutParams(
            bubbleSize + dp(4), bubbleSize + dp(4),
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12)
            y = dp(180)
        }

        // Drag + click
        var downX = 0f; var downY = 0f
        var startLpX = 0; var startLpY = 0
        var moved = false

        root.setOnTouchListener { v, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY
                    startLpX = lp.x; startLpY = lp.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - downX).toInt()
                    val dy = (ev.rawY - downY).toInt()
                    if (dx * dx + dy * dy > 25) moved = true
                    lp.x = startLpX + dx
                    lp.y = startLpY + dy
                    runCatching { windowManager?.updateViewLayout(root, lp) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        openApp()
                        dismissBubble()
                    } else {
                        // Snap to nearest edge
                        val screenW = getScreenWidth()
                        lp.x = if (lp.x + bubbleSize / 2 < screenW / 2) dp(12)
                        else screenW - bubbleSize - dp(12)
                        runCatching { windowManager?.updateViewLayout(root, lp) }
                    }
                    true
                }
                else -> false
            }
        }
        root.setOnClickListener { /* handled in touch */ }

        root.scaleX = 0f; root.scaleY = 0f; root.alpha = 0f
        bubbleRoot = root
        wlp = lp
        runCatching { windowManager?.addView(root, lp) }
        root.animate().scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(280L)
            .setInterpolator(OvershootInterpolator(1.8f))
            .start()
    }

    private fun updateBadge() {
        val root = bubbleRoot ?: return
        val badge = root.getChildAt(1) as? TextView ?: return
        badge.text = formatCount(unreadCount)
        badge.visibility = if (unreadCount > 0) View.VISIBLE else View.GONE
    }

    private fun dismissBubble() {
        val root = bubbleRoot ?: return
        root.animate().scaleX(0f).scaleY(0f).alpha(0f).setDuration(180L)
            .withEndAction {
                runCatching { windowManager?.removeView(root) }
                bubbleRoot = null
                unreadCount = 0
            }.start()
    }

    private fun openApp() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        })
    }

    @Suppress("DEPRECATION")
    private fun getScreenWidth(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val wm = getSystemService(WindowManager::class.java)
            wm.currentWindowMetrics.bounds.width()
        } else {
            val point = Point()
            (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getSize(point)
            point.x
        }
    }

    private fun formatCount(n: Int) = if (n > 99) "99+" else n.toString()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISMISS -> dismissBubble()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        runCatching { bubbleRoot?.let { windowManager?.removeView(it) } }
        scope.cancel()
        ioScope.cancel()
    }

    companion object {
        const val NOTIF_ID = 2002
        const val ACTION_DISMISS = "com.messagingapp.BUBBLE_DISMISS"
    }
}
