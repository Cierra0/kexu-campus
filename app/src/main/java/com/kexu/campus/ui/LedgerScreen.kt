package com.kexu.campus.ui

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kexu.campus.data.CampusData
import com.kexu.campus.data.CampusRepository
import com.kexu.campus.data.Dates
import com.kexu.campus.data.Ledger
import com.kexu.campus.data.LedgerEntry
import java.time.LocalDate
import java.time.YearMonth

private val PIE_COLORS = listOf(
    Color(0xFF2F6B57), Color(0xFF5B7DB1), Color(0xFFC98A4B),
    Color(0xFF8C6BB1), Color(0xFFB15B5B), Color(0xFF6A8A3A),
    Color(0xFF4A8C8C), Color(0xFF9A8B4F)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(data: CampusData, repo: CampusRepository) {
    val context = LocalContext.current
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = runCatching { YearMonth.parse(monthText) }.getOrDefault(YearMonth.now())

    var editing by remember { mutableStateOf<LedgerEntry?>(null) }
    var adding by remember { mutableStateOf(false) }
    var budgetEditing by remember { mutableStateOf(false) }

    val monthEntries = remember(data.ledger, monthText) {
        Ledger.inMonth(data.ledger, month.year, month.monthValue)
    }
    val summary = remember(monthEntries) { Ledger.summarize(monthEntries) }
    val budget = data.settings.monthlyBudget

    ScreenScaffold(
        title = "记账",
        subtitle = "只保存在本机 · 可导出 CSV",
        actions = {
            IconButton(onClick = {
                shareText(
                    context,
                    "kexu-ledger-${month.year}-${month.monthValue}.csv",
                    "text/csv",
                    Ledger.csv(data.ledger)
                )
            }) {
                Icon(Icons.Filled.Share, contentDescription = "导出 CSV")
            }
        },
        floatingAction = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("记一笔") }
            )
        }
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    IconButton(onClick = { monthText = month.minusMonths(1).toString() }) {
                        Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "上月")
                    }
                    Text(
                        "${month.year} 年 ${month.monthValue} 月",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(onClick = { monthText = month.plusMonths(1).toString() }) {
                        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "下月")
                    }
                }
            }

            item {
                SectionCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatColumn("支出", String.format("%.2f", summary.expense), MaterialTheme.colorScheme.error)
                        StatColumn("收入", String.format("%.2f", summary.income), MaterialTheme.colorScheme.primary)
                        StatColumn(
                            "结余",
                            String.format("%.2f", summary.balance),
                            if (summary.balance < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    if (budget > 0) {
                        val ratio = (summary.expense / budget).toFloat().coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { ratio },
                            modifier = Modifier.fillMaxWidth(),
                            color = if (summary.expense > budget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "本月预算 ${String.format("%.2f", budget)}，已用 ${String.format("%.2f", summary.expense)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { budgetEditing = true }) {
                            Text(if (budget > 0) "修改预算" else "设置月度预算")
                        }
                    }
                }
            }

            if (summary.byCategory.isNotEmpty()) {
                item {
                    SectionCard(title = "支出分类") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CategoryPie(summary = summary)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                summary.byCategory.take(5).forEachIndexed { index, item ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            Modifier
                                                .size(8.dp)
                                                .background(PIE_COLORS[index % PIE_COLORS.size], CircleShape)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(item.category, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                        Text(
                                            String.format("%.1f", item.amount),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (monthEntries.isEmpty()) {
                item {
                    EmptyState(
                        title = "本月还没有记录",
                        subtitle = "点右下角「记一笔」，记下今天的第一笔开销。",
                        action = { TextButton(onClick = { adding = true }) { Text("记一笔") } }
                    )
                }
            } else {
                val grouped = monthEntries.sortedByDescending { it.date }.groupBy { it.date }
                grouped.forEach { (date, list) ->
                    item(key = "date-$date") {
                        Text(
                            "$date · 支出 ${String.format("%.2f", list.filter { it.type == 0 }.sumOf { it.amount })}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                        )
                    }
                    items(list, key = { it.id }) { entry ->
                        LedgerCard(entry = entry, onClick = { editing = entry })
                    }
                }
            }
        }
    }

    if (adding) {
        LedgerEditDialog(
            entry = null,
            defaultDate = month.atDay(minOf(LocalDate.now().dayOfMonth, month.lengthOfMonth())).toString(),
            onDismiss = { adding = false },
            onSave = {
                repo.upsertLedgerEntry(it)
                adding = false
            }
        )
    }

    val current = editing
    if (current != null) {
        LedgerEditDialog(
            entry = current,
            defaultDate = current.date,
            onDismiss = { editing = null },
            onSave = {
                repo.upsertLedgerEntry(it)
                editing = null
            },
            onDelete = {
                repo.deleteLedgerEntry(current.id)
                editing = null
            }
        )
    }

    if (budgetEditing) {
        BudgetDialog(
            current = budget,
            onDismiss = { budgetEditing = false },
            onSave = { value ->
                repo.updateSettings { it.copy(monthlyBudget = value) }
                budgetEditing = false
                Toast.makeText(context, "预算已保存", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun StatColumn(title: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = color)
        Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CategoryPie(summary: Ledger.MonthSummary) {
    if (summary.expense <= 0) return
    val surface = MaterialTheme.colorScheme.surface
    Canvas(Modifier.size(108.dp)) {
        var startAngle = -90f
        summary.byCategory.forEachIndexed { index, item ->
            val sweep = (item.amount / summary.expense * 360f).toFloat()
            drawArc(
                color = PIE_COLORS[index % PIE_COLORS.size],
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = true
            )
            startAngle += sweep
        }
        drawCircle(color = surface, radius = size.minDimension * 0.28f, center = Offset(size.width / 2, size.height / 2))
    }
}

@Composable
private fun LedgerCard(entry: LedgerEntry, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .background(
                        if (entry.type == 1) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(entry.category.take(1), fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.category.ifBlank { "其他" }, fontWeight = FontWeight.Medium)
                if (entry.note.isNotBlank()) {
                    Text(
                        entry.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                (if (entry.type == 1) "+" else "-") + String.format("%.2f", entry.amount),
                fontWeight = FontWeight.SemiBold,
                color = if (entry.type == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun LedgerEditDialog(
    entry: LedgerEntry?,
    defaultDate: String,
    onDismiss: () -> Unit,
    onSave: (LedgerEntry) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var type by rememberSaveable { mutableStateOf(entry?.type ?: 0) }
    var amountText by rememberSaveable {
        mutableStateOf(entry?.amount?.takeIf { it > 0 }?.let { String.format("%.2f", it) } ?: "")
    }
    var category by rememberSaveable { mutableStateOf(entry?.category ?: "餐饮") }
    var note by rememberSaveable { mutableStateOf(entry?.note ?: "") }
    var date by rememberSaveable { mutableStateOf(entry?.date ?: defaultDate) }
    var error by remember { mutableStateOf<String?>(null) }

    val categories = if (type == 1) Ledger.INCOME_CATEGORIES else Ledger.EXPENSE_CATEGORIES

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (entry == null) "记一笔" else "编辑记录") },
        text = {
            Column(Modifier.padding(top = 4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = type == 0, onClick = { type = 0; category = "餐饮" }, label = { Text("支出") })
                    FilterChip(selected = type == 1, onClick = { type = 1; category = "生活费" }, label = { Text("收入") })
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("金额") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { item ->
                        FilterChip(
                            selected = category == item,
                            onClick = { category = item },
                            label = { Text(item, fontSize = 12.sp) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可不填）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        val current = Dates.parse(date) ?: LocalDate.now()
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                date = String.format("%04d-%02d-%02d", year, month + 1, day)
                            },
                            current.year,
                            current.monthValue - 1,
                            current.dayOfMonth
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("日期：$date") }
                val message = error
                if (message != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = amountText.toDoubleOrNull()
                if (amount == null || amount <= 0) {
                    error = "请输入有效金额"
                } else {
                    onSave(
                        (entry ?: LedgerEntry()).copy(
                            type = type,
                            amount = amount,
                            category = category,
                            note = note.trim(),
                            date = date
                        )
                    )
                }
            }) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (entry != null && onDelete != null) {
                    TextButton(onClick = onDelete) { Text("删除") }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

@Composable
private fun BudgetDialog(current: Double, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var text by rememberSaveable { mutableStateOf(if (current > 0) String.format("%.2f", current) else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("月度预算") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { ch -> ch.isDigit() || ch == '.' } },
                label = { Text("每月支出预算") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.toDoubleOrNull() ?: 0.0) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
