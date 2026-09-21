package moe.ouom.neriplayer.core.classical.source

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import moe.ouom.neriplayer.core.api.bili.BiliClient

/**
 * Lightweight Bilibili Source data layer for the Classical negative-one page.
 *
 * Scope is deliberately tiny: video basic info + first comment page, nothing else.
 * - No AI, no metadata entities, no timeline, no markers.
 * - Bounded LRU: last [MAX_CACHED_SOURCES] BVids keep intro + first comment page.
 *   Later comment pages and expanded replies live only in the ViewModel and are
 *   released on source change.
 * - Never throws: intro failure -> null; comment failure -> snapshot without comments.
 */
data class BiliSourceIntro(
    val bvid: String,
    val aid: Long,
    val title: String,
    val coverUrl: String?,
    val uploaderName: String,
    val uploaderMid: Long,
    val pubdateSec: Long,
    val desc: String,
    val sourceUrl: String
)

data class BiliSourceSnapshot(
    val intro: BiliSourceIntro,
    val firstComments: BiliClient.BiliCommentPage?
)

open class BiliSourceRepository(
    private val api: BiliSourceApi = BiliClient()
) {
    companion object {
        const val MAX_CACHED_SOURCES = 5

        /** Bilibili default comment sort (hot). First version only serves the default stream. */
        const val DEFAULT_COMMENT_SORT = 2
        const val FIRST_PAGE_SIZE = 20
    }

    private val lock = Mutex()
    private val cache = object : LinkedHashMap<String, BiliSourceSnapshot>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BiliSourceSnapshot>): Boolean {
            return size > MAX_CACHED_SOURCES
        }
    }

    open suspend fun cached(bvid: String): BiliSourceSnapshot? {
        if (bvid.isBlank()) return null
        return lock.withLock { cache[bvid] }
    }

    /**
     * Preload intro + first comment page for [bvid] in background.
     * Returns the snapshot (cached or freshly loaded), or null when the intro
     * itself cannot be loaded. Callers must check source generation before
     * applying the result: late arrivals must never overwrite a newer source.
     */
    open suspend fun preload(bvid: String, aidHint: Long = 0L): BiliSourceSnapshot? {
        if (bvid.isBlank()) return null
        lock.withLock { cache[bvid] }?.let { return it }
        val info = runCatching { api.getVideoBasicInfoByBvid(bvid) }.getOrNull()
            ?: return null
        val aid = info.aid.takeIf { it > 0L } ?: aidHint.takeIf { it > 0L } ?: 0L
        val intro = BiliSourceIntro(
            bvid = info.bvid.ifBlank { bvid },
            aid = aid,
            title = info.title,
            coverUrl = info.coverUrl.takeIf { it.isNotBlank() },
            uploaderName = info.ownerName,
            uploaderMid = info.ownerMid,
            pubdateSec = info.pubdateSec,
            desc = info.desc,
            sourceUrl = "https://www.bilibili.com/video/${info.bvid.ifBlank { bvid }}"
        )
        val firstPage = if (aid > 0L) {
            runCatching {
                api.getCommentPageByAvid(
                    avid = aid,
                    sort = DEFAULT_COMMENT_SORT,
                    page = 1,
                    pageSize = FIRST_PAGE_SIZE
                )
            }.getOrNull()
        } else {
            null
        }
        val snapshot = BiliSourceSnapshot(intro = intro, firstComments = firstPage)
        lock.withLock { cache[bvid] = snapshot }
        return snapshot
    }

    open suspend fun loadCommentPage(
        avid: Long,
        page: Int,
        pageSize: Int = FIRST_PAGE_SIZE
    ): BiliClient.BiliCommentPage? {
        if (avid <= 0L || page < 1) return null
        return runCatching {
            api.getCommentPageByAvid(
                avid = avid,
                sort = DEFAULT_COMMENT_SORT,
                page = page,
                pageSize = pageSize
            )
        }.getOrNull()
    }

    open suspend fun loadReplies(
        avid: Long,
        rootRpid: String,
        page: Int,
        pageSize: Int = 10
    ): BiliClient.BiliCommentPage? {
        if (avid <= 0L || rootRpid.isBlank() || page < 1) return null
        return runCatching {
            api.getCommentRepliesByAvid(
                avid = avid,
                rootRpid = rootRpid,
                page = page,
                pageSize = pageSize
            )
        }.getOrNull()
    }
}
