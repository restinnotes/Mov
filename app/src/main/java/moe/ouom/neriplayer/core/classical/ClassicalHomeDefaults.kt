package moe.ouom.neriplayer.core.classical

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.first
import moe.ouom.neriplayer.data.settings.SettingsRepository

/**
 * Classical Home 一次性默认值。
 *
 * - 仅 classical flavor：新安装默认关闭普通 Trending/Radar/Recommendation 卡片
 *   （雷达歌单、私人雷达、每日推荐等网易推荐流），保留 Continue/Recent 等本地导向内容；
 * - 只做一次、只降级 schema 默认（三个全 true 时才写 false），用户在设置页可随时再开；
 * - official flavor 不触碰。
 */
object ClassicalHomeDefaults {
    private const val PREFS = "classical_home_defaults"
    private const val KEY_APPLIED = "applied_v1"

    suspend fun applyIfNeeded(context: Context, settings: SettingsRepository) {
        if (!ClassicalLowPower.enabled) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_APPLIED, false)) return
        val trending = runCatching { settings.homeCardTrendingFlow.first() }.getOrDefault(true)
        val radar = runCatching { settings.homeCardRadarFlow.first() }.getOrDefault(true)
        val recommended = runCatching { settings.homeCardRecommendedFlow.first() }.getOrDefault(true)
        // 仅当三者仍是 schema 默认（全 true）时降级；用户改过任意一项就不动。
        if (trending && radar && recommended) {
            runCatching { settings.setHomeCardTrending(false) }
            runCatching { settings.setHomeCardRadar(false) }
            runCatching { settings.setHomeCardRecommended(false) }
        }
        prefs.edit { putBoolean(KEY_APPLIED, true) }
    }
}
