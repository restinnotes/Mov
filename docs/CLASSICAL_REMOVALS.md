# Classical Removals (gates re-applied on classical-clean)

Source: `git diff ac7bdea4..classical-low-power` (7 commits, 30 files).
Principle: **gate, don't delete upstream**. Official flavor behaves == upstream.

## 1. Lyrics stack — fully OFF in classical

| item | gate | notes |
|---|---|---|
| Lyrics page | `ClassicalLowPower.lyricsDisabled` → `NowPlayingScreen` AnimatedContent stays on player | no lyrics request/parse/tick |
| Lyrics matching/parsing | `PlayerLyricsProvider` early-return | background chain never starts |
| Lyricon 200ms feed | `LyriconManager.initialize` skipped in `NeriPlayerApplication` | `lyriconDisabled` |
| Floating lyrics | `FloatingLyricsOverlayManager.initialize` skipped | `externalLyricsDisabled` |
| Status-bar lyrics | same gate (notification path) | no content served |
| Bluetooth lyrics | `PlayerManagerExternalBluetoothLyrics` early-return | no periodic position work |
| SuperLyric background | covered by lyrics+lyricon gates | no feed |

Verify: hide UI **and** no network/parse/feed/tick (position flow never enters lyrics tree).

## 2. Dynamic visuals — OFF

- `HyperBackground` / dynamic background: not composed when `dynamicBackgroundDisabled`.
- AudioReactive (PCM level/beat tap): not enabled when `audioReactiveDisabled` → screen-off zero compute.
- Waveform 60fps `withFrameNanos` loop: replaced by static/light progress in classical
  (`WaveformSlider` low-power branch).

## 3. Heavy glass — cheapest path by default

- `AdvancedGlass` / overscroll / RenderEffect / backdrop capture: classical prefers lowest-cost
  visual path. If upstream later optimizes glass, re-audit cost before re-enabling.
- `enhancedAdvancedBlurEnabled` 强制 OFF（保留基础 blur 开关与 Material 质感）。

## 4. YouTube prewarm — OFF unless user opts in

- `YouTubeMusicUiGatewayInitializer.initialize()` skipped.
- `YouTubeAuthRotationWorker` periodic rotation not scheduled.
- `YouTubeFeatureGate` forced `false`; bootstrap cache cleared on transition.
- `warmYouTubePlaybackOnAppStart()` early-returns.
- User explicitly enabling YouTube in settings still works (gate is "no *background* prewarm",
  not "no YouTube ever") — search setting respects explicit choice.

## 5. Bilibili audio offload — ON (under strict conditions)

- Upstream forces BILIBILI → PCM (`requiresPcmAudioProcessing`).
- Classical keeps the PCM default, but when `preferClassicalOffload` (classical flavor) **and**
  1.0x speed, no EQ/loudness/PCM effects, source == BILIBILI (NETEASE still PCM), allows
  hardware offload/DSP path. Backed by `PlaybackAudioOffloadPolicyTest`.

## 6. Cache — 2GB default for fresh installs

- `ClassicalLowPower.DEFAULT_CLASSICAL_CACHE_BYTES = 2GiB`.
- Wired as the *default* in `PlaybackPreferenceSnapshot`, `SettingsRepository`, `PlayerManager.initialize`
  via `ClassicalLowPower.effectiveDefaultCacheBytes()`. Existing users' stored values untouched.
- Uses upstream cache/storage framework — no second cache subsystem.

## 7. Sync — upstream only

- GitHub/WebDAV sync follows upstream schema. No classical sync tables/fields.

## 8. 古典曲目完整性（非 AI）

- Bilibili SponsorBlock 自动跳段：classical 强制 OFF
  （掌声/调音/演前介绍/乐章间停顿不得自动跳过；手动 seek 不受影响）。
- 首页推荐卡（Trending/雷达/每日推荐）：新安装默认关闭，用户可在设置-个性化中再开。
- 设置首页：仅隐藏歌词页（死入口）；Motion/流量管理等开关全部保留。
- 更多选项 sheet：隐藏歌词行为/字号入口，保留专辑入口。

## 8. Flavor

- `official` flavor: `CLASSICAL_LOW_POWER=false`, behavior == upstream.
- `classical` flavor: `applicationIdSuffix=.classical`, `CLASSICAL_LOW_POWER=true`.
