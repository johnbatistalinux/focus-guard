package com.focusguard.app.ui

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.focusguard.app.data.FocusPreferencesStore
import com.focusguard.app.data.FocusRuntime
import com.focusguard.app.data.SystemTimeProvider
import com.focusguard.app.domain.FocusConfig
import com.focusguard.app.domain.FocusPolicyEngine
import com.focusguard.app.domain.FocusPreset
import com.focusguard.app.domain.SocialAppCatalog
import com.focusguard.app.domain.TargetApp
import com.focusguard.app.service.FocusAccessibilityService

/**
 * Tela de Configuração Ultraleve do FocusGuard.
 *
 * Herda diretamente de [android.app.Activity] (sem `AppCompat` nem `Jetpack Compose`),
 * mantendo o uso de memória RAM próximo a zero e respeitando a Trava Anti-Sabotagem.
 */
class MainActivity : Activity() {

    private lateinit var store: FocusPreferencesStore
    private lateinit var policyEngine: FocusPolicyEngine

    private lateinit var serviceStatusText: TextView
    private lateinit var serviceActionBtn: Button
    private lateinit var lockBannerText: TextView

    private lateinit var rewardLevelText: TextView
    private lateinit var rewardMasteryText: TextView
    private lateinit var rewardMasteryPercentText: TextView
    private lateinit var rewardTimeSavedText: TextView
    private lateinit var rewardEquivalentText: TextView
    private lateinit var rewardConsistencyText: TextView
    private lateinit var consistencyDotsContainer: LinearLayout

    private lateinit var appsContainer: LinearLayout
    private lateinit var paramsContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = FocusRuntime.getStore(this)
        policyEngine = FocusRuntime.getEngine(this)
        setContentView(buildRootView())
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun buildRootView(): View {
        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B0F19"))
            isFillViewport = true
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(20)
            setPadding(pad, dp(28), pad, dp(36))
        }

