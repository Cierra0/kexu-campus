package com.kexu.campus.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 正方 V9 新版「周课表」DOM 解析：
 * #coursesTable > .Courses-content > ul.stage_N（N=起始节次）> li（周一..周日）
 *   > span.course-hasContent（text 为 "课程名<br>地点"）
 * 页面不带周次字段，按整学期 1..totalWeeks 导入。
 */
object ZhengfangDomTimetable {

    fun extractionJs(): String = """
        (function(){
          try{
            var table=document.getElementById('coursesTable')||document.querySelector('#coursesTable');
            if(table){
              var items=[]; var uls=table.querySelectorAll('.Courses-content ul');
              for(var u=0;u<uls.length;u++){
                var ul=uls[u];
                var m=(ul.className||'').match(/stage_(\d+)/);
                var section=m?parseInt(m[1],10):null;
                var lis=ul.children;
                for(var c=0;c<lis.length;c++){
                  var lis2=lis[c].querySelectorAll('.course-hasContent');
                  for(var k=0;k<lis2.length;k++){
                    var el=lis2[k];
                    var text=(el.innerHTML||'').replace(/<br\s*\/?>/gi,'\n').replace(/<[^>]+>/g,'').replace(/&nbsp;/g,' ').trim();
                    items.push({day:c+1,section:section,text:text});
                  }
                }
              }
              var body=(document.body&&document.body.innerText)?document.body.innerText:'';
              var wk=(body.match(/第\s*(\d+)\s*周/)||[])[1]||'';
              var mon=(document.querySelector('.rq-head-1')&&document.querySelector('.rq-head-1').innerText)?document.querySelector('.rq-head-1').innerText.trim():'';
              return JSON.stringify({items:items,week:wk,monday:mon});
            }
            return '';
          }catch(e){return '';}
        })()
    """.trimIndent()

    data class Result(
        val courses: List<Course>,
        val warnings: List<String> = emptyList(),
        val currentWeek: Int? = null,
        val mondayText: String = ""
    )

    fun parse(json: String, totalWeeks: Int = 16): Result {
        val root = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull()
            ?: return Result(emptyList(), listOf("不是有效 JSON"))
        val items = root["items"]?.jsonArray ?: return Result(emptyList(), listOf("没有课程块"))
        val currentWeek = root["week"]?.jsonPrimitive?.content?.toIntOrNull()
        val mondayText = root["monday"]?.jsonPrimitive?.content.orEmpty()
        val fullWeeks = "1-${totalWeeks.coerceAtLeast(1)}"

        val courses = LinkedHashMap<String, Course>()
        for (elem in items) {
            val obj = runCatching { elem.jsonObject }.getOrNull() ?: continue
            val day = obj["day"]?.jsonPrimitive?.content?.toIntOrNull() ?: continue
            val section = obj["section"]?.jsonPrimitive?.content?.toIntOrNull() ?: continue
            val text = obj["text"]?.jsonPrimitive?.content.orEmpty()
            if (day !in 1..7 || section !in 1..12) continue
            val lines = text.split('\n').map { it.trim() }.filter { it.isNotBlank() }
            if (lines.isEmpty()) continue
            val name = lines.first()
            val location = lines.getOrNull(1).orEmpty()
            val weeks = Regex("(?:第)?(\\d{1,2})(?:\\s*-\\s*(\\d{1,2}))?\\s*周")
                .find(text)?.let { m ->
                    val a = m.groupValues[1]
                    val b = m.groupValues.getOrNull(2).orEmpty()
                    if (b.isNotBlank()) "$a-$b" else a
                } ?: fullWeeks
            val key = "${name}|${day}|${section}"
            val session = Session(
                dayOfWeek = day,
                startSection = section,
                endSection = section + 1,
                weeks = weeks,
                location = location
            )
            val existing = courses[key]
            courses[key] = if (existing == null) {
                Course(id = "zfd_${courses.size}", name = name, source = "zhengfang", sessions = listOf(session))
            } else {
                existing.copy(sessions = existing.sessions + session)
            }
        }
        if (courses.isEmpty()) return Result(emptyList(), listOf("页面表格里没有课程块"))
        return Result(courses.values.toList(), currentWeek = currentWeek, mondayText = mondayText)
    }
}
