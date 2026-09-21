# Classical Rebase Audit (authoritative: git fetch 2026-09-21)

> Fetch-time truth. Do not trust prompt dates/commits; `git fetch --all --tags` output below is canonical.

## 1. Remotes

- `origin = https://github.com/cwuom/NeriPlayer.git` (fetch+push) — this IS upstream.
- `upstream = https://github.com/cwuom/NeriPlayer.git` (added 2026-09-21 as alias for future `git fetch upstream` workflow).
- No separate fork remote. Local work diverges directly from `origin/master`.

## 2. Heads (fetch 2026-09-21)

| ref | sha | date | subject |
|---|---|---|---|
| `upstream/master` = `origin/master` = local `master` | `ac7bdea460b84a25e9b6feb1c66b2592360202f1` | 2026-09-02 17:13:23 +0800 | fix: preserve null values in cached playback descriptors (#408) |
| fork base (verified `merge-base --is-ancestor`) | `ac7bdea460b84a25e9b6feb1c66b2592360202f1` | same as above | matches documented baseline |
| `classical-low-power` tip | `0f50f581fc560ccd59cd4c7bbcb6536b1edb5f2c` | 2026-09-08 19:48:46 -0400 | docs: add personal fork customization guide and A/B power test tool |
| pre-rebuild HEAD (`feature/playback-markers`) | `3fedb88db7bae017db437e1a07ab1f4f05d8dca4` | 2026-09-20 12:32:56 -0400 | fix(zen): align client protocol for anonymous access |
| `classical-clean` base | `ac7bdea460b84a25e9b6feb1c66b2592360202f1` | 2026-09-02 | fresh from `upstream/master` |

- `git merge-base origin/master classical-low-power` = `ac7bdea4` (fork point confirmed).
- `git merge-base origin/master feature/playback-markers` = `ac7bdea4`.
- `classical-low-power` is ancestor of `3fedb88d` (7 low-power commits + 26 AI-experiment commits = 33 total).

## 3. Three-line comparison

### A. fork base → latest upstream

```
0 commits, 0 files. upstream/master == fork base (ac7bdea4).
git fetch 2026-09-21 introduced no new master commits.
Submodules follow upstream pins (NeriPlayer-LTW 31d55c60, lyrics-core 825661a1, lyrics-ui d844e105, miuix 48bd198a).
```

So "sync to latest upstream" is currently a no-op — the rebuild value is entirely delta reduction.

### B. fork base → current Classical (pre-rebuild HEAD 3fedb88d)

```
33 commits, 213 files changed, +36362 / -397.
New files (diff-filter=A): 165.
Includes ~12k lines Room schema JSON exports (16.json/17.json/18.json).
```

### C. latest upstream vs current Classical

Identical to B (because A is empty).

### Early-classical subset (fork base → classical-low-power)

```
7 commits, 30 files changed, +1222 / -143.
New files: 11. This is the KEEP baseline re-applied to classical-clean via cherry-pick.
```

## 4. Archive

- Branch `archive/classical-ai-experiment` → `3fedb88d` (pre-rebuild HEAD).
- Tag `classical-ai-experiment-3fedb88d` → same.
- Worktree dirt stashed: `classical-ai-experiment-worktree-20260921` (tracked mods)
  + `classical-ai-experiment-untracked-20260921` (untracked AI/agent/provider files).
- `classical-low-power` branch left untouched (early 7-commit baseline).

## 5. File classification (base 3fedb88d vs ac7bdea4)

KEEP (re-applied to classical-clean):
- `core/classical/ClassicalLowPower.kt`, `core/classical/LowPowerDiagnostics.kt`
- flavor `official`/`classical` in `app/build.gradle.kts` (+ `src/classical/res/values*/strings.xml`)
- lyrics gates (`LyriconManager`, `PlayerLyricsProvider`, `FloatingLyricsOverlayManager`,
  `PlayerManagerExternalBluetoothLyrics`, `PlayerManagerLifecycleExtensions`)
- `HyperBackground`, `WaveformSlider`, `NowPlayingScreen` (minimal low-power branches)
- `PlaybackAudioOffloadPolicy` (Bili offload), `NeriPlayerApplication`/`AppContainer` (YT prewarm gate)
- `ClassicalLowPowerTest`, `PlaybackAudioOffloadPolicyTest`, `LowPowerDiagnosticsCard`
- `build-baseline.txt`, `docs/classical-power-audit.md`, `tools/power-test.ps1`, `CUSTOM_CLASSICAL.md` (rewritten)

DROP (stays only on archive branch, never on classical-clean):
- AI metadata ingest: `core/classical/ingest/*` (ChunkMerger, GroupPlanner, IngestionHook, Worker,
  ParseQueue, Zen transport, MetadataAiBackend, prompt/validator/batch builder)
- AI providers: `core/classical/ai/*` incl. Kilo/LLM7/OVH/OpenCode/user-OAI-compatible (untracked at stash time)
- Assistant: `core/classical/assistant/*` + `assistant/agent/*`, translator/tools/harness
- Research: `core/classical/research/*`
- Metadata domain: `core/classical/metadata/*` (V1, SourceIdentity, TextNormalization kept only if needed by search — no)
- Library: `core/classical/library/*` + `ui/screen/classical/ClassicalLibraryScreen.kt`,
  `ClassicalUnifiedLibraryScreen.kt`, `ClassicalLibraryNavigation.kt`
- Stats/tags: `core/classical/stats/*`
- Playback doubles: `core/classical/playback/ClassicalPlaybackMode.kt`,
  `ClassicalPlaybackPolicy.kt`, `ClassicalNowPlayingProjection.kt`
- Timeline/markers: `core/classical/source/ClassicalTimeline.kt`,
  `data/marker/*`, `data/local/database/entity/PlaybackMarkerEntity.kt`, dao/store,
  `BilibiliMarkerImporter`, `ui/screen/PlaybackMarkersSheet.kt`, `docs/MARKER_SYNC_SPEC.md`,
  `docs/HANDOFF_DESKTOP_PLAYBACK_MARKERS.md`
- 5-page pager: `ui/screen/classical/ClassicalNowPlaying*` (Host/Pager/Header/PlaybackModeRow),
  `ClassicalSourceDetailHost/Sheet`, `ClassicalAssistantScreen`
- Custom queue/playlist pages, smart collections, prune/override/effective-index repos
- `workbench/*` module, `fixtures/classical/*`, Room `ClassicalMetadataDao/Entities` (9 tables)

UPSTREAM_REPLACED (use upstream, delete ours):
- queue/playlist/parts/library/playback-mode/search-paging/sync/crash/glass — upstream owns all.

ARCHIVE_ONLY: Zen protocol, AI fixtures, assistant scenarios/goldens, metadata schemas + 16/17/18.json exports.

## 6. Room versions

- Upstream (`upstream/master`): `NeriUserDataDatabase version = 15`, 34 entities, migrations 1_2..14_15.
- Pre-rebuild HEAD: `version = 18` (+9 classical tables + `playback_marker`).
- `classical-clean`: starts at 15, bumped to **19** with a destructive-but-scoped
  `MIGRATION_15_19 + 16_19/17_19/18_19` path that `DROP TABLE IF EXISTS` the 10 experiment
  tables and preserves playlists/favorites/history/settings/downloads. Fresh installs create v19
  directly. Downgrade 18→15 is never attempted.

## 7. Rebuild deltas (measured 2026-09-21, HEAD cb1ef3c7)

- Before (3fedb88d vs ac7bdea4): 213 files, +36362/-397, 165 new files, Room v18.
- After (classical-clean vs upstream/master): 58 files, +7877/-277, 31 new files, Room v19.
  - Of the +7877, 3505 are generated `19.json` Room schema export (upstream tracks
    schemas the same way); docs/tools add ~590. **Hand-written main-source delta:
    36 files, +2844/-258.**
- 14 commits on top of upstream/master (7 low-power cherry-picks + 7 clean rebuild commits).
