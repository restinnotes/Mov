package moe.ouom.neriplayer.ui.viewmodel.artist

import moe.ouom.neriplayer.core.api.bili.BiliClient
import moe.ouom.neriplayer.core.di.AppContainer

interface BiliUploaderDataSource {
    suspend fun profile(mid: Long): BiliClient.UploaderProfile
    suspend fun videos(mid: Long, page: Int = 1, keyword: String = ""): BiliClient.UploaderVideoPage
    suspend fun contents(mid: Long, page: Int = 1): BiliClient.UploaderContentPage
    suspend fun videoInfo(bvid: String): BiliClient.VideoBasicInfo
}

internal class LiveBiliUploaderDataSource : BiliUploaderDataSource {
    private val client get() = AppContainer.biliClient
    override suspend fun profile(mid: Long) = client.getUploaderProfile(mid)
    override suspend fun videos(mid: Long, page: Int, keyword: String) =
        client.getUploaderVideos(mid, page = page, keyword = keyword)
    override suspend fun contents(mid: Long, page: Int) = client.getUploaderContents(mid, page = page)
    override suspend fun videoInfo(bvid: String) = client.getVideoBasicInfoByBvid(bvid)
}
