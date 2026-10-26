# 校序（Xiaoxu Campus）

面向高校学生的「校园时间管理」Android App。**导入优先、数据本地、不登录学校系统**。

> 当前版本：0.9.9「校序」（内置 1515 所高校教务入口、统一登录教务系统、新用户三步引导；烫梨模式）
> 技术栈：Kotlin + Jetpack Compose + Material 3 + kotlinx.serialization
> 包名：`com.kexu.campus`

---

## 为什么这样设计

参考项目「烫梨」用 WebView 替用户登录教务系统取数。它很聪明，但：

1. 每所学校都要单独适配，维护成本极高；
2. 未获校方授权的"代登录 + 抓取"存在明确法律风险（详见仓库外部分析报告）。

所以「校序」第一版**完全不碰学校系统**：

- 课表来自你主动导入的 **CSV / 同学分享码 / 手动添加**；
- 成绩由你**手动录入**；
- 所有数据只存在本机（一个 JSON 文件），卸载即删除；
- 分享码只包含课程名、地点、时间、周次，不含成绩、学号、教师。

以后拿到学校授权，可以在 `DataSource` 层增加"官方 API / 授权同步"实现，而不改动 UI 与数据层——这就是"架构预留自动同步"的含义。

---

## 功能（MVP）

| 模块 | 能力 |
|---|---|
| 课表 | 周视图、左右切周、当前课高亮、点击查看/编辑、空白格加课、5/7 天模式 |
| 导入 | CSV 文件导入、PDF/Word/图片（OCR）识别课表、分享码导入、兼容「烫梨」分享码、手动加课 |
| 工具页 | 底部导航「工具」聚合记账、去广告、锁机、学校教务直通；记账数据、去广告状态、锁机倒计时一屏可见 |
| 分享 | 生成分享码 + 二维码，微信/QQ 发送或面对面扫码 |
| 成绩 | 手动录入、按学期筛选、加权平均、GPA 估算 |
| 记账 | 收支记录、分类、月度汇总、预算进度、支出分类环形图、CSV 导出 |
| 去广告 | 本地 DNS 过滤（VpnService + anti-AD 规则，约 10 万条），自定义屏蔽/放行，严格模式 |
| 锁机（轻量版） | 桌面替换：只显示允许的应用；其它应用拦回锁机桌面；短时解锁 5/10/15 分钟与次数配额；定时锁机、本周统计、按分类选应用、紧急退出、一键锁屏 |
| 同学 | 扫二维码或粘贴分享码添加；只共享忙闲时间；三色格对照：黑色=都有课、灰色=一方有课、白色=都没课；不显示课程名/地点 |
| 学校教务直通 | 内置学校目录 + 搜索 + 自定义入口；东华理工预设 `atrust1.ecut.edu.cn`；在 App 内 WebView 里自己登录学校网站，登录后读取当前课表/成绩页面文字，解析预览后导入；不后台抓取、不改动学校系统 |
| 多学校配置（0.9.x） | `SchoolProfile` 记录学校/厂商/教务地址/登录地址/域名白名单；自定义学校粘贴教务网址自动识别正方/强智并推导 `jwBase`；WebView 顶部「导入此页课表/成绩」按学校类型自动选解析：正方 `kbList`、强智 `#kbtable` DOM / `#dataList` 成绩 |
| aTrust / SSO 兼容 | 支持跨域 Cookie、`window.open` 新窗口、WebView UA 识别；提供「刷新 / 桌面版 / 诊断 / 浏览器打开」；诊断会显示 WebView 版本、控制台报错、当前 URL 和 iframe |
| 剪贴板兜底 | 在系统浏览器登录 aTrust/教务系统后复制课表/成绩页面文字，回 App「从剪贴板识别」解析导入；不依赖 App 内 WebView 渲染 |
| 东华理工统一门户同步（0.8） | 优先直连 aTrust 教务代理节点（`172-20-130-13.atrust.ecut.edu.cn`）→ 失效时回退 aTrust 工作台，自动点「新教务管理系统」→ 捕获代理节点 → 直接请求正方 `kbList` / 读取 jqGrid 成绩 DOM |
| 账号与一键同步 | 用 Android Keystore 加密保存教务账号（不进备份、可清除）；保存「课表页/成绩页」目标后，点「一键同步课表」自动填充登录并读取；验证码/统一身份认证需手动完成 |
| 校历 | 当前第几周、距放假天数、开学日期设置 |
| 导出 | ICS 日历导出、JSON 备份/恢复 |
| 其他 | 深色模式、示例数据、清空数据 |

