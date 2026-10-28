package moe.ouom.neriplayer.ui.component.classical

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.ouom.neriplayer.R
import moe.ouom.neriplayer.core.api.bili.BiliClient
import moe.ouom.neriplayer.core.api.bili.biliBvidOrNull
import moe.ouom.neriplayer.core.api.bili.biliCidOrNull
import moe.ouom.neriplayer.core.classical.formatClassicalDuration
import moe.ouom.neriplayer.core.classical.queue.buildBiliVideoPartSongs
import moe.ouom.neriplayer.core.classical.queue.sharedBiliVideoInfoCache
import moe.ouom.neriplayer.core.player.PlayerManager
import moe.ouom.neriplayer.data.model.SongItem
import moe.ouom.neriplayer.data.model.displayName

/** Loads the complete page list independently of which parts happen to be queued. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BiliVideoPartsSheet(song: SongItem, onDismiss: () -> Unit, onPlay: (SongItem) -> Unit) {
    val bvid = song.biliBvidOrNull() ?: return
    var info by remember(bvid) { mutableStateOf<BiliClient.VideoBasicInfo?>(null) }
    var failed by remember(bvid) { mutableStateOf(false) }
    var attempt by remember(bvid) { mutableIntStateOf(0) }
    var query by remember(bvid) { mutableStateOf("") }
    var locateRequest by remember(bvid) { mutableIntStateOf(0) }
    val current by PlayerManager.currentSongFlow.collectAsStateWithLifecycle()
    val currentCid = current?.takeIf { it.biliBvidOrNull() == bvid }?.biliCidOrNull()
    val listState = rememberLazyListState()
    LaunchedEffect(bvid, attempt) {
        failed = false
        try {
            info = sharedBiliVideoInfoCache.get(bvid) ?: withContext(Dispatchers.IO) {
                PlayerManager.biliClient.getVideoBasicInfoByBvid(bvid)
            }.also { sharedBiliVideoInfoCache.put(bvid, it) }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) { failed = true }
    }
    val parts = remember(info, song.coverUrl) { info?.let { buildBiliVideoPartSongs(it, song.coverUrl.orEmpty()) }.orEmpty() }
    val visibleParts = remember(parts, query) {
        parts.mapIndexed { index, part -> index to part }.filter { (index, part) ->
            query.isBlank() || part.displayName().contains(query, ignoreCase = true) ||
                (index + 1).toString() == query.trim().removePrefix("P").removePrefix("p")
        }
    }
    LaunchedEffect(info, locateRequest) {
        if (info != null && query.isBlank()) {
            val index = parts.indexOfFirst { it.biliCidOrNull() == (currentCid ?: song.biliCidOrNull()) }
            if (index >= 0) listState.scrollToItem(index)
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(stringResource(R.string.classical_all_parts), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp))
            Text(info?.title ?: song.displayName(), style = MaterialTheme.typography.bodyMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            if (info != null) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(query, { query = it }, modifier = Modifier.weight(1f), singleLine = true,
                        placeholder = { Text(stringResource(R.string.classical_parts_search)) })
                    if (currentCid != null) TextButton(onClick = { query = ""; locateRequest++ }) {
                        Text(stringResource(R.string.classical_parts_locate))
                    }
                }
                LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                    itemsIndexed(visibleParts, key = { _, item -> item.second.biliCidOrNull() ?: item.first }) { _, (index, part) ->
                        val selected = part.biliCidOrNull() == currentCid
                        ListItem(
                            headlineContent = { Text(part.displayName(), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            leadingContent = { Text("P${index + 1}", color = MaterialTheme.colorScheme.primary) },
                            trailingContent = { Text(formatClassicalDuration(part.durationMs), style = MaterialTheme.typography.bodySmall) },
                            colors = ListItemDefaults.colors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
                            modifier = Modifier.clickable { onPlay(part) }
                        )
                    }
                    if (visibleParts.isEmpty()) item {
                        Text(stringResource(R.string.classical_parts_empty), modifier = Modifier.padding(24.dp))
                    }
                }
            } else if (failed) {
                Text(stringResource(R.string.classical_parts_failed), modifier = Modifier.padding(20.dp))
                TextButton(onClick = { attempt++ }, modifier = Modifier.padding(horizontal = 12.dp)) {
                    Text(stringResource(R.string.classical_parts_retry))
                }
            } else {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }
    }
}
