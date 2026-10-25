package com.focusguard.app.domain

/**
 * Pure decision logic that tells a real app switch apart from a transient window layered
 * above the monitored app.
 *
 * Virtual keyboards, One UI helpers, autofill panels and OEM suggestion bars emit
 * `TYPE_WINDOW_STATE_CHANGED` with packages that cannot always be listed in advance. Before
 * pausing a session (and removing the floating timer) the service asks this arbiter whether the
 * monitored app still owns a visible application window at confirmation time.
 *
 * Kept free of `android.*` imports so every combination runs on the local JVM.
 */
object WindowExitArbiter {

    /**
     * Interactive window snapshot captured at confirmation time.
     *
     * @property visibleApplicationPackages packages of the `TYPE_APPLICATION` windows that exposed
     * their root node.
     * @property visibleApplicationWindowCount total `TYPE_APPLICATION` windows currently on screen,
     * including the ones whose root node could not be read.
     * @property isImeVisible true when a `TYPE_INPUT_METHOD` window (virtual keyboard) is displayed.
     */
    data class WindowSnapshot(
        val visibleApplicationPackages: List<String>,
        val visibleApplicationWindowCount: Int,
        val isImeVisible: Boolean
    ) {
        /**
         * True when every on-screen application window could be attributed to a package, so
         * [visibleApplicationPackages] is a trustworthy picture of the foreground.
         */
        val hasFullyAttributedApplicationWindows: Boolean
            get() = visibleApplicationWindowCount > 0 &&
                visibleApplicationPackages.size == visibleApplicationWindowCount

        companion object {
            val UNKNOWN = WindowSnapshot(
                visibleApplicationPackages = emptyList(),
                visibleApplicationWindowCount = 0,
                isImeVisible = false
            )
        }
    }

    /**
     * Returns true only when leaving [monitoredPackages] is the real user intent.
     *
     * @param eventPackage package that emitted the window state change.
     * @param monitoredPackages every package alias of the app that owns the active session
     * (e.g. `com.instagram.android` and `com.instagram.lite`).
     * @param isHomeLauncherEvent true when [eventPackage] belongs to the device launcher, which is
     * always treated as an explicit exit.
     * @param snapshot interactive windows observed at confirmation time.
     */
    fun isRealExit(
        eventPackage: String,
        monitoredPackages: Set<String>,
        isHomeLauncherEvent: Boolean,
        snapshot: WindowSnapshot
    ): Boolean {
        if (eventPackage in monitoredPackages) return false
        if (isHomeLauncherEvent) return true

        // The monitored app still owns a visible application window: the event came from an
        // auxiliary surface (IME, keyboard toolbar, suggestions strip, autofill) above it.
        if (snapshot.visibleApplicationPackages.any { it in monitoredPackages }) return false

        // Every application window on screen was attributed and none belongs to the monitored
        // app, so the foreground really changed.
        if (snapshot.hasFullyAttributedApplicationWindows) return true

        // Windows are missing or unattributed. While the keyboard is on screen the monitored app
        // is almost certainly the window behind it, so prefer keeping the session alive.
        return !snapshot.isImeVisible
    }
}
