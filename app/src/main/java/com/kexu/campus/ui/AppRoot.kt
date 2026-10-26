package com.kexu.campus.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kexu.campus.AppConfig
import com.kexu.campus.data.CampusData
import com.kexu.campus.data.CampusRepository
import com.kexu.campus.data.Feedback
import com.kexu.campus.data.UpdateChecker
import android.content.Context
import android.content.Intent
import android.net.Uri

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabItem("timetable", "课表", Icons.Outlined.CalendarMonth),
    TabItem("grades", "成绩", Icons.Outlined.School),
    TabItem("friends", "同学", Icons.Outlined.People),
    TabItem("tools", "工具", Icons.Outlined.Build),
    TabItem("settings", "设置", Icons.Outlined.Settings)
)

@Composable
fun AppRoot(repo: CampusRepository, data: CampusData) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = tabs.any { it.route == currentRoute }
    val context = LocalContext.current
    var updateRelease by remember { mutableStateOf<UpdateChecker.Release?>(null) }
    var crashText by remember { mutableStateOf("") }
    var showOnboarding by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val onboarded = context.getSharedPreferences("kexu_onboarding", Context.MODE_PRIVATE)
            .getBoolean("done", false)
        if (!onboarded) showOnboarding = true
    }

    LaunchedEffect(Unit) {
        crashText = Feedback.readAndClearCrash(context)
        val prefs = context.getSharedPreferences("kexu_update", Context.MODE_PRIVATE)
        val last = prefs.getLong("last_check", 0L)
        if (System.currentTimeMillis() - last >= AppConfig.UPDATE_CHECK_INTERVAL_MS) {
            prefs.edit().putLong("last_check", System.currentTimeMillis()).apply()
            val latest = UpdateChecker.latest(context)
            val ignored = prefs.getString("ignored_version", "").orEmpty()
            if (latest != null &&
                latest.version != ignored &&
                UpdateChecker.isNewer(latest.version, UpdateChecker.currentVersion(context))
            ) {
                updateRelease = latest
            }
        }
    }

    updateRelease?.let { release ->
        AlertDialog(
            onDismissRequest = { updateRelease = null },
            title = { Text("发现新版本 ${release.version}") },
            text = {
                Text(
                    release.notes.ifBlank { "建议更新到最新版本，修复已知问题。" },
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val url = release.apkUrl.ifBlank { release.pageUrl }
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                    updateRelease = null
                }) { Text("去下载") }
            },
            dismissButton = {
                TextButton(onClick = {
                    context.getSharedPreferences("kexu_update", Context.MODE_PRIVATE)
                        .edit().putString("ignored_version", release.version).apply()
                    updateRelease = null
                }) { Text("忽略此版本") }
            }
        )
    }

    if (showOnboarding) {
        AlertDialog(
            onDismissRequest = { showOnboarding = false },
            title = { Text("欢迎使用校序") },
            text = {
                Text(
                    "导入课表只需要三步：\n\n" +
                        "1. 选择学校：内置 1515 所高校的教务入口，也可手动输入学校名和教务网址\n" +
                        "2. 登录教务系统：在 App 内登录（遇到验证码/短信验证按提示完成）\n" +
                        "3. 导入课表：进入课表页后点顶部「导入此页课表」，先预览、确认后写入\n\n" +
                        "也可以在「工具 → 导入/分享」用文件、图片 OCR 或分享码导入。",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    context.getSharedPreferences("kexu_onboarding", Context.MODE_PRIVATE)
                        .edit().putBoolean("done", true).apply()
                    showOnboarding = false
                    runCatching { navController.navigate("schoolSync") }
                }) { Text("去选择学校") }
            },
            dismissButton = {
                TextButton(onClick = {
                    context.getSharedPreferences("kexu_onboarding", Context.MODE_PRIVATE)
                        .edit().putBoolean("done", true).apply()
                    showOnboarding = false
                }) { Text("先看看") }
            }
        )
    }

    if (crashText.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { crashText = "" },
            title = { Text("检测到上次异常退出") },
            text = { Text("是否把诊断信息发到开发者邮箱？不会包含账号密码、Cookie 或课表内容。") },
            confirmButton = {
                TextButton(onClick = {
                    Feedback.sendReport(context, data, crash = crashText)
                    crashText = ""
                }) { Text("发送邮件") }
            },
            dismissButton = {
                TextButton(onClick = {
                    Feedback.copyDiagnostics(context, Feedback.diagnostics(context, data, crashText))
                    crashText = ""
                }) { Text("复制诊断信息") }
            }
        )
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                if (currentRoute != tab.route) {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "timetable",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("timetable") {
                TimetableScreen(
                    data = data,
                    repo = repo,
                    onEditCourse = { id -> navController.navigate("courseEdit?courseId=$id") },
                    onAddAt = { day, section ->
                        navController.navigate("courseEdit?day=$day&section=$section")
                    },
                    onOpenImport = { navController.navigate("import") },
                    onOpenSettings = { navController.navigate("settings") },
                    onOpenSync = { navController.navigate("schoolSync") }
                )
            }

            composable("grades") {
                GradesScreen(data = data, repo = repo)
            }

            composable("ledger") {
                LedgerScreen(data = data, repo = repo)
            }

            composable("tools") {
                ToolsScreen(
                    data = data,
                    onOpenLedger = { navController.navigate("ledger") },
                    onOpenAdBlock = { navController.navigate("adblock") },
                    onOpenLock = { navController.navigate("focus") },
                    onOpenSchoolSync = { navController.navigate("schoolSync") }
                )
            }

            composable("schoolSync") {
                SchoolSyncScreen(
                    data = data,
                    repo = repo,
                    onBack = { navController.popBackStack() }
                )
            }

            composable("friends") {
                FriendsScreen(
                    data = data,
                    repo = repo,
                    onAddFriend = { navController.navigate("import?friend=true") },
                    onOpenFreeTime = { id -> navController.navigate("freeTime/$id") }
                )
            }

            composable("settings") {
                SettingsScreen(
                    data = data,
                    repo = repo,
                    onOpenImport = { navController.navigate("import") },
                    onOpenAdBlock = { navController.navigate("adblock") },
                    onOpenFocus = { navController.navigate("focus") },
                    onOpenSchoolSync = { navController.navigate("schoolSync") }
                )
            }

            composable("adblock") {
                AdBlockScreen(
                    data = data,
                    repo = repo,
                    onBack = { navController.popBackStack() }
                )
            }

            composable("focus") {
                FocusScreen(
                    data = data,
                    repo = repo,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "courseEdit?courseId={courseId}&day={day}&section={section}",
                arguments = listOf(
                    navArgument("courseId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("day") {
                        type = NavType.IntType
                        defaultValue = 1
                    },
                    navArgument("section") {
                        type = NavType.IntType
                        defaultValue = 1
                    }
                )
            ) { entry ->
                CourseEditScreen(
                    courseId = entry.arguments?.getString("courseId"),
                    presetDay = entry.arguments?.getInt("day") ?: 1,
                    presetSection = entry.arguments?.getInt("section") ?: 1,
                    data = data,
                    onSave = {
                        repo.upsertCourse(it)
                        navController.popBackStack()
                    },
                    onDelete = {
                        repo.deleteCourse(it)
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "import?friend={friend}",
                arguments = listOf(
                    navArgument("friend") {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                )
            ) { entry ->
                ImportScreen(
                    data = data,
                    repo = repo,
                    friendMode = entry.arguments?.getBoolean("friend") ?: false,
                    onBack = { navController.popBackStack() },
                    onManualAdd = { navController.navigate("courseEdit?day=1&section=1") }
                )
            }

            composable(
                route = "freeTime/{friendId}",
                arguments = listOf(navArgument("friendId") { type = NavType.StringType })
            ) { entry ->
                FreeTimeScreen(
                    data = data,
                    friendId = entry.arguments?.getString("friendId") ?: "",
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
