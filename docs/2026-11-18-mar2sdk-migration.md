# 商业 SDK 迁移记录

当前项目已通过 GitHub Packages Maven 接入 `com.mar2game.mar2sdk` 的 `core`、`impl`、`debug`，统一固定为 **2.0.15**。已读取 Maven metadata 确认这是当前最新版；仓库凭据继续从用户 Gradle 配置或环境变量读取。

SDK 依赖要求将构建工具升级为 AGP **9.3.2**、Gradle **9.5.0**、JDK **21**、compileSdk **37**。应用保持 targetSdk **36**、minSdk **29** 和 JVM 编译目标 **11**。

保留原来的 30 个广告点位：2 个开屏、27 个插屏，以及继续禁用的 `openMainBottomAdv`。原 AdMob 应用和广告单元 ID、数数与 Singular 配置保持；业务事件名和参数沿用原调用，通过 `Core.log` 等新 API 上报。

全屏广告统一使用 `impl` 的 `AdActivity.showAd`。SDK 负责策略、加载和展示，宿主观察 `ON_PAUSE → ON_RESUME`，返回后只执行一次原业务回调；广告页未启动时直接继续。启动广告页成功不代表实际曝光。初始化、UMP、预加载和前台广告衔接由宿主适配，避免重复策略判断和触发埋点。

原业务事件保留。旧宿主轮询专用的 `splash_open_ad_load_timeout`、`splash_open_ad_close_timeout` 不再产生，加载和关闭诊断由 SDK 事件记录；`AdActivity` 当前不向宿主提供失败或关闭结果回调。

配置位于 `app/src/main/res/raw/`：

| 文件 | 用途 |
| --- | --- |
| `ad_config.json`、`admob_config.json` | 原广告点位策略、AdMob 单元与缓存配置 |
| `common_config.json` | 包名、商业服务与 Play Integrity |
| `thinking_config.json`、`singular_config.json`、`log_config.json` | 数数、归因和日志渠道 |
| `notification_config.json`、`notification_content.json` | 通知触发策略和多语言内容 |

按本次用户授权，商业后台身份暂用参考项目 `DataRestoreFileRecovery` 的配置；本文不记录密钥。需要独立后台统计时，更换 `common_config.json` 中对应产品的服务地址、`serverAppID` 和 `serverClientKey`。

通知保留原 52 条通用内容及 3 条删除提醒，共 55 条内容、388 个语言变体。删除场景保持首次等待 600 秒、场景间隔 3600 秒；未启用 SDK 示例的其他触发器。`CommercialNotificationRouter` 负责常驻通知目的页和删除观察事件的适配，常驻入口保留 Clean、File、App、Battery 及原图标。

已直接检查 Maven **2.0.15** 的 source JAR 和 AAR 字节码：`getContent(scene)` 调用 `NotificationContentSelector`，按场景筛选并选择当前语言。SDK 使用文档第 6 节中“始终取第一项、不按场景和语言筛选”的描述已过时。

验证结果：Gradle `help`、`build --dry-run`、Debug 和 Release（含 R8）构建通过。隔离原有失效文件后，27 项单元测试通过，覆盖广告点位、广告页返回时序、前台广告机会及通知配置。

原全量单测中的 `ScanFilesStateTest` 引用全工程不存在的 `deepCopyCategories`，本次未修改该扫描模块或相关测试。验证时使用本地临时 init 脚本，在 `compileDebugUnitTestKotlin` 任务上排除 `**/ScanFilesStateTest.kt`；普通全量单测仍会被这一既有问题阻断。Release 产物为未签名 APK，因为本机未配置发布签名。

设备上的实际广告曝光、UMP 表单及通知授权仍需使用对应运行环境验收。可用模拟器首次启动曾在 Firebase Provider 的字节码校验阶段发生启动 ANR，期间有明显内存压力，未进入商业 SDK 初始化；ARM64 设备可安装并启动到系统权限页。
