package moe.ouom.neriplayer.core.classical

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LowPowerDiagnosticsSnapshot(
    val classicalLowPowerEnabled: Boolean = ClassicalLowPower.enabled,
    val lyricsSubsystemDisabled: Boolean = ClassicalLowPower.lyricsDisabled,
    val lyriconDisabled: Boolean = ClassicalLowPower.lyriconDisabled,
    val waveformDisabled: Boolean = ClassicalLowPower.enabled,
    val dynamicBackgroundDisabled: Boolean = ClassicalLowPower.dynamicBackgroundDisabled,

    val source: String = "None",
    val mimeType: String = "-",
    val codec: String = "-",
    val bitrateKbps: Int? = null,
    val sampleRateHz: Int? = null,
    val channelCount: Int? = null,
    val playbackSpeed: Float = 1.0f,
    val hasEqualizerOrEffects: Boolean = false,
    val audioDecoderName: String = "-",

    val offloadRequested: Boolean = false,
    val offloadEligible: Boolean = false,
    val isOffloadedPlayback: Boolean = false,
    val isSleepingForOffload: Boolean = false,
    val lastOffloadUpdateReason: String = "init",
    val updatedAtMs: Long = System.currentTimeMillis()
)

/**
 * 零开销低功耗与 Audio Offload 诊断记录器。
 *
 * 绝对不使用任何定时轮询或周期性计时器。
 * 状态更新完全由已有播放器回调、格式解析与状态流触发。
 * Logcat 仅使用标签 `NeriPower`，且仅在关键指标发生实质变化时打印单行日志。
 */
object LowPowerDiagnostics {
    private const val TAG = "NeriPower"

    private val _snapshotFlow = MutableStateFlow(LowPowerDiagnosticsSnapshot())
    val snapshotFlow: StateFlow<LowPowerDiagnosticsSnapshot> = _snapshotFlow.asStateFlow()

    @Volatile
    private var lastLoggedFingerprint: String = ""

    fun current(): LowPowerDiagnosticsSnapshot = _snapshotFlow.value

    @Synchronized
    fun update(transform: (LowPowerDiagnosticsSnapshot) -> LowPowerDiagnosticsSnapshot) {
        val previous = _snapshotFlow.value
        val updated = transform(previous).copy(updatedAtMs = System.currentTimeMillis())
        if (previous == updated) return
        _snapshotFlow.value = updated

        // 仅在核心状态发生变化时向 Logcat 输出单行，严禁周期性刷屏
        val currentFingerprint = buildFingerprint(updated)
        if (currentFingerprint != lastLoggedFingerprint) {
            lastLoggedFingerprint = currentFingerprint
            Log.i(
                TAG,
                "StateChanged: source=${updated.source} mime=${updated.mimeType} " +
                    "codec=${updated.codec} rate=${updated.sampleRateHz ?: "-"} " +
                    "ch=${updated.channelCount ?: "-"} speed=${updated.playbackSpeed}x " +
                    "offloadRequested=${updated.offloadRequested} " +
                    "offloadEligible=${updated.offloadEligible} " +
                    "offloadedPlayback=${updated.isOffloadedPlayback} " +
                    "sleepingForOffload=${updated.isSleepingForOffload} " +
                    "decoder=${updated.audioDecoderName} effects=${updated.hasEqualizerOrEffects} " +
                    "classical=${updated.classicalLowPowerEnabled} reason=${updated.lastOffloadUpdateReason}"
            )
        }
    }

    private fun buildFingerprint(s: LowPowerDiagnosticsSnapshot): String {
        return "${s.source}|${s.mimeType}|${s.codec}|${s.bitrateKbps}|${s.sampleRateHz}|" +
            "${s.channelCount}|${s.playbackSpeed}|${s.hasEqualizerOrEffects}|${s.audioDecoderName}|" +
            "${s.offloadRequested}|${s.offloadEligible}|${s.isOffloadedPlayback}|" +
            "${s.isSleepingForOffload}|${s.lastOffloadUpdateReason}"
    }

    fun onAudioInputFormat(
        mimeType: String?,
        codecs: String?,
        sampleRate: Int,
        channelCount: Int,
        bitrate: Int
    ) {
        update { prev ->
            prev.copy(
                mimeType = mimeType ?: prev.mimeType,
                codec = codecs ?: prev.codec,
                sampleRateHz = if (sampleRate > 0) sampleRate else prev.sampleRateHz,
                channelCount = if (channelCount > 0) channelCount else prev.channelCount,
                bitrateKbps = if (bitrate > 0) bitrate / 1000 else prev.bitrateKbps
            )
        }
    }

    fun onAudioDecoderInitialized(decoderName: String) {
        update { prev -> prev.copy(audioDecoderName = decoderName) }
    }

    fun onOffloadedPlaybackChanged(active: Boolean) {
        update { prev -> prev.copy(isOffloadedPlayback = active) }
    }

    fun onSleepingForOffloadChanged(sleeping: Boolean) {
        update { prev -> prev.copy(isSleepingForOffload = sleeping) }
    }

    fun onOffloadPreferencesUpdated(
        offloadRequested: Boolean,
        offloadEligible: Boolean,
        reason: String
    ) {
        update { prev ->
            prev.copy(
                offloadRequested = offloadRequested,
                offloadEligible = offloadEligible,
                lastOffloadUpdateReason = reason
            )
        }
    }

    fun onSourceInfoChanged(
        source: String,
        mimeType: String?,
        codec: String?,
        sampleRateHz: Int?,
        bitrateKbps: Int?,
        channelCount: Int?
    ) {
        update { prev ->
            prev.copy(
                source = source,
                mimeType = mimeType ?: prev.mimeType,
                codec = codec ?: prev.codec,
                sampleRateHz = sampleRateHz ?: prev.sampleRateHz,
                bitrateKbps = bitrateKbps ?: prev.bitrateKbps,
                channelCount = channelCount ?: prev.channelCount
            )
        }
    }

    fun onSoundConfigChanged(
        speed: Float,
        hasEffects: Boolean
    ) {
        update { prev ->
            prev.copy(
                playbackSpeed = speed,
                hasEqualizerOrEffects = hasEffects
            )
        }
    }
}
