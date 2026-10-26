package com.kexu.campus.data

/** 从学校成绩页文本里启发式解析成绩（课程名、学分、分数）。只用于本机预览确认。 */
object GradeTextParser {

    data class Item(val name: String, val credit: Double, val score: Double)

    fun parse(text: String): List<Item> {
        val result = mutableListOf<Item>()
        val lines = text.split('\n', '\r').map { it.trim() }.filter { it.isNotEmpty() }
        for (line in lines) {
            val cells = line.split('\t', '|', '，', ' ')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (cells.size < 2) continue

            val numerics = cells.mapNotNull { it.toDoubleOrNull() }
            val score = numerics.maxOrNull()?.takeIf { it in 0.0..150.0 } ?: continue
            val credit = numerics.firstOrNull { it in 0.5..10.0 && it != score } ?: 0.0
            val name = cells
                .filter {
                    it.length in 2..30 &&
                        it.any { ch -> ch in '\u4e00'..'\u9fff' } &&
                        it.toDoubleOrNull() == null &&
                        !it.contains("学分") &&
                        !it.contains("成绩") &&
                        !it.contains("课程") &&
                        !it.contains("绩点") &&
                        !it.contains("序号")
                }
                .maxByOrNull { it.length }
                ?: continue
            result.add(Item(name, credit, score))
        }
        return result.distinctBy { it.name }
    }
}
