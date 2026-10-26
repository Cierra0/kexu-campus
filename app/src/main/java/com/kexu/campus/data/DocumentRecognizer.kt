package com.kexu.campus.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * 课表文件识别：
 * - .docx：直接解压 word/document.xml 提取文字（表格按 tab/换行分隔），不依赖 OCR；
 * - .pdf：用系统 PdfRenderer 渲染前 3 页，再走中文 OCR；
 * - .jpg/.png/.webp：直接 OCR。
 *
 * 识别结果只是文字，交给 TimetableTextParser 做启发式解析，最终由用户确认后导入。
 */
object DocumentRecognizer {

    data class OcrLine(
        val text: String,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    ) {
        val centerX: Int get() = (left + right) / 2
        val centerY: Int get() = (top + bottom) / 2
    }

    data class Result(
        val ok: Boolean,
        val text: String = "",
        val error: String = "",
        val source: String = "",
        val lines: List<OcrLine> = emptyList()
    )

    fun recognize(context: Context, uri: Uri, displayName: String): Result = runCatching {
        val lower = displayName.lowercase()
        val type = context.contentResolver.getType(uri) ?: ""
        when {
            lower.endsWith(".docx") || type.contains("wordprocessingml") ->
                Result(true, docxText(context, uri), source = "Word")

            lower.endsWith(".pdf") || type.contains("pdf") ->
                Result(true, pdfText(context, uri), source = "PDF")

            lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
                lower.endsWith(".webp") || type.startsWith("image/") -> {
                val (text, lines) = imageText(context, uri)
                Result(true, text, source = "图片", lines = lines)
            }

            else -> Result(false, error = "不支持的文件类型，请选择 PDF / .docx / 图片")
        }
    }.getOrElse {
        Result(false, error = "识别失败：${it.message ?: "未知错误"}")
    }

    // ---------------- Word ----------------

    private fun docxText(context: Context, uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("无法读取文件")
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val xml = zip.readBytes().toString(Charsets.UTF_8)
                    return parseWordXml(xml)
                }
                entry = zip.nextEntry
            }
        }
        error("不是有效的 .docx 文件（老 .doc 请先另存为 .docx）")
    }

    private fun parseWordXml(xml: String): String {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))
        val sb = StringBuilder()
        var inText = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name.substringAfter(':')
                    if (name == "t") inText = true
                    if (name == "tab") sb.append('\t')
                }
                XmlPullParser.TEXT -> if (inText) sb.append(parser.text)
                XmlPullParser.END_TAG -> {
                    when (parser.name.substringAfter(':')) {
                        "t" -> inText = false
                        "p" -> sb.append('\n')
                        "tc" -> sb.append('\t')
                        "tr" -> sb.append('\n')
                    }
                }
            }
            event = parser.next()
        }
        return sb.toString()
    }

    // ---------------- PDF ----------------

    private fun pdfText(context: Context, uri: Uri): String {
        val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("无法打开 PDF")
        val renderer = PdfRenderer(pfd)
        val sb = StringBuilder()
        try {
            val pages = minOf(renderer.pageCount, 3)
            for (index in 0 until pages) {
                renderer.openPage(index).use { page ->
                    val scale = 2
                    val width = (page.width * scale).coerceIn(320, 2400)
                    val height = (page.height * scale).coerceIn(320, 2400)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    sb.append(ocr(bitmap)).append('\n')
                    bitmap.recycle()
                }
            }
        } finally {
            renderer.close()
            pfd.close()
        }
        return sb.toString()
    }

    // ---------------- 图片 ----------------

    private fun imageText(context: Context, uri: Uri): Pair<String, List<OcrLine>> {
        val original = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            ?: error("无法读取图片")
        val scaled = scaleDown(original, 2400)
        val (text, lines) = ocrLines(scaled)
        if (scaled !== original) original.recycle()
        scaled.recycle()
        return text to lines
    }

    private fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
        val side = maxOf(bitmap.width, bitmap.height)
        if (side <= maxSide) return bitmap
        val ratio = maxSide.toFloat() / side
        val width = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    // ---------------- OCR ----------------

    private fun ocr(bitmap: Bitmap): String {
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        return try {
            val result = Tasks.await(
                recognizer.process(InputImage.fromBitmap(bitmap, 0)),
                30,
                TimeUnit.SECONDS
            )
            result.text
        } finally {
            runCatching { recognizer.close() }
        }
    }

    private fun ocrLines(bitmap: Bitmap): Pair<String, List<OcrLine>> {
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        return try {
            val result = Tasks.await(
                recognizer.process(InputImage.fromBitmap(bitmap, 0)),
                30,
                TimeUnit.SECONDS
            )
            val lines = mutableListOf<OcrLine>()
            for (block in result.textBlocks) {
                for (line in block.lines) {
                    val box = line.boundingBox ?: continue
                    lines.add(
                        OcrLine(
                            text = line.text,
                            left = box.left,
                            top = box.top,
                            right = box.right,
                            bottom = box.bottom
                        )
                    )
                }
            }
            result.text to lines
        } finally {
            runCatching { recognizer.close() }
        }
    }
}
