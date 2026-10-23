package com.focusguard.app.service

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.focusguard.app.domain.BlockReason
import com.focusguard.app.domain.InterventionAction
import com.focusguard.app.ui.FocusTheme
import com.focusguard.app.ui.FocusTheme.dp
import com.focusguard.app.ui.FocusTheme.performConfirmHaptic
import com.focusguard.app.ui.FocusTheme.performLightHaptic
import java.util.Locale

/**
 * Native barrier and floating timer overlay manager using Accessibility Overlays.
 * Follows the flat, dark-only design system with thumb-zone actions and in-place updates.
 */
class OverlayController(
    private val context: Context,
    private val onExitToHomeClicked: () -> Unit,
    private val onConfirmSessionClicked: (isQuickMicroSession: Boolean) -> Unit,
    private val onEndSessionEarlyClicked: () -> Unit = {}
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    // Barrier overlay elements (built once, updated in place)
    private var barrierRootView: LinearLayout? = null
    private var barrierWindowParams: WindowManager.LayoutParams? = null
    private var lastBarrierKeepScreenOn: Boolean? = null

    private var categoryTagText: TextView? = null
    private var barrierTitleText: TextView? = null
    private var breathingRingView: View? = null
    private var breathingAnimator: ValueAnimator? = null
    private var barrierCounterText: TextView? = null
    private var barrierCueText: TextView? = null

    private var quotaContainer: LinearLayout? = null
    private var quotaBarFill: View? = null
    private var quotaInfoText: TextView? = null

    private var primaryExitButton: Button? = null
    private var secondaryProceedButton: Button? = null
    private var tertiaryQuickMicroButton: Button? = null

    private var currentReclaimedMinutesOnAbort: Int = 5
    private var isMindfulPauseActive: Boolean = false

    // Floating pill overlay elements
    private var floatingPillText: TextView? = null
    private var floatingPillParams: WindowManager.LayoutParams? = null
    private var isPillAlignedStart: Boolean = false
    private var lastPillStateKey: Int? = null

    private fun dp(value: Int): Int = FocusTheme.dp(context, value)

    fun render(action: InterventionAction) {
        when (action) {
            InterventionAction.DismissOverlay, is InterventionAction.KickToHome -> {
                dismissAll()
            }
            InterventionAction.NoOp -> Unit
            is InterventionAction.ShowMindfulPause -> {
                removeFloatingPill()
                isMindfulPauseActive = true
                currentReclaimedMinutesOnAbort = action.reclaimedMinutesOnAbort
                renderMindfulPause(action)
            }
            is InterventionAction.ShowHardBlock -> {
                removeFloatingPill()
                isMindfulPauseActive = false
                renderHardBlock(action)
            }
            is InterventionAction.ShowFloatingTimer -> {
                removeBarrier()
                showOrUpdateFloatingPill(action.remainingSessionSeconds)
            }
        }
    }

    fun dismissAll() {
        stopBreathingAnimation()
        removeBarrier()
        removeFloatingPill()
    }

    private fun renderMindfulPause(action: InterventionAction.ShowMindfulPause) {
        if (barrierRootView == null) createAndAttachBarrierView()

        updateKeepScreenOn(!action.readyToStartSession)

        categoryTagText?.apply {
            visibility = View.VISIBLE
            text = if (action.groupName.isNotBlank()) {
                "${action.groupName} • ${action.displayName}".uppercase()
            } else {
                action.displayName.uppercase()
            }
            setTextColor(FocusTheme.COLOR_TEXT_MUTED)
        }

        barrierTitleText?.text = "Respire fundo"

        breathingRingView?.visibility = View.VISIBLE
        startBreathingAnimation()

        barrierCounterText?.apply {
            text = if (action.readyToStartSession) "Pronto" else "${action.remainingPauseSeconds}s"
            setTextColor(FocusTheme.COLOR_ACCENT)
        }

        val cue = action.breathingCue.ifBlank {
            if (action.readyToStartSession) {
                "Pausa concluída. Você realmente precisa entrar agora?"
            } else {
                "Quebrando o impulso automático de abrir o feed..."
            }
        }
        barrierCueText?.apply {
            visibility = View.VISIBLE
            text = cue
        }

        quotaContainer?.visibility = View.VISIBLE
        val remainingSec = action.remainingDailyQuotaSeconds
        quotaInfoText?.text = "Cota restante hoje: ${formatDuration(remainingSec)}"
        val fillFraction = (remainingSec / 3600f).coerceIn(0.05f, 1.0f)
        quotaBarFill?.apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, fillFraction)
            background = FocusTheme.flatBox(
                when {
                    remainingSec > 900L -> FocusTheme.COLOR_ACCENT
                    remainingSec > 300L -> FocusTheme.COLOR_WARN
                    else -> FocusTheme.COLOR_DANGER
                },
                cornerDp = 2
            )
        }

        primaryExitButton?.apply {
            text = action.anchorActionLabel
            background = FocusTheme.flatRipple(FocusTheme.COLOR_ACCENT, cornerDp = 12)
            setTextColor(FocusTheme.COLOR_ACCENT_TEXT_DARK)
        }

        secondaryProceedButton?.apply {
            visibility = View.VISIBLE
            isEnabled = action.readyToStartSession
            alpha = if (action.readyToStartSession) 1.0f else 0.4f
            text = if (action.readyToStartSession) {
                "Continuar (${action.sessionDurationMinutes} min)"
            } else {
                "Aguarde a pausa..."
            }
        }

        tertiaryQuickMicroButton?.apply {
            visibility = if (action.allowQuickMicroSession) View.VISIBLE else View.GONE
            isEnabled = action.allowQuickMicroSession
        }
    }

    private fun renderHardBlock(action: InterventionAction.ShowHardBlock) {
        if (barrierRootView == null) createAndAttachBarrierView()

        updateKeepScreenOn(false)
        stopBreathingAnimation()

        val isDailyExhausted = action.reason == BlockReason.DAILY_QUOTA_EXCEEDED

        categoryTagText?.apply {
            visibility = View.VISIBLE
            text = "${action.displayName} • BLOQUEIO".uppercase()
            setTextColor(FocusTheme.COLOR_DANGER)
        }

        barrierTitleText?.text = if (isDailyExhausted) "Limite Diário Atingido" else "Período de Espera"

        breathingRingView?.visibility = View.GONE
        barrierCounterText?.apply {
            text = if (isDailyExhausted) "Até amanhã" else formatDuration(action.remainingBlockSeconds)
            setTextColor(if (isDailyExhausted) FocusTheme.COLOR_DANGER else FocusTheme.COLOR_WARN)
        }

        val reasonMessage = if (isDailyExhausted) {
            if (action.groupName.isNotBlank()) {
                "Você atingiu seu limite diário do grupo ${action.groupName}."
            } else {
                "Você atingiu seu limite diário para esta rede social."
            }
        } else {
            if (action.groupName.isNotBlank()) {
                "Período de resfriamento do grupo ${action.groupName} ativo."
            } else {
                "Período de resfriamento pós-sessão ativo."
            }
        }
        barrierCueText?.apply {
            visibility = View.VISIBLE
            text = reasonMessage
        }

        quotaContainer?.visibility = View.GONE
        secondaryProceedButton?.visibility = View.GONE
        tertiaryQuickMicroButton?.visibility = View.GONE

        primaryExitButton?.apply {
            text = "Voltar para o início"
            background = FocusTheme.flatRipple(FocusTheme.COLOR_ELEVATED, cornerDp = 12)
            setTextColor(FocusTheme.COLOR_TEXT_PRIMARY)
        }
    }

    private fun createAndAttachBarrierView() {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(FocusTheme.COLOR_OVERLAY_BG)
            val padH = dp(24)
            val padV = dp(32)
            setPadding(padH, padV, padH, padV)
        }
        FocusTheme.applySystemWindowInsetsPadding(root, applyTop = true, applyBottom = true)

        val catTag = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(6))
        }

        val titleView = TextView(context).apply {
            setTextColor(FocusTheme.COLOR_TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 1
        }

        val ringContainer = FrameLayout(context).apply {
            val size = dp(120)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, dp(14), 0, dp(14))
            }
        }

        val ringView = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(FocusTheme.COLOR_ACCENT_SUBTLE)
            }
        }

        val counterView = TextView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            setTextColor(FocusTheme.COLOR_ACCENT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 36f)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
        }
        ringContainer.addView(ringView)
        ringContainer.addView(counterView)

        val cueView = TextView(context).apply {
            setTextColor(FocusTheme.COLOR_TEXT_MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(14))
        }

        val quotaBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(20) }
        }

        val barContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(dp(180), dp(4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(6)
            }
            background = FocusTheme.flatBox(FocusTheme.COLOR_ELEVATED, cornerDp = 2)
        }

        val fillView = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.7f)
            background = FocusTheme.flatBox(FocusTheme.COLOR_ACCENT, cornerDp = 2)
        }
        barContainer.addView(fillView)

        val quotaText = TextView(context).apply {
            setTextColor(FocusTheme.COLOR_TEXT_MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
        }
        quotaBox.addView(barContainer)
        quotaBox.addView(quotaText)

        val exitBtn = Button(context).apply {
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            minHeight = dp(52)
            setPadding(dp(20), dp(12), dp(20), dp(12))
            setOnClickListener { view ->
                view.performConfirmHaptic()
                if (isMindfulPauseActive) {
                    text = "✓ +${currentReclaimedMinutesOnAbort} min resgatados 🌱"
                }
                onExitToHomeClicked()
            }
        }

        val proceedBtn = Button(context).apply {
            isAllCaps = false
            setTextColor(FocusTheme.COLOR_TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            minHeight = dp(48)
            background = FocusTheme.flatRipple(FocusTheme.COLOR_ELEVATED, cornerDp = 12)
            setPadding(dp(20), dp(10), dp(20), dp(10))
            setOnClickListener { view ->
                view.performLightHaptic()
                onConfirmSessionClicked(false)
            }
        }

        val quickMicroBtn = Button(context).apply {
            text = "⚡ Resposta rápida (1 min)"
            isAllCaps = false
            setTextColor(FocusTheme.COLOR_TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            minHeight = dp(44)
            background = FocusTheme.flatRipple(Color.TRANSPARENT, cornerDp = 8)
            visibility = View.GONE
            setOnClickListener { view ->
                view.performLightHaptic()
                onConfirmSessionClicked(true)
            }
        }

        val btnParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) }

        root.addView(catTag)
        root.addView(titleView)
        root.addView(ringContainer)
        root.addView(cueView)
        root.addView(quotaBox)
        root.addView(exitBtn, btnParams)
        root.addView(proceedBtn, btnParams)
        root.addView(quickMicroBtn)

        val windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

        runCatching {
            windowManager.addView(root, windowParams)
            barrierRootView = root
            barrierWindowParams = windowParams
            lastBarrierKeepScreenOn = false
            categoryTagText = catTag
            barrierTitleText = titleView
            breathingRingView = ringView
            barrierCounterText = counterView
            barrierCueText = cueView
            quotaContainer = quotaBox
            quotaBarFill = fillView
            quotaInfoText = quotaText
            primaryExitButton = exitBtn
            secondaryProceedButton = proceedBtn
            tertiaryQuickMicroButton = quickMicroBtn
        }
    }

    private fun updateKeepScreenOn(shouldKeepScreenOn: Boolean) {
        if (lastBarrierKeepScreenOn != shouldKeepScreenOn) {
            lastBarrierKeepScreenOn = shouldKeepScreenOn
            val view = barrierRootView
            val params = barrierWindowParams
            if (view != null && params != null && view.isAttachedToWindow) {
                params.flags = if (shouldKeepScreenOn) {
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                } else {
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                }
                runCatching { windowManager.updateViewLayout(view, params) }
            }
        }
    }

    private fun removeBarrier() {
        stopBreathingAnimation()
        val view = barrierRootView ?: return
        barrierRootView = null
        barrierWindowParams = null
        lastBarrierKeepScreenOn = null
        categoryTagText = null
        barrierTitleText = null
        breathingRingView = null
        barrierCounterText = null
        barrierCueText = null
        quotaContainer = null
        quotaBarFill = null
        quotaInfoText = null
        primaryExitButton = null
        secondaryProceedButton = null
        tertiaryQuickMicroButton = null
        isMindfulPauseActive = false
        runCatching { windowManager.removeViewImmediate(view) }
    }

    private fun showOrUpdateFloatingPill(remainingSeconds: Long) {
        if (floatingPillText == null) createAndAttachFloatingPill()

        val isEndingSoon = remainingSeconds <= 30L
        val isUrgent = remainingSeconds <= 10L

        floatingPillText?.apply {
            val prefix = when {
                isUrgent -> "🚨 "
                isEndingSoon -> "⚠️ "
                else -> "● "
            }
            text = "$prefix${formatDuration(remainingSeconds)}"

            val stateKey = if (isUrgent) 2 else if (isEndingSoon) 1 else 0
            if (lastPillStateKey != stateKey) {
                lastPillStateKey = stateKey
                val (fill, txtColor) = when (stateKey) {
                    2 -> Pair(FocusTheme.COLOR_DANGER_SUBTLE, FocusTheme.COLOR_DANGER)
                    1 -> Pair(FocusTheme.COLOR_WARN_SUBTLE, FocusTheme.COLOR_WARN)
                    else -> Pair(FocusTheme.COLOR_SURFACE, FocusTheme.COLOR_TEXT_PRIMARY)
                }
                setTextColor(txtColor)
                background = FocusTheme.flatBox(fill, cornerDp = 20)
            }
        }
    }

    private fun createAndAttachFloatingPill() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or if (isPillAlignedStart) Gravity.START else Gravity.END
            x = dp(16)
            y = dp(48)
        }

        val pill = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener { view ->
                view.performLightHaptic()
                isPillAlignedStart = !isPillAlignedStart
                val p = floatingPillParams ?: return@setOnClickListener
                p.gravity = Gravity.TOP or if (isPillAlignedStart) Gravity.START else Gravity.END
                if (isAttachedToWindow) {
                    runCatching { windowManager.updateViewLayout(this, p) }
                }
            }
            setOnLongClickListener { view ->
                view.performConfirmHaptic()
                onEndSessionEarlyClicked()
                true
            }
        }

        runCatching {
            windowManager.addView(pill, params)
            floatingPillText = pill
            floatingPillParams = params
            lastPillStateKey = null
        }
    }

    private fun removeFloatingPill() {
        val pill = floatingPillText ?: return
        floatingPillText = null
        floatingPillParams = null
        lastPillStateKey = null
        runCatching { windowManager.removeViewImmediate(pill) }
    }

    private fun startBreathingAnimation() {
        val ring = breathingRingView ?: return
        if (breathingAnimator != null) return
        breathingAnimator = ValueAnimator.ofFloat(0.88f, 1.14f).apply {
            duration = 4000L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val scale = anim.animatedValue as Float
                ring.scaleX = scale
                ring.scaleY = scale
            }
            start()
        }
    }

    private fun stopBreathingAnimation() {
        breathingAnimator?.cancel()
        breathingAnimator = null
        breathingRingView?.apply {
            scaleX = 1.0f
            scaleY = 1.0f
        }
    }

    private fun formatDuration(seconds: Long): String {
        val safeSeconds = seconds.coerceAtLeast(0L)
        val m = safeSeconds / 60L
        val s = safeSeconds % 60L
        return String.format(Locale.US, "%02d:%02d", m, s)
    }
}
