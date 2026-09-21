package moe.ouom.neriplayer.ui.screen.source

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import moe.ouom.neriplayer.core.api.bili.BiliClient
import moe.ouom.neriplayer.ui.viewmodel.source.ClassicalSourceViewModel
import moe.ouom.neriplayer.util.format.formatDuration

private const val SEEK_TAG = "SOURCE_SEEK"

/**
 * 1:1 BBPlayer 风格评论区页面。
 *
 * 结构：
 * - 顶栏：返回箭头 (←) + "评论区" 标题 (粗体 18sp) + [精选] / [最新] 切换
 * - 列表项 (1:1 BBPlayer CommentItem)：
 *   - 左侧 40dp 圆形头像
 *   - 右侧：
 *     - 用户名 (14sp 粗体 secondary) + 相对时间 (刚刚/x分钟前/x小时前/昨天/日期)
 *     - 正文 (15sp, 行高 22sp) + 行内表情 + 可点击时间戳
 *     - 图片九宫格 (100x100，点击弹窗大图预览)
 *     - 操作栏：点赞 (拇指图标 16dp + 点赞数) + 回复 (气泡图标 16dp + 回复数)
 *     - 回复预览气泡 (surfaceVariant 底色，12dp 圆角，8dp 内边距)：
 *       最多展示 3 条 "<粗体作者>: 内容"，超出展示 "查看全部 X 条回复" (primary 强调色)
 *     - 点击展开完整楼中楼回复
 *   - 评论项之间带浅色细分割线 (HorizontalDivider)
 */
