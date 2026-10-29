# Mov. — Movement

针对 Android 手机长时间播放 Bilibili 古典音乐定制的 NeriPlayer 轻量低功耗 fork。

> 方向：**相对于最新 upstream 最少改动**。队列、歌单、分 P、Library、播放模式、同步全部跟 upstream；
> Classical 只做低功耗减法 + B 站默认搜索/薄聚合 + 播放页三页布局（作品/播放/队列）。
> AI Metadata / Assistant / Classical Library / Timeline / Workbench 已从本分支删除，
> 留在 `archive/classical-ai-experiment`（tag `classical-ai-experiment-3fedb88d`）。

---

## 1. Fork 基线 / 上游同步点

- **项目仓库（origin）**: `https://github.com/restinnotes/Mov`。
- **上游仓库（upstream）**: `https://github.com/cwuom/NeriPlayer`。
- **Fork base / 当前 upstream HEAD**: `ac7bdea460b84a25e9b6feb1c66b2592360202f1`
  （2026-09-02，`git fetch --all --tags @ 2026-09-21` 确认 master 无更新）
- **本分支**: `classical-clean`（自 `upstream/master` 新建，按需 cherry-pick 最小补丁）
- **子模块**: 跟 upstream（NeriPlayer-LTW `31d55c6`，lyrics-core `825661a`，lyrics-ui `d844e10`，miuix `48bd198`）
- 审计细节见 `docs/CLASSICAL_REBASE_AUDIT.md`；减法清单见 `docs/CLASSICAL_REMOVALS.md`。

---

## 2. 定制原则

1. **集中式 Gate（`ClassicalLowPower`）**：所有 Classical 行为分支经过
   `core/classical/ClassicalLowPower`，不散落魔法常量；`classical` flavor 编译期注入
   `BuildConfig.CLASSICAL_LOW_POWER = true`，测试可 override。
2. **UPSTREAM FIRST**：upstream 已有的队列/歌单/分 P/Library/播放模式/同步/搜索分页直接用，
   不写第二套。
3. **双安装**：`official`（行为 == upstream）与 `classical`
  （`applicationIdSuffix = .classical`，显示名 `Mov.`）共存。
4. **零开销诊断（`LowPowerDiagnostics`）**：无定时器轮询，只挂 Media3 事件；`NeriPower` 单行日志。

---

## 3. 减法（低功耗门禁，official 不受影响）

| 子系统 | Classical 行为 |
| :--- | :--- |
| 歌词整套（页面/请求/匹配/解析） | OFF：不断网、不解析、不 tick |
| Lyricon 200ms feed | OFF，0 CPU 唤醒 |
| 悬浮/状态栏/蓝牙/灵动岛歌词 | OFF，不提供内容 |
| 动态背景 / Hyper shader | OFF，不 compose |
| AudioReactive（level/beat） | OFF，熄屏零计算 |
| Waveform 60fps 帧循环 | 静态/轻量进度条 |
| AdvancedGlass 重效果 | 最低成本视觉路径 |
| YouTube 后台预热/rotation/bootstrap | OFF（用户显式启用 YouTube 仍可用） |
| Bilibili Audio Offload | 1.0x、无 EQ/响度/PCM 音效时允许硬件 offload（NETEASE 仍强制 PCM） |
| 流式缓存默认 | 新安装 2GB（`effectiveDefaultCacheBytes()`；已有用户设置不动） |
| Classical 音频缓冲 | 向前缓冲 30–90 秒；启播仍为 1 秒、卡顿后恢复仍为 3 秒；回退缓冲 60 秒 |
| 播放与预加载缓存争用 | 正在播放遇到被预加载锁住的缓存区间时直接拉流，不等待该写入锁 |
| GitHub / WebDAV 同步 | 100% 跟 upstream，无 classical schema |

---

## 4. 搜索（基于 upstream 的薄增强）

- Classical 干净安装默认 **Bilibili**；默认来源可在设置行切换：Bilibili / 聚合 / YouTube
 （`data/search/ClassicalSearchPreference.kt`，`normalizeDefaultSearchMode` 保证 stale 偏好回退）。
- **薄聚合**：Bili + YouTube 并发，各自独立排序/结果/错误/分页，分区展示，不做跨平台
  AI rerank（`ui/viewmodel/tab/ExploreAggregateSearch.kt`，
  `ui/screen/tab/ClassicalAggregateSearchResults.kt`，`ClassicalSearchModeRow.kt`）。
- **Bili 排序补丁**（`util/search/RemotePlatformSearchRanker.kt`）：仅当 `hit_columns` 标明命中时
  才把 description/tag 纳入匹配；server 顺序做 tie-break；音乐区轻微 boost、绝不过滤。
- Classical 源顺序 Bili 优先，不预拉网易推荐；pager 越界 clamp（`ui/util/PagerTabSafety.kt`）。
- UP 主详情的投稿页可按关键词请求 B 站搜索并继续分页；输入防抖 350ms，清空恢复全部投稿，
  切换关键词或 UP 主时取消旧请求并拒绝晚到结果；搜索失败支持重试。

---

## 5. Source 与播放体验（BBPlayer 风格，用 Compose 重写，无共享代码）

- Classical 竖屏中央播放页提供睡眠定时器入口，开启时高亮；支持倒计时、播完当前曲目、
  播完队列，以及倒计时到期后播完当前曲目。加载/中途缓冲使用现有播放控件的旋转等待动画，
  等待超过 1 秒显示，恢复或暂停后移除。
