package com.kexu.campus.ui

import android.app.TimePickerDialog
import android.app.admin.DevicePolicyManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.kexu.campus.data.CampusData
import com.kexu.campus.data.CampusRepository
import com.kexu.campus.focus.LockScheduler
import com.kexu.campus.focus.LockService
import com.kexu.campus.focus.LockSession
import com.kexu.campus.focus.LockAdminReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

private data class InstalledApp(val packageName: String, val label: String, val category: String)

@Composable
fun FocusScreen(data: CampusData, repo: CampusRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    var running by remember { mutableStateOf(LockSession.active) }
    var remaining by remember { mutableStateOf(LockSession.remainingSeconds()) }
    var briefRemaining by remember {
        mutableStateOf(((LockSession.briefUnlockUntil - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0))
    }
    var unlockRemaining by remember { mutableStateOf(LockSession.briefUnlockRemaining) }

    var duration by rememberSaveable { mutableStateOf(data.settings.lockDurationMinutes) }
    var unlockMinutes by rememberSaveable { mutableStateOf(data.settings.unlockDurationMinutes) }
    var unlockCount by rememberSaveable { mutableStateOf(data.settings.unlockMaxCount) }
    var allowed by remember {
        mutableStateOf(data.settings.lockAllowedPackages.toSet())
    }
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var showAppPicker by remember { mutableStateOf(false) }
    var pickerCategory by remember { mutableStateOf("全部") }

    var scheduleEnabled by rememberSaveable { mutableStateOf(data.settings.lockScheduleEnabled) }
    var scheduleHour by rememberSaveable { mutableStateOf(data.settings.lockScheduleHour) }
    var scheduleMinute by rememberSaveable { mutableStateOf(data.settings.lockScheduleMinute) }

    val weekStartMillis = remember {
        LocalDate.now().with(DayOfWeek.MONDAY)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
    val weekMinutes = remember(data.lockRecords, weekStartMillis) {
        data.lockRecords.filter { it.startAt >= weekStartMillis }.sumOf { it.actualMinutes }
    }
    val weekUnlock = remember(data.lockRecords, weekStartMillis) {
        data.lockRecords.filter { it.startAt >= weekStartMillis }.sumOf { it.usedUnlock }
    }

    var overlayOk by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var usageOk by remember { mutableStateOf(hasUsageAccess(context)) }
    var homeRoleOk by remember { mutableStateOf(isHomeRoleHeld(context)) }
    var adminOk by remember { mutableStateOf(isDeviceAdmin(context)) }

    LaunchedEffect(Unit) {
        while (true) {
            running = LockSession.active
            remaining = LockSession.remainingSeconds()
            briefRemaining = ((LockSession.briefUnlockUntil - System.currentTimeMillis()) / 1000)
                .toInt().coerceAtLeast(0)
            unlockRemaining = LockSession.briefUnlockRemaining
            overlayOk = Settings.canDrawOverlays(context)
            usageOk = hasUsageAccess(context)
            homeRoleOk = isHomeRoleHeld(context)
            adminOk = isDeviceAdmin(context)
            delay(1000)
        }
    }

    LaunchedEffect(refresh) {
        apps = withContext(Dispatchers.IO) { loadLaunchableApps(context) }
    }

    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refresh++ }
    val usageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refresh++ }
    val homeRoleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refresh++ }
    val adminLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refresh++ }

    val canStart = overlayOk && usageOk

    fun startLock() {
        if (!canStart) return
        repo.updateSettings {
            it.copy(
                lockDurationMinutes = duration,
                lockAllowedPackages = allowed.toList(),
                unlockDurationMinutes = unlockMinutes,
                unlockMaxCount = unlockCount
            )
        }
        val intent = Intent(context, LockService::class.java).apply {
            action = LockService.ACTION_START
            putExtra(LockService.EXTRA_DURATION_MINUTES, duration)
            putStringArrayListExtra(LockService.EXTRA_ALLOWED_PACKAGES, ArrayList(allowed))
            putExtra(LockService.EXTRA_UNLOCK_MINUTES, unlockMinutes)
            putExtra(LockService.EXTRA_UNLOCK_COUNT, unlockCount)
        }
        ContextCompat.startForegroundService(context, intent)
        runCatching {
            context.startActivity(
                Intent(context, LockHomeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
        refresh++
    }

    fun stopLock() {
        context.stopService(Intent(context, LockService::class.java))
        LockSession.stop()
        refresh++
    }

    fun requestHomeRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null &&
                roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            ) {
                homeRoleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
                return
            }
        }
        runCatching { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
    }

    ScreenScaffold(
        title = "锁机",
        subtitle = "只显示允许的应用 · 桌面替换 · 短时解锁限量",
        onBack = onBack
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionCard(title = "状态") {
                    if (running) {
                        Text(
                            "锁机中 · 剩余 ${formatSeconds(remaining)}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (briefRemaining > 0) {
                                "短时解锁中 · 剩余 ${formatSeconds(briefRemaining)}"
                            } else {
                                "短时解锁还剩 $unlockRemaining 次"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(context, LockHomeActivity::class.java)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("回到锁机桌面") }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { stopLock() }, modifier = Modifier.fillMaxWidth()) {
                            Text("紧急结束锁机")
                        }
                    } else {
                        Text("未在锁机中", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "开始后：桌面会换成锁机界面，只显示下面勾选的应用；打开其它应用会被拦回锁机桌面。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                SectionCard(title = "本周统计") {
                    Text(
                        "已锁机 ${weekMinutes / 60} 小时 ${weekMinutes % 60} 分钟 · 短时解锁 $weekUnlock 次",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "统计只保存在本机，用于回顾自己的专注情况。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                SectionCard(title = "权限与桌面") {
                    PermissionRow(
                        title = "悬浮窗权限",
                        desc = "用于拦截不在允许列表的应用",
                        granted = overlayOk
                    ) {
                        overlayLauncher.launch(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    PermissionRow(
                        title = "使用情况访问权限",
                        desc = "用于判断当前前台应用（只读包名）",
                        granted = usageOk
                    ) {
                        usageLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                    Spacer(Modifier.height(8.dp))
                    PermissionRow(
                        title = "设为默认桌面（推荐）",
                        desc = "锁机时按 Home 回到锁机界面；不设置也能靠遮罩拦截",
                        granted = homeRoleOk
                    ) { requestHomeRole() }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "轻量模式说明：没有设备所有者权限，懂手机的人仍可通过系统设置、安全模式或 ADB 绕过；" +
                            "它适合自我管理，不是无法破解的管控。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (homeRoleOk) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                runCatching { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("恢复系统桌面") }
                    }
                }
            }

            item {
                SectionCard(title = "锁机设置") {
                    Text("锁机时长", fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(30, 60, 90, 120).forEach { minutes ->
                            FilterChip(
                                selected = duration == minutes,
                                onClick = { duration = minutes },
                                label = { Text("$minutes 分钟", fontSize = 12.sp) }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { showAppPicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (allowed.isEmpty()) "选择允许使用的应用（必选）"
                            else "已允许 ${allowed.size} 个应用 · 点击修改"
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("短时解锁时长", fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 10, 15).forEach { minutes ->
                            FilterChip(
                                selected = unlockMinutes == minutes,
                                onClick = { unlockMinutes = minutes },
                                label = { Text("$minutes 分钟", fontSize = 12.sp) }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("每次锁机可短时解锁次数", fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 1, 2, 3, 5).forEach { count ->
                            FilterChip(
                                selected = unlockCount == count,
                                onClick = { unlockCount = count },
                                label = { Text(if (count == 0) "不允许" else "$count 次", fontSize = 12.sp) }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { startLock() },
                        enabled = canStart && !running && allowed.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            when {
                                running -> "已在锁机中"
                                !canStart -> "请先授予悬浮窗和使用情况权限"
                                allowed.isEmpty() -> "请先选择允许的应用"
                                else -> "开始锁机（$duration 分钟）"
                            }
                        )
                    }
                }
            }

            item {
                SectionCard(title = "定时锁机") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("每天定时自动开始", fontSize = 14.sp)
                            Text(
                                "当前：%02d:%02d 开始，时长 $duration 分钟".format(scheduleHour, scheduleMinute),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        androidx.compose.material3.Switch(
                            checked = scheduleEnabled,
                            onCheckedChange = { enabled ->
                                scheduleEnabled = enabled
                                val updated = data.settings.copy(
                                    lockScheduleEnabled = enabled,
                                    lockScheduleHour = scheduleHour,
                                    lockScheduleMinute = scheduleMinute
                                )
                                repo.updateSettings { updated }
                                LockScheduler.schedule(context, updated)
                            }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    scheduleHour = hour
                                    scheduleMinute = minute
                                    val updated = data.settings.copy(
                                        lockScheduleEnabled = scheduleEnabled,
                                        lockScheduleHour = hour,
                                        lockScheduleMinute = minute
                                    )
                                    repo.updateSettings { updated }
                                    LockScheduler.schedule(context, updated)
                                },
                                scheduleHour,
                                scheduleMinute,
                                true
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("修改开始时间") }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "需要「闹钟与提醒」权限；如果系统不允许精确闹钟，会自动退化为非精确时间。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                SectionCard(title = "一键锁屏（可选）") {
                    Text(
                        "开启设备管理器后可以立即锁屏；不涉及擦除数据。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!adminOk) {
                            OutlinedButton(
                                onClick = {
                                    val component = ComponentName(context, LockAdminReceiver::class.java)
                                    adminLauncher.launch(
                                        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
                                            putExtra(
                                                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                                "校序只需要「锁定屏幕」权限，用于一键锁屏。"
                                            )
                                        }
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("开启设备管理器") }
                        }
                        OutlinedButton(
                            onClick = {
                                if (isDeviceAdmin(context)) {
                                    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE)
                                        as DevicePolicyManager
                                    dpm.lockNow()
                                }
                            },
                            enabled = adminOk,
                            modifier = Modifier.weight(1f)
                        ) { Text("立即锁屏") }
                    }
                }
            }

            item {
                SectionCard(title = "安全说明") {
                    listOf(
                        "• 锁机界面上的「紧急退出」可以随时提前结束，避免把自己锁死。",
                        "• 电话、紧急呼叫、系统 UI 永远放行，不会影响接打电话。",
                        "• 只读取前台应用包名，不记录使用内容，也不上传。",
                        "• 轻量模式可以被系统设置绕过，请仅用于自我管理，不要用来限制他人。"
                    ).forEach {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }

    if (showAppPicker) {
        AlertDialog(
            onDismissRequest = { showAppPicker = false },
            title = { Text("允许在锁机时使用的应用") },
            text = {
                if (apps.isEmpty()) {
                    Text("正在读取应用列表…")
                } else {
                    Column {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("全部", "学习", "社交", "游戏", "影音", "工具", "其他").forEach { category ->
                                FilterChip(
                                    selected = pickerCategory == category,
                                    onClick = { pickerCategory = category },
                                    label = { Text(category, fontSize = 11.sp) }
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        LazyColumn(Modifier.heightIn(max = 380.dp)) {
                            items(apps.filter { pickerCategory == "全部" || it.category == pickerCategory }, key = { it.packageName }) { app ->
                                val checked = allowed.contains(app.packageName)
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            allowed = if (checked) {
                                                allowed - app.packageName
                                            } else {
                                                allowed + app.packageName
                                            }
                                        }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(checked = checked, onCheckedChange = null)
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(app.label, fontSize = 14.sp)
                                        Text(
                                            "${app.category} · ${app.packageName}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAppPicker = false }) { Text("完成") }
            }
        )
    }
}

@Composable
private fun PermissionRow(
    title: String,
    desc: String,
    granted: Boolean,
    onOpen: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (granted) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
            )
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        if (granted) {
            Text("已开启", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        } else {
            OutlinedButton(onClick = onOpen) { Text("去开启", fontSize = 12.sp) }
        }
    }
}

private fun formatSeconds(seconds: Int): String =
    "%02d:%02d".format(seconds / 60, seconds % 60)

private fun loadLaunchableApps(context: Context): List<InstalledApp> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val infos = context.packageManager.queryIntentActivities(intent, 0)
    return infos
        .map {
            val pkg = it.activityInfo.packageName
            val label = it.loadLabel(context.packageManager).toString()
            InstalledApp(pkg, label, categoryOf(context, label, pkg))
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label }
}

private fun categoryOf(context: Context, label: String, packageName: String): String {
    val learningHints = listOf("学习", "词典", "单词", "课程", "考研", "四六级", "百词", "题库", "作业", "网课")
    if (learningHints.any { label.contains(it) }) return "学习"
    val info = runCatching { context.packageManager.getApplicationInfo(packageName, 0) }.getOrNull()
        ?: return "其他"
    return when (info.category) {
        ApplicationInfo.CATEGORY_GAME -> "游戏"
        ApplicationInfo.CATEGORY_SOCIAL -> "社交"
        ApplicationInfo.CATEGORY_AUDIO,
        ApplicationInfo.CATEGORY_VIDEO,
        ApplicationInfo.CATEGORY_IMAGE -> "影音"
        ApplicationInfo.CATEGORY_PRODUCTIVITY,
        ApplicationInfo.CATEGORY_NEWS -> "工具"
        else -> "其他"
    }
}

private fun hasUsageAccess(context: Context): Boolean {
    return try {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            appOps.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        mode == android.app.AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) {
        false
    }
}

private fun isHomeRoleHeld(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    return try {
        val roleManager = context.getSystemService(RoleManager::class.java)
        roleManager?.isRoleHeld(RoleManager.ROLE_HOME) == true
    } catch (_: Exception) {
        false
    }
}

private fun isDeviceAdmin(context: Context): Boolean {
    return try {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        dpm.isAdminActive(ComponentName(context, LockAdminReceiver::class.java))
    } catch (_: Exception) {
        false
    }
}
