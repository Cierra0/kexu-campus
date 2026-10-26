package com.kexu.campus.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.kexu.campus.data.CampusData
import com.kexu.campus.data.CampusRepository
import com.kexu.campus.data.CsvImporter
import com.kexu.campus.data.DecodedShare
import com.kexu.campus.data.Friend
import com.kexu.campus.data.ShareCode
import java.util.UUID

@Composable
fun ImportScreen(
    data: CampusData,
    repo: CampusRepository,
    friendMode: Boolean,
    onBack: () -> Unit,
    onManualAdd: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var importText by rememberSaveable { mutableStateOf("") }
    var decoded by remember { mutableStateOf<DecodedShare?>(null) }
    var csvResult by remember { mutableStateOf<CsvImporter.Result?>(null) }
    var showTemplate by rememberSaveable { mutableStateOf(false) }

    val shareCode = remember(data.courses, data.settings.shareName) {
        if (data.courses.isEmpty()) "" else ShareCode.encode(data.settings.shareName, data.courses)
    }
    val qrBitmap = remember(shareCode) { if (shareCode.isBlank()) null else makeQr(shareCode) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (text == null) {
                Toast.makeText(context, "读取文件失败", Toast.LENGTH_SHORT).show()
            } else {
                csvResult = CsvImporter.parse(text, "1-${data.settings.totalWeeks.coerceAtLeast(1)}")
            }
        }
    }

    ScreenScaffold(
        title = if (friendMode) "添加同学" else "导入与分享",
        subtitle = "数据只保存在本机 · 不登录任何学校系统",
        onBack = onBack
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!friendMode) {
                item {
                    OcrImportCard(data = data, repo = repo, onImported = onBack)
                }
                item {
                    SectionCard(title = "分享我的课表") {
                        if (data.courses.isEmpty()) {
                            Text("还没有课程，先导入或添加课程后再分享。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text(
                                "对方用「校序」扫码或粘贴分享码即可导入。分享的是「有课/没课」的时间格，" +
                                    "不含课程名、地点、教师、学分。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            if (qrBitmap != null) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        bitmap = qrBitmap,
                                        contentDescription = "课表二维码",
                                        modifier = Modifier
                                            .size(220.dp)
                                            .background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(12.dp))
                                            .padding(8.dp)
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = shareCode,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("分享码（也可直接发文字）") },
                                maxLines = 3,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(shareCode))
                                        Toast.makeText(context, "已复制分享码", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("复制")
                                }
                                Button(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, "我的课表，用「校序」导入：\n$shareCode")
                                        }
                                        runCatching { context.startActivity(Intent.createChooser(intent, "分享课表")) }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.Share, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("发送")
                                }
                            }
                        }
                    }
                }
            }

            item {
                SectionCard(title = if (friendMode) "粘贴同学的分享码" else "导入分享码") {
                    OutlinedTextField(
                        value = importText,
                        onValueChange = {
                            importText = it
                            decoded = null
                        },
                        label = { Text("把同学发来的整段消息粘贴到这里") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                val result = ShareCode.decode(importText)
                                decoded = result
                                if (result == null) {
                                    Toast.makeText(context, "没有识别到课表分享码", Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("解析") }
                        OutlinedButton(
                            onClick = {
                                val clip = clipboard.getText()
                                if (clip != null && clip.text.isNotBlank()) {
                                    importText = clip.text
                                    decoded = ShareCode.decode(clip.text)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("读剪贴板") }
                    }

                    val preview = decoded
                    if (preview != null) {
                        Spacer(Modifier.height(12.dp))
                        val busyOnly = preview.courses.all { it.name.isBlank() }
                        Text(
                            if (busyOnly) {
                                "来自「${preview.name.ifBlank { "同学" }}」 · 共享忙闲时间 · ${preview.courses.sumOf { it.sessions.size }} 个时段"
                            } else {
                                "来自「${preview.name.ifBlank { "同学" }}」 · ${preview.courses.size} 门课 · ${preview.courses.sumOf { it.sessions.size }} 个时段"
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                        if (busyOnly) {
                            Text(
                                "对方没有共享课程名称，只共享了有课/没课的时间。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            preview.courses.take(6).forEach { course ->
                                Text("· ${course.name}", style = MaterialTheme.typography.bodySmall)
                            }
                            if (preview.courses.size > 6) {
                                Text("等 ${preview.courses.size} 门", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    repo.addFriend(
                                        Friend(
                                            id = UUID.randomUUID().toString(),
                                            name = preview.name.ifBlank { "同学" },
                                            courses = preview.courses
                                        )
                                    )
                                    Toast.makeText(context, "已添加同学课表", Toast.LENGTH_SHORT).show()
                                    onBack()
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("存为同学") }
                            OutlinedButton(
                                onClick = {
                                    repo.addCourses(preview.courses)
                                    Toast.makeText(context, "已合并到我的课表", Toast.LENGTH_SHORT).show()
                                    onBack()
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("并入我的") }
                        }
                    }
                }
            }

            if (!friendMode) {
                item {
                    SectionCard(title = "从 CSV 文件导入") {
                        Text(
                            "列顺序：课程名,教师,星期,开始节,结束节,周次,地点,学分（同一门课的多个时段会自动合并）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    filePicker.launch(
                                        arrayOf(
                                            "text/*",
                                            "text/comma-separated-values",
                                            "text/csv",
                                            "application/vnd.ms-excel"
                                        )
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("选择 CSV 文件") }
                            OutlinedButton(
                                onClick = { showTemplate = !showTemplate },
                                modifier = Modifier.weight(1f)
                            ) { Text(if (showTemplate) "收起模板" else "查看模板") }
                        }
                        if (showTemplate) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                CsvImporter.template(),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val result = csvResult
                        if (result != null) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "识别到 ${result.courses.size} 门课 · ${result.courses.sumOf { it.sessions.size }} 个时段",
                                fontWeight = FontWeight.SemiBold
                            )
                            result.errors.take(3).forEach {
                                Text("· $it", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                            }
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    repo.addCourses(result.courses)
                                    Toast.makeText(context, "导入成功", Toast.LENGTH_SHORT).show()
                                    onBack()
                                },
                                enabled = result.courses.isNotEmpty()
                            ) { Text("导入这些课程") }
                        }
                    }
                }

                item {
                    SectionCard(title = "手动添加") {
                        Text(
                            "适合实验课、体育分项、重修班等教务系统里没有的课程。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = onManualAdd, modifier = Modifier.fillMaxWidth()) {
                            Text("手动添加课程")
                        }
                    }
                }
            }

            item {
                SectionCard("关于数据") {
                    Text(
                        "校序不访问、不登录任何学校教务系统；课表来自你主动导入的文件、分享码或手动输入。" +
                            "所有数据只保存在本机，卸载即删除。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun makeQr(text: String, size: Int = 640): androidx.compose.ui.graphics.ImageBitmap? = runCatching {
    val hints = mapOf(
        EncodeHintType.CHARACTER_SET to "UTF-8",
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
        EncodeHintType.MARGIN to 1
    )
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        for (x in 0 until size) {
            pixels[y * size + x] = if (matrix.get(x, y)) {
                android.graphics.Color.BLACK
            } else {
                android.graphics.Color.WHITE
            }
        }
    }
    Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}.getOrNull()
