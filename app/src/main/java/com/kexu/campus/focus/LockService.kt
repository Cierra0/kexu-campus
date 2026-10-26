package com.kexu.campus.focus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.kexu.campus.CampusApp
import com.kexu.campus.data.LockRecord
import com.kexu.campus.ui.LockHomeActivity

/**
 * 轻量版锁机：
 * - 前台服务维持会话与倒计时；
 * - 自己作为「锁机桌面」的兜底（HOME 角色由用户在系统里选择）；
 * - 每秒查一次前台应用，不在白名单就盖一层「已锁定」遮罩，并提供回到锁机桌面的按钮；
 * - 短时解锁期间放行（但设置类应用仍然拦截），次数用完需等下次锁机；
 * - 结束/进程被杀 → 会话自动失效，不会锁死手机。
 *
 * 注意：没有 Device Owner 时，系统设置、安全模式、ADB 仍可绕过，这是「自律工具」而非强制管控。
 */
class LockService : Service() {

    companion object {
        const val ACTION_START = "com.kexu.campus.lock.START"
        const val ACTION_STOP = "com.kexu.campus.lock.STOP"
        const val EXTRA_DURATION_MINUTES = "duration_minutes"
        const val EXTRA_ALLOWED_PACKAGES = "allowed_packages"
        const val EXTRA_UNLOCK_MINUTES = "unlock_minutes"
        const val EXTRA_UNLOCK_COUNT = "unlock_count"

        private const val CHANNEL_ID = "lock"
        private const val NOTIF_ID = 2002
        private const val TAG = "KexuLock"

        /** 电话、紧急呼叫、系统 UI 永远放行，避免接不到电话或无法挂断。 */
        private val SYSTEM_ALLOWED = setOf(
            "com.android.dialer",
            "com.android.phone",
            "com.android.incallui",
            "com.android.server.telecom",
            "com.google.android.dialer",
            "com.android.emergency",
            "com.android.systemui"
        )
    }

    private val handler = Handler(Looper.getMainLooper())
    private val overlayWindows = mutableListOf<OverlayWindow>()
    private var overlayShowing = false
    private var remainingSeconds = 0
    private var sessionStartAt = 0L
    private var initialUnlockCount = 0

    private data class OverlayWindow(
        val manager: WindowManager,
        val view: View,
        val countdown: TextView
    )

    private val ticker = object : Runnable {
        override fun run() {
            if (!LockSession.active) {
                stopSelf()
                return
            }
            remainingSeconds = LockSession.remainingSeconds()
            if (remainingSeconds <= 0) {
                stopSelf()
                return
            }
            updateNotification()
            updateOverlayText()
            checkForegroundApp()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val minutes = intent.getIntExtra(EXTRA_DURATION_MINUTES, 60).coerceIn(1, 24 * 60)
                val allowed = intent.getStringArrayListExtra(EXTRA_ALLOWED_PACKAGES)?.toSet() ?: emptySet()
                val unlockMinutes = intent.getIntExtra(EXTRA_UNLOCK_MINUTES, 5).coerceIn(1, 60)
                val unlockCount = intent.getIntExtra(EXTRA_UNLOCK_COUNT, 2).coerceIn(0, 20)
                sessionStartAt = System.currentTimeMillis()
                initialUnlockCount = unlockCount
                LockSession.start(minutes, allowed, unlockMinutes, unlockCount)
                startForegroundCompat()
                handler.removeCallbacks(ticker)
                handler.post(ticker)
                return START_NOT_STICKY
            }
            else -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
    }

