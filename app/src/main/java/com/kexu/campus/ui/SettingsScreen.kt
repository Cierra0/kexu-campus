package com.kexu.campus.ui

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.kexu.campus.data.CampusData
import com.kexu.campus.data.CampusRepository
import com.kexu.campus.data.Dates
import com.kexu.campus.data.IcsExporter
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    data: CampusData,
    repo: CampusRepository,
    onOpenImport: () -> Unit,
    onOpenAdBlock: () -> Unit,
    onOpenFocus: () -> Unit,
    onOpenSchoolSync: () -> Unit
) {
    val context = LocalContext.current
    val settings = data.settings
    var pendingClear by remember { mutableStateOf(false) }
    val checkScope = androidx.compose.runtime.rememberCoroutineScope()
    var checkingUpdate by remember { mutableStateOf(false) }
    var githubOwner by remember { mutableStateOf(com.kexu.campus.data.UpdateChecker.owner(context)) }
    var githubRepo by remember { mutableStateOf(com.kexu.campus.data.UpdateChecker.repo(context)) }

    val backupPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (text == null) {
                Toast.makeText(context, "读取备份失败", Toast.LENGTH_SHORT).show()
            } else if (repo.importBackup(text)) {
                Toast.makeText(context, "备份已恢复", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "备份格式不正确", Toast.LENGTH_LONG).show()
            }
        }
    }

    ScreenScaffold(title = "设置", subtitle = "校序 · 数据只在本机") {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionCard(title = "学校与学期") {
                    OutlinedTextField(
                        value = settings.schoolName,
                        onValueChange = { value -> repo.updateSettings { it.copy(schoolName = value) } },
                        label = { Text("学校名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = settings.termName,
                        onValueChange = { value -> repo.updateSettings { it.copy(termName = value) } },
                        label = { Text("学期名称（如 2026-2027-1）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("开学日期", fontSize = 14.sp, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = {
                            val current = Dates.parse(settings.startDate) ?: LocalDate.now()
                            DatePickerDialog(
                                context,
                                { _, year, month, day ->
                                    repo.updateSettings {
                                        it.copy(startDate = String.format("%04d-%02d-%02d", year, month + 1, day))
                                    }
                                },
                                current.year,
                                current.monthValue - 1,
                                current.dayOfMonth
                            ).show()
                        }) {
                            Text(settings.startDate.ifBlank { "未设置" })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("总周数", fontSize = 14.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            repo.updateSettings { it.copy(totalWeeks = (it.totalWeeks - 1).coerceAtLeast(1)) }
                        }) { Icon(Icons.Filled.Remove, contentDescription = "减少") }
                        Text("${settings.totalWeeks} 周", fontSize = 14.sp)
                        IconButton(onClick = {
                            repo.updateSettings { it.copy(totalWeeks = (it.totalWeeks + 1).coerceAtMost(30)) }
                        }) { Icon(Icons.Filled.Add, contentDescription = "增加") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("显示周末", fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Switch(
                            checked = settings.showWeekend,
                            onCheckedChange = { value -> repo.updateSettings { it.copy(showWeekend = value) } }
                        )
                    }
                }
            }

            item {
                SectionCard(title = "外观") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to "跟随系统", 1 to "浅色", 2 to "深色").forEach { (value, label) ->
                            FilterChip(
                                selected = settings.darkMode == value,
                                onClick = { repo.updateSettings { it.copy(darkMode = value) } },
                                label = { Text(label, fontSize = 12.sp) }
                            )
                        }
                    }
                }
            }

            item {
                SectionCard(title = "分享") {
                    OutlinedTextField(
                        value = settings.shareName,
                        onValueChange = { value -> repo.updateSettings { it.copy(shareName = value) } },
                        label = { Text("分享课表时显示的名字") },
                        placeholder = { Text("例如：小董") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "只影响分享码里的署名，不会上传任何信息。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                SectionCard(title = "数据") {
                    OutlinedButton(onClick = onOpenImport, modifier = Modifier.fillMaxWidth()) {
                        Text("导入课表 / 分享课表")
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            shareText(context, "kexu-backup.json", "application/json", repo.snapshotJson())
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("导出备份（JSON）") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            shareText(
                                context,
                                "kexu-timetable.ics",
                                "text/calendar",
                                IcsExporter.build(data)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("导出课表（ICS，可导入手机日历）") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            backupPicker.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("从备份恢复") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            repo.seedDemo()
                            Toast.makeText(context, "已载入示例数据", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("载入示例数据（用于体验）") }
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { pendingClear = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("清空全部数据", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            item {
                SectionCard(title = "工具") {
                    OutlinedButton(
                        onClick = onOpenAdBlock,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("去广告（本地 DNS 过滤）") }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "仅在本机过滤广告域名，不接管其他流量；需要系统 VPN 权限，与其它 VPN 不能同时开启。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onOpenFocus,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("锁机（桌面替换 + 应用限制）") }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "锁机时桌面只显示允许的应用，打开其它应用会被拦回；支持短时解锁次数、紧急退出。需要悬浮窗与使用情况权限。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onOpenSchoolSync,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("学校教务直通（登录后读取课表/成绩）") }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "在 App 内打开学校网站，由你自己登录；登录后读取当前课表/成绩页面。可加密保存账号用于自动填充，不写入备份。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                SectionCard(title = "关于校序") {
                    Text("校序 v0.9.9 · GPL-3.0 开源", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "反馈邮箱：${com.kexu.campus.AppConfig.CONTACT_EMAIL}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        androidx.compose.material3.OutlinedButton(
                            onClick = {
                                checkScope.launch {
                                    checkingUpdate = true
                                    val latest = com.kexu.campus.data.UpdateChecker.latest(context)
                                    checkingUpdate = false
                                    if (latest == null) {
                                        Toast.makeText(
                                            context,
                                            "检查失败：仓库还没配置或网络异常",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else if (com.kexu.campus.data.UpdateChecker.isNewer(
                                            latest.version,
                                            com.kexu.campus.data.UpdateChecker.currentVersion(context)
                                        )
                                    ) {
                                        runCatching {
                                            context.startActivity(
                                                android.content.Intent(
                                                    android.content.Intent.ACTION_VIEW,
                                                    Uri.parse(latest.apkUrl.ifBlank { latest.pageUrl })
                                                )
                                            )
                                        }
                                        Toast.makeText(
                                            context,
                                            "发现新版本 ${latest.version}，已打开下载页",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "已是最新版本 ${com.kexu.campus.data.UpdateChecker.currentVersion(context)}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (checkingUpdate) "检查中…" else "检查更新", fontSize = 12.sp)
                        }
                        androidx.compose.material3.OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            Uri.parse(com.kexu.campus.data.UpdateChecker.repoUrl(context))
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("GitHub 仓库", fontSize = 12.sp) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "GitHub 仓库（用于检查更新，建好后填这里）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = githubOwner,
                            onValueChange = { githubOwner = it },
                            label = { Text("用户名", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = githubRepo,
                            onValueChange = { githubRepo = it },
                            label = { Text("仓库名", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    androidx.compose.material3.Button(
                        onClick = {
                            com.kexu.campus.data.UpdateChecker.saveRepo(context, githubOwner, githubRepo)
                            Toast.makeText(context, "已保存仓库地址，下次检查更新生效", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("保存仓库地址") }
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.Button(
                        onClick = { com.kexu.campus.data.Feedback.sendReport(context = context, data = data) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("反馈问题（发邮件）") }
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = {
                            com.kexu.campus.data.Feedback.copyDiagnostics(
                                context,
                                com.kexu.campus.data.Feedback.diagnostics(context, data)
                            )
                            Toast.makeText(context, "诊断信息已复制", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("复制诊断信息（不含账号密码）") }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "校序不保存学校账号密码到备份或服务器；自动填充所需的账号密码用 Android Keystore 加密存本机。" +
                            "课表、成绩、同学数据由你主动导入或读取，默认只保存在本机。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "请仅访问你本人的账号数据；不要批量抓取或影响学校系统；学校若有规定请遵守。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (pendingClear) {
        ConfirmDialog(
            title = "清空全部数据",
            message = "课表、成绩、同学数据都会被删除，且无法恢复。建议先导出备份。",
            confirmText = "清空",
            onConfirm = {
                repo.clearAll()
                pendingClear = false
                Toast.makeText(context, "已清空", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { pendingClear = false }
        )
    }
}

internal fun shareText(context: Context, fileName: String, mime: String, content: String) {
    runCatching {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(content)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "导出"))
    }.onFailure {
        Toast.makeText(context, "导出失败：${it.message}", Toast.LENGTH_LONG).show()
    }
}
