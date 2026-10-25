package com.focusguard.app.domain

/**
 * Pure, stateful debouncer that turns a stream of 1 Hz [WindowExitArbiter.Verdict] samples into a
 * single confirmed exit decision.
 *
 * Two independent streaks are tracked so transient ambiguities cannot flip the session state:
 * an explicit [WindowExitArbiter.Verdict.SESSION_HIDDEN] is confirmed quickly, while an
 * [WindowExitArbiter.Verdict.UNKNOWN] picture (no readable application window) needs more samples
 * before the session is paused.
 *
 * Kept free of `android.*` imports so every combination runs on the local JVM.
 */
class SessionExitTracker(
    private val hiddenTicksToExit: Int = DEFAULT_HIDDEN_TICKS_TO_EXIT,
    private val unknownTicksToExit: Int = DEFAULT_UNKNOWN_TICKS_TO_EXIT
) {

    private var hiddenStreak = 0
    private var unknownStreak = 0

    /**
     * Feeds one sample and returns true only when the exit is confirmed.
     */
    fun onTick(verdict: WindowExitArbiter.Verdict): Boolean = when (verdict) {
        WindowExitArbiter.Verdict.SESSION_VISIBLE -> {
            reset()
            false
        }

        WindowExitArbiter.Verdict.SESSION_HIDDEN -> {
            unknownStreak = 0
            hiddenStreak++
            hiddenStreak >= hiddenTicksToExit
        }

        WindowExitArbiter.Verdict.UNKNOWN -> {
            hiddenStreak = 0
            unknownStreak++
            unknownStreak >= unknownTicksToExit
        }
    }

    /**
     * Clears both streaks, e.g. after a confirmed exit or when no session is counting.
     */
    fun reset() {
        hiddenStreak = 0
        unknownStreak = 0
    }

    companion object {
        /** ~2 s: an explicit app switch must not stall the timer for long. */
        const val DEFAULT_HIDDEN_TICKS_TO_EXIT = 2

        /** ~5 s: tolerated only while the foreground window exposes no readable root. */
        const val DEFAULT_UNKNOWN_TICKS_TO_EXIT = 5
    }
}
