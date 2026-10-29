package moe.ouom.neriplayer.ui.screen.classical

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import moe.ouom.neriplayer.R

@Composable
internal fun ClassicalPlaybackPager(
    onNavigateUp: () -> Unit,
    onOpenTools: () -> Unit,
    source: @Composable (returnToPlayer: () -> Unit) -> Unit,
    queue: @Composable (returnToPlayer: () -> Unit) -> Unit,
    player: @Composable () -> Unit
) {
    val pager = rememberPagerState(initialPage = 1, pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val returnToPlayer = { scope.launch { pager.animateScrollToPage(1) }; Unit }
    BackHandler(enabled = pager.currentPage != 1) { returnToPlayer() }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)
        .windowInsetsPadding(WindowInsets.navigationBars)) {
        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onNavigateUp) {
                Icon(Icons.Outlined.KeyboardArrowDown, stringResource(R.string.cd_back))
            }
            val titles = listOf(R.string.classical_page_source, R.string.classical_page_player, R.string.classical_page_queue)
            titles.forEachIndexed { index, title ->
                TextButton(onClick = { scope.launch { pager.animateScrollToPage(index) } }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(title), color = if (pager.currentPage == index)
                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onOpenTools) {
                Icon(Icons.Filled.MoreVert, stringResource(R.string.classical_playback_tools))
            }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth(), key = { it }) { page ->
            // Dispose idle side pages so their selection/reply BackHandlers cannot
            // intercept Back while the user is on the player page.
            if (page == pager.currentPage || pager.isScrollInProgress) {
                when (page) {
                    0 -> source(returnToPlayer)
                    1 -> player()
                    2 -> queue(returnToPlayer)
                }
            }
        }
    }
}
