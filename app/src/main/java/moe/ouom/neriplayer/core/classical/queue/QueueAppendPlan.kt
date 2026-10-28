package moe.ouom.neriplayer.core.classical.queue

import moe.ouom.neriplayer.data.model.SongItem
import moe.ouom.neriplayer.data.model.sameIdentityAs

internal data class QueueAppendPlan(val queue: List<SongItem>, val targetIndex: Int)

/** A direct tap selects an existing entry or appends it without deleting any queue item. */
internal fun planQueueAppendAndPlay(queue: List<SongItem>, song: SongItem): QueueAppendPlan {
    val existing = queue.indexOfFirst { it.sameIdentityAs(song) }
    return if (existing >= 0) QueueAppendPlan(queue, existing)
        else QueueAppendPlan(queue + song, queue.size)
}
