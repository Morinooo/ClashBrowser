# ClashBrowser

[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue.svg)](LICENSE)
[![Android build](https://github.com/Morinooo/ClashBrowser/actions/workflows/android.yml/badge.svg)](https://github.com/Morinooo/ClashBrowser/actions/workflows/android.yml)
[![Platform](https://img.shields.io/badge/platform-Android%208.0%2B%20(arm64--v8a)-green.svg)](#环境要求)

**内置 mihomo 内核的轻量 Android 浏览器——只代理自己的 WebView，不需要 VPN 服务。**

ClashBrowser 是一个轻量、无广告的 Android 浏览器，内置 [mihomo](https://github.com/MetaCubeX/mihomo)（Clash Meta）内核与应用内代理面板。浏览器流量通过 AndroidX WebKit 的 `ProxyController` 指向本机 mihomo 混合端口，因此可以在**不申请 Android VPN 权限**、**不影响手机上其它应用**的前提下访问代理站点与 DRM 流媒体网站。

> 本项目为独立项目，与 MetaCubeX、Clash 项目或 mihomo 项目均无隶属、背书或维护关系。

[English README](README.md)

---

## 下载

正式签名 APK 发布在 **[Releases](https://github.com/Morinooo/ClashBrowser/releases)**。

- [最新版本](https://github.com/Morinooo/ClashBrowser/releases/latest)
- 要求 **Android 8.0+（API 26）**，且为 **arm64-v8a** 设备
- 使用 `SHA256SUMS` 校验下载文件：`sha256sum -c SHA256SUMS`
- Release APK 由推送 `v*` Tag 触发的发布工作流自动构建并签名，不是 debug 包
- 未上架 Google Play

ClashBrowser **不内置**任何订阅、节点或代理服务，你需要自备 Clash 兼容订阅或配置。

---

## 和普通浏览器、传统代理软件有什么区别？

| | 普通浏览器 | 系统级代理 / VPN 客户端 | ClashBrowser |
|---|---|---|---|
| 需要 VPN 权限 | 否 | 是 | **否** |
| 影响其它应用 | 否 | 是 | **否** |
| 内置订阅 / 节点 / 策略组管理 | 否 | 一般有 | **有** |
| 内核被杀后的恢复 | — | 视客户端而定 | **前台自动复活、回前台重启** |
| 需要安装的 App 数量 | 1 | 浏览器 + 客户端 | **1** |

设计思路：把 Clash 配置中心直接放进浏览器，并让代理范围严格限定在浏览器自己的网络栈内。

## 功能

### 浏览
- 多标签浏览：每个标签独立 WebView、懒创建；标签切换器；长按标签计数按钮快速新建
- 紧凑地址栏：直达网址、无空格含点自动补 `https://`、其余走 Google 搜索；聚焦时展开并显示清空按钮
- 前进 / 后退 / 刷新 / 停止、加载进度条、主页按钮
- `target="_blank"` / `window.open()` 新建标签承载（可开关）
- HTML5 视频全屏播放，返回键退出全屏

### 内置代理内核
- 内置 mihomo 以**子进程**运行，由 App 启动、停止、重启
- WebView 流量通过 `androidx.webkit.ProxyController` 指向 `127.0.0.1:7890`（免 VPN、免 root）
- 订阅链接导入、YAML 校验、原子写入、备份自愈、热重载
- 配置档案卡片：名称、流量用量 / 总量、到期日期、用量进度条、相对更新时间（解析 `subscription-userinfo` 响应头）
- 节点与策略组列表（Selector / URLTest / Fallback / LoadBalance）、单节点测速、批量测速
- 运行模式切换：全局 / 规则 / 直连（`PATCH /configs`）；全局模式下若 `GLOBAL` 指向 `DIRECT` 会自动校正
- 全屏 YAML 编辑器，可直接编辑 `config.yaml`
- 内核日志实时查看
- 代理总开关（关闭即直连并销毁内核）

### 内核可靠性
- 等待 `/version` 就绪后才允许加载网页
- 启动失败自动重试一次；通过 `/proc` 清理残留内核进程
- 前台内核被系统查杀后约 1.5 秒自动复活；回前台时重启并重新绑定代理
- 配置只在丢失时从备份恢复，绝不被解析结果误重置
- 划掉最近任务时强制销毁内核，不驻留后台

### 浏览数据
- 本地主页：Logo、搜索框、最多 8 个来自收藏夹的圆形图标
- 收藏夹：多级文件夹、重命名 / 改网址 / 移动 / 删除 / 多选、搜索、全部删除，Netscape HTML 导入导出（保留文件夹层级、自动去重）
- 浏览记录（上限 500 条）：查看 / 打开 / 删除 / 清空；无痕模式开关
- 意外关闭恢复：进程被直接杀死后，下次冷启动询问是否恢复上次标签

### 舒适性与媒体
- 外观模式：浅色 / 深色 / 跟随系统；切换时重建 WebView 并保留各标签前进后退历史
- 网页深浅色同步（算法暗化 / Force Dark，取决于 WebView 支持）
- 浏览器标识：安卓手机 / 电脑 UA 切换
- 长按上下文菜单：后台打开、新标签打开、查看图片、保存图片、下载图片、分享图片、看图模式、页面信息、标记广告、复制链接文本、扫描二维码、复制链接、分享
- 图片保存走 MediaStore（API 29+）或应用专属目录（API 26–28）；分享走 FileProvider；二维码用 ZXing 离线解码
- 非图片下载管理器：任务列表含实时进度、重试、打开、二次确认删除；API 29+ 存入系统 Download/ClashBrowser，旧系统存应用私有目录；跟随应用内代理与会话 Cookie
- 文件上传：`<input type="file">` 经 SAF 唤起系统文件选择器（支持多选）
- 页内查找（实时计数/上下跳转）、阅读模式（正文纯净重排/字号可调）、资源嗅探（按标签的流地址列表：复制/下载/打开）
- 工具箱-更多集中设置：搜索引擎可选（Google/Bing/百度/DuckDuckGo）、桌面模式（UA+真桌面视口）、无痕隔离（不写历史/快照/缓存，退出清除无痕站点 Cookie）、订阅信息（userinfo 解析加固+刷新）
- DRM / EME 加固：放行 `RESOURCE_PROTECTED_MEDIA_ID`、第三方 Cookie、标准 Chrome 移动 UA（去掉 `wv` 标记）、许可证服务商与媒资 CDN 白名单、EME 探测 4 秒限时回退
- 密码输入加固：输入法类型改写为原生密码类型、禁止个性化学习、获焦时重建输入连接，稳定触发厂商安全键盘（如 vivo）
- 轻量广告 / 追踪域名拦截 + 用户标记广告域名

## 软件截图

仓库暂未收录截图，也不使用任何伪造图片。计划中的截图与贡献方式见 [docs/screenshots/README.md](docs/screenshots/README.md)。

## 技术栈

| 方面 | 选型 |
|---|---|
| 语言 | Kotlin 1.9.24（JVM target 17） |
| UI | Jetpack Compose（BOM 2024.06.00）、Material 3、单 `ComponentActivity` |
| 浏览器内核 | 系统 WebView + `androidx.webkit` 1.11.0 |
| 代理内核 | 预编译 mihomo `android-arm64-v8a` 可执行文件，子进程运行 |
| 网络 | OkHttp 4.12.0（订阅拉取、本机 Clash REST API、图片拉流） |
| 配置解析 | SnakeYAML 2.2（仅校验） |
| 二维码 | ZXing core 3.5.3 |
| 并发 | kotlinx.coroutines 1.8.1（`StateFlow`） |
| 存储 | SharedPreferences + 应用私有文件（无数据库） |
| 构建 | AGP 8.5.2、Gradle 8.7、JDK 17、`minSdk 26`、`targetSdk 34`、仅 `arm64-v8a` |

## 技术架构

```
                    ┌──────────────────────────────────────────────┐
                    │              MainActivity (Compose)          │
                    │  标签 · 地址栏 · 主页 · 底部工具栏            │
                    └───────┬───────────────┬──────────────┬───────┘
                            │               │              │
                ┌───────────▼───┐   ┌───────▼──────┐  ┌────▼──────────┐
                │  ProxyPanel   │   │ ToolboxSheet │  │  HomeScreen   │
                │  （代理面板）  │   │  （工具箱）   │  │  （主页）      │
                └───────┬───────┘   └───────┬──────┘  └────┬──────────┘
                        │                   │              │
                        ▼                   ▼              ▼
                ┌─────────────────────────────────────────────────────┐
                │                     ProxyManager                    │
                │  进程控制 · Clash REST API · 订阅 / 档案             │
                │  配置写入与热重载 · 日志 · 运行模式                  │
                └──────┬───────────────────────┬──────────────────────┘
                       │ ProcessBuilder        │ OkHttp
                       ▼                       ▼
        libmihomo.so（子进程）        http://127.0.0.1:9090
        mixed-port 127.0.0.1:7890     (/version /proxies /configs /delay)
                       ▲
                       │ HTTP 代理
        ┌──────────────┴──────────────┐
        │  系统 WebView 网络栈         │
        │  ProxyController            │
        │  127.0.0.1:7890             │
        └──────────────┬──────────────┘
                       ▼
                网页 / DRM 流媒体
```

### 工作原理

1. 首次启动时把内核二进制与 Geo 数据库放到应用 `filesDir/mihomo`，以子进程方式启动内核（`libmihomo.so -d filesDir/mihomo`），并轮询 `GET /version` 直到就绪。
2. 通过 `ProxyController.setProxyOverride(http://127.0.0.1:7890)` 设置 WebView 代理。只有本应用 WebView 使用它，系统与其它应用不受影响，也不涉及 VPN 权限。
3. HTTPS 请求以 `CONNECT` 隧道到达 mihomo，由内核解析目标域名并应用订阅中的规则与策略组。
4. 配置经校验、强制保留 `mixed-port: 7890` / `external-controller: 127.0.0.1:9090` 后原子写入，并通过 `PUT /configs?force=true` 热重载。
5. App 监视内核进程：前台被杀约 1.5 秒后重启；后台被杀则在回到前台时重启并重新绑定代理。

### 目录结构

```
app/src/main/java/com/jomo/clashbrowser/
├── MainActivity.kt        # Activity、标签、WebView 工厂、地址栏、上下文菜单分发
├── ProxyManager.kt        # 内核进程、Clash REST API、订阅、档案、配置、日志
├── ProxyPanel.kt          # 代理配置中心
├── ToolboxSheet.kt        # 工具箱：收藏夹、历史、外观、UA、关于
├── HomeScreen.kt          # 本地主页与图标编辑
├── ContextMenu.kt         # 长按菜单、看图模式、页面信息、扫码结果
├── MediaActions.kt        # 图片拉取 / 保存 / 下载 / 分享 / 二维码解码
├── DownloadStore.kt       # 下载管理：任务 / 进度 / 重试 / 打开 / 删除
├── BookmarkStore.kt       # 书签树 + Netscape HTML 导入导出
├── HomeStore.kt           # 首页图标偏好
├── HistoryStore.kt        # 历史记录 + 无痕开关
├── SessionStore.kt        # 标签快照、干净退出标记、恢复提示
├── AdBlocker.kt           # 域名黑名单拦截
├── SecureWebView.kt       # 密码键盘加固
├── WebViewProxyHelper.kt  # WebView ProxyController 绑定
├── CoreKeeperService.kt   # 划掉任务时的哨兵（销毁内核、标记干净退出）
└── UiTheme.kt             # 主题辅助、WebView 深浅色同步
```

## 环境要求

- Android 8.0（API 26）及以上，**arm64-v8a** 真机
- 较新的 Android System WebView
- 可用的 Clash 兼容订阅（或手写 `config.yaml`）
- 构建环境：JDK 17、Android SDK Platform 34，首次构建需要联网

## 从源码构建

```bash
git clone <this-repository>
cd ClashBrowser
./gradlew assembleDebug
```

首次构建时，Gradle 任务 `fetchCoreAssets` 会在缺失时下载以下文件到源码目录：

- `app/src/main/jniLibs/arm64-v8a/libmihomo.so` —— GitHub Releases 上的官方 `mihomo-android-arm64-v8a` 构建
- `app/src/main/assets/Country.mmdb` —— MetaCubeX/meta-rules-dat 的 `country-lite.mmdb`
- `app/src/main/assets/geosite.dat` —— 来自 MetaCubeX/meta-rules-dat

这些二进制文件**不提交到本仓库**（见 `.gitignore`），原因是体积与许可证。构建需要能访问 GitHub；脚本在 GitHub 不可达时会回退到第三方镜像，相关供应链注意事项见 [SECURITY.md](SECURITY.md)。

Windows 离线方式：

```powershell
pwsh -File scripts/fetch-assets.ps1
```

或手动把三个文件放到上述路径。

产物：`app/build/outputs/apk/debug/app-debug.apk`

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> 本仓库未配置 release 签名。debug APK 仅供测试，请勿对外分发。

## 使用

1. 打开 App，内核自动启动；右上角指示灯绿色=代理生效，红色=直连。
2. 点指示灯打开代理配置中心，粘贴订阅链接并更新，YAML 会自动校验并热重载。
3. 在 Selector 策略组里点节点切换；「全部测速」查看各节点延迟。
4. 按需切换全局 / 规则 / 直连。
5. 「编辑配置」可修改原始 `config.yaml`；「内核日志」用于排查问题。

> **注意**：订阅拉取目前是直连（不走代理），因此订阅地址本身必须无需代理即可访问。

## 当前开发状态

版本 **1.15.0**（`versionCode 22`），持续开发中。版本名遵循 semver（新增功能=次版本、修复=修订号），旧号对照见 CHANGELOG。

| 模块 | 状态 |
|---|---|
| 浏览、标签、主页、收藏夹、历史、会话恢复 | 已实现 |
| 内置 mihomo、订阅 / 档案、节点、模式、日志 | 已实现 |
| 免 VPN 的 WebView 局部代理 | 已实现 |
| 内核生命周期加固（重试、复活、干净退出） | 已实现 |
| 书签导入导出、图片操作、二维码、广告拦截 | 已实现 |
| 下载管理（进度 / 重试 / 打开 / 删除）、文件上传（SAF） | 已实现 |
| 页内查找、阅读模式、资源嗅探 | 已实现 |
| 搜索引擎选择、桌面视口、无痕隔离、订阅信息 | 已实现 |
| JVM 单元测试 + CI lint 门禁 | 已实现 |
| CI 密钥 release 签名（tag 构建） | 已实现 |
| 站点权限管理 | **未实现** |

## 已知限制

- **仅支持 arm64-v8a**，32 位设备与 x86 模拟器无法安装。
- **只代理应用内 WebView**，这是设计取舍；其它应用需自行配置代理。
- **订阅拉取是直连**，被墙的订阅地址无法在 App 内更新。
- **没有 VPN 服务**，不经过 WebView 的系统或应用流量不会被代理。
- **无痕隔离历史、会话快照、缓存与表单数据，退出时清除无痕期间访问站点的 Cookie**；普通标签 Cookie 不受影响，但仍非独立进程/配置沙箱。
- **未实现站点权限管理。**
- **广告拦截是内置小型域名表** + 用户标记，不是规则订阅引擎，也没有开关。
- **DRM 播放依赖系统 WebView 与 Widevine**；无可用的 CDM 时，EME 探测 4 秒超时后回退非加密流。
- **只有当 WebView 提供方支持 `ProxyController` 时代理才生效**，否则静默降级为直连。
- **代理运行期间，本机 mihomo API（`127.0.0.1:9090`，空 secret）与混合端口（`127.0.0.1:7890`）对同设备其它应用可达。**
- **内核与 Geo 数据在构建时从 GitHub 的 "latest" 下载且无校验固定**，构建不可复现。
- **两种签名渠道**：CI tag 构建产出 release 签名包；手动交付的 APK 为 debug 签名。两者签名不同、不能互相覆盖安装，请选定一个渠道。
- **运行模式偏好**在保存配置或手动重启内核时恢复；冷启动后使用 `config.yaml` 中记录的模式。

## Roadmap

以下为计划方向，不是承诺：

- 固定内核 / Geo 数据版本并在构建时校验
- 配置 release 签名与可复现的发布流程
- 补充截图与演示视频
- 视需求增加其它 ABI

## 隐私说明

- 仅声明 `INTERNET` 与 `ACCESS_NETWORK_STATE` 两个权限。
- 无统计、无崩溃上报、无遥测、无广告。
- 订阅、历史、书签、设置均保存在应用私有存储（`SharedPreferences` 与 `filesDir`），且关闭了 `allowBackup`。
- 应用内不包含任何订阅链接、节点、API Key 或服务器地址。
- 代理运行期间，本机回环端口对同设备其它应用可达（见「已知限制」）。

## 免责声明

ClashBrowser 是一个带内置代理内核的浏览器，本身不提供、不出售、不内置任何代理服务、订阅或节点。你需对自己使用的订阅与节点负责，遵守所在地区的法律法规，并尊重所访问网站的服务条款。本软件按“原样”提供，不附带任何担保。在某些国家和地区，使用本软件绕过网络限制可能违法，请自行评估风险。

## 许可证

ClashBrowser 是自由软件：你可以依据自由软件基金会发布的 **GNU 通用公共许可证第 3 版** 重新分发和/或修改它。详见 [LICENSE](LICENSE)。

内置 Geo 数据来自 [MetaCubeX/meta-rules-dat](https://github.com/MetaCubeX/meta-rules-dat)（GPL-3.0），内置内核为 [mihomo](https://github.com/MetaCubeX/mihomo)（MIT）。第三方声明（含 MaxMind GeoLite2 数据署名）见 [NOTICE](NOTICE)。

## 致谢

- [MetaCubeX/mihomo](https://github.com/MetaCubeX/mihomo) —— 代理内核
- [MetaCubeX/meta-rules-dat](https://github.com/MetaCubeX/meta-rules-dat) —— GeoIP / GeoSite 数据
- [MaxMind GeoLite2](https://www.maxmind.com) —— 地理位置数据
- Jetpack Compose、AndroidX、AndroidX WebKit —— UI 与 WebView 代理 API
- OkHttp、kotlinx.coroutines、SnakeYAML、ZXing —— 网络、并发、解析、二维码

## 参与贡献

欢迎提交 Issue 与 Pull Request，请先阅读 [CONTRIBUTING.md](CONTRIBUTING.md)。安全问题请按 [SECURITY.md](SECURITY.md) 私下报告，不要开公开 Issue。
