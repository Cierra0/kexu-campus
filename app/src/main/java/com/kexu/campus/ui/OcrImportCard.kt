package com.kexu.campus.ui

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kexu.campus.data.CampusData
import com.kexu.campus.data.CampusRepository
import com.kexu.campus.data.DocumentRecognizer
import com.kexu.campus.data.TimetableTextParser
import com.kexu.campus.data.WEEKDAY_NAMES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun OcrImportCard(
    data: CampusData,
    repo: CampusRepository,
    onImported: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    var source by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }
    var parsed by remember { mutableStateOf<List<TimetableTextParser.Item>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<Int>>(emptySet()) }

    val defaultWeeks = "1-${data.settings.totalWeeks.coerceAtLeast(1)}"

    fun reparse() {
        val items = TimetableTextParser.parse(text, defaultWeeks)
        parsed = items
        selected = items.indices.toSet()
        error = if (items.isEmpty()) {
            "没有自动解析出课程。可以把文字改成「课程名 周一 1-2节 1-16周 教1-201」这样的格式再点解析。"
        } else {
            null
        }
    }

    fun useItems(items: List<TimetableTextParser.Item>) {
        parsed = items
        selected = items.indices.toSet()
        error = if (items.isEmpty()) {
            "没有自动解析出课程。可以手动修改识别文字后再解析。"
        } else {
            null
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            working = true
            error = null
            val name = queryDisplayName(context, uri)
            val result = withContext(Dispatchers.IO) {
                DocumentRecognizer.recognize(context, uri, name)
            }
            working = false
            if (result.ok) {
                source = result.source
                text = result.text
                if (result.lines.isNotEmpty()) {
                    val imageItems = withContext(Dispatchers.Default) {
                        com.kexu.campus.data.TimetableImageParser.parse(
                            result.lines,
                            result.grid,
                            defaultWeeks
                        )
                    }
                    if (imageItems.isNotEmpty()) useItems(imageItems) else reparse()
                } else {
                    reparse()
                }
            } else {
                error = result.error
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            working = true
            error = null
            val name = queryDisplayName(context, uri)
            val result = withContext(Dispatchers.IO) {
                DocumentRecognizer.recognize(context, uri, name)
            }
            working = false
            if (result.ok) {
                source = "$source（实验）"
                text = result.text
                error = "图片 OCR 为实验功能：识别结果可能不准，请逐条核对后再导入。"
                val imageItems = withContext(Dispatchers.Default) {
                    com.kexu.campus.data.TimetableImageParser.parse(
                        result.lines,
                        result.grid,
                        defaultWeeks
                    )
                }
                if (imageItems.isNotEmpty()) useItems(imageItems) else reparse()
            } else {
                error = result.error
            }
        }
    }

    SectionCard(title = "从 Word / PDF 导入（推荐）") {
        Text(
            "① .docx 直接读表格，最准；② .pdf 会识别文字。\n" +
                "图片 OCR 已降级为实验功能（可能不准），建议优先用 Word/PDF 或让同学发分享码。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                picker.launch(
                    arrayOf(
                        "application/pdf",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "application/msword"
                    )
                )
            },
            enabled = !working,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (working) "正在识别…" else "选择 Word / PDF 导入（推荐）")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { imagePicker.launch(arrayOf("image/*")) },
            enabled = !working,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (working) "正在识别…" else "图片 OCR 导入（实验，可能不准）", fontSize = 12.sp)
        }
        if (working) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("正在提取文字（PDF/图片需要 OCR，可能要几秒）", fontSize = 12.sp)
            }
        }
        val message = error
        if (message != null) {
            Spacer(Modifier.height(8.dp))
            Text(message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }

        if (text.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                "来源：$source · 识别到 ${text.length} 个字符",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("识别出的文字（可修改）") },
                minLines = 4,
                maxLines = 10,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { reparse() }, modifier = Modifier.fillMaxWidth()) {
                Text("用当前文字重新解析")
            }
        }

        if (parsed.isNotEmpty()) {
            val courses = TimetableTextParser.toCourses(parsed)
            Spacer(Modifier.height(10.dp))
            Text(
                "解析到 ${parsed.size} 个时段 · ${courses.size} 门课，勾选后导入：",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(4.dp))
            parsed.forEachIndexed { index, item ->
                val checked = selected.contains(index)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            selected = if (checked) selected - index else selected + index
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = checked, onCheckedChange = null)
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text(item.name, fontSize = 14.sp)
                        Text(
                            "周${WEEKDAY_NAMES.getOrElse(item.dayOfWeek - 1) { "?" }} · " +
                                "第 ${item.startSection}-${item.endSection} 节 · ${item.weeks} 周" +
                                if (item.location.isNotBlank()) " · ${item.location}" else "",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val items = parsed.filterIndexed { index, _ -> selected.contains(index) }
                    val toImport = TimetableTextParser.toCourses(items)
                    if (toImport.isEmpty()) {
                        error = "请至少勾选一门课程"
                    } else {
                        repo.addCourses(toImport)
                        Toast.makeText(context, "已导入 ${toImport.size} 门课", Toast.LENGTH_SHORT).show()
                        onImported()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("导入选中课程") }
        }
    }
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index) else ""
                } else {
                    ""
                }
            } ?: ""
    }.getOrDefault("")
}
