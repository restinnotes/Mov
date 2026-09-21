package moe.ouom.neriplayer.ui.screen.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceTimestampParserTest {

    @Test
    fun `parses mm colon ss`() {
        val links = parseTimestampLinks("前奏很棒 12:34 进入主题")
        assertEquals(1, links.size)
        assertEquals((12 * 60 + 34) * 1000L, links[0].positionMs)
    }

    @Test
    fun `parses h mm ss`() {
        val links = parseTimestampLinks("1:02:15 是高潮")
        assertEquals(1, links.size)
        assertEquals((3600 + 2 * 60 + 15) * 1000L, links[0].positionMs)
    }

    @Test
    fun `plain comments have no links but still render`() {
        assertTrue(parseTimestampLinks("太好听了，反复循环").isEmpty())
        assertTrue(parseTimestampLinks("").isEmpty())
    }

    @Test
    fun `multiple timestamps in one message`() {
        val links = parseTimestampLinks("00:10 开头 03:20 中段 1:00:00 终曲")
        assertEquals(3, links.size)
        assertEquals(10_000L, links[0].positionMs)
        assertEquals(200_000L, links[1].positionMs)
        assertEquals(3_600_000L, links[2].positionMs)
    }

    @Test
    fun `offsets cover the matched text`() {
        val message = "听 12:34 吧"
        val link = parseTimestampLinks(message).single()
        assertEquals("12:34", message.substring(link.start, link.endExclusive))
    }

    @Test
    fun `unreasonable hours are plain text`() {
        assertTrue(parseTimestampLinks("99:00:00 不是时间戳").isEmpty())
    }
}
