package com.focusguard.app.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.focusguard.app.domain.BlockReason
import com.focusguard.app.domain.InterventionAction
import java.util.Locale

/**
 * Gerenciador de Overlays Nativos via [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY].
 *
 * Otimizações de Memória (Foco em 4 GB RAM):
 * - Usa 100% Views nativas do Android (`LinearLayout`, `TextView`, `Button`) já residentes no `Zygote`.
 * - Atualiza textos in-place durante a contagem regressiva (zero re-inflação de layout a cada segundo).
 * - Desanexa e libera as referências das Views imediatamente ao voltar para `DismissOverlay`,
 *   permitindo que o Garbage Collector mantenha o heap livre quando em segundo plano.
 */
class OverlayController(
    private val context: Context,
    private val onExitToHomeClicked: () -> Unit,
    private val onConfirmSessionClicked: (isQuickMicroSession: Boolean) -> Unit,
    private val onEndSessionEarlyClicked: () -> Unit = {}
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var barrierRootView: LinearLayout? = null
    private var barrierTitleText: TextView? = null
    private var barrierSubtitleText: TextView? = null
    private var barrierCounterText: TextView? = null
    private var barrierRewardBannerText: TextView? = null
    private var barrierQuotaInfoText: TextView? = null
    private var primaryExitButton: Button? = null
    private var tertiaryQuickMicroButton: Button? = null
    private var secondaryProceedButton: Button? = null

    private var currentReclaimedMinutesOnAbort: Int = 5
    private var isMindfulPauseActive: Boolean = false

    private var floatingPillText: TextView? = null
    private var floatingPillParams: WindowManager.LayoutParams? = null
    private var isPillAlignedStart: Boolean = false
    private var lastPillEndingSoonState: Boolean? = null

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
                val cue = action.breathingCue.ifBlank {
                    if (action.readyToStartSession) {
                        "Pausa concluída. Você realmente precisa entrar agora?"
                    } else {
                        "Quebrando o impulso automático de abrir o feed..."
                    }
                }
                showOrUpdateBarrier(
                    title = "Respire fundo antes de abrir o ${action.displayName}",
                    subtitle = cue,
                    counterValue = if (action.readyToStartSession) "Pronto" else "${action.remainingPauseSeconds}s",
                    rewardBanner = action.rewardBannerText,
                    quotaText = "Cota diária restante hoje: ${formatDuration(action.remainingDailyQuotaSeconds)}",
                    exitButtonLabel = action.anchorActionLabel,
                    showQuickMicroButton = action.allowQuickMicroSession,
                    showProceedButton = true,
                    proceedEnabled = action.readyToStartSession
                )
            }
            is InterventionAction.ShowHardBlock -> {
                removeFloatingPill()
                isMindfulPauseActive = false
                val isDailyExhausted = action.reason == BlockReason.DAILY_QUOTA_EXCEEDED
                showOrUpdateBarrier(
                    title = "${action.displayName} bloqueado",
                    subtitle = if (isDailyExhausted) {
                        "Você atingiu seu limite máximo diário para esta rede social."
                    } else {
                        "Período de resfriamento (Cooldown) pós-sessão ativo."
                    },
                    counterValue = if (isDailyExhausted) {
                        "Até amanhã"
                    } else {
                        formatDuration(action.remainingBlockSeconds)
                    },
                    rewardBanner = "🌱 Aproveite este intervalo fora da tela para focar no que importa.",
                    quotaText = if (isDailyExhausted) {
                        "O acesso será liberado novamente após a meia-noite."
                    } else {
                        "Aguarde o fim do cooldown para evitar o deep scrolling."
                    },
                    exitButtonLabel = "Voltar para a Tela Inicial",
                    showQuickMicroButton = false,
                    showProceedButton = false,
                    proceedEnabled = false
                )
            }
            is InterventionAction.ShowFloatingTimer -> {
                removeBarrier()
                showOrUpdateFloatingPill(action.remainingSessionSeconds)
            }
        }
    }

    fun dismissAll() {
        removeBarrier()
        removeFloatingPill()
    }

    private fun showOrUpdateBarrier(
        title: String,
        subtitle: String,
        counterValue: String,
        rewardBanner: String,
        quotaText: String,
        exitButtonLabel: String,
        showQuickMicroButton: Boolean,
        showProceedButton: Boolean,
        proceedEnabled: Boolean
    ) {
        if (barrierRootView == null) {
            createAndAttachBarrierView()
        }

        barrierTitleText?.text = title
        barrierSubtitleText?.text = subtitle
        barrierCounterText?.text = counterValue
        barrierQuotaInfoText?.text = quotaText
        primaryExitButton?.text = exitButtonLabel

        barrierRewardBannerText?.apply {
            if (rewardBanner.isNotBlank()) {
                visibility = View.VISIBLE
                text = rewardBanner
            } else {
                visibility = View.GONE
            }
        }

        tertiaryQuickMicroButton?.apply {
            visibility = if (showQuickMicroButton) View.VISIBLE else View.GONE
            isEnabled = showQuickMicroButton
        }

        secondaryProceedButton?.apply {
            if (showProceedButton) {
                visibility = View.VISIBLE
                isEnabled = proceedEnabled
                alpha = if (proceedEnabled) 1.0f else 0.38f
                text = if (proceedEnabled) {
                    "Liberar sessão completa"
                } else {
                    "Aguarde a pausa terminar..."
                }
            } else {
                visibility = View.GONE
            }
        }
    }

    private fun createAndAttachBarrierView() {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(COLOR_BARRIER_BG)
            val padH = dp(28)
            val padV = dp(32)
            setPadding(padH, padV, padH, padV)
        }

        val titleView = TextView(context).apply {
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        val subtitleView = TextView(context).apply {
            setTextColor(COLOR_TEXT_MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(20))
        }

        val counterView = TextView(context).apply {
            setTextColor(COLOR_ACCENT_SKY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 48f)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(16))
        }

        val rewardBannerView = TextView(context).apply {
            setTextColor(COLOR_REWARD_TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = roundedRect(fillColor = COLOR_REWARD_BG, strokeColor = COLOR_REWARD_BORDER, cornerDp = 10)
            setPadding(dp(14), dp(8), dp(14), dp(8))
        }

        val bannerParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(14)
        }

        val quotaView = TextView(context).apply {
            setTextColor(COLOR_TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(24))
        }

        val exitBtn = Button(context).apply {
            text = "Fechar e voltar ao que importa"
            isAllCaps = false
            setTextColor(COLOR_EXIT_BTN_TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            background = roundedRect(fillColor = COLOR_EXIT_BTN_BG, cornerDp = 12)
            setPadding(dp(20), dp(14), dp(20), dp(14))
            setOnClickListener { view ->
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                if (isMindfulPauseActive) {
                    text = "✓ Boa escolha! +${currentReclaimedMinutesOnAbort} min resgatados 🌱"
                }
                onExitToHomeClicked()
            }
        }

        val quickMicroBtn = Button(context).apply {
            text = "⚡ Sessão rápida (1 min — Só responder)"
            isAllCaps = false
            setTextColor(COLOR_MICRO_BTN_TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            background = roundedRect(fillColor = COLOR_MICRO_BTN_BG, strokeColor = COLOR_MICRO_BTN_BORDER, cornerDp = 12)
            setPadding(dp(20), dp(12), dp(20), dp(12))
            visibility = View.GONE
            setOnClickListener { onConfirmSessionClicked(true) }
        }

        val proceedBtn = Button(context).apply {
            isAllCaps = false
            setTextColor(COLOR_PROCEED_BTN_TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            background = roundedRect(fillColor = COLOR_PROCEED_BTN_BG, strokeColor = COLOR_PROCEED_BTN_BORDER, cornerDp = 12)
            setPadding(dp(20), dp(12), dp(20), dp(12))
            setOnClickListener { onConfirmSessionClicked(false) }
        }

        val btnParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(12)
        }

        root.addView(titleView)
        root.addView(subtitleView)
        root.addView(counterView)
        root.addView(rewardBannerView, bannerParams)
        root.addView(quotaView)
        root.addView(exitBtn, btnParams)
        root.addView(quickMicroBtn, btnParams)
        root.addView(proceedBtn, btnParams)

        val windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        runCatching {
            windowManager.addView(root, windowParams)
            barrierRootView = root
            barrierTitleText = titleView
            barrierSubtitleText = subtitleView
            barrierCounterText = counterView
            barrierRewardBannerText = rewardBannerView
            barrierQuotaInfoText = quotaView
            primaryExitButton = exitBtn
            tertiaryQuickMicroButton = quickMicroBtn
            secondaryProceedButton = proceedBtn
        }
    }

    private fun removeBarrier() {
        val view = barrierRootView ?: return
        if (view.isAttachedToWindow) {
            runCatching { windowManager.removeViewImmediate(view) }
        }
        barrierRootView = null
        barrierTitleText = null
        barrierSubtitleText = null
        barrierCounterText = null
        barrierRewardBannerText = null
        barrierQuotaInfoText = null
        primaryExitButton = null
        tertiaryQuickMicroButton = null
        secondaryProceedButton = null
        isMindfulPauseActive = false
    }

    private fun showOrUpdateFloatingPill(remainingSeconds: Long) {
        if (floatingPillText == null) {
            createAndAttachFloatingPill()
        }

        val isEndingSoon = remainingSeconds <= 30L
        floatingPillText?.apply {
            text = "${formatDuration(remainingSeconds)} •"
            // Só realoca e troca o Drawable quando o estado visual muda (ex: ao entrar nos últimos 30s)
            if (lastPillEndingSoonState != isEndingSoon) {
                lastPillEndingSoonState = isEndingSoon
                setTextColor(if (isEndingSoon) COLOR_PILL_WARN_TEXT else COLOR_PROCEED_BTN_TEXT)
                background = roundedRect(
                    fillColor = if (isEndingSoon) COLOR_PILL_WARN_BG else COLOR_PILL_NORMAL_BG,
                    strokeColor = if (isEndingSoon) COLOR_PILL_WARN_BORDER else COLOR_ACCENT_SKY,
                    cornerDp = 20
                )
            }
        }
    }

    private fun createAndAttachFloatingPill() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
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
            // Toque simples: alterna a pílula de canto (Direita <-> Esquerda) para não cobrir botões do app
            setOnClickListener {
                isPillAlignedStart = !isPillAlignedStart
                val updatedParams = floatingPillParams ?: return@setOnClickListener
                updatedParams.gravity = Gravity.TOP or if (isPillAlignedStart) Gravity.START else Gravity.END
                if (isAttachedToWindow) {
                    runCatching { windowManager.updateViewLayout(this, updatedParams) }
                }
            }
            // Toque longo: encerra a sessão voluntariamente com Bônus de Saída Consciente
            setOnLongClickListener { view ->
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                onEndSessionEarlyClicked()
                true
            }
        }

        runCatching {
            windowManager.addView(pill, params)
            floatingPillText = pill
            floatingPillParams = params
            lastPillEndingSoonState = null
        }
    }

    private fun removeFloatingPill() {
        val pill = floatingPillText ?: return
        if (pill.isAttachedToWindow) {
            runCatching { windowManager.removeViewImmediate(pill) }
        }
        floatingPillText = null
        floatingPillParams = null
        lastPillEndingSoonState = null
    }

    private fun roundedRect(
        fillColor: Int,
        strokeColor: Int? = null,
        cornerDp: Int
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(cornerDp).toFloat()
        setColor(fillColor)
        if (strokeColor != null) {
            setStroke(dp(1), strokeColor)
        }
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        context.resources.displayMetrics
    ).toInt()

    private fun formatDuration(totalSeconds: Long): String {
        val clamped = totalSeconds.coerceAtLeast(0L)
        val minutes = clamped / 60L
        val seconds = clamped % 60L
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    companion object {
        private const val COLOR_BARRIER_BG = 0xF50B0F19.toInt()
        private const val COLOR_TEXT_PRIMARY = 0xFFF8FAFC.toInt()
        private const val COLOR_TEXT_SECONDARY = 0xFFCBD5E1.toInt()
        private const val COLOR_TEXT_MUTED = 0xFF94A3B8.toInt()
        private const val COLOR_ACCENT_SKY = 0xFF38BDF8.toInt()

        private const val COLOR_REWARD_TEXT = 0xFF6EE7B7.toInt()
        private const val COLOR_REWARD_BG = 0xFF064E3B.toInt()
        private const val COLOR_REWARD_BORDER = 0xFF059669.toInt()

        private const val COLOR_EXIT_BTN_TEXT = 0xFF062E22.toInt()
        private const val COLOR_EXIT_BTN_BG = 0xFF34D399.toInt()

        private const val COLOR_MICRO_BTN_TEXT = 0xFFBAE6FD.toInt()
        private const val COLOR_MICRO_BTN_BG = 0xFF0C4A6E.toInt()
        private const val COLOR_MICRO_BTN_BORDER = 0xFF0284C7.toInt()

        private const val COLOR_PROCEED_BTN_TEXT = 0xFFE2E8F0.toInt()
        private const val COLOR_PROCEED_BTN_BG = 0xFF1E293B.toInt()
        private const val COLOR_PROCEED_BTN_BORDER = 0xFF475569.toInt()

        private const val COLOR_PILL_NORMAL_BG = 0xCC0F172A.toInt()
        private const val COLOR_PILL_WARN_TEXT = 0xFFFCA5A5.toInt()
        private const val COLOR_PILL_WARN_BG = 0xD9450A0A.toInt()
        private const val COLOR_PILL_WARN_BORDER = 0xFFF87171.toInt()
    }
}
