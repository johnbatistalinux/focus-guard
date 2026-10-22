package com.focusguard.app.domain

/**
 * Grupo de limites de foco com cota e temporizadores independentes.
 *
 * @property id Identificador único estável do grupo (ex: "group_social", "group_games").
 * @property name Nome legível do grupo (ex: "Redes Sociais", "Jogos").
 * @property iconEmoji Emoji representativo do grupo (ex: "📱", "🎮").
 * @property enabled Se o grupo está ativo para monitoramento e intervenções.
 * @property appIds Conjunto de IDs de [TargetApp] pertencentes a este grupo.
 * @property mindfulPauseSeconds Duração da pausa de respiração para apps deste grupo.
 * @property sessionDurationMinutes Duração máxima de cada sessão desbloqueada no grupo.
 * @property cooldownMinutes Período de resfriamento pós-sessão para o grupo.
 * @property dailyQuotaMinutes Teto diário somado (se sharedQuota = true) ou por app no grupo.
 * @property progressivePauseEnabled Se verdadeiro, adiciona +5s por sessão no grupo.
 * @property sharedQuota Se verdadeiro, todos os apps deste grupo dividem a mesma cota diária e cooldown.
 */
data class FocusGroup(
    val id: String,
    val name: String,
    val iconEmoji: String = "📱",
    val enabled: Boolean = true,
    val appIds: Set<String> = emptySet(),
    val mindfulPauseSeconds: Int = 10,
    val sessionDurationMinutes: Int = 5,
    val cooldownMinutes: Int = 10,
    val dailyQuotaMinutes: Int = 30,
    val progressivePauseEnabled: Boolean = true,
    val sharedQuota: Boolean = true
) {
    init {
        require(id.isNotBlank()) { "id do grupo não pode ser vazio." }
        require(name.isNotBlank()) { "name do grupo não pode ser vazio." }
        require(mindfulPauseSeconds in 3..120) { "mindfulPauseSeconds deve estar entre 3 e 120s." }
        require(sessionDurationMinutes in 1..60) { "sessionDurationMinutes deve estar entre 1 e 60 min." }
        require(cooldownMinutes in 1..180) { "cooldownMinutes deve estar entre 1 e 180 min." }
        require(dailyQuotaMinutes in 5..240) { "dailyQuotaMinutes deve estar entre 5 e 240 min." }
    }

    val sessionDurationSeconds: Long get() = sessionDurationMinutes * 60L
    val cooldownSeconds: Long get() = cooldownMinutes * 60L
    val dailyQuotaSeconds: Long get() = dailyQuotaMinutes * 60L

    companion object {
        const val DEFAULT_GROUP_SOCIAL_ID = "group_social"
        const val DEFAULT_GROUP_GAMES_ID = "group_games"
    }
}

/**
 * Configuração global escolhida pelo usuário.
 *
 * @property enabledAppIds IDs das redes sociais do [SocialAppCatalog] ativas (ex: "instagram", "tiktok").
 * @property mindfulPauseSeconds Tempo obrigatório de respiração antes de abrir (padrão: 10s).
 * @property sessionDurationMinutes Tempo máximo de cada sessão liberada (padrão: 5 min).
 * @property cooldownMinutes Tempo de bloqueio pós-sessão para impedir reabertura impulsiva (padrão: 10 min).
 * @property dailyQuotaMinutes Teto máximo de uso somado por dia para cada rede social (padrão: 30 min).
 * @property progressivePauseEnabled Se verdadeiro, adiciona +5s na pausa a cada sessão iniciada do app no dia.
 * @property selectedAnchorHabit Hábito positivo exibido no botão principal de desistência da pausa consciente.
 * @property groups Lista de categorias independentes com cotas e temporizadores próprios.
 * @property customApps Aplicativos instalados pelo usuário adicionados dinamicamente aos grupos.
 * @property sharedQuotaEnabled Configuração padrão de cotas compartilhadas em novos grupos.
 */
