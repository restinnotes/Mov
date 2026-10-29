package moe.ouom.neriplayer.ui.screen.classical

import androidx.compose.runtime.Composable
import moe.ouom.neriplayer.ui.screen.source.ClassicalCommentsScreen
import moe.ouom.neriplayer.ui.viewmodel.source.ClassicalSourceViewModel

@Composable
internal fun ClassicalSourcePane(
    viewModel: ClassicalSourceViewModel,
    onSeekTo: (Long) -> Unit,
    onReturnToPlayer: () -> Unit
) {
    ClassicalCommentsScreen(viewModel, onSeekTo, onReturnToPlayer, embedded = true)
}
