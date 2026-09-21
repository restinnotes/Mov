package moe.ouom.neriplayer.util.search

import moe.ouom.neriplayer.core.api.bili.BiliClient

/**
 * 远端平台 (Bilibili) 搜索结果排序器.
 *
 * 与通用 [SearchTextMatcher] 的分工:
 * - [SearchTextMatcher] 保持纯文本匹配语义, 服务本地 playlist / 拼音 / library 搜索, 本文件绝不改它;
 * - 本文件只解决远端搜索特有的问题, 全部基于真实 Bilibili API 实测数据:
 *   1. B 站返回的 `description` / `tag` 经常是唯一包含实体 (如 `Wilhelm Backhaus`) 的字段,
 *      且 `hit_columns` 会明确标出命中位置. 客户端之前只看 title/author, 把 server 前排的
 *      相关视频直接判 null 沉底 (实测: `Backhaus` server #3 -> client #19).
 *      因此仅当 `hit_columns` 标明命中时, 才把 description/tag 纳入匹配 (避免长文本 fuzzy 误伤).
 *   2. 远端排序以 B 站 server 顺序为 baseline: 同分 visible 时用 server index 做 tie-break,
 *      未命中 (null) 的结果保持 server 相对顺序沉底, 不重写 B 站搜索.
 *   3. 音乐区轻微 boost (实测 tids=3 语义, 结论 C): 同等匹配质量下音乐分区靠前,
 *      但绝不做分区过滤 (tids=3 会丢掉 `Tiegerman` 17/20 个音乐区命中, 故不用).
 */
object RemotePlatformSearchRanker {

    private const val TITLE_BIAS = 0
    private const val AUTHOR_BIAS = 24
    private const val TAG_BIAS = 32
    private const val DESCRIPTION_BIAS = 40

    /**
     * 与 [SearchTextMatcher] 内部 `FUZZY_MATCH_SCORE = 96` 对齐的模糊匹配分值下限
     * (该常量为 private, 此处镜像一份只做门限比较, 不参与打分).
     * 长文本 (tag/description, 常为中英混合) 走非 ASCII 宽松 fuzzy 路径时,
     * 跨几十个字符的 subsequence 也能"命中" (实测 `Vegh Quartet` 误吸
     * `Ghost Quartet`: Dave→ve…Ghost→gh, score=206), 必须挡掉.
     */
    private const val FUZZY_SCORE_FLOOR = 96

    /**
     * Bilibili 音乐区 (tids=3) 实测 top 结果中出现的 typeid.
     * 193 (MV) 未在采样中出现, 按语义保留.
     */
    val MUSIC_TYPE_IDS: Set<Int> = setOf(28, 29, 59, 130, 193, 267)

    fun score(query: String, item: BiliClient.SearchVideoItem): Int? {
        val baseValues = listOf(
            SearchTextMatcher.value(item.titlePlain, TITLE_BIAS),
            SearchTextMatcher.value(item.author, AUTHOR_BIAS)
        )
        val base = SearchTextMatcher.score(query, baseValues)
        val hits = item.hitColumns.mapTo(HashSet(item.hitColumns.size)) { it.trim().lowercase() }
        val extended = ArrayList<Any?>(4)
        extended.addAll(baseValues)
        if (item.tag.isNotBlank() && hits.contains("tag")) {
            extended.add(SearchTextMatcher.value(item.tag, TAG_BIAS))
        }
        if (item.description.isNotBlank() && hits.contains("description")) {
            extended.add(SearchTextMatcher.value(item.description, DESCRIPTION_BIAS))
        }
        val score = SearchTextMatcher.score(query, extended) ?: return null
        if (base != null) return score
        // title/author 完全无命中、仅靠 server 命中的 tag/description 兜底时,
        // 只接受强匹配 (exact/prefix/contains), 拒绝 fuzzy 大 gap 噪声.
        return if (score < FUZZY_SCORE_FLOOR + DESCRIPTION_BIAS) score else null
    }

    fun rankBiliVideos(
        query: String,
        items: List<BiliClient.SearchVideoItem>
    ): List<BiliClient.SearchVideoItem> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank() || items.size <= 1) return items
        data class Ranked(val item: BiliClient.SearchVideoItem, val index: Int, val score: Int?)
        val ranked = items.mapIndexed { index, item -> Ranked(item, index, score(normalizedQuery, item)) }
        val matched = ranked.filter { it.score != null }
        if (matched.isEmpty()) return items
        val orderedMatched = matched.sortedWith(
            compareBy<Ranked> { it.score ?: Int.MAX_VALUE }
                .thenBy { if (it.item.typeid in MUSIC_TYPE_IDS) 0 else 1 }
                .thenBy { it.index }
        )
        val unmatched = ranked.filter { it.score == null }.sortedWith(
            compareBy<Ranked> { if (it.item.typeid in MUSIC_TYPE_IDS) 0 else 1 }
                .thenBy { it.index }
        )
        return (orderedMatched + unmatched).map { it.item }
    }
}
