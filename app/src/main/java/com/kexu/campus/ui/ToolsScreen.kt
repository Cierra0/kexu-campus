package com.kexu.campus.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kexu.campus.data.CampusData
import com.kexu.campus.data.Ledger
import com.kexu.campus.dns.DnsFilterService
import com.kexu.campus.focus.LockSession
import kotlinx.coroutines.delay
import java.time.YearMonth

@Composable
fun ToolsScreen(
    data: CampusData,
    onOpenLedger: () -> Unit,
    onOpenAdBlock: () -> Unit,
    onOpenLock: () -> Unit,
    onOpenSchoolSync: () -> Unit
) {
    val month = YearMonth.now()
    val monthExpense = remember(data.ledger, month) {
        Ledger.inMonth(data.ledger, month.year, month.monthValue)
            .filter { it.type == 0 }
            .sumOf { it.amount }
    }
    var adRunning by remember { mutableStateOf(DnsFilterService.isRunning) }
    var lockRunning by remember { mutableStateOf(LockSession.active) }
    var lockRemaining by remember { mutableStateOf(LockSession.remainingSeconds()) }

    LaunchedEffect(Unit) {
        while (true) {
            adRunning = DnsFilterService.isRunning
            lockRunning = LockSession.active
            lockRemaining = LockSession.remainingSeconds()
            delay(1500)
        }
    }

    ScreenScaffold(
        title = "工具",
        subtitle = "教务 · 记账 · 去广告 · 锁机"
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionCard(title = "学校教务（登录 / 同步）") {
                    Text(
                        "登录一次学校教务系统，之后就能同步课表和成绩；支持东华理工 aTrust，" +
                            "也可以自定义其他学校的教务网址。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = onOpenSchoolSync,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("登录 / 同步教务系统") }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "账号密码仅用于本机自动填充，加密保存，不上传；登录和同步过程都在本机完成。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                SectionCard(title = "生活") {
                    ToolRow(
                        title = "记账",
                        subtitle = "本月支出 ¥${String.format("%.2f", monthExpense)} · 预算、分类、CSV 导出",
                        onClick = onOpenLedger
                    )
                }
            }

            item {
                SectionCard(title = "专注与清净") {
                    ToolRow(
                        title = "去广告",
                        subtitle = if (adRunning) "运行中 · 本地 DNS 过滤" else "未开启 · 内置 anti-AD 规则",
                        onClick = onOpenAdBlock,
                        highlight = adRunning
                    )
                    Spacer(Modifier.height(10.dp))
                    ToolRow(
                        title = "锁机",
                        subtitle = if (lockRunning) {
                            "锁机中 · 剩余 ${"%02d:%02d".format(lockRemaining / 60, lockRemaining % 60)} · 短时解锁剩 ${LockSession.briefUnlockRemaining} 次"
                        } else {
                            "未开启 · 桌面替换 / 白名单 / 短时解锁"
                        },
                        onClick = onOpenLock,
                        highlight = lockRunning
                    )
                }
            }

            item {
                SectionCard(title = "说明") {
                    Text(
                        "这三个工具都只在本机运行：记账数据不出手机；去广告只接管 DNS；" +
                            "锁机依靠悬浮窗与前台服务实现，没有设备所有者权限时属于自律工具。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    highlight: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    subtitle,
                    fontSize = 11.sp,
                    color = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "›",
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
