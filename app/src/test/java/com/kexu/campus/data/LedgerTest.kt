package com.kexu.campus.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerTest {

    private val entries = listOf(
        LedgerEntry(type = 0, amount = 12.5, category = "餐饮", date = "2026-10-01"),
        LedgerEntry(type = 0, amount = 30.0, category = "餐饮", date = "2026-10-02"),
        LedgerEntry(type = 0, amount = 50.0, category = "交通", date = "2026-10-03"),
        LedgerEntry(type = 1, amount = 1500.0, category = "生活费", date = "2026-10-01"),
        LedgerEntry(type = 0, amount = 99.0, category = "餐饮", date = "2026-09-30")
    )

    @Test
    fun filtersByMonth() {
        val october = Ledger.inMonth(entries, 2026, 10)
        assertEquals(4, october.size)
        val september = Ledger.inMonth(entries, 2026, 9)
        assertEquals(1, september.size)
    }

    @Test
    fun summarizesIncomeAndExpense() {
        val summary = Ledger.summarize(Ledger.inMonth(entries, 2026, 10))
        assertEquals(1500.0, summary.income, 0.001)
        assertEquals(92.5, summary.expense, 0.001)
        assertEquals(1407.5, summary.balance, 0.001)
        assertEquals("交通", summary.byCategory.first().category)
        assertEquals(50.0, summary.byCategory.first().amount, 0.001)
        val food = summary.byCategory.first { it.category == "餐饮" }
        assertEquals(42.5, food.amount, 0.001)
    }

    @Test
    fun exportsCsv() {
        val csv = Ledger.csv(entries)
        assertTrue(csv.startsWith("日期,类型,分类,金额,备注"))
        assertTrue(csv.contains("2026-10-01,支出,餐饮,12.5"))
        assertTrue(csv.contains("2026-10-01,收入,生活费,1500.0"))
    }
}
