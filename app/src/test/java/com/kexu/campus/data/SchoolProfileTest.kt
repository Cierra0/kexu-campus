package com.kexu.campus.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SchoolProfileTest {

    @Test
    fun customUrlDetectsZhengfangAndDerivesBase() {
        val p = SchoolProfile.custom("jw.example.edu.cn/jwglxt/xtgl/index_initMenu.html?jsdm=xs")
        assertNotNull(p)
        assertEquals("zhengfang", p!!.vendor)
        assertEquals("https://jw.example.edu.cn/jwglxt/", p.jwBase)
        assertTrue(p.hosts.contains("jw.example.edu.cn"))
        assertTrue(p.hosts.contains("*.example.edu.cn"))
        assertTrue(p.allowsUrl("https://jw.example.edu.cn/jwglxt/anything"))
    }

    @Test
    fun customUrlDetectsQiangzhi() {
        val p = SchoolProfile.custom("https://jw.example.edu.cn/jsxsd/framework/xsMain.jsp")
        assertEquals("qiangzhi", p?.vendor)
        assertEquals("https://jw.example.edu.cn/jsxsd/", p?.jwBase)
    }

    @Test
    fun invalidUrlReturnsNull() {
        assertNull(SchoolProfile.custom(""))
        assertNull(SchoolProfile.custom("not a url"))
    }

    @Test
    fun ecutProfileIsVerifiedAndSupportsSync() {
        val p = SchoolProfile.ECUT
        assertEquals("zhengfang", p.vendor)
        assertTrue(p.verified)
        assertTrue(p.supportsSync)
        assertTrue(p.allowsUrl("https://172-20-130-13.atrust.ecut.edu.cn/jwglxt/x"))
    }

    @Test
    fun zhengfangUrlsAreBuiltFromJwBase() {
        val t = ZhengfangSource.timetableUrl(SchoolProfile.ECUT)
        assertTrue(t!!.endsWith("/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default"))
        val g = ZhengfangSource.gradesUrl(SchoolProfile.ECUT)
        assertTrue(g!!.contains("/jwglxt/cjcx/"))
    }

    @Test
    fun zhengfangDetectScoresJwglxtPages() {
        assertTrue(ZhengfangSource.detect("https://a.edu.cn/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html", "") >= 70)
        assertEquals(0, ZhengfangSource.detect("https://a.edu.cn/other", ""))
    }
}
