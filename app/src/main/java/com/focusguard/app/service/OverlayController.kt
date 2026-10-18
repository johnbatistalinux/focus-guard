package com.focusguard.app.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
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
    private val onConfirmSessionClicked: () -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var barrierRootView: LinearLayout? = null
    private var barrierTitleText: TextView? = null
    private var barrierSubtitleText: TextView? = null
    private var barrierCounterText: TextView? = null
    private var barrierQuotaInfoText: TextView? = null
    private var primaryExitButton: Button? = null
    private var secondaryProceedButton: Button? = null

    private var floatingPillText: TextView? = null

    fun render(action: InterventionAction) {
        when (action) {
            InterventionAction.DismissOverlay, is InterventionAction.KickToHome -> {
                dismissAll()
            }
            InterventionAction.NoOp -> Unit
            is InterventionAction.ShowMindfulPause -> {
                removeFloatingPill()
                showOrUpdateBarrier(
                    title = "Respire fundo antes de abrir o ${action.displayName}",
                    subtitle = if (action.readyToStartSession) {
                        "Pausa concluída. Você realmente precisa entrar agora?"
                    } else {
                        "Quebrando o impulso automático de abrir o feed..."
                    },
                    counterValue = if (action.readyToStartSession) "Pronto" else "${action.remainingPauseSeconds}s",
                    quotaText = "Cota diária restante hoje: ${formatDuration(action.remainingDailyQuotaSeconds)}",
                    showProceedButton = true,
                    proceedEnabled = action.readyToStartSession
                )
            }
            is InterventionAction.ShowHardBlock -> {
                removeFloatingPill()
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
                    quotaText = if (isDailyExhausted) {
                        "O acesso será liberado novamente após a meia-noite."
                    } else {
                        "Aguarde o fim do cooldown para evitar o deep scrolling."
                    },
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
        quotaText: String,
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

        secondaryProceedButton?.apply {
            if (showProceedButton) {
                visibility = View.VISIBLE
                isEnabled = proceedEnabled
                alpha = if (proceedEnabled) 1.0f else 0.38f
                text = if (proceedEnabled) {
                    "Liberar sessão curta consciente"
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
            setBackgroundColor(Color.parseColor("#F50B0F19"))
            val padH = dp(28)
            val padV = dp(32)
            setPadding(padH, padV, padH, padV)
        }

        val titleView = TextView(context).apply {
            setTextColor(Color.parseColor("#F8FAFC"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        val subtitleView = TextView(context).apply {
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(28))
        }

        val counterView = TextView(context).apply {
            setTextColor(Color.parseColor("#38BDF8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 48f)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(24))
        }

        val quotaView = TextView(context).apply {
            setTextColor(Color.parseColor("#CBD5E1"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(36))
        }

        val exitBtn = Button(context).apply {
            text = "Fechar e voltar ao que importa"
            isAllCaps = false
            setTextColor(Color.parseColor("#062E22"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            background = roundedRect(fillColor = "#34D399", cornerDp = 12)
            setPadding(dp(20), dp(14), dp(20), dp(14))
            setOnClickListener { onExitToHomeClicked() }
        }

        val proceedBtn = Button(context).apply {
            isAllCaps = false
            setTextColor(Color.parseColor("#E2E8F0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            background = roundedRect(fillColor = "#1E293B", strokeColor = "#475569", cornerDp = 12)
            setPadding(dp(20), dp(12), dp(20), dp(12))
            setOnClickListener { onConfirmSessionClicked() }
        }

        val btnParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(14)
        }

        root.addView(titleView)
        root.addView(subtitleView)
        root.addView(counterView)
        root.addView(quotaView)
        root.addView(exitBtn, btnParams)
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
            barrierQuotaInfoText = quotaView
            primaryExitButton = exitBtn
            secondaryProceedButton = proceedBtn
        }
    }

    private fun removeBarrier() {
        val view = barrierRootView ?: return
        runCatching { windowManager.removeViewImmediate(view) }
        barrierRootView = null
        barrierTitleText = null
        barrierSubtitleText = null
        barrierCounterText = null
        barrierQuotaInfoText = null
        primaryExitButton = null
        secondaryProceedButton = null
    }

    private fun showOrUpdateFloatingPill(remainingSeconds: Long) {
        if (floatingPillText == null) {
            createAndAttachFloatingPill()
        }

        val isEndingSoon = remainingSeconds <= 30L
        floatingPillText?.apply {
            text = formatDuration(remainingSeconds)
            setTextColor(
                if (isEndingSoon) Color.parseColor("#FCA5A5") else Color.parseColor("#E2E8F0")
            )
            background = roundedRect(
                fillColor = if (isEndingSoon) "#D9450A0A" else "#CC0F172A",
                strokeColor = if (isEndingSoon) "#F87171" else "#38BDF8",
                cornerDp = 20
            )
        }
    }

    private fun createAndAttachFloatingPill() {
        val pill = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(16)
            y = dp(48)
        }

        runCatching {
            windowManager.addView(pill, params)
            floatingPillText = pill
        }
    }

    private fun removeFloatingPill() {
        val pill = floatingPillText ?: return
        runCatching { windowManager.removeViewImmediate(pill) }
        floatingPillText = null
    }

    private fun roundedRect(
        fillColor: String,
        strokeColor: String? = null,
        cornerDp: Int
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(cornerDp).toFloat()
        setColor(Color.parseColor(fillColor))
        if (strokeColor != null) {
            setStroke(dp(1), Color.parseColor(strokeColor))
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
}
