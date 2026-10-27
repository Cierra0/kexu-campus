package com.kexu.campus.data

import android.graphics.Bitmap

/**
 * 从课表截图里检测表格线：
 * - 竖线用来确定「星期」列
 * - 横线用来确定「上午/下午/晚上和节次」行带
 * 检测不到时返回 null，调用方回退到按文字坐标聚类。
 */
object ImageGridDetector {

    fun detect(bitmap: Bitmap): DocumentRecognizer.TableGrid? {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 200 || h < 200) return null
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val vScore = IntArray(w)
        val hScore = IntArray(h)
        var y = 0
        while (y < h) {
            val rowOffset = y * w
            var x = 0
            while (x < w) {
                val p = pixels[rowOffset + x]
                val gray = ((p shr 16 and 0xFF) * 299 + (p shr 8 and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
                if (gray < 150) {
                    vScore[x]++
                    hScore[y]++
                }
                x++
            }
            y++
        }

        val vLines = mergeLines(vScore, h * 0.38f)
        val hLines = mergeLines(hScore, w * 0.16f)
        if (vLines.size < 6 || hLines.size < 4) return null
        return DocumentRecognizer.TableGrid(vLines, hLines, w, h)
    }

    private fun mergeLines(score: IntArray, threshold: Float): List<Int> {
        val hits = ArrayList<Int>()
        for (i in score.indices) {
            if (score[i] >= threshold) hits.add(i)
        }
        if (hits.isEmpty()) return emptyList()
        val merged = ArrayList<Int>()
        var start = hits[0]
        var prev = hits[0]
        for (i in 1 until hits.size) {
            val v = hits[i]
            if (v - prev <= 4) {
                prev = v
            } else {
                merged.add((start + prev) / 2)
                start = v
                prev = v
            }
        }
        merged.add((start + prev) / 2)
        return merged
    }
}
