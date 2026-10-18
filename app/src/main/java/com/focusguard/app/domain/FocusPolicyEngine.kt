package com.focusguard.app.domain

/**
 * Motor central de políticas anti-doomscrolling (100% Kotlin puro, zero dependência do Android SDK).
 *
 * Responsável por:
 * 1. Avaliar aberturas de pacotes e filtrar eventos transitórios (teclados/IME, SystemUI).
 * 2. Conduzir a Pausa Consciente (ex: 10s) antes de permitir o uso.
 * 3. Controlar a Sessão Ativa (ex: 5 min) com pausa automática caso o usuário saia do app.
 * 4. Acionar o Cooldown pós-sessão (ex: 10 min) e respeitar a Cota Diária (ex: 30 min).
 * 5. Determinar se a Trava Anti-Sabotagem nas configurações está ativa.
 */
class FocusPolicyEngine(
    private val store: FocusPolicyStore,
    private val timeProvider: TimeProvider,
    private val catalog: SocialAppCatalog = SocialAppCatalog
) {

    private sealed interface EngineState {
        data object Idle : EngineState

        data class InMindfulPause(
            val app: TargetApp,
            val remainingSeconds: Int
        ) : EngineState

        data class InActiveSession(
            val app: TargetApp,
            val remainingSessionSeconds: Long,
            val isPausedInBackground: Boolean = false,
            val pausedAtRealtimeMs: Long = 0L
        ) : EngineState

        data class InHardBlock(
            val app: TargetApp,
            val reason: BlockReason
        ) : EngineState
    }

    private var currentState: EngineState = EngineState.Idle

    /**
     * Pacotes de sistema que abrem janelas sobre o app alvo (como teclado virtual ou controle de volume)
     * e NÃO devem cancelar nem pausar a sessão ativa.
     */
    fun isTransientSystemPackage(packageName: String): Boolean {
        val lower = packageName.lowercase()
        return lower == "com.android.systemui" ||
            lower.contains("inputmethod") ||
            lower.contains("keyboard") ||
            lower == "com.google.android.gms" ||
            lower == "com.focusguard.app" ||
            lower == "com.focusguard.app.debug"
    }

    /**
     * Processa a mudança de janela em primeiro plano reportada pelo AccessibilityService.
     */
    fun onWindowPackageChanged(packageName: String): InterventionAction {
        if (packageName.isBlank() || isTransientSystemPackage(packageName)) {
            return InterventionAction.NoOp
        }

        val config = store.getConfig()
        val targetApp = catalog.findByPackageName(packageName)

        // Se o usuário foi para o Launcher ou outro app não monitorado (ex: WhatsApp)
        if (targetApp == null || targetApp.id !in config.enabledAppIds) {
            return handleExitToNonMonitoredApp()
        }

        val nowMs = timeProvider.elapsedRealtimeMs()
        val today = timeProvider.currentEpochDay()
        val usage = getSanitizedUsageState(targetApp.id, today, nowMs, config)

        // 1. Verifica se a Cota Diária já esgotou
        val remainingDailySeconds = (config.dailyQuotaSeconds - usage.usedDailySeconds).coerceAtLeast(0L)
        if (remainingDailySeconds <= 0L) {
            currentState = EngineState.InHardBlock(targetApp, BlockReason.DAILY_QUOTA_EXCEEDED)
            return InterventionAction.ShowHardBlock(
                appId = targetApp.id,
                displayName = targetApp.displayName,
                reason = BlockReason.DAILY_QUOTA_EXCEEDED,
                remainingBlockSeconds = 0L
            )
        }

        // 2. Verifica se está em período de Cooldown pós-sessão
        if (usage.cooldownUntilRealtimeMs > nowMs) {
            val remainingCooldownSec = ((usage.cooldownUntilRealtimeMs - nowMs + 999L) / 1000L)
            currentState = EngineState.InHardBlock(targetApp, BlockReason.COOLDOWN_ACTIVE)
            return InterventionAction.ShowHardBlock(
                appId = targetApp.id,
                displayName = targetApp.displayName,
                reason = BlockReason.COOLDOWN_ACTIVE,
                remainingBlockSeconds = remainingCooldownSec
            )
        }

        // 3. Verifica se já existe estado ativo para este mesmo app
        when (val state = currentState) {
            is EngineState.InActiveSession -> {
                if (state.app.id == targetApp.id) {
                    val awayMs = if (state.isPausedInBackground) (nowMs - state.pausedAtRealtimeMs).coerceAtLeast(0L) else 0L
                    val graceLimitMs = config.sessionDurationSeconds * 1000L
                    if (awayMs <= graceLimitMs && state.remainingSessionSeconds > 0L) {
                        val effectiveRemaining = minOf(state.remainingSessionSeconds, remainingDailySeconds)
                        currentState = state.copy(
                            remainingSessionSeconds = effectiveRemaining,
                            isPausedInBackground = false,
                            pausedAtRealtimeMs = 0L
                        )
                        return InterventionAction.ShowFloatingTimer(
                            appId = targetApp.id,
                            displayName = targetApp.displayName,
                            remainingSessionSeconds = effectiveRemaining,
                            remainingDailyQuotaSeconds = remainingDailySeconds
                        )
                    }
                }
            }
            is EngineState.InMindfulPause -> {
                if (state.app.id == targetApp.id) {
                    return InterventionAction.ShowMindfulPause(
                        appId = targetApp.id,
                        displayName = targetApp.displayName,
                        remainingPauseSeconds = state.remainingSeconds,
                        readyToStartSession = state.remainingSeconds <= 0,
                        remainingDailyQuotaSeconds = remainingDailySeconds
                    )
                }
            }
            else -> Unit
        }

        // 4. Inicia nova Pausa Consciente
        val initialPause = config.mindfulPauseSeconds
        currentState = EngineState.InMindfulPause(targetApp, initialPause)
        return InterventionAction.ShowMindfulPause(
            appId = targetApp.id,
            displayName = targetApp.displayName,
            remainingPauseSeconds = initialPause,
            readyToStartSession = false,
            remainingDailyQuotaSeconds = remainingDailySeconds
        )
    }

    /**
     * Chamado quando o usuário confirma o início da sessão após concluir a Pausa Consciente.
     */
    fun onConfirmStartSession(): InterventionAction {
        val state = currentState
        if (state !is EngineState.InMindfulPause || state.remainingSeconds > 0) {
            return InterventionAction.NoOp
        }

        val config = store.getConfig()
        val nowMs = timeProvider.elapsedRealtimeMs()
        val today = timeProvider.currentEpochDay()
        val usage = getSanitizedUsageState(state.app.id, today, nowMs, config)
        val remainingDailySeconds = (config.dailyQuotaSeconds - usage.usedDailySeconds).coerceAtLeast(0L)

        if (remainingDailySeconds <= 0L) {
            currentState = EngineState.InHardBlock(state.app, BlockReason.DAILY_QUOTA_EXCEEDED)
            return InterventionAction.ShowHardBlock(
                appId = state.app.id,
                displayName = state.app.displayName,
                reason = BlockReason.DAILY_QUOTA_EXCEEDED,
                remainingBlockSeconds = 0L
            )
        }

        val sessionSeconds = minOf(config.sessionDurationSeconds, remainingDailySeconds)
        currentState = EngineState.InActiveSession(
            app = state.app,
            remainingSessionSeconds = sessionSeconds,
            isPausedInBackground = false
        )

        return InterventionAction.ShowFloatingTimer(
            appId = state.app.id,
            displayName = state.app.displayName,
            remainingSessionSeconds = sessionSeconds,
            remainingDailyQuotaSeconds = remainingDailySeconds
        )
    }

    /**
     * Chamado quando o usuário clica em "Fechar e voltar à Home" durante a pausa ou bloqueio.
     */
    fun onUserAborted(): InterventionAction {
        val state = currentState
        if (state is EngineState.InMindfulPause || state is EngineState.InHardBlock) {
            currentState = EngineState.Idle
        }
        return InterventionAction.DismissOverlay
    }

    /**
     * Processa um pulso de 1 segundo (1 Hz) do cronômetro principal.
     * Só deve rodar quando houver uma Pausa Consciente, Bloqueio de Cooldown ou Sessão Ativa em tela.
     */
    fun onSecondTick(): InterventionAction {
        val config = store.getConfig()
        val nowMs = timeProvider.elapsedRealtimeMs()
        val today = timeProvider.currentEpochDay()

        return when (val state = currentState) {
            EngineState.Idle -> InterventionAction.NoOp

            is EngineState.InMindfulPause -> {
                val usage = getSanitizedUsageState(state.app.id, today, nowMs, config)
                val remainingDaily = (config.dailyQuotaSeconds - usage.usedDailySeconds).coerceAtLeast(0L)
                val nextSec = (state.remainingSeconds - 1).coerceAtLeast(0)
                currentState = state.copy(remainingSeconds = nextSec)
                InterventionAction.ShowMindfulPause(
                    appId = state.app.id,
                    displayName = state.app.displayName,
                    remainingPauseSeconds = nextSec,
                    readyToStartSession = nextSec == 0,
                    remainingDailyQuotaSeconds = remainingDaily
                )
            }

            is EngineState.InActiveSession -> {
                if (state.isPausedInBackground) {
                    return InterventionAction.NoOp
                }

                val usage = getSanitizedUsageState(state.app.id, today, nowMs, config)
                val newUsedDaily = usage.usedDailySeconds + 1L
                val newRemainingSession = state.remainingSessionSeconds - 1L
                val newRemainingDaily = (config.dailyQuotaSeconds - newUsedDaily).coerceAtLeast(0L)

                // Se a sessão de 5 min acabou OU a cota diária acabou neste segundo
                if (newRemainingSession <= 0L || newRemainingDaily <= 0L) {
                    val cooldownUntil = nowMs + (config.cooldownSeconds * 1000L)
                    store.saveUsageState(
                        usage.copy(
                            usedDailySeconds = newUsedDaily,
                            cooldownUntilRealtimeMs = cooldownUntil
                        )
                    )
                    currentState = EngineState.Idle
                    val reason = if (newRemainingDaily <= 0L) {
                        BlockReason.DAILY_QUOTA_EXCEEDED
                    } else {
                        BlockReason.COOLDOWN_ACTIVE
                    }
                    InterventionAction.KickToHome(appId = state.app.id, reason = reason)
                } else {
                    store.saveUsageState(usage.copy(usedDailySeconds = newUsedDaily))
                    currentState = state.copy(remainingSessionSeconds = newRemainingSession)
                    InterventionAction.ShowFloatingTimer(
                        appId = state.app.id,
                        displayName = state.app.displayName,
                        remainingSessionSeconds = newRemainingSession,
                        remainingDailyQuotaSeconds = newRemainingDaily
                    )
                }
            }

            is EngineState.InHardBlock -> {
                if (state.reason == BlockReason.DAILY_QUOTA_EXCEEDED) {
                    return InterventionAction.ShowHardBlock(
                        appId = state.app.id,
                        displayName = state.app.displayName,
                        reason = BlockReason.DAILY_QUOTA_EXCEEDED,
                        remainingBlockSeconds = 0L
                    )
                }
                val usage = getSanitizedUsageState(state.app.id, today, nowMs, config)
                if (usage.cooldownUntilRealtimeMs <= nowMs) {
                    // Cooldown acabou! Transiciona direto para a Pausa Consciente
                    val initialPause = config.mindfulPauseSeconds
                    val remainingDaily = (config.dailyQuotaSeconds - usage.usedDailySeconds).coerceAtLeast(0L)
                    currentState = EngineState.InMindfulPause(state.app, initialPause)
                    InterventionAction.ShowMindfulPause(
                        appId = state.app.id,
                        displayName = state.app.displayName,
                        remainingPauseSeconds = initialPause,
                        readyToStartSession = false,
                        remainingDailyQuotaSeconds = remainingDaily
                    )
                } else {
                    val remainingCooldownSec = ((usage.cooldownUntilRealtimeMs - nowMs + 999L) / 1000L)
                    InterventionAction.ShowHardBlock(
                        appId = state.app.id,
                        displayName = state.app.displayName,
                        reason = BlockReason.COOLDOWN_ACTIVE,
                        remainingBlockSeconds = remainingCooldownSec
                    )
                }
            }
        }
    }

    /**
     * Verifica se a Trava Anti-Sabotagem está ativa (algum app habilitado está em Cooldown
     * ou com a Cota Diária estourada). Impede que o usuário desative o bloqueio por impulso.
     */
    fun getSettingsLockStatus(): SettingsLockStatus {
        val config = store.getConfig()
        val nowMs = timeProvider.elapsedRealtimeMs()
        val today = timeProvider.currentEpochDay()

        for (appId in config.enabledAppIds) {
            val app = catalog.SUPPORTED_APPS.find { it.id == appId } ?: continue
            val usage = getSanitizedUsageState(appId, today, nowMs, config)

            if (usage.usedDailySeconds >= config.dailyQuotaSeconds) {
                return SettingsLockStatus(
                    isLocked = true,
                    reasonMessage = "Configurações travadas: a cota diária do ${app.displayName} já foi atingida hoje."
                )
            }
            if (usage.cooldownUntilRealtimeMs > nowMs) {
                val remainingMin = ((usage.cooldownUntilRealtimeMs - nowMs + 59_999L) / 60_000L)
                return SettingsLockStatus(
                    isLocked = true,
                    reasonMessage = "Configurações travadas: ${app.displayName} está em cooldown por mais ${remainingMin} min."
                )
            }
        }
        return SettingsLockStatus(isLocked = false, reasonMessage = null)
    }

    /**
     * Indica se o ticker de 1 Hz precisa continuar rodando.
     */
    fun shouldRunTicker(): Boolean = when (val state = currentState) {
        EngineState.Idle -> false
        is EngineState.InMindfulPause -> state.remainingSeconds > 0
        is EngineState.InActiveSession -> !state.isPausedInBackground
        is EngineState.InHardBlock -> state.reason == BlockReason.COOLDOWN_ACTIVE
    }

    private fun handleExitToNonMonitoredApp(): InterventionAction {
        when (val state = currentState) {
            is EngineState.InActiveSession -> {
                if (!state.isPausedInBackground) {
                    currentState = state.copy(
                        isPausedInBackground = true,
                        pausedAtRealtimeMs = timeProvider.elapsedRealtimeMs()
                    )
                }
            }
            is EngineState.InMindfulPause, is EngineState.InHardBlock -> {
                currentState = EngineState.Idle
            }
            EngineState.Idle -> Unit
        }
        return InterventionAction.DismissOverlay
    }

    private fun getSanitizedUsageState(
        appId: String,
        todayEpochDay: Long,
        nowRealtimeMs: Long,
        config: FocusConfig
    ): AppUsageState {
        val raw = store.getUsageState(appId, todayEpochDay)
        val maxPossibleCooldownMs = nowRealtimeMs + (config.cooldownSeconds * 1000L)

        // Se o aparelho foi reiniciado (elapsedRealtimeMs voltou perto de zero), limpa cooldown órfão
        if (raw.cooldownUntilRealtimeMs > maxPossibleCooldownMs) {
            val fixed = raw.copy(cooldownUntilRealtimeMs = 0L)
            store.saveUsageState(fixed)
            return fixed
        }
        return raw
    }
}

data class SettingsLockStatus(
    val isLocked: Boolean,
    val reasonMessage: String?
)
