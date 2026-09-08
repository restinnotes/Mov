# NeriPlayer Classical 低功耗审计（upstream 原样行为）

- upstream commit: `ac7bdea460b84a25e9b6feb1c66b2592360202f1`
- Media3: exoplayer/datasource/hls/okhttp 均为 `1.10.1`（`gradle/libs.versions.toml:27-31`）；
  注意 `app/libs/` 内 FFmpeg 解码 aar 按 `media3-1.8.0` 构建，属版本错位但 baseline 可构建，Classical 不动它。
- 关键历史提交：`397335d`（audio offload policy + idle shutdown + 播放初始化优化）、
  `a6a2830`（idle shutdown 可配置化，默认 60min）。
- 本文档只记录 upstream 实际行为（file:line），不对“应该多省电”做推测。

## A. 播放时的周期性 timer / coroutine / Handler

1. 播放进度主循环 `core/player/playback/PlayerManagerPlaybackExtensions.kt:2080-2232`
   `startProgressUpdates()`：`while (isActive) + delay(updateIntervalMs)`（`:2098,2114,2170,2199,2229`）。
   间隔由 `core/player/policy/progress/PlaybackProgressUpdatePolicy.kt:31-44` 决定：
   - 启动未推进：80ms（`STARTUP=80L :3`，`:36-37`）
   - NowPlaying 可见或外部歌词节拍活跃：250ms（`INTERACTIVE=250L :4`，`:39-40`）
   - 后台/熄屏：1500ms（`BACKGROUND=1500L :5`，`:42-43`）
   - 统计子节拍：1000ms（`STATS=1000L :6`，`:2216-2228` 内判断 `now-last>=1000L` 才写统计）
   - 播放状态持久化节流 15s（`PlayerManager.kt:489`，`:2250,2265` 检查）。
   - 熄屏后台播放的常态就是 1500ms tick + 1000ms 统计子节拍，无更细粒度轮询。
2. Lyricon 独立 200ms feed（见 E）。
3. 悬浮歌词合流：`FloatingLyricsOverlayManager.kt:293-299` `postDelayed(16ms)` 内容合流；
   `:372-379` `postOnAnimation` 布局更新。仅在歌词内容变化时触发，无常驻 tick。
4. Runtime 看门狗 `watchdog/PlayerManagerRuntimeWatchdogExtensions.kt:62-147`：
   1500ms poll（`policy/progress/PlaybackRuntimeStallPolicy.kt:6 POLL=1500L`），
   由进度 tick（`:2127,2135`）及播放状态变化（`PlayerManagerLifecycleExtensions.kt:856-865,892-901`） armed，
   不是无条件常驻。
5. Startup 看门狗 `PlayerManagerStartupWatchdogExtensions.kt:80-126`：一次性 `delay`（非轮询）。
6. 睡眠定时器 `timer/SleepTimerManager.kt:104-128`：仅 COUNTDOWN 模式 1s tick。
7. USB 独占 keepalive `AudioPlayerService.kt:899-918`：前台 5s / 后台 1s（`:217-218,370-376`）。
   仅 USB 独占播放时启动，普通播放不运行。
8. Service 侧收集器（`AudioPlayerService.kt:1549-1566`）：position 变化按 2000ms 桶（`:211,1552`）推
   `updatePlaybackState()`；widget 进度按 15000ms 桶推局部刷新；MediaSession 节流 1000ms/漂移 1500ms
  （`service/MediaSessionPlaybackStateThrottler.kt:21-23`）。均为事件+节流，无固定高频 tick。
9. 结论：熄屏后台常驻的只有 1500ms 进度循环（含 1000ms 统计子判断）。200/250ms 只在
   “歌词开 / NowPlaying 可见 / 蓝牙歌词节拍”时出现——这正是 Classical 要砍掉的。

## B. 随播放常驻的组件

- `PlayerManager` 单例 + `mainScope/ioScope`、`ExoPlayer`、`SimpleCache`（默认 1GB）、
  `ConditionalHttpDataSourceFactory`（OkHttp）。
