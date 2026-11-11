package moe.ouom.neriplayer.ui.component.playback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.ouom.neriplayer.R
import moe.ouom.neriplayer.core.player.PlayerManager
import moe.ouom.neriplayer.core.player.engine.BackgroundSoundConfig
import moe.ouom.neriplayer.ui.component.sheet.bottomSheetScrollGuard
import moe.ouom.neriplayer.ui.haptic.HapticOutlinedButton
import moe.ouom.neriplayer.ui.haptic.HapticTextButton

@Composable
internal fun NoiseMaskingSheet(onDismiss: () -> Unit) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f)
            .bottomSheetScrollGuard { scrollState.value == 0 }
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .windowInsetsPadding(WindowInsets.navigationBars),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(stringResource(R.string.noise_masking_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.noise_masking_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        BackgroundSoundCard()
        HapticOutlinedButton(
            onClick = { PlayerManager.setBackgroundSound(BackgroundSoundConfig()) },
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(stringResource(R.string.noise_masking_reset))
        }
        HapticTextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.action_done))
        }
    }
}
