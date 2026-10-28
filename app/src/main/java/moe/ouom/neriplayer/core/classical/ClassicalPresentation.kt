package moe.ouom.neriplayer.core.classical

import java.util.Locale
import moe.ouom.neriplayer.core.api.bili.biliBvidOrNull
import moe.ouom.neriplayer.data.model.SongItem
import moe.ouom.neriplayer.data.model.stableKey

/** Keep the most recently played part of each work, in history order. */
internal fun classicalRecentWorks(songs: List<SongItem>): List<SongItem> =
    songs.distinctBy { song -> song.biliBvidOrNull() ?: song.stableKey() }

internal fun formatClassicalDuration(durationMs: Long): String {
    val seconds = durationMs.coerceAtLeast(0L) / 1000
    return if (seconds >= 3600) {
        String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    } else {
        String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
    }
}