- `AudioPlayerService` 前台服务 + `MediaSession` + 播放通知 + widget updater。
- `AudioDeviceCallback`（主线程 Handler，`PlayerManagerLifecycleExtensions.kt:1613`，仅设备插拔回调）。
- `AnalyticsListener`（解码器/格式/underrun 日志，无轮询）。
- 看门狗 job（按需 armed）、播放统计 tracker（批量写，lifecycle flush）。
- `AudioReactive.teeSink` 常驻注入（见 D），但 disabled 时每 buffer 直接 return。

## C. 每 100/200/500ms 更新项

- 无 100ms 常驻循环。最接近的是 80ms（启动期进度）、200ms（Lyricon feed）、250ms（交互期进度）。
- 500ms：仅 `WaveformSlider` 振幅补间 `tween(500ms)`（`:113-119`，UI 动画，非后台任务）
  与音量 fade 步进（`fadeStepsFor: duration/40ms`，切歌瞬间，非周期）。
- 熄屏后台：1500ms（进度）+ 1000ms（统计子节拍判断）+ 按需看门狗 1500ms poll。

## D. waveform / FFT / Visualizer / music level

- 无 `android.media.audiofx.Visualizer` / FFT 类使用（全文搜索无命中）。
- `AudioReactive`（`core/player/effects/AudioReactive.kt`）：
  - `:45-62` `enabled` 开关，关闭即清零 level/beat；`:112-117` disabled 时 `handlePcmBuffer` 直接 return，
    不做 RMS/beat 数学。
  - 唯一的生产环境 setter：`ui/NeriApp.kt:3318-3320`
    `enabled = showNowPlaying && effectiveAudioReactiveEnabled && lifecycleResumed`，
    dispose 即 false。熄屏/退后台/关闭 NowPlaying 即关闭。
  - 但 `ReactiveRenderersFactory.kt:123-140 buildAudioSink()` 无条件注入
    `TeeAudioProcessor(AudioReactive.teeSink)`（`:131`），三个 AudioProcessor
   （volumeNormalization, balance, tee）常驻处理链。disabled 时 tee 只过手不计算，
    仍有一次虚调用开销——Classical 保留该结构（rebase 友好），靠 disabled 归零计算量。
    注意：只要任一 AudioProcessor 在链上，offload 即不可用（见 H，`audioReactiveActive` 只是
    上层开关，处理链本身是否影响 offload 取决于 Media3 是否因 processor 而回退 PCM——
    upstream 用 `AudioReactive.enabled` 作为代理条件，Classical 沿用此判定）。
- `HyperBackground`（`ui/view/HyperBackground.kt`）：
  - 45fps 稳态 / 60fps 切歌后 900ms boost（`:66-68`），`:253-339` 帧循环包在
    `repeatOnLifecycle(RESUMED)`（`:268`）内，熄屏即停，这是已确认的。
  - 另有按 `coverUrl` 的 Coil+Palette 取色（`:202-237`）与 520ms 调色过渡（`:65,363-412`），均为切歌时一次性。
  - 挂载点唯一：`ui/NeriApp.kt:4535-4544`（`effectiveDynamicBackgroundEnabled` 才 compose）。
- `WaveformSlider`（`ui/component/playback/WaveformSlider.kt`）：
  帧动画包在 `repeatOnLifecycle(RESUMED)`（`:155`）+ `isWaveAnimating` 门（`:124-126`）内，
  熄屏不跑；但 NowPlaying/歌词页/引导页挂载时前台仍跑，Classical 要静态化（见 CUSTOM_CLASSICAL.md 设计）。

## E. 歌词链路的 position 处理

- 进度 tick 内（`:2203-2213`）：
  - `if (lyriconEnabled) { setPlaybackSpeed; setPosition }`（`:2203-2211`）；
  - `updateExternalBluetoothLyricLine(positionMs)`（`:2213`，内部 `shouldProvide...` 为 false 即清行返回）。
