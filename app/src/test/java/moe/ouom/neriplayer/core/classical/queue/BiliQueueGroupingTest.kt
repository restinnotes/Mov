package moe.ouom.neriplayer.core.classical.queue

import moe.ouom.neriplayer.core.api.bili.BiliClient
import moe.ouom.neriplayer.core.api.bili.biliBvidOrNull
import moe.ouom.neriplayer.core.api.bili.biliCidOrNull
import moe.ouom.neriplayer.core.api.bili.biliVideoTitleOrNull
import moe.ouom.neriplayer.core.api.bili.buildBiliSongAlbum
import moe.ouom.neriplayer.data.model.SongItem
import moe.ouom.neriplayer.data.model.stableKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BiliQueueGroupingTest {

    private fun biliPart(
        bvid: String,
        cid: Long,
        name: String = "P$cid",
        videoTitle: String? = null
    ): SongItem {
        return SongItem(
            id = 100L,
            name = name,
            artist = "UP",
            album = buildBiliSongAlbum(cid = cid, bvid = bvid, videoTitle = videoTitle),
            albumId = 0L,
            durationMs = 60_000L,
            coverUrl = null,
            channelId = "bilibili",
            audioId = "100",
            subAudioId = cid.toString()
        )
    }

    private fun local(name: String): SongItem {
        return SongItem(
            id = name.hashCode().toLong(),
            name = name,
            artist = "a",
            album = "专辑",
            albumId = 0L,
            durationMs = 1000L,
            coverUrl = null
        )
    }

    private fun info(bvid: String, cids: List<Long>): BiliClient.VideoBasicInfo {
        return BiliClient.VideoBasicInfo(
            aid = 100L,
            bvid = bvid,
            title = "视频标题 $bvid",
            coverUrl = "",
            desc = "",
            durationSec = 300,
            ownerMid = 2L,
            ownerName = "UP",
            ownerFace = "",
            stats = BiliClient.VideoStats(0, 0, 0, 0, 0, 0, 0),
            pages = cids.mapIndexed { i, cid ->
                BiliClient.VideoPage(
                    cid = cid,
                    page = i + 1,
                    part = "P${i + 1}",
                    durationSec = 60,
                    width = 0,
                    height = 0
                )
            }
        )
    }

    @Test
    fun `album title round-trips and identity is stable`() {
        val song = biliPart("BV1x", 11L, videoTitle = " Goldberg | Variations ")
        assertEquals("BV1x", song.biliBvidOrNull())
        assertEquals(11L, song.biliCidOrNull())
        assertEquals("Goldberg   Variations", song.biliVideoTitleOrNull())
        val legacy = biliPart("BV1x", 11L)
        assertNull(legacy.biliVideoTitleOrNull())
        // Same part, old vs new album format: identical stable identity.
        assertEquals(legacy.stableKey(), song.stableKey())
    }

    @Test
    fun `consecutive same-video run forms one group`() {
        val songs = listOf(
            local("前"),
            biliPart("BV1a", 1L, videoTitle = "标题A"),
            biliPart("BV1a", 2L, videoTitle = "标题A"),
            biliPart("BV1a", 3L, videoTitle = "标题A"),
            local("后")
        )
        val rows = groupConsecutiveBiliRuns(songs)
        assertEquals(6, rows.size)
        val header = rows[1] as BiliQueueRow.GroupHeader
        assertEquals("标题A", header.title)
        assertEquals(3, header.count)
        assertEquals(1, header.firstQueueIndex)
        assertTrue(rows[2] is BiliQueueRow.Song)
    }

    @Test
    fun `single bili item renders as plain row`() {
        val songs = listOf(local("前"), biliPart("BV1a", 1L), local("后"))
        val rows = groupConsecutiveBiliRuns(songs)
        assertEquals(3, rows.size)
        assertTrue(rows.all { it is BiliQueueRow.Song })
    }

    @Test
    fun `non-consecutive runs form independent groups`() {
        val songs = listOf(
            biliPart("BV1a", 1L, videoTitle = "A"),
            biliPart("BV1a", 2L, videoTitle = "A"),
            local("中"),
            biliPart("BV1a", 3L, videoTitle = "A"),
            biliPart("BV1a", 4L, videoTitle = "A")
        )
        val headers = groupConsecutiveBiliRuns(songs)
            .filterIsInstance<BiliQueueRow.GroupHeader>()
        assertEquals(2, headers.size)
    }

    @Test
    fun `merge inserts missing parts after current`() {
        val tapped = biliPart("BV1a", 1L)
        val queue = listOf(local("前"), tapped, local("后"))
        val parts = buildBiliVideoPartSongs(info("BV1a", listOf(1L, 2L, 3L)), "")
        val merged = mergeBiliVideoPartsAfterCurrent(
            queue = queue,
            currentIndex = 1,
            tappedKey = tapped.stableKey(),
            parts = parts
        )!!
        assertEquals(1, merged.currentIndex)
        assertEquals(
            listOf("前", "P1", "P2", "P3", "后"),
            merged.queue.map { it.name }
        )
    }

    @Test
    fun `merge skips parts already queued`() {
        val queue = listOf(
            biliPart("BV1a", 1L),
            biliPart("BV1a", 3L)
        )
        val tapped = queue[0]
        val parts = buildBiliVideoPartSongs(info("BV1a", listOf(1L, 2L, 3L)), "")
        val merged = mergeBiliVideoPartsAfterCurrent(
            queue = queue,
            currentIndex = 0,
            tappedKey = tapped.stableKey(),
            parts = parts
        )!!
        assertEquals(listOf(1L, 2L, 3L), merged.queue.map { it.biliCidOrNull() })
    }

    @Test
    fun `merge aborts when song changed`() {
        val queue = listOf(biliPart("BV1a", 1L))
        val parts = buildBiliVideoPartSongs(info("BV1a", listOf(1L, 2L)), "")
        assertNull(
            mergeBiliVideoPartsAfterCurrent(
                queue = queue,
                currentIndex = 0,
                tappedKey = "stale-key",
                parts = parts
            )
        )
    }

    @Test
    fun `cache honors ttl and bound`() {
        var now = 0L
        val cache = BiliVideoInfoCache(maxEntries = 2, ttlMs = 1000L, nowMs = { now })
        val a = info("A", listOf(1L))
        cache.put("A", a)
        assertEquals(a, cache.get("A"))
        now = 2000L
        assertNull(cache.get("A"))
        cache.put("A", a)
        cache.put("B", info("B", listOf(1L)))
        cache.put("C", info("C", listOf(1L)))
        assertNull(cache.get("A"))
        assertTrue(cache.get("C") != null)
    }
}
