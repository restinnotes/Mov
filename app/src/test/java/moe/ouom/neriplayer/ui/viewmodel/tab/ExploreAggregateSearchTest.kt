package moe.ouom.neriplayer.ui.viewmodel.tab

import moe.ouom.neriplayer.data.model.SongItem
import moe.ouom.neriplayer.data.search.ClassicalDefaultSearchMode
import moe.ouom.neriplayer.data.search.normalizeDefaultSearchMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExploreAggregateSearchTest {

    @Test
    fun `clean classical install defaults to bilibili`() {
        assertEquals(
            ExploreSearchMode.Provider(SearchSource.BILIBILI),
            resolveInitialSearchMode(
                classical = true,
                defaultMode = ClassicalDefaultSearchMode.BILIBILI,
                youtubeAvailable = true
            )
        )
    }

    @Test
    fun `preference switches default to aggregate or youtube`() {
        assertEquals(
            ExploreSearchMode.Aggregate,
            resolveInitialSearchMode(
                classical = true,
                defaultMode = ClassicalDefaultSearchMode.AGGREGATE,
                youtubeAvailable = true
            )
        )
        assertEquals(
            ExploreSearchMode.Provider(SearchSource.YOUTUBE_MUSIC),
            resolveInitialSearchMode(
                classical = true,
                defaultMode = ClassicalDefaultSearchMode.YOUTUBE,
                youtubeAvailable = true
            )
        )
    }

    @Test
    fun `stale youtube default falls back safely`() {
        assertEquals(
            ExploreSearchMode.Provider(SearchSource.BILIBILI),
            resolveInitialSearchMode(
                classical = true,
                defaultMode = ClassicalDefaultSearchMode.YOUTUBE,
                youtubeAvailable = false
            )
        )
        assertEquals(
            ClassicalDefaultSearchMode.BILIBILI,
            normalizeDefaultSearchMode("YOUTUBE", youtubeAvailable = false)
        )
        assertEquals(
            ClassicalDefaultSearchMode.BILIBILI,
            normalizeDefaultSearchMode("BOGUS", youtubeAvailable = true)
        )
    }

    @Test
    fun `official flavor keeps legacy default`() {
        assertEquals(
            ExploreSearchMode.Provider(SearchSource.NETEASE),
            resolveInitialSearchMode(
                classical = false,
                defaultMode = ClassicalDefaultSearchMode.BILIBILI,
                youtubeAvailable = true
            )
        )
    }

    @Test
    fun `aggregate dedups only identical source identities`() {
        val song = biliSong(aid = 1001L, bvid = "BV1xx411c7mD")
        val first = listOf(ExploreSearchResult.Song(song))
        val merged = mergeAggregateProviderItems(first, listOf(ExploreSearchResult.Song(song)))
        assertEquals(1, merged.size)
        val other = biliSong(aid = 1002L, bvid = "BV1yy411c7mE")
        val grown = mergeAggregateProviderItems(first, listOf(ExploreSearchResult.Song(other)))
        assertEquals(2, grown.size)
        assertTrue(grown.map { it.stableKey }.distinct().size == 2)
    }

    private fun biliSong(aid: Long, bvid: String): SongItem {
        return SongItem(
            id = aid,
            name = "title $bvid",
            artist = "uploader",
            album = "Bilibili|$bvid",
            albumId = 0L,
            durationMs = 180_000L,
            coverUrl = null,
            channelId = "bilibili",
            audioId = aid.toString()
        )
    }
}
