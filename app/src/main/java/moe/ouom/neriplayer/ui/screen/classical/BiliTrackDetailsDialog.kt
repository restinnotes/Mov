package moe.ouom.neriplayer.ui.screen.classical

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import moe.ouom.neriplayer.R
import moe.ouom.neriplayer.core.api.bili.biliBvidOrNull
import moe.ouom.neriplayer.core.api.bili.biliCidOrNull
import moe.ouom.neriplayer.data.model.SongItem
import moe.ouom.neriplayer.data.model.displayArtist
import moe.ouom.neriplayer.data.model.displayName
import moe.ouom.neriplayer.ui.viewmodel.source.ClassicalSourceViewModel
import moe.ouom.neriplayer.util.format.formatDuration

@Composable
internal fun BiliTrackDetailsDialog(song: SongItem, source: ClassicalSourceViewModel, onDismiss: () -> Unit) {
    val state by source.uiState.collectAsStateWithLifecycle()
    val bvid = song.biliBvidOrNull()
    val cid = song.biliCidOrNull()
    val intro = (state.intro as? ClassicalSourceViewModel.IntroState.Ready)?.intro?.takeIf { it.bvid == bvid }
    val uriHandler = LocalUriHandler.current
    val storedTitle = song.album.split('|', limit = 4).getOrNull(3)?.takeIf { it.isNotBlank() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.classical_song_details)) },
        text = {
            SelectionContainer {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    listOf(
                        "作品" to (intro?.title ?: storedTitle ?: song.displayName()),
                        "当前分P" to song.displayName(),
                        "UP 主" to (intro?.uploaderName ?: song.displayArtist()),
                        "时长" to formatDuration(song.durationMs),
                        "来源" to "Bilibili",
                        "BV" to bvid.orEmpty(),
                        "CID" to (cid?.toString().orEmpty())
                    ).filter { it.second.isNotBlank() }.forEach { (label, value) ->
                        Column {
                            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(value, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
        dismissButton = if (bvid == null) null else ({
            TextButton(onClick = { uriHandler.openUri("https://www.bilibili.com/video/$bvid/") }) { Text("在 Bilibili 打开") }
        })
    )
}
