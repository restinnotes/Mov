package moe.ouom.neriplayer.ui.screen.source

import moe.ouom.neriplayer.core.api.bili.BiliClient

/**
 * 富文本评论分段：纯文本 / 行内表情 / 可点击时间戳。
 * 表情优先切分（Bili 占位如 [doge]），文本段内再识别时间戳。
 * 纯投影函数：只决定渲染，不过滤任何评论。
 */
sealed interface RichCommentSegment {
    data class Text(val text: String) : RichCommentSegment
    data class Emote(val key: String, val url: String, val large: Boolean) : RichCommentSegment
    data class TimeLink(val text: String, val positionMs: Long) : RichCommentSegment
}

fun parseRichCommentSegments(
    message: String,
    emotes: Map<String, BiliClient.BiliCommentEmote>
): List<RichCommentSegment> {
    if (message.isEmpty()) return emptyList()
    if (emotes.isEmpty()) {
        return splitTimestamps(message, 0).ifEmpty { listOf(RichCommentSegment.Text(message)) }
    }
    val keys = emotes.keys.sortedByDescending { it.length }
    val pattern = Regex(keys.joinToString("|", "(", ")") { Regex.escape(it) })
    val out = ArrayList<RichCommentSegment>(4)
    var cursor = 0
    for (match in pattern.findAll(message)) {
        if (match.range.first > cursor) {
            out += splitTimestamps(message.substring(cursor, match.range.first), cursor)
        }
        val emote = emotes[match.value]
        if (emote != null) {
            out += RichCommentSegment.Emote(
                key = match.value,
                url = emote.url,
                large = emote.size == 2
            )
        } else {
            out += splitTimestamps(match.value, match.range.first)
        }
        cursor = match.range.last + 1
    }
    if (cursor < message.length) {
        out += splitTimestamps(message.substring(cursor), cursor)
    }
    return out.ifEmpty { listOf(RichCommentSegment.Text(message)) }
}

private fun splitTimestamps(chunk: String, baseOffset: Int): List<RichCommentSegment> {
    if (chunk.isEmpty()) return emptyList()
    val links = parseTimestampLinks(chunk)
    if (links.isEmpty()) return listOf(RichCommentSegment.Text(chunk))
    val out = ArrayList<RichCommentSegment>(links.size * 2 + 1)
    var cursor = 0
    for (link in links) {
        if (link.start > cursor) {
            out += RichCommentSegment.Text(chunk.substring(cursor, link.start))
        }
        out += RichCommentSegment.TimeLink(
            text = chunk.substring(link.start, link.endExclusive),
            positionMs = link.positionMs
        )
        cursor = link.endExclusive
    }
    if (cursor < chunk.length) {
        out += RichCommentSegment.Text(chunk.substring(cursor))
    }
    return out
}

/** Bili 风格相对时间：x秒/分钟/小时前、昨天、yyyy-MM-dd。纯函数。 */
fun formatCommentRelativeTime(epochMs: Long, nowMs: Long): String {
    if (epochMs <= 0L) return ""
    val diff = nowMs - epochMs
    if (diff < 0L) return ""
    val minutes = diff / 60_000L
    if (minutes < 1L) return "刚刚"
    if (minutes < 60L) return "${minutes}分钟前"
    val hours = minutes / 60L
    if (hours < 24L) return "${hours}小时前"
    if (hours < 48L) return "昨天"
    return runCatching {
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date(epochMs))
    }.getOrDefault("")
}
