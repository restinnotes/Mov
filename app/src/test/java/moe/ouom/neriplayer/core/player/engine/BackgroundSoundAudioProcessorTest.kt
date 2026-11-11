package moe.ouom.neriplayer.core.player.engine

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.StreamMetadata
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundSoundAudioProcessorTest {
    @Test
    fun `disabled mode preserves PCM even with saved music muted`() {
        val processor = BackgroundSoundAudioProcessor {
            BackgroundSoundConfig(enabled = false, musicVolume = 0f, backgroundVolume = 1f)
        }
        processor.configure(AudioFormat(44100, 2, C.ENCODING_PCM_16BIT))
        processor.flush(StreamMetadata.DEFAULT)
        val samples = listOf(-32768, 32767, -1234, 1234, 0, 0)
        val input = ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.nativeOrder())
        samples.forEach { input.putShort(it.toShort()) }
        input.flip()
        processor.queueInput(input)
        val output = processor.output
        samples.forEach { assertEquals(it.toShort(), output.short) }
        assertEquals(0, input.remaining())
        assertEquals(0, output.remaining())
    }

    @Test
    fun `music can be muted while noise continues in both channels`() {
        val config = BackgroundSoundConfig(enabled = true, musicVolume = 0f, backgroundVolume = 0.5f)
        val processor = BackgroundSoundAudioProcessor { config }
        processor.configure(AudioFormat(44100, 2, C.ENCODING_PCM_16BIT))
        processor.flush(StreamMetadata.DEFAULT)
        val input = ByteBuffer.allocateDirect(44100 * 4).order(ByteOrder.nativeOrder())
        while (input.hasRemaining()) input.putShort(0)
        input.flip()
        processor.queueInput(input)
        val output = processor.output
        var nonzero = 0
        while (output.remaining() >= 4) {
            val left = output.short
            val right = output.short
            assertEquals(left, right)
            if (left.toInt() != 0) nonzero++
        }
        assertTrue(nonzero > 40000)
    }

    @Test
    fun `all synthesized sounds remain finite bounded and continuous`() {
        for (rate in listOf(22050, 44100, 48000, 96000)) {
            for (sound in BackgroundSound.entries) {
                val generator = BackgroundSoundGenerator(Random(42))
                var energy = 0.0
                var mean = 0.0
                repeat(rate) {
                    val sample = generator.next(sound, rate)
                    assertTrue(sample.isFinite() && sample in -1f..1f)
                    energy += sample * sample
                    mean += sample
                }
                assertTrue("Silent $sound at $rate", energy / rate > 0.0001)
                assertTrue("DC offset in $sound at $rate", abs(mean / rate) < 0.1)
            }
        }
    }

    @Test
    fun `brown and pink emphasize slower changes than white`() {
        fun roughness(sound: BackgroundSound): Double {
            val generator = BackgroundSoundGenerator(Random(71))
            var previous = 0f
            var differences = 0.0
            var energy = 0.0
            repeat(44100) {
                val sample = generator.next(sound, 44100)
                differences += (sample - previous) * (sample - previous)
                energy += sample * sample
                previous = sample
            }
            return differences / energy
        }
        assertTrue(roughness(BackgroundSound.PINK) < roughness(BackgroundSound.WHITE) * 0.6)
        assertTrue(roughness(BackgroundSound.BROWN) < roughness(BackgroundSound.PINK) * 0.3)
    }

    @Test
    fun `independent gains retain headroom at full scale`() {
        assertEquals(0.3f, mixBackgroundSample(0.6f, 1f, 0.5f, 0f), 0.00001f)
        assertEquals(0.2f, mixBackgroundSample(1f, 0.4f, 0f, 0.5f), 0.00001f)
        assertEquals(1f, mixBackgroundSample(1f, 1f, 1f, 1f), 0.00001f)
        assertEquals(0f, mixBackgroundSample(1f, -1f, 1f, 1f), 0.00001f)
        assertEquals(0f, mixBackgroundSample(1f, 1f, 0f, 0f), 0.00001f)
    }

    @Test
    fun `invalid persisted gains are sanitized`() {
        val config = BackgroundSoundConfig(musicVolume = Float.NaN, backgroundVolume = 9f).sanitized()
        assertEquals(1f, config.musicVolume, 0f)
        assertEquals(1f, config.backgroundVolume, 0f)
    }
}
