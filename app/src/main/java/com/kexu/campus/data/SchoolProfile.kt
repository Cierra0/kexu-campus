package com.kexu.campus.data

import kotlinx.serialization.Serializable
import java.net.URI

/**
 * 一所学校的教务配置（参考 Tangli v1.2.4 的 SchoolProfile 思路，独立 Kotlin 实现）：
 * 厂商 / 教务根地址 / 登录地址 / 访问方式 / 域名白名单。
 *
 * 内置东华理工；其他学校由用户粘贴教务网址生成「自定义学校」。
 */
@Serializable
data class SchoolProfile(
    val id: String = "",
    val name: String = "",
    val vendor: String = "other",       // zhengfang | qiangzhi | other
    val jwBase: String = "",
    val loginUrl: String = "",
    val access: String = "direct",      // direct | atrust | webvpn
    val hosts: List<String> = emptyList(),
    val verified: Boolean = false,
    val ecutFeatures: Boolean = false
) {
    val isEcut: Boolean get() = id == ID_ECUT
    val vendorLabel: String get() = when (vendor) {
        "zhengfang" -> "正方"
        "qiangzhi" -> "强智"
        else -> "其他"
    }
    val accessLabel: String get() = when (access) {
        "atrust" -> "aTrust"
        "webvpn" -> "WebVPN"
        else -> "直连"
    }
    val supportsSync: Boolean get() = (vendor == "zhengfang" || vendor == "qiangzhi") && jwBase.isNotBlank()

    fun allowsUrl(url: String): Boolean {
        val host = hostOf(url).lowercase()
        if (host.isBlank()) return false
        return hosts.any { rule ->
            val r = rule.lowercase()
            when {
                r.startsWith("*.") -> host == r.substring(2) || host.endsWith("." + r.substring(2))
                else -> host == r
            }
        }
    }

    companion object {
        const val ID_ECUT = "ecut"
        const val ID_CUSTOM = "custom"

        val ECUT = SchoolProfile(
            id = ID_ECUT,
            name = "东华理工大学",
            vendor = "zhengfang",
            jwBase = "https://172-20-130-13.atrust.ecut.edu.cn/jwglxt/",
            loginUrl = "https://172-20-130-13.atrust.ecut.edu.cn/jwglxt/xtgl/index_initMenu.html?jsdm=xs",
            access = "atrust",
            hosts = listOf("*.ecut.edu.cn", "172.20.130.13"),
            verified = true,
            ecutFeatures = true
        )

        fun custom(input: String, vendor: String? = null): SchoolProfile? {
            val url = normalizeUrl(input) ?: return null
            val host = hostOf(url)
            if (host.isBlank()) return null
            val v = vendor ?: guessVendor(url)
            val origin = originOf(url)
            val path = pathOf(url)
            val lowerPath = path.lowercase()
            val base = when (v) {
                "zhengfang" -> {
                    val i = lowerPath.indexOf("/jwglxt/")
                    if (i >= 0) origin + path.substring(0, i + "/jwglxt/".length)
                    else if (lowerPath.endsWith("/jwglxt")) origin + path + "/"
                    else origin + "/jwglxt/"
                }
                "qiangzhi" -> {
                    val i = lowerPath.indexOf("/jsxsd/")
                    if (i >= 0) origin + path.substring(0, i + "/jsxsd/".length)
                    else if (lowerPath.endsWith("/jsxsd")) origin + path + "/"
                    else origin + "/jsxsd/"
                }
                else -> origin + "/"
            }
            val hosts = mutableListOf(host.lowercase())
            parentDomain(host)?.let { hosts.add("*.$it") }
            return SchoolProfile(
                id = ID_CUSTOM,
                name = "自定义学校",
                vendor = v,
                jwBase = base,
                loginUrl = url,
                access = when {
                    host.lowercase().contains("atrust") -> "atrust"
                    host.lowercase().contains("webvpn") -> "webvpn"
                    else -> "direct"
                },
                hosts = hosts,
                verified = false,
                ecutFeatures = false
            )
        }

        fun guessVendor(url: String): String {
            val path = pathOf(url).lowercase()
            if (path.contains("/jsxsd")) return "qiangzhi"
            if (path.contains("/jwglxt")) return "zhengfang"
            if (hostOf(url).lowercase().startsWith("jwglxt.")) return "zhengfang"
            return "other"
        }

        fun normalizeUrl(input: String): String? {
            var s = input.trim().replace(" ", "")
            if (s.isBlank()) return null
            if (!s.matches(Regex("(?i)^https?://.*"))) s = "https://$s"
            return runCatching {
                val uri = URI.create(s)
                if (uri.host.isNullOrBlank() || !uri.host.contains(".")) null else s
            }.getOrNull()
        }

        fun hostOf(url: String): String = runCatching { URI.create(url).host.orEmpty() }.getOrDefault("")

        fun originOf(url: String): String = runCatching {
            val uri = URI.create(url)
            "${uri.scheme ?: "https"}://${uri.rawAuthority}"
        }.getOrDefault("")

        fun pathOf(url: String): String = runCatching {
            URI.create(url).rawPath.orEmpty()
        }.getOrDefault("")

        /** 主域名：jw.abc.edu.cn -> *.abc.edu.cn；IP 返回 null。 */
        fun parentDomain(host: String): String? {
            val h = host.lowercase()
            if (h.matches(Regex("[0-9.]+"))) return null
            val parts = h.split(".")
            val keep = if (h.matches(Regex(".*\\.(edu|com|org|net|gov|ac)\\.[a-z]{2}"))) 3 else 2
            if (parts.size <= keep) return null
            return parts.takeLast(keep).joinToString(".")
        }
    }
}
