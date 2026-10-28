package moe.ouom.neriplayer.core.classical.queue

import moe.ouom.neriplayer.core.api.bili.BiliClient
import moe.ouom.neriplayer.core.api.bili.biliBvidOrNull
import moe.ouom.neriplayer.core.api.bili.biliCidOrNull
import moe.ouom.neriplayer.core.api.bili.biliVideoTitleOrNull
import moe.ouom.neriplayer.core.api.bili.buildBiliPartSong
import moe.ouom.neriplayer.core.player.PlayerManager
import moe.ouom.neriplayer.core.player.persistence.mergeBiliPartsIntoQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.ouom.neriplayer.data.model.SongItem
import moe.ouom.neriplayer.data.model.stableKey

/**
 * Bilibili 整视频进队列 + 队列分组合显示。
 *
 * 背景：点播只会把当前分P（常为 P1）放入队列，同一视频的其余分P丢失；
 * 队列里同一视频的多个分P应该成组显示（合集大标题默认展开，点标题收缩）。
 *
 * - 视频标题存在 album 第 4 段（见 buildBiliSongAlbum），随队列持久化；
 *   stableKey 不含 album 原文，新旧格式身份一致。
 * - 扩展永不阻塞点播：先播当前分P，后台拉取整视频后把缺失分P插到当前项之后；
 *   切歌后晚到结果直接丢弃。
 */

/** Build whole-video part songs for [info] (all pages, titled for group headers). */
fun buildBiliVideoPartSongs(
    info: BiliClient.VideoBasicInfo,
    coverUrl: String
): List<SongItem> {
    return info.pages.map { page ->
        buildBiliPartSong(page, info, coverUrl, info.title)
    }
}

data class BiliPartsMergeResult(
    val queue: List<SongItem>,
    val currentIndex: Int
)

/**
 * Complete the tapped work in page order, retaining the other queued works.
 *
 * - Aborts (null) when the playing item no longer matches [tappedKey].
 * - Parts already present anywhere in the queue (same bvid + cid) are skipped.
 * - Single-part videos merge to nothing (null).
 * Pure function, unit tested.
 */
fun mergeBiliVideoPartsAfterCurrent(
    queue: List<SongItem>,
    currentIndex: Int,
    tappedKey: String,
    parts: List<SongItem>
): BiliPartsMergeResult? {
    val tapped = queue.getOrNull(currentIndex) ?: return null
    if (tapped.stableKey() != tappedKey) return null
    if (parts.size <= 1) return null
    val tappedBvid = tapped.biliBvidOrNull() ?: return null
    val presentCids = queue.mapNotNullTo(HashSet()) { song ->
        if (song.biliBvidOrNull() == tappedBvid) song.biliCidOrNull() else null
    }
    val missing = parts.filter { part ->
        val cid = part.biliCidOrNull()
        cid == null || cid !in presentCids
    }
    if (missing.isEmpty()) return null
    // Keep the work in page order even when the resumed part is P5. Other works
    // retain their relative order, and existing duplicates/extra parts are retained.
    val firstVideoIndex = queue.indexOfFirst { it.biliBvidOrNull() == tappedBvid }
    val remaining = queue.filter { it.biliBvidOrNull() == tappedBvid }.toMutableList()
    val orderedParts = parts.map { part ->
        val existingIndex = remaining.indexOfFirst { it.biliCidOrNull() == part.biliCidOrNull() }
        if (existingIndex >= 0) remaining.removeAt(existingIndex).copy(album = part.album) else part
    } + remaining
    val merged = queue.take(firstVideoIndex) + orderedParts +
        queue.drop(firstVideoIndex).filterNot { it.biliBvidOrNull() == tappedBvid }
    return BiliPartsMergeResult(merged, merged.indexOfFirst { it.stableKey() == tappedKey })
}

sealed interface BiliQueueRow {
    /** Collapsible group header for one consecutive same-video run (2+ items). */
    data class GroupHeader(
        val groupKey: String,
        val bvid: String,
        val title: String,
        val count: Int,
        val firstQueueIndex: Int,
        /** Sum of part durations; unknown (0) durations contribute nothing. */
        val totalDurationMs: Long = 0L
    ) : BiliQueueRow {
        fun containsQueueIndex(queueIndex: Int): Boolean =
            queueIndex >= firstQueueIndex && queueIndex < firstQueueIndex + count
    }

    data class Song(
        val song: SongItem,
        val queueIndex: Int,
        /** Non-null when rendered under a group header. */
        val groupKey: String?
    ) : BiliQueueRow
}

/**
 * Group consecutive same-bvid runs (2+ items) under collapsible headers.
 * Single items and non-Bili songs render as plain rows. Non-consecutive runs
 * of one bvid form independent groups. Pure function, unit tested.
 */
