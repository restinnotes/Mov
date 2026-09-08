# Neri Classical (Low Power Fork)

这是一个针对 Android 手机长时间熄屏播放 Bilibili 古典音乐定制的 NeriPlayer 个人低功耗构建版本。

---

## 1. 上游基线信息 (Upstream Baseline)

- **上游仓库**: `https://github.com/cwuom/NeriPlayer`
- **上游 Commit**: `ac7bdea460b84a25e9b6feb1c66b2592360202f1`
- **初始分支**: `master` -> 个人分支 `classical-low-power`
- **子模块**:
  - `np-submodule/NeriPlayer-LTW` (`31d55c6`)
  - `np-submodule/accompanist-lyrics-core` (`825661a`)
  - `np-submodule/accompanist-lyrics-ui` (`d844e10`)
  - `np-submodule/miuix` (`48bd198`)

---

## 2. 定制原则与架构设计 (Design Philosophy)

1. **集中式 Gate 管理 (`ClassicalLowPower`)**:
   - 不在业务代码中散落魔法常量。所有 Classical 特性由 `moe.ouom.neriplayer.core.classical.ClassicalLowPower` 统一门禁。
   - 通过 Product Flavor `classical` 的 `BuildConfig.CLASSICAL_LOW_POWER = true` 在编译期注入，同时支持运行时与单元测试 override。
2. **独立共存 (Dual-Installation)**:
   - Flavor: `classical`
   - ApplicationId: `moe.ouom.neriplayer.classical`（带 `.classical` 后缀，不影响原版 NeriPlayer 同时安装）
   - 应用显示名称: `Neri Classical`
   - FileProvider 与 Startup Provider 权限由清单动态注入 `${applicationId}.fileprovider` 与 `${applicationId}.androidx-startup`，完全合法隔离。
3. **零开销诊断 (`LowPowerDiagnostics`)**:
   - 彻底杜绝任何定时器与周期性轮询。
   - 仅挂接在 Media3 的 `AnalyticsListener`、`AudioOffloadListener` 及底层音频流配置变更事件上。
   - 调试页面新增卡片实时展示源、格式、编解码器、硬件 DSP Offload 状态、睡眠状态与省电门禁。
   - 仅在状态实质变化时向 Logcat 输出单行，Tag 为 `NeriPower`。

---

## 3. 哪些功能被 Runtime/Compile-Time Gate

| 子系统 / 功能 | 原版行为 | Classical 行为 | 状态 |
| :--- | :--- | :--- | :--- |
| **Bilibili Audio Offload** | 原版在 `PlaybackAudioOffloadPolicy.kt` 强制 `BILIBILI` 走 PCM 管线 | Classical 模式下移除 Bilibili 强制 PCM 规则，单倍速无音效下优先进入硬件 DSP Offload | **已开启 Offload** |
| **Lyricon 200ms Feed** | 原版在 `LyriconManager.kt` 启动 200ms 轮询协程推送进度 | Classical 模式下 `startFeedLoop`、`setPosition`、`updateSong` 彻底短路，0 CPU 唤醒 | **完全不运行** |
| **歌词请求与匹配** | 切歌与前台时向网易云/QQ/LRCLIB 联网检索与解析逐字歌词 | `PlayerLyricsProvider` 与 `PlayerManagerPersistenceExtensions` 顶层直接返回 `emptyList()`，零网络零解析 | **完全不运行** |
| **悬浮 / 状态栏 / 蓝牙歌词** | 原版后台监听播放进度并高频刷新歌词覆盖层与系统广播 | `FloatingLyricsOverlayManager` 与 `PlayerManagerExternalBluetoothLyrics` 彻底屏蔽，前台通知与 MediaSession 移除歌词 Action | **完全不运行** |
| **动态背景 / Hyper Shader** | 原版使用 RuntimeShader 渲染 45/60fps 流体动态背景 | `HyperBackground` 与 `effectiveDynamicBackgroundEnabled` 门控关闭，不 compose 对应视图 | **完全不运行** |
| **音频分流 (AudioReactive)** | `AudioReactive.teeSink` 对 PCM 进行 RMS、EMA 均值与鼓点计算 | `AudioReactive.enabled` 永远保持 `false`，进入分流的 buffer 立即 return，零浮点数学运算 | **零计算量** |
| **Waveform 进度条动画** | 原版在播放时以 60fps 驱动 `withFrameNanos` 计算波浪振幅 | `WaveformSlider` 动画门禁关闭，跳过 `repeatOnLifecycle` 帧循环，渲染为轻量静态高对比度进度条 | **零帧循环** |
| **YouTube Music 预热** | 原版在 App 启动和后台任务中每 6 小时调度 worker 预热与 rotation | `YouTubeAuthRotationWorker` 调度与 `YouTubeMusicUiGatewayInitializer` 在启动时跳过，网关保持静默 | **已安静化** |
| **流式缓存默认上限** | 原版默认 1 GB | Classical 默认流式缓存提升至 2 GB (`DEFAULT_CLASSICAL_CACHE_BYTES`)，避免长乐章重复下载 | **2 GB 默认** |
| **GitHub / WebDAV 同步** | 原版双端三路合并逻辑 | **100% 原样保留**，协议与 JSON / ProtoBuf 结构未做任何修改，保持与 Desktop 兼容 | **完整保留** |
| **Bilibili 核心播放能力** | QR 登录、Web 登录、合集、分 P、搜索、收藏夹、队列、循环 | **100% 原样保留**，认证、WBI 签名、换源重试均正常运行 | **完整保留** |

