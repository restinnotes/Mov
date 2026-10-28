@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package moe.ouom.neriplayer.core.player.watchdog

import androidx.media3.common.Player
import moe.ouom.neriplayer.core.player.PlayerManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoMoreInteractions

class RuntimePlaybackRestartTest {
    @Test
    fun `rebuffering after real progress does not also start a startup recovery`() {
        val initialized = PlayerManager.initialized
        val pendingLoad = PlayerManager.pendingMediaLoadActive
        val progressReported = PlayerManager.playbackProgressAdvanceReported
        val expeditedSeek = PlayerManager.expeditedYouTubeSeekRecoveryPending
        val token = PlayerManager.playbackStartupWatchdogToken
        val job = PlayerManager.playbackStartupWatchdogJob
        try {
            PlayerManager.initialized = true
            PlayerManager.pendingMediaLoadActive = false
            PlayerManager.playbackProgressAdvanceReported = true
            PlayerManager.expeditedYouTubeSeekRecoveryPending = false

            PlayerManager.schedulePlaybackStartupWatchdog("state_buffering")

            assertEquals(token, PlayerManager.playbackStartupWatchdogToken)
            assertSame(job, PlayerManager.playbackStartupWatchdogJob)
        } finally {
            PlayerManager.initialized = initialized
            PlayerManager.pendingMediaLoadActive = pendingLoad
            PlayerManager.playbackProgressAdvanceReported = progressReported
            PlayerManager.expeditedYouTubeSeekRecoveryPending = expeditedSeek
        }
    }

    @Test
    fun `a midstream stall cancels the old loader and prepares again at the same position`() {
        val player = mock(Player::class.java)

        restartRuntimePlaybackAtPosition(player, 3_600_123L)

        inOrder(player).apply {
            verify(player).stop()
            verify(player).seekTo(3_600_123L)
            verify(player).prepare()
            verify(player).play()
        }
        // No queue replacement, track skip or arbitrary forward/backward seek.
        verifyNoMoreInteractions(player)
    }

    @Test
    fun `unknown negative positions restart from zero`() {
        val player = mock(Player::class.java)

        restartRuntimePlaybackAtPosition(player, -1L)

        inOrder(player).apply {
            verify(player).stop()
            verify(player).seekTo(0L)
            verify(player).prepare()
            verify(player).play()
        }
    }
}
