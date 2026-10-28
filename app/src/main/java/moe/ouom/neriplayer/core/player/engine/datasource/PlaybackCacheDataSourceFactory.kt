@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package moe.ouom.neriplayer.core.player.engine.datasource

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource

/** Active playback must never wait for a prefetch writer holding the same cache span. */
internal fun buildPlaybackCacheDataSourceFactory(
    cache: Cache,
    upstreamFactory: DataSource.Factory,
    eventListener: CacheDataSource.EventListener? = null
): CacheDataSource.Factory = CacheDataSource.Factory()
    .setCache(cache)
    .setUpstreamDataSourceFactory(upstreamFactory)
    // Read cached spans normally; if another writer owns a hole, stream it directly.
    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    .setEventListener(eventListener)