fun groupConsecutiveBiliRuns(songs: List<SongItem>): List<BiliQueueRow> {
    if (songs.isEmpty()) return emptyList()
    val rows = ArrayList<BiliQueueRow>(songs.size)
    var index = 0
    while (index < songs.size) {
        val song = songs[index]
        val bvid = song.biliBvidOrNull()
        if (bvid.isNullOrBlank()) {
            rows += BiliQueueRow.Song(song = song, queueIndex = index, groupKey = null)
            index += 1
            continue
        }
        var end = index + 1
        while (end < songs.size && songs[end].biliBvidOrNull() == bvid) {
            end += 1
        }
        if (end - index < 2) {
            rows += BiliQueueRow.Song(song = song, queueIndex = index, groupKey = null)
            index += 1
            continue
        }
        val groupKey = "$bvid@${songs[index].stableKey()}"
        val title = songs[index].biliVideoTitleOrNull()?.takeIf { it.isNotBlank() }
            ?: songs[index].name
        rows += BiliQueueRow.GroupHeader(
            groupKey = groupKey,
            bvid = bvid,
            title = title,
            count = end - index,
            firstQueueIndex = index,
            totalDurationMs = (index until end).sumOf { songs[it].durationMs.coerceAtLeast(0L) }
        )
        for (i in index until end) {
            rows += BiliQueueRow.Song(
                song = songs[i],
                queueIndex = i,
                groupKey = groupKey
            )
        }
        index = end
    }
    return rows
}

data class BiliQueueSummary(
    /** Group headers plus ungrouped items: one per video/standalone song. */
    val workCount: Int,
    val itemCount: Int,
    val totalDurationMs: Long
)

/**
 * Queue header stats for the classical sheet ("3 个作品 · 9 个分P · 共 …").
 * Uses the same grouping as the list, so counts match what is rendered.
 * Pure function, unit tested.
 */
fun summarizeBiliQueue(songs: List<SongItem>): BiliQueueSummary {
    val rows = groupConsecutiveBiliRuns(songs)
    val workCount = rows.count { row ->
        row is BiliQueueRow.GroupHeader || (row is BiliQueueRow.Song && row.groupKey == null)
    }
    return BiliQueueSummary(
        workCount = workCount,
        itemCount = songs.size,
        totalDurationMs = songs.sumOf { it.durationMs.coerceAtLeast(0L) }
    )
}

/**
 * Tiny TTL LRU for video page lists: tapping the same video repeatedly must
 * not hit the view API every time. Pure logic (time injected), unit tested.
 */
class BiliVideoInfoCache(
    private val maxEntries: Int = 32,
    private val ttlMs: Long = 30 * 60 * 1000L,
    private val nowMs: () -> Long = System::currentTimeMillis
) {
    private data class Entry(val info: BiliClient.VideoBasicInfo, val storedAtMs: Long)

    private val map = LinkedHashMap<String, Entry>(16, 0.75f, true)

    @Synchronized
    fun get(bvid: String): BiliClient.VideoBasicInfo? {
        val entry = map[bvid] ?: return null
        if (nowMs() - entry.storedAtMs > ttlMs) {
            map.remove(bvid)
            return null
        }
        return entry.info
    }

    @Synchronized
    fun put(bvid: String, info: BiliClient.VideoBasicInfo) {
        map[bvid] = Entry(info, nowMs())
        while (map.size > maxEntries) {
            val eldest = map.keys.firstOrNull() ?: break
            map.remove(eldest)
        }
    }
}

/** Process-wide page-list cache shared by play-path expansions. */
internal val sharedBiliVideoInfoCache = BiliVideoInfoCache()

/**
 * Expand the currently playing Bilibili video to its whole page list.
 *
 * Called in background right after a direct tap starts playback: the tapped
 * part keeps playing, missing sibling parts are inserted right after it.
 * Aborts when the user has moved on (song key mismatch) or the video is
 * single-part. Returns true when the queue changed.
 */
internal suspend fun PlayerManager.expandBiliVideoPartsAfterCurrent(
    tappedKey: String,
    client: BiliClient,
    infoCache: BiliVideoInfoCache = sharedBiliVideoInfoCache
): Boolean {
    val tapped = withContext(Dispatchers.Main.immediate) {
        currentPlaylist.getOrNull(currentIndex)
    } ?: return false
    if (tapped.stableKey() != tappedKey) return false
    val bvid = tapped.biliBvidOrNull() ?: return false
    val info = infoCache.get(bvid)
        ?: runCatching { client.getVideoBasicInfoByBvid(bvid) }.getOrNull()
            ?.also { infoCache.put(bvid, it) }
        ?: return false
    if (info.pages.size <= 1) return false
    if (currentSongFlow.value?.stableKey() != tappedKey) return false
    val parts = buildBiliVideoPartSongs(info, tapped.coverUrl.orEmpty())
    return withContext(Dispatchers.Main.immediate) {
        if (currentSongFlow.value?.stableKey() != tappedKey) false
        else mergeBiliPartsIntoQueue(tappedKey, parts)
    }
}
