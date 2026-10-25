package com.focusguard.app.domain

/**
 * Pure resolver that answers a single question from a snapshot of the interactive windows:
 * does the app that owns the active session still own the foreground?
 *
 * The resolver deliberately ignores which package emitted the window-state-change event, because
 * an accessibility event describes which *window* changed, not who owns the foreground. Virtual
 * keyboards, One UI helpers, autofill panels and OEM suggestion bars therefore can never be
 * interpreted as "another app".
 *
 * Kept free of `android.*` imports so every combination runs on the local JVM.
 */
object WindowExitArbiter {

    /**
     * Interactive window snapshot captured at sampling time.
     *
     * @property applicationPackages packages of the `TYPE_APPLICATION` windows that exposed their
     * root node.
     * @property applicationWindowCount total `TYPE_APPLICATION` windows currently on screen,
     * including the ones whose root node could not be read.
     * @property isImeVisible true when a `TYPE_INPUT_METHOD` window (virtual keyboard) is displayed.
     */
    data class ForegroundSnapshot(
        val applicationPackages: List<String>,
        val applicationWindowCount: Int,
        val isImeVisible: Boolean
    ) {
        /**
         * True when every on-screen application window could be attributed to a package, so
         * [applicationPackages] is a trustworthy picture of the foreground.
         */
        val hasFullyAttributedApplicationWindows: Boolean
            get() = applicationWindowCount > 0 && applicationPackages.size == applicationWindowCount

        companion object {
            val UNKNOWN = ForegroundSnapshot(
                applicationPackages = emptyList(),
                applicationWindowCount = 0,
                isImeVisible = false
            )
        }
    }

    /**
     * Outcome of a foreground evaluation.
     */
    enum class Verdict {
        /** A monitored application window is on screen (an IME layered above it is irrelevant). */
        SESSION_VISIBLE,

        /** Every application window was attributed and none of them is monitored. */
        SESSION_HIDDEN,

        /** The window list is empty or contains at least one window without a readable root. */
        UNKNOWN
    }

    /**
     * Resolves whether the monitored app still owns the foreground.
     *
     * @param monitoredPackages every package alias of the app that owns the active session
     * (e.g. `com.instagram.android` and `com.instagram.lite`).
     * @param snapshot interactive windows observed at sampling time.
     */
    fun evaluate(
        monitoredPackages: Set<String>,
        snapshot: ForegroundSnapshot
    ): Verdict {
        // 1. The monitored app still owns a visible application window: whatever sits above it
        // (IME, suggestions strip, autofill) is auxiliary.
        if (snapshot.applicationPackages.any { it in monitoredPackages }) {
            return Verdict.SESSION_VISIBLE
        }

        // 2. Every application window on screen was attributed and none belongs to the monitored
        // app, so the foreground really changed.
        if (snapshot.hasFullyAttributedApplicationWindows) {
            return Verdict.SESSION_HIDDEN
        }

        // 3. No application window at all, or at least one window without a readable root: the
        // picture is incomplete, so guessing would risk tearing down a live session.
        return Verdict.UNKNOWN
    }
}