---

## 构建

### 环境

- JDK 17 或 21
- Android SDK（compileSdk 35、build-tools 35.0.0）
- Gradle 8.9（项目可自行生成 wrapper）

### 命令

```bash
export JAVA_HOME=/path/to/jdk
export ANDROID_HOME=/path/to/android-sdk

# 生成 wrapper（首次）
gradle wrapper --gradle-version 8.9

# 编译 Debug APK
./gradlew assembleDebug

# 跑单元测试
./gradlew testDebugUnitTest
```

Windows 把 `./gradlew` 换成 `gradlew.bat`。

产物：`app/build/outputs/apk/debug/app-debug.apk`

### local.properties

```
sdk.dir=C:/path/to/android-sdk
```

---

## 目录结构

```
app/src/main/java/com/kexu/campus/
├── CampusApp.kt              # Application，持有 Repository
├── MainActivity.kt           # 单 Activity + Compose
├── data/
│   ├── Models.kt             # 数据模型（Course/Session/Grade/Friend/Settings）
│   ├── WeekRange.kt          # 周次解析（1-16、单双周、逗号分隔）
│   ├── ShareCode.kt          # 分享码编解码（含烫梨兼容）
│   ├── CampusRepository.kt   # JSON 本地存储 + 数据操作
│   ├── Utilities.kt          # 日期、CSV 导入、GPA、共同空闲、ICS 导出
│   ├── Ledger.kt             # 记账汇总/CSV
│   └── ...
├── dns/                      # 去广告：DnsFilterService / DnsPacket / DomainRules
├── focus/                    # 专注锁机：FocusService / LockAdminReceiver
├── ui/
│   ├── AppRoot.kt            # 导航 + 底部栏
│   ├── Common.kt             # 通用组件
│   ├── TimetableScreen.kt    # 课表
│   ├── CourseEditScreen.kt   # 加课/改课
│   ├── ImportScreen.kt       # 导入/分享
│   ├── GradesScreen.kt       # 成绩
│   ├── LedgerScreen.kt       # 记账
│   ├── FriendsScreen.kt      # 同学 + 共同空闲
│   ├── AdBlockScreen.kt      # 去广告
│   ├── FocusScreen.kt        # 专注锁机
│   ├── SettingsScreen.kt     # 设置/备份/关于
│   └── theme/Theme.kt        # 配色
└── ...
app/src/main/assets/          # ad-rules.runtime.json（anti-AD 规则）
app/src/test/                 # JVM 单元测试（周次/分享码/CSV/空闲/GPA/ICS/记账）
```

---

## 权限说明（0.3 起）

| 权限 | 用途 | 是否必须 |
|---|---|---|
| VPN（`BIND_VPN_SERVICE`） | 去广告：仅接管 DNS，不代理其它流量 | 使用去广告时 |
| 悬浮窗（`SYSTEM_ALERT_WINDOW`） | 专注时显示全屏倒计时遮罩 | 使用专注锁机时 |
| 使用情况访问（`PACKAGE_USAGE_STATS`） | 判断当前前台 App，只读包名 | 使用锁机时 |
| 默认桌面（HOME 角色） | 锁机时按 Home 回到锁机界面 | 推荐设置，不设也能靠遮罩拦截 |
| 设备管理器 | 一键锁屏（仅 `force-lock`，不擦除数据） | 一键锁屏时 |
| 相机（`CAMERA`） | 扫同学的课表二维码 | 使用扫码添加时 |
| 网络（`INTERNET`） | 学校教务直通：加载学校自己的网页、DNS 去广告 | 使用这些功能时 |
| 通知 | 去广告/专注的前台服务通知 | 可拒绝 |

