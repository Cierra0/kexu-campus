package com.kexu.campus.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.kexu.campus.CampusApp
import com.kexu.campus.MainActivity
import com.kexu.campus.data.Dates
import com.kexu.campus.data.WeekRange
import com.kexu.campus.focus.LockService
import com.kexu.campus.focus.LockSession
import com.kexu.campus.ui.theme.KexuTheme
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 锁机桌面：锁机时作为「主界面」，只显示本次允许的应用、倒计时和短时解锁入口。
 * 用户需要在系统里把「校序」设为默认桌面，按 Home 才会回到这里；
 * 即使没设默认桌面，其它应用也会被 LockService 的遮罩拦住。
 */
class LockHomeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!LockSession.active) finish()
            }
        })
        setContent {
            KexuTheme(darkMode = 2) {
                androidx.compose.material3.Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LockHomeScreen()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
    }
}

private data class LockApp(val packageName: String, val label: String, val icon: ImageBitmap?)

@Composable
private fun LockHomeScreen() {
    val context = LocalContext.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var showExitDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }

    val active = LockSession.active && LockSession.endAtMillis > now
    val remaining = ((LockSession.endAtMillis - now) / 1000).toInt().coerceAtLeast(0)
    val briefRemaining = ((LockSession.briefUnlockUntil - now) / 1000).toInt().coerceAtLeast(0)
    val briefActive = briefRemaining > 0

    val apps by produceState(initialValue = emptyList<LockApp>(), key1 = active) {
        value = if (!active) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) {
                val pm = context.packageManager
                LockSession.allowedPackages.mapNotNull { pkg ->
                    runCatching {
                        val info = pm.getApplicationInfo(pkg, 0)
                        LockApp(
                            packageName = pkg,
                            label = pm.getApplicationLabel(info).toString(),
                            icon = pm.getApplicationIcon(info)
                                .toBitmap(144, 144)
                                .asImageBitmap()
                        )
                    }.getOrNull()
                }.sortedBy { it.label }
            }
        }
    }

    if (!active) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("本次锁机已结束", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                "如果「校序」已经被设为默认桌面，可以在这里恢复原来的桌面。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    context.startActivity(
                        Intent(context, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("打开校序") }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                    }.onFailure {
                        Toast.makeText(context, "请在系统设置 → 应用中修改默认桌面", Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("恢复系统桌面") }
            Spacer(Modifier.height(10.dp))
            TextButton(
                onClick = { context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            ) { Text("去设置里重新开始锁机") }
        }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 40.dp)
    ) {
        Text(
            "锁机中",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            formatSeconds(remaining),
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            if (briefActive) {
                "短时解锁中 · 剩余 ${formatSeconds(briefRemaining)}，结束后自动回到锁机"
            } else {
                "只能使用下面允许的应用；打开其它应用会被拦回这里"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val todayCourses = remember(active) {
            if (!active) {
                emptyList()
            } else {
                val data = (context.applicationContext as CampusApp).repo.data.value
                val today = LocalDate.now().dayOfWeek.value
                val week = Dates.currentWeek(data.settings)
                data.courses.mapNotNull { course ->
                    val session = course.sessions.firstOrNull {
                        it.dayOfWeek == today && WeekRange.isActive(it.weeks, week)
                    } ?: return@mapNotNull null
                    course to session
                }.sortedBy { it.second.startSection }
            }
        }
        if (todayCourses.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                "今天有 ${todayCourses.size} 门课",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            todayCourses.take(4).forEach { (course, session) ->
                Text(
                    "第 ${session.startSection}-${session.endSection} 节 · ${course.name}" +
                        if (session.location.isNotBlank()) " · ${session.location}" else "",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        if (apps.isEmpty()) {
            Text(
                "本次没有允许任何应用。\n点下方「紧急退出」可以提前结束锁机。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                items(apps, key = { it.packageName }) { app ->
                    Column(
                        Modifier.clickable {
                            runCatching {
                                val launch = context.packageManager.getLaunchIntentForPackage(app.packageName)
                                if (launch != null) {
                                    context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                            }.onFailure {
                                Toast.makeText(context, "打不开这个应用", Toast.LENGTH_SHORT).show()
                            }
                        },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (app.icon != null) {
                                Image(bitmap = app.icon, contentDescription = app.label, modifier = Modifier.size(56.dp))
                            } else {
                                Text(app.label.take(1), fontSize = 20.sp)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            app.label,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (briefActive) {
            OutlinedButton(
                onClick = { LockSession.endBriefUnlock() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("提前结束短时解锁") }
        } else {
            Button(
                onClick = {
                    val ok = LockSession.tryStartBriefUnlock()
                    if (!ok) {
                        Toast.makeText(context, "本次锁机的短时解锁次数已用完", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = LockSession.briefUnlockRemaining > 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (LockSession.briefUnlockRemaining > 0) {
                        "短时解锁 ${LockSession.unlockDurationMinutes} 分钟（剩 ${LockSession.briefUnlockRemaining} 次）"
                    } else {
                        "短时解锁次数已用完"
                    }
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = { showExitDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) { Text("紧急退出") }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("紧急退出锁机") },
            text = { Text("确定要提前结束本次锁机吗？结束后所有限制会立即解除。") },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    context.stopService(Intent(context, LockService::class.java))
                    LockSession.stop()
                }) { Text("结束锁机") }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { Text("继续锁机") }
            }
        )
    }
}

private fun formatSeconds(seconds: Int): String =
    "%02d:%02d".format(seconds / 60, seconds % 60)
