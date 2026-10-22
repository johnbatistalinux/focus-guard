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
import com.focusguard.app.domain.FocusGroup
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

        val appsHeader = sectionHeader("Grupos de Limites & Aplicativos")
        appsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val paramsHeader = sectionHeader("Hábito Âncora & Parâmetros Padrão")
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

        val groups = config.getEffectiveGroups()

        for (group in groups) {
            val groupCard = buildGroupCard(group, config, today, nowMs, isLocked)
            appsContainer.addView(groupCard)
        }

        val createGroupBtn = TextView(this).apply {
            text = "✨ Criar Novo Grupo de Limite"
            setTextColor(Color.parseColor("#38BDF8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = interactiveCardBackground("#0F172A", "#38BDF8", cornerDp = 12)
            val padH = dp(16)
            val padV = dp(14)
            setPadding(padH, padV, padH, padV)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(4)
                bottomMargin = dp(16)
            }
            isEnabled = !isLocked
            alpha = if (isLocked) 0.45f else 1.0f
            setOnClickListener { view ->
                performHaptic(view)
                val newGroupId = "group_${System.currentTimeMillis()}"
                val newGroup = FocusGroup(
                    id = newGroupId,
                    name = "Nova Categoria",
                    iconEmoji = "🎯",
                    enabled = true,
                    appIds = emptySet(),
                    mindfulPauseSeconds = config.mindfulPauseSeconds,
                    sessionDurationMinutes = config.sessionDurationMinutes,
                    cooldownMinutes = config.cooldownMinutes,
                    dailyQuotaMinutes = config.dailyQuotaMinutes,
                    progressivePauseEnabled = config.progressivePauseEnabled,
                    sharedQuota = true
                )
                GroupConfigDialog(
                    context = this@MainActivity,
                    group = newGroup,
                    isNewGroup = true,
                    isLocked = isLocked,
                    onSave = { createdGroup ->
                        val currentGroups = config.getEffectiveGroups().toMutableList()
                        currentGroups.add(createdGroup)
                        val allEnabled = currentGroups.filter { it.enabled }.flatMap { it.appIds }.toSet()
                        updateConfig(config.copy(groups = currentGroups, enabledAppIds = allEnabled))
                    }
                ).show()
            }
        }
        appsContainer.addView(createGroupBtn)
    }

    private fun buildGroupCard(
        group: FocusGroup,
        config: FocusConfig,
        today: Long,
        nowMs: Long,
        isLocked: Boolean
    ): View {
        val cardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBackground("#111827", "#1E293B", cornerDp = 14)
            val pad = dp(16)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) }
        }

        // Header Row: [Emoji + Nome + Badges] --- [⚙️ Limites] [Switch]
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val titleContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val titleView = TextView(this).apply {
            text = "${group.iconEmoji} ${group.name}"
            setTextColor(if (group.enabled) Color.parseColor("#F8FAFC") else Color.parseColor("#64748B"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
        }
        titleContainer.addView(titleView)

        if (!group.enabled) {
            val inactiveBadge = TextView(this).apply {
                text = "Inativo"
                setTextColor(Color.parseColor("#94A3B8"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                background = cardBackground("#334155", null, cornerDp = 4)
                setPadding(dp(6), dp(2), dp(6), dp(2))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp(8) }
            }
            titleContainer.addView(inactiveBadge)
        }

        val groupUsage = store.getUsageState("group_${group.id}", today)
        val isGroupCooldown = group.sharedQuota &&
            groupUsage.cooldownUntilRealtimeMs > nowMs &&
            (groupUsage.cooldownUntilRealtimeMs - nowMs) <= group.cooldownSeconds * 1000L

        if (isGroupCooldown) {
            val cooldownBadge = TextView(this).apply {
                text = "⏳ Cooldown"
                setTextColor(Color.parseColor("#FCA5A5"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                typeface = Typeface.DEFAULT_BOLD
                background = cardBackground("#450A0A", "#DC2626", cornerDp = 6)
                setPadding(dp(6), dp(2), dp(6), dp(2))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp(8) }
            }
            titleContainer.addView(cooldownBadge)
        }

        val configBtn = TextView(this).apply {
            text = "⚙️ Limites"
            setTextColor(Color.parseColor("#38BDF8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            background = interactiveCardBackground("#1E293B", "#334155", cornerDp = 8)
            val padH = dp(10)
            val padV = dp(6)
            setPadding(padH, padV, padH, padV)
            setOnClickListener { view ->
                performHaptic(view)
                GroupConfigDialog(
                    context = this@MainActivity,
                    group = group,
                    isNewGroup = false,
                    isLocked = isLocked,
                    onSave = { updatedGroup ->
                        val currentGroups = config.getEffectiveGroups().toMutableList()
                        val idx = currentGroups.indexOfFirst { it.id == updatedGroup.id }
                        if (idx >= 0) {
                            currentGroups[idx] = updatedGroup
                        } else {
                            currentGroups.add(updatedGroup)
                        }
                        val allEnabled = currentGroups.filter { it.enabled }.flatMap { it.appIds }.toSet()
                        updateConfig(config.copy(groups = currentGroups, enabledAppIds = allEnabled))
                    },
                    onDelete = { groupToDelete ->
                        val currentGroups = config.getEffectiveGroups().filter { it.id != groupToDelete.id }
                        val allEnabled = currentGroups.filter { it.enabled }.flatMap { it.appIds }.toSet()
                        updateConfig(config.copy(groups = currentGroups, enabledAppIds = allEnabled))
                    }
                ).show()
            }
        }

        val groupSwitch = Switch(this).apply {
            isChecked = group.enabled
            isEnabled = !isLocked
            alpha = if (isLocked) 0.45f else 1.0f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(8) }
            setOnCheckedChangeListener { _, checked ->
                val currentGroups = config.getEffectiveGroups().map { g ->
                    if (g.id == group.id) g.copy(enabled = checked) else g
                }
                val allEnabled = currentGroups.filter { it.enabled }.flatMap { it.appIds }.toSet()
                updateConfig(config.copy(groups = currentGroups, enabledAppIds = allEnabled))
            }
        }

        headerRow.addView(titleContainer)
        headerRow.addView(configBtn)
        headerRow.addView(groupSwitch)

        // Subtítulo e barra de progresso da cota
        var groupUsedSec = if (group.sharedQuota) groupUsage.usedDailySeconds else 0L
        for (appId in group.appIds) {
            val u = store.getUsageState(appId, today)
            if (group.sharedQuota) {
                groupUsedSec = maxOf(groupUsedSec, u.usedDailySeconds)
            } else {
                groupUsedSec += u.usedDailySeconds
            }
        }
        val groupUsedMin = groupUsedSec / 60L
        val quotaMin = group.dailyQuotaMinutes

        val quotaSubtitle = TextView(this).apply {
            val modeText = if (group.sharedQuota) "Cota compartilhada" else "Cota individual"
            text = "Hoje: ${groupUsedMin}/${quotaMin} min • $modeText • Sessão: ${group.sessionDurationMinutes}m • Pausa: ${group.mindfulPauseSeconds}s"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(8), 0, dp(4))
        }

        val quotaProgressTrack = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(4)
            ).apply {
                topMargin = dp(4)
                bottomMargin = dp(12)
            }
            background = cardBackground("#1E293B", null, cornerDp = 2)
        }

        val ratio = if (quotaMin > 0) (groupUsedMin.toFloat() / quotaMin.toFloat()).coerceIn(0f, 1f) else 0f
        val fillColor = when {
            ratio >= 1.0f -> "#EF4444"
            ratio >= 0.70f -> "#F59E0B"
            else -> "#10B981"
        }
        val quotaProgressFill = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, ratio.coerceAtLeast(0.001f))
            background = cardBackground(fillColor, null, cornerDp = 2)
        }
        val quotaProgressEmpty = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (1f - ratio).coerceAtLeast(0.001f))
        }
        quotaProgressTrack.addView(quotaProgressFill)
        quotaProgressTrack.addView(quotaProgressEmpty)

        // Lista de Apps dentro do grupo
        val appsListLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        if (group.appIds.isEmpty()) {
            val emptyAppsView = TextView(this).apply {
                text = "Nenhum aplicativo adicionado neste grupo ainda."
                setTextColor(Color.parseColor("#64748B"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(8))
            }
            appsListLayout.addView(emptyAppsView)
        } else {
            for (appId in group.appIds) {
                val app = SocialAppCatalog.findById(appId, config.customApps)
                    ?: TargetApp(
                        id = appId,
                        displayName = appId,
                        packageNames = setOf(appId),
                        brandColorHex = "#64748B",
                        isCustom = true
                    )
                val appUsage = store.getUsageState(app.id, today)
                val appUsedMin = appUsage.usedDailySeconds / 60L
                val isAppCooldown = appUsage.cooldownUntilRealtimeMs > nowMs &&
                    (appUsage.cooldownUntilRealtimeMs - nowMs) <= group.cooldownSeconds * 1000L

                val appRow = buildGroupAppRow(
                    app = app,
                    usedMinutes = appUsedMin,
                    sessionsToday = appUsage.sessionsStartedToday,
                    isCooldown = isAppCooldown,
                    isLocked = isLocked,
                    onRemove = {
                        val currentGroups = config.getEffectiveGroups().map { g ->
                            if (g.id == group.id) {
                                g.copy(appIds = g.appIds - app.id)
                            } else g
                        }
                        val allEnabled = currentGroups.filter { it.enabled }.flatMap { it.appIds }.toSet()
                        updateConfig(config.copy(groups = currentGroups, enabledAppIds = allEnabled))
                    }
                )
                appsListLayout.addView(appRow)
            }
        }

        val addAppsBtn = TextView(this).apply {
            text = "+ Adicionar Aplicativos"
            setTextColor(Color.parseColor("#38BDF8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = interactiveCardBackground("#1E293B", "#334155", cornerDp = 8)
            val padH = dp(14)
            val padV = dp(10)
            setPadding(padH, padV, padH, padV)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(10)
            }
            isEnabled = !isLocked
            alpha = if (isLocked) 0.45f else 1.0f
            setOnClickListener { view ->
                performHaptic(view)
                AppPickerDialog(
                    context = this@MainActivity,
                    group = group,
                    config = config,
                    onAppsConfirmed = { selectedIds, allCustomApps ->
                        val currentGroups = config.getEffectiveGroups().map { g ->
                            if (g.id == group.id) {
                                g.copy(appIds = selectedIds)
                            } else {
                                g.copy(appIds = g.appIds - selectedIds)
                            }
                        }
                        val allEnabled = currentGroups.filter { it.enabled }.flatMap { it.appIds }.toSet()
                        updateConfig(
                            config.copy(
                                groups = currentGroups,
                                customApps = allCustomApps,
                                enabledAppIds = allEnabled
                            )
                        )
                    }
                ).show()
            }
        }

        cardLayout.addView(headerRow)
        cardLayout.addView(quotaSubtitle)
        cardLayout.addView(quotaProgressTrack)
        cardLayout.addView(appsListLayout)
        cardLayout.addView(addAppsBtn)

        return cardLayout
    }

    private fun buildGroupAppRow(
        app: TargetApp,
        usedMinutes: Long,
        sessionsToday: Int,
        isCooldown: Boolean,
        isLocked: Boolean,
        onRemove: () -> Unit
    ): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = cardBackground("#0F172A", "#1E293B", cornerDp = 8)
            val padH = dp(12)
            val padV = dp(8)
            setPadding(padH, padV, padH, padV)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }
        }

        val brandDot = View(this).apply {
            val size = dp(8)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = dp(10)
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
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
        }
        nameRow.addView(nameView)

        if (isCooldown) {
            val cooldownBadge = TextView(this).apply {
                text = "⏳ Cooldown"
                setTextColor(Color.parseColor("#FCA5A5"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                typeface = Typeface.DEFAULT_BOLD
                background = cardBackground("#450A0A", "#DC2626", cornerDp = 4)
                setPadding(dp(4), dp(2), dp(4), dp(2))
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp(6) }
                layoutParams = params
            }
            nameRow.addView(cooldownBadge)
        }

        val statsView = TextView(this).apply {
            text = "Hoje: ${usedMinutes} min • $sessionsToday sessão(ões)"
            setTextColor(if (isCooldown) Color.parseColor("#F87171") else Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        }

        textColumn.addView(nameRow)
        textColumn.addView(statsView)

        val removeBtn = TextView(this).apply {
            text = "✕"
            setTextColor(Color.parseColor("#EF4444"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val pad = dp(8)
            setPadding(pad, pad, pad, pad)
            isEnabled = !isLocked
            alpha = if (isLocked) 0.3f else 1.0f
            setOnClickListener { view ->
                performHaptic(view)
                onRemove()
            }
        }

        row.addView(brandDot)
        row.addView(textColumn)
        row.addView(removeBtn)

        return row
    }

    private fun updateGlobalAndDefaultGroup(
        config: FocusConfig,
        transform: (FocusConfig) -> FocusConfig
    ) {
        val updated = transform(config)
        val currentGroups = config.getEffectiveGroups().toMutableList()
        val socialIdx = currentGroups.indexOfFirst { it.id == FocusGroup.DEFAULT_GROUP_SOCIAL_ID }
        if (socialIdx >= 0) {
            val social = currentGroups[socialIdx]
            currentGroups[socialIdx] = social.copy(
                mindfulPauseSeconds = updated.mindfulPauseSeconds,
                sessionDurationMinutes = updated.sessionDurationMinutes,
                cooldownMinutes = updated.cooldownMinutes,
                dailyQuotaMinutes = updated.dailyQuotaMinutes,
                progressivePauseEnabled = updated.progressivePauseEnabled
            )
        }
        updateConfig(updated.copy(groups = currentGroups))
    }

    private fun renderParamsList(config: FocusConfig, isLocked: Boolean) {
        paramsContainer.removeAllViews()

        // Fileira de Perfis Rápidos de Foco (aplica aos parâmetros padrão e Redes Sociais)
        paramsContainer.addView(buildPresetsRow(config, isLocked))

        paramsContainer.addView(
            buildStepperRow(
                label = "Pausa Consciente Base (Padrão)",
                valueText = "${config.mindfulPauseSeconds} seg",
                isLocked = isLocked,
                onDecrement = {
                    val next = (config.mindfulPauseSeconds - 5).coerceAtLeast(5)
                    updateGlobalAndDefaultGroup(config) { it.copy(mindfulPauseSeconds = next) }
                },
                onIncrement = {
                    val next = (config.mindfulPauseSeconds + 5).coerceAtMost(60)
                    updateGlobalAndDefaultGroup(config) { it.copy(mindfulPauseSeconds = next) }
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
                    updateGlobalAndDefaultGroup(config) { it.copy(progressivePauseEnabled = enabled) }
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
                label = "Duração da Sessão Liberada (Padrão)",
                valueText = "${config.sessionDurationMinutes} min",
                isLocked = isLocked,
                onDecrement = {
                    val next = (config.sessionDurationMinutes - 1).coerceAtLeast(1)
                    updateGlobalAndDefaultGroup(config) { it.copy(sessionDurationMinutes = next) }
                },
                onIncrement = {
                    val next = (config.sessionDurationMinutes + 1).coerceAtMost(30)
                    val coercedDailyQuota = maxOf(config.dailyQuotaMinutes, next)
                    updateGlobalAndDefaultGroup(config) {
                        it.copy(
                            sessionDurationMinutes = next,
                            dailyQuotaMinutes = coercedDailyQuota
                        )
                    }
                }
            )
        )

        paramsContainer.addView(
            buildStepperRow(
                label = "Cooldown Pós-Sessão (Padrão)",
                valueText = "${config.cooldownMinutes} min",
                isLocked = isLocked,
                onDecrement = {
                    val next = (config.cooldownMinutes - 5).coerceAtLeast(5)
                    updateGlobalAndDefaultGroup(config) { it.copy(cooldownMinutes = next) }
                },
                onIncrement = {
                    val next = (config.cooldownMinutes + 5).coerceAtMost(60)
                    updateGlobalAndDefaultGroup(config) { it.copy(cooldownMinutes = next) }
                }
            )
        )

        paramsContainer.addView(
            buildStepperRow(
                label = "Cota Máxima Diária (Padrão)",
                valueText = "${config.dailyQuotaMinutes} min",
                isLocked = isLocked,
                onDecrement = {
                    val next = (config.dailyQuotaMinutes - 5).coerceAtLeast(5)
                    val coercedSession = minOf(config.sessionDurationMinutes, next)
                    updateGlobalAndDefaultGroup(config) {
                        it.copy(
                            dailyQuotaMinutes = next,
                            sessionDurationMinutes = coercedSession
                        )
                    }
                },
                onIncrement = {
                    val next = (config.dailyQuotaMinutes + 5).coerceAtMost(120)
                    updateGlobalAndDefaultGroup(config) { it.copy(dailyQuotaMinutes = next) }
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
                    updateGlobalAndDefaultGroup(config) { preset.applyTo(it) }
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
