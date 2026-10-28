package moe.ouom.neriplayer.core.classical

import moe.ouom.neriplayer.core.api.bili.buildBiliSongAlbum
import moe.ouom.neriplayer.data.model.SongItem
import org.junit.Assert.assertEquals
import org.junit.Test

class ClassicalPresentationTest {
    private fun song(id: Long, bvid: String? = null, cid: Long = id) = SongItem(
        id = id,
        name = "Work $id, part $cid",
        artist = "Artist",
        album = bvid?.let { buildBiliSongAlbum(cid = cid, bvid = it) } ?: "Album",
        albumId = 0L,
        durationMs = 60_000L,
        coverUrl = null,
        channelId = if (bvid != null) "bilibili" else null,
        audioId = if (bvid != null) id.toString() else null,
        subAudioId = if (bvid != null) cid.toString() else null
    )

    @Test
    fun `recent works preserve latest part and recency without merging other songs`() {
        val latest = song(1, "BV1first", 3)
        val other = song(2, "BV1second", 4)
        val localA = song(3)
        val localB = song(4)
        assertEquals(
            listOf(latest, other, localA, localB),
            classicalRecentWorks(listOf(latest, other, localA, song(1, "BV1first", 1), localB, localA))
        )
    }

    @Test
    fun `long queue durations keep hours without minute rollover`() {
        assertEquals("0:00", formatClassicalDuration(-1))
        assertEquals("59:59", formatClassicalDuration(3_599_999))
        assertEquals("1:00:00", formatClassicalDuration(3_600_000))
        assertEquals("1:57:43", formatClassicalDuration(7_063_000))
    }
}
