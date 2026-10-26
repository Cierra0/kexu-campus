package com.kexu.campus.data

/** 正方教务（V9，/jwglxt/）适配：检测、课表/成绩地址、页面内提取脚本。 */
object ZhengfangSource {

    const val TIMETABLE_PATH = "kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default"
    const val GRADES_PATH = "cjcx/cjcx_cxDgXscj.html?gnmkdm=N305005&layout=default"

    fun detect(url: String, html: String): Int {
        var score = 0
        val u = url.lowercase()
        if (u.contains("/jwglxt/") || u.endsWith("/jwglxt")) score += 50
        if (u.contains("xskbcx") || u.contains("login_slogin") || u.contains("/xtgl/")) score += 20
        if (html.contains("kbgrid_table") || html.contains("xskbcx_cx")) score += 30
        if (html.contains("ajaxForm") && html.contains("xnm") && html.contains("xqm")) score += 10
        if (html.contains("正方软件") || html.contains("zftal")) score += 10
        return minOf(100, score)
    }

    fun timetableUrl(profile: SchoolProfile): String? =
        profile.jwBase.takeIf { it.isNotBlank() }?.let { it.trimEnd('/') + "/" + TIMETABLE_PATH }

    fun gradesUrl(profile: SchoolProfile): String? =
        profile.jwBase.takeIf { it.isNotBlank() }?.let { it.trimEnd('/') + "/" + GRADES_PATH }

    /**
     * 在当前正方页面里请求 kbList 接口。自动从 pathname 推导 /jwglxt/ 前缀，
     * 兼容根目录部署；主接口没数据时尝试 cxXsgrkb。
     */
    fun extractionJs(): String = """
        (function(){
          try {
            var p = location.pathname;
            var base = '/jwglxt/';
            var i = p.indexOf('/jwglxt/');
            if (i >= 0) { base = p.substring(0, i + 8); }
            else {
              var ks = ['/kbcx/', '/xtgl/', '/cjcx/'];
              for (var k = 0; k < ks.length; k++) {
                var j = p.indexOf(ks[k]);
                if (j >= 0) { base = p.substring(0, j + 1); break; }
              }
            }
            var y = document.getElementById('xnm');
            var t = document.getElementById('xqm');
            var xnm = (y && y.value) ? y.value : '';
            var xqm = (t && t.value) ? t.value : '';
            var body = 'xnm=' + encodeURIComponent(xnm) + '&xqm=' + encodeURIComponent(xqm) + '&kzlx=ck&xsdm=';
            function post(path) {
              var x = new XMLHttpRequest();
              x.open('POST', base + path, false);
              x.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
              x.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
              x.send(body);
              return x.responseText || '';
            }
            var out = '';
            try { out = post('kbcx/xskbcx_cxXsKb.html?gnmkdm=N2151'); } catch (e) {}
            if (out.indexOf('kbList') < 0) {
              try {
                var t2 = post('kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151');
                if (t2.indexOf('kbList') >= 0) out = t2;
              } catch (e) {}
            }
            return out;
          } catch (e) { return 'ERR:' + e; }
        })()
    """.trimIndent()
}
