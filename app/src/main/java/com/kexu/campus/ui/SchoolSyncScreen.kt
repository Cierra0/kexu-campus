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
import com.kexu.campus.data.SchoolEntry
import com.kexu.campus.data.SchoolProfile
import com.kexu.campus.data.SchoolProfileStore
import com.kexu.campus.data.TimetableTextParser

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
    var customSchoolName by rememberSaveable { mutableStateOf("") }
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

    fun openUrl(
        url: String,
        name: String,
        autoLogin: Boolean = false,
        autoRead: Boolean = false,
        target: String = "",
        autoEhall: Boolean = false,
        ehallTarget: String = "timetable"
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
                putExtra(SchoolWebViewActivity.EXTRA_AUTO_EHALL, autoEhall)
                putExtra(SchoolWebViewActivity.EXTRA_EHALL_TARGET, ehallTarget)
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
                SectionCard(title = "登录教务系统") {
                    Text(
                        "当前：${profile.name} · ${profile.vendorLabel} · ${profile.accessLabel}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "入口：${profile.loginUrl.ifBlank { profile.jwBase }}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                openUrl(
                                    profile.loginUrl.ifBlank { profile.jwBase },
                                    profile.name,
                                    autoLogin = store.hasAccount(),
                                    autoEhall = profile.isEcut,
                                    ehallTarget = "timetable"
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("登录教务·课表") }
                        OutlinedButton(
                            onClick = {
                                openUrl(
                                    profile.loginUrl.ifBlank { profile.jwBase },
                                    profile.name,
                                    autoLogin = store.hasAccount(),
                                    autoEhall = profile.isEcut,
                                    ehallTarget = "grades"
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("登录教务·成绩") }
                    }
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { startSync("timetable") }) {
                        Text("按已保存页面导入", fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "登录后进入办事大厅/教务系统，打开课表或成绩页，点顶部「导入此页课表/成绩」。",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (timetableTarget.isNotBlank() || gradesTarget.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "已保存页面：课表${if (timetableTarget.isBlank()) "未设置" else "已设置"} · " +
                                "成绩${if (gradesTarget.isBlank()) "未设置" else "已设置"}",
                            fontSize = 11.sp,
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
                        value = customSchoolName,
                        onValueChange = { customSchoolName = it },
                        label = { Text("学校名称（可选，如 华东理工大学）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
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
                                val p = SchoolProfile.custom(customSchoolInput, customSchoolName)
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
                SectionCard(title = "选择学校（收录 1515 所教务入口）") {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("搜学校名，如 华东理工") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("东华理工大学", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                "已适配 · 烫梨模式（办事大厅代理）",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Button(
                            onClick = {
                                profile = SchoolProfile.ECUT
                                SchoolProfileStore.save(context, SchoolProfile.ECUT)
                                Toast.makeText(context, "已选择东华理工大学", Toast.LENGTH_SHORT).show()
                            }
                        ) { Text("使用", fontSize = 12.sp) }
                    }
                    Spacer(Modifier.height(4.dp))
                    val results = SchoolDirectory.search(context, query, 30)
                    results.forEach { entry ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(
                                    "${entry.vendorLabel} · ${entry.loginUrl.take(70)}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = {
                                    val p = SchoolProfile.custom(entry.loginUrl, entry.name, entry.vendor)
                                    if (p == null) {
                                        Toast.makeText(context, "这个网址无效，请手动输入", Toast.LENGTH_SHORT).show()
                                    } else {
                                        profile = p
                                        SchoolProfileStore.save(context, p)
                                        Toast.makeText(context, "已选择 ${entry.name}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) { Text("使用", fontSize = 12.sp) }
                        }
                    }
                    if (results.isEmpty()) {
                        Text(
                            "没找到？换短一点的关键词，或在下面手动输入教务网址。",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
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

private fun readClipboard(context: Context): String = runCatching {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
}.getOrDefault("")
