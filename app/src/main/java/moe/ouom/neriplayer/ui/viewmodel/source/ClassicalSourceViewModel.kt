package moe.ouom.neriplayer.ui.viewmodel.source

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import moe.ouom.neriplayer.core.api.bili.BiliClient
import moe.ouom.neriplayer.core.api.bili.biliBvidOrNull
import moe.ouom.neriplayer.core.classical.source.BiliSourceIntro
import moe.ouom.neriplayer.core.classical.source.BiliSourceRepository
import moe.ouom.neriplayer.data.model.SongItem

/**
 * Classical Source (-1 page) state holder.
 *
 * Owns only: current Bili source, description, comments (+preload/cache).
 * Knows nothing about Composer/Recording/AI/Library.
 * Never observes playback position: only timestamp taps call seek (handled by UI).
 *
 * Cancellation: every song change bumps [sourceGen] and cancels pending jobs;
 * late arrivals check the generation and never overwrite a newer source.
 * Memory is bounded: comment items are capped, later pages and expanded replies
 * are released on source change; only intro + first page survive in the
 * repository LRU.
 */
class ClassicalSourceViewModel(
    private val repository: BiliSourceRepository = BiliSourceRepository()
) : ViewModel() {

    companion object {
        /** Upper bound of root comment items kept per source. */
        const val MAX_COMMENT_ITEMS = 200
    }

    sealed interface IntroState {
        data object Loading : IntroState
        data class Ready(val intro: BiliSourceIntro) : IntroState
        /** Current song is not a Bilibili track. */
        data object Empty : IntroState
        data object Error : IntroState
    }

    enum class CommentsLoadState {
        INITIAL_LOADING,
        READY,
        LOADING_MORE,
        END,
        ERROR
    }

    /** 精选 (Bili sort=2) / 最新 (Bili sort=0)，各自独立翻页。 */
    enum class CommentSort(val biliSort: Int) {
        FEATURED(2),
        LATEST(0)
    }

    data class CommentsState(
        val items: List<BiliClient.BiliRichComment> = emptyList(),
        val loadState: CommentsLoadState = CommentsLoadState.INITIAL_LOADING,
        /** Non-fatal page error; items are kept and a retry row is shown. */
        val pageError: String? = null,
        val page: Int = 0,
        val hasMore: Boolean = false
    )

    data class ReplyThreadState(
        val items: List<BiliClient.BiliRichComment> = emptyList(),
        val loading: Boolean = false,
        val error: String? = null,
        val page: Int = 0,
        val hasMore: Boolean = false
    )

    data class SourceUiState(
        val bvid: String = "",
        val aid: Long = 0L,
        val intro: IntroState = IntroState.Empty,
        val commentSort: CommentSort = CommentSort.FEATURED,
        val comments: CommentsState = CommentsState(
            loadState = CommentsLoadState.INITIAL_LOADING
        ),
        val replies: Map<String, ReplyThreadState> = emptyMap()
    )

    private val _uiState = MutableStateFlow(SourceUiState())
    val uiState: StateFlow<SourceUiState> = _uiState

    private var sourceGen = 0L
    private var preloadJob: Job? = null
    private var moreJob: Job? = null
    private val replyJobs = mutableMapOf<String, Job>()

    /**
     * Called when the playing song changes (also fires while the user stays on
     * PLAYER, so the first comment page is preloaded before swiping to SOURCE).
     * Background work only: never blocks playback.
     */
    fun onSongChanged(song: SongItem?) {
        val bvid = song?.biliBvidOrNull()
        val aidHint = song?.audioId?.trim()?.toLongOrNull() ?: 0L
        if (bvid.isNullOrBlank()) {
            sourceGen += 1
            cancelPending()
            _uiState.value = SourceUiState(intro = IntroState.Empty)
            return
        }
        if (bvid == _uiState.value.bvid && _uiState.value.bvid.isNotBlank()) return
        val gen = ++sourceGen
        cancelPending()
        _uiState.value = SourceUiState(
            bvid = bvid,
            aid = aidHint,
            intro = IntroState.Loading,
            comments = CommentsState(loadState = CommentsLoadState.INITIAL_LOADING)
        )
        preloadJob = viewModelScope.launch {
            val cached = repository.cached(bvid)
            if (cached != null) {
                if (gen == sourceGen) applySnapshot(gen, cached.intro, cached.firstComments)
                return@launch
            }
            val snapshot = repository.preload(bvid, aidHint)
            if (gen != sourceGen) return@launch
            if (snapshot == null) {
                _uiState.value = _uiState.value.copy(intro = IntroState.Error)
                return@launch
            }
            applySnapshot(gen, snapshot.intro, snapshot.firstComments)
        }
    }

    private fun applySnapshot(
        gen: Long,
        intro: BiliSourceIntro,
        firstPage: BiliClient.BiliCommentPage?
    ) {
        if (gen != sourceGen) return
        val comments = when {
            firstPage == null -> CommentsState(
                loadState = CommentsLoadState.ERROR,
                pageError = null
            )
            firstPage.items.isEmpty() && !firstPage.hasMore -> CommentsState(
                items = emptyList(),
                loadState = CommentsLoadState.END,
                page = 1,
                hasMore = false
            )
            else -> CommentsState(
                items = firstPage.items.take(MAX_COMMENT_ITEMS),
                loadState = if (firstPage.hasMore) {
                    CommentsLoadState.READY
                } else {
                    CommentsLoadState.END
                },
                page = 1,
                hasMore = firstPage.hasMore
            )
        }
        _uiState.value = _uiState.value.copy(
            aid = intro.aid,
            intro = IntroState.Ready(intro),
            comments = comments
        )
    }

    private fun cancelPending() {
        preloadJob?.cancel()
        preloadJob = null
        moreJob?.cancel()
        moreJob = null
        replyJobs.values.forEach { it.cancel() }
        replyJobs.clear()
    }

    /** Load the next comment page. No-op unless scrolled near the bottom. */
    fun loadMore() {
        val state = _uiState.value
        val comments = state.comments
        if (state.aid <= 0L) return
        if (comments.loadState == CommentsLoadState.INITIAL_LOADING ||
            comments.loadState == CommentsLoadState.LOADING_MORE ||
            comments.loadState == CommentsLoadState.END
        ) {
            return
        }
        if (!comments.hasMore || moreJob?.isActive == true) return
        val gen = sourceGen
        val nextPage = comments.page + 1
        _uiState.value = state.copy(
            comments = comments.copy(
                loadState = CommentsLoadState.LOADING_MORE,
                pageError = null
            )
        )
        moreJob = viewModelScope.launch {
            val result = repository.loadCommentPage(
                state.aid,
                nextPage,
                sort = state.commentSort.biliSort
            )
            if (gen != sourceGen) return@launch
            val current = _uiState.value.comments
            if (result == null) {
                _uiState.value = _uiState.value.copy(
                    comments = current.copy(
                        loadState = CommentsLoadState.ERROR,
                        pageError = "加载失败，点击重试"
                    )
                )
                return@launch
            }
            val merged = (current.items + result.items).take(MAX_COMMENT_ITEMS)
            val reachedCap = merged.size >= MAX_COMMENT_ITEMS
            _uiState.value = _uiState.value.copy(
                comments = current.copy(
                    items = merged,
                    loadState = if (!result.hasMore || reachedCap) {
                        CommentsLoadState.END
                    } else {
                        CommentsLoadState.READY
                    },
                    pageError = null,
                    page = nextPage,
                    hasMore = result.hasMore && !reachedCap
                )
            )
        }
    }

    /** Switch 精选/最新：独立翻页，切换即从第 1 页重载。 */
    fun setCommentSort(sort: CommentSort) {
        val state = _uiState.value
        if (state.bvid.isBlank() || state.commentSort == sort) return
        reloadFirstPage(sort)
    }

    /** Retry after a page failure. First-page failure reloads from page 1. */
    fun retry() {
        val state = _uiState.value
        if (state.bvid.isBlank()) return
        if (state.comments.items.isEmpty()) {
            reloadFirstPage(state.commentSort)
        } else {
            _uiState.value = state.copy(
                comments = state.comments.copy(
                    loadState = CommentsLoadState.READY,
                    pageError = null
                )
            )
            loadMore()
        }
    }

    private fun reloadFirstPage(sort: CommentSort) {
        val state = _uiState.value
        val gen = ++sourceGen
        cancelPending()
        _uiState.value = state.copy(
            commentSort = sort,
            comments = CommentsState(loadState = CommentsLoadState.INITIAL_LOADING)
        )
        preloadJob = viewModelScope.launch {
            val result = repository.loadCommentPage(
                state.aid,
                page = 1,
                sort = sort.biliSort
            )
            if (gen != sourceGen) return@launch
            if (result == null) {
                _uiState.value = _uiState.value.copy(
                    comments = CommentsState(loadState = CommentsLoadState.ERROR)
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(
                comments = CommentsState(
                    items = result.items.take(MAX_COMMENT_ITEMS),
                    loadState = if (result.hasMore) {
                        CommentsLoadState.READY
                    } else {
                        CommentsLoadState.END
                    },
                    page = 1,
                    hasMore = result.hasMore
                )
            )
        }
    }

    /** Expand or collapse the reply thread under [rootRpid]. Replies load on demand. */
    fun toggleReplies(rootRpid: String) {
        val state = _uiState.value
        if (state.aid <= 0L || rootRpid.isBlank()) return
        if (state.replies.containsKey(rootRpid)) {
            replyJobs.remove(rootRpid)?.cancel()
            _uiState.value = state.copy(replies = state.replies - rootRpid)
            return
        }
        _uiState.value = state.copy(
            replies = state.replies + (rootRpid to ReplyThreadState(loading = true))
        )
        val gen = sourceGen
        val job = viewModelScope.launch {
            val result = repository.loadReplies(state.aid, rootRpid, page = 1)
            if (gen != sourceGen) return@launch
            if (result == null) {
                _uiState.value = _uiState.value.copy(
                    replies = _uiState.value.replies + (
                        rootRpid to ReplyThreadState(
                            loading = false,
                            error = "加载失败，点击重试"
                        )
                        )
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(
                replies = _uiState.value.replies + (
                    rootRpid to ReplyThreadState(
                        items = result.items,
                        loading = false,
                        page = 1,
                        hasMore = result.hasMore
                    )
                    )
            )
        }
        replyJobs[rootRpid] = job
    }

    fun loadMoreReplies(rootRpid: String) {
        val state = _uiState.value
        val thread = state.replies[rootRpid] ?: return
        if (thread.loading || !thread.hasMore || state.aid <= 0L) return
        if (replyJobs[rootRpid]?.isActive == true) return
        _uiState.value = state.copy(
            replies = state.replies + (rootRpid to thread.copy(loading = true, error = null))
        )
        val gen = sourceGen
        val nextPage = thread.page + 1
        replyJobs[rootRpid] = viewModelScope.launch {
            val result = repository.loadReplies(state.aid, rootRpid, nextPage)
            if (gen != sourceGen) return@launch
            val current = _uiState.value.replies[rootRpid] ?: return@launch
            if (result == null) {
                _uiState.value = _uiState.value.copy(
                    replies = _uiState.value.replies + (
                        rootRpid to current.copy(
                            loading = false,
                            error = "加载失败，点击重试"
                        )
                        )
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(
                replies = _uiState.value.replies + (
                    rootRpid to current.copy(
                        items = current.items + result.items,
                        loading = false,
                        page = nextPage,
                        hasMore = result.hasMore
                    )
                    )
            )
        }
    }

    override fun onCleared() {
        cancelPending()
        super.onCleared()
    }
}
