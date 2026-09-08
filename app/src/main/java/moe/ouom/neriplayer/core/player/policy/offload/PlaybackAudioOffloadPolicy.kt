package moe.ouom.neriplayer.core.player.policy.offload

import moe.ouom.neriplayer.core.classical.ClassicalLowPower
import moe.ouom.neriplayer.core.player.model.PlaybackAudioSource
import kotlin.math.abs

private const val PLAYBACK_PARAMETER_EPSILON = 0.001f

internal fun requiresPcmAudioProcessing(
    usbExclusivePlaybackEnabled: Boolean,
    playbackSpeed: Float,
    playbackPitch: Float,
    equalizerEnabled: Boolean,
    loudnessGainMb: Int,
    volumeBalance: Float,
    volumeNormalizationEnabled: Boolean,
    highResolutionOutputEnabled: Boolean,
    audioReactiveActive: Boolean,
    audioSource: PlaybackAudioSource?,
    listenTogetherPlaybackRate: Float,
    preferClassicalOffload: Boolean = ClassicalLowPower.enabled,
): Boolean {
    // Upstream note: 网易云直链和 B 站换源都容易触发系统 offload 残留缓冲，主动走 PCM 管线。
    // 在 Classical Low Power 模式下，针对 Bilibili 开放 offload 首选，在满足单倍速、无 EQ 与音效时
    // 允许进入 Android Audio Offload / DSP 路径省电。
    val sourceRequiresPcm = if (preferClassicalOffload) {
        audioSource == PlaybackAudioSource.NETEASE
    } else {
        audioSource == PlaybackAudioSource.NETEASE ||
            audioSource == PlaybackAudioSource.BILIBILI
    }

    return sourceRequiresPcm ||
        usbExclusivePlaybackEnabled ||
        abs(playbackSpeed - 1f) > PLAYBACK_PARAMETER_EPSILON ||
        abs(playbackPitch - 1f) > PLAYBACK_PARAMETER_EPSILON ||
        equalizerEnabled ||
        loudnessGainMb != 0 ||
        abs(volumeBalance) > PLAYBACK_PARAMETER_EPSILON ||
        volumeNormalizationEnabled ||
        highResolutionOutputEnabled ||
        audioReactiveActive ||
        abs(listenTogetherPlaybackRate - 1f) > PLAYBACK_PARAMETER_EPSILON
}

internal fun shouldUpdateAudioOffloadForReactiveChange(
    audioReactiveEnabled: Boolean,
    playbackActive: Boolean,
): Boolean {
    return audioReactiveEnabled || !playbackActive
}
