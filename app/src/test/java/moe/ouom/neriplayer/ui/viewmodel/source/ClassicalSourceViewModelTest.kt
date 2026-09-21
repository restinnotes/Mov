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

    private class FakeApi : BiliSourceApi {
        var infoDelayMs = 0L
        val infos = mutableMapOf<String, BiliClient.VideoBasicInfo>()
        val pages = mutableMapOf<Int, BiliClient.BiliCommentPage>()

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
        ): BiliClient.BiliCommentPage = pages[page] ?: error("no page $page")

        override suspend fun getCommentRepliesByAvid(
            avid: Long,
            rootRpid: String,
            page: Int,
            pageSize: Int
        ): BiliClient.BiliCommentPage = pages[page] ?: error("no replies page $page")
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
}
