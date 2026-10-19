package com.focusguard.app.domain

/**
 * Motor central de políticas anti-doomscrolling (100% Kotlin puro, zero dependência do Android SDK).
 *
 * Responsável por:
 * 1. Avaliar aberturas de pacotes e filtrar eventos transitórios (teclados/IME, SystemUI).
 * 2. Conduzir a Pausa Consciente (com Pausa Progressiva e Guia de Respiração 1 Hz).
 * 3. Controlar a Sessão Ativa (Completa ou Micro-Sessão de 1 min) com pausa automática ao sair.
 * 4. Contabilizar Reforço Positivo (Impulsos Vencidos, Tempo Resgatado, Bônus de Saída Voluntária e Consistência).
 * 5. Acionar o Cooldown pós-sessão e respeitar a Cota Diária e a Trava Anti-Sabotagem.
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
            val sessionEpochDay: Long,
            val initialSessionSeconds: Long,
            val remainingSessionSeconds: Long,
            val isQuickMicroSession: Boolean = false,
            val isPausedInBackground: Boolean = false,
            val pausedAtRealtimeMs: Long = 0L
        ) : EngineState

        data class InHardBlock(
            val app: TargetApp,
            val reason: BlockReason
        ) : EngineState
    }

    private var currentState: EngineState = EngineState.Idle
    private val pausedSessionsByAppId = HashMap<String, EngineState.InActiveSession>(4)

    /**
     * Pacotes de sistema que abrem janelas sobre o app alvo (como teclado virtual, autofill, área de transferência,
     * seletor de mídia ou volume) e NÃO devem cancelar nem pausar a sessão ativa.
     */
    fun isTransientSystemPackage(packageName: String): Boolean {
        val lower = packageName.lowercase()
        return lower == "android" ||
            lower == "com.android.systemui" ||
            lower == "com.google.android.as" ||
            lower == "com.google.android.ext.services" ||
            lower == "com.google.android.gms" ||
            lower == "com.google.android.tts" ||
            lower == "com.google.android.googlequicksearchbox" ||
            lower == "com.android.credentialmanager" ||
            lower == "com.android.captiveportallogin" ||
            lower == "com.miui.phrase" ||
            lower == "com.miui.freeform" ||
            lower == "com.samsung.android.app.cocktailbarservice" ||
            lower == "com.samsung.android.scs" ||
            lower.startsWith("com.focusguard.app") ||
            lower.contains("inputmethod") ||
            lower.contains("keyboard") ||
            lower.contains("honeyboard") ||
            lower.contains("smartsuggestions") ||
            lower.contains("icecone") ||
            lower.contains("neatart") ||
            lower.contains("handwriting") ||
            lower.contains("stickercenter") ||
            lower.contains("aremoji") ||
            lower.contains("avatarstickers") ||
            lower.contains("intelligence") ||
            lower.contains("aicore") ||
            lower.contains("bixby.service") ||
            lower.contains("bixby.agent") ||
            lower.contains("inputshare") ||
            lower.contains("swiftkey") ||
            lower.contains("gboard") ||
            lower.contains("florisboard") ||
            lower.contains("openboard") ||
            lower.contains("heliboard") ||
            lower.contains("typewise") ||
            lower.contains("fleksy") ||
            lower.contains("touchpal") ||
            lower.contains("facemoji") ||
            lower.contains("kika") ||
            lower.contains("svoiceime") ||
            lower.contains("autofill") ||
            lower.contains("clipboard") ||
            lower.contains("credentialmanager") ||
            lower.contains("samsungpass") ||
            lower.contains("permissioncontroller") ||
            lower.contains("packageinstaller") ||
            lower.contains("biometric") ||
            lower.contains("securitycenter")
    }

    /**
     * Classes de janela transitórias (como SoftInputWindow do teclado, PopupWindow ou Autofill)
     * que abrem sobre a Activity atual sem representar troca de aplicativo.
     */
    fun isTransientWindowClassName(className: String?): Boolean {
        if (className.isNullOrBlank()) return false
        val lower = className.lowercase()
        return lower.contains("softinputwindow") ||
            lower.contains("inputmethod") ||
            lower.contains("keyboard") ||
            lower.contains("honeyboard") ||
            lower.contains("suggestions") ||
            lower.contains("popupwindow") ||
            lower.contains("popupdecorview") ||
            lower.contains("autofill") ||
            lower.contains("magnifier") ||
            lower.contains("toast") ||
            lower.contains("floatingtoolbar") ||
            lower.contains("actionmode")
    }

    /**
     * Processa a mudança de janela em primeiro plano reportada pelo AccessibilityService.
     */
    fun onWindowPackageChanged(packageName: String, className: String? = null): InterventionAction {
        if (packageName.isBlank()) {
            return InterventionAction.NoOp
        }

        val isFocusGuardMainActivity = packageName.lowercase().startsWith("com.focusguard.app") &&
            className?.endsWith(".MainActivity") == true

        if (!isFocusGuardMainActivity &&
            (isTransientSystemPackage(packageName) || isTransientWindowClassName(className))
        ) {
            return InterventionAction.NoOp
        }

        val config = store.getConfig()
        val targetApp = catalog.findByPackageName(packageName)

        // Se o usuário foi para o Launcher ou outro app não monitorado (ex: WhatsApp ou MainActivity)
        if (targetApp == null || targetApp.id !in config.enabledAppIds) {
            return handleExitToNonMonitoredApp()
        }

        val nowMs = timeProvider.elapsedRealtimeMs()
        val today = timeProvider.currentEpochDay()

        // Se havia outra rede social com sessão ativa em primeiro plano, pausa-a no mapa por app
        val activeState = currentState as? EngineState.InActiveSession
        if (activeState != null && activeState.app.id != targetApp.id) {
            val pausedOther = if (!activeState.isPausedInBackground) {
                activeState.copy(isPausedInBackground = true, pausedAtRealtimeMs = nowMs)
            } else {
                activeState
            }
            pausedSessionsByAppId[activeState.app.id] = pausedOther
            currentState = EngineState.Idle
        }

        purgeExpiredBackgroundSessions(nowMs, today, config)

        val usage = getSanitizedUsageState(targetApp.id, today, nowMs, config)

        // 1. Verifica se a Cota Diária já esgotou
        val remainingDailySeconds = (config.dailyQuotaSeconds - usage.usedDailySeconds).coerceAtLeast(0L)
        if (remainingDailySeconds <= 0L) {
            pausedSessionsByAppId.remove(targetApp.id)
            markTodayQuotaExceededInReward(today)
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
            pausedSessionsByAppId.remove(targetApp.id)
            val remainingCooldownSec = ((usage.cooldownUntilRealtimeMs - nowMs + 999L) / 1000L)
            currentState = EngineState.InHardBlock(targetApp, BlockReason.COOLDOWN_ACTIVE)
            return InterventionAction.ShowHardBlock(
                appId = targetApp.id,
                displayName = targetApp.displayName,
                reason = BlockReason.COOLDOWN_ACTIVE,
                remainingBlockSeconds = remainingCooldownSec
            )
        }

        // 3. Verifica se já existe sessão ativa/pausada dentro da janela de tolerância para este app
        val existingSession = (currentState as? EngineState.InActiveSession)
            ?.takeIf { it.app.id == targetApp.id }
            ?: pausedSessionsByAppId[targetApp.id]

        if (existingSession != null && existingSession.remainingSessionSeconds > 0L) {
            val effectiveRemaining = minOf(existingSession.remainingSessionSeconds, remainingDailySeconds)
            val resumed = existingSession.copy(
                remainingSessionSeconds = effectiveRemaining,
                isPausedInBackground = false,
                pausedAtRealtimeMs = 0L
            )
            pausedSessionsByAppId.remove(targetApp.id)
            currentState = resumed
            return InterventionAction.ShowFloatingTimer(
                appId = targetApp.id,
                displayName = targetApp.displayName,
                remainingSessionSeconds = effectiveRemaining,
                remainingDailyQuotaSeconds = remainingDailySeconds
            )
        }

        val currentPause = currentState as? EngineState.InMindfulPause
        if (currentPause != null && currentPause.app.id == targetApp.id) {
            val reward = store.getRewardState(today)
            val ready = currentPause.remainingSeconds <= 0
            return buildMindfulPauseAction(
                app = targetApp,
                remainingPauseSeconds = currentPause.remainingSeconds,
                readyToStartSession = ready,
                remainingDailyQuotaSeconds = remainingDailySeconds,
                config = config,
                reward = reward
            )
        }

        // 4. Inicia nova Pausa Consciente (com incremento de tentativa do dia e Pausa Progressiva)
        val currentReward = store.getRewardState(today)
        val updatedReward = currentReward.copy(attemptsToday = currentReward.attemptsToday + 1)
        store.saveRewardState(updatedReward)

        val initialPause = computeProgressivePauseSeconds(config, usage.sessionsStartedToday)
        currentState = EngineState.InMindfulPause(targetApp, initialPause)
        return buildMindfulPauseAction(
            app = targetApp,
            remainingPauseSeconds = initialPause,
            readyToStartSession = false,
            remainingDailyQuotaSeconds = remainingDailySeconds,
            config = config,
            reward = updatedReward
        )
    }

    /**
     * Chamado quando a tela do dispositivo é desligada (Power Off / Lock Screen),
     * pausando imediatamente qualquer sessão ativa para não descontar a cota no bolso.
     */
    fun onScreenTurnedOff(): InterventionAction = handleExitToNonMonitoredApp()

    private fun purgeExpiredBackgroundSessions(
        nowMs: Long,
        todayEpochDay: Long,
        config: FocusConfig
    ) {
        val graceLimitMs = config.sessionDurationSeconds * 1000L
        val active = currentState as? EngineState.InActiveSession
        if (active != null && active.isPausedInBackground) {
            pausedSessionsByAppId[active.app.id] = active
        }

        val iterator = pausedSessionsByAppId.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next().value
            val awayMs = if (entry.isPausedInBackground) (nowMs - entry.pausedAtRealtimeMs).coerceAtLeast(0L) else 0L
            if (entry.isPausedInBackground && awayMs > graceLimitMs) {
                iterator.remove()
                if ((currentState as? EngineState.InActiveSession)?.app?.id == entry.app.id) {
                    currentState = EngineState.Idle
                }
                if (entry.remainingSessionSeconds > 0L) {
                    creditExpiredBackgroundSession(entry, todayEpochDay)
                }
            }
        }
    }

    private fun creditExpiredBackgroundSession(state: EngineState.InActiveSession, todayEpochDay: Long) {
        val unusedSeconds = state.remainingSessionSeconds.coerceAtLeast(0L)
        if (unusedSeconds <= 0L) return
        val reward = store.getRewardState(todayEpochDay)
        val isSameDay = state.sessionEpochDay == todayEpochDay
        store.saveRewardState(
            reward.copy(
                voluntaryExitsToday = if (isSameDay) reward.voluntaryExitsToday + 1 else reward.voluntaryExitsToday,
                totalImpulsesBeaten = reward.totalImpulsesBeaten + 1,
                reclaimedSecondsToday = if (isSameDay) reward.reclaimedSecondsToday + unusedSeconds else reward.reclaimedSecondsToday,
                totalReclaimedSeconds = reward.totalReclaimedSeconds + unusedSeconds
            )
        )
    }

    /**
     * Chamado quando o usuário confirma o início da sessão após concluir a Pausa Consciente.
     *
     * @param isQuickMicroSession Se verdadeiro, inicia uma Micro-Sessão rápida de 1 minuto (60s)
     *                            para responder mensagens sem cair no feed.
     */
    fun onConfirmStartSession(isQuickMicroSession: Boolean = false): InterventionAction {
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
            pausedSessionsByAppId.remove(state.app.id)
            markTodayQuotaExceededInReward(today)
            currentState = EngineState.InHardBlock(state.app, BlockReason.DAILY_QUOTA_EXCEEDED)
            return InterventionAction.ShowHardBlock(
                appId = state.app.id,
                displayName = state.app.displayName,
                reason = BlockReason.DAILY_QUOTA_EXCEEDED,
                remainingBlockSeconds = 0L
            )
        }

        store.saveUsageState(usage.copy(sessionsStartedToday = usage.sessionsStartedToday + 1))

        val requestedSeconds = if (isQuickMicroSession) {
            minOf(QUICK_MICRO_SESSION_SECONDS, config.sessionDurationSeconds)
        } else {
            config.sessionDurationSeconds
        }
        val sessionSeconds = minOf(requestedSeconds, remainingDailySeconds)

        pausedSessionsByAppId.remove(state.app.id)
        currentState = EngineState.InActiveSession(
            app = state.app,
            sessionEpochDay = today,
            initialSessionSeconds = sessionSeconds,
            remainingSessionSeconds = sessionSeconds,
            isQuickMicroSession = isQuickMicroSession,
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
     * Chamado quando o usuário clica no botão verde de desistência consciente durante a pausa ou bloqueio.
     * Contabiliza Reforço Positivo (+1 impulso vencido e +N minutos resgatados) quando acionado na Pausa Consciente.
     */
    fun onUserAborted(): InterventionAction {
        val state = currentState
        if (state is EngineState.InMindfulPause) {
            val config = store.getConfig()
            val nowMs = timeProvider.elapsedRealtimeMs()
            val today = timeProvider.currentEpochDay()
            val usage = getSanitizedUsageState(state.app.id, today, nowMs, config)
            val remainingDaily = (config.dailyQuotaSeconds - usage.usedDailySeconds).coerceAtLeast(0L)
            val reward = store.getRewardState(today)
            val savedSec = minOf(config.sessionDurationSeconds, remainingDaily).coerceAtLeast(0L)
            store.saveRewardState(
                reward.copy(
                    abortedToday = reward.abortedToday + 1,
                    totalImpulsesBeaten = reward.totalImpulsesBeaten + 1,
                    reclaimedSecondsToday = reward.reclaimedSecondsToday + savedSec,
                    totalReclaimedSeconds = reward.totalReclaimedSeconds + savedSec
                )
            )
            currentState = EngineState.Idle
        } else if (state is EngineState.InHardBlock) {
            currentState = EngineState.Idle
        }
        return InterventionAction.DismissOverlay
    }

    /**
     * Chamado quando o usuário encerra voluntariamente uma sessão ativa antes do cronômetro zerar
     * (ex: toque longo na pílula flutuante).
     *
     * Concede o Bônus de Saída Voluntária:
     * - Soma os segundos restantes da sessão ao Tempo Resgatado.
     * - Incrementa `voluntaryExitsToday` e `totalImpulsesBeaten`.
     * - Reduz o Cooldown pós-sessão pela metade (ou zera o Cooldown se o uso foi < 60s).
     */
    fun onUserEndedSessionEarly(): InterventionAction {
        val state = currentState
        if (state !is EngineState.InActiveSession) {
            return InterventionAction.NoOp
        }

        val config = store.getConfig()
        val nowMs = timeProvider.elapsedRealtimeMs()
        val today = timeProvider.currentEpochDay()
        val usage = getSanitizedUsageState(state.app.id, today, nowMs, config)

        val unusedSeconds = state.remainingSessionSeconds.coerceAtLeast(0L)
        val elapsedSessionSeconds = (state.initialSessionSeconds - unusedSeconds).coerceAtLeast(0L)

        val reward = store.getRewardState(today)
        store.saveRewardState(
            reward.copy(
                voluntaryExitsToday = reward.voluntaryExitsToday + 1,
                totalImpulsesBeaten = reward.totalImpulsesBeaten + 1,
                reclaimedSecondsToday = reward.reclaimedSecondsToday + unusedSeconds,
                totalReclaimedSeconds = reward.totalReclaimedSeconds + unusedSeconds
            )
        )

        val discountedCooldownMs = if (elapsedSessionSeconds < QUICK_MICRO_SESSION_SECONDS) {
            0L
        } else {
            (config.cooldownSeconds * 1000L) / 2L
        }
        val cooldownUntil = if (discountedCooldownMs > 0L) nowMs + discountedCooldownMs else 0L
        store.saveUsageState(usage.copy(cooldownUntilRealtimeMs = cooldownUntil))

        pausedSessionsByAppId.remove(state.app.id)
        currentState = EngineState.Idle
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
                val ready = nextSec == 0
                val reward = store.getRewardState(today)
                currentState = state.copy(remainingSeconds = nextSec)
                buildMindfulPauseAction(
                    app = state.app,
                    remainingPauseSeconds = nextSec,
                    readyToStartSession = ready,
                    remainingDailyQuotaSeconds = remainingDaily,
                    config = config,
                    reward = reward
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

                // Se a sessão acabou OU a cota diária acabou neste segundo
                if (newRemainingSession <= 0L || newRemainingDaily <= 0L) {
                    val cooldownDurationMs = if (state.isQuickMicroSession && newRemainingDaily > 0L) {
                        (config.cooldownSeconds * 1000L) / 2L
                    } else {
                        config.cooldownSeconds * 1000L
                    }
                    val cooldownUntil = nowMs + cooldownDurationMs
                    store.saveUsageState(
                        usage.copy(
                            usedDailySeconds = newUsedDaily,
                            cooldownUntilRealtimeMs = cooldownUntil
                        )
                    )
                    if (newRemainingDaily <= 0L) {
                        markTodayQuotaExceededInReward(today)
                    }
                    pausedSessionsByAppId.remove(state.app.id)
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
                val remainingDaily = (config.dailyQuotaSeconds - usage.usedDailySeconds).coerceAtLeast(0L)
                if (remainingDaily <= 0L) {
                    markTodayQuotaExceededInReward(today)
                    currentState = EngineState.InHardBlock(state.app, BlockReason.DAILY_QUOTA_EXCEEDED)
                    return InterventionAction.ShowHardBlock(
                        appId = state.app.id,
                        displayName = state.app.displayName,
                        reason = BlockReason.DAILY_QUOTA_EXCEEDED,
                        remainingBlockSeconds = 0L
                    )
                }
                if (usage.cooldownUntilRealtimeMs <= nowMs) {
                    // Cooldown acabou! Transiciona direto para a Pausa Consciente
                    val currentReward = store.getRewardState(today)
                    val updatedReward = currentReward.copy(attemptsToday = currentReward.attemptsToday + 1)
                    store.saveRewardState(updatedReward)

                    val initialPause = computeProgressivePauseSeconds(config, usage.sessionsStartedToday)
                    currentState = EngineState.InMindfulPause(state.app, initialPause)
                    buildMindfulPauseAction(
                        app = state.app,
                        remainingPauseSeconds = initialPause,
                        readyToStartSession = false,
                        remainingDailyQuotaSeconds = remainingDaily,
                        config = config,
                        reward = updatedReward
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
     * Retorna o resumo consolidado de Reforço Positivo e Consistência para exibição na UI.
     */
    fun getRewardSummary(): FocusRewardSummary {
        val nowMs = timeProvider.elapsedRealtimeMs()
        val today = timeProvider.currentEpochDay()
        val config = store.getConfig()
        purgeExpiredBackgroundSessions(nowMs, today, config)

        val reward = store.getRewardState(today)

        val masteryPct = if (reward.attemptsToday > 0) {
            ((reward.abortedToday * 100) / reward.attemptsToday).coerceIn(0, 100)
        } else {
            100
        }

        val reclaimedMinToday = reward.reclaimedSecondsToday / 60L
        val totalReclaimedMin = reward.totalReclaimedSeconds / 60L

        val windowDays = reward.trackedDaysCount.coerceIn(1, 14)
        var daysWithinQuota = 0
        for (bitIndex in 0 until windowDays) {
            if (((reward.consistencyBitmask ushr bitIndex) and 1L) == 1L) {
                daysWithinQuota++
            }
        }
        val consistencyPct = ((daysWithinQuota * 100) / windowDays).coerceIn(0, 100)

        val dotsBuilder = StringBuilder()
        for (bitIndex in 6 downTo 0) {
            if (dotsBuilder.isNotEmpty()) dotsBuilder.append(' ')
            val symbol = when {
                bitIndex >= reward.trackedDaysCount -> '·'
                ((reward.consistencyBitmask ushr bitIndex) and 1L) == 1L -> '●'
                else -> '○'
            }
            dotsBuilder.append(symbol)
        }

        return FocusRewardSummary(
            attemptsToday = reward.attemptsToday,
            abortedToday = reward.abortedToday,
            voluntaryExitsToday = reward.voluntaryExitsToday,
            masteryPercentToday = masteryPct,
            reclaimedMinutesToday = reclaimedMinToday,
            totalReclaimedMinutes = totalReclaimedMin,
            tangibleEquivalentText = formatTangibleEquivalent(totalReclaimedMin),
            focusLevelTitle = formatFocusLevelTitle(reward.totalImpulsesBeaten),
            last7DaysDots = dotsBuilder.toString(),
            consistencySummaryText = "$daysWithinQuota dos últimos $windowDays dias dentro da meta ($consistencyPct%)"
        )
    }

    /**
     * Verifica se a Trava Anti-Sabotagem está ativa (algum app habilitado está com sessão em andamento,
     * em Cooldown ou com a Cota Diária estourada). Impede que o usuário desative o bloqueio por impulso.
     */
    fun getSettingsLockStatus(): SettingsLockStatus {
        val config = store.getConfig()
        val nowMs = timeProvider.elapsedRealtimeMs()
        val today = timeProvider.currentEpochDay()

        purgeExpiredBackgroundSessions(nowMs, today, config)

        val activeOrPaused = (currentState as? EngineState.InActiveSession)
            ?.takeIf { it.app.id in config.enabledAppIds && it.remainingSessionSeconds > 0L }
            ?: pausedSessionsByAppId.values.firstOrNull {
                it.app.id in config.enabledAppIds && it.remainingSessionSeconds > 0L
            }

        if (activeOrPaused != null) {
            return SettingsLockStatus(
                isLocked = true,
                reasonMessage = "Configurações travadas: há uma sessão do ${activeOrPaused.app.displayName} em andamento."
            )
        }

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

    /**
     * Indica se o serviço deve escutar trocas de janela de todos os pacotes para detectar saída ao Launcher.
     * Permanece verdadeiro enquanto houver barreira ou sessão ativa em primeiro plano, evitando que eventos
     * [InterventionAction.NoOp] (ex: abertura de teclado virtual) desliguem o monitoramento da sessão.
     */
    fun shouldListenToAllPackages(): Boolean = when (val state = currentState) {
        EngineState.Idle -> false
        is EngineState.InMindfulPause -> true
        is EngineState.InActiveSession -> !state.isPausedInBackground
        is EngineState.InHardBlock -> true
    }

    private fun computeProgressivePauseSeconds(config: FocusConfig, sessionsStartedToday: Int): Int {
        val base = config.mindfulPauseSeconds
        if (!config.progressivePauseEnabled || sessionsStartedToday <= 0) {
            return base
        }
        val extra = sessionsStartedToday * PROGRESSIVE_PAUSE_STEP_SECONDS
        val cap = maxOf(base, MAX_PROGRESSIVE_PAUSE_SECONDS)
        return (base + extra).coerceAtMost(cap)
    }

    private fun buildMindfulPauseAction(
        app: TargetApp,
        remainingPauseSeconds: Int,
        readyToStartSession: Boolean,
        remainingDailyQuotaSeconds: Long,
        config: FocusConfig,
        reward: FocusRewardState
    ): InterventionAction.ShowMindfulPause {
        val allowMicro = readyToStartSession &&
            remainingDailyQuotaSeconds >= QUICK_MICRO_SESSION_SECONDS &&
            config.sessionDurationMinutes > 1

        val effectiveReclaimedSec = minOf(config.sessionDurationSeconds, remainingDailyQuotaSeconds).coerceAtLeast(0L)
        val effectiveReclaimedMin = ((effectiveReclaimedSec + 59L) / 60L).toInt().coerceAtLeast(1)

        return InterventionAction.ShowMindfulPause(
            appId = app.id,
            displayName = app.displayName,
            remainingPauseSeconds = remainingPauseSeconds,
            readyToStartSession = readyToStartSession,
            remainingDailyQuotaSeconds = remainingDailyQuotaSeconds,
            breathingCue = computeBreathingCue(remainingPauseSeconds, readyToStartSession),
            anchorActionLabel = "Fechar e ir: ${config.selectedAnchorHabit}",
            rewardBannerText = formatOverlayRewardBanner(reward),
            reclaimedMinutesOnAbort = effectiveReclaimedMin,
            allowQuickMicroSession = allowMicro
        )
    }

    private fun computeBreathingCue(remainingSeconds: Int, readyToStart: Boolean): String {
        if (readyToStart || remainingSeconds <= 0) {
            return "Pausa concluída. Você realmente precisa entrar agora?"
        }
        return when (remainingSeconds % 10) {
            9, 8, 7, 0 -> "🌬️ Inspire profundamente pelo nariz..."
            6, 5 -> "⏸️ Segure o ar por um instante..."
            else -> "🍃 Expire devagar soltando o impulso..."
        }
    }

    private fun formatOverlayRewardBanner(reward: FocusRewardState): String {
        val savedMinToday = reward.reclaimedSecondsToday / 60L
        if (reward.abortedToday == 0 && reward.voluntaryExitsToday == 0) {
            return "🌱 Foco Consciente: feche agora para resgatar os primeiros minutos do dia"
        }
        val masteryPct = if (reward.attemptsToday > 0) {
            ((reward.abortedToday * 100) / reward.attemptsToday).coerceIn(0, 100)
        } else {
            100
        }
        return "🛡️ ${reward.abortedToday}/${reward.attemptsToday} impulsos vencidos hoje ($masteryPct%) • +${savedMinToday} min resgatados"
    }

    private fun formatTangibleEquivalent(totalReclaimedMinutes: Long): String {
        if (totalReclaimedMinutes <= 0L) {
            return "Cada desistência na porta converte tempo de feed em vida real."
        }
        val approxPages = (totalReclaimedMinutes / 2L).coerceAtLeast(1L)
        val walks = totalReclaimedMinutes / 15L
        return if (walks >= 1L) {
            "Equivale a ~$approxPages páginas lidas 📖 ou $walks caminhada(s) de 15 min 🚶"
        } else {
            "Equivale a ~$approxPages páginas de um livro lidas 📖"
        }
    }

    private fun formatFocusLevelTitle(totalImpulsesBeaten: Int): String = when {
        totalImpulsesBeaten >= 150 -> "Mestre do Próprio Tempo 🏆"
        totalImpulsesBeaten >= 50 -> "Mente Blindada 💎"
        totalImpulsesBeaten >= 10 -> "Guardião da Atenção 🛡️"
        else -> "Iniciante Consciente 🌱"
    }

    private fun markTodayQuotaExceededInReward(todayEpochDay: Long) {
        val reward = store.getRewardState(todayEpochDay)
        if ((reward.consistencyBitmask and 1L) != 0L) {
            store.saveRewardState(reward.copy(consistencyBitmask = reward.consistencyBitmask and -2L))
        }
    }

    private fun handleExitToNonMonitoredApp(): InterventionAction {
        when (val state = currentState) {
            is EngineState.InActiveSession -> {
                val paused = if (!state.isPausedInBackground) {
                    state.copy(
                        isPausedInBackground = true,
                        pausedAtRealtimeMs = timeProvider.elapsedRealtimeMs()
                    )
                } else {
                    state
                }
                pausedSessionsByAppId[state.app.id] = paused
                currentState = paused
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

    companion object {
        private const val PROGRESSIVE_PAUSE_STEP_SECONDS = 5
        private const val MAX_PROGRESSIVE_PAUSE_SECONDS = 60
        private const val QUICK_MICRO_SESSION_SECONDS = 60L
    }
}

data class SettingsLockStatus(
    val isLocked: Boolean,
    val reasonMessage: String?
)
