package com.kexu.campus

/**
 * 发布相关配置。
 *
 * GitHub 仓库名建议用英文（GitHub 对仓库名有字符限制）：
 * owner = 你的 GitHub 用户名，repo = 仓库名（如 kexu-campus）。
 * 首次发版后把 tag 命名为 v0.9.0 这种格式，App 会拿它和当前 versionName 比较。
 */
object AppConfig {
    const val GITHUB_OWNER = "Cierra0"
    const val GITHUB_REPO = "kexu-campus"
    const val CONTACT_EMAIL = "2377553531@qq.com"
    const val CHECK_UPDATE_DAILY = true
    const val UPDATE_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
}
