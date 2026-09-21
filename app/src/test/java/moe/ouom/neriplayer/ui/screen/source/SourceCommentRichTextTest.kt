package moe.ouom.neriplayer.ui.screen.source

import moe.ouom.neriplayer.core.api.bili.BiliClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceCommentRichTextTest {

    private fun emote(key: String): BiliClient.BiliCommentEmote {
        return BiliClient.BiliCommentEmote(text = key, url = "https://x/$key.png", size = 1)
    }

    @Test
    fun `plain text keeps timestamps clickable`() {
        val segments = parseRichCommentSegments("前奏 12:34 好听", emptyMap())
        assertEquals(3, segments.size)
        val link = segments[1] as RichCommentSegment.TimeLink
        assertEquals("12:34", link.text)
        assertEquals((12 * 60 + 34) * 1000L, link.positionMs)
    }

    @Test
    fun `emote placeholders become image segments`() {
        val segments = parseRichCommentSegments(
            "哈哈[doge]好听",
            mapOf("[doge]" to emote("[doge]"))
        )
        assertEquals(3, segments.size)
        assertTrue(segments[0] is RichCommentSegment.Text)
        val image = segments[1] as RichCommentSegment.Emote
        assertEquals("https://x/[doge].png", image.url)
    }

    @Test
    fun `longer emote keys win over prefixes`() {
        val segments = parseRichCommentSegments(
            "[dogeflower]",
            mapOf("[doge]" to emote("[doge]"), "[dogeflower]" to emote("[dogeflower]"))
        )
        assertEquals(1, segments.size)
        assertEquals("[dogeflower]", (segments[0] as RichCommentSegment.Emote).key)
    }

    @Test
    fun `unknown placeholders stay text`() {
        val segments = parseRichCommentSegments("[unknown]hi", emptyMap())
        assertEquals(1, segments.size)
        assertTrue(segments[0] is RichCommentSegment.Text)
    }

    @Test
    fun `relative time buckets`() {
        val now = 1_700_000_000_000L
        assertEquals("刚刚", formatCommentRelativeTime(now - 30_000L, now))
        assertEquals("5分钟前", formatCommentRelativeTime(now - 5 * 60_000L, now))
        assertEquals("3小时前", formatCommentRelativeTime(now - 3 * 3_600_000L, now))
        assertEquals("昨天", formatCommentRelativeTime(now - 30 * 3_600_000L, now))
        // Far past: absolute date (timezone-dependent day, assert shape only).
        assertTrue(
            Regex("\\d{4}-\\d{2}-\\d{2}").matches(
                formatCommentRelativeTime(now - 5 * 86_400_000L, now)
            )
        )
    }
}
