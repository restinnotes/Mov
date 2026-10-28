package moe.ouom.neriplayer.ui.screen.classical

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import moe.ouom.neriplayer.R
import moe.ouom.neriplayer.core.player.PlayerManager
import moe.ouom.neriplayer.ui.component.playback.PlaybackControlIndicator
import moe.ouom.neriplayer.data.model.SongItem
import moe.ouom.neriplayer.data.model.displayArtist
import moe.ouom.neriplayer.data.model.displayName
import moe.ouom.neriplayer.util.format.formatDuration

/**
 * 1:1 BBPlayer 风格竖屏播放页（Classical flavor 竖屏专用）。
 *
 * 结构：
 * - 顶栏：左返回 (v) + 居中 "正在播放" + 右更多 (⋮)
 * - 封面：居中大圆角矩形封面 (RoundedCornerShape 28dp)，无任何角标
 * - 标题栏：左侧大字标题 (加粗) + 作者 (点击进 UP 主)，右侧爱心收藏
 * - 进度条：简洁 Slider + 左右两端时间 ("00:04" / "01:45:52")，无杂乱码率文本
 * - 主控区：上一首、大号圆形边框播放/暂停按钮 (直径 76dp)、下一首
 * - 次控区：随机播放、循环模式、定时关闭、评论气泡、播放队列
 * - 无底部悬浮药丸 dock，无歌词分页
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BBPlayerPlaybackPortraitContent(
    currentSong: SongItem?,
    coverUrl: String?,
    isPlaying: Boolean,
    isPlaybackWaiting: Boolean,
    isAudioRouteMuted: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    playbackDurationMs: Long,
    onSeekTo: (Long) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onTogglePlayPause: () -> Unit,
    shuffleEnabled: Boolean,
    onToggleShuffle: () -> Unit,
    repeatMode: Int,
    onCycleRepeatMode: () -> Unit,
    onOpenComments: () -> Unit,
    onOpenQueue: () -> Unit,
    sleepTimerActive: Boolean,
    onOpenSleepTimer: () -> Unit,
    onOpenMoreOptions: () -> Unit,
    onNavigateUp: () -> Unit,
    onOpenArtist: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val coverSize = (screenWidthDp - 64.dp).coerceIn(240.dp, 360.dp)

    val playbackPositionMs by PlayerManager.playbackPositionFlow.collectAsStateWithLifecycle()
    var draggingProgressFraction by remember { mutableStateOf<Float?>(null) }
    val displayFraction = if (playbackDurationMs > 0L) {
        (draggingProgressFraction ?: (playbackPositionMs.toFloat() / playbackDurationMs))
            .coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. 顶栏：v (左) + 正在播放 (中) + ⋮ (右)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateUp, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = "返回",
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = stringResource(
                    if (isPlaybackWaiting && !isAudioRouteMuted) R.string.player_waiting
                    else R.string.player_now_playing
                ),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 17.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            IconButton(onClick = onOpenMoreOptions, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "更多",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // 2. 封面：大圆角矩形，居中，无任何 badge
        Box(
            modifier = Modifier
                .size(coverSize)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (!coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = currentSong?.displayName().orEmpty(),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // 3. 歌曲标题 + 艺术家 + 爱心收藏 (横排：左标题/作者，右爱心)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = currentSong?.displayName() ?: "未在播放",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = currentSong?.displayArtist() ?: "未知艺术家",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(onClick = onOpenArtist)
                )
            }
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (isFavorite) "取消收藏" else "收藏",
                    tint = if (isFavorite) Color(0xFFE91E63) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // 4. 进度条：简洁 Slider + 两端时间文本 ("00:04" / "01:45:52")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Slider(
                value = displayFraction,
                onValueChange = { fraction -> draggingProgressFraction = fraction },
                onValueChangeFinished = {
                    val targetMs = ((draggingProgressFraction ?: 0f) * playbackDurationMs).toLong()
                    onSeekTo(targetMs)
                    draggingProgressFraction = null
                },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val currentMs = draggingProgressFraction?.let {
                    (it * playbackDurationMs).toLong()
                } ?: playbackPositionMs
                Text(
                    text = formatDuration(currentMs),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatDuration(playbackDurationMs),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // 5. 主控制区：上一首、大号带圆形边框的播放/暂停、下一首
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPrev,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = "上一首",
                    modifier = Modifier.size(34.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            // 大号圆形边框播放按钮 (BBPlayer 招牌样式：粗边框空心环 + 中心三角)
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .border(
                        width = 3.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    )
                    .clip(CircleShape)
                    .clickable(onClick = onTogglePlayPause),
                contentAlignment = Alignment.Center
            ) {
                PlaybackControlIndicator(
                    isPlaying = isPlaying,
                    isPlaybackWaiting = isPlaybackWaiting,
                    isAudioRouteMuted = isAudioRouteMuted,
                    playContentDescription = stringResource(R.string.player_play),
                    pauseContentDescription = stringResource(R.string.player_pause),
                    waitingContentDescription = stringResource(R.string.player_waiting),
                    restoreVolumeContentDescription = stringResource(R.string.player_restore_volume),
                    modifier = Modifier.size(38.dp),
                    color = MaterialTheme.colorScheme.primary,
                    progressIndicatorSize = 38.dp,
                    progressStrokeWidth = 3.dp
                )
            }

            IconButton(
                onClick = onNext,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = "下一首",
                    modifier = Modifier.size(34.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // 6. 次级控制区：随机、循环、定时关闭、评论、队列
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleShuffle, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Shuffle,
                    contentDescription = if (shuffleEnabled) "随机播放已开启" else "随机播放已关闭",
                    modifier = Modifier.size(24.dp),
                    tint = if (shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onCycleRepeatMode, modifier = Modifier.size(40.dp)) {
                val (repeatIcon, isActive) = when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> Icons.Filled.RepeatOne to true
                    Player.REPEAT_MODE_ALL -> Icons.Outlined.Repeat to true
                    else -> Icons.Outlined.Repeat to false
                }
                Icon(
                    imageVector = repeatIcon,
                    contentDescription = "循环模式",
                    modifier = Modifier.size(24.dp),
                    tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onOpenSleepTimer, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Timer,
                    contentDescription = stringResource(
                        if (sleepTimerActive) R.string.sleep_timer_running else R.string.sleep_timer_title
                    ),
                    modifier = Modifier.size(24.dp),
                    tint = if (sleepTimerActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onOpenComments, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = "评论区",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onOpenQueue, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Outlined.FormatListBulleted,
                    contentDescription = "播放队列",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
