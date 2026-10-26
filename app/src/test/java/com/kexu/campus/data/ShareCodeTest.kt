package com.kexu.campus.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater

class ShareCodeTest {

    @Test
    fun roundTrip() {
        val courses = listOf(
            Course(
                name = "高等数学",
                teacher = "李老师",
                credits = 5.0,
                sessions = listOf(
                    Session(1, 1, 2, "1-16", "教1-201"),
                    Session(3, 3, 4, "1-16", "教1-201")
                )
            )
        )
        val code = ShareCode.encode("小董", courses)
        assertTrue(code.startsWith(ShareCode.PREFIX))

        // 隐私模式：只保留忙闲时间，课程名/地点/教师/学分都不应被分享
        val decoded = ShareCode.decode("给你我的课表 $code 记得导入")
        assertEquals("小董", decoded?.name)
        assertEquals(1, decoded?.courses?.size)
        assertTrue(decoded?.courses?.get(0)?.name.isNullOrEmpty())
        assertEquals(2, decoded?.courses?.get(0)?.sessions?.size)
        assertEquals("", decoded?.courses?.get(0)?.sessions?.get(0)?.location)
        assertEquals(1, decoded?.courses?.get(0)?.sessions?.get(0)?.dayOfWeek)
    }

    @Test
    fun invalidTextReturnsNull() {
        assertNull(ShareCode.decode(""))
        assertNull(ShareCode.decode("普通聊天内容"))
        assertNull(ShareCode.decode("KXKB1:!!!not-base64!!!"))
    }

    @Test
    fun compatibleWithTangliCode() {
        val code = tangliCode()
        val decoded = ShareCode.decode(code)
        assertEquals(true, decoded?.fromTangli)
        assertEquals("同学", decoded?.name)
        assertEquals("大学英语", decoded?.courses?.first()?.name)
        assertEquals(3, decoded?.courses?.first()?.sessions?.first()?.startSection)
    }

    /** 构造一个「烫梨 TLKB1」格式的分享码，验证兼容能力。 */
    private fun tangliCode(): String {
        val json = """
            {"o":"同学","s":"2026-2027-1","d":"2026-09-07",
             "c":[{"n":"大学英语","t":[[2,3,4,"1-16","外语楼-305"]]}]}
        """.trimIndent()
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        deflater.setInput(json.toByteArray(Charsets.UTF_8))
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!deflater.finished()) {
            out.write(buffer, 0, deflater.deflate(buffer))
        }
        deflater.end()
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
        return ShareCode.TANGLI_PREFIX + encoded
    }
}
