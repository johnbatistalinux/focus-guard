package com.focusguard.app.service

import android.util.Log
import com.focusguard.app.BuildConfig

/**
 * Debug-only diagnostics ring buffer for the foreground flow.
 *
 * The whole message construction is lazy ([message] is a lambda), so release builds never pay an
 * allocation: `BuildConfig.DEBUG` is a `static final false` and R8 strips the branch entirely.
 *
 * Usage on device:
 * ```
 * adb logcat -s FocusGuard
 * ```
 */
object DebugEventLog {

    private const val TAG = "FocusGuard"
    private const val CAPACITY = 100

    private val buffer = ArrayDeque<String>(CAPACITY)

    /**
     * Records one diagnostic line plus the last [CAPACITY] entries in memory. No-op in release.
     */
    fun log(tag: String, message: () -> String) {
        if (!BuildConfig.DEBUG) return
        val line = "$tag: ${message()}"
        if (buffer.size >= CAPACITY) {
            buffer.removeFirst()
        }
        buffer.addLast(line)
        Log.d(TAG, line)
    }

    /**
     * Snapshot of the ring buffer, oldest first. Empty in release builds.
     *
     * Exposed for future debug surfaces (e.g. a hidden diagnostics screen) so the buffer is not
     * write-only; the primary workflow remains `adb logcat -s FocusGuard`.
     */
    fun dump(): List<String> {
        if (!BuildConfig.DEBUG) return emptyList()
        return buffer.toList()
    }
}
