package moe.ouom.neriplayer.ui.screen.source

/**
 * Timestamp links inside Bilibili comments, e.g. "12:34" or "1:02:15".
 * Pure parsing: projection only, never a filter — every normal comment renders.
 */
data class TimestampLink(
    /** Start offset (inclusive) in the original message. */
    val start: Int,
    /** End offset (exclusive) in the original message. */
    val endExclusive: Int,
    val positionMs: Long
)

private val TimestampPattern = Regex("""(?<!\d)(?:(\d+):)?([0-5]?\d):([0-5]\d)(?!\d)""")

/** Max supported timestamp: 24h. Longer hour parts are treated as plain text. */
private const val MAX_HOURS = 24L

fun parseTimestampLinks(message: String): List<TimestampLink> {
    if (message.isEmpty()) return emptyList()
    val out = ArrayList<TimestampLink>(2)
    for (match in TimestampPattern.findAll(message)) {
        val hours = match.groups[1]?.value?.toLongOrNull() ?: 0L
        val minutes = match.groups[2]?.value?.toLongOrNull() ?: continue
        val seconds = match.groups[3]?.value?.toLongOrNull() ?: continue
        if (hours > MAX_HOURS) continue
        val totalSec = hours * 3600L + minutes * 60L + seconds
        out += TimestampLink(
            start = match.range.first,
            endExclusive = match.range.last + 1,
            positionMs = totalSec * 1000L
        )
    }
    return out
}
