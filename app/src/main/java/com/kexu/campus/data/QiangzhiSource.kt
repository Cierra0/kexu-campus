package com.kexu.campus.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 强智教务（/jsxsd/）适配：检测、地址、成绩表 DOM 提取与解析。 */
object QiangzhiSource {

    const val TIMETABLE_PATH = "xskb/xskb_list.do"
    const val GRADES_PATH = "kscj/cjcx_list"

    fun detect(url: String, html: String): Int {
        var score = 0
        val u = url.lowercase()
        if (u.contains("/jsxsd/")) score += 50
        if (u.contains("xskb") || u.contains("kscj") || u.contains("xskb_list")) score += 20
        if (html.contains("jsxsd") || html.contains("kbtable") || html.contains("dataList")) score += 20
        if (html.contains("强智") || html.contains("青果")) score += 10
        return minOf(100, score)
    }

    fun timetableUrl(profile: SchoolProfile): String? =
        profile.jwBase.takeIf { it.isNotBlank() }?.let { it.trimEnd('/') + "/" + TIMETABLE_PATH }

    fun gradesUrl(profile: SchoolProfile): String? =
        profile.jwBase.takeIf { it.isNotBlank() }?.let { it.trimEnd('/') + "/" + GRADES_PATH }

    fun timetableExtractionJs(): String = """
        (function(){
          try {
            var t = document.getElementById('kbtable') || document.querySelector('table#kbtable');
            if (!t) return '';
            var rows = [];
            var trs = t.querySelectorAll('tr');
            for (var r = 0; r < trs.length; r++) {
              var cells = [];
              var cs = trs[r].cells || [];
              for (var c = 0; c < cs.length; c++) {
                var e = cs[c];
                cells.push({
                  text: (e.innerText || '').replace(/\u00a0/g, ' ').trim(),
                  rowSpan: e.rowSpan || 1,
                  colSpan: e.colSpan || 1
                });
              }
              rows.push(cells);
            }
            return JSON.stringify({rows: rows});
          } catch (e) { return ''; }
        })()
    """.trimIndent()

    /**
     * 读取强智成绩页表格（#dataList）成 {header:[...], rows:[[...]]}；
     * 不按固定列号，交给解析器按表头关键词匹配，兼容各校列顺序。
     */
    fun gradesExtractionJs(): String = """        (function(){
          try {
            var table = document.getElementById('dataList')
              || document.querySelector('table#dataList')
              || document.querySelector('table.dataList');
            if (!table) {
              var tables = document.querySelectorAll('table');
              for (var i = 0; i < tables.length; i++) {
                if ((tables[i].innerText || '').indexOf('成绩') >= 0) { table = tables[i]; break; }
              }
            }
            if (!table) return '';
            var trs = table.querySelectorAll('tr');
            if (!trs || trs.length < 2) return '';
            function cells(tr) {
              var out = [];
              var cs = tr.cells || [];
              for (var i = 0; i < cs.length; i++) out.push((cs[i].innerText || '').trim());
              return out;
            }
            var header = cells(trs[0]);
            // 有些学校表头占两行，找第一行包含“课程”的行
            if ((header.join('') || '').indexOf('课程') < 0 && trs.length > 1) {
              header = cells(trs[1]);
            }
            var rows = [];
            for (var r = 1; r < trs.length; r++) {
              var row = cells(trs[r]);
              if (row.join('').trim().length === 0) continue;
              rows.push(row);
            }
            return JSON.stringify({header: header, rows: rows});
          } catch (e) { return ''; }
        })()
    """.trimIndent()
}

/** 强智成绩表解析：按表头关键词定位课程名/学分/成绩/绩点/学年/学期。 */
object QiangzhiGradesParser {

    data class Result(val grades: List<Grade>, val warnings: List<String> = emptyList())

    fun parse(json: String): Result {
        val root = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull()
            ?: return Result(emptyList(), listOf("成绩表不是有效 JSON"))
        val header = root["header"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        val rows = root["rows"]?.jsonArray ?: return Result(emptyList(), listOf("没有成绩行"))

        fun idx(vararg keys: String): Int = header.indexOfFirst { h ->
            keys.any { h.contains(it) }
        }

        val nameIdx = idx("课程名称", "课程名", "课程")
        val creditIdx = idx("学分")
        val scoreIdx = idx("成绩", "分数")
        val gpaIdx = idx("绩点")
        val yearIdx = idx("学年")
        val termIdx = idx("学期")
        val natureIdx = idx("课程性质", "性质")
        val examIdx = idx("考核", "考试")

        val grades = mutableListOf<Grade>()
        for (rowElement in rows) {
            val row = runCatching { rowElement.jsonArray.map { it.jsonPrimitive.content } }.getOrNull()
                ?: continue
            val name = row.getOrNull(nameIdx.coerceAtLeast(0))?.trim().orEmpty()
            if (name.isBlank()) continue
            val scoreText = row.getOrNull(scoreIdx.coerceAtLeast(0))?.trim().orEmpty()
            val score = scoreText.toDoubleOrNull() ?: 0.0
            val year = row.getOrNull(yearIdx.coerceAtLeast(0))?.trim().orEmpty()
            val term = row.getOrNull(termIdx.coerceAtLeast(0))?.trim().orEmpty()
            val termName = when {
                year.isNotBlank() && term.isNotBlank() -> "$year 学年第${term}学期"
                year.isNotBlank() -> year
                else -> "未分学期"
            }
            grades.add(
                Grade(
                    termName = termName,
                    courseName = name,
                    credit = row.getOrNull(creditIdx.coerceAtLeast(0))?.trim()?.toDoubleOrNull() ?: 0.0,
                    score = score,
                    gpa = row.getOrNull(gpaIdx.coerceAtLeast(0))?.trim()?.toDoubleOrNull(),
                    category = row.getOrNull(natureIdx.coerceAtLeast(0))?.trim().orEmpty().ifBlank { "必修" },
                    examType = row.getOrNull(examIdx.coerceAtLeast(0))?.trim().orEmpty().ifBlank { "正常考试" }
                )
            )
        }
        return Result(grades.distinctBy { "${it.termName}|${it.courseName}" })
    }
}
