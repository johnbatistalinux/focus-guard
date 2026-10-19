package com.focusguard.app.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
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
    private lateinit var rewardTimeSavedText: TextView
    private lateinit var rewardEquivalentText: TextView
    private lateinit var rewardConsistencyText: TextView

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
            text = "FocusGuard v1.2"
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
            background = cardBackground("#38BDF8", null, cornerDp = 10)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        val btnMargin = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12) }

        serviceCard.addView(serviceStatusText)
        serviceCard.addView(serviceActionBtn, btnMargin)

        // Card de Conquistas & Reforço Positivo
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
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBackground("#062E22", "#059669")
            val pad = dp(16)
            setPadding(pad, pad, pad, pad)
        }

        rewardLevelText = TextView(this).apply {
            setTextColor(Color.parseColor("#34D399"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
        }

        rewardMasteryText = TextView(this).apply {
            setTextColor(Color.parseColor("#ECFDF5"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(6), 0, 0)
        }

        rewardTimeSavedText = TextView(this).apply {
            setTextColor(Color.parseColor("#A7F3D0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(6), 0, 0)
        }

        rewardEquivalentText = TextView(this).apply {
            setTextColor(Color.parseColor("#6EE7B7"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(2), 0, 0)
        }

        rewardConsistencyText = TextView(this).apply {
            setTextColor(Color.parseColor("#D1FAE5"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.MONOSPACE
            setPadding(0, dp(10), 0, 0)
        }

        val pillHintText = TextView(this).apply {
            text = "💡 Na sessão: toque na pílula p/ mudar de lado • segure p/ encerrar com bônus"
            setTextColor(Color.parseColor("#A7F3D0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(0, dp(8), 0, 0)
        }

        card.addView(rewardLevelText)
        card.addView(rewardMasteryText)
        card.addView(rewardTimeSavedText)
        card.addView(rewardEquivalentText)
        card.addView(rewardConsistencyText)
        card.addView(pillHintText)
        return card
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
        rewardLevelText.text = "Nível de Foco: ${summary.focusLevelTitle}"
        rewardMasteryText.text =
            "🛡️ Hoje: ${summary.abortedToday}/${summary.attemptsToday} impulsos vencidos (${summary.masteryPercentToday}%) • ${summary.voluntaryExitsToday} saída(s) consciente(s)"
        rewardTimeSavedText.text =
            "🌱 Tempo resgatado: +${summary.reclaimedMinutesToday} min hoje • ${formatMinutesSummary(summary.totalReclaimedMinutes)} no total"
        rewardEquivalentText.text = summary.tangibleEquivalentText
        rewardConsistencyText.text = "7d: [${summary.last7DaysDots}] • ${summary.consistencySummaryText}"

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

        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val nameView = TextView(this).apply {
            text = app.displayName
            setTextColor(Color.parseColor("#F1F5F9"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
        }

        val statusSuffix = if (isCooldown) " • Em Cooldown" else ""
        val statsView = TextView(this).apply {
            text = "Hoje: ${usedMinutes}/${quotaMinutes} min • $sessionsToday sessão(ões)$statusSuffix"
            setTextColor(if (isCooldown) Color.parseColor("#F87171") else Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(2), 0, 0)
        }

        textColumn.addView(nameView)
        textColumn.addView(statsView)

        val toggle = Switch(this).apply {
            this.isChecked = isChecked
            isEnabled = !isLocked
            alpha = if (isLocked) 0.45f else 1.0f
            setOnCheckedChangeListener { _, checked -> onToggle(checked) }
        }

        row.addView(textColumn)
        row.addView(toggle)
        return row
    }

    private fun renderParamsList(config: FocusConfig, isLocked: Boolean) {
        paramsContainer.removeAllViews()

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
                    updateConfig(config.copy(sessionDurationMinutes = next))
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
                    updateConfig(config.copy(dailyQuotaMinutes = next))
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
            background = cardBackground("#1E293B", "#334155", cornerDp = 8)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { onCycleHabit() }
        }

        row.addView(textCol)
        row.addView(cycleBtn)
        return row
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
            background = cardBackground("#1E293B", "#334155", cornerDp = 8)
            val size = dp(38)
            layoutParams = LinearLayout.LayoutParams(size, size)
            setPadding(0, 0, 0, 0)
            setOnClickListener { onClick() }
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

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        resources.displayMetrics
    ).toInt()
}
