package com.kexu.campus.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kexu.campus.data.CampusData
import com.kexu.campus.data.CampusRepository
import com.kexu.campus.data.Course
import com.kexu.campus.data.Dates
import com.kexu.campus.data.SECTION_COUNT
import com.kexu.campus.data.Session
import com.kexu.campus.data.Settings
import com.kexu.campus.data.WEEKDAY_NAMES
import com.kexu.campus.ui.theme.CourseTextColor
import com.kexu.campus.ui.theme.courseColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    data: CampusData,
    repo: CampusRepository,
    onEditCourse: (String) -> Unit,
    onAddAt: (Int, Int) -> Unit,
    onOpenImport: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSync: () -> Unit
) {
    val settings = data.settings
    val currentWeek = remember(settings.startDate, settings.totalWeeks) { Dates.currentWeek(settings) }
    var week by rememberSaveable { mutableStateOf(currentWeek) }
    var selectedCourse by remember { mutableStateOf<Course?>(null) }
    var pendingDelete by remember { mutableStateOf<Course?>(null) }

    LaunchedEffect(currentWeek) {
        week = currentWeek
    }
    // 已导入课程但开学日期为空（清空数据/旧版本导入）：自动把本周一设为第 1 周，保证课表能显示
    LaunchedEffect(settings.startDate, data.courses.size) {
        if (settings.startDate.isBlank() && data.courses.isNotEmpty()) {
            repo.updateSettings {
                it.copy(
                    startDate = java.time.LocalDate.now()
                        .with(java.time.DayOfWeek.MONDAY).toString(),
                    totalWeeks = maxOf(it.totalWeeks, 20)
                )
            }
        }
    }

    ScreenScaffold(
        title = settings.schoolName.ifBlank { "校序" },
        subtitle = if (settings.startDate.isBlank()) "先设置开学日期，课表才知道现在是第几周" else Dates.termProgressText(settings),
        actions = {
            IconButton(onClick = onOpenSync) {
                Icon(Icons.Outlined.Sync, contentDescription = "一键同步课表")
            }
            IconButton(onClick = onOpenImport) {
                Icon(Icons.Outlined.FileUpload, contentDescription = "导入/分享")
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "设置")
            }
        },
        floatingAction = {
            ExtendedFloatingActionButton(
                onClick = { onAddAt(Dates.todayWeekday(), 1) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("加课") }
            )
        }
    ) {
        if (settings.startDate.isBlank()) {
            Column(Modifier.padding(16.dp)) {
                SetupCard(onOpenSettings = onOpenSettings, onOpenSync = onOpenSync)
            }
        } else {
            WeekSelector(
                week = week,
                totalWeeks = settings.totalWeeks,
                currentWeek = currentWeek,
                onWeekChange = { week = it }
            )
            if (data.courses.isEmpty()) {
                EmptyState(
                    title = "还没有课程",
                    subtitle = "可以导入 CSV、粘贴同学分享码，或点右下角「加课」手动添加",
                    action = { Button(onClick = onOpenImport) { Text("开始导入") } }
                )
            } else {
                TimetableGrid(
                    courses = data.courses,
                    week = week,
                    showWeekend = settings.showWeekend,
                    settings = settings,
                    onCourseClick = { selectedCourse = it },
                    onEmptyClick = { day, section -> onAddAt(day, section) }
                )
            }
        }
    }

    val course = selectedCourse
    if (course != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedCourse = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            CourseDetailSheet(
                course = course,
                onEdit = {
                    selectedCourse = null
                    onEditCourse(course.id)
                },
                onDelete = {
                    selectedCourse = null
                    pendingDelete = course
                }
            )
        }
    }

    val deleting = pendingDelete
    if (deleting != null) {
        ConfirmDialog(
            title = "删除课程",
            message = "确定删除「${deleting.name}」吗？此操作不可恢复。",
            confirmText = "删除",
            onConfirm = {
                repo.deleteCourse(deleting.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun SetupCard(onOpenSettings: () -> Unit, onOpenSync: () -> Unit) {
    SectionCard(title = "第一步 · 设置学期") {
        Text(
            "先填写开学日期和总周数，课表就会自动定位到本周，并在每周切换时保持正确。",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onOpenSettings) { Text("去设置") }
            OutlinedButton(onClick = onOpenSync) { Text("登录系统导入") }
        }
    }
    Spacer(Modifier.height(12.dp))
    SectionCard(title = "第二步 · 导入课表") {
        Text(
            "支持四种方式：① 登录学校教务系统读取；② CSV 文件导入；③ 粘贴同学的分享码；④ 手动加课。\n" +
                "所有数据只保存在本机，不需要登录任何学校系统。",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun TimetableGrid(
    courses: List<Course>,
    week: Int,
    showWeekend: Boolean,
    settings: Settings,
    onCourseClick: (Course) -> Unit,
    onEmptyClick: (Int, Int) -> Unit
) {
    val days = if (showWeekend) 7 else 5
    val cellHeight = 62.dp
    val timeColumnWidth = 30.dp
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val scrollState = rememberScrollState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = 8.dp)
        ) {
            Spacer(Modifier.width(timeColumnWidth))
            for (day in 1..days) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    val isToday = Dates.isToday(settings, week, day)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "周${WEEKDAY_NAMES[day - 1]}",
                            fontSize = 12.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) primary else subTextColor
                        )
                        val date = Dates.dateOf(settings, week, day)
                        if (date != null) {
                            Text("${date.monthValue}/${date.dayOfMonth}", fontSize = 9.sp, color = subTextColor)
                        }
                    }
                }
            }
        }

        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(cellHeight * SECTION_COUNT)
        ) {
            val dayWidth = (maxWidth - timeColumnWidth - 16.dp) / days
            val leftOffset = 8.dp + timeColumnWidth

            Canvas(Modifier.fillMaxSize()) {
                val cellPx = cellHeight.toPx()
                val startX = leftOffset.toPx()
                val dayPx = dayWidth.toPx()
                for (i in 0..SECTION_COUNT) {
                    val y = i * cellPx
                    drawLine(
                        color = lineColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f
                    )
                }
                for (d in 0..days) {
                    val x = startX + d * dayPx
                    drawLine(
                        color = lineColor,
                        start = Offset(x, 0f),
                        end = Offset(x, SECTION_COUNT * cellPx),
                        strokeWidth = 1f
                    )
                }
            }

            for (section in 1..SECTION_COUNT) {
                Box(
                    Modifier
                        .offset(x = 8.dp, y = cellHeight * (section - 1))
                        .width(timeColumnWidth)
                        .height(cellHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text("$section", fontSize = 10.sp, color = subTextColor, textAlign = TextAlign.Center)
                }
            }

            for (day in 0 until days) {
                for (section in 1..SECTION_COUNT) {
                    Box(
                        Modifier
                            .offset(
                                x = leftOffset + dayWidth * day,
                                y = cellHeight * (section - 1)
                            )
                            .width(dayWidth)
                            .height(cellHeight)
                            .clickable { onEmptyClick(day + 1, section) }
                    )
                }
            }

            for (day in 1..days) {
                val blocks = layoutBlocks(courses, day, week)
                blocks.forEach { block ->
                    val laneWidth = dayWidth / block.lanes
                    val x = leftOffset + dayWidth * (day - 1) + laneWidth * block.lane
                    val y = cellHeight * (block.session.startSection - 1)
                    val h = cellHeight * (block.session.endSection - block.session.startSection + 1)
                    Box(
                        Modifier
                            .offset(x = x + 1.dp, y = y + 1.dp)
                            .width(laneWidth - 2.dp)
                            .height(h - 2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(courseColor(block.course.colorIndex))
                            .clickable { onCourseClick(block.course) }
                            .padding(4.dp)
                    ) {
                        Column {
                            Text(
                                block.course.name,
                                fontSize = 10.sp,
                                lineHeight = 12.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                color = CourseTextColor,
                                fontWeight = FontWeight.Medium
                            )
                            if (block.session.location.isNotBlank()) {
                                Text(
                                    block.session.location,
                                    fontSize = 8.sp,
                                    lineHeight = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = CourseTextColor.copy(alpha = 0.72f)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(96.dp))
    }
}

private data class Block(
    val course: Course,
    val session: Session,
    val lane: Int,
    val lanes: Int
)

private fun layoutBlocks(courses: List<Course>, day: Int, week: Int): List<Block> {
    val entries = courses
        .flatMap { course ->
            course.sessions
                .filter { it.dayOfWeek == day && com.kexu.campus.data.WeekRange.isActive(it.weeks, week) }
                .map { session -> course to session }
        }
        .sortedWith(compareBy({ it.second.startSection }, { it.second.endSection }))
    if (entries.isEmpty()) return emptyList()

    val laneEnds = mutableListOf<Int>()
    val assigned = entries.map { pair ->
        val session = pair.second
        var lane = laneEnds.indexOfFirst { it < session.startSection }
        if (lane < 0) {
            laneEnds.add(session.endSection)
            lane = laneEnds.size - 1
        } else {
            laneEnds[lane] = session.endSection
        }
        Triple(pair.first, session, lane)
    }
    val lanes = laneEnds.size.coerceAtLeast(1)
    return assigned.map { Block(it.first, it.second, it.third, lanes) }
}

@Composable
private fun CourseDetailSheet(
    course: Course,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(courseColor(course.colorIndex))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                course.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
        val meta = buildList {
            if (course.teacher.isNotBlank()) add("教师 ${course.teacher}")
            if (course.credits > 0) add("${course.credits} 学分")
            add(if (course.source == "custom") "手动添加" else "导入")
        }
        Text(
            meta.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        course.sessions.forEach { session ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("周${WEEKDAY_NAMES.getOrElse(session.dayOfWeek - 1) { "?" }}", Modifier.width(42.dp), fontSize = 13.sp)
                Text("第 ${session.startSection}-${session.endSection} 节", Modifier.width(88.dp), fontSize = 13.sp)
                Text(session.weeks, Modifier.weight(1f), fontSize = 13.sp)
                if (session.location.isNotBlank()) {
                    Text(
                        session.location,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (course.note.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(course.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("编辑") }
            OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) { Text("删除") }
        }
    }
}