data class FocusConfig(
    val enabledAppIds: Set<String> = setOf("instagram", "tiktok"),
    val mindfulPauseSeconds: Int = 10,
    val sessionDurationMinutes: Int = 5,
    val cooldownMinutes: Int = 10,
    val dailyQuotaMinutes: Int = 30,
    val progressivePauseEnabled: Boolean = true,
    val selectedAnchorHabit: String = DEFAULT_ANCHOR_HABIT,
    val groups: List<FocusGroup> = emptyList(),
    val customApps: List<TargetApp> = emptyList(),
    val sharedQuotaEnabled: Boolean = true
) {
    init {
        require(mindfulPauseSeconds in 3..120) { "mindfulPauseSeconds deve estar entre 3 e 120 segundos." }
        require(sessionDurationMinutes in 1..60) { "sessionDurationMinutes deve estar entre 1 e 60 minutos." }
        require(cooldownMinutes in 1..180) { "cooldownMinutes deve estar entre 1 e 180 minutos." }
        require(dailyQuotaMinutes in 5..240) { "dailyQuotaMinutes deve estar entre 5 e 240 minutos." }
        require(selectedAnchorHabit.isNotBlank()) { "selectedAnchorHabit não pode ser vazio." }
    }

    val sessionDurationSeconds: Long get() = sessionDurationMinutes * 60L
    val cooldownSeconds: Long get() = cooldownMinutes * 60L
    val dailyQuotaSeconds: Long get() = dailyQuotaMinutes * 60L

    fun getEffectiveGroups(): List<FocusGroup> {
        if (groups.isNotEmpty()) return groups
        return listOf(
            FocusGroup(
                id = FocusGroup.DEFAULT_GROUP_SOCIAL_ID,
                name = "Redes Sociais",
                iconEmoji = "📱",
                enabled = true,
                appIds = enabledAppIds,
                mindfulPauseSeconds = mindfulPauseSeconds,
                sessionDurationMinutes = sessionDurationMinutes,
                cooldownMinutes = cooldownMinutes,
                dailyQuotaMinutes = dailyQuotaMinutes,
                progressivePauseEnabled = progressivePauseEnabled,
                sharedQuota = sharedQuotaEnabled
            ),
            FocusGroup(
                id = FocusGroup.DEFAULT_GROUP_GAMES_ID,
                name = "Jogos & Outros",
                iconEmoji = "🎮",
                enabled = true,
                appIds = emptySet(),
                mindfulPauseSeconds = mindfulPauseSeconds,
                sessionDurationMinutes = sessionDurationMinutes,
                cooldownMinutes = cooldownMinutes,
                dailyQuotaMinutes = dailyQuotaMinutes,
                progressivePauseEnabled = progressivePauseEnabled,
                sharedQuota = sharedQuotaEnabled
            )
        )
    }

    fun findActiveGroupForApp(appId: String): FocusGroup? {
        return getEffectiveGroups().firstOrNull { it.enabled && it.appIds.contains(appId) }
    }

    fun findGroupForApp(appId: String): FocusGroup? {
        return getEffectiveGroups().firstOrNull { it.appIds.contains(appId) }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FocusConfig) return false

        if (enabledAppIds != other.enabledAppIds) return false
        if (mindfulPauseSeconds != other.mindfulPauseSeconds) return false
        if (sessionDurationMinutes != other.sessionDurationMinutes) return false
        if (cooldownMinutes != other.cooldownMinutes) return false
        if (dailyQuotaMinutes != other.dailyQuotaMinutes) return false
        if (progressivePauseEnabled != other.progressivePauseEnabled) return false
        if (selectedAnchorHabit != other.selectedAnchorHabit) return false
        if (sharedQuotaEnabled != other.sharedQuotaEnabled) return false
        if (customApps != other.customApps) return false
        if (getEffectiveGroups() != other.getEffectiveGroups()) return false

        return true
    }

    override fun hashCode(): Int {
        var result = enabledAppIds.hashCode()
        result = 31 * result + mindfulPauseSeconds
        result = 31 * result + sessionDurationMinutes
        result = 31 * result + cooldownMinutes
        result = 31 * result + dailyQuotaMinutes
        result = 31 * result + progressivePauseEnabled.hashCode()
        result = 31 * result + selectedAnchorHabit.hashCode()
        result = 31 * result + sharedQuotaEnabled.hashCode()
        result = 31 * result + customApps.hashCode()
        result = 31 * result + getEffectiveGroups().hashCode()
        return result
    }

    companion object {
        const val DEFAULT_ANCHOR_HABIT = "Ler 5 páginas 📖"

        val PRESET_ANCHOR_HABITS: List<String> = listOf(
            "Ler 5 páginas 📖",
            "Beber um copo d'água 💧",
            "Alongar os ombros 🧘",
            "Respirar longe da tela 🌿",
            "Voltar ao estudo/trabalho 🎯"
        )
    }
}