> 这些权限只在对应功能开启时使用；App 不申请通讯录、定位、短信。

## 截图

| 课表 | 成绩 |
|---|---|
| ![课表](docs/screenshots/timetable.png) | ![成绩](docs/screenshots/grades.png) |

| 去广告 | 专注锁机 |
|---|---|
| ![去广告](docs/screenshots/adblock.png) | ![专注](docs/screenshots/focus.png) |

锁机桌面 / 拦截 / 短时解锁：

| 锁机桌面 | 拦截其它应用 |
|---|---|
| ![锁机桌面](docs/screenshots/lock-home.png) | ![拦截](docs/screenshots/lock-blocked.png) |

| 短时解锁中 | 锁机结束 |
|---|---|
| ![短时解锁](docs/screenshots/lock-brief-unlock.png) | ![锁机结束](docs/screenshots/lock-ended.png) |

| 工具页 | 锁机设置（统计/定时/分类） |
|---|---|
| ![工具页](docs/screenshots/tools.png) | ![锁机设置](docs/screenshots/lock-settings.png) |

| 同学忙闲三色格 | 图片 OCR 识别导入 |
|---|---|
| ![隐私三色格](docs/screenshots/privacy-grid.png) | ![OCR](docs/screenshots/ocr-import.png) |

| 学校教务直通 | WebView 内登录学校网站 |
|---|---|
| ![学校目录](docs/screenshots/school-sync.png) | ![学校登录](docs/screenshots/school-webview.png) |

| 教务账号（加密保存） | 一键同步自动填充登录 |
|---|---|
| ![账号加密保存](docs/screenshots/school-account.png) | ![自动填充](docs/screenshots/school-autologin.png) |

| 东华理工 aTrust 入口（登录页） | 统一门户同步（实验） |
|---|---|
| ![aTrust 登录](docs/screenshots/atrust-login.png) | ![统一门户同步](docs/screenshots/ecut-sync.png) |

![专注遮罩](docs/screenshots/focus-overlay.png)

## 测试记录

- 环境：MuMu 模拟器（Android 12 / SDK 32，x86_64），1920×1080。
- 已验证：启动、课表渲染、切周、成绩/记账页面、设置；去广告开启与 DNS 拦截（`000dn.com` 等被拦截，`baidu.com` 正常）；旧版专注遮罩与长按 5 秒紧急退出；设备管理器激活。
- 锁机（轻量版）已验证：设为默认桌面 → 开始锁机 → 锁机桌面只显示允许的应用 → 打开 Chrome（允许）正常、打开相册（不允许）被遮罩拦回 → 短时解锁 5 分钟后相册可用 → 紧急退出后限制立即解除。
- 0.8.0 借鉴 Tangli（MIT）的 `CampusSyncClient` / `JwglxtFetcher`：在 Chrome 里确认东华理工「新教务管理系统」就是正方 V-9.0（`172-20-130-13.atrust.ecut.edu.cn/jwglxt/...`，内有「学生课表查询(新)/学生成绩查询」）；App 优先直连该代理节点打开正方目标页，会话失效时回退 aTrust 工作台并自动点「新教务管理系统」捕获节点；课表用 XHR `POST /jwglxt/kbcx/xskbcx_cxXsKb.html?gnmkdm=N2151` 读 `kbList`，成绩读 `tabGrid` DOM。
- MuMu 实测：能登录 aTrust 并进入新教务系统/办事大厅代理页；但 MuMu 里 aTrust 工作台/办事大厅内容区依赖客户端环境，自动点卡片/直连节点在模拟器上未跑通。**需要在你装了 aTrust 客户端的手机上验证完整同步**；若直连失败会自动回退工作台，也可手动点「新教务管理系统」。
- 0.8.0 新增单元测试：JwglxtKbParser（jxb_id 聚合、教室随时段、周次）、JwglxtGradesParser（jqGrid 列索引）。
- 相机扫码本身在 MuMu 上没有可用摄像头，未做真实扫码头测试；建议真机扫一次 App 生成的二维码。
- PDF/Word 识别：.docx 走 zip+XML 直接解析，PDF 前 3 页与图片走 ML Kit 中文 OCR；识别文本用 `TimetableTextParser` 启发式解析（JVM 单测覆盖）。MuMu 的文件选择器对 docx 索引不完整，建议在真机上把「PDF / Word → 识别 → 解析 → 导入」各跑一遍。
- MuMu 是多显示器环境（应用窗口不在 display 0），代码已做「多显示器同时挂载遮罩」的兼容；真机单显示器不受影响。
- 轻量锁机的边界：没有 Device Owner 时，系统设置、安全模式、ADB 仍可绕过，属于自律工具；真·无法绕过的 Kiosk 需要 Device Owner（见可行性报告第 8 节）。

