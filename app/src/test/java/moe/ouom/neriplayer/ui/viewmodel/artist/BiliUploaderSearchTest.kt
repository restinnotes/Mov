package moe.ouom.neriplayer.ui.viewmodel.artist

import android.app.Application
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import moe.ouom.neriplayer.R
import moe.ouom.neriplayer.core.api.bili.BiliClient
import moe.ouom.neriplayer.data.model.BiliUploaderSummary
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class BiliUploaderSearchTest {
    private val store = ViewModelStore()
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    @Test fun `Android factory can still find Application constructor`() {
        assertNotNull(BiliUploaderDetailViewModel::class.java.getConstructor(Application::class.java))
    }

    private fun TestScope.create(source: FakeSource): BiliUploaderDetailViewModel {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val app = mock(Application::class.java)
        `when`(app.getString(R.string.bili_uploader_load_failed, "offline")).thenReturn("offline")
        return BiliUploaderDetailViewModel(app, source, dispatcher).also { vm ->
            store.put("vm", vm)
            vm.start(BiliUploaderSummary(42, "UP", ""))
            runCurrent()
        }
    }

    @Test fun `typing debounces and searches only the current uploader`() = runTest {
        val source = FakeSource()
        val vm = create(source)
        vm.setVideoQuery("贝")
        runCurrent()
        advanceTimeBy(100)
        vm.setVideoQuery("贝多芬")
        runCurrent()
        advanceTimeBy(350)
        runCurrent()
        assertEquals(listOf(Request(42, 1, ""), Request(42, 1, "贝多芬")), source.requests)
        assertEquals("贝多芬", vm.uiState.value.videos.single().title)
        assertFalse(vm.uiState.value.videosSearching)
    }

    @Test fun `pagination keeps keyword and removes overlapping videos`() = runTest {
        val source = FakeSource()
        source.loader = { _, page, query ->
            page(page, listOf(video("shared", query), video("page$page", query)), hasMore = page < 2)
        }
        val vm = create(source)
        vm.setVideoQuery("piano")
        advanceUntilIdle()
        vm.loadMoreVideos()
        runCurrent()
        assertEquals(Request(42, 2, "piano"), source.requests.last())
        assertEquals(listOf("shared", "page1", "page2"), vm.uiState.value.videos.map { it.bvid })
        assertFalse(vm.uiState.value.videosHasMore)
    }

    @Test fun `clear cancels search and reloads all submissions from page one`() = runTest {
        val source = FakeSource()
        val vm = create(source)
        vm.setVideoQuery("pending")
        runCurrent()
        vm.setVideoQuery("")
        runCurrent()
        advanceTimeBy(500)
        runCurrent()
        assertEquals(listOf(Request(42, 1, ""), Request(42, 1, "")), source.requests)
        assertEquals("all", vm.uiState.value.videos.single().title)
    }

    @Test fun `late cancelled result cannot replace a newer query`() = runTest {
        val late = CompletableDeferred<Unit>()
        val source = FakeSource()
        source.loader = { _, page, query ->
            if (query == "old") withContext(NonCancellable) { late.await() }
            page(page, listOf(video(query, query)))
        }
        val vm = create(source)
        vm.setVideoQuery("old")
        advanceTimeBy(350); runCurrent()
        vm.setVideoQuery("new")
        advanceTimeBy(350); runCurrent()
        late.complete(Unit); runCurrent()
        assertEquals("new", vm.uiState.value.videos.single().title)
    }

    @Test fun `failed search retries same query and page`() = runTest {
        val source = FakeSource()
        val vm = create(source)
        source.loader = { _, _, _ -> throw IOException("offline") }
        vm.setVideoQuery("Bach")
        advanceUntilIdle()
        assertEquals("offline", vm.uiState.value.videosError)
        source.loader = { _, page, query -> page(page, listOf(video(query, query))) }
        vm.retryVideos(); runCurrent()
        assertEquals(Request(42, 1, "Bach"), source.requests.last())
        assertNull(vm.uiState.value.videosError)
        assertEquals("Bach", vm.uiState.value.videos.single().title)
    }

    @Test fun `switch uploader resets query and rejects late search`() = runTest {
        val late = CompletableDeferred<Unit>()
        val source = FakeSource()
        val vm = create(source)
        source.loader = { mid, page, query ->
            if (query.isNotBlank()) withContext(NonCancellable) { late.await() }
            page(page, listOf(video("$mid", "$mid")))
        }
        vm.setVideoQuery("old")
        advanceTimeBy(350); runCurrent()
        vm.start(BiliUploaderSummary(99, "Other", "")); runCurrent()
        late.complete(Unit); runCurrent()
        assertEquals(99L, vm.uiState.value.header?.mid)
        assertEquals("", vm.uiState.value.videoQuery)
        assertEquals("99", vm.uiState.value.videos.single().title)
    }

    private data class Request(val mid: Long, val page: Int, val query: String)
    private class FakeSource : BiliUploaderDataSource {
        val requests = mutableListOf<Request>()
        var loader: suspend (Long, Int, String) -> BiliClient.UploaderVideoPage = { _, page, query ->
            page(page, listOf(video(query, query.ifBlank { "all" })))
        }
        override suspend fun profile(mid: Long) = BiliClient.UploaderProfile(mid, "UP", "", "", "")
        override suspend fun videos(mid: Long, page: Int, keyword: String): BiliClient.UploaderVideoPage {
            requests += Request(mid, page, keyword)
            return loader(mid, page, keyword)
        }
        override suspend fun contents(mid: Long, page: Int) = BiliClient.UploaderContentPage(page, 30, emptyList(), emptyList(), false)
        override suspend fun videoInfo(bvid: String): BiliClient.VideoBasicInfo = error("unused")
    }
    companion object {
        private fun page(page: Int, items: List<BiliClient.UploaderVideo>, hasMore: Boolean = false) =
            BiliClient.UploaderVideoPage(page, 30, items.size, items, hasMore)
        private fun video(bvid: String, title: String) =
            BiliClient.UploaderVideo(1, bvid, title, "", 60, 42, "UP", null, null)
    }
}
