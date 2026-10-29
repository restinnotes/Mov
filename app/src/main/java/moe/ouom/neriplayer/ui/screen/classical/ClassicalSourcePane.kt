package moe.ouom.neriplayer.ui.screen.classical

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
    onReturnToPlayer: () -> Unit
) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        SecondaryTabRow(selectedTabIndex = selected) {
            listOf(R.string.classical_source_info, R.string.classical_source_comments).forEachIndexed { index, title ->
                Tab(selected == index, onClick = { selected = index }, text = { Text(stringResource(title)) })
            }
        }
        if (selected == 1) {
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
