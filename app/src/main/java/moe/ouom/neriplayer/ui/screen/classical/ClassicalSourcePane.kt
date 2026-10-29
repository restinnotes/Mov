package moe.ouom.neriplayer.ui.screen.classical

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import moe.ouom.neriplayer.R
import moe.ouom.neriplayer.data.model.SongItem
import moe.ouom.neriplayer.ui.screen.PlaybackStatsAction
import moe.ouom.neriplayer.ui.screen.ShareSongAction
import moe.ouom.neriplayer.ui.screen.source.ClassicalCommentsScreen
import moe.ouom.neriplayer.ui.screen.source.SourceIntroContent
import moe.ouom.neriplayer.ui.viewmodel.source.ClassicalSourceViewModel

@Composable
internal fun ClassicalSourcePane(
    viewModel: ClassicalSourceViewModel,
    song: SongItem?,
    queue: List<SongItem>,
    snackbarHostState: SnackbarHostState,
    onDetails: (SongItem) -> Unit,
    onSeekTo: (Long) -> Unit,
    onReturnToPlayer: () -> Unit,
    selectedTab: Int,
    onSelectedTabChange: (Int) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(R.string.classical_source_info, R.string.classical_source_comments).forEachIndexed { index, title ->
                TextButton(onClick = { onSelectedTabChange(index) }, colors = ButtonDefaults.textButtonColors(
                    containerColor = if (selectedTab == index) MaterialTheme.colorScheme.surfaceContainerHigh
                        else androidx.compose.ui.graphics.Color.Transparent,
                    contentColor = if (selectedTab == index) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
                )) { Text(stringResource(title)) }
            }
        }
        if (selectedTab == 1) {
            ClassicalCommentsScreen(viewModel, onSeekTo, onReturnToPlayer, embedded = true, modifier = Modifier.weight(1f))
        } else {
            SourceIntroContent(state.intro, modifier = Modifier.weight(1f))
            if (song != null) {
                TextButton(onClick = { onDetails(song) }, modifier = Modifier.padding(horizontal = 12.dp)) {
                    Text(stringResource(R.string.classical_song_details))
                }
                ShareSongAction(song, queue, snackbarHostState, onDismissSheet = { after -> after() })
                PlaybackStatsAction(song)
            }
        }
    }
}
