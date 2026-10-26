# NOTICE · 第三方组件与许可证

本项目（校序 / Xiaoxu Campus）整体以 **GPL-3.0** 分发（完整文本见 [LICENSE](LICENSE)）；以下部分参考/移植了开源项目，保留其原始版权与许可证声明：

## TangLi（烫梨）

- 仓库：https://github.com/augustlies/tangli
- 许可证：**v1.2.3 及以前为 MIT；v1.2.4 起为 GPL-3.0**
- 已使用部分（来自 MIT 时期版本，保留 MIT 声明）：
  - `app/src/main/java/com/kexu/campus/dns/DnsPacket.java`（DNS 报文解析/构造，移植并改包名）
  - `app/src/main/java/com/kexu/campus/dns/DnsFilterService.java`（仅 DNS 的 VpnService 实现，移植并增加 Android 14+ 前台服务类型、自定义域名、计数、多显示器适配）
  - `app/src/main/java/com/kexu/campus/dns/DomainRules.java`（域名后缀匹配，移植并支持自定义屏蔽/放行）
  - `app/src/main/assets/ad-rules.runtime.json`（广告域名规则，来自 anti-AD 并经该项目整理）
  - 0.8.x 东华理工同步流程参考其 `CampusSyncClient.java` / `JwglxtFetcher.java` / `Grades.java`（aTrust 节点嗅探、正方 `kbList` 课表接口、jqGrid 成绩 DOM 列映射），已用 Kotlin 重写并适配
- 若后续移植 v1.2.4 及以后（GPL-3.0）的代码，会在对应文件保留其版权声明；本项目同为 GPL-3.0，满足许可证兼容要求。

## anti-AD

- 仓库：https://github.com/privacy-protection-tools/anti-AD
- 许可证：MIT
- 使用部分：`ad-rules.runtime.json` 中的广告域名规则（约 10 万条）

## 其他依赖

- ZXing（Apache-2.0）：二维码生成/扫描
- ML Kit（Google，受其服务条款约束）：中文 OCR 文字识别
- AndroidX / Jetpack Compose / Kotlin（Apache-2.0）

## 说明

- 图标、界面、课表/记账/专注逻辑为原创。
- 本项目不包含任何第三方统计 SDK 或广告 SDK。
- 学校教务同步仅用于用户本人账号的个人学习管理；不保存学校密码到服务器、不后台批量抓取、不修改学校系统。

> 分发本项目（含 APK）时，请保留本文件、`LICENSE` 与上述许可证声明，并提供对应源码（GitHub 仓库即可），同时自行评估「DNS 广告过滤」「应用限制」「教务同步」功能在当地的合规风险。
