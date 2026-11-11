package moe.ouom.neriplayer.ui.component.playback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.ouom.neriplayer.R
import moe.ouom.neriplayer.core.player.PlayerManager
import moe.ouom.neriplayer.core.player.engine.BackgroundSound
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BackgroundSoundCard() {
    val config by PlayerManager.backgroundSoundState.collectAsState()
    val available = PlayerManager.backgroundSoundAvailable()
    Card(colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
    )) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.noise_masking_enable),
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Switch(checked = config.enabled, enabled = available,
                    onCheckedChange = { PlayerManager.setBackgroundSound(config.copy(enabled = it)) })
            }
            Text(
                stringResource(if (available) R.string.background_sound_hint else R.string.background_sound_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BackgroundSound.entries.forEach { sound ->
                    val label = when (sound) {
                        BackgroundSound.PINK -> R.string.background_sound_pink
                        BackgroundSound.BROWN -> R.string.background_sound_brown
                        BackgroundSound.WHITE -> R.string.background_sound_white
                        BackgroundSound.RAIN -> R.string.background_sound_rain
                        BackgroundSound.FAN -> R.string.background_sound_fan
                    }
                    FilterChip(selected = config.sound == sound, enabled = available,
                        onClick = { PlayerManager.setBackgroundSound(config.copy(sound = sound)) },
                        label = { Text(stringResource(label)) })
                }
            }
            Text(stringResource(R.string.background_sound_music_volume, (config.musicVolume * 100).roundToInt()))
            Slider(value = config.musicVolume, enabled = available && config.enabled,
                onValueChange = { PlayerManager.setBackgroundSound(config.copy(musicVolume = it), persist = false) },
                onValueChangeFinished = { PlayerManager.setBackgroundSound(PlayerManager.backgroundSoundState.value) })
            Text(stringResource(R.string.background_sound_volume, (config.backgroundVolume * 100).roundToInt()))
            Slider(value = config.backgroundVolume, enabled = available && config.enabled,
                onValueChange = { PlayerManager.setBackgroundSound(config.copy(backgroundVolume = it), persist = false) },
                onValueChangeFinished = { PlayerManager.setBackgroundSound(PlayerManager.backgroundSoundState.value) })
        }
    }
}
