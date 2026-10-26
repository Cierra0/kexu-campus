package com.kexu.campus.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.kexu.campus.data.CampusData
import com.kexu.campus.data.CampusRepository
import com.kexu.campus.data.Course
import com.kexu.campus.data.Dates
import com.kexu.campus.data.Friend
import com.kexu.campus.data.SECTION_COUNT
import com.kexu.campus.data.ShareCode
import com.kexu.campus.data.WEEKDAY_NAMES
import com.kexu.campus.data.WeekRange
import java.util.UUID

private val CELL_BOTH_BUSY = Color(0xFF1F2420)
private val CELL_ONE_BUSY = Color(0xFFAEB6AD)
private val CELL_FREE = Color(0xFFFFFFFF)

@Composable
fun FriendsScreen(
    data: CampusData,
    repo: CampusRepository,
    onAddFriend: () -> Unit,
    onOpenFreeTime: (String) -> Unit
) {
    var pendingDelete by remember { mutableStateOf<Friend?>(null) }
    val context = LocalContext.current

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (!contents.isNullOrBlank()) {
            val decoded = ShareCode.decode(contents)
            if (decoded == null) {
                Toast.makeText(context, "这不是校序的课表二维码", Toast.LENGTH_SHORT).show()
            } else {
                repo.addFriend(
                    Friend(
                        id = UUID.randomUUID().toString(),
                        name = decoded.name.ifBlank { "同学" },
                        courses = decoded.courses
                    )
                )
                Toast.makeText(context, "已添加同学", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun launchScan() {
        scanLauncher.launch(
            ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("扫描同学的课表二维码")
                setBeepEnabled(false)
                setOrientationLocked(false)
            }
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchScan()
        } else {
            Toast.makeText(context, "需要相机权限才能扫码，也可以让同学发分享码粘贴", Toast.LENGTH_LONG).show()
        }
    }

    fun onScanClick() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) launchScan() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    ScreenScaffold(
        title = "同学",
        subtitle = "只共享忙闲时间 · 看不到课程内容",
        actions = {
            IconButton(onClick = { onScanClick() }) {
                Icon(Icons.Outlined.QrCodeScanner, contentDescription = "扫码加同学")
            }
        },
        floatingAction = {
            ExtendedFloatingActionButton(
                onClick = onAddFriend,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("加同学") }
            )
        }
    ) {
        if (data.friends.isEmpty()) {
            EmptyState(
                title = "还没有同学",
                subtitle = "让同学在「导入与分享」里生成分享码，你粘贴导入即可。\n分享的只是有课/没课的时间格，不含课程名、地点、教师。",
                action = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.material3.Button(onClick = { onScanClick() }) {
                            Text("扫码添加")
                        }
                        Spacer(Modifier.height(6.dp))
                        androidx.compose.material3.TextButton(onClick = onAddFriend) {
                            Text("粘贴分享码")
                        }
                    }
                }
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(data.friends, key = { it.id }) { friend ->
                    FriendCard(
                        friend = friend,
                        onClick = { onOpenFreeTime(friend.id) },
                        onDelete = { pendingDelete = friend }
                    )
                }
                item {
                    SectionCard("关于隐私") {
                        Text(
                            "同学课表只保存「有课/没课」的时间格，不包含课程名、地点、教师、成绩。" +
                                "你们看到的是三色格：黑色=都有课，灰色=一方有课，白色=都没课。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    val deleting = pendingDelete
    if (deleting != null) {
        ConfirmDialog(
            title = "删除同学",
            message = "确定删除「${deleting.name}」的忙闲数据吗？只会删除你本机的这份快照。",
            confirmText = "删除",
            onConfirm = {
                repo.deleteFriend(deleting.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun FriendCard(friend: Friend, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(friend.name.ifBlank { "同学" }, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(3.dp))
                Text(
                    "已共享忙闲时间 · 点击查看三色对照",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun FreeTimeScreen(data: CampusData, friendId: String, onBack: () -> Unit) {
    val settings = data.settings
    val friend = data.friends.firstOrNull { it.id == friendId }
    val currentWeek = remember(settings.startDate, settings.totalWeeks) { Dates.currentWeek(settings) }
    var week by rememberSaveable { mutableStateOf(currentWeek) }

    val days = remember(settings.showWeekend) { if (settings.showWeekend) 1..7 else 1..5 }
    val myBusy = remember(data.courses, week) { busySlots(data.courses, week) }
    val friendBusy = remember(friend, week) { busySlots(friend?.courses ?: emptyList(), week) }
    val bothFree = (1..SECTION_COUNT).sumOf { section ->
        days.count { day -> !myBusy.contains(day * 100 + section) && !friendBusy.contains(day * 100 + section) }
    }

    ScreenScaffold(
        title = friend?.name?.ifBlank { "同学" } ?: "忙闲对照",
        subtitle = "只显示有课/没课 · 不含课程信息",
        onBack = onBack
    ) {
        if (friend == null) {
            EmptyState(title = "找不到这位同学", subtitle = "可能已经被删除。")
        } else if (settings.startDate.isBlank()) {
            EmptyState(title = "先设置开学日期", subtitle = "在「设置」里填写学期开学日期后，才能对照课表。")
        } else {
            WeekSelector(
                week = week,
                totalWeeks = settings.totalWeeks,
                currentWeek = currentWeek,
                onWeekChange = { week = it }
            )

            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(6.dp))
                LegendRow()

                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.width(30.dp))
                    for (day in days) {
                        Text(
                            "周${WEEKDAY_NAMES[day - 1]}",
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))

                for (section in 1..SECTION_COUNT) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(30.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "$section",
                            modifier = Modifier.width(30.dp),
                            textAlign = TextAlign.Center,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        for (day in days) {
                            val meBusy = myBusy.contains(day * 100 + section)
                            val taBusy = friendBusy.contains(day * 100 + section)
                            val color = when {
                                meBusy && taBusy -> CELL_BOTH_BUSY
                                meBusy || taBusy -> CELL_ONE_BUSY
                                else -> CELL_FREE
                            }
                            Box(
                                Modifier
                                    .weight(1f)
                                    .padding(1.dp)
                                    .fillMaxWidth()
                                    .height(26.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(color)
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                        RoundedCornerShape(4.dp)
                                    )
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    "本周你们都没课的时间格：$bothFree 个（白色格子）。约饭、约自习优先看白色区域。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "隐私说明：这里只根据「有课/没课」着色，不显示任何课程名、地点、教师；" +
                        "对方的课程信息从未离开过对方手机。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LegendRow() {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendItem(CELL_BOTH_BUSY, "都有课")
        LegendItem(CELL_ONE_BUSY, "一方有课")
        LegendItem(CELL_FREE, "都没课")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(14.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(3.dp))
        )
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 把课程时段展开成 (day*100+section) 的忙闲集合。 */
private fun busySlots(courses: List<Course>, week: Int): Set<Int> {
    val result = mutableSetOf<Int>()
    for (course in courses) {
        for (session in course.sessions) {
            if (!WeekRange.isActive(session.weeks, week)) continue
            val start = session.startSection.coerceIn(1, SECTION_COUNT)
            val end = session.endSection.coerceIn(start, SECTION_COUNT)
            for (section in start..end) result.add(session.dayOfWeek * 100 + section)
        }
    }
    return result
}
