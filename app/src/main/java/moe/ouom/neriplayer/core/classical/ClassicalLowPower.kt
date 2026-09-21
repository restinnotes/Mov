package moe.ouom.neriplayer.core.classical

import moe.ouom.neriplayer.BuildConfig

/**
 * Classical low-power fork 的集中 gate。
 *
 * 设计约束（方便以后 rebase upstream）:
 * - 所有 Classical 行为分支都经过这里，不要在业务代码里散落魔法常量；
 * - 默认实现直接读 BuildConfig.CLASSICAL_LOW_POWER（classical flavor 为 true）；
 * - 测试可通过 [overrideForTests] 覆盖，不碰 BuildConfig。
 */
object ClassicalLowPower {
    @Volatile
    private var testOverride: Boolean? = null

    /** Classical flavor 下为 true；官方 flavor 永远为 false。 */
    val enabled: Boolean
        get() = testOverride ?: BuildConfig.CLASSICAL_LOW_POWER

    /** 歌词子系统总开关：Classical 下彻底不运行（不断网、不解析、不 tick）。 */
    val lyricsDisabled: Boolean
        get() = enabled

    /** Lyricon/SuperLyric 独立 200ms feed：Classical 下不启动。 */
    val lyriconDisabled: Boolean
        get() = enabled

    /** 悬浮/状态栏/蓝牙/灵动岛歌词：Classical 下不提供内容。 */
    val externalLyricsDisabled: Boolean
        get() = enabled

    /** 动态背景/Hyper shader：Classical 下不 compose。 */
    val dynamicBackgroundDisabled: Boolean
        get() = enabled

    /** AudioReactive PCM 分流（level/beat）：Classical 下不启用（熄屏零计算）。 */
    val audioReactiveDisabled: Boolean
        get() = enabled

    /** YouTube Music 后台预热/bootstrap：Classical 下默认禁用。 */
    val youTubePrewarmDisabled: Boolean
        get() = enabled

    /** Classical 默认 2GB 流式缓存（仅用于新安装默认值，不改已有用户设置）。 */
    const val DEFAULT_CLASSICAL_CACHE_BYTES = 2L * 1024L * 1024L * 1024L

    /** Upstream 默认 1GB；official flavor 沿用。 */
    const val DEFAULT_UPSTREAM_CACHE_BYTES = 1024L * 1024L * 1024L

    /** 新安装默认值：classical→2GB，official→1GB。已存用户设置不受影响。 */
    fun effectiveDefaultCacheBytes(): Long =
        if (enabled) DEFAULT_CLASSICAL_CACHE_BYTES else DEFAULT_UPSTREAM_CACHE_BYTES

    internal fun overrideForTests(enabled: Boolean?) {
        testOverride = enabled
    }
}
