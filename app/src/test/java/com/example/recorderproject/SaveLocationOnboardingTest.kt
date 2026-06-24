package com.example.recorderproject

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveLocationOnboardingTest {

    @Test fun `prompts when no folder chosen and not yet asked`() {
        assertTrue(SaveLocationOnboarding.shouldPrompt(hasFolder = false, alreadyPrompted = false))
    }

    @Test fun `does not prompt once a folder is chosen`() {
        assertFalse(SaveLocationOnboarding.shouldPrompt(hasFolder = true, alreadyPrompted = false))
    }

    @Test fun `does not prompt again after the user was already asked`() {
        assertFalse(SaveLocationOnboarding.shouldPrompt(hasFolder = false, alreadyPrompted = true))
    }
}
