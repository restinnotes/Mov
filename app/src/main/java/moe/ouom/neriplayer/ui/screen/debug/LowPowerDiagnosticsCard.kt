package moe.ouom.neriplayer.ui.screen.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EnergySavingsLeaf
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import moe.ouom.neriplayer.core.classical.LowPowerDiagnostics

@Composable
fun LowPowerDiagnosticsCard(modifier: Modifier = Modifier) {
    val snapshot by LowPowerDiagnostics.snapshotFlow.collectAsStateWithLifecycle()

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ListItem(
                leadingContent = {
                    Icon(
                        imageVector = Icons.Outlined.EnergySavingsLeaf,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                headlineContent = {
                    Text(
                        text = "Low Power Diagnostics",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                supportingContent = {
                    Text(
                        text = "Zero-overhead state telemetry (Logcat tag: NeriPower)",
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            DiagnosticRow("Classical Mode", if (snapshot.classicalLowPowerEnabled) "ACTIVE" else "INACTIVE")
            DiagnosticRow("Lyrics Subsystem", if (snapshot.lyricsSubsystemDisabled) "DISABLED (0 CPU)" else "ENABLED")
            DiagnosticRow("Lyricon 200ms Feed", if (snapshot.lyriconDisabled) "DISABLED (0 CPU)" else "ENABLED")
            DiagnosticRow("Waveform Animations", if (snapshot.waveformDisabled) "DISABLED (0 CPU)" else "ENABLED")
            DiagnosticRow("Dynamic Hyper Shader", if (snapshot.dynamicBackgroundDisabled) "DISABLED (0 CPU)" else "ENABLED")

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            DiagnosticRow("Current Source", snapshot.source)
            DiagnosticRow("MIME Type", snapshot.mimeType)
            DiagnosticRow("Codec", snapshot.codec)
            DiagnosticRow("Decoder Name", snapshot.audioDecoderName)
            DiagnosticRow("Sample Rate", snapshot.sampleRateHz?.let { "${it} Hz" } ?: "-")
            DiagnosticRow("Channels", snapshot.channelCount?.toString() ?: "-")
            DiagnosticRow("Bitrate", snapshot.bitrateKbps?.let { "${it} kbps" } ?: "-")
            DiagnosticRow("Playback Speed", "${snapshot.playbackSpeed}x")
            DiagnosticRow("EQ / Audio Effects", if (snapshot.hasEqualizerOrEffects) "ACTIVE (forces PCM)" else "OFF (clean DSP)")

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            DiagnosticRow("Offload Requested", if (snapshot.offloadRequested) "YES (AudioOffloadEnabled)" else "NO (Disabled)")
            DiagnosticRow("Offload Eligible", if (snapshot.offloadEligible) "YES" else "NO (PCM Required)")
            DiagnosticRow("Offloaded Playback", if (snapshot.isOffloadedPlayback) "YES (Hardware DSP)" else "NO / Pending Callback")
            DiagnosticRow("Sleeping for Offload", if (snapshot.isSleepingForOffload) "YES (CPU Sleeping)" else "NO")
            DiagnosticRow("Last Offload Reason", snapshot.lastOffloadUpdateReason)
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
