package com.kexu.campus.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.kexu.campus.CampusApp
import com.kexu.campus.ui.LockHomeActivity

/** 定时锁机到点：启动锁机服务，尽量打开锁机桌面，并重排下一天。 */
class LockAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext as? CampusApp ?: return
        val settings = app.repo.data.value.settings
        if (!settings.lockScheduleEnabled) return

        val serviceIntent = Intent(context, LockService::class.java).apply {
            action = LockService.ACTION_START
            putExtra(LockService.EXTRA_DURATION_MINUTES, settings.lockDurationMinutes)
            putStringArrayListExtra(
                LockService.EXTRA_ALLOWED_PACKAGES,
                ArrayList(settings.lockAllowedPackages)
            )
            putExtra(LockService.EXTRA_UNLOCK_MINUTES, settings.unlockDurationMinutes)
            putExtra(LockService.EXTRA_UNLOCK_COUNT, settings.unlockMaxCount)
        }
        runCatching {
            ContextCompat.startForegroundService(context, serviceIntent)
        }
        runCatching {
            context.startActivity(
                Intent(context, LockHomeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
        LockScheduler.schedule(context, settings)
    }
}

/** 开机后恢复定时锁机（AlarmManager 的闹钟不会跨重启保留）。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as? CampusApp ?: return
        LockScheduler.schedule(context, app.repo.data.value.settings)
    }
}
