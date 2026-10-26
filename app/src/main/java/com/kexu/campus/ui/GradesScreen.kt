package com.kexu.campus.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.kexu.campus.data.CampusData
import com.kexu.campus.data.CampusRepository
import com.kexu.campus.data.Gpa
import com.kexu.campus.data.Grade

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradesScreen(data: CampusData, repo: CampusRepository) {
    var selectedTerm by rememberSaveable { mutableStateOf("全部") }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Grade?>(null) }

    val terms = remember(data.grades) {
        listOf("全部") + data.grades
            .map { it.termName.ifBlank { "未分学期" } }
            .distinct()
    }
    val filtered = remember(data.grades, selectedTerm) {
        data.grades.filter {
            selectedTerm == "全部" || it.termName.ifBlank { "未分学期" } == selectedTerm
        }.sortedByDescending { it.createdAt }
    }
    val summary = remember(filtered) { Gpa.summarize(filtered) }
    val defaultTerm = data.grades.firstOrNull()?.termName ?: data.settings.termName

    ScreenScaffold(
        title = "成绩",
        subtitle = "手动录入 · 仅保存在本机",
        floatingAction = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("记成绩") }
            )
        }
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 8.dp, bottom = 150.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                SectionCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatColumn("加权平均", if (summary.credits > 0) String.format("%.1f", summary.weightedScore) else "—")
                        StatColumn("平均绩点", if (summary.credits > 0) String.format("%.2f", summary.weightedGpa) else "—")
                        StatColumn("总学分", if (summary.credits > 0) trimNumber(summary.credits) else "—")
                    }
                }
            }

            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    terms.forEach { term ->
                        FilterChip(
                            selected = selectedTerm == term,
                            onClick = { selectedTerm = term },
                            label = { Text(term, fontSize = 12.sp) }
                        )
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    EmptyState(
                        title = "还没有成绩",
                        subtitle = "点右下角「记成绩」手动录入；也可以按学期筛选查看。",
                        action = {
                            TextButton(onClick = { showAdd = true }) { Text("添加第一条成绩") }
                        }
                    )
                }
            } else {
                items(filtered, key = { it.id }) { grade ->
                    GradeCard(grade = grade, onClick = { editing = grade })
                }
            }

            item {
                Text(
                    "绩点按常见 4.0 换算规则估算，仅供参考；如学校有自定义绩点，可在录入时直接填写。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showAdd) {
        GradeEditDialog(
            grade = null,
            defaultTerm = defaultTerm,
            onDismiss = { showAdd = false },
            onSave = {
                repo.upsertGrade(it)
                showAdd = false
            }
        )
    }

    val current = editing
    if (current != null) {
        GradeEditDialog(
            grade = current,
            defaultTerm = defaultTerm,
            onDismiss = { editing = null },
            onSave = {
                repo.upsertGrade(it)
                editing = null
            },
            onDelete = {
                repo.deleteGrade(current.id)
                editing = null
            }
        )
    }
}

@Composable
private fun StatColumn(title: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GradeCard(grade: Grade, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(grade.courseName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(3.dp))
                Text(
                    listOf(
                        grade.termName.ifBlank { "未分学期" },
                        grade.category.ifBlank { "必修" },
                        "${trimNumber(grade.credit)} 学分"
                    ).joinToString(" · "),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (grade.score % 1.0 == 0.0) grade.score.toInt().toString() else String.format("%.1f", grade.score),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (grade.score < 60) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                Text(
                    "绩点 " + String.format("%.2f", grade.gpa ?: Gpa.scoreToGpa(grade.score)),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun GradeEditDialog(
    grade: Grade?,
    defaultTerm: String,
    onDismiss: () -> Unit,
    onSave: (Grade) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var courseName by rememberSaveable { mutableStateOf(grade?.courseName ?: "") }
    var termName by rememberSaveable { mutableStateOf(grade?.termName ?: defaultTerm) }
    var creditText by rememberSaveable { mutableStateOf(grade?.credit?.takeIf { it > 0 }?.let { trimNumber(it) } ?: "") }
    var scoreText by rememberSaveable { mutableStateOf(grade?.score?.takeIf { it > 0 }?.let { trimNumber(it) } ?: "") }
    var gpaText by rememberSaveable { mutableStateOf(grade?.gpa?.let { trimNumber(it) } ?: "") }
    var category by rememberSaveable { mutableStateOf(grade?.category ?: "必修") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (grade == null) "记一条成绩" else "编辑成绩") },
        text = {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(top = 4.dp)
            ) {
                OutlinedTextField(
                    value = courseName,
                    onValueChange = { courseName = it },
                    label = { Text("课程名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = termName,
                    onValueChange = { termName = it },
                    label = { Text("学期（如 2026-2027-1）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = creditText,
                        onValueChange = { creditText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("学分") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = scoreText,
                        onValueChange = { scoreText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("成绩") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = gpaText,
                        onValueChange = { gpaText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("绩点（可不填）") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("性质") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                val message = error
                if (message != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val score = scoreText.toDoubleOrNull()
                val credit = creditText.toDoubleOrNull()
                if (courseName.isBlank()) {
                    error = "请填写课程名"
                } else if (score == null || score < 0 || score > 150) {
                    error = "请填写有效成绩（0-150）"
                } else if (credit == null || credit <= 0) {
                    error = "请填写有效学分"
                } else {
                    onSave(
                        (grade ?: Grade()).copy(
                            courseName = courseName.trim(),
                            termName = termName.trim(),
                            credit = credit,
                            score = score,
                            gpa = gpaText.toDoubleOrNull(),
                            category = category.trim().ifBlank { "必修" }
                        )
                    )
                }
            }) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (grade != null && onDelete != null) {
                    TextButton(onClick = onDelete) { Text("删除") }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

private fun trimNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format("%.1f", value)
