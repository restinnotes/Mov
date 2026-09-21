package moe.ouom.neriplayer.core.classical

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassicalStartupTest {

    @Test
    fun `classical fresh install skips disclaimer and onboarding`() {
        assertTrue(effectiveDisclaimerAccepted(null, classical = true))
        assertTrue(effectiveOnboardingCompleted(null, classical = true))
        assertTrue(effectiveDisclaimerAccepted(false, classical = true))
        assertTrue(effectiveOnboardingCompleted(false, classical = true))
    }

    @Test
    fun `official follows stored flags`() {
        assertFalse(effectiveDisclaimerAccepted(null, classical = false))
        assertFalse(effectiveOnboardingCompleted(false, classical = false))
        assertTrue(effectiveDisclaimerAccepted(true, classical = false))
        assertTrue(effectiveOnboardingCompleted(true, classical = false))
    }
}
