package moe.ouom.neriplayer.core.classical.queue

import moe.ouom.neriplayer.data.model.SongItem
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueAppendPlanTest {
    private fun song(id: Long) = SongItem(id, "Song $id", "Artist", "Album", 0L, 60_000L, null)

    @Test fun `direct tap preserves previous current and queued songs`() {
        val queue = listOf(song(1), song(2), song(3))
        val plan = planQueueAppendAndPlay(queue, song(4))
        assertEquals(queue + song(4), plan.queue)
        assertEquals(3, plan.targetIndex)
    }

    @Test fun `repeat tap locates existing song without reordering or duplicating`() {
        val queue = listOf(song(1), song(2), song(3))
        val plan = planQueueAppendAndPlay(queue, song(2))
        assertEquals(queue, plan.queue)
        assertEquals(1, plan.targetIndex)
    }

    @Test fun `empty queue starts with the tapped song`() {
        assertEquals(QueueAppendPlan(listOf(song(1)), 0), planQueueAppendAndPlay(emptyList(), song(1)))
    }
}
