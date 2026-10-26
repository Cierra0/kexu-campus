package com.kexu.campus.ui

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kexu.campus.data.CampusData
import com.kexu.campus.data.Course
import com.kexu.campus.data.SECTION_COUNT
import com.kexu.campus.data.Session
import com.kexu.campus.data.WEEKDAY_NAMES
import com.kexu.campus.ui.theme.courseColor

@Composable
fun CourseEditScreen(
    courseId: String?,
    presetDay: Int,
    presetSection: Int,
    data: CampusData,
    onSave: (Course) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit
) {
    val existing = remember(courseId) { data.courses.firstOrNull { it.id == courseId } }
    val defaultWeeks = "1-${data.settings.totalWeeks.coerceAtLeast(1)}"

    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var teacher by rememberSaveable { mutableStateOf(existing?.teacher ?: "") }
    var creditsText by rememberSaveable {
        mutableStateOf(existing?.credits?.takeIf { it > 0 }?.let { trimNumberText(it) } ?: "")
    }
    var note by rememberSaveable { mutableStateOf(existing?.note ?: "") }
    var colorIndex by rememberSaveable { mutableStateOf(existing?.colorIndex ?: 0) }
    var error by remember { mutableStateOf<String?>(null) }

    val sessions = remember {
        mutableStateListOf<Session>().apply {
            addAll(
                existing?.sessions ?: listOf(
                    Session(
                        dayOfWeek = presetDay,
                        startSection = presetSection,
                        endSection = presetSection,
                        weeks = defaultWeeks
                    )
                )
            )
        }
    }

    ScreenScaffold(
        title = if (existing == null) "添加课程" else "编辑课程",
        onBack = onBack,
        actions = {
            if (existing != null) {
                IconButton(onClick = { onDelete(existing.id) }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "删除")
                }
            }
            TextButton(onClick = {
                if (name.isBlank()) {
                    error = "请填写课程名"
                    return@TextButton
                }
                if (sessions.isEmpty()) {
                    error = "至少需要一个上课时段"
                    return@TextButton
                }
                val cleaned = sessions.map {
                    it.copy(
                        startSection = it.startSection.coerceIn(1, SECTION_COUNT),
                        endSection = it.endSection.coerceIn(it.startSection, SECTION_COUNT),
                        weeks = it.weeks.ifBlank { defaultWeeks }
                    )
                }
                val course = (existing ?: Course()).copy(
                    name = name.trim(),
                    teacher = teacher.trim(),
                    credits = creditsText.toDoubleOrNull() ?: 0.0,
                    note = note.trim(),
                    colorIndex = colorIndex,
                    sessions = cleaned
                )
                onSave(course)
            }) { Text("保存") }
        }
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionCard(title = "基本信息") {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("课程名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = teacher,
                            onValueChange = { teacher = it },
                            label = { Text("教师") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = creditsText,
                            onValueChange = { creditsText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                            label = { Text("学分") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.width(110.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("备注（可不填）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                SectionCard(title = "颜色") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (index in 0 until 8) {
                            val selected = colorIndex == index
                            Box(
                                Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(courseColor(index))
                                    .then(
                                        if (selected) {
                                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable { colorIndex = index }
                            )
                        }
                    }
                }
            }

            itemsIndexed(sessions) { index, session ->
                SessionEditorCard(
                    session = session,
                    onChange = { sessions[index] = it },
                    onRemove = if (sessions.size > 1) {
                        { sessions.removeAt(index) }
                    } else {
                        null
                    }
                )
            }

            item {
                OutlinedButton(
                    onClick = {
                        sessions.add(
                            Session(
                                dayOfWeek = 1,
                                startSection = 1,
                                endSection = 2,
                                weeks = defaultWeeks
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("添加上课时段")
                }
            }

            item {
                val message = error
                if (message != null) {
                    Text(message, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(6.dp))
                }
                Text(
                    "周次写法：1-16 表示第 1 到 16 周；1-8,10-16 表示跳过第 9 周；1-16单 / 2-16双 表示单双周。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SessionEditorCard(
    session: Session,
    onChange: (Session) -> Unit,
    onRemove: (() -> Unit)?
) {
    SectionCard(title = "上课时段") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DropdownField(
                label = "周",
                value = session.dayOfWeek,
                options = (1..7).toList(),
                display = { "周${WEEKDAY_NAMES.getOrElse(it - 1) { "?" }}" },
                onSelect = { onChange(session.copy(dayOfWeek = it)) }
            )
            DropdownField(
                label = "起",
                value = session.startSection,
                options = (1..SECTION_COUNT).toList(),
                display = { "$it" },
                onSelect = {
                    onChange(
                        session.copy(
                            startSection = it,
                            endSection = maxOf(it, session.endSection)
                        )
                    )
                }
            )
            DropdownField(
                label = "止",
                value = session.endSection,
                options = (session.startSection..SECTION_COUNT).toList(),
                display = { "$it" },
                onSelect = { onChange(session.copy(endSection = it)) }
            )
            Spacer(Modifier.weight(1f))
            if (onRemove != null) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Outlined.Close, contentDescription = "删除时段")
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = session.weeks,
            onValueChange = { onChange(session.copy(weeks = it)) },
            label = { Text("周次") },
            placeholder = { Text("如 1-16 或 1-8,10-16 或 1-16单") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = session.location,
            onValueChange = { onChange(session.copy(location = it)) },
            label = { Text("地点") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun trimNumberText(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