@Composable
fun ClassicalCommentsScreen(
    viewModel: ClassicalSourceViewModel,
    onSeekTo: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        // BBPlayer elevated Appbar
        Surface(
            tonalElevation = 2.dp,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "评论区",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    modifier = Modifier.padding(end = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = state.commentSort == ClassicalSourceViewModel.CommentSort.FEATURED,
                        onClick = {
                            viewModel.setCommentSort(ClassicalSourceViewModel.CommentSort.FEATURED)
                        },
                        label = { Text("精选", fontSize = 12.sp) },
                        modifier = Modifier.height(32.dp)
                    )
                    FilterChip(
                        selected = state.commentSort == ClassicalSourceViewModel.CommentSort.LATEST,
                        onClick = {
                            viewModel.setCommentSort(ClassicalSourceViewModel.CommentSort.LATEST)
                        },
                        label = {
                            Text("最新", fontSize = 12.sp)
                        },
                        modifier = Modifier.height(32.dp)
                    )
                }
            }
        }

        var commentInputText by remember { mutableStateOf("") }
        val context = LocalContext.current

        LaunchedEffect(state.sendResultEvent) {
            state.sendResultEvent?.let { msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                viewModel.clearSendResultEvent()
            }
        }

        SourceCommentsContent(
            state = state,
            onSortChange = viewModel::setCommentSort,
            onLoadMore = viewModel::loadMore,
            onRetry = viewModel::retry,
            onToggleReplies = viewModel::toggleReplies,
            onLoadMoreReplies = viewModel::loadMoreReplies,
            onReplyTarget = viewModel::setReplyTarget,
            onSeekTo = onSeekTo,
            modifier = Modifier.weight(1f)
        )

        // 底部评论/回复发送栏
        Surface(
            tonalElevation = 4.dp,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                val target = state.replyTarget
                if (target != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "回复 @${target.targetUserName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "取消",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier
                                .clickable { viewModel.setReplyTarget(null) }
                                .padding(horizontal = 6.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = commentInputText,
                        onValueChange = { commentInputText = it },
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        singleLine = false,
                        maxLines = 4,
                        decorationBox = { innerTextField ->
                            if (commentInputText.isBlank()) {
                                Text(
                                    text = if (target != null) "回复 @${target.targetUserName}..." else "发条友善的评论...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            innerTextField()
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            viewModel.sendComment(commentInputText) {
                                commentInputText = ""
                            }
                        },
                        enabled = commentInputText.isNotBlank() && !state.sending
                    ) {
                        if (state.sending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "发送",
                                tint = if (commentInputText.isNotBlank()) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

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
                                    text = formatCommentRelativeTime(
                                        intro.pubdateSec * 1000L,
                                        System.currentTimeMillis()
                                    ).ifBlank { "" },
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
    onSortChange: (ClassicalSourceViewModel.CommentSort) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onToggleReplies: (String) -> Unit,
    onLoadMoreReplies: (String) -> Unit,
    onReplyTarget: (ClassicalSourceViewModel.ReplyTarget) -> Unit,
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
    val nowMs = remember { System.currentTimeMillis() }
    var viewerUrl by remember { mutableStateOf<String?>(null) }
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
    if (viewerUrl != null) {
        Dialog(
            onDismissRequest = { viewerUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { viewerUrl = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = viewerUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
    // 注意：精选/最新 切换已在顶部 Appbar 右侧提供，此处不再重复渲染第二行 Chip。
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)
    ) {
        items(
            items = comments.items,
            key = { it.rpid }
        ) { comment ->
            Column {
                CommentRow(
                    comment = comment,
                    nowMs = nowMs,
                    thread = state.replies[comment.rpid],
                    onToggleReplies = { onToggleReplies(comment.rpid) },
                    onLoadMoreReplies = { onLoadMoreReplies(comment.rpid) },
                    onReplyTarget = onReplyTarget,
                    onSeekTo = onSeekTo,
                    onPictureClick = { viewerUrl = it }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )
            }
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
    nowMs: Long,
    thread: ClassicalSourceViewModel.ReplyThreadState?,
    onToggleReplies: () -> Unit,
    onLoadMoreReplies: () -> Unit,
    onReplyTarget: (ClassicalSourceViewModel.ReplyTarget) -> Unit,
    onSeekTo: (Long) -> Unit,
    onPictureClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            if (comment.avatarUrl.isNotBlank()) {
                AsyncImage(
                    model = comment.avatarUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = comment.userName.ifBlank { "匿名" },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        ),
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = formatCommentRelativeTime(comment.ctimeSec * 1000L, nowMs),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Spacer(Modifier.height(4.dp))
                RichCommentText(
                    message = comment.message,
                    emotes = comment.emotes,
                    onSeekTo = onSeekTo
                )
                if (comment.pictures.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    CommentPictureGrid(
                        pictures = comment.pictures,
                        onPictureClick = onPictureClick
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val liked = comment.liked
                    Icon(
                        imageVector = if (liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                        contentDescription = null,
                        tint = if (liked) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (comment.likeCount > 0L) {
                            comment.likeCount.toString()
                        } else {
                            "点赞"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    if (comment.replyCount > 0) {
                        Spacer(Modifier.width(16.dp))
                        TextButton(onClick = onToggleReplies) {
                            Icon(
                                imageVector = Icons.Outlined.ChatBubbleOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = comment.replyCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "回复",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier
                            .clickable {
                                onReplyTarget(
                                    ClassicalSourceViewModel.ReplyTarget(
                                        rootRpid = comment.rpid,
                                        parentRpid = comment.rpid,
                                        targetUserName = comment.userName.ifBlank { "匿名" }
                                    )
                                )
                            }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
        // 内嵌前排回复预览（列表 payload 自带，无需额外请求）。
        if (comment.repliesPreview.isNotEmpty() && thread == null) {
            Surface(
                onClick = onToggleReplies,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 52.dp, top = 8.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    comment.repliesPreview.forEach { preview ->
                        Text(
                            text = buildAnnotatedString {
                                withStyle(
                                    androidx.compose.ui.text.SpanStyle(
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                    )
                                ) {
                                    append(preview.userName.ifBlank { "匿名" })
                                    append(": ")
                                }
                                append(preview.message)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    if (comment.replyCount > comment.repliesPreview.size) {
                        Text(
                            text = "查看全部 ${comment.replyCount} 条回复",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        if (thread != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 52.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                thread.items.forEach { reply ->
                    ReplyRow(
                        reply = reply,
                        nowMs = nowMs,
                        onReplyTarget = onReplyTarget,
                        onSeekTo = onSeekTo,
                        onPictureClick = onPictureClick
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
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
                    else -> {
                        TextButton(onClick = onToggleReplies) { Text("收起回复") }
                    }
                }
            }
        } else if (comment.replyCount > 0 && comment.repliesPreview.isEmpty()) {
            TextButton(
                onClick = onToggleReplies,
                modifier = Modifier.padding(start = 44.dp)
            ) { Text("查看回复(${comment.replyCount})") }
        }
    }
}

@Composable
private fun ReplyRow(
    reply: BiliClient.BiliRichComment,
    nowMs: Long,
    onReplyTarget: (ClassicalSourceViewModel.ReplyTarget) -> Unit,
    onSeekTo: (Long) -> Unit,
    onPictureClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth()) {
        if (reply.avatarUrl.isNotBlank()) {
            AsyncImage(
                model = reply.avatarUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = reply.userName.ifBlank { "匿名" },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    ),
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatCommentRelativeTime(reply.ctimeSec * 1000L, nowMs),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Spacer(Modifier.height(2.dp))
            RichCommentText(
                message = reply.message,
                emotes = reply.emotes,
                onSeekTo = onSeekTo
            )
            if (reply.pictures.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                CommentPictureGrid(
                    pictures = reply.pictures,
                    onPictureClick = onPictureClick
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "回复",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier
                        .clickable {
                            onReplyTarget(
                                ClassicalSourceViewModel.ReplyTarget(
                                    rootRpid = reply.root,
                                    parentRpid = reply.rpid,
                                    targetUserName = reply.userName.ifBlank { "匿名" }
                                )
                            )
                        }
                        .padding(vertical = 2.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommentPictureGrid(
    pictures: List<String>,
    onPictureClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        pictures.take(9).forEach { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onPictureClick(url) }
            )
        }
    }
}

/** 富文本评论：纯文本 + 行内表情 + 可点击时间戳 seek。 */
@Composable
private fun RichCommentText(
    message: String,
    emotes: Map<String, BiliClient.BiliCommentEmote>,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val segments = remember(message, emotes) {
        parseRichCommentSegments(message, emotes)
    }
    if (segments.all { it is RichCommentSegment.Text }) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = modifier
        )
        return
    }
    val inlineContent = remember(segments) {
        segments.filterIsInstance<RichCommentSegment.Emote>().mapIndexed { i, emote ->
            val size = if (emote.large) 40.sp else 22.sp
            "emote_$i" to InlineTextContent(
                placeholder = Placeholder(size, size, PlaceholderVerticalAlign.TextCenter)
            ) {
                AsyncImage(
                    model = emote.url,
                    contentDescription = emote.key,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }.toMap()
    }
    val annotated = remember(segments) {
        buildAnnotatedString {
            var emoteIndex = 0
            val linkStyle = androidx.compose.ui.text.SpanStyle(
                color = androidx.compose.ui.graphics.Color(0xFF1E88E5),
                fontWeight = FontWeight.Bold
            )
            for (segment in segments) {
                when (segment) {
                    is RichCommentSegment.Text -> append(segment.text)
                    is RichCommentSegment.Emote -> {
                        appendInlineContent("emote_${emoteIndex++}", "[表情]")
                    }
                    is RichCommentSegment.TimeLink -> {
                        pushStringAnnotation(
                            tag = SEEK_TAG,
                            annotation = segment.positionMs.toString()
                        )
                        withStyle(linkStyle) { append(segment.text) }
                        pop()
                    }
                }
            }
        }
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val onTimestampClick: (Long) -> Unit = { positionMs ->
        onSeekTo(positionMs)
        android.widget.Toast.makeText(
            context,
            "已跳转至 ${formatDuration(positionMs)}",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    if (inlineContent.isEmpty()) {
        ClickableText(
            text = annotated,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                lineHeight = 22.sp
            ),
            modifier = modifier,
            onClick = { offset ->
                annotated.getStringAnnotations(tag = SEEK_TAG, start = offset, end = offset)
                    .firstOrNull()
                    ?.item
                    ?.toLongOrNull()
                    ?.let(onTimestampClick)
            }
        )
    } else {
        var layoutResult by remember {
            mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null)
        }
        Text(
            text = annotated,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                lineHeight = 22.sp
            ),
            inlineContent = inlineContent,
            onTextLayout = { layoutResult = it },
            modifier = modifier.pointerInput(annotated) {
                detectTapGestures { pos ->
                    layoutResult?.getOffsetForPosition(pos)?.let { offset ->
                        annotated.getStringAnnotations(tag = SEEK_TAG, start = offset, end = offset)
                            .firstOrNull()
                            ?.item
                            ?.toLongOrNull()
                            ?.let(onTimestampClick)
                    }
                }
            }
        )
    }
}
