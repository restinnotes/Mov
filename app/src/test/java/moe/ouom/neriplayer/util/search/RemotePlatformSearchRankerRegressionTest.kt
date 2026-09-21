package moe.ouom.neriplayer.util.search

import moe.ouom.neriplayer.core.api.bili.BiliClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bilibili 远端排序回归（Subagent 04）：
 * 维格四重奏 / Végh / Backhaus 查询不得被客户端排序埋没；
 * 模糊噪声（Ghost Quartet）不得吸顶。
 */
class RemotePlatformSearchRankerRegressionTest {

    private fun video(
        aid: Long,
        title: String,
        author: String = "uploader",
        tag: String = "",
        description: String = "",
        hitColumns: List<String> = emptyList(),
        typeid: Int = 0
    ): BiliClient.SearchVideoItem {
        return BiliClient.SearchVideoItem(
            aid = aid,
            bvid = "BV1test$aid",
            titleHtml = title,
            titlePlain = title,
            author = author,
            mid = 1L,
            coverUrl = "",
            durationSec = 600,
            play = null,
            pubdate = null,
            typeid = typeid,
            typename = "",
            tag = tag,
            description = description,
            hitColumns = hitColumns
        )
    }

    @Test
    fun `vegh quartet exact title stays above fuzzy ghost`() {
        val vegh = video(1, "Végh Quartet - Beethoven Complete String Quartets", "classical")
        val ghost = video(2, "Ghost Quartet Live", "streamer")
        val ranked = RemotePlatformSearchRanker.rankBiliVideos("Vegh Quartet", listOf(ghost, vegh))
        assertEquals(1L, ranked.first().aid)
    }

    @Test
    fun `chinese query matches title`() {
        val vegh = video(1, "维格四重奏 贝多芬全套四重奏", "classical")
        val other = video(2, "流行金曲串烧", "pop")
        val ranked = RemotePlatformSearchRanker.rankBiliVideos("维格四重奏", listOf(other, vegh))
        assertEquals(1L, ranked.first().aid)
    }

    @Test
    fun `backhaus found via hit description does not sink`() {
        val backhaus = video(
            3,
            "钢琴之夜",
            "hall",
            description = "Wilhelm Backhaus 1959 年萨尔茨堡现场",
            hitColumns = listOf("description")
        )
        val filler = video(4, "钢琴之夜精选", "hall")
        val ranked = RemotePlatformSearchRanker.rankBiliVideos("Backhaus", listOf(filler, backhaus))
        assertTrue(ranked.indexOfFirst { it.aid == 3L } <= 1)
    }

    @Test
    fun `server order preserved when nothing matches`() {
        val items = listOf(video(1, "aaa"), video(2, "bbb"), video(3, "ccc"))
        val ranked = RemotePlatformSearchRanker.rankBiliVideos("zzzz-no-match", items)
        assertEquals(listOf(1L, 2L, 3L), ranked.map { it.aid })
    }
}