---

## 4. 修改文件清单 (Modified Files)

- `app/build.gradle.kts`: 添加 `official` 与 `classical` flavors、applicationIdSuffix、release 本地签名自动装载。
- `app/src/classical/res/values/strings.xml` (及 `values-zh`, `values-en`): 定义应用名 `Neri Classical`。
- `app/src/main/java/moe/ouom/neriplayer/core/classical/ClassicalLowPower.kt`: 集中式特性开关与测试覆盖入口。
- `app/src/main/java/moe/ouom/neriplayer/core/classical/LowPowerDiagnostics.kt`: 零开销状态诊断器与 `NeriPower` 日志记录器。
- `app/src/main/java/moe/ouom/neriplayer/NeriPlayerApplication.kt`: 跳过 Classical 下的 Lyricon、悬浮歌词与 YouTube 预热。
- `app/src/main/java/moe/ouom/neriplayer/core/di/AppContainer.kt`: Classical 下 YouTubeFeatureGate 默认禁用且不预热。
- `app/src/main/java/moe/ouom/neriplayer/core/lyricon/LyriconManager.kt`: 拦截所有歌词推送与 200ms feed 启动。
- `app/src/main/java/moe/ouom/neriplayer/core/player/PlayerManager.kt`: 拦截 Lyricon 同步与歌词偏移计算。
- `app/src/main/java/moe/ouom/neriplayer/core/player/lifecycle/PlayerManagerLifecycleExtensions.kt`: 拦截歌词设置流，连接 `LowPowerDiagnostics` 解码与 Offload 事件。
- `app/src/main/java/moe/ouom/neriplayer/core/player/lyrics/FloatingLyricsOverlayManager.kt`: 拦截悬浮窗初始化与显示判断。
- `app/src/main/java/moe/ouom/neriplayer/core/player/lyrics/PlayerManagerExternalBluetoothLyrics.kt`: 拦截外部蓝牙歌词加载与高频更新。
- `app/src/main/java/moe/ouom/neriplayer/core/player/metadata/PlayerLyricsProvider.kt`: 拦截歌词获取入口，直接返回空。
- `app/src/main/java/moe/ouom/neriplayer/core/player/persistence/PlayerManagerPersistenceExtensions.kt`: 拦截播放持久化中的歌词加载。
- `app/src/main/java/moe/ouom/neriplayer/core/player/playback/PlayerManagerPlaybackExtensions.kt`: 进度更新循环中剔除 Lyricon 与外部蓝牙歌词更新。
- `app/src/main/java/moe/ouom/neriplayer/core/player/policy/offload/PlaybackAudioOffloadPolicy.kt`: 针对 Classical 开放 Bilibili 音频的 Offload 许可。
- `app/src/main/java/moe/ouom/neriplayer/ui/NeriApp.kt`: 关闭动态背景与 AudioReactive 开关。
- `app/src/main/java/moe/ouom/neriplayer/ui/component/playback/WaveformSlider.kt`: 关闭帧循环与波形计算，转为静态平滑进度条。
- `app/src/main/java/moe/ouom/neriplayer/ui/screen/NowPlayingScreen.kt`: 隐藏歌词按钮、关闭横向左滑进入歌词页与歌词全屏页面。
- `app/src/main/java/moe/ouom/neriplayer/ui/screen/debug/LowPowerDiagnosticsCard.kt`: 调试页低功耗与 Offload 诊断展示卡片。
- `app/src/main/java/moe/ouom/neriplayer/ui/screen/debug/DebugHomeScreen.kt`: 在 Debug 主页挂载诊断卡片。
- `app/src/main/java/moe/ouom/neriplayer/ui/view/HyperBackground.kt`: 防御性拦截 HyperBackground 组合。
- `tools/power-test.ps1`: A/B 功耗对比测试自动化抓取脚本。

