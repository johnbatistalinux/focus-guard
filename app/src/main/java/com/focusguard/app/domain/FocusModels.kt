package com.focusguard.app.domain

/**
 * Configuração global escolhida pelo usuário.
 *
 * @property enabledAppIds IDs das redes sociais do [SocialAppCatalog] ativas (ex: "instagram", "tiktok").
 * @property mindfulPauseSeconds Tempo obrigatório de respiração antes de abrir (padrão: 10s).
 * @property sessionDurationMinutes Tempo máximo de cada sessão liberada (padrão: 5 min).
 * @property cooldownMinutes Tempo de bloqueio pós-sessão para impedir reabertura impulsiva (padrão: 10 min).
 * @property dailyQuotaMinutes Teto máximo de uso somado por dia para cada rede social (padrão: 30 min).
 */
data class FocusConfig(
    val enabledAppIds: Set<String> = setOf("instagram", "tiktok"),
    val mindfulPauseSeconds: Int = 10,
    val sessionDurationMinutes: Int = 5,
    val cooldownMinutes: Int = 10,
    val dailyQuotaMinutes: Int = 30
) {
    init {
        require(mindfulPauseSeconds in 3..120) { "mindfulPauseSeconds deve estar entre 3 e 120 segundos." }
        require(sessionDurationMinutes in 1..60) { "sessionDurationMinutes deve estar entre 1 e 60 minutos." }
        require(cooldownMinutes in 1..180) { "cooldownMinutes deve estar entre 1 e 180 minutos." }
        require(dailyQuotaMinutes in 5..240) { "dailyQuotaMinutes deve estar entre 5 e 240 minutos." }
    }

    val sessionDurationSeconds: Long get() = sessionDurationMinutes * 60L
    val cooldownSeconds: Long get() = cooldownMinutes * 60L
    val dailyQuotaSeconds: Long get() = dailyQuotaMinutes * 60L
}

/**
 * Estado persistido do uso diário e cooldown de um aplicativo específico.
 *
 * @property appId Identificador lógico do app no [SocialAppCatalog].
 * @property epochDay Dia do calendário local ao qual [usedDailySeconds] pertence.
 * @property usedDailySeconds Segundos consumidos hoje.
 * @property cooldownUntilRealtimeMs Timestamp monotônico (elapsedRealtime) até quando o cooldown está ativo.
 */
data class AppUsageState(
    val appId: String,
    val epochDay: Long,
    val usedDailySeconds: Long = 0L,
    val cooldownUntilRealtimeMs: Long = 0L
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
        val remainingDailyQuotaSeconds: Long
    ) : InterventionAction

    /** Sessão liberada em andamento: exibe apenas a pílula flutuante discreta no canto da tela. */
    data class ShowFloatingTimer(
        val appId: String,
        val displayName: String,
        val remainingSessionSeconds: Long,
        val remainingDailyQuotaSeconds: Long
    ) : InterventionAction
}