## 数据与隐私

- 课表/成绩/记账/同学数据默认只存本机；只有开启「去广告」时会访问上游 DNS（223.5.5.5 / 119.29.29.29），且请求不携带任何用户标识。
- 无账号体系、无统计 SDK、无广告。
- 数据文件：`files/campus.json`，原子写入；`allowBackup=false`。
- 分享码是压缩后的 JSON（Deflate + Base64URL），不含敏感字段。

---

## 路线图

- [x] 0.1.0 课表 / 成绩 / 同学 / 分享 / ICS / 备份
- [x] 0.2.0 记账（收支/分类/预算/图表/CSV）
- [x] 0.3.0 DNS 去广告（VpnService + anti-AD 规则，可自定义屏蔽/放行）
- [x] 0.4.0 锁机（轻量版）：桌面替换、允许应用白名单、短时解锁配额、紧急退出、一键锁屏
- [x] 0.7.0 教务账号加密保存、自动填充登录、保存同步目标、一键同步课表/成绩
- [x] 0.7.2 aTrust 白屏诊断（WebView 版本/控制台/iframe）、浏览器打开、剪贴板导入课表/成绩兜底
- [x] 0.8.0 东华理工统一门户静默同步（办事大厅代理 + 正方 kbList/jqGrid，移植自 Tangli）
- [ ] 0.9.0 课表桌面小组件、作息时间自定义、更多学校适配
- [ ] 0.9.0 多学期归档、成绩趋势、空教室
- [ ] 1.0.0 官方授权数据源（OAuth/API）

> 0.3 起会新增系统权限（VPN、悬浮窗、使用情况等），属于"直接 APK / GitHub 分发"场景；
> 如要上架应用商店，这些功能需要单独评估或做成可选插件。

## 许可证

**GNU General Public License v3.0（GPL-3.0）**

- 本项目整体以 GPL-3.0 分发，完整文本见 [LICENSE](LICENSE)。
- 你可以自由使用、修改、再分发；但**分发修改版或 APK 时必须同样以 GPL-3.0 开源**，并提供对应源码（GitHub 仓库即可）。
- 项目里来自 MIT 许可的第三方部分（如 Tangli ≤v1.2.3 的 DNS/正方解析、anti-AD 规则）保留其原始版权与 MIT 声明，见 [NOTICE.md](NOTICE.md)。MIT 与 GPL 兼容，可以一起以 GPL-3.0 分发。
- Tangli v1.2.4 起本身是 GPL-3.0；后续若参考/移植其 1.2.4 代码，会保留其版权声明，本项目也会继续以 GPL-3.0 分发。
- 本项目不包含任何第三方统计 SDK 或广告 SDK。