- Lyricon 三入口（`core/lyricon/LyriconManager.kt`）：
  - `startFeedLoop()`（`:330-364`）：`while(isActive){ extrapolate anchor; setPosition; delay(200L) }`，
    间隔常量 `LyriconPositionFeed.kt:4 LYRICON_FEED_INTERVAL_MS=200L`，显示 lead 400ms（`:10`）。
  - `setPlaybackState(true)->startFeedLoop / false->stopFeedLoop`（`:111-123`）。
  - `updateSong()`（`:172-238`）触发 `getLyrics+getTranslatedLyrics`（IO）并 `setSong` 给 provider，
    另有 `updateSuperLyric` 逐行推送。
  - 调用方：切歌 `PlayerManager.kt:814-866 syncLyriconSong`（IO job 拉歌词），
    进度/seek/暂停/路由丢失 `PlayerManagerPlaybackExtensions.kt:293,1537,1673,1792,2326`，
    设置流 `PlayerManagerLifecycleExtensions.kt:849,1091,1097,1099`，
    应用启动 `NeriPlayerApplication.kt:156-158`（仅 `lyriconEnabled` 快照为 true 才 `initialize`）。
- 外部歌词（蓝牙/悬浮/状态栏/灵动岛）`lyrics/PlayerManagerExternalBluetoothLyrics.kt`：
  - `shouldProvideExternalLyricLine()`（`:204-210`）五开关任一即拉歌词并逐 tick 查行；
  - `isExternalBluetoothLyricCadenceActive()`（`:232-238`）为 true 时把进度间隔锁到 250ms。
- 歌词拉取（`metadata/PlayerLyricsProvider.kt:657-1043`） playoffs：
  `getLyrics` 依次碰本地/已存/已下载 → YouTubeMusicClient → LRCLIB → NeteaseClient → AMLL（`:931-1043`）；
  `getTranslatedLyrics` 另碰网易云翻译/YouTube 翻译（`:737-860`）。Bilibili 曲默认走空（`:1024`），
  除非 `matchedLyricSource==CLOUD_MUSIC` 才回退网易云（`:832-848,1026-1030`）。
  结论：Classical 必须在 `syncLyriconSong` / `syncExternalBluetoothLyrics` 源头短路，
  否则每首切歌至少 1-2 次网络 lyric 请求 + 200ms 常驻 feed。

## F. 第三方平台后台预热

- YouTube：`warmBootstrap()`（`api/youtube/YouTubeMusicPlaybackRepository.kt:1756-1795`，
  内部 `warmWebPoTokenSessionAsync` + `bootstrap` + `warmPlayerScriptAsync`，有去重与启动延迟
  `:1822-1855`），由 `AppContainer.kt:528-529,606-607` 挂到播放 intent 预热与
  `prefetch/PlayerManagerYouTubePrefetchExtensions.kt:94,149,186-191` 队列窗口预取触发；
  首要门 `YouTubeFeatureGate.isEnabled()`（`:1757`，默认 true，`data/platform/youtube/YouTubeFeatureGate.kt:7`）。
  `YouTubeAuthRotationWorker` 6h 周期保活（`:53-78`，`NeriPlayerApplication.kt:148-149` 调度），纯 HTTP 无 WebView。
  `installYouTubeBackgroundWebViewGuard`（`YouTubeBackgroundWebViewGuard.kt:40-70`）仅在创建 WebView 时装，
  非周期预热。
- 网易云/QQ：无后台周期任务；仅歌词/搜索/播放解析时按需请求（`PlayerLyricsProvider`、`QQMusicSearchApi` 经
  `EditableLyricsMatcher.kt:190-268` 外部匹配分支）。 lyric 源头短路后即安静。
- 设置快照预热（`BootstrapSettingsSnapshot.kt:69,84`）是本地 DataStore 预读，与平台无关，保留。

## G. 当前 audio offload 开启方式

- 唯一设置点 `lifecycle/PlayerManagerLifecycleExtensions.kt:1525-1559 updateAudioOffloadPreferences(reason)`：
  计算 `requiresPcmAudioProcessing(...)`（`:1527-1539`），变化才下发（`:1540`），
  PCM→`AUDIO_OFFLOAD_MODE_DISABLED`，否则→`AUDIO_OFFLOAD_MODE_ENABLED`（`:1543-1547`），
  经 `TrackSelectionParameters.AudioOffloadPreferences.Builder().setAudioOffloadMode(...)` 写入
  `player.trackSelectionParameters`（`:1548-1554`）。注意只设了 mode，未设 gapless/speed-required 等标志。
