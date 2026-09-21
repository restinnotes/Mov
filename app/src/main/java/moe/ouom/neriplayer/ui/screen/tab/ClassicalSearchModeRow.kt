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
 * Classical 搜索来源三选一：B站 / 聚合 / YouTube。
 * 点选即时生效并持久化为默认来源；同一时刻只存在一套搜索界面
 * （聚合开时整块替换单源 pager，不再有独立的聚合开关）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClassicalSearchModeRow(
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
            selected = defaultMode == ClassicalDefaultSearchMode.BILIBILI,
            onClick = { onDefaultModeChange(ClassicalDefaultSearchMode.BILIBILI) },
            label = { Text("B站") },
        )
        FilterChip(
            selected = defaultMode == ClassicalDefaultSearchMode.AGGREGATE,
            onClick = { onDefaultModeChange(ClassicalDefaultSearchMode.AGGREGATE) },
            label = { Text("聚合") },
        )
        if (youtubeAvailable) {
            FilterChip(
                selected = defaultMode == ClassicalDefaultSearchMode.YOUTUBE,
                onClick = { onDefaultModeChange(ClassicalDefaultSearchMode.YOUTUBE) },
                label = { Text("YouTube") },
            )
        }
    }
}