    private fun startForegroundCompat() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "锁机", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, LockHomeActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val brief = if (LockSession.isBriefUnlocking()) {
            val seconds = ((LockSession.briefUnlockUntil - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
            "短时解锁中 · 剩 %02d:%02d".format(seconds / 60, seconds % 60)
        } else {
            "剩余 %02d:%02d · 短时解锁剩 %d 次".format(
                remainingSeconds / 60, remainingSeconds % 60, LockSession.briefUnlockRemaining
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("锁机中")
            .setContentText(brief)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIF_ID, buildNotification())
    }

    private fun checkForegroundApp() {
        if (!Settings.canDrawOverlays(this)) {
            android.util.Log.w(TAG, "no overlay permission")
            return
        }
        val pkg = currentForegroundPackage() ?: return
        if (LockSession.isBriefUnlocking()) {
            // 短时解锁期间也禁止进入系统设置，避免改默认桌面/关权限
            if (pkg == "com.android.settings") showBlockedOverlay() else hideBlockedOverlay()
            return
        }
        if (isAllowed(pkg)) hideBlockedOverlay() else showBlockedOverlay()
    }

    private fun isAllowed(pkg: String): Boolean {
        if (pkg == packageName) return true
        if (pkg in LockSession.allowedPackages) return true
        return pkg in SYSTEM_ALLOWED
    }

    private fun currentForegroundPackage(): String? {
        return try {
            val usageStats = getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
            val now = System.currentTimeMillis()
            val events = usageStats.queryEvents(now - 15_000, now)
            val event = android.app.usage.UsageEvents.Event()
            var packageName: String? = null
            var latest = 0L
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED ||
                    event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND
                ) {
                    if (event.timeStamp >= latest) {
                        latest = event.timeStamp
                        packageName = event.packageName
                    }
                }
            }
            packageName
        } catch (_: Exception) {
            null
        }
    }

    private fun showBlockedOverlay() {
        if (overlayShowing) return
        overlayShowing = true
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
        val displays = displayManager.displays
            .filter { it.state == android.view.Display.STATE_ON }
            .ifEmpty { listOfNotNull(displayManager.getDisplay(android.view.Display.DEFAULT_DISPLAY)) }
        for (display in displays) {
            val wm = runCatching {
                createDisplayContext(display).getSystemService(Context.WINDOW_SERVICE) as WindowManager
            }.getOrNull() ?: continue
            val (root, countdown) = buildOverlayView()
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.CENTER }
            runCatching {
                wm.addView(root, params)
                overlayWindows.add(OverlayWindow(wm, root, countdown))
            }.onFailure {
                android.util.Log.e(TAG, "overlay failed on display ${display.displayId}: ${it.message}", it)
            }
        }
    }

    private fun buildOverlayView(): Pair<View, TextView> {
        val context = this
        val root = FrameLayout(context).apply { setBackgroundColor(Color.argb(246, 16, 18, 16)) }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        val title = TextView(context).apply {
            text = "锁机中"
            setTextColor(Color.WHITE)
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        val countdown = TextView(context).apply {
            text = remainingText()
            setTextColor(Color.parseColor("#9BD3BE"))
            textSize = 48f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
        }
        val message = TextView(context).apply {
            text = "这个应用不在本次锁机的允许列表里"
            setTextColor(Color.parseColor("#C3CAC3"))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 0)
        }
        val button = Button(context).apply {
            text = "回到锁机桌面"
            setOnClickListener {
                startActivity(
                    Intent(context, LockHomeActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                )
            }
        }
        column.addView(title)
        column.addView(countdown)
        column.addView(message)
        val buttonParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 64 }
        column.addView(button, buttonParams)
        root.addView(column)
        return root to countdown
    }

    private fun updateOverlayText() {
        overlayWindows.forEach { it.countdown.text = remainingText() }
    }

    private fun hideBlockedOverlay() {
        if (!overlayShowing) return
        overlayWindows.forEach { runCatching { it.manager.removeView(it.view) } }
        overlayWindows.clear()
        overlayShowing = false
    }

    private fun remainingText(): String {
        val seconds = remainingSeconds.coerceAtLeast(0)
        return "%02d:%02d".format(seconds / 60, seconds % 60)
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        hideBlockedOverlay()
        val wasActive = LockSession.active
        if (wasActive) {
            runCatching {
                val now = System.currentTimeMillis()
                val actualMinutes = ((now - sessionStartAt) / 60_000L).toInt().coerceAtLeast(0)
                (application as CampusApp).repo.addLockRecord(
                    LockRecord(
                        startAt = sessionStartAt,
                        endAt = now,
                        plannedMinutes = LockSession.plannedMinutes,
                        actualMinutes = actualMinutes,
                        usedUnlock = (initialUnlockCount - LockSession.briefUnlockRemaining).coerceAtLeast(0),
                        endedEarly = LockSession.remainingSeconds() > 0
                    )
                )
            }
        }
        LockSession.stop()
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
        super.onDestroy()
    }
}
