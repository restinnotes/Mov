package moe.ouom.neriplayer.core.classical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassicalLowPowerTest {

    @Test
    fun classicalLowPowerGatesAreActiveWhenEnabled() {
        ClassicalLowPower.overrideForTests(true)
        try {
            assertTrue(ClassicalLowPower.enabled)
            assertTrue(ClassicalLowPower.lyricsDisabled)
            assertTrue(ClassicalLowPower.lyriconDisabled)
            assertTrue(ClassicalLowPower.externalLyricsDisabled)
            assertTrue(ClassicalLowPower.dynamicBackgroundDisabled)
            assertTrue(ClassicalLowPower.audioReactiveDisabled)
            assertTrue(ClassicalLowPower.youTubePrewarmDisabled)
        } finally {
            ClassicalLowPower.overrideForTests(null)
        }
    }

    @Test
    fun classicalLowPowerGatesAreInactiveWhenDisabled() {
        ClassicalLowPower.overrideForTests(false)
        try {
            assertFalse(ClassicalLowPower.enabled)
            assertFalse(ClassicalLowPower.lyricsDisabled)
            assertFalse(ClassicalLowPower.lyriconDisabled)
            assertFalse(ClassicalLowPower.externalLyricsDisabled)
            assertFalse(ClassicalLowPower.dynamicBackgroundDisabled)
            assertFalse(ClassicalLowPower.audioReactiveDisabled)
            assertFalse(ClassicalLowPower.youTubePrewarmDisabled)
        } finally {
            ClassicalLowPower.overrideForTests(null)
        }
    }

    @Test
    fun lowPowerDiagnosticsTracksStateChangesWithoutPolling() {
        LowPowerDiagnostics.onSourceInfoChanged(
            source = "BILIBILI",
            mimeType = "audio/mp4",
            codec = "AAC",
            sampleRateHz = 44100,
            bitrateKbps = 192,
            channelCount = 2
        )
        LowPowerDiagnostics.onAudioDecoderInitialized("c2.android.aac.decoder")
        LowPowerDiagnostics.onOffloadPreferencesUpdated(
            offloadRequested = true,
            offloadEligible = true,
            reason = "test_source"
        )
        LowPowerDiagnostics.onOffloadedPlaybackChanged(true)
        LowPowerDiagnostics.onSleepingForOffloadChanged(true)
        LowPowerDiagnostics.onSoundConfigChanged(
            speed = 1.0f,
            hasEffects = false
        )

        val snapshot = LowPowerDiagnostics.current()
        assertEquals("BILIBILI", snapshot.source)
        assertEquals("audio/mp4", snapshot.mimeType)
        assertEquals("AAC", snapshot.codec)
        assertEquals("c2.android.aac.decoder", snapshot.audioDecoderName)
        assertEquals(44100, snapshot.sampleRateHz)
        assertEquals(192, snapshot.bitrateKbps)
        assertEquals(2, snapshot.channelCount)
        assertEquals(1.0f, snapshot.playbackSpeed, 0.001f)
        assertFalse(snapshot.hasEqualizerOrEffects)
        assertTrue(snapshot.offloadRequested)
        assertTrue(snapshot.offloadEligible)
        assertTrue(snapshot.isOffloadedPlayback)
        assertTrue(snapshot.isSleepingForOffload)
        assertEquals("test_source", snapshot.lastOffloadUpdateReason)
    }
}
