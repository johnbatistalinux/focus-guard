package com.focusguard.app.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import com.focusguard.app.R
import com.focusguard.app.domain.InterventionAction
import com.focusguard.app.ui.design.Theme
import com.focusguard.app.ui.design.dp

/**
 * Native barrier and floating-timer overlay manager built on accessibility
 * overlays. Owns only the window lifecycle; the visual hierarchy lives in
 * [BarrierView] and [FloatingPillView].
 */
class OverlayController(
    private val context: Context,
    private val onExitToHomeClicked: () -> Unit,
    private val onConfirmSessionClicked: (isQuickMicroSession: Boolean) -> Unit,
    private val onEndSessionEarlyClicked: () -> Unit = {}
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var barrierView: BarrierView? = null
    private var barrierParams: WindowManager.LayoutParams? = null
    private var lastKeepScreenOn: Boolean? = null

    private var pillView: FloatingPillView? = null
    private var pillParams: WindowManager.LayoutParams? = null
    private var isPillAlignedStart: Boolean = false

    private var previewMode: Boolean = false

    fun render(action: InterventionAction) {
        previewMode = false
        when (action) {
            InterventionAction.DismissOverlay, is InterventionAction.KickToHome -> dismissAll()
            InterventionAction.NoOp -> Unit
            is InterventionAction.ShowMindfulPause -> {
                removeFloatingPill()
                renderBarrier(keepScreenOn = !action.readyToStartSession) { view ->
                    view.showMindfulPause(action)
                }
            }
            is InterventionAction.ShowHardBlock -> {
                removeFloatingPill()
                renderBarrier(keepScreenOn = false) { view ->
                    view.showHardBlock(action)
                }
            }
            is InterventionAction.ShowFloatingTimer -> {
                removeBarrier()
                showOrUpdateFloatingPill(action.remainingSessionSeconds, action.totalSessionSeconds)
            }
        }
    }

    /**
     * Renders a representative mindful pause without touching the policy engine.
     * Any action dismisses the preview.
     */
    fun renderPreview() {
        dismissAll()
        previewMode = true
        renderBarrier(keepScreenOn = false) { view ->
            view.showMindfulPause(
                InterventionAction.ShowMindfulPause(
                    appId = "preview",
                    displayName = "Instagram",
                    remainingPauseSeconds = 0,
                    readyToStartSession = true,
                    remainingDailyQuotaSeconds = 900L,
                    breathingCue = context.getString(R.string.overlay_pause_cue_ready),
                    anchorActionLabel = context.getString(R.string.overlay_exit_default),
                    allowQuickMicroSession = true,
                    groupName = context.getString(R.string.onboarding_group_name),
                    sessionDurationMinutes = 5
                )
            )
        }
    }

    fun dismissAll() {
        previewMode = false
        removeBarrier()
        removeFloatingPill()
    }

    /**
     * Reconciles "engine says a session is active" with "the pill is on screen".
     *
     * Called once per tick before the engine renders, so a pill dropped by the OEM window manager
     * (IME inset reconfiguration, memory pressure, animation glitch) is forgotten here and
     * re-attached by the very next [render] call.
     */
    fun ensurePillAttached() {
        val view = pillView ?: return
        if (!view.isAttachedToWindow) {
            pillView = null
            pillParams = null
            DebugEventLog.log("overlay") { "pill dropped by window manager; will re-attach" }
        }
    }

    fun isShowingBarrier(): Boolean = barrierView != null

    /**
     * Checks whether the soft keyboard (IME) is currently visible according to [WindowManager]
     * and [WindowInsets], independent of the accessibility window list.
     */
    fun isImeVisible(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metricsIme = runCatching {
                windowManager.currentWindowMetrics.windowInsets.isVisible(WindowInsets.Type.ime())
            }.getOrDefault(false)
            if (metricsIme) return true
            val viewIme = runCatching {
                pillView?.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true
            }.getOrDefault(false)
            if (viewIme) return true
            return false
        }
        @Suppress("DEPRECATION")
        val bottomInset = pillView?.rootWindowInsets?.systemWindowInsetBottom ?: 0
        return bottomInset > context.dp(100)
    }

    private fun handleExitToHome() {
        if (previewMode) {
            dismissAll()
        } else {
            onExitToHomeClicked()
        }
    }

    private fun handleConfirmSession(isQuickMicroSession: Boolean) {
        if (previewMode) {
            dismissAll()
        } else {
            onConfirmSessionClicked(isQuickMicroSession)
        }
    }

    private fun handleEndSessionEarly() {
        if (previewMode) {
            dismissAll()
        } else {
            onEndSessionEarlyClicked()
        }
    }

    private fun renderBarrier(
        keepScreenOn: Boolean,
        bind: (BarrierView) -> Unit
    ) {
        var view = barrierView
        if (view == null) {
            view = BarrierView(
                context,
                reducedMotion = Theme.isReducedMotion(context),
                onExitToHome = { handleExitToHome() },
                onConfirmSession = { quick -> handleConfirmSession(quick) }
            )
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                flagsFor(keepScreenOn),
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.CENTER }

            val attached = runCatching {
                windowManager.addView(view, params)
            }.isSuccess
            if (!attached) return

            barrierView = view
            barrierParams = params
            lastKeepScreenOn = keepScreenOn
        } else {
            updateKeepScreenOn(keepScreenOn)
        }
        bind(view)
    }

    private fun flagsFor(keepScreenOn: Boolean): Int = if (keepScreenOn) {
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
    } else {
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
    }

    private fun updateKeepScreenOn(shouldKeepScreenOn: Boolean) {
        val view = barrierView ?: return
        val params = barrierParams ?: return
        if (lastKeepScreenOn == shouldKeepScreenOn || !view.isAttachedToWindow) return
        params.flags = flagsFor(shouldKeepScreenOn)
        if (runCatching { windowManager.updateViewLayout(view, params) }.isSuccess) {
            lastKeepScreenOn = shouldKeepScreenOn
        }
    }

    private fun removeBarrier() {
        val view = barrierView ?: return
        view.stopBreathing()
        barrierView = null
        barrierParams = null
        lastKeepScreenOn = null
        runCatching {
            if (view.isAttachedToWindow) windowManager.removeView(view)
        }
    }

    private fun showOrUpdateFloatingPill(remainingSeconds: Long, totalSeconds: Long) {
        val attachedView = pillView?.takeIf { it.isAttachedToWindow }
        if (pillView != null && attachedView == null) {
            // WindowManager dropped the overlay (e.g. IME inset reconfiguration by the OEM):
            // forget the stale instance so the next tick re-attaches a fresh one.
            pillView = null
            pillParams = null
        }
        val view = attachedView ?: createAndAttachFloatingPill() ?: return
        view.bind(remainingSeconds, totalSeconds)
    }

    private fun createAndAttachFloatingPill(): FloatingPillView? {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or if (isPillAlignedStart) Gravity.START else Gravity.END
            x = context.dp(16)
            y = context.dp(48)
            // The pill must never be resized or pushed around when the soft keyboard opens.
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setFitInsetsTypes(0)
                setFitInsetsSides(0)
            }
        }

        val view = FloatingPillView(
            context,
            onToggleSide = {
                isPillAlignedStart = !isPillAlignedStart
                val currentParams = pillParams
                val currentView = pillView
                if (currentParams != null && currentView != null) {
                    currentParams.gravity =
                        Gravity.TOP or if (isPillAlignedStart) Gravity.START else Gravity.END
                    if (currentView.isAttachedToWindow) {
                        runCatching { windowManager.updateViewLayout(currentView, currentParams) }
                    }
                }
            },
            onEndSession = { handleEndSessionEarly() }
        )

        val attached = runCatching {
            windowManager.addView(view, params)
        }.isSuccess
        if (!attached) return null

        pillView = view
        pillParams = params
        DebugEventLog.log("overlay") { "pill attached alignStart=$isPillAlignedStart" }
        return view
    }

    private fun removeFloatingPill() {
        val view = pillView ?: return
        pillView = null
        pillParams = null
        runCatching {
            if (view.isAttachedToWindow) windowManager.removeView(view)
        }
        DebugEventLog.log("overlay") { "pill detached" }
    }
}
