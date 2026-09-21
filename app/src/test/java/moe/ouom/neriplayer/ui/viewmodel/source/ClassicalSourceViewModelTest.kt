package moe.ouom.neriplayer.ui.viewmodel.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import moe.ouom.neriplayer.core.api.bili.BiliClient
import moe.ouom.neriplayer.core.classical.source.BiliSourceIntro
import moe.ouom.neriplayer.core.classical.source.BiliSourceRepository
import moe.ouom.neriplayer.core.classical.source.BiliSourceSnapshot
import moe.ouom.neriplayer.data.model.SongItem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Source page regression: preload, paging, cancellation, bounded memory.
 * No network: fake repository with controllable delay.
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

    private fun intro(bvid: String, aid: Long = 100L): BiliSourceIntro {
        return BiliSourceIntro(
            bvid = bvid,
            aid = aid,
            title = "标题 $bvid",
            coverUrl = null,
            uploaderName = "UP",
            uploaderMid = 2L,
            pubdateSec = 1_700_000_000L,
            desc = "简介",
            sourceUrl = "https://www.bilibili.com/video/$bvid"
        )
    }

    private class FakeRepository : BiliSourceRepository(BiliClient()) {
        var preloadDelayMs = 0L
        val snapshots = mutableMapOf<String, BiliSourceSnapshot>()
        val pages = mutableMapOf<Int, BiliClient.BiliCommentPage>()

        override suspend fun cached(bvid: String): BiliSourceSnapshot? = null

        override suspend fun preload(bvid: String, aidHint: Long): BiliSourceSnapshot? {
            if (preloadDelayMs > 0L) delay(preloadDelayMs)
            return snapshots[bvid]
        }

        override suspend fun loadCommentPage(
            avid: Long,
            page: Int,
            pageSize: Int
        ): BiliClient.BiliCommentPage? = pages[page]
    }

    @Test
    fun `non bili song shows empty source`() = runTest {
        val vm = ClassicalSourceViewModel(FakeRepository())
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
        val repo = FakeRepository()
        val bvid = "BV1test0001"
        repo.snapshots[bvid] = BiliSourceSnapshot(
            intro = intro(bvid),
            firstComments = BiliClient.BiliCommentPage(
                items = listOf(comment("1"), comment("2")),
                hasMore = true,
                nextPage = 2
            )
        )
        val vm = ClassicalSourceViewModel(repo)
        vm.onSongChanged(biliSong(bvid))
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
        val repo = FakeRepository()
        repo.preloadDelayMs = 100L
        val slow = "BV1slow0001"
        val fast = "BV1fast0002"
        repo.snapshots[slow] = BiliSourceSnapshot(
            intro = intro(slow),
            firstComments = BiliClient.BiliCommentPage(
                items = listOf(comment("slow-1")),
                hasMore = false,
                nextPage = 2
            )
        )
        repo.snapshots[fast] = BiliSourceSnapshot(
            intro = intro(fast),
            firstComments = BiliClient.BiliCommentPage(
                items = listOf(comment("fast-1")),
                hasMore = false,
                nextPage = 2
            )
        )
        val vm = ClassicalSourceViewModel(repo)
        vm.onSongChanged(biliSong(slow))
        // Switch before the slow preload completes.
        repo.preloadDelayMs = 0L
        vm.onSongChanged(biliSong(fast))
        testScheduler.advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(fast, state.bvid)
        assertEquals(listOf("fast-1"), state.comments.items.map { it.rpid })
    }

    @Test
    fun `page failure keeps first page and offers retry`() = runTest {
        val repo = FakeRepository()
        val bvid = "BV1test0003"
        repo.snapshots[bvid] = BiliSourceSnapshot(
            intro = intro(bvid),
            firstComments = BiliClient.BiliCommentPage(
                items = listOf(comment("1")),
                hasMore = true,
                nextPage = 2
            )
        )
        // No page 2 registered -> loadCommentPage returns null (failure).
        val vm = ClassicalSourceViewModel(repo)
        vm.onSongChanged(biliSong(bvid))
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
        val repo = FakeRepository()
        val bvid = "BV1test0004"
        val many = (1..250).map { comment("c$it") }
        repo.snapshots[bvid] = BiliSourceSnapshot(
            intro = intro(bvid),
            firstComments = BiliClient.BiliCommentPage(
                items = many,
                hasMore = true,
                nextPage = 2
            )
        )
        val vm = ClassicalSourceViewModel(repo)
        vm.onSongChanged(biliSong(bvid))
        assertTrue(
            vm.uiState.value.comments.items.size <= ClassicalSourceViewModel.MAX_COMMENT_ITEMS
        )
    }
}