---

## 5. 如何重新构建 (How to Rebuild)

### 依赖环境
- JDK: 17 (`D:\Android\jdk17\current` 或已配置 `JAVA_HOME`)
- Android SDK: `D:\Android\Sdk`（包含 Platform 37、Build-Tools 37.0.0/36.0.0、NDK 27.0.12077973、CMake 3.22.1）

### 构建 Classical 签名 Release APK (默认仅构建 arm64-v8a)
```powershell
.\gradlew.bat :app:assembleClassicalRelease
```
产物位置：`app/build/outputs/apk/classical/release/NeriClassical-<version>-classical.apk`

### 构建 Classical Debug APK
```powershell
.\gradlew.bat :app:assembleClassicalDebug
```

### 构建官方 Baseline 原版对比
```powershell
.\gradlew.bat :app:assembleOfficialRelease
```

---

## 6. 如何从 Upstream 更新 (How to Update from Upstream)

1. 保留 upstream 远端：
   ```powershell
   git fetch origin master
   ```
2. 在 `classical-low-power` 分支上 rebase 或 merge：
   ```powershell
   git checkout classical-low-power
   git rebase origin/master
   ```
3. 冲突处理提示：
   - 本 fork 的核心改动均围绕 `ClassicalLowPower.enabled` 做了防护性包装；
   - 如 upstream 修改了 `PlayerManagerLifecycleExtensions.kt` 或 `PlaybackAudioOffloadPolicy.kt`，只需核实 `preferClassicalOffload` 是否继续生效；
   - 重新执行编译验证：
     ```powershell
     .\gradlew.bat :app:testClassicalDebugUnitTest :app:assembleClassicalRelease
     ```

---

## 7. 已知问题与说明 (Known Issues & Notes)

1. **Audio Offload 依赖硬件与音频源编码**:
   - Audio Offload 是向 Android 系统 HAL / DSP 发起请求（`AUDIO_OFFLOAD_MODE_ENABLED`）；
   - Bilibili 默认 192k/320k AAC 在绝大多数现代高通/天玑平台（包括 Redmi Turbo 4 Pro）上支持硬件 Offload；
   - 如果用户在设置中开启了均衡器、调整了播放倍速（非 1.0x）、开启了响度增强或高解析浮点输出，Android 系统规范要求经过 CPU PCM 变换，此时会自动安全回退至标准系统 AudioTrack，不会中断播放。
2. **桌面小组件与状态栏歌词**:
   - 桌面小组件的歌词切换按钮在 Classical 构建中将处于不可用/隐藏状态，这与“彻底切断歌词”的目标一致。
