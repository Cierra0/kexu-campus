package com.kexu.campus.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater

@Serializable
data class SharePayload(
    val v: Int = 2,
    val mode: String = "busy",
    val name: String = "",
    val courses: List<Course> = emptyList()
)

data class DecodedShare(
    val name: String,
    val courses: List<Course>,
    val fromTangli: Boolean = false
)

/**
 * 课表分享码：只包含课程名、地点、时间、周次；压缩后 Base64URL，前缀 "KXKB1:"。
 * 同时兼容「烫梨」的 "TLKB1:" 分享码，方便从参考项目迁移。
 */
object ShareCode {

    const val PREFIX = "KXKB1:"
    const val TANGLI_PREFIX = "TLKB1:"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val urlEncoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    private val urlDecoder: Base64.Decoder = Base64.getUrlDecoder()

    /**
     * 分享课表的「忙闲版本」：只保留星期、节次、周次，不含课程名、教师、地点、学分。
     * 这样同学互相导入后只能看到"哪段时间有课"，看不到具体课程信息。
     */
    fun encode(name: String, courses: List<Course>): String {
        val payload = SharePayload(
            mode = "busy",
            name = name,
            courses = courses.map { course ->
                Course(
                    name = "",
                    teacher = "",
                    credits = 0.0,
                    note = "",
                    source = "shared",
                    sessions = course.sessions.map {
                        Session(
                            dayOfWeek = it.dayOfWeek,
                            startSection = it.startSection,
                            endSection = it.endSection,
                            weeks = it.weeks,
                            location = "",
                            campus = ""
                        )
                    }
                )
            }
        )
        val raw = json.encodeToString(SharePayload.serializer(), payload).toByteArray(Charsets.UTF_8)
        return PREFIX + urlEncoder.encodeToString(deflate(raw))
    }

    fun decode(text: String?): DecodedShare? {
        if (text.isNullOrBlank()) return null

        val ownIndex = text.indexOf(PREFIX)
        if (ownIndex >= 0) {
            val code = collectCode(text, ownIndex + PREFIX.length)
            if (code.isBlank()) return null
            val bytes = runCatching { inflate(urlDecoder.decode(code)) }.getOrNull() ?: return null
            val payload = runCatching {
                json.decodeFromString(SharePayload.serializer(), String(bytes, Charsets.UTF_8))
            }.getOrNull() ?: return null
            return DecodedShare(
                name = payload.name,
                courses = payload.courses.map { it.copy(source = "shared") }
            )
        }

        val tangliIndex = text.indexOf(TANGLI_PREFIX)
        if (tangliIndex >= 0) {
            val code = collectCode(text, tangliIndex + TANGLI_PREFIX.length)
            if (code.isBlank()) return null
            val bytes = runCatching { inflate(urlDecoder.decode(code)) }.getOrNull() ?: return null
            val root = runCatching { json.parseToJsonElement(String(bytes, Charsets.UTF_8)) }
                .getOrNull() as? JsonObject ?: return null
            return parseTangli(root)
        }

        return null
    }

    private fun parseTangli(root: JsonObject): DecodedShare {
        val name = root.stringOrEmpty("o")
        val array = root["c"] as? JsonArray ?: return DecodedShare(name, emptyList(), true)
        val courses = array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val courseName = obj.stringOrEmpty("n").ifBlank { return@mapNotNull null }
            val sessions = (obj["t"] as? JsonArray)?.mapNotNull { sessionElement ->
                val arr = sessionElement as? JsonArray ?: return@mapNotNull null
                val day = arr.getOrNull(0).asIntOrNull() ?: return@mapNotNull null
                val start = arr.getOrNull(1).asIntOrNull() ?: return@mapNotNull null
                val end = arr.getOrNull(2).asIntOrNull() ?: return@mapNotNull null
                Session(
                    dayOfWeek = day,
                    startSection = start,
                    endSection = end,
                    weeks = arr.getOrNull(3).asStringOrEmpty().ifBlank { "1-20" },
                    location = arr.getOrNull(4).asStringOrEmpty()
                )
            } ?: emptyList()
            Course(name = courseName, source = "shared", sessions = sessions)
        }
        return DecodedShare(name, courses, true)
    }

    private fun collectCode(text: String, from: Int): String {
        var end = from
        while (end < text.length && isBase64UrlChar(text[end])) end++
        return text.substring(from, end)
    }

    private fun isBase64UrlChar(ch: Char): Boolean =
        ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '-' || ch == '_'

    private fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        deflater.setInput(data)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!deflater.finished()) {
            val n = deflater.deflate(buffer)
            out.write(buffer, 0, n)
        }
        deflater.end()
        return out.toByteArray()
    }

    private fun inflate(data: ByteArray): ByteArray {
        val inflater = Inflater(true)
        inflater.setInput(data)
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(2048)
        var guard = 0
        while (!inflater.finished() && guard++ < 100_000) {
            val n = try {
                inflater.inflate(buffer)
            } catch (_: Exception) {
                break
            }
            if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
            out.write(buffer, 0, n)
        }
        inflater.end()
        return out.toByteArray()
    }

    private fun JsonObject.stringOrEmpty(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull ?: ""

    private fun JsonElement?.asStringOrEmpty(): String =
        (this as? JsonPrimitive)?.contentOrNull ?: ""

    private fun JsonElement?.asIntOrNull(): Int? =
        try {
            (this as? JsonPrimitive)?.jsonPrimitive?.content?.toIntOrNull()
        } catch (_: Exception) {
            null
        }
}
