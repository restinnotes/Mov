package moe.ouom.neriplayer.core.player.resolver.bili

import moe.ouom.neriplayer.data.platform.bili.isBiliStreamUrl
import java.io.IOException
import java.net.URI
import java.util.Locale

/** B 站长音频按有界 Range 拉取，短读后由数据源从实际字节偏移继续。 */
internal fun shouldUseBiliResumableRange(url: String): Boolean {
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    if (uri.scheme?.lowercase(Locale.US) !in setOf("http", "https")) return false
    val path = uri.path.orEmpty().lowercase(Locale.US)
    return isBiliStreamUrl(url) &&
        listOf(".m4s", ".m4a", ".mp4", ".flac", ".flv").any(path::endsWith)
}

internal fun isBiliErrorResponseContentType(contentType: String?): Boolean {
    val type = contentType?.substringBefore(';')?.trim()?.lowercase(Locale.US).orEmpty()
    return type.startsWith("text/") || type == "application/json" ||
        type.endsWith("+json") || type == "application/xml" || type.endsWith("+xml")
}

/** 不允许 CDN 把另一段数据写入当前偏移的缓存。200 响应由 OkHttpDataSource 跳过偏移。 */
internal fun validateBiliRangeResponse(
    url: String,
    responseCode: Int,
    responseHeaders: Map<String, List<String>>,
    requestedStartPosition: Long
) {
    if (!shouldUseBiliResumableRange(url) || responseCode != 206) return
    val contentRange = responseHeaders.entries
        .firstOrNull { it.key.equals("Content-Range", ignoreCase = true) }
        ?.value?.firstOrNull()?.trim()
    val match = contentRange?.let(BILI_CONTENT_RANGE_PATTERN::matchEntire)
    val start = match?.groupValues?.get(1)?.toLongOrNull()
    val end = match?.groupValues?.get(2)?.toLongOrNull()
    val totalText = match?.groupValues?.get(3)
    val total = totalText?.toLongOrNull()
    if (start != requestedStartPosition || end == null || end < requestedStartPosition ||
        (totalText != "*" && (total == null || end >= total))
    ) {
        throw IOException(
            "Invalid Bilibili Content-Range: requested=$requestedStartPosition, received=$contentRange"
        )
    }
}

private val BILI_CONTENT_RANGE_PATTERN = Regex(
    "bytes\\s+(\\d+)\\s*-\\s*(\\d+)\\s*/\\s*(\\d+|\\*)",
    RegexOption.IGNORE_CASE
)
