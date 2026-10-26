package com.kexu.campus.ui

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kexu.campus.data.CampusData
import com.kexu.campus.data.CampusRepository
import com.kexu.campus.data.Course
import com.kexu.campus.data.CredentialStore
import com.kexu.campus.data.Grade
import com.kexu.campus.data.GradeTextParser
import com.kexu.campus.data.JwglxtKbParser
import com.kexu.campus.data.SchoolDirectory
import com.kexu.campus.data.SchoolProfile
import com.kexu.campus.data.SchoolProfileStore
import com.kexu.campus.data.SchoolSite
import com.kexu.campus.data.TimetableTextParser
import com.kexu.campus.data.ZhengfangSource

/**
 * 学校教务直通：
 * - 用户搜索/选择学校 → 打开学校官网或办事大厅 → 在 WebView 里自己登录；
 * - 进入课表/成绩页面后点「读取」，App 只取当前页面表格文字，解析预览后由用户确认导入；
 * - App 不保存账号密码、不后台抓取、不上传页面内容。
 */
@Composable
fun SchoolSyncScreen(
    data: CampusData,
    repo: CampusRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val store = remember { CredentialStore(context) }
    var query by rememberSaveable { mutableStateOf("") }
    var customUrl by rememberSaveable {
        val saved = data.settings.syncEntryUrl
        mutableStateOf(
            when {
                saved.isBlank() -> "https://"
                saved.contains("ehall.ecut.edu.cn") -> "https://atrust1.ecut.edu.cn"
                else -> saved
            }
        )
    }
    var lastSchool by remember { mutableStateOf(data.settings.syncSchoolName) }
    var username by remember { mutableStateOf(store.username()) }
    var password by remember { mutableStateOf(store.password()) }
    var consent by remember { mutableStateOf(store.hasAccount()) }
    var savedAccount by remember { mutableStateOf(store.hasAccount()) }
    var timetableTarget by remember {
        mutableStateOf(store.target("timetable").takeIf { !it.contains("ehall") } ?: "")
    }
    var gradesTarget by remember {
        mutableStateOf(store.target("grades").takeIf { !it.contains("ehall") } ?: "")
    }
    var profile by remember { mutableStateOf(SchoolProfileStore.load(context)) }
    var customSchoolInput by rememberSaveable { mutableStateOf("") }
    val isEcut = profile.isEcut
    var extractedText by remember { mutableStateOf("") }
    var extractedKind by remember { mutableStateOf("") }
    var timetableItems by remember { mutableStateOf<List<TimetableTextParser.Item>>(emptyList()) }
    var gradeItems by remember { mutableStateOf<List<GradeTextParser.Item>>(emptyList()) }
    var pendingCourses by remember { mutableStateOf<List<Course>>(emptyList()) }
    var pendingGrades by remember { mutableStateOf<List<Grade>>(emptyList()) }
    var showRaw by remember { mutableStateOf(false) }

    val webLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // 用户可能在 WebView 里保存了同步目标后直接返回（resultCode=CANCELED），这里统一刷新状态
        timetableTarget = store.target("timetable")
        gradesTarget = store.target("grades")
        savedAccount = store.hasAccount()
        if (result.resultCode == Activity.RESULT_OK) {
            extractedText = result.data?.getStringExtra(SchoolWebViewActivity.EXTRA_TEXT).orEmpty()
            extractedKind = result.data?.getStringExtra(SchoolWebViewActivity.EXTRA_KIND).orEmpty()
            if (extractedText.isBlank()) {
                Toast.makeText(context, "没有读到内容，请确认已登录并打开课表/成绩页面", Toast.LENGTH_LONG).show()
            } else if (extractedKind == "qzgrades") {
                val parsed = com.kexu.campus.data.QiangzhiGradesParser.parse(extractedText)
                if (parsed.grades.isEmpty()) {
                    Toast.makeText(
                        context,
                        "强智成绩表为空：${parsed.warnings.firstOrNull() ?: "请确认在成绩查询页"}",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    pendingGrades = parsed.grades
                    Toast.makeText(
                        context,
                        "解析到 ${parsed.grades.size} 条成绩，请在下方确认导入",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else if (extractedKind == "qzkbjson") {
                val parsed = com.kexu.campus.data.QiangzhiTimetableParser.parse(extractedText)
                if (parsed.courses.isEmpty()) {
                    Toast.makeText(
                        context,
                        "强智课表解析为空：${parsed.warnings.firstOrNull() ?: "可换用读取课表/剪贴板"}",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    pendingCourses = parsed.courses
                    Toast.makeText(
                        context,
                        "解析到 ${parsed.courses.size} 门课，请在下方确认导入",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else if (extractedKind == "kbjson") {
                val parsed = JwglxtKbParser.parse(extractedText)
                if (parsed.ok()) {
                    pendingCourses = parsed.courses
                    Toast.makeText(
                        context,
                        "解析到 ${parsed.courseCount} 门课（${parsed.rawRecords} 条时段），请在下方确认导入",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        "识别到正方数据但解析为空：${parsed.warnings.firstOrNull() ?: "请确认学期"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else if (extractedKind == "timetable") {
                timetableItems = TimetableTextParser.parse(
                    extractedText,
                    "1-${data.settings.totalWeeks.coerceAtLeast(1)}"
                )
            } else {
                gradeItems = GradeTextParser.parse(extractedText)
            }
        }
    }

    val ecutLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val count = result.data?.getIntExtra(EcutSyncActivity.EXTRA_COUNT, 0) ?: 0
        val message = result.data?.getStringExtra(EcutSyncActivity.EXTRA_MESSAGE).orEmpty()
        if (count > 0) {
            Toast.makeText(context, "同步成功：$message", Toast.LENGTH_LONG).show()
        } else if (message.isNotBlank()) {
            Toast.makeText(context, "同步未完成：$message", Toast.LENGTH_LONG).show()
        }
    }

    fun openUrl(
        url: String,
        name: String,
        autoLogin: Boolean = false,
        autoRead: Boolean = false,
        target: String = ""
    ) {
        if (url.isBlank()) return
        repo.updateSettings { it.copy(syncEntryUrl = url, syncSchoolName = name) }
        lastSchool = name
        webLauncher.launch(
            Intent(context, SchoolWebViewActivity::class.java).apply {
                putExtra(SchoolWebViewActivity.EXTRA_URL, url)
                putExtra(SchoolWebViewActivity.EXTRA_TITLE, name)
                putExtra(SchoolWebViewActivity.EXTRA_AUTO_LOGIN, autoLogin)
                putExtra(SchoolWebViewActivity.EXTRA_AUTO_READ, autoRead)
                putExtra(SchoolWebViewActivity.EXTRA_TARGET, target)
            }
        )
    }

    fun saveAccount() {
        val entry = customUrl.trim()
        if (!entry.startsWith("http")) {
            Toast.makeText(context, "先填写 http(s) 开头的入口网址", Toast.LENGTH_SHORT).show()
            return
        }
        if (username.isBlank() || password.isBlank()) {
            Toast.makeText(context, "请填写账号和密码", Toast.LENGTH_SHORT).show()
            return
        }
        if (!consent) {
            Toast.makeText(context, "请先勾选确认", Toast.LENGTH_SHORT).show()
            return
        }
        store.saveAccount(entry, username.trim(), password)
        repo.updateSettings { it.copy(syncEntryUrl = entry) }
        savedAccount = true
        Toast.makeText(context, "账号已加密保存在本机", Toast.LENGTH_SHORT).show()
    }

    fun startSync(kind: String) {
        if (!store.hasAccount()) {
            Toast.makeText(context, "先在上面保存教务账号", Toast.LENGTH_LONG).show()
            return
        }
        val entry = customUrl.trim().ifBlank { store.entryUrl() }
        val target = store.target(kind).takeIf { !it.contains("ehall") } ?: ""
        val start = target.ifBlank { entry }
        if (start.isBlank()) {
            Toast.makeText(context, "先填写入口网址，或登录后把课表页设为同步目标", Toast.LENGTH_LONG).show()
            return
        }
        if (target.isBlank()) {
            Toast.makeText(
                context,
                "还没有保存课表页：登录后进入课表页面，点「设为课表页」，下次就能一键同步",
                Toast.LENGTH_LONG
            ).show()
        }
        openUrl(start, lastSchool.ifBlank { "教务同步" }, autoLogin = true, autoRead = target.isNotBlank(), target = kind)
    }

    ScreenScaffold(
        title = "学校教务直通",
        subtitle = "自己登录 · 手动读取 · 只存本机",
        onBack = onBack
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionCard(title = "使用说明与边界") {
                    Text(
                        "1. 选择学校，打开官网/办事大厅，在页面里用你本人的账号登录；App 不接触账号密码。\n" +
                            "2. 进入课表或成绩页面后，点顶部「读取课表 / 读取成绩」。\n" +
                            "3. 识别结果会先给你预览，确认后才写入本机。\n" +
                            "4. 请仅用于个人学习管理；不要批量抓取、不要影响学校系统；学校若有规定请遵守。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                SectionCard(title = "教务账号（本机加密保存）") {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("学号 / 用户名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { consent = !consent }
                    ) {
                        Checkbox(checked = consent, onCheckedChange = { consent = it })
                        Text(
                            "我确认只保存本人的教务账号；密码用 Android Keystore 加密存本机，不会写入备份或上传",
                            fontSize = 12.sp
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { saveAccount() }, enabled = consent) {
                            Text(if (savedAccount) "更新保存" else "加密保存账号")
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = {
                            store.clear()
                            savedAccount = false
                            consent = false
                            username = ""
                            password = ""
                            timetableTarget = ""
                            gradesTarget = ""
                            Toast.makeText(context, "已清除本机保存的教务账号", Toast.LENGTH_SHORT).show()
                        }) { Text("清除账号") }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (savedAccount) {
                            "已保存账号。同步时只用于自动填充学校登录页，遇到验证码/统一身份认证需你手动完成。"
                        } else {
                            "还没有保存账号。入口 URL 填在下方「打开学校教务」里，保存后会记住。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                SectionCard(title = "一键同步（点一下才读取）") {
                    Text(
                        if (timetableTarget.isBlank()) "课表页：未设置——先登录，进入课表页后点「设为课表页」"
                        else "课表页：已保存",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (gradesTarget.isBlank()) "成绩页：未设置" else "成绩页：已保存",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (isEcut) {
                                    ZhengfangSource.timetableUrl(profile)?.let { url ->
                                        openUrl(url, profile.name, autoLogin = store.hasAccount())
                                    } ?: startSync("timetable")
                                } else {
                                    startSync("timetable")
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isEcut) "登录正方·同步课表" else "一键同步课表")
                        }
                        OutlinedButton(
                            onClick = {
                                if (isEcut) {
                                    ZhengfangSource.gradesUrl(profile)?.let { url ->
                                        openUrl(url, profile.name, autoLogin = store.hasAccount())
                                    } ?: startSync("grades")
                                } else {
                                    startSync("grades")
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("同步成绩")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (isEcut) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                val url = readClipboard(context).trim()
                                if (!url.startsWith("http")) {
                                    Toast.makeText(
                                        context,
                                        "先在 Chrome 复制课表页地址栏 URL",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    openUrl(url, profile.name, autoLogin = store.hasAccount())
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("从剪贴板 URL 打开课表页") }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "如果登录页/工作台空白：用 Chrome 登录后复制课表页地址栏 URL，这里直接打开，然后点顶部「导入此页课表」。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                if (profile.isEcut) {
                    SectionCard(title = "东华理工：直接登录正方（推荐）") {
                        Text(
                            "不要走 aTrust 工作台（在部分手机 WebView 里会空白）。这里直接打开正方教务，" +
                                "登录后点顶部「导入此页课表/成绩」即可。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    ZhengfangSource.timetableUrl(profile)?.let { url ->
                                        openUrl(url, profile.name, autoLogin = store.hasAccount())
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("登录正方·课表") }
                            OutlinedButton(
                                onClick = {
                                    ZhengfangSource.gradesUrl(profile)?.let { url ->
                                        openUrl(url, profile.name, autoLogin = store.hasAccount())
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("登录正方·成绩") }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "如果登录后又被弹回登录页：退出其他设备上的 aTrust 会话再试；进入正方页面后点「导入此页课表」。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                if (pendingCourses.isNotEmpty() || pendingGrades.isNotEmpty()) {
                    SectionCard(title = "待导入预览（确认后才写入本机）") {
                        if (pendingCourses.isNotEmpty()) {
                            Text(
                                "课表：${pendingCourses.size} 门课",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            pendingCourses.take(6).forEach { course ->
                                Text(
                                    "· ${course.name}（${course.sessions.size} 个时段）",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (pendingCourses.size > 6) {
                                Text("…等 ${pendingCourses.size} 门", fontSize = 12.sp)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        repo.replaceSyncedCourses(pendingCourses)
                                        Toast.makeText(context, "已导入 ${pendingCourses.size} 门课", Toast.LENGTH_LONG).show()
                                        pendingCourses = emptyList()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text("确认导入课表") }
                                TextButton(onClick = { pendingCourses = emptyList() }) { Text("放弃") }
                            }
                        }
                        if (pendingGrades.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "成绩：${pendingGrades.size} 条",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            pendingGrades.take(6).forEach { grade ->
                                Text(
                                    "· ${grade.courseName} · ${grade.credit} 学分 · ${grade.score} 分",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (pendingGrades.size > 6) Text("…等 ${pendingGrades.size} 条", fontSize = 12.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        repo.upsertGrades(pendingGrades)
                                        Toast.makeText(context, "已导入 ${pendingGrades.size} 条成绩", Toast.LENGTH_LONG).show()
                                        pendingGrades = emptyList()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text("确认导入成绩") }
                                TextButton(onClick = { pendingGrades = emptyList() }) { Text("放弃") }
                            }
                        }
                    }
                }
            }

            item {
                SectionCard(title = "我的学校 / 自定义学校") {
                    Text(
                        "当前：${profile.name} · ${profile.vendorLabel} · ${profile.accessLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (profile.jwBase.isNotBlank()) {
                        Text(
                            "教务地址：${profile.jwBase}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customSchoolInput,
                        onValueChange = { customSchoolInput = it },
                        label = { Text("粘贴学校教务网址（如 …/jwglxt/ 或 …/jsxsd/）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val p = SchoolProfile.custom(customSchoolInput)
                                if (p == null) {
                                    Toast.makeText(context, "网址无效，请填完整教务网址", Toast.LENGTH_LONG).show()
                                } else {
                                    profile = p
                                    SchoolProfileStore.save(context, p)
                                    Toast.makeText(
                                        context,
                                        "已识别为「${p.vendorLabel}」：${p.jwBase}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("识别并保存") }
                        TextButton(
                            onClick = {
                                profile = SchoolProfile.ECUT
                                SchoolProfileStore.save(context, SchoolProfile.ECUT)
                                Toast.makeText(context, "已恢复东华理工", Toast.LENGTH_SHORT).show()
                            }
                        ) { Text("恢复东华理工") }
                    }
                    if (profile.supportsSync) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                openUrl(
                                    profile.loginUrl.ifBlank { profile.jwBase },
                                    profile.name
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("登录并导入此页课表") }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "打开后登录学校教务，进入课表页，点顶部「导入此页课表」即可读取正方数据。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "暂不支持这个教务系统：可以打开网页后用「读取课表」或剪贴板导入。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                SectionCard(title = "打开学校教务") {
                    OutlinedTextField(
                        value = customUrl,
                        onValueChange = { customUrl = it },
                        label = { Text("自定义入口 URL（官网 / 办事大厅 / 教务系统）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val url = customUrl.trim()
                            if (!url.startsWith("http")) {
                                Toast.makeText(context, "请填写 http(s) 开头的网址", Toast.LENGTH_SHORT).show()
                            } else {
                                openUrl(url, "自定义入口")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("打开并登录") }
                    if (lastSchool.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "上次使用：$lastSchool",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                SectionCard(title = "网页打不开？从剪贴板识别") {
                    Text(
                        "在系统浏览器（Chrome/Edge 等）登录 aTrust 或教务系统，打开课表/成绩页面，长按复制页面文字，" +
                            "回到这里读取剪贴板即可解析导入。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val text = readClipboard(context)
                                if (text.isBlank()) {
                                    Toast.makeText(context, "剪贴板是空的，先在浏览器里复制页面文字", Toast.LENGTH_LONG).show()
                                } else {
                                    extractedText = text
                                    extractedKind = "timetable"
                                    timetableItems = TimetableTextParser.parse(
                                        text,
                                        "1-${data.settings.totalWeeks.coerceAtLeast(1)}"
                                    )
                                    if (timetableItems.isEmpty()) {
                                        Toast.makeText(
                                            context,
                                            "没解析出课程，可把文字改成「课程名 周一 1-2节 1-16周 地点」再试",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("识别课表文字") }
                        OutlinedButton(
                            onClick = {
                                val text = readClipboard(context)
                                if (text.isBlank()) {
                                    Toast.makeText(context, "剪贴板是空的", Toast.LENGTH_LONG).show()
                                } else {
                                    extractedText = text
                                    extractedKind = "grades"
                                    gradeItems = GradeTextParser.parse(text)
                                    if (gradeItems.isEmpty()) {
                                        Toast.makeText(context, "没解析出成绩，可以换一段表格文字再试", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("识别成绩文字") }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("搜索学校（输入校名）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            items(SchoolDirectory.search(query), key = { it.name }) { school ->
                SchoolRow(
                    school = school,
                    onOpenSite = { openUrl(school.siteUrl, school.name) },
                    onOpenEntry = if (school.entryUrl.isNotBlank()) {
                        { openUrl(school.entryUrl, school.name) }
                    } else {
                        null
                    }
                )
            }

            if (extractedText.isNotBlank()) {
                item {
                    SectionCard(title = if (extractedKind == "timetable") "课表识别结果" else "成绩识别结果") {
                        if (extractedKind == "timetable") {
                            Text(
                                "识别到 ${timetableItems.size} 个时段 · ${TimetableTextParser.toCourses(timetableItems).size} 门课",
                                fontWeight = FontWeight.SemiBold
                            )
                            timetableItems.take(8).forEach { item ->
                                Text(
                                    "${item.name} · 周${item.dayOfWeek} 第${item.startSection}-${item.endSection}节 · ${item.weeks}周",
                                    fontSize = 12.sp
                                )
                            }
                            if (timetableItems.size > 8) Text("…等 ${timetableItems.size} 条", fontSize = 12.sp)
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    val courses = TimetableTextParser.toCourses(timetableItems)
                                    if (courses.isEmpty()) {
                                        Toast.makeText(context, "没有可导入的课程", Toast.LENGTH_SHORT).show()
                                    } else {
                                        repo.addCourses(courses)
                                        Toast.makeText(context, "已导入 ${courses.size} 门课", Toast.LENGTH_SHORT).show()
                                        onBack()
                                    }
                                },
                                enabled = timetableItems.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("导入识别到的课程") }
                        } else {
                            Text(
                                "识别到 ${gradeItems.size} 条成绩",
                                fontWeight = FontWeight.SemiBold
                            )
                            gradeItems.take(8).forEach { item ->
                                Text(
                                    "${item.name} · ${item.credit} 学分 · ${item.score} 分",
                                    fontSize = 12.sp
                                )
                            }
                            if (gradeItems.size > 8) Text("…等 ${gradeItems.size} 条", fontSize = 12.sp)
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    if (gradeItems.isEmpty()) {
                                        Toast.makeText(context, "没有可导入的成绩", Toast.LENGTH_SHORT).show()
                                    } else {
                                        gradeItems.forEach { item ->
                                            repo.upsertGrade(
                                                Grade(
                                                    termName = data.settings.termName,
                                                    courseName = item.name,
                                                    credit = item.credit,
                                                    score = item.score
                                                )
                                            )
                                        }
                                        Toast.makeText(context, "已导入 ${gradeItems.size} 条成绩", Toast.LENGTH_SHORT).show()
                                        onBack()
                                    }
                                },
                                enabled = gradeItems.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("导入识别到的成绩") }
                        }
                        Spacer(Modifier.height(4.dp))
                        TextButton(onClick = { showRaw = !showRaw }) {
                            Text(if (showRaw) "收起原始文字" else "查看原始文字")
                        }
                        if (showRaw) {
                            Text(extractedText.take(3000), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SchoolRow(
    school: SchoolSite,
    onOpenSite: () -> Unit,
    onOpenEntry: (() -> Unit)?
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(school.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(
                    "${school.region} · ${school.siteUrl}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = onOpenSite) { Text("官网", fontSize = 12.sp) }
            if (onOpenEntry != null) {
                Spacer(Modifier.width(6.dp))
                Button(onClick = onOpenEntry) { Text("教务入口", fontSize = 12.sp) }
            }
        }
    }
}

private fun readClipboard(context: Context): String = runCatching {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
}.getOrDefault("")