/**
 * Perfis pré-definidos de intervenção e rigor de foco.
 */
enum class FocusPreset(
    val title: String,
    val description: String,
    val mindfulPauseSeconds: Int,
    val sessionDurationMinutes: Int,
    val cooldownMinutes: Int,
    val dailyQuotaMinutes: Int
) {
    BALANCED(
        title = "Equilíbrio",
        description = "10s pausa • 5m sessão • 10m cooldown • 30m cota",
        mindfulPauseSeconds = 10,
        sessionDurationMinutes = 5,
        cooldownMinutes = 10,
        dailyQuotaMinutes = 30
    ),
    DEEP_FOCUS(
        title = "Foco Total",
        description = "20s pausa • 3m sessão • 20m cooldown • 15m cota",
        mindfulPauseSeconds = 20,
        sessionDurationMinutes = 3,
        cooldownMinutes = 20,
        dailyQuotaMinutes = 15
    ),
    LIGHT_DETOX(
        title = "Desmame Leve",
        description = "5s pausa • 10m sessão • 5m cooldown • 60m cota",
        mindfulPauseSeconds = 5,
        sessionDurationMinutes = 10,
        cooldownMinutes = 5,
        dailyQuotaMinutes = 60
    );

    fun applyTo(config: FocusConfig): FocusConfig = config.copy(
        mindfulPauseSeconds = mindfulPauseSeconds,
        sessionDurationMinutes = sessionDurationMinutes,
        cooldownMinutes = cooldownMinutes,
        dailyQuotaMinutes = dailyQuotaMinutes
    )
}

/**
 * Estado persistido do uso diário e cooldown de um aplicativo específico.
 *
 * @property appId Identificador lógico do app no [SocialAppCatalog].
 * @property epochDay Dia do calendário local ao qual [usedDailySeconds] pertence.
 * @property usedDailySeconds Segundos consumidos hoje.
 * @property cooldownUntilRealtimeMs Timestamp monotônico (elapsedRealtime) até quando o cooldown está ativo.
 * @property sessionsStartedToday Quantidade de sessões iniciadas hoje para este app (usado na Pausa Progressiva).
 */
data class AppUsageState(
    val appId: String,
    val epochDay: Long,
    val usedDailySeconds: Long = 0L,
    val cooldownUntilRealtimeMs: Long = 0L,
    val sessionsStartedToday: Int = 0
)

