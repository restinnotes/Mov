package moe.ouom.neriplayer.core.player.engine

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

enum class BackgroundSound { PINK, BROWN, WHITE, RAIN, FAN }

data class BackgroundSoundConfig(
    val enabled: Boolean = false,
    val sound: BackgroundSound = BackgroundSound.PINK,
    val musicVolume: Float = 1f,
    val backgroundVolume: Float = 0.2f,
) {
    fun sanitized() = copy(
        musicVolume = musicVolume.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1f,
        backgroundVolume = backgroundVolume.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.2f,
    )
    val requiresProcessing: Boolean get() = enabled
}

internal object BackgroundSoundMixState {
    @Volatile var config = BackgroundSoundConfig()
}

/** Continuous synthesis: no assets, loop seams, network, or second audio focus owner. */
internal class BackgroundSoundGenerator(private val random: Random = Random.Default) {
    private val pink = DoubleArray(7)
    private var brown = 0.0
    private var low = 0.0
    private var phase = 0.0

    fun next(sound: BackgroundSound, sampleRate: Int): Float {
        val white = random.nextDouble(-1.0, 1.0)
        return when (sound) {
            BackgroundSound.WHITE -> white * 0.5
            BackgroundSound.PINK, BackgroundSound.RAIN -> {
                // These pink filter coefficients target 44.1 kHz.
                pink[0] = 0.99886 * pink[0] + white * 0.0555179
                pink[1] = 0.99332 * pink[1] + white * 0.0750759
                pink[2] = 0.96900 * pink[2] + white * 0.1538520
                pink[3] = 0.86650 * pink[3] + white * 0.3104856
                pink[4] = 0.55000 * pink[4] + white * 0.5329522
                pink[5] = -0.7616 * pink[5] - white * 0.0168980
                val sample = (pink.sum() + white * 0.5362) * 0.11
                pink[6] = white * 0.115926
                if (sound == BackgroundSound.RAIN) sample * 0.65 + white * 0.18 else sample
            }
            BackgroundSound.BROWN -> {
                val rate = 44100.0 / sampleRate.coerceAtLeast(1)
                brown = (brown + white * 0.035 * kotlin.math.sqrt(rate)) / (1.0 + 0.003 * rate)
                brown * 1.8
            }
            BackgroundSound.FAN -> {
                low += (1.0 - kotlin.math.exp(-2.0 * PI * 350.0 / sampleRate)) * (white - low)
                phase = (phase + 2.0 * PI * 60.0 / sampleRate) % (2.0 * PI)
                low * 1.4 + sin(phase) * 0.07 + sin(phase * 2) * 0.025
            }
        }.toFloat().coerceIn(-1f, 1f)
    }
}

internal fun mixBackgroundSample(music: Float, noise: Float, musicGain: Float, noiseGain: Float): Float {
    // Reserve headroom when both layers are loud, preserving their relative gain.
    return ((music * musicGain + noise * noiseGain) / maxOf(1f, musicGain + noiseGain))
        .coerceIn(-1f, 1f)
}

@UnstableApi
internal class BackgroundSoundAudioProcessor(
    private val configProvider: () -> BackgroundSoundConfig = { BackgroundSoundMixState.config },
) : BaseAudioProcessor() {
    private var generator = BackgroundSoundGenerator()
    private var musicGain = 1f
    private var noiseGain = 0f
    private var encoding = C.ENCODING_PCM_16BIT
    private var channels = 2
    private var rate = 44100

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.channelCount <= 0 || inputAudioFormat.sampleRate <= 0) return AudioFormat.NOT_SET
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) return AudioFormat.NOT_SET
        encoding = inputAudioFormat.encoding
        channels = inputAudioFormat.channelCount
        rate = inputAudioFormat.sampleRate
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val config = configProvider().sanitized()
        val targetNoise = if (config.enabled) config.backgroundVolume else 0f
        val targetMusic = if (config.enabled) config.musicVolume else 1f
        val output = replaceOutputBuffer(inputBuffer.remaining())
        if (!config.requiresProcessing && musicGain == 1f && noiseGain == 0f) {
            output.put(inputBuffer)
        } else {
            val frameBytes = channels * if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
            val smoothing = (1.0 - kotlin.math.exp(-1.0 / (rate * 0.025))).toFloat()
            while (inputBuffer.remaining() >= frameBytes) {
                musicGain += (targetMusic - musicGain) * smoothing
                noiseGain += (targetNoise - noiseGain) * smoothing
                if (kotlin.math.abs(musicGain - targetMusic) < 0.00001f) musicGain = targetMusic
                if (kotlin.math.abs(noiseGain - targetNoise) < 0.00001f) noiseGain = targetNoise
                val noise = if (noiseGain > 0f) generator.next(config.sound, rate) else 0f
                repeat(channels) {
                    val music = if (encoding == C.ENCODING_PCM_FLOAT) inputBuffer.float else inputBuffer.short / 32768f
                    val mixed = mixBackgroundSample(music, noise, musicGain, noiseGain)
                    if (encoding == C.ENCODING_PCM_FLOAT) output.putFloat(mixed)
                    else output.putShort((mixed * 32768f).toInt().coerceIn(-32768, 32767).toShort())
                }
            }
            while (inputBuffer.hasRemaining()) output.put(inputBuffer.get())
        }
        output.flip()
    }

    override fun onReset() {
        generator = BackgroundSoundGenerator()
        musicGain = 1f
        noiseGain = 0f
    }
}
