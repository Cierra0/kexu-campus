package com.kexu.campus.data

data class SchoolSite(
    val name: String,
    val region: String,
    val siteUrl: String,
    val entryUrl: String = ""
)

/**
 * 学校目录：只收录学校官网地址（东华理工额外带办事大厅入口）。
 * 其他学校可以打开官网后自己找「教务系统 / 网上办事大厅」，或直接在 App 里填自定义入口。
 *
 * 注意：这里只提供入口，不包含任何学校账号、密码或后台抓取逻辑；
 * 登录在 WebView 中由用户本人完成。
 */
object SchoolDirectory {

    val schools = listOf(
        SchoolSite("东华理工大学", "江西", "https://www.ecut.edu.cn", "https://atrust1.ecut.edu.cn"),
        SchoolSite("南昌大学", "江西", "https://www.ncu.edu.cn"),
        SchoolSite("江西师范大学", "江西", "https://www.jxnu.edu.cn"),
        SchoolSite("江西财经大学", "江西", "https://www.jxufe.edu.cn"),
        SchoolSite("华东交通大学", "江西", "https://www.ecjtu.edu.cn"),
        SchoolSite("南昌航空大学", "江西", "https://www.nchu.edu.cn"),
        SchoolSite("江西理工大学", "江西", "https://www.jxust.edu.cn"),
        SchoolSite("北京大学", "北京", "https://www.pku.edu.cn"),
        SchoolSite("清华大学", "北京", "https://www.tsinghua.edu.cn"),
        SchoolSite("中国人民大学", "北京", "https://www.ruc.edu.cn"),
        SchoolSite("北京航空航天大学", "北京", "https://www.buaa.edu.cn"),
        SchoolSite("复旦大学", "上海", "https://www.fudan.edu.cn"),
        SchoolSite("上海交通大学", "上海", "https://www.sjtu.edu.cn"),
        SchoolSite("同济大学", "上海", "https://www.tongji.edu.cn"),
        SchoolSite("浙江大学", "浙江", "https://www.zju.edu.cn"),
        SchoolSite("南京大学", "江苏", "https://www.nju.edu.cn"),
        SchoolSite("东南大学", "江苏", "https://www.seu.edu.cn"),
        SchoolSite("武汉大学", "湖北", "https://www.whu.edu.cn"),
        SchoolSite("华中科技大学", "湖北", "https://www.hust.edu.cn"),
        SchoolSite("中山大学", "广东", "https://www.sysu.edu.cn"),
        SchoolSite("四川大学", "四川", "https://www.scu.edu.cn"),
        SchoolSite("西安交通大学", "陕西", "https://www.xjtu.edu.cn"),
        SchoolSite("哈尔滨工业大学", "黑龙江", "https://www.hit.edu.cn"),
        SchoolSite("中国科学技术大学", "安徽", "https://www.ustc.edu.cn"),
        SchoolSite("天津大学", "天津", "https://www.tju.edu.cn")
    )

    fun search(query: String): List<SchoolSite> {
        val q = query.trim()
        if (q.isEmpty()) return schools
        return schools.filter { it.name.contains(q, ignoreCase = true) || it.region.contains(q) }
    }
}
