@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package moe.ouom.neriplayer.core.player.engine.datasource

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.ByteArrayOutputStream
import java.io.IOException

class BiliResumableDataSourceTest {
    @Test
    fun unknownTotalLengthKeepsReadingBeyondTheFirstChunk() {
        val fixture = Fixture(unknownTotal = true)
        val source = fixture.source()
        val result = try {
            assertEquals(C.LENGTH_UNSET.toLong(), source.open(fixture.spec()))
            readAll(source)
        } finally {
            source.close()
        }
        assertArrayEquals(fixture.content, result)
        assertEquals(11, fixture.positions.size)
    }

    @Test
    fun midstreamShortReadResumesAtExactOffsetWithoutLosingOrDuplicatingBytes() {
        val fixture = Fixture(shortReadAt = 8)
        val source = fixture.source()
        val result = try {
            assertEquals(fixture.content.size.toLong(), source.open(fixture.spec()))
            readAll(source)
        } finally {
            source.close()
        }
        assertArrayEquals(fixture.content, result)
        assertEquals(8L * MIB, fixture.positions[8])
        assertEquals(8L * MIB + 12345L, fixture.positions[9])
        assertTrue(fixture.lengths.all { it <= MIB })
    }

    @Test
    fun resumesPlaybackFromNonzeroByteOffset() {
        val fixture = Fixture(shortReadAt = 1)
        val source = fixture.source()
        val start = 2L * MIB + 500L
        val result = try {
            source.open(fixture.spec(start))
            readAll(source)
        } finally {
            source.close()
        }
        assertArrayEquals(fixture.content.copyOfRange(start.toInt(), fixture.content.size), result)
        assertEquals(start, fixture.positions.first())
        assertEquals(start + MIB + 12345L, fixture.positions[2])
    }

    @Test
    fun rejectsMisalignedContinuationBeforeReturningWrongBytes() {
        val fixture = Fixture(misalignedAt = 1)
        val source = fixture.source()
        val output = ByteArrayOutputStream()
        val error = try {
            source.open(fixture.spec())
            runCatching { readAll(source, output) }.exceptionOrNull()
        } finally {
            source.close()
        }
        assertTrue(error is IOException)
        assertArrayEquals(fixture.content.copyOfRange(0, MIB), output.toByteArray())
        assertEquals(2, fixture.closedCount)
    }

    @Test
    fun rejectsMidstreamErrorPageBeforeReturningItsBody() {
        val fixture = Fixture(errorPageAt = 1)
        val source = fixture.source()
        val output = ByteArrayOutputStream()
        val error = try {
            source.open(fixture.spec())
            runCatching { readAll(source, output) }.exceptionOrNull()
        } finally {
            source.close()
        }
        assertTrue(error is HttpDataSource.InvalidContentTypeException)
        assertEquals(MIB, output.size())
        assertEquals(2, fixture.closedCount)
    }

    @Test
    fun repeatedZeroByteEofStopsWithoutInfiniteRequests() {
        val fixture = Fixture(alwaysEmpty = true)
        val source = fixture.source()
        val error = try {
            source.open(fixture.spec())
            runCatching { readAll(source) }.exceptionOrNull()
        } finally {
            source.close()
        }
        assertTrue(error is IOException)
        assertEquals(listOf(0L, 0L), fixture.positions)
    }

    private fun readAll(source: HttpDataSource, output: ByteArrayOutputStream = ByteArrayOutputStream()): ByteArray {
        val buffer = ByteArray(8192)
        while (true) {
            val read = source.read(buffer, 0, buffer.size)
            if (read == C.RESULT_END_OF_INPUT) return output.toByteArray()
            output.write(buffer, 0, read)
        }
    }

    private class Fixture(
        val shortReadAt: Int = -1,
        val misalignedAt: Int = -1,
        val errorPageAt: Int = -1,
        val alwaysEmpty: Boolean = false,
        val unknownTotal: Boolean = false
    ) {
        val content = ByteArray(10 * MIB + 125) { (it % 251).toByte() }
        val positions = mutableListOf<Long>()
        val lengths = mutableListOf<Long>()
        var closedCount = 0
        private val uri = mock(Uri::class.java).also {
            `when`(it.toString()).thenReturn("https://upos-sz-mirror.bilivideo.com/upgcxcode/audio.m4s")
            `when`(it.host).thenReturn("upos-sz-mirror.bilivideo.com")
        }

        fun spec(position: Long = 0L): DataSpec = DataSpec.Builder().setUri(uri).setPosition(position).build()

        fun source() = ResumableChunkedHttpDataSource(
            upstreamFactory = object : HttpDataSource.Factory {
                override fun createDataSource(): HttpDataSource = Delegate()
                override fun setDefaultRequestProperties(properties: Map<String, String>): HttpDataSource.Factory = this
            },
            transformDataSpec = { it }
        )

        private inner class Delegate : HttpDataSource {
            private var index = -1
            private var cursor = 0
            private var end = 0
            private var advertisedEnd = 0
            private var start = 0
            private var closed = false

            override fun open(dataSpec: DataSpec): Long {
                index = positions.size
                positions += dataSpec.position
                lengths += dataSpec.length
                start = dataSpec.position.toInt()
                cursor = start
                advertisedEnd = minOf(content.size.toLong(), dataSpec.position + dataSpec.length).toInt()
                end = when {
                    alwaysEmpty -> start
                    index == shortReadAt -> minOf(advertisedEnd, start + 12345)
                    else -> advertisedEnd
                }
                return (advertisedEnd - start).toLong()
            }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (cursor >= end) return C.RESULT_END_OF_INPUT
                val read = minOf(length, end - cursor)
                content.copyInto(buffer, offset, cursor, cursor + read)
                cursor += read
                return read
            }

            override fun getUri(): Uri = this@Fixture.uri
            override fun getResponseCode(): Int = 206
            override fun getResponseHeaders(): Map<String, List<String>> = mapOf(
                "Content-Type" to listOf(if (index == errorPageAt) "text/html" else "audio/mp4"),
                "Content-Length" to listOf((advertisedEnd - start).toString()),
                "Content-Range" to listOf("bytes ${if (index == misalignedAt) 0 else start}-${advertisedEnd - 1}/${if (unknownTotal) "*" else content.size}")
            )
            override fun close() {
                if (!closed) { closedCount++; closed = true }
            }
            override fun addTransferListener(listener: TransferListener) = Unit
            override fun setRequestProperty(name: String, value: String) = Unit
            override fun clearRequestProperty(name: String) = Unit
            override fun clearAllRequestProperties() = Unit
        }
    }

    companion object { private const val MIB = 1024 * 1024 }
}
