package com.kexu.campus.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 正方成绩页（jqGrid tabGrid）DOM 表格解析（移植自 Tangli 的 Grades.parseFromDomRows）。
 * 列索引映射（真机确认）：1 学年、2 学期、4 课程名、5 课程性质、6 学分、
 * 8 绩点、9 考核方式、22 成绩（缺失时回退 21）。
 */
object JwglxtGradesParser {

    data class Result(val grades: List<Grade>, val warnings: List<String> = emptyList())

    fun parse(rowsJson: String): Result {
        val rows = runCatching {
            val root = Json.parseToJsonElement(rowsJson).jsonObject
            root["rows"]?.jsonArray ?: return@runCatching null
        }.getOrNull() ?: return Result(emptyList(), listOf("没有读到成绩表格"))

        val grades = mutableListOf<Grade>()
        val warnings = mutableListOf<String>()
        for (rowElement in rows) {
            val cells = runCatching {
                rowElement.jsonArray.map { it.jsonPrimitive.content }
            }.getOrNull() ?: continue
            if (cells.size < 23) continue
            val name = cells.getOrNull(4)?.trim().orEmpty()
            if (name.isBlank() || name == "课程名称") continue

            val year = cells.getOrNull(1)?.trim().orEmpty().ifBlank { "未知学年" }
            val term = cells.getOrNull(2)?.trim().orEmpty().ifBlank { "1" }
            val scoreText = cells.getOrNull(22)?.trim().orEmpty()
                .ifBlank { cells.getOrNull(21)?.trim().orEmpty() }
            val score = scoreText.toDoubleOrNull() ?: 0.0
            if (scoreText.isNotBlank() && scoreText.toDoubleOrNull() == null) {
                warnings.add("非数值成绩：$name = $scoreText")
            }
            grades.add(
                Grade(
                    termName = "$year 学年第${term}学期",
                    courseName = name,
                    credit = cells.getOrNull(6)?.trim()?.toDoubleOrNull() ?: 0.0,
                    score = score,
                    gpa = cells.getOrNull(8)?.trim()?.toDoubleOrNull(),
                    category = cells.getOrNull(5)?.trim().orEmpty().ifBlank { "必修" },
                    examType = cells.getOrNull(9)?.trim().orEmpty().ifBlank { "正常考试" }
                )
            )
        }
        return Result(grades.distinctBy { "${it.termName}|${it.courseName}" }, warnings)
    }
}
