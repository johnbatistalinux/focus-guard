package com.focusguard.app.domain

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
 */
data class FocusConfig(
    val enabledAppIds: Set<String> = setOf("instagram", "tiktok"),
    val mindfulPauseSeconds: Int = 10,
    val sessionDurationMinutes: Int = 5,
    val cooldownMinutes: Int = 10,
    val dailyQuotaMinutes: Int = 30,
    val progressivePauseEnabled: Boolean = true,
    val selectedAnchorHabit: String = DEFAULT_ANCHOR_HABIT
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
        val remainingBlockSeconds: Long
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
        val allowQuickMicroSession: Boolean = false
    ) : InterventionAction

    /** Sessão liberada em andamento: exibe apenas a pílula flutuante discreta no canto da tela. */
    data class ShowFloatingTimer(
        val appId: String,
        val displayName: String,
        val remainingSessionSeconds: Long,
        val remainingDailyQuotaSeconds: Long
    ) : InterventionAction
}
