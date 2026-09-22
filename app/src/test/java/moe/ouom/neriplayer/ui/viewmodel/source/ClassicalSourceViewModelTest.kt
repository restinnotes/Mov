package moe.ouom.neriplayer.ui.viewmodel.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import moe.ouom.neriplayer.core.api.bili.BiliClient
import moe.ouom.neriplayer.core.classical.source.BiliSourceApi
import moe.ouom.neriplayer.core.classical.source.BiliSourceRepository
import moe.ouom.neriplayer.data.model.SongItem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Source page regression: preload, paging, cancellation, bounded memory.
 * No network: fake [BiliSourceApi]; the real repository (incl. its LRU) runs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClassicalSourceViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun biliSong(bvid: String, aid: Long = 100L, cid: Long = 200L): SongItem {
        return SongItem(
            id = aid,
            name = "戈德堡变奏曲",
            artist = "古尔德",
            album = "Bilibili|$cid|$bvid",
            albumId = 0L,
            durationMs = 3_000_000L,
            coverUrl = null,
            channelId = "bilibili",
            audioId = aid.toString(),
            subAudioId = cid.toString()
        )
    }

    private fun comment(rpid: String, message: String = "好听 $rpid"): BiliClient.BiliRichComment {
        return BiliClient.BiliRichComment(
            rpid = rpid,
            parent = "0",
            root = "0",
            userMid = 1L,
            userName = "听众",
            avatarUrl = "",
            message = message,
            likeCount = 0L,
            replyCount = 0,
            ctimeSec = 1_700_000_000L
        )
    }

    private inner class FakeApi : BiliSourceApi {
        var infoDelayMs = 0L
        val infos = mutableMapOf<String, BiliClient.VideoBasicInfo>()
        val pages = mutableMapOf<Int, BiliClient.BiliCommentPage>()
        val pagesBySort = mutableMapOf<Int, BiliClient.BiliCommentPage>()

        fun addSource(
            bvid: String,
            comments: List<BiliClient.BiliRichComment>,
            hasMore: Boolean = false,
            aid: Long = 100L
        ) {
            infos[bvid] = BiliClient.VideoBasicInfo(
                aid = aid,
                bvid = bvid,
                title = "标题 $bvid",
                coverUrl = "",
                desc = "简介",
                durationSec = 180,
                ownerMid = 2L,
                ownerName = "UP",
                ownerFace = "",
                stats = BiliClient.VideoStats(0, 0, 0, 0, 0, 0, 0),
                pages = emptyList(),
                pubdateSec = 1_700_000_000L
            )
            pages[1] = BiliClient.BiliCommentPage(
                items = comments,
                hasMore = hasMore,
                nextPage = 2
            )
        }

        override suspend fun getVideoBasicInfoByBvid(bvid: String): BiliClient.VideoBasicInfo {
            if (infoDelayMs > 0L) delay(infoDelayMs)
            return infos[bvid] ?: error("no info for $bvid")
        }

        override suspend fun getCommentPageByAvid(
            avid: Long,
            sort: Int,
            page: Int,
            pageSize: Int
        ): BiliClient.BiliCommentPage {
            if (page == 1) {
                pagesBySort[sort]?.let { return it }
            }
            return pages[page] ?: error("no page $page")
        }

        override suspend fun getCommentRepliesByAvid(
            avid: Long,
            rootRpid: String,
            page: Int,
            pageSize: Int
        ): BiliClient.BiliCommentPage = pages[page] ?: error("no replies page $page")

        override suspend fun sendComment(
            avid: Long,
            message: String,
            rootRpid: String?,
            parentRpid: String?
        ): BiliClient.BiliRichComment {
            return comment("sent-${System.currentTimeMillis()}", message)
        }
    }

    @Test
    fun `non bili song shows empty source`() = runTest {
        val vm = ClassicalSourceViewModel(BiliSourceRepository(FakeApi()))
        val local = SongItem(
            id = 1L,
            name = "本地",
            artist = "艺术家",
            album = "专辑",
            albumId = 0L,
            durationMs = 1000L,
            coverUrl = null
        )
        vm.onSongChanged(local)
        assertEquals(
            ClassicalSourceViewModel.IntroState.Empty,
            vm.uiState.value.intro
        )
    }

    @Test
    fun `preload fills intro and first page`() = runTest {
        val api = FakeApi()
        val bvid = "BV1test0001"
        api.addSource(bvid, listOf(comment("1"), comment("2")), hasMore = true)
        val vm = ClassicalSourceViewModel(BiliSourceRepository(api))
        vm.onSongChanged(biliSong(bvid))
        testScheduler.advanceUntilIdle()
        val state = vm.uiState.value
        assertTrue(state.intro is ClassicalSourceViewModel.IntroState.Ready)
        assertEquals(2, state.comments.items.size)
        assertEquals(
            ClassicalSourceViewModel.CommentsLoadState.READY,
            state.comments.loadState
        )
    }

    @Test
    fun `late preload never overwrites newer source`() = runTest {
        val api = FakeApi()
        api.infoDelayMs = 100L
        val slow = "BV1slow0001"
        val fast = "BV1fast0002"
        api.addSource(slow, listOf(comment("slow-1")))
        api.addSource(fast, listOf(comment("fast-1")))
        val vm = ClassicalSourceViewModel(BiliSourceRepository(api))
        vm.onSongChanged(biliSong(slow))
        // Switch before the slow preload completes.
        api.infoDelayMs = 0L
        vm.onSongChanged(biliSong(fast))
        testScheduler.advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(fast, state.bvid)
        assertEquals(listOf("fast-1"), state.comments.items.map { it.rpid })
    }

    @Test
    fun `page failure keeps first page and offers retry`() = runTest {
        val api = FakeApi()
        val bvid = "BV1test0003"
        api.addSource(bvid, listOf(comment("1")), hasMore = true)
        api.pages.remove(2)
        val vm = ClassicalSourceViewModel(BiliSourceRepository(api))
        vm.onSongChanged(biliSong(bvid))
        testScheduler.advanceUntilIdle()
        vm.loadMore()
        testScheduler.advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(listOf("1"), state.comments.items.map { it.rpid })
        assertEquals(
            ClassicalSourceViewModel.CommentsLoadState.ERROR,
            state.comments.loadState
        )
    }

    @Test
    fun `comment items are bounded`() = runTest {
        val api = FakeApi()
        val bvid = "BV1test0004"
        api.addSource(bvid, (1..250).map { comment("c$it") }, hasMore = true)
        val vm = ClassicalSourceViewModel(BiliSourceRepository(api))
        vm.onSongChanged(biliSong(bvid))
        testScheduler.advanceUntilIdle()
        assertTrue(
            vm.uiState.value.comments.items.size <= ClassicalSourceViewModel.MAX_COMMENT_ITEMS
        )
    }

    @Test
    fun `switching sort reloads first page independently`() = runTest {
        val api = FakeApi()
        val bvid = "BV1test0005"
        api.addSource(bvid, listOf(comment("hot-1")), hasMore = true)
        api.pagesBySort[0] = BiliClient.BiliCommentPage(
            items = listOf(comment("latest-1")),
            hasMore = false,
            nextPage = 2
        )
        val vm = ClassicalSourceViewModel(BiliSourceRepository(api))
        vm.onSongChanged(biliSong(bvid))
        testScheduler.advanceUntilIdle()
        assertEquals(
            ClassicalSourceViewModel.CommentSort.FEATURED,
            vm.uiState.value.commentSort
        )
        vm.setCommentSort(ClassicalSourceViewModel.CommentSort.LATEST)
        testScheduler.advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(
            ClassicalSourceViewModel.CommentSort.LATEST,
            state.commentSort
        )
        assertEquals(listOf("latest-1"), state.comments.items.map { it.rpid })
        assertEquals(
            ClassicalSourceViewModel.CommentsLoadState.END,
            state.comments.loadState
        )
    }

    @Test
    fun `sending root comment prepends to list`() = runTest {
        val api = FakeApi()
        val bvid = "BV1test0006"
        api.addSource(bvid, listOf(comment("c1")), hasMore = false)
        val vm = ClassicalSourceViewModel(BiliSourceRepository(api))
        vm.onSongChanged(biliSong(bvid))
        testScheduler.advanceUntilIdle()
        assertEquals(1, vm.uiState.value.comments.items.size)
        vm.sendComment("hello world")
        testScheduler.advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(2, state.comments.items.size)
        assertEquals("hello world", state.comments.items[0].message)
        assertEquals("评论发送成功", state.sendResultEvent)
    }

    @Test
    fun `replying sets target and appends to thread`() = runTest {
        val api = FakeApi()
        val bvid = "BV1test0007"
        api.addSource(bvid, listOf(comment("root-1")), hasMore = false)
        val vm = ClassicalSourceViewModel(BiliSourceRepository(api))
        vm.onSongChanged(biliSong(bvid))
        testScheduler.advanceUntilIdle()
        vm.setReplyTarget(
            ClassicalSourceViewModel.ReplyTarget(
                rootRpid = "root-1",
                parentRpid = "root-1",
                targetUserName = "听众"
            )
        )
        vm.sendComment("回复测试")
        testScheduler.advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals("回复发送成功", state.sendResultEvent)
        val thread = state.replies["root-1"]
        assertTrue(thread != null)
        assertEquals("回复测试", thread?.items?.single()?.message)
    }

    @Test
    fun `loadMore deduplicates comments by rpid`() = runTest {
        val api = FakeApi()
        val bvid = "BV1test0008"
        api.addSource(bvid, listOf(comment("dup-1"), comment("c2")), hasMore = true)
        api.pages[2] = BiliClient.BiliCommentPage(
            items = listOf(comment("dup-1"), comment("c3")),
            hasMore = false,
            nextPage = 3
        )
        val vm = ClassicalSourceViewModel(BiliSourceRepository(api))
        vm.onSongChanged(biliSong(bvid))
        testScheduler.advanceUntilIdle()
        assertEquals(2, vm.uiState.value.comments.items.size)
        vm.loadMore()
        testScheduler.advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(3, state.comments.items.size)
        assertEquals(listOf("dup-1", "c2", "c3"), state.comments.items.map { it.rpid })
    }
}
