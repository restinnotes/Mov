package moe.ouom.neriplayer.core.player.resolver.bili

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class BiliPlaybackRangeSupportTest {
    private val url = "https://upos-sz-mirror.bilivideo.com/upgcxcode/track.m4s?deadline=123"

    @Test
    fun enablesRangesForBiliAudioIncludingP2pButLeavesOtherSourcesAlone() {
        assertTrue(shouldUseBiliResumableRange(url))
        assertTrue(shouldUseBiliResumableRange("https://cn-demo.mcdn.bilivideo.cn:8082/upgcxcode/track.m4s"))
        assertFalse(shouldUseBiliResumableRange("https://example.com/audio.m4s"))
        assertFalse(shouldUseBiliResumableRange("https://www.bilibili.com/video/BV123"))
        assertFalse(shouldUseBiliResumableRange("https://upos-sz-mirror.bilivideo.com/index.m3u8"))
        assertFalse(shouldUseBiliResumableRange("file:///upgcxcode/track.m4s"))
    }

    @Test
    fun acceptsExactResumptionOffsetAndUnknownTotal() {
        validateBiliRangeResponse(url, 206, mapOf("content-range" to listOf("bytes 1048576-2097151/10000000")), 1048576L)
        validateBiliRangeResponse(url, 206, mapOf("Content-Range" to listOf("bytes 1048576-2097151/*")), 1048576L)
        validateBiliRangeResponse(url, 200, emptyMap(), 1048576L)
    }

    @Test
    fun rejectsMissingMalformedMisalignedAndOverflowingRanges() {
        val ranges = listOf(null, "invalid", "bytes 0-1023/10000000", "bytes 2048-1024/10000000", "bytes 1024-2048/2048", "bytes 1024-2048/999999999999999999999999")
        ranges.forEach { range ->
            val headers = range?.let { mapOf("Content-Range" to listOf(it)) }.orEmpty()
            assertTrue("range=$range", runCatching {
                validateBiliRangeResponse(url, 206, headers, 1024L)
            }.exceptionOrNull() is IOException)
        }
    }

    @Test
    fun rejectsErrorDocumentsButAllowsMediaAndMissingMime() {
        listOf("text/html; charset=UTF-8", "TEXT/PLAIN", "application/json", "application/problem+json", "application/xml").forEach {
            assertTrue(it, isBiliErrorResponseContentType(it))
        }
        listOf(null, "", "audio/mp4", "video/mp4", "audio/flac", "application/octet-stream").forEach {
            assertFalse(it, isBiliErrorResponseContentType(it))
        }
    }
}