        val headerTitle = TextView(this).apply {
            text = "FocusGuard v1.3"
            setTextColor(Color.parseColor("#F8FAFC"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            typeface = Typeface.DEFAULT_BOLD
        }

        val headerSubtitle = TextView(this).apply {
            text = "Anti-Doomscrolling Ultraleve • Zero Ads • Zero Rede"
            setTextColor(Color.parseColor("#64748B"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(4), 0, dp(20))
        }

        // Card de Status do Serviço de Acessibilidade
        val serviceCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBackground("#111827", "#1E293B")
            val pad = dp(16)
            setPadding(pad, pad, pad, pad)
        }

        serviceStatusText = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
        }

        serviceActionBtn = Button(this).apply {
            text = "Abrir Configurações de Acessibilidade"
            isAllCaps = false
            setTextColor(Color.parseColor("#062E22"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            background = interactiveCardBackground("#38BDF8", null, cornerDp = 10)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setOnClickListener { view ->
                performHaptic(view)
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        val btnMargin = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12) }

        serviceCard.addView(serviceStatusText)
        serviceCard.addView(serviceActionBtn, btnMargin)

        // Bento Dashboard de Conquistas & Reforço Positivo
        val rewardCard = buildRewardCard()
        val rewardCardMargin = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(14) }

        // Banner da Trava Anti-Sabotagem
        lockBannerText = TextView(this).apply {
            setTextColor(Color.parseColor("#FECACA"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            background = cardBackground("#450A0A", "#DC2626", cornerDp = 10)
            val pad = dp(14)
            setPadding(pad, pad, pad, pad)
            visibility = View.GONE
        }

        val lockParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(16) }

        val appsHeader = sectionHeader("Redes Sociais Monitoradas")
        appsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val paramsHeader = sectionHeader("Parâmetros de Intervenção & Reforço")
        paramsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        content.addView(headerTitle)
        content.addView(headerSubtitle)
        content.addView(serviceCard)
        content.addView(rewardCard, rewardCardMargin)
        content.addView(lockBannerText, lockParams)
        content.addView(appsHeader)
        content.addView(appsContainer)
        content.addView(paramsHeader)
        content.addView(paramsContainer)

        scrollView.addView(content)
        return scrollView
    }

    private fun buildRewardCard(): View {
        val rootCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBackground("#06171E", "#0E393F", cornerDp = 16)
            val pad = dp(16)
            setPadding(pad, pad, pad, pad)
        }

        // Badge de Nível de Foco
        rewardLevelText = TextView(this).apply {
            setTextColor(Color.parseColor("#34D399"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            background = cardBackground("#064E3B", "#059669", cornerDp = 8)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
            layoutParams = params
        }

        // Grid Bento (2 Colunas)
        val bentoRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }

        // Bento Esquerda: Domínio Hoje
        val leftBento = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBackground("#0B1E28", "#153E4D", cornerDp = 12)
            val pad = dp(12)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(5)
            }
        }

        val masteryHeader = TextView(this).apply {
            text = "DOMÍNIO HOJE"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.DEFAULT_BOLD
        }

        rewardMasteryPercentText = TextView(this).apply {
            setTextColor(Color.parseColor("#34D399"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(2), 0, dp(2))
        }

        rewardMasteryText = TextView(this).apply {
            setTextColor(Color.parseColor("#CBD5E1"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        }

        leftBento.addView(masteryHeader)
        leftBento.addView(rewardMasteryPercentText)
        leftBento.addView(rewardMasteryText)

        // Bento Direita: Tempo Resgatado
        val rightBento = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBackground("#0B1E28", "#153E4D", cornerDp = 12)
            val pad = dp(12)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(5)
            }
        }

        val timeHeader = TextView(this).apply {
            text = "TEMPO RESGATADO"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.DEFAULT_BOLD
        }

        rewardTimeSavedText = TextView(this).apply {
            setTextColor(Color.parseColor("#38BDF8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(2), 0, dp(2))
        }

        rewardEquivalentText = TextView(this).apply {
            setTextColor(Color.parseColor("#CBD5E1"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        }

        rightBento.addView(timeHeader)
        rightBento.addView(rewardTimeSavedText)
        rightBento.addView(rewardEquivalentText)

        bentoRow.addView(leftBento)
        bentoRow.addView(rightBento)

        // Bento Inferior: Consistência Semanal (7 dias)
        val consistencyCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBackground("#0B1E28", "#153E4D", cornerDp = 12)
            val pad = dp(12)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }

        val consistencyHeader = TextView(this).apply {
            text = "CONSISTÊNCIA SEMANAL"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.DEFAULT_BOLD
        }

        rewardConsistencyText = TextView(this).apply {
            setTextColor(Color.parseColor("#D1FAE5"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(3), 0, dp(8))
        }

        consistencyDotsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        consistencyCard.addView(consistencyHeader)
        consistencyCard.addView(rewardConsistencyText)
        consistencyCard.addView(consistencyDotsContainer)

        val pillHintText = TextView(this).apply {
            text = "💡 Na sessão: toque na pílula p/ alternar lado • segure p/ encerrar com bônus"
            setTextColor(Color.parseColor("#64748B"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(0, dp(2), 0, 0)
        }

        rootCard.addView(rewardLevelText)
        rootCard.addView(bentoRow)
        rootCard.addView(consistencyCard)
        rootCard.addView(pillHintText)
        return rootCard
    }

    private fun refreshUi() {
        val isEnabledInOs = isAccessibilityServiceEnabledInSettings()
        if (isEnabledInOs) {
            serviceStatusText.text = "● Serviço de Acessibilidade Ativo (Proteção ligada)"
            serviceStatusText.setTextColor(Color.parseColor("#34D399"))
            serviceActionBtn.text = "Gerenciar Permissão no Sistema"
        } else {
            serviceStatusText.text = "○ Serviço Desativado — Ative para interceptar aberturas"
            serviceStatusText.setTextColor(Color.parseColor("#FBBF24"))
            serviceActionBtn.text = "Ativar Serviço de Acessibilidade"
        }

        val summary = policyEngine.getRewardSummary()
        rewardLevelText.text = "Nível: ${summary.focusLevelTitle}"
        rewardMasteryPercentText.text = "${summary.masteryPercentToday}%"
        rewardMasteryText.text = "${summary.abortedToday}/${summary.attemptsToday} impulsos resistidos"
        rewardTimeSavedText.text = "+${summary.reclaimedMinutesToday}m"
        rewardEquivalentText.text = summary.tangibleEquivalentText.ifBlank { "Hoje • Fora da tela" }
        rewardConsistencyText.text = summary.consistencySummaryText
        renderConsistencyPills(summary.last7DaysDots)

        val lockStatus = policyEngine.getSettingsLockStatus()
        if (lockStatus.isLocked) {
            lockBannerText.visibility = View.VISIBLE
            lockBannerText.text = "🔒 ${lockStatus.reasonMessage}\nAs configurações estão em modo somente leitura até o fim do bloqueio."
        } else {
            lockBannerText.visibility = View.GONE
        }

        val config = store.getConfig()
        renderAppsList(config, isLocked = lockStatus.isLocked)
        renderParamsList(config, isLocked = lockStatus.isLocked)
    }

    private fun renderConsistencyPills(dots: String) {
        consistencyDotsContainer.removeAllViews()
        val dayLabels = listOf("D-6", "D-5", "D-4", "D-3", "D-2", "Ontem", "Hoje")
        val cleanDots = dots.filter { it == '●' || it == '○' }

        for (i in 0 until 7) {
            val isSuccess = if (i < cleanDots.length) cleanDots[i] == '●' else false
            val isToday = i == 6

            val pill = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val dotView = View(this).apply {
                val size = dp(if (isToday) 12 else 9)
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    bottomMargin = dp(4)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    val color = when {
                        isSuccess -> Color.parseColor("#10B981")
                        isToday -> Color.parseColor("#38BDF8")
                        else -> Color.parseColor("#475569")
                    }
                    setColor(color)
                    if (isToday) {
                        setStroke(dp(1), Color.parseColor("#F8FAFC"))
                    }
                }
            }

            val labelView = TextView(this).apply {
                text = if (isToday) "Hoje" else dayLabels[i]
                setTextColor(if (isToday) Color.parseColor("#F8FAFC") else Color.parseColor("#64748B"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                typeface = if (isToday) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                gravity = Gravity.CENTER
            }

            pill.addView(dotView)
            pill.addView(labelView)
            consistencyDotsContainer.addView(pill)
        }
    }

    private fun renderAppsList(config: FocusConfig, isLocked: Boolean) {
        appsContainer.removeAllViews()
        val today = SystemTimeProvider.currentEpochDay()
        val nowMs = SystemTimeProvider.elapsedRealtimeMs()

        for (app in SocialAppCatalog.SUPPORTED_APPS) {
            val usage = store.getUsageState(app.id, today)
            val usedMin = usage.usedDailySeconds / 60L
            val isCooldown = usage.cooldownUntilRealtimeMs > nowMs &&
                (usage.cooldownUntilRealtimeMs - nowMs) <= config.cooldownSeconds * 1000L

            val row = buildAppRow(
                app = app,
                isChecked = app.id in config.enabledAppIds,
                usedMinutes = usedMin,
                quotaMinutes = config.dailyQuotaMinutes,
                sessionsToday = usage.sessionsStartedToday,
                isCooldown = isCooldown,
                isLocked = isLocked,
                onToggle = { enabled ->
                    val updatedIds = config.enabledAppIds.toMutableSet()
                    if (enabled) updatedIds.add(app.id) else updatedIds.remove(app.id)
                    updateConfig(config.copy(enabledAppIds = updatedIds))
                }
            )
            appsContainer.addView(row)
        }
    }

    private fun buildAppRow(
        app: TargetApp,
        isChecked: Boolean,
        usedMinutes: Long,
        quotaMinutes: Int,
        sessionsToday: Int,
        isCooldown: Boolean,
        isLocked: Boolean,
        onToggle: (Boolean) -> Unit
    ): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = cardBackground("#111827", "#1E293B")
            val padH = dp(16)
            val padV = dp(14)
            setPadding(padH, padV, padH, padV)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }

        // Pílula com a cor de marca do aplicativo
        val brandDot = View(this).apply {
            val size = dp(10)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = dp(12)
                gravity = Gravity.CENTER_VERTICAL
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(app.brandColorHex))
            }
        }

        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val nameRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val nameView = TextView(this).apply {
            text = app.displayName
            setTextColor(Color.parseColor("#F1F5F9"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
        }
        nameRow.addView(nameView)

        if (isCooldown) {
            val cooldownBadge = TextView(this).apply {
                text = "⏳ Cooldown"
                setTextColor(Color.parseColor("#FCA5A5"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                typeface = Typeface.DEFAULT_BOLD
                background = cardBackground("#450A0A", "#DC2626", cornerDp = 6)
                setPadding(dp(6), dp(2), dp(6), dp(2))
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp(8) }
                layoutParams = params
            }
            nameRow.addView(cooldownBadge)
        }

        val statsView = TextView(this).apply {
            text = "Hoje: ${usedMinutes}/${quotaMinutes} min • $sessionsToday sessão(ões)"
            setTextColor(if (isCooldown) Color.parseColor("#F87171") else Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(0, dp(2), 0, 0)
        }

        // Barra de progresso nativa ultra-fina (4dp) indicando consumo da cota
        val quotaProgressTrack = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(4)
            ).apply {
                topMargin = dp(6)
            }
            background = cardBackground("#1E293B", null, cornerDp = 2)
        }

        val ratio = if (quotaMinutes > 0) (usedMinutes.toFloat() / quotaMinutes.toFloat()).coerceIn(0f, 1f) else 0f
        val quotaProgressFill = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, ratio.coerceAtLeast(0.001f))
            val fillColor = when {
                ratio >= 1.0f -> "#EF4444"
                ratio >= 0.70f -> "#F59E0B"
                else -> "#10B981"
            }
            background = cardBackground(fillColor, null, cornerDp = 2)
        }
        val quotaProgressEmpty = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (1f - ratio).coerceAtLeast(0.001f))
        }
        quotaProgressTrack.addView(quotaProgressFill)
        quotaProgressTrack.addView(quotaProgressEmpty)

        textColumn.addView(nameRow)
        textColumn.addView(statsView)
        textColumn.addView(quotaProgressTrack)

        val toggle = Switch(this).apply {
            this.isChecked = isChecked
            isEnabled = !isLocked
            alpha = if (isLocked) 0.45f else 1.0f
            setOnCheckedChangeListener { _, checked -> onToggle(checked) }
        }

        row.addView(brandDot)
        row.addView(textColumn)
        row.addView(toggle)
        return row
    }

    private fun renderParamsList(config: FocusConfig, isLocked: Boolean) {
        paramsContainer.removeAllViews()

        // Fileira de Perfis Rápidos de Foco
        paramsContainer.addView(buildPresetsRow(config, isLocked))

        paramsContainer.addView(
            buildStepperRow(
                label = "Pausa Consciente Base ao Abrir",
                valueText = "${config.mindfulPauseSeconds} seg",
                isLocked = isLocked,
                onDecrement = {
                    val next = (config.mindfulPauseSeconds - 5).coerceAtLeast(5)
                    updateConfig(config.copy(mindfulPauseSeconds = next))
                },
                onIncrement = {
                    val next = (config.mindfulPauseSeconds + 5).coerceAtMost(60)
                    updateConfig(config.copy(mindfulPauseSeconds = next))
                }
            )
        )

        paramsContainer.addView(
            buildToggleParamRow(
                title = "Pausa Progressiva (+5s por sessão no dia)",
                subtitle = "Aumenta a reflexão a cada reabertura repetida no mesmo dia",
                isChecked = config.progressivePauseEnabled,
                isLocked = isLocked,
                onToggle = { enabled ->
                    updateConfig(config.copy(progressivePauseEnabled = enabled))
                }
            )
        )

        paramsContainer.addView(
            buildAnchorHabitRow(
                currentHabit = config.selectedAnchorHabit,
                isLocked = isLocked,
                onCycleHabit = {
                    val presets = FocusConfig.PRESET_ANCHOR_HABITS
                    val idx = presets.indexOf(config.selectedAnchorHabit)
                    val nextHabit = presets[(idx + 1) % presets.size]
                    updateConfig(config.copy(selectedAnchorHabit = nextHabit))
                }
            )
        )

        paramsContainer.addView(
            buildStepperRow(
                label = "Duração da Sessão Liberada",
                valueText = "${config.sessionDurationMinutes} min",
                isLocked = isLocked,
                onDecrement = {
                    val next = (config.sessionDurationMinutes - 1).coerceAtLeast(1)
                    updateConfig(config.copy(sessionDurationMinutes = next))
                },
                onIncrement = {
                    val next = (config.sessionDurationMinutes + 1).coerceAtMost(30)
                    val coercedDailyQuota = maxOf(config.dailyQuotaMinutes, next)
                    updateConfig(
                        config.copy(
                            sessionDurationMinutes = next,
                            dailyQuotaMinutes = coercedDailyQuota
                        )
                    )
                }
            )
        )

        paramsContainer.addView(
            buildStepperRow(
                label = "Cooldown Pós-Sessão",
                valueText = "${config.cooldownMinutes} min",
                isLocked = isLocked,
                onDecrement = {
                    val next = (config.cooldownMinutes - 5).coerceAtLeast(5)
                    updateConfig(config.copy(cooldownMinutes = next))
                },
                onIncrement = {
                    val next = (config.cooldownMinutes + 5).coerceAtMost(60)
                    updateConfig(config.copy(cooldownMinutes = next))
                }
            )
        )

        paramsContainer.addView(
            buildStepperRow(
                label = "Cota Máxima Diária por App",
                valueText = "${config.dailyQuotaMinutes} min",
                isLocked = isLocked,
                onDecrement = {
                    val next = (config.dailyQuotaMinutes - 5).coerceAtLeast(5)
                    val coercedSession = minOf(config.sessionDurationMinutes, next)
                    updateConfig(
                        config.copy(
                            dailyQuotaMinutes = next,
                            sessionDurationMinutes = coercedSession
                        )
                    )
                },
                onIncrement = {
                    val next = (config.dailyQuotaMinutes + 5).coerceAtMost(120)
                    updateConfig(config.copy(dailyQuotaMinutes = next))
                }
            )
        )
    }

    private fun buildToggleParamRow(
        title: String,
        subtitle: String,
        isChecked: Boolean,
        isLocked: Boolean,
        onToggle: (Boolean) -> Unit
    ): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = cardBackground("#111827", "#1E293B")
            val padH = dp(16)
            val padV = dp(12)
            setPadding(padH, padV, padH, padV)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val titleView = TextView(this).apply {
            text = title
            setTextColor(Color.parseColor("#E2E8F0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }

        val subView = TextView(this).apply {
            text = subtitle
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(0, dp(2), 0, 0)
        }

        textCol.addView(titleView)
        textCol.addView(subView)

        val toggle = Switch(this).apply {
            this.isChecked = isChecked
            isEnabled = !isLocked
            alpha = if (isLocked) 0.45f else 1.0f
            setOnCheckedChangeListener { _, checked -> onToggle(checked) }
        }

        row.addView(textCol)
        row.addView(toggle)
        return row
    }

    private fun buildAnchorHabitRow(
        currentHabit: String,
        isLocked: Boolean,
        onCycleHabit: () -> Unit
    ): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = cardBackground("#111827", "#1E293B")
            val padH = dp(16)
            val padV = dp(12)
            setPadding(padH, padV, padH, padV)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val labelView = TextView(this).apply {
            text = "Âncora do Botão Verde (Meta Real)"
            setTextColor(Color.parseColor("#E2E8F0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }

        val valueView = TextView(this).apply {
            text = "Ao desistir: \"$currentHabit\""
            setTextColor(Color.parseColor("#34D399"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(2), 0, 0)
        }

        textCol.addView(labelView)
        textCol.addView(valueView)

        val cycleBtn = Button(this).apply {
            text = "Trocar"
            isAllCaps = false
            isEnabled = !isLocked
            alpha = if (isLocked) 0.4f else 1.0f
            setTextColor(Color.parseColor("#F8FAFC"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            background = interactiveCardBackground("#1E293B", "#334155", cornerDp = 8)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { view ->
                performHaptic(view)
                onCycleHabit()
            }
        }

        row.addView(textCol)
        row.addView(cycleBtn)
        return row
    }

    private fun buildPresetsRow(config: FocusConfig, isLocked: Boolean): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(14)
            setPadding(pad, pad, pad, pad)
            background = cardBackground("#111827", "#1E293B", cornerDp = 12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }

        val headerText = TextView(this).apply {
            text = "⚡ PERFIS RÁPIDOS DE FOCO"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(10))
        }

        val chipsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        for ((index, preset) in FocusPreset.entries.withIndex()) {
            val isActive = config.mindfulPauseSeconds == preset.mindfulPauseSeconds &&
                config.sessionDurationMinutes == preset.sessionDurationMinutes &&
                config.cooldownMinutes == preset.cooldownMinutes &&
                config.dailyQuotaMinutes == preset.dailyQuotaMinutes

            val chip = Button(this).apply {
                text = preset.title
                isAllCaps = false
                isEnabled = !isLocked
                alpha = if (isLocked) 0.45f else 1.0f
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (isActive) Color.parseColor("#38BDF8") else Color.parseColor("#E2E8F0"))
                val fill = if (isActive) "#0C4A6E" else "#1E293B"
                val stroke = if (isActive) "#38BDF8" else "#334155"
                background = interactiveCardBackground(fill, stroke, cornerDp = 10)
                val params = LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                    if (index > 0) marginStart = dp(8)
                }
                layoutParams = params
                setPadding(0, 0, 0, 0)
                setOnClickListener { view ->
                    performHaptic(view)
                    updateConfig(preset.applyTo(config))
                }
            }
            chipsRow.addView(chip)
        }

        container.addView(headerText)
        container.addView(chipsRow)
        return container
    }

    private fun buildStepperRow(
        label: String,
        valueText: String,
        isLocked: Boolean,
        onDecrement: () -> Unit,
        onIncrement: () -> Unit
    ): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = cardBackground("#111827", "#1E293B")
            val padH = dp(16)
            val padV = dp(12)
            setPadding(padH, padV, padH, padV)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }

        val labelView = TextView(this).apply {
            text = label
            setTextColor(Color.parseColor("#E2E8F0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val minusBtn = stepperButton("-", isLocked, onDecrement)
        val valView = TextView(this).apply {
            text = valueText
            setTextColor(Color.parseColor("#38BDF8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            minWidth = dp(68)
        }
        val plusBtn = stepperButton("+", isLocked, onIncrement)

        row.addView(labelView)
        row.addView(minusBtn)
        row.addView(valView)
        row.addView(plusBtn)
        return row
    }

    private fun stepperButton(symbol: String, isLocked: Boolean, onClick: () -> Unit): Button =
        Button(this).apply {
            text = symbol
            isAllCaps = false
            isEnabled = !isLocked
            alpha = if (isLocked) 0.4f else 1.0f
            setTextColor(Color.parseColor("#F8FAFC"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            background = interactiveCardBackground("#1E293B", "#334155", cornerDp = 8)
            val size = dp(38)
            layoutParams = LinearLayout.LayoutParams(size, size)
            setPadding(0, 0, 0, 0)
            setOnClickListener { view ->
                performHaptic(view)
                onClick()
            }
        }

    private fun updateConfig(newConfig: FocusConfig) {
        if (policyEngine.getSettingsLockStatus().isLocked) return
        store.saveConfig(newConfig)
        FocusAccessibilityService.notifyConfigUpdated()
        refreshUi()
    }

    private fun isAccessibilityServiceEnabledInSettings(): Boolean {
        if (FocusAccessibilityService.isServiceRunning()) return true
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(packageName)
    }

    private fun formatMinutesSummary(totalMinutes: Long): String {
        if (totalMinutes < 60L) return "${totalMinutes} min"
        val hours = totalMinutes / 60L
        val mins = totalMinutes % 60L
        return if (mins == 0L) "${hours}h" else "${hours}h ${mins}m"
    }

    private fun sectionHeader(title: String): TextView = TextView(this).apply {
        text = title.uppercase()
        setTextColor(Color.parseColor("#94A3B8"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(22), 0, dp(10))
    }

    private fun cardBackground(
        fillColor: String,
        strokeColor: String?,
        cornerDp: Int = 12
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(cornerDp).toFloat()
        setColor(Color.parseColor(fillColor))
        if (strokeColor != null) {
            setStroke(dp(1), Color.parseColor(strokeColor))
        }
    }

    private fun interactiveCardBackground(
        fillColor: String,
        strokeColor: String? = null,
        cornerDp: Int = 12,
        rippleColor: String = "#33FFFFFF"
    ): Drawable {
        val content = cardBackground(fillColor, strokeColor, cornerDp)
        val mask = cardBackground("#FFFFFF", null, cornerDp)
        return RippleDrawable(ColorStateList.valueOf(Color.parseColor(rippleColor)), content, mask)
    }

    private fun performHaptic(view: View) {
        val feedbackConstant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
        view.performHapticFeedback(feedbackConstant)
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        resources.displayMetrics
    ).toInt()
}