/**
 * Estado persistido do sistema de Reforço Positivo e Consistência Resiliente.
 *
 * @property epochDay Dia do calendário local ao qual os contadores diários pertencem.
 * @property attemptsToday Quantidade de vezes que a barreira de Pausa Consciente abriu hoje.
 * @property abortedToday Quantidade de vezes que o usuário desistiu conscientemente na barreira hoje.
 * @property voluntaryExitsToday Quantidade de vezes que o usuário encerrou uma sessão antes do tempo zerar hoje.
 * @property reclaimedSecondsToday Segundos de vida poupados hoje (desistências na porta + sobras de saídas voluntárias).
 * @property totalReclaimedSeconds Total acumulado vitalício de segundos poupados fora do feed.
 * @property totalImpulsesBeaten Total acumulado vitalício de impulsos vencidos + saídas conscientes.
 * @property consistencyBitmask Máscara de bits dos últimos dias (bit 0 = hoje, bit 1 = ontem; 1 = dentro da cota).
 * @property trackedDaysCount Quantidade de dias acompanhados desde a instalação (1..64).
 */
data class FocusRewardState(
    val epochDay: Long,
    val attemptsToday: Int = 0,
    val abortedToday: Int = 0,
    val voluntaryExitsToday: Int = 0,
    val reclaimedSecondsToday: Long = 0L,
    val totalReclaimedSeconds: Long = 0L,
    val totalImpulsesBeaten: Int = 0,
    val consistencyBitmask: Long = 1L,
    val trackedDaysCount: Int = 1
)

/**
 * Resumo formatado de conquistas e reforço positivo para exibição na MainActivity e no Overlay.
 */
data class FocusRewardSummary(
    val attemptsToday: Int,
    val abortedToday: Int,
    val voluntaryExitsToday: Int,
    val masteryPercentToday: Int,
    val reclaimedMinutesToday: Long,
    val totalReclaimedMinutes: Long,
    val tangibleEquivalentText: String,
    val focusLevelTitle: String,
    val last7DaysDots: String,
    val consistencySummaryText: String
)

/**
 * Motivo pelo qual o acesso ao aplicativo foi bloqueado imediatamente.
 */
enum class BlockReason {
    COOLDOWN_ACTIVE,
    DAILY_QUOTA_EXCEEDED
}

/**
 * Decisão determinística retornada pelo [FocusPolicyEngine] ao receber eventos de janela ou ticks.
 */
sealed interface InterventionAction {
    /** Pacote ignorado (não monitorado) ou retorno ao Launcher; oculta qualquer overlay. */
    data object DismissOverlay : InterventionAction

    /** Evento transitório irrelevante (ex: teclado virtual subiu sobre a mesma sessão); mantém estado atual. */
    data object NoOp : InterventionAction

    /** Exige que o serviço feche o aplicativo alvo imediatamente (GLOBAL_ACTION_HOME). */
    data class KickToHome(
        val appId: String,
        val reason: BlockReason
    ) : InterventionAction

    /** Exibe a tela de Bloqueio Estrito (Cooldown ativo ou Cota Diária esgotada). */
    data class ShowHardBlock(
        val appId: String,
        val displayName: String,
        val reason: BlockReason,
        val remainingBlockSeconds: Long,
        val groupName: String = ""
    ) : InterventionAction

    /** Exibe a tela de Pausa Consciente com contagem regressiva antes de permitir a sessão. */
    data class ShowMindfulPause(
        val appId: String,
        val displayName: String,
        val remainingPauseSeconds: Int,
        val readyToStartSession: Boolean,
        val remainingDailyQuotaSeconds: Long,
        val breathingCue: String = "",
        val anchorActionLabel: String = "Fechar e voltar ao que importa",
        val rewardBannerText: String = "",
        val reclaimedMinutesOnAbort: Int = 5,
        val allowQuickMicroSession: Boolean = false,
        val groupName: String = ""
    ) : InterventionAction

    /** Sessão liberada em andamento: exibe apenas a pílula flutuante discreta no canto da tela. */
    data class ShowFloatingTimer(
        val appId: String,
        val displayName: String,
        val remainingSessionSeconds: Long,
        val remainingDailyQuotaSeconds: Long,
        val groupName: String = ""
    ) : InterventionAction
}