- Bilibili 常规 `bilivideo` 与海外 Akamai CDN 同级，保留接口返回顺序；P2P/mcdn 放到末尾，
  不修改带签名的 CDN 域名或参数。接口顺序不能保证每个网络里都最快，仍保留同音轨备用线路恢复。
- 中途持续无进度时，运行期看门狗先停止旧读取并在原位置重新 prepare，之后才切备用线路或刷新 URL；
  短暂网络故障保留已缓存字节。实际播放推进后由运行期看门狗负责，避免与启动看门狗同时恢复。
- 播放页（classical 竖屏）：每次打开回到中间播放页，左右滑动可切换；
  中间保留圆角封面、标题/作者、收藏、进度和主播放控制，主控两侧为随机和循环，
  底部保留分享、定时和队列（长按弹出全部分P）。左页把作品简介与评论串成单一列表；
  右页承载队列，点播后留在该页。移除跨页页眉、页码点和常驻说明；B站曲目详情使用来源专属字段。
  右上角工具菜单保留音质、音效、下载等操作。横屏/宽屏与 official 保持上游布局。
- 收藏短按加入/移出默认“我喜欢的音乐”，长按选择其他本地歌单或新建歌单；
  复用歌单仓库的去重、同步提醒和操作结果反馈。
- 评论：分隔线、相对时间、行内表情、图片九宫格（点看大图）、前排回复预览气泡、
  完整楼中楼按需加载、精选/最新切换、时间戳 seek；点赞数仅展示。
- 队列：点播 B 站视频即整视频进队列（先播当前分P，后台补齐其余分P，切歌丢弃晚到结果）；
  队列 sheet 里同一视频连续分P成组显示，大标题默认展开、点标题收缩。
  视频标题存于 album 第 4 段（stableKey 不含 album 原文，新旧格式身份一致）。

- 右侧队列页内嵌现有队列组件，保留分组、多选、排序和导出；普通队列弹层入口仍可复用。
- 左页作品简介与评论共用 LazyColumn，无 tab、重复页眉或嵌套 HorizontalPager。
- 简介：封面、原标题、UP 主、发布日期、视频简介、来源链接。**无分 P 列表**
  （分 P 已是队列独立项）。
- 评论：LazyColumn，`stable key = rpid`，连续滚动自动翻页；
  状态机 INITIAL_LOADING / READY / LOADING_MORE / END / ERROR；
  分页失败保留已加载页 + 底部重试；精选/最新各自独立翻页。
- 时间戳（`12:34` / `1:02:15`）识别为可点击 seek；**不建 Timeline 系统**。
- 楼中楼按需加载（每 root 独立 Job），replies 不预加载。
- 预加载：BVID/CID 已知即后台拉简介 + 第一页评论（点气泡前 1~2s 就绪）；只预加载这两样。
- 切歌立即取消旧 jobs，晚到结果不覆盖；BVID 缓存为有界 LRU（最近 5 个，仅简介+首页）；
  评论 items 上限 200；离屏/切歌释放后续页；后台不翻页不轮询。
- Source 树不订阅 playback position；只有时间戳点击调 `PlayerManager.seekTo(ms)`。
- 文件：`core/api/bili/BiliClient.kt`（评论 API 增量）、
  `core/classical/source/BiliSourceRepository.kt`、
  `ui/viewmodel/source/ClassicalSourceViewModel.kt`、
  `ui/screen/source/ClassicalSourcePage.kt` + `SourceTimestampParser.kt`。

---

## 6. 数据库

- 上游 schema v15；本分支 **v19**，新增迁移 `15_19 / 16_19 / 17_19 / 18_19`，
  `DROP TABLE IF EXISTS` 10 张 AI 实验表
 （9 张 classical_* + playback_marker），保留歌单/收藏/历史/设置/下载。
- 新安装直接建 v19；旧 AI 构建（v18）覆盖安装可正常升级；禁止静默全库清空。

---

## 7. 构建

依赖：JDK 17，Android SDK（Platform 37、Build-Tools 37.0.0/36.0.0、NDK 27、CMake 3.22.1）。

```powershell
.\gradlew.bat :app:testClassicalDebugUnitTest
.\gradlew.bat :app:assembleClassicalDebug
.\gradlew.bat :app:assembleClassicalRelease   # 默认仅 arm64-v8a
.\gradlew.bat :app:assembleOfficialRelease    # 官方 baseline 对比
```

产物：`app/build/outputs/apk/classical/release/Mov-<version>-classical.apk`

---

## 8. 上游更新流程

```powershell
git fetch upstream
git rebase upstream/master   # 冲突应只集中在少数 Classical 文件；若大面积冲突说明 delta 又变大了
```

---

## 9. 已知限制

1. Audio Offload 需硬件/DSP 支持；开 EQ、变速、响度增强、高解析浮点输出时自动回退 AudioTrack。
2. Source 页 v1：宽屏/横屏布局下右滑手势可用，但评论预加载与分页逻辑与竖屏一致；
   非 Bilibili 歌曲显示空态（无简介/评论）。
3. 评论 v1 只有默认排序；聚合搜索 YouTube 侧无分页（跟上游单页能力一致）。
4. 桌面小组件/状态栏歌词在 Classical 下不可用（符合彻底切断歌词的目标）。
