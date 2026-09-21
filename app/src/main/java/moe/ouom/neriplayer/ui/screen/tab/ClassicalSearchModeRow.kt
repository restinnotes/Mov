package moe.ouom.neriplayer.ui.screen.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import moe.ouom.neriplayer.data.search.ClassicalDefaultSearchMode

/**
 * Classical 搜索模式行（Subagent 04）：
 * 聚合开关 + 默认搜索（B站/聚合/YouTube）选择。用户可改，持久化由 VM 负责。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClassicalSearchModeRow(
    aggregateEnabled: Boolean,
    onAggregateChange: (Boolean) -> Unit,
    defaultMode: ClassicalDefaultSearchMode,
    onDefaultModeChange: (ClassicalDefaultSearchMode) -> Unit,
    youtubeAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FilterChip(
            selected = aggregateEnabled,
            onClick = { onAggregateChange(!aggregateEnabled) },
            label = { Text("聚合") },
        )
        FilterChip(
            selected = defaultMode == ClassicalDefaultSearchMode.BILIBILI,
            onClick = { onDefaultModeChange(ClassicalDefaultSearchMode.BILIBILI) },
            label = { Text("默认：B站") },
        )
        FilterChip(
            selected = defaultMode == ClassicalDefaultSearchMode.AGGREGATE,
            onClick = { onDefaultModeChange(ClassicalDefaultSearchMode.AGGREGATE) },
            label = { Text("默认：聚合") },
        )
        if (youtubeAvailable) {
            FilterChip(
                selected = defaultMode == ClassicalDefaultSearchMode.YOUTUBE,
                onClick = { onDefaultModeChange(ClassicalDefaultSearchMode.YOUTUBE) },
                label = { Text("默认：YouTube") },
            )
        }
    }
}
