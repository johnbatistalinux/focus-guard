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
            val pausedAtRealtimeMs: Long = 0L,
            val baseUsedDailySeconds: Long = 0L,
            val baseAppUsedDailySeconds: Long = 0L,
            val baseGroupCooldownUntilMs: Long = 0L,
            val baseAppCooldownUntilMs: Long = 0L
        ) : EngineState

        data class InHardBlock(
            val app: TargetApp,
            val reason: BlockReason
        ) : EngineState
    }

    private var currentState: EngineState = EngineState.Idle
    private val pausedSessionsByAppId = HashMap<String, EngineState.InActiveSession>(4)

    init {
        recoverInterruptedSessions()
    }

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
            lower.contains("voiceinput") ||
            lower.contains("neatplugin") ||
            lower.contains("mojitok") ||
            lower.contains("emoji") ||
            lower.contains("vision") ||
            lower == "com.samsung.smt" ||
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

        if (isTransientWindowClassName(className)) {
            return InterventionAction.NoOp
        }

        val config = store.getConfig()
        val targetApp = catalog.findByPackageName(packageName, config.customApps)
        val activeGroup = if (targetApp != null) config.findActiveGroupForApp(targetApp.id) else null

        // Só descarta o pacote como transitório se ele NÃO for um app monitorado:
        // apps customizados podem ter nomes que casam com heurísticas (ex: "vision").
        if (!isFocusGuardMainActivity && targetApp == null && isTransientSystemPackage(packageName)) {
            return InterventionAction.NoOp
        }

        // Se o usuário foi para o Launcher ou outro app não monitorado (ex: WhatsApp ou MainActivity)
        if (targetApp == null || activeGroup == null) {
            return handleExitToNonMonitoredApp()
        }

        val nowMs = timeProvider.elapsedRealtimeMs()
        val today = timeProvider.currentEpochDay()

        // Se havia outra rede social com sessão ativa em primeiro plano, pausa-a no mapa por app
        val activeState = currentState as? EngineState.InActiveSession
        if (activeState != null && activeState.app.id != targetApp.id) {
            val otherGroup = resolveGroupForApp(config, activeState.app.id)
            val elapsedInSession = (activeState.initialSessionSeconds - activeState.remainingSessionSeconds).coerceAtLeast(0L)
            val flushedUsedDaily = activeState.baseUsedDailySeconds + elapsedInSession
            val flushedAppUsedDaily = activeState.baseAppUsedDailySeconds + elapsedInSession
            val (usage, appUsage) = getEffectiveUsage(activeState.app.id, otherGroup, today, nowMs)
            store.saveUsageState(usage.copy(usedDailySeconds = flushedUsedDaily))
            if (appUsage != null) {
                store.saveUsageState(appUsage.copy(usedDailySeconds = flushedAppUsedDaily))
            }
            val pausedOther = if (!activeState.isPausedInBackground) {
                activeState.copy(
                    initialSessionSeconds = activeState.remainingSessionSeconds,
                    isPausedInBackground = true,
                    pausedAtRealtimeMs = nowMs,
                    baseUsedDailySeconds = flushedUsedDaily,
                    baseAppUsedDailySeconds = flushedAppUsedDaily
                )
            } else {
                activeState
            }
            pausedSessionsByAppId[activeState.app.id] = pausedOther
            currentState = EngineState.Idle
        }

        purgeExpiredBackgroundSessions(nowMs, today, config)
        persistSessionGuards()

        val (usage, appUsage) = getEffectiveUsage(targetApp.id, activeGroup, today, nowMs)
        val gName = groupDisplayName(activeGroup)

        // 1. Verifica se a Cota Diária já esgotou
        val remainingDailySeconds = (activeGroup.dailyQuotaSeconds - usage.usedDailySeconds).coerceAtLeast(0L)
        if (remainingDailySeconds <= 0L) {
            pausedSessionsByAppId.remove(targetApp.id)
            persistSessionGuards()
            markTodayQuotaExceededInReward(today)
            currentState = EngineState.InHardBlock(targetApp, BlockReason.DAILY_QUOTA_EXCEEDED)
            return InterventionAction.ShowHardBlock(
                appId = targetApp.id,
                displayName = targetApp.displayName,
                reason = BlockReason.DAILY_QUOTA_EXCEEDED,
                remainingBlockSeconds = 0L,
                groupName = gName
            )
        }

        // 2. Verifica se está em período de Cooldown pós-sessão
        if (usage.cooldownUntilRealtimeMs > nowMs) {
            pausedSessionsByAppId.remove(targetApp.id)
            persistSessionGuards()
            val remainingCooldownSec = ((usage.cooldownUntilRealtimeMs - nowMs + 999L) / 1000L)
            currentState = EngineState.InHardBlock(targetApp, BlockReason.COOLDOWN_ACTIVE)
            return InterventionAction.ShowHardBlock(
                appId = targetApp.id,
                displayName = targetApp.displayName,
                reason = BlockReason.COOLDOWN_ACTIVE,
                remainingBlockSeconds = remainingCooldownSec,
                groupName = gName
            )
        }

        // 3. Verifica se já existe sessão ativa/pausada dentro da janela de tolerância para este app
        val existingSession = (currentState as? EngineState.InActiveSession)
            ?.takeIf { it.app.id == targetApp.id }
            ?: pausedSessionsByAppId[targetApp.id]

        if (existingSession != null && existingSession.remainingSessionSeconds > 0L) {
            if (!existingSession.isPausedInBackground && currentState === existingSession) {
                val elapsedInSession =
                    (existingSession.initialSessionSeconds - existingSession.remainingSessionSeconds).coerceAtLeast(0L)
                val liveUsedDaily = existingSession.baseUsedDailySeconds + elapsedInSession
                val liveRemainingDaily = (activeGroup.dailyQuotaSeconds - liveUsedDaily).coerceAtLeast(0L)
                val effectiveRemaining = minOf(existingSession.remainingSessionSeconds, liveRemainingDaily)
                return InterventionAction.ShowFloatingTimer(
                    appId = targetApp.id,
                    displayName = targetApp.displayName,
                    remainingSessionSeconds = effectiveRemaining,
                    remainingDailyQuotaSeconds = liveRemainingDaily,
                    groupName = gName,
                    totalSessionSeconds = existingSession.initialSessionSeconds
                )
            }

            val effectiveRemaining = minOf(existingSession.remainingSessionSeconds, remainingDailySeconds)
            val resumed = existingSession.copy(
                initialSessionSeconds = effectiveRemaining,
                remainingSessionSeconds = effectiveRemaining,
                isPausedInBackground = false,
                pausedAtRealtimeMs = 0L,
                baseUsedDailySeconds = usage.usedDailySeconds,
                baseAppUsedDailySeconds = appUsage?.usedDailySeconds ?: usage.usedDailySeconds,
                baseGroupCooldownUntilMs = usage.cooldownUntilRealtimeMs,
                baseAppCooldownUntilMs = appUsage?.cooldownUntilRealtimeMs ?: 0L
            )
            pausedSessionsByAppId.remove(targetApp.id)
            currentState = resumed
            persistSessionGuards()
            return InterventionAction.ShowFloatingTimer(
                appId = targetApp.id,
                displayName = targetApp.displayName,
                remainingSessionSeconds = effectiveRemaining,
                remainingDailyQuotaSeconds = remainingDailySeconds,
                groupName = gName,
                totalSessionSeconds = resumed.initialSessionSeconds
            )
        }

        val currentPause = currentState as? EngineState.InMindfulPause
        if (currentPause != null && currentPause.app.id == targetApp.id) {
            val reward = store.getRewardState(today)
            val ready = currentPause.remainingSeconds <= 0
            return buildMindfulPauseAction(
                app = targetApp,
                group = activeGroup,
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

        val initialPause = computeProgressivePauseSeconds(activeGroup, usage.sessionsStartedToday)
        currentState = EngineState.InMindfulPause(targetApp, initialPause)
        return buildMindfulPauseAction(
            app = targetApp,
            group = activeGroup,
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

    /**
     * Chamado quando o app monitorado deixou de ocupar o primeiro plano (Launcher, dashboard ou
     * troca de app detectada pela amostragem de janelas do serviço).
     *
     * Existe para que o serviço expresse "o app da sessão saiu da frente" sem fabricar um nome de
     * pacote que não existe; o estado resultante é idêntico a uma saída real.
     */
    fun onForegroundLost(): InterventionAction = handleExitToNonMonitoredApp()

    /**
     * Finaliza sessões em andamento quando o AccessibilityService é encerrado de forma graciosa.
     * Contabiliza o tempo ainda não descarregado e aplica o cooldown devido, evitando que
     * desligar o serviço nas configurações funcione como atalho para escapar da barreira.
     */
    fun onServiceStopped() {
        val config = store.getConfig()
        val today = timeProvider.currentEpochDay()
        val nowMs = timeProvider.elapsedRealtimeMs()

        val active = currentState as? EngineState.InActiveSession
        if (active != null) {
            finalizeInterruptedSession(active, config, today, nowMs)
        }
        for (session in pausedSessionsByAppId.values) {
            if (active != null && session.app.id == active.app.id) continue
            finalizeInterruptedSession(session, config, today, nowMs)
        }

        pausedSessionsByAppId.clear()
        currentState = EngineState.Idle
        store.saveSessionGuards(emptyList())
    }

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

        var removedExpiredSession = false
        val iterator = pausedSessionsByAppId.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next().value
            val awayMs = if (entry.isPausedInBackground) (nowMs - entry.pausedAtRealtimeMs).coerceAtLeast(0L) else 0L
            if (entry.isPausedInBackground && awayMs > graceLimitMs) {
                iterator.remove()
                removedExpiredSession = true
                if ((currentState as? EngineState.InActiveSession)?.app?.id == entry.app.id) {
                    currentState = EngineState.Idle
                }
                if (entry.remainingSessionSeconds > 0L) {
                    creditExpiredBackgroundSession(entry, todayEpochDay)
                }
            }
        }
        if (removedExpiredSession) {
            persistSessionGuards()
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
        val group = resolveGroupForApp(config, state.app.id)
        val nowMs = timeProvider.elapsedRealtimeMs()
        val today = timeProvider.currentEpochDay()

        val (usage, appUsage) = getEffectiveUsage(state.app.id, group, today, nowMs)
        val remainingDailySeconds = (group.dailyQuotaSeconds - usage.usedDailySeconds).coerceAtLeast(0L)
        val gName = groupDisplayName(group)

        if (remainingDailySeconds <= 0L) {
            pausedSessionsByAppId.remove(state.app.id)
            persistSessionGuards()
            markTodayQuotaExceededInReward(today)
            currentState = EngineState.InHardBlock(state.app, BlockReason.DAILY_QUOTA_EXCEEDED)
            return InterventionAction.ShowHardBlock(
                appId = state.app.id,
                displayName = state.app.displayName,
                reason = BlockReason.DAILY_QUOTA_EXCEEDED,
                remainingBlockSeconds = 0L,
                groupName = gName
            )
        }

        store.saveUsageState(usage.copy(sessionsStartedToday = usage.sessionsStartedToday + 1))
        if (appUsage != null) {
            store.saveUsageState(appUsage.copy(sessionsStartedToday = appUsage.sessionsStartedToday + 1))
        }

        val requestedSeconds = if (isQuickMicroSession) {
            minOf(QUICK_MICRO_SESSION_SECONDS, group.sessionDurationSeconds)
        } else {
            group.sessionDurationSeconds
        }
        val sessionSeconds = minOf(requestedSeconds, remainingDailySeconds)

        pausedSessionsByAppId.remove(state.app.id)
        currentState = EngineState.InActiveSession(
            app = state.app,
            sessionEpochDay = today,
            initialSessionSeconds = sessionSeconds,
            remainingSessionSeconds = sessionSeconds,
            isQuickMicroSession = isQuickMicroSession,
            isPausedInBackground = false,
            baseUsedDailySeconds = usage.usedDailySeconds,
            baseAppUsedDailySeconds = appUsage?.usedDailySeconds ?: usage.usedDailySeconds,
            baseGroupCooldownUntilMs = usage.cooldownUntilRealtimeMs,
            baseAppCooldownUntilMs = appUsage?.cooldownUntilRealtimeMs ?: 0L
        )
        persistSessionGuards()

        return InterventionAction.ShowFloatingTimer(
            appId = state.app.id,
            displayName = state.app.displayName,
            remainingSessionSeconds = sessionSeconds,
            remainingDailyQuotaSeconds = remainingDailySeconds,
            groupName = gName,
            totalSessionSeconds = sessionSeconds
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
            val group = resolveGroupForApp(config, state.app.id)
            val nowMs = timeProvider.elapsedRealtimeMs()
            val today = timeProvider.currentEpochDay()
            val (usage, _) = getEffectiveUsage(state.app.id, group, today, nowMs)
            val remainingDaily = (group.dailyQuotaSeconds - usage.usedDailySeconds).coerceAtLeast(0L)
            val reward = store.getRewardState(today)
            val savedSec = minOf(group.sessionDurationSeconds, remainingDaily).coerceAtLeast(0L)
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
        persistSessionGuards()
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
        val group = resolveGroupForApp(config, state.app.id)
        val nowMs = timeProvider.elapsedRealtimeMs()
        val today = timeProvider.currentEpochDay()

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
            (group.cooldownSeconds * 1000L) / 2L
        }
        val cooldownUntil = if (discountedCooldownMs > 0L) nowMs + discountedCooldownMs else 0L

        val flushedUsedDaily = state.baseUsedDailySeconds + elapsedSessionSeconds
        val flushedAppUsedDaily = state.baseAppUsedDailySeconds + elapsedSessionSeconds

        val (usage, appUsage) = getEffectiveUsage(state.app.id, group, today, nowMs)
        store.saveUsageState(usage.copy(usedDailySeconds = flushedUsedDaily, cooldownUntilRealtimeMs = cooldownUntil))
        if (appUsage != null) {
            store.saveUsageState(appUsage.copy(usedDailySeconds = flushedAppUsedDaily, cooldownUntilRealtimeMs = cooldownUntil))
        }

        pausedSessionsByAppId.remove(state.app.id)
        currentState = EngineState.Idle
        persistSessionGuards()
        return InterventionAction.DismissOverlay
    }

    /**
     * Ends an active or background-paused session on user request from the dashboard.
     */
    fun onUserEndedSessionFromDashboard(): InterventionAction {
        if (currentState is EngineState.InActiveSession) {
            return onUserEndedSessionEarly()
        }
        val paused = pausedSessionsByAppId.values.firstOrNull() ?: return InterventionAction.NoOp
        pausedSessionsByAppId.remove(paused.app.id)
        currentState = paused
        return onUserEndedSessionEarly()
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
                val group = resolveGroupForApp(config, state.app.id)
                val (usage, _) = getEffectiveUsage(state.app.id, group, today, nowMs)
                val remainingDaily = (group.dailyQuotaSeconds - usage.usedDailySeconds).coerceAtLeast(0L)
                val nextSec = (state.remainingSeconds - 1).coerceAtLeast(0)
                val ready = nextSec == 0
                val reward = store.getRewardState(today)
                currentState = state.copy(remainingSeconds = nextSec)
                buildMindfulPauseAction(
                    app = state.app,
                    group = group,
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

                val group = resolveGroupForApp(config, state.app.id)
                val newRemainingSession = state.remainingSessionSeconds - 1L
                val elapsedInSession = (state.initialSessionSeconds - newRemainingSession).coerceAtLeast(0L)
                val newUsedDaily = state.baseUsedDailySeconds + elapsedInSession
                val newAppUsedDaily = state.baseAppUsedDailySeconds + elapsedInSession
                val newRemainingDaily = (group.dailyQuotaSeconds - newUsedDaily).coerceAtLeast(0L)
                val gName = groupDisplayName(group)

                // Se a sessão acabou OU a cota diária acabou neste segundo
                if (newRemainingSession <= 0L || newRemainingDaily <= 0L) {
                    val cooldownDurationMs = if (state.isQuickMicroSession && newRemainingDaily > 0L) {
                        (group.cooldownSeconds * 1000L) / 2L
                    } else {
                        group.cooldownSeconds * 1000L
                    }
                    val cooldownUntil = nowMs + cooldownDurationMs
                    val (usage, appUsage) = getEffectiveUsage(state.app.id, group, today, nowMs)
                    store.saveUsageState(
                        usage.copy(
                            usedDailySeconds = newUsedDaily,
                            cooldownUntilRealtimeMs = cooldownUntil
                        )
                    )
                    if (appUsage != null) {
                        store.saveUsageState(
                            appUsage.copy(
                                usedDailySeconds = newAppUsedDaily,
                                cooldownUntilRealtimeMs = cooldownUntil
                            )
                        )
                    }
                    if (newRemainingDaily <= 0L) {
                        markTodayQuotaExceededInReward(today)
                    }
                    pausedSessionsByAppId.remove(state.app.id)
                    currentState = EngineState.Idle
                    persistSessionGuards()
                    val reason = if (newRemainingDaily <= 0L) {
                        BlockReason.DAILY_QUOTA_EXCEEDED
                    } else {
                        BlockReason.COOLDOWN_ACTIVE
                    }
                    InterventionAction.KickToHome(appId = state.app.id, reason = reason)
                } else {
                    if (elapsedInSession % USAGE_FLUSH_INTERVAL_SECONDS == 0L) {
                        val (usage, appUsage) = getEffectiveUsage(state.app.id, group, today, nowMs)
                        store.saveUsageState(usage.copy(usedDailySeconds = newUsedDaily))
                        if (appUsage != null) {
                            store.saveUsageState(appUsage.copy(usedDailySeconds = newAppUsedDaily))
                        }
                    }
                    currentState = state.copy(remainingSessionSeconds = newRemainingSession)
                    InterventionAction.ShowFloatingTimer(
                        appId = state.app.id,
                        displayName = state.app.displayName,
                        remainingSessionSeconds = newRemainingSession,
                        remainingDailyQuotaSeconds = newRemainingDaily,
                        groupName = gName,
                        totalSessionSeconds = state.initialSessionSeconds
                    )
                }
            }

            is EngineState.InHardBlock -> {
                val group = resolveGroupForApp(config, state.app.id)
                val gName = groupDisplayName(group)
                if (state.reason == BlockReason.DAILY_QUOTA_EXCEEDED) {
                    return InterventionAction.ShowHardBlock(
                        appId = state.app.id,
                        displayName = state.app.displayName,
                        reason = BlockReason.DAILY_QUOTA_EXCEEDED,
                        remainingBlockSeconds = 0L,
                        groupName = gName
                    )
                }
                val (usage, _) = getEffectiveUsage(state.app.id, group, today, nowMs)
                val remainingDaily = (group.dailyQuotaSeconds - usage.usedDailySeconds).coerceAtLeast(0L)
                if (remainingDaily <= 0L) {
                    markTodayQuotaExceededInReward(today)
                    currentState = EngineState.InHardBlock(state.app, BlockReason.DAILY_QUOTA_EXCEEDED)
                    return InterventionAction.ShowHardBlock(
                        appId = state.app.id,
                        displayName = state.app.displayName,
                        reason = BlockReason.DAILY_QUOTA_EXCEEDED,
                        remainingBlockSeconds = 0L,
                        groupName = gName
                    )
                }
                if (usage.cooldownUntilRealtimeMs <= nowMs) {
                    // Cooldown acabou! Transiciona direto para a Pausa Consciente
                    val currentReward = store.getRewardState(today)
                    val updatedReward = currentReward.copy(attemptsToday = currentReward.attemptsToday + 1)
                    store.saveRewardState(updatedReward)

                    val initialPause = computeProgressivePauseSeconds(group, usage.sessionsStartedToday)
                    currentState = EngineState.InMindfulPause(state.app, initialPause)
                    buildMindfulPauseAction(
                        app = state.app,
                        group = group,
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
                        remainingBlockSeconds = remainingCooldownSec,
                        groupName = gName
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
        val last7DaysWithinQuota = ArrayList<Boolean>(7)
        val last7DaysTracked = ArrayList<Boolean>(7)
        for (bitIndex in 6 downTo 0) {
            if (dotsBuilder.isNotEmpty()) dotsBuilder.append(' ')
            val isTracked = bitIndex < reward.trackedDaysCount
            val isWithinQuota = isTracked && ((reward.consistencyBitmask ushr bitIndex) and 1L) == 1L
            last7DaysTracked.add(isTracked)
            last7DaysWithinQuota.add(isWithinQuota)
            val symbol = when {
                !isTracked -> '·'
                isWithinQuota -> '●'
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
            consistencySummaryText = "$daysWithinQuota dos últimos $windowDays dias dentro da meta ($consistencyPct%)",
            last7DaysWithinQuota = last7DaysWithinQuota,
            last7DaysTracked = last7DaysTracked
        )
    }

    /**
     * Snapshot da sessão ativa ou pausada em segundo plano, usado pela tela Hoje.
     */
    fun getActiveSessionSnapshot(): ActiveSessionSnapshot? {
        val state = (currentState as? EngineState.InActiveSession)
            ?.takeIf { it.remainingSessionSeconds > 0L }
            ?: pausedSessionsByAppId.values.firstOrNull { it.remainingSessionSeconds > 0L }
            ?: return null

        val config = store.getConfig()
        val group = resolveGroupForApp(config, state.app.id)
        return ActiveSessionSnapshot(
            appId = state.app.id,
            displayName = state.app.displayName,
            groupName = groupDisplayName(group),
            remainingSeconds = state.remainingSessionSeconds,
            totalSeconds = state.initialSessionSeconds
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

        val activeGroups = config.getEffectiveGroups().filter { it.enabled }
        val allActiveAppIds = activeGroups.flatMap { it.appIds }.toSet()

        val activeOrPaused = (currentState as? EngineState.InActiveSession)
            ?.takeIf { it.app.id in allActiveAppIds && it.remainingSessionSeconds > 0L }
            ?: pausedSessionsByAppId.values.firstOrNull {
                it.app.id in allActiveAppIds && it.remainingSessionSeconds > 0L
            }

        if (activeOrPaused != null) {
            return SettingsLockStatus(
                isLocked = true,
                reasonMessage = "Configurações travadas: há uma sessão do ${activeOrPaused.app.displayName} em andamento."
            )
        }

        for (group in activeGroups) {
            val isDefaultSocial = group.id == FocusGroup.DEFAULT_GROUP_SOCIAL_ID
            if (group.sharedQuota) {
                val rawGroupUsage = getSanitizedUsageState(groupUsageKey(group.id), today, nowMs, group.cooldownSeconds)
                var maxUsed = rawGroupUsage.usedDailySeconds
                var maxCooldown = rawGroupUsage.cooldownUntilRealtimeMs
                var maxApp: TargetApp? = null
                for (appId in group.appIds) {
                    val appUsage = getSanitizedUsageState(appId, today, nowMs, group.cooldownSeconds)
                    if (appUsage.usedDailySeconds > maxUsed) {
                        maxUsed = appUsage.usedDailySeconds
                        maxApp = catalog.findById(appId, config.customApps)
                    }
                    if (appUsage.cooldownUntilRealtimeMs > maxCooldown) {
                        maxCooldown = appUsage.cooldownUntilRealtimeMs
                        maxApp = catalog.findById(appId, config.customApps)
                    }
                }
                if (maxUsed >= group.dailyQuotaSeconds) {
                    val targetName = if (isDefaultSocial && maxApp != null) maxApp.displayName else "do grupo ${group.name}"
                    return SettingsLockStatus(
                        isLocked = true,
                        reasonMessage = "Configurações travadas: a cota diária $targetName já foi atingida hoje."
                    )
                }
                if (maxCooldown > nowMs) {
                    val remainingMin = ((maxCooldown - nowMs + 59_999L) / 60_000L)
                    val targetName = if (isDefaultSocial && maxApp != null) maxApp.displayName else "o grupo ${group.name}"
                    return SettingsLockStatus(
                        isLocked = true,
                        reasonMessage = "Configurações travadas: $targetName está em cooldown por mais ${remainingMin} min."
                    )
                }
            } else {
                for (appId in group.appIds) {
                    val app = catalog.findById(appId, config.customApps) ?: continue
                    val usage = getSanitizedUsageState(appId, today, nowMs, group.cooldownSeconds)
                    if (usage.usedDailySeconds >= group.dailyQuotaSeconds) {
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
            }
        }
        return SettingsLockStatus(isLocked = false, reasonMessage = null)
    }

    /**
     * Indica se existe uma sessão ativa contando em primeiro plano.
     *
     * Usado pelo serviço para confirmar trocas de janela antes de pausar a sessão e remover a
     * pílula flutuante, evitando falsos positivos de teclados e painéis auxiliares da OEM.
     */
    fun hasForegroundSession(): Boolean {
        val state = currentState as? EngineState.InActiveSession ?: return false
        return !state.isPausedInBackground
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

    private fun groupUsageKey(groupId: String): String = "group_$groupId"

    private fun groupDisplayName(group: FocusGroup): String =
        if (group.id == FocusGroup.DEFAULT_GROUP_SOCIAL_ID) "" else group.name

    private fun resolveGroupForApp(config: FocusConfig, appId: String): FocusGroup {
        return config.findActiveGroupForApp(appId)
            ?: config.findGroupForApp(appId)
            ?: config.getEffectiveGroups().first()
    }

    private fun getEffectiveUsage(
        appId: String,
        group: FocusGroup,
        today: Long,
        nowMs: Long
    ): Pair<AppUsageState, AppUsageState?> {
        val appUsage = getSanitizedUsageState(appId, today, nowMs, group.cooldownSeconds)
        if (!group.sharedQuota) {
            return Pair(appUsage, null)
        }
        val rawGroupUsage = getSanitizedUsageState(groupUsageKey(group.id), today, nowMs, group.cooldownSeconds)
        val mergedGroupUsage = rawGroupUsage.copy(
            usedDailySeconds = maxOf(rawGroupUsage.usedDailySeconds, appUsage.usedDailySeconds),
            cooldownUntilRealtimeMs = maxOf(rawGroupUsage.cooldownUntilRealtimeMs, appUsage.cooldownUntilRealtimeMs),
            sessionsStartedToday = maxOf(rawGroupUsage.sessionsStartedToday, appUsage.sessionsStartedToday)
        )
        return Pair(mergedGroupUsage, appUsage)
    }

    private fun computeProgressivePauseSeconds(group: FocusGroup, sessionsStartedToday: Int): Int {
        val base = group.mindfulPauseSeconds
        if (!group.progressivePauseEnabled || sessionsStartedToday <= 0) {
            return base
        }
        val extra = sessionsStartedToday * PROGRESSIVE_PAUSE_STEP_SECONDS
        val cap = maxOf(base, MAX_PROGRESSIVE_PAUSE_SECONDS)
        return (base + extra).coerceAtMost(cap)
    }

    private fun buildMindfulPauseAction(
        app: TargetApp,
        group: FocusGroup,
        remainingPauseSeconds: Int,
        readyToStartSession: Boolean,
        remainingDailyQuotaSeconds: Long,
        config: FocusConfig,
        reward: FocusRewardState
    ): InterventionAction.ShowMindfulPause {
        val allowMicro = readyToStartSession &&
            remainingDailyQuotaSeconds >= QUICK_MICRO_SESSION_SECONDS &&
            group.sessionDurationMinutes > 1

        val effectiveReclaimedSec = minOf(group.sessionDurationSeconds, remainingDailyQuotaSeconds).coerceAtLeast(0L)
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
            allowQuickMicroSession = allowMicro,
            groupName = groupDisplayName(group),
            sessionDurationMinutes = group.sessionDurationMinutes
        )
    }

    private fun computeBreathingCue(remainingSeconds: Int, readyToStart: Boolean): String {
        if (readyToStart || remainingSeconds <= 0) {
            return "Pausa concluída. Você realmente precisa entrar agora?"
        }
        return when (remainingSeconds % 10) {
            9, 8, 7, 0 -> "Inspire profundamente pelo nariz."
            6, 5 -> "Segure o ar por um instante."
            else -> "Expire devagar, soltando o impulso."
        }
    }

    private fun formatOverlayRewardBanner(reward: FocusRewardState): String {
        val savedMinToday = reward.reclaimedSecondsToday / 60L
        if (reward.abortedToday == 0 && reward.voluntaryExitsToday == 0) {
            return "Feche agora para resgatar os primeiros minutos do dia."
        }
        val masteryPct = if (reward.attemptsToday > 0) {
            ((reward.abortedToday * 100) / reward.attemptsToday).coerceIn(0, 100)
        } else {
            100
        }
        return "${reward.abortedToday}/${reward.attemptsToday} impulsos vencidos hoje ($masteryPct%) · +${savedMinToday} min resgatados"
    }

    private fun formatTangibleEquivalent(totalReclaimedMinutes: Long): String {
        if (totalReclaimedMinutes <= 0L) {
            return "Cada desistência na porta converte tempo de feed em vida real."
        }
        val approxPages = (totalReclaimedMinutes / 2L).coerceAtLeast(1L)
        val walks = totalReclaimedMinutes / 15L
        return if (walks >= 1L) {
            "Equivale a ~$approxPages páginas lidas ou $walks caminhada(s) de 15 min"
        } else {
            "Equivale a ~$approxPages páginas de um livro lidas"
        }
    }

    private fun formatFocusLevelTitle(totalImpulsesBeaten: Int): String = when {
        totalImpulsesBeaten >= 150 -> "Mestre do próprio tempo"
        totalImpulsesBeaten >= 50 -> "Mente blindada"
        totalImpulsesBeaten >= 10 -> "Guardião da atenção"
        else -> "Iniciante consciente"
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
                val config = store.getConfig()
                val group = resolveGroupForApp(config, state.app.id)
                val today = timeProvider.currentEpochDay()
                val nowMs = timeProvider.elapsedRealtimeMs()
                val elapsedInSession = (state.initialSessionSeconds - state.remainingSessionSeconds).coerceAtLeast(0L)
                val flushedUsedDaily = state.baseUsedDailySeconds + elapsedInSession
                val flushedAppUsedDaily = state.baseAppUsedDailySeconds + elapsedInSession

                val (usage, appUsage) = getEffectiveUsage(state.app.id, group, today, nowMs)
                store.saveUsageState(usage.copy(usedDailySeconds = flushedUsedDaily))
                if (appUsage != null) {
                    store.saveUsageState(appUsage.copy(usedDailySeconds = flushedAppUsedDaily))
                }

                val paused = if (!state.isPausedInBackground) {
                    state.copy(
                        initialSessionSeconds = state.remainingSessionSeconds,
                        isPausedInBackground = true,
                        pausedAtRealtimeMs = nowMs,
                        baseUsedDailySeconds = flushedUsedDaily,
                        baseAppUsedDailySeconds = flushedAppUsedDaily
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
        persistSessionGuards()
        return InterventionAction.DismissOverlay
    }

    private fun persistSessionGuards() {
        val config = store.getConfig()
        val guards = LinkedHashMap<String, SessionGuard>()

        val active = currentState as? EngineState.InActiveSession
        if (active != null && active.remainingSessionSeconds > 0L) {
            val group = resolveGroupForApp(config, active.app.id)
            guards[active.app.id] = SessionGuard(
                appId = active.app.id,
                groupId = group.id,
                cooldownSeconds = interruptedCooldownSeconds(active, group)
            )
        }
        for (session in pausedSessionsByAppId.values) {
            if (session.remainingSessionSeconds <= 0L) continue
            val group = resolveGroupForApp(config, session.app.id)
            guards[session.app.id] = SessionGuard(
                appId = session.app.id,
                groupId = group.id,
                cooldownSeconds = interruptedCooldownSeconds(session, group)
            )
        }
        store.saveSessionGuards(guards.values.toList())
    }

    private fun recoverInterruptedSessions() {
        val guards = store.getSessionGuards()
        if (guards.isEmpty()) return

        val config = store.getConfig()
        val today = timeProvider.currentEpochDay()
        val nowMs = timeProvider.elapsedRealtimeMs()

        for (guard in guards) {
            val cooldownMs = guard.cooldownSeconds.coerceAtLeast(0L) * 1000L
            if (cooldownMs <= 0L) continue

            val cooldownUntil = nowMs + cooldownMs
            val group = config.getEffectiveGroups().firstOrNull { it.id == guard.groupId }
            val usageKeys = buildList {
                add(guard.appId)
                if (group?.sharedQuota == true) add(groupUsageKey(group.id))
            }
            for (key in usageKeys) {
                val usage = store.getUsageState(key, today)
                if (usage.cooldownUntilRealtimeMs < cooldownUntil) {
                    store.saveUsageState(usage.copy(cooldownUntilRealtimeMs = cooldownUntil))
                }
            }
        }
        store.saveSessionGuards(emptyList())
    }

    private fun finalizeInterruptedSession(
        state: EngineState.InActiveSession,
        config: FocusConfig,
        todayEpochDay: Long,
        nowMs: Long
    ) {
        val group = resolveGroupForApp(config, state.app.id)
        val elapsedInSession = (state.initialSessionSeconds - state.remainingSessionSeconds).coerceAtLeast(0L)
        val flushedUsedDaily = state.baseUsedDailySeconds + elapsedInSession
        val flushedAppUsedDaily = state.baseAppUsedDailySeconds + elapsedInSession
        val cooldownMs = interruptedCooldownSeconds(state, group) * 1000L
        val cooldownUntil = if (cooldownMs > 0L) nowMs + cooldownMs else 0L

        val (usage, appUsage) = getEffectiveUsage(state.app.id, group, todayEpochDay, nowMs)
        store.saveUsageState(
            usage.copy(
                usedDailySeconds = maxOf(usage.usedDailySeconds, flushedUsedDaily),
                cooldownUntilRealtimeMs = maxOf(usage.cooldownUntilRealtimeMs, cooldownUntil)
            )
        )
        if (appUsage != null) {
            store.saveUsageState(
                appUsage.copy(
                    usedDailySeconds = maxOf(appUsage.usedDailySeconds, flushedAppUsedDaily),
                    cooldownUntilRealtimeMs = maxOf(appUsage.cooldownUntilRealtimeMs, cooldownUntil)
                )
            )
        }
    }

    private fun interruptedCooldownSeconds(state: EngineState.InActiveSession, group: FocusGroup): Long {
        return if (state.isQuickMicroSession) group.cooldownSeconds / 2L else group.cooldownSeconds
    }

    private fun getSanitizedUsageState(
        id: String,
        todayEpochDay: Long,
        nowRealtimeMs: Long,
        cooldownSeconds: Long
    ): AppUsageState {
        val raw = store.getUsageState(id, todayEpochDay)
        val maxPossibleCooldownMs = nowRealtimeMs + (cooldownSeconds * 1000L)

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
        private const val USAGE_FLUSH_INTERVAL_SECONDS = 15L
    }
}

data class SettingsLockStatus(
    val isLocked: Boolean,
    val reasonMessage: String?
)
