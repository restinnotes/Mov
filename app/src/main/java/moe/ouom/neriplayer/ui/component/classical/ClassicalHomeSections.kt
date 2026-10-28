package moe.ouom.neriplayer.ui.component.classical

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import moe.ouom.neriplayer.R
import moe.ouom.neriplayer.core.api.bili.biliBvidOrNull
import moe.ouom.neriplayer.core.api.bili.biliVideoTitleOrNull
import moe.ouom.neriplayer.core.classical.queue.sharedBiliVideoInfoCache
import moe.ouom.neriplayer.data.local.playlist.model.LocalPlaylist
import moe.ouom.neriplayer.data.local.playlist.system.SystemLocalPlaylists
import moe.ouom.neriplayer.data.model.SongItem
import moe.ouom.neriplayer.data.model.displayArtist
import moe.ouom.neriplayer.data.model.displayName
import moe.ouom.neriplayer.data.model.stableKey
import moe.ouom.neriplayer.ui.util.rememberPlaylistDisplayCoverUrl
import moe.ouom.neriplayer.ui.util.rememberSongDisplayCoverUrl
import moe.ouom.neriplayer.util.media.fastScrollableImageRequest

@Composable
internal fun ClassicalRecentShelf(
    songs: List<SongItem>,
    offlineMode: Boolean,
    onPlay: (SongItem) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(songs.take(12), key = { it.stableKey() }) { song ->
            val bvid = song.biliBvidOrNull()
            // Only display an exact part count when the video metadata is known.
            val partCount = bvid?.let { sharedBiliVideoInfoCache.get(it)?.pages?.size }
            ClassicalShelfCard(
                title = song.biliVideoTitleOrNull()?.takeIf { it.isNotBlank() } ?: song.displayName(),
                subtitle = song.displayArtist(),
                coverUrl = rememberSongDisplayCoverUrl(song),
                badge = partCount?.takeIf { it > 1 }?.let { "${it}P" },
                offlineMode = offlineMode,
                onClick = { onPlay(song) }
            )
        }
    }
}

@Composable
internal fun ClassicalPlaylistShelf(
    playlists: List<LocalPlaylist>,
    offlineMode: Boolean,
    onOpen: (Long) -> Unit
) {
    val context = LocalContext.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(playlists.take(12), key = { it.id }) { playlist ->
            val name = remember(playlist.id, playlist.name, context) {
                SystemLocalPlaylists.resolve(playlist.id, playlist.name, context)?.currentName ?: playlist.name
            }
            ClassicalShelfCard(
                title = name,
                subtitle = pluralStringResource(R.plurals.home_song_count_format, playlist.songs.size, playlist.songs.size),
                coverUrl = rememberPlaylistDisplayCoverUrl(playlist),
                offlineMode = offlineMode,
                playlist = true,
                onClick = { onOpen(playlist.id) }
            )
        }
    }
}

@Composable
private fun ClassicalShelfCard(
    title: String,
    subtitle: String,
    coverUrl: String?,
    offlineMode: Boolean,
    badge: String? = null,
    playlist: Boolean = false,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.width(111.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.size(111.dp).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (playlist) Icons.AutoMirrored.Outlined.PlaylistPlay else Icons.Outlined.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            if (!coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = fastScrollableImageRequest(context, coverUrl, sizePx = 384, offlineMode = offlineMode),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (badge != null) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
    }
}
