package moe.ouom.neriplayer.ui.screen.classical

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

@Composable
internal fun ClassicalPlaybackPager(
    visible: Boolean,
    source: @Composable (returnToPlayer: () -> Unit) -> Unit,
    queue: @Composable (returnToPlayer: () -> Unit) -> Unit,
    player: @Composable (openQueue: () -> Unit) -> Unit
) {
    val pager = rememberPagerState(initialPage = 1, pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val returnToPlayer = { scope.launch { pager.animateScrollToPage(1) }; Unit }
    // The overlay can reopen before its exit animation disposes this composition.
    LaunchedEffect(visible) { if (visible) pager.scrollToPage(1) }
    BackHandler(enabled = visible && pager.currentPage != 1) { returnToPlayer() }
    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()
        .windowInsetsPadding(WindowInsets.statusBars)
        .windowInsetsPadding(WindowInsets.navigationBars), key = { it }) { page ->
        // Inactive side pages must not register competing BackHandlers.
        if (page == pager.currentPage || pager.isScrollInProgress) {
            when (page) {
                0 -> source(returnToPlayer)
                1 -> player { scope.launch { pager.animateScrollToPage(2) } }
                2 -> queue(returnToPlayer)
            }
        }
    }
}
