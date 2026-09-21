package moe.ouom.neriplayer.core.classical.source

import moe.ouom.neriplayer.core.api.bili.BiliClient

/**
 * Narrow Source API surface: the only Bilibili calls the Classical Source
 * page needs (intro + default-sort comments + on-demand replies).
 *
 * [BiliClient] implements this; tests use fakes without touching
 * [moe.ouom.neriplayer.core.di.AppContainer] or the network.
 */
interface BiliSourceApi {
    suspend fun getVideoBasicInfoByBvid(bvid: String): BiliClient.VideoBasicInfo

    suspend fun getCommentPageByAvid(
        avid: Long,
        sort: Int,
        page: Int,
        pageSize: Int = 20
    ): BiliClient.BiliCommentPage

    suspend fun getCommentRepliesByAvid(
        avid: Long,
        rootRpid: String,
        page: Int,
        pageSize: Int = 10
    ): BiliClient.BiliCommentPage
}
