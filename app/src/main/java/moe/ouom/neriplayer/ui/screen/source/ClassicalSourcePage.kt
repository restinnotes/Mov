package moe.ouom.neriplayer.ui.screen.source

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import moe.ouom.neriplayer.core.api.bili.BiliClient
import moe.ouom.neriplayer.ui.viewmodel.source.ClassicalSourceViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val SEEK_TAG = "SOURCE_SEEK"

/**
 * Classical Source negative-one page: SOURCE <- PLAYER.
 *
 * Exactly two sections behind fixed tabs ([简介][评论]), each vertically
 * scrollable on its own. No nested HorizontalPager, no Timeline/Metadata/AI.
 * Receives no playback position flow; timestamp taps only call [onSeekTo].
 */
@Composable
fun ClassicalSourcePage(
    viewModel: ClassicalSourceViewModel,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(SourceTab.INTRO) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedTab == SourceTab.INTRO,
                onClick = { selectedTab = SourceTab.INTRO },
                label = { Text("简介") }
            )
            FilterChip(
                selected = selectedTab == SourceTab.COMMENTS,
                onClick = { selectedTab = SourceTab.COMMENTS },
                label = {
                    Text(
                        if (state.comments.items.isEmpty()) {
                            "评论"
                        } else {
                            "评论 ${state.comments.items.size}"
                        }
                    )
                }
            )
        }
        when (selectedTab) {
            SourceTab.INTRO -> SourceIntroContent(
                introState = state.intro,
                modifier = Modifier.weight(1f)
            )
            SourceTab.COMMENTS -> SourceCommentsContent(
                state = state,
                onLoadMore = viewModel::loadMore,
                onRetry = viewModel::retry,
                onToggleReplies = viewModel::toggleReplies,
                onLoadMoreReplies = viewModel::loadMoreReplies,
                onSeekTo = onSeekTo,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private enum class SourceTab { INTRO, COMMENTS }

@Composable
private fun SourceIntroContent(
    introState: ClassicalSourceViewModel.IntroState,
    modifier: Modifier = Modifier
) {
    when (introState) {
        ClassicalSourceViewModel.IntroState.Loading -> {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        ClassicalSourceViewModel.IntroState.Empty -> {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "当前歌曲暂无 Bilibili 来源信息",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        ClassicalSourceViewModel.IntroState.Error -> {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "简介加载失败",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        is ClassicalSourceViewModel.IntroState.Ready -> {
            val intro = introState.intro
            val uriHandler = LocalUriHandler.current
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "intro-header") {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        if (!intro.coverUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = intro.coverUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(112.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                            Spacer(Modifier.width(12.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = intro.title,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = intro.uploaderName.ifBlank { "UP 主未知" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (intro.pubdateSec > 0L) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = formatEpochSec(intro.pubdateSec),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                if (intro.desc.isNotBlank()) {
                    item(key = "intro-desc") {
                        SelectionContainer {
                            Text(
                                text = intro.desc,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                item(key = "intro-link") {
                    Text(
                        text = "在 Bilibili 打开",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            runCatching { uriHandler.openUri(intro.sourceUrl) }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceCommentsContent(
    state: ClassicalSourceViewModel.SourceUiState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onToggleReplies: (String) -> Unit,
    onLoadMoreReplies: (String) -> Unit,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val comments = state.comments
    if (state.intro == ClassicalSourceViewModel.IntroState.Empty) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "当前歌曲暂无 Bilibili 评论",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    if (comments.loadState == ClassicalSourceViewModel.CommentsLoadState.INITIAL_LOADING &&
        comments.items.isEmpty()
    ) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (comments.loadState == ClassicalSourceViewModel.CommentsLoadState.ERROR &&
        comments.items.isEmpty()
    ) {
        Box(
            modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "评论加载失败",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = onRetry) { Text("重试") }
            }
        }
        return
    }
    val listState = rememberLazyListState()
    LaunchedEffect(listState, comments.items.size, comments.hasMore) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
        }
            .map { it ?: 0 }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (lastVisible >= comments.items.size - 3 && comments.items.isNotEmpty()) {
                    onLoadMore()
                }
            }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = comments.items,
            key = { it.rpid }
        ) { comment ->
            CommentRow(
                comment = comment,
                thread = state.replies[comment.rpid],
                onToggleReplies = { onToggleReplies(comment.rpid) },
                onLoadMoreReplies = { onLoadMoreReplies(comment.rpid) },
                onSeekTo = onSeekTo
            )
        }
        item(key = "comments-footer") {
            when (comments.loadState) {
                ClassicalSourceViewModel.CommentsLoadState.LOADING_MORE -> {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                }
                ClassicalSourceViewModel.CommentsLoadState.END -> {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (comments.items.isEmpty()) "暂无评论" else "到底了",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                ClassicalSourceViewModel.CommentsLoadState.ERROR -> {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = comments.pageError ?: "加载失败",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(onClick = onRetry) { Text("重试") }
                    }
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: BiliClient.BiliRichComment,
    thread: ClassicalSourceViewModel.ReplyThreadState?,
    onToggleReplies: () -> Unit,
    onLoadMoreReplies: () -> Unit,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            if (comment.avatarUrl.isNotBlank()) {
                AsyncImage(
                    model = comment.avatarUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                )
                Spacer(Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = comment.userName.ifBlank { "匿名" },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (comment.likeCount > 0L) {
                        Text(
                            text = "♥ ${comment.likeCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (comment.ctimeSec > 0L) {
                    Text(
                        text = formatEpochSec(comment.ctimeSec),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(2.dp))
                TimestampCommentText(
                    message = comment.message,
                    onSeekTo = onSeekTo
                )
                if (comment.replyCount > 0) {
                    TextButton(onClick = onToggleReplies) {
                        Text(
                            if (thread == null) {
                                "查看回复(${comment.replyCount})"
                            } else {
                                "收起回复"
                            }
                        )
                    }
                }
            }
        }
        if (thread != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 40.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                thread.items.forEach { reply ->
                    ReplyRow(reply = reply, onSeekTo = onSeekTo)
                }
                when {
                    thread.loading -> {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator(modifier = Modifier.size(20.dp)) }
                    }
                    thread.error != null -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = thread.error!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            TextButton(onClick = onToggleReplies) { Text("重试") }
                        }
                    }
                    thread.hasMore -> {
                        TextButton(onClick = onLoadMoreReplies) { Text("更多回复") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplyRow(
    reply: BiliClient.BiliRichComment,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth()) {
        if (reply.avatarUrl.isNotBlank()) {
            AsyncImage(
                model = reply.avatarUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = reply.userName.ifBlank { "匿名" },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            TimestampCommentText(
                message = reply.message,
                onSeekTo = onSeekTo
            )
        }
    }
}

/** Comment text where mm:ss / h:mm:ss segments are clickable seek links. */
@Composable
private fun TimestampCommentText(
    message: String,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val links = remember(message) { parseTimestampLinks(message) }
    if (links.isEmpty()) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier
        )
        return
    }
    val annotated = remember(message, links) {
        buildAnnotatedString {
            var cursor = 0
            val linkStyle = androidx.compose.ui.text.SpanStyle(
                color = androidx.compose.ui.graphics.Color(0xFF1E88E5)
            )
            for (link in links) {
                if (link.start > cursor) {
                    append(message.substring(cursor, link.start))
                }
                pushStringAnnotation(tag = SEEK_TAG, annotation = link.positionMs.toString())
                withStyle(linkStyle) {
                    append(message.substring(link.start, link.endExclusive))
                }
                pop()
                cursor = link.endExclusive
            }
            if (cursor < message.length) {
                append(message.substring(cursor))
            }
        }
    }
    ClickableText(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier,
        onClick = { offset ->
            annotated.getStringAnnotations(tag = SEEK_TAG, start = offset, end = offset)
                .firstOrNull()
                ?.item
                ?.toLongOrNull()
                ?.let { onSeekTo(it) }
        }
    )
}

private fun formatEpochSec(epochSec: Long): String {
    if (epochSec <= 0L) return ""
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(epochSec * 1000L))
    }.getOrDefault("")
}
