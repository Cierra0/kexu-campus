package com.kexu.campus.data

import java.time.YearMonth

/** 记账的纯逻辑：按月筛选、汇总、分类统计、CSV 导出。 */
object Ledger {

    val EXPENSE_CATEGORIES = listOf("餐饮", "交通", "购物", "学习", "娱乐", "医疗", "住宿", "其他")
    val INCOME_CATEGORIES = listOf("生活费", "奖学金", "兼职", "其他")

    data class CategorySum(val category: String, val amount: Double)

    data class MonthSummary(
        val income: Double,
        val expense: Double,
        val byCategory: List<CategorySum>
    ) {
        val balance: Double get() = income - expense
    }

    fun inMonth(entries: List<LedgerEntry>, year: Int, month: Int): List<LedgerEntry> =
        entries.filter { entry ->
            val date = Dates.parse(entry.date)
            date != null && date.year == year && date.monthValue == month
        }

    fun summarize(entries: List<LedgerEntry>): MonthSummary {
        val income = entries.filter { it.type == 1 }.sumOf { it.amount }
        val expense = entries.filter { it.type == 0 }.sumOf { it.amount }
        val byCategory = entries
            .filter { it.type == 0 }
            .groupBy { it.category.ifBlank { "其他" } }
            .map { (category, list) -> CategorySum(category, list.sumOf { it.amount }) }
            .sortedByDescending { it.amount }
        return MonthSummary(income, expense, byCategory)
    }

    fun currentMonth(): YearMonth = YearMonth.now()

    fun csv(entries: List<LedgerEntry>): String {
        val sb = StringBuilder("日期,类型,分类,金额,备注\n")
        entries.sortedBy { it.date }.forEach { entry ->
            sb.append(entry.date).append(',')
                .append(if (entry.type == 1) "收入" else "支出").append(',')
                .append(entry.category.replace(",", "，")).append(',')
                .append(entry.amount).append(',')
                .append(entry.note.replace(",", "，").replace("\n", " "))
                .append('\n')
        }
        return sb.toString()
    }
}
