package com.example.recorderproject

/**
 * Decides whether to interrupt the record flow to ask the user where recordings
 * should be saved. Kept free of Android types so it is unit-testable on the JVM.
 */
object SaveLocationOnboarding {
    /** Prompt for a save folder only when none is chosen and the user hasn't been asked yet. */
    fun shouldPrompt(hasFolder: Boolean, alreadyPrompted: Boolean): Boolean =
        !hasFolder && !alreadyPrompted
}
