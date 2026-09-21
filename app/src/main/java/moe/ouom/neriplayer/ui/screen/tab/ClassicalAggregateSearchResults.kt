package moe.ouom.neriplayer.ui.screen.tab

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.ouom.neriplayer.core.api.youtube.YouTubeMusicCreatorSummary
import moe.ouom.neriplayer.data.model.SongItem
import moe.ouom.neriplayer.data.model.displayArtist
import moe.ouom.neriplayer.data.model.displayName
import moe.ouom.neriplayer.data.model.stableKey
import moe.ouom.neriplayer.ui.viewmodel.tab.AggregateProviderState
import moe.ouom.neriplayer.ui.viewmodel.tab.AggregateSearchState
import moe.ouom.neriplayer.ui.viewmodel.tab.ExploreSearchResult

/**
 * Classical 聚合搜索结果（Subagent 04 合约）：
 * 按 provider 分组展示（Bilibili / YouTube），各自独立分页与错误态；
 * 不做跨平台 relevance 混排；去重仅限确定性 source 身份（VM 层已做）。
 */
@Composable
fun ClassicalAggregateSearchResults(
    aggregate: AggregateSearchState,
    youtubeAvailable: Boolean,
    onLoadMoreBili: () -> Unit,
    onSongTap: (SongItem, List<SongItem>, Int) -> Unit,
    onPlaySong: (SongItem) -> Unit,
    onYouTubeCreatorClick: (YouTubeMusicCreatorSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        item(key = "agg-bili-header") {
            Text(
                text = "Bilibili",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        aggregateProviderRows(
            keyPrefix = "bili",
            state = aggregate.bili,
            emptyText = "Bilibili 暂无结果",
            onLoadMore = onLoadMoreBili,
            onSongTap = onSongTap,
            onPlaySong = onPlaySong,
            onYouTubeCreatorClick = onYouTubeCreatorClick,
        )
        if (youtubeAvailable) {
            item(key = "agg-yt-header") {
                Text(
                    text = "YouTube",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            aggregateProviderRows(
                keyPrefix = "yt",
                state = aggregate.youtube,
                emptyText = "YouTube 暂无结果",
                onLoadMore = {},
                canLoadMore = false,
                onSongTap = onSongTap,
                onPlaySong = onPlaySong,
                onYouTubeCreatorClick = onYouTubeCreatorClick,
            )
        }
    }
}

private fun LazyListScope.aggregateProviderRows(
    keyPrefix: String,
    state: AggregateProviderState,
    emptyText: String,
    onLoadMore: () -> Unit,
    onSongTap: (SongItem, List<SongItem>, Int) -> Unit,
    onPlaySong: (SongItem) -> Unit,
    onYouTubeCreatorClick: (YouTubeMusicCreatorSummary) -> Unit,
    canLoadMore: Boolean = true,
) {
    val songs = state.items.mapNotNull { (it as? ExploreSearchResult.Song)?.song }
    if (!state.loading && state.items.isEmpty() && state.error == null) {
        item(key = "${keyPrefix}-empty") {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
    itemsIndexed(
        items = state.items,
        key = { _, item -> "$keyPrefix:${item.stableKey}" }
    ) { index, item ->
        when (item) {
            is ExploreSearchResult.Song -> {
                val song = item.song
                val songIndex = songs.indexOfFirst { it.stableKey() == song.stableKey() }
                    .takeIf { it >= 0 } ?: index
                ListItem(
                    headlineContent = {
                        Text(
                            text = song.displayName(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    supportingContent = {
                        Text(
                            text = song.displayArtist(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    trailingContent = {
                        IconButton(onClick = { onPlaySong(song) }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        }
                    },
                    modifier = Modifier.clickable {
                        onSongTap(song, songs, songIndex)
                    }
                )
            }
            is ExploreSearchResult.YouTubeCreator -> {
                ListItem(
                    headlineContent = {
                        Text(
                            text = item.creator.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    supportingContent = {
                        Text(
                            text = item.creator.subtitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier.clickable { onYouTubeCreatorClick(item.creator) }
                )
            }
            else -> Unit
        }
    }
    if (state.loading) {
        item(key = "${keyPrefix}-loading") {
            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    } else if (state.error != null) {
        val error = state.error
        item(key = "${keyPrefix}-error") {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(text = error, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onLoadMore) { Text("重试") }
            }
        }
    } else if (canLoadMore && state.hasMore) {
        item(key = "${keyPrefix}-more") {
            Button(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) {
                Text("加载更多")
            }
        }
    }
}

/** Classical 干净空态：搜当前源 + 历史（上游已渲染）+ 聚合 hint，不含网易推荐。 */
@Composable
fun ClassicalEmptySearchState(
    aggregateEnabled: Boolean,
    onEnableAggregate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "搜索 Bilibili / 当前源",
                style = MaterialTheme.typography.bodyLarge
            )
            if (!aggregateEnabled) {
                TextButton(onClick = onEnableAggregate) {
                    Text("试试聚合搜索（B站 + YouTube）")
                }
            }
        }
    }
}
