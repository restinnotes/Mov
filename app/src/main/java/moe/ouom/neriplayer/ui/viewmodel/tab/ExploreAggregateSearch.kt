package moe.ouom.neriplayer.ui.viewmodel.tab

import moe.ouom.neriplayer.data.search.ClassicalDefaultSearchMode

/**
 * Classical 搜索模式（Subagent 04 合约）。
 * Aggregate 不是伪 provider：各 provider 拥有独立分页与加载/错误状态。
 */
sealed interface ExploreSearchMode {
    data class Provider(val source: SearchSource) : ExploreSearchMode
    data object Aggregate : ExploreSearchMode
}

/** 单个 provider 在聚合结果中的独立状态。 */
data class AggregateProviderState(
    val items: List<ExploreSearchResult> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val page: Int = 0,
    val hasMore: Boolean = false
)

data class AggregateSearchState(
    val bili: AggregateProviderState = AggregateProviderState(),
    val youtube: AggregateProviderState = AggregateProviderState(),
    val keyword: String = "",
    val matchQuery: String = "",
    val searching: Boolean = false
)

/**
 * 同一 provider 内确定性去重：仅当 source 身份可证明同一条目时合并
 *（同 BVID / 同 videoId / 同规范 URL → 同 stableKey）。
 * 绝不用 AI 相似度跨平台合并不同录音。
 */
internal fun mergeAggregateProviderItems(
    current: List<ExploreSearchResult>,
    incoming: List<ExploreSearchResult>
): List<ExploreSearchResult> = mergeExploreSearchResults(current, incoming)

/**
 * 初始搜索模式：Classical 干净安装默认 Bilibili；
 * stale YOUTUBE 偏好安全回退 Bilibili；Official 沿用 NETEASE。
 */
internal fun resolveInitialSearchMode(
    classical: Boolean,
    defaultMode: ClassicalDefaultSearchMode,
    youtubeAvailable: Boolean
): ExploreSearchMode {
    if (!classical) return ExploreSearchMode.Provider(SearchSource.NETEASE)
    return when (defaultMode) {
        ClassicalDefaultSearchMode.BILIBILI -> ExploreSearchMode.Provider(SearchSource.BILIBILI)
        ClassicalDefaultSearchMode.AGGREGATE -> ExploreSearchMode.Aggregate
        ClassicalDefaultSearchMode.YOUTUBE ->
            if (youtubeAvailable) {
                ExploreSearchMode.Provider(SearchSource.YOUTUBE_MUSIC)
            } else {
                ExploreSearchMode.Provider(SearchSource.BILIBILI)
            }
    }
}