- 触发点（reason）：`player_initialize`（`:586`）、`playback_sound_config`（`PlayerManager.kt:1967`）、
  `playback_high_resolution_output`（`:1848`）、`audio_reactive_*`（`:567-584`）、
  `resolved_stream_source`（`PlayerManagerPlaybackExtensions.kt:1048`）、
  `refreshed_stream_source`（`url/PlayerManagerUrlExtensions.kt:1188`）、
  `playback_candidate_source`（看门狗 `:430`）、`usb_exclusive_policy`（`:3514`）。
- 状态观测：`addAudioOffloadListener`（`:587-601`）`onOffloadedPlayback` / `onSleepingForOffloadChanged`
  仅打日志（`NERI-PlayerManager`），无策略联动。Media3 1.10.1 无“当前正在 offload” getter，
  诊断只能展示“请求值 + 最近一次回调事件”，不可伪造（Classical 诊断按此实现）。

## H. 导致 offload 不可用的条件

`policy/offload/PlaybackAudioOffloadPolicy.kt:8-33 requiresPcmAudioProcessing` 任一即强制 PCM：

- `:22-23` **音频源为 NETEASE 或 BILIBILI 即强制 PCM**（注释称两家直链/换源易触发系统 offload 残留缓冲）。
  这是 Classical 的最大障碍：Bilibili 默认永远走 PCM，offload 从未被请求。
- `:24` USB 独占开；`:25-26` speed/pitch 偏离 1.0 超 0.001；`:27` EQ 开；`:28` loudnessGainMb≠0；
  `:29` volumeBalance≠0；`:30`响度归一化开；`:31` 高解析输出开；`:32` AudioReactive 活跃；
  `:33` ListenTogether 速率≠1。
- 设备侧：`AUDIO_OFFLOAD_MODE_ENABLED` 只是“请求”，解码器/ AbstractAudioSession 是否真 offload 由系统决定；
  失败自动回退 PCM（不断播），符合“prefer 而非 require”语义，Classical 沿用。

## I. AudioPlayerService 关闭条件

- 空闲关闭三件套：`service/PlaybackServiceIdleShutdownCoordinator.kt:8-49`（`refresh()->delay(delayMs)->复核 startId+eligibility->onShutdown`）、
  `service/PlaybackServiceIdlePolicy.kt:3-18`（eligible = 已初始化且有内容且非传输活跃/缓冲且无 LT/USB/睡眠定时）、
  `data/settings/PlaybackServiceIdleShutdownPreference.kt:3-15`（默认 60min，选项 0/15/30/60/120/240，0=关闭）。
- `stopIdlePlaybackService`（`AudioPlayerService.kt:844-860`）：持久化 + 统计 flush → `allowServiceRestart=false` →
  `keepPlayerRuntimeAfterServiceStop=true` → `stopForeground + stopSelf`。注意 idle 关闭**保留 player runtime**
  以便快速恢复，不是释放解码器。
- `onDestroy`（`:2810` 起）：cancel coordinator；`keepPlayerRuntimeAfterServiceStop` 为 true 则保留 runtime，
  否则按 `preservePlaybackForRestart` 挂起/释放。
- 外部 stop：`MEDIA_SESSION_STOP_SOURCE` 才真停服，其余外部 stop 按 pause 处理
 （`shouldStopServiceForExternalPauseCommand`，`:1292-1300`）。taskRemoved 按队列是否为空决定停播/停服（`:281-318`）。
- Classical 结论：idle 机制本身已合理（播时不杀、闲时 60min 收），保留不动；
  重点是别新增轮询、别在后台为 UI 而每秒碰 service。

## J. wakelock / WAKE_MODE

- 默认 `policy/wake/PlaybackWakeModePolicy.kt:7 DEFAULT=C.WAKE_MODE_NETWORK`；
  `https?` → NETWORK（`:16`），本地/offline-cache → LOCAL（`:15,17-21`），空 → NONE（`:12`）。
  `PlayerManager.kt:2494-2504` 按当前 URL 切换，初始 NETWORK。Bilibili https 播放即 NETWORK（WiFi 锁+CPU 锁由 Media3 持有），
  属播放必需，不动。
- 无自建 `PowerManager.WakeLock`（除 USB 独占 `usb/session/UsbExclusiveWakeLock` 与切歌瞬态
  `policy/wake/PlaybackTransitionWakeLock`——后者在 position 推进后释放 `:2122-2125`）。
  Classical 不新增、不强制清零，pause/stop 后随 idle shutdown 自然释放。
