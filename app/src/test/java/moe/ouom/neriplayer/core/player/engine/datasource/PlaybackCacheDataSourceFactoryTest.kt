@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package moe.ouom.neriplayer.core.player.engine.datasource

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.DefaultContentMetadata
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class PlaybackCacheDataSourceFactoryTest {
    @Test
    fun `playback reads upstream while a prefetch writer holds the cache span`() {
        val cache = mock(Cache::class.java)
        `when`(cache.getContentMetadata("track")).thenReturn(DefaultContentMetadata.EMPTY)
        // A locked hole cannot be acquired without waiting. Blocking here would freeze playback.
        `when`(cache.startReadWriteNonBlocking(anyString(), anyLong(), anyLong())).thenReturn(null)
        doAnswer { throw AssertionError("Playback waited for the prefetch cache writer") }
            .`when`(cache).startReadWrite(anyString(), anyLong(), anyLong())
        val uri = mock(Uri::class.java)
        `when`(uri.scheme).thenReturn("https")
        `when`(uri.toString()).thenReturn("https://example.com/audio.m4a")
        val payload = byteArrayOf(1, 2, 3, 4)
        val upstream = object : DataSource {
            private var read = false
            override fun open(dataSpec: DataSpec): Long = payload.size.toLong()
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (read) return C.RESULT_END_OF_INPUT
                payload.copyInto(buffer, offset)
                read = true
                return payload.size
            }
            override fun getUri(): Uri = uri
            override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) = Unit
            override fun close() = Unit
        }
        val source = buildPlaybackCacheDataSourceFactory(cache, DataSource.Factory { upstream })
            .createDataSource()
        try {
            assertEquals(4L, source.open(DataSpec.Builder().setUri(uri).setKey("track").build()))
            val buffer = ByteArray(4)
            assertEquals(4, source.read(buffer, 0, buffer.size))
            assertArrayEquals(payload, buffer)
            assertEquals(C.RESULT_END_OF_INPUT, source.read(buffer, 0, buffer.size))
        } finally {
            source.close()
        }
        verify(cache, never()).startReadWrite(anyString(), anyLong(), anyLong())
    }
}
