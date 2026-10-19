package com.focusguard.app.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FocusPolicyEngineTest {

    private lateinit var fakeTime: FakeTimeProvider
    private lateinit var fakeStore: InMemoryPolicyStore
    private lateinit var engine: FocusPolicyEngine

    @BeforeEach
    fun setUp() {
        fakeTime = FakeTimeProvider(initialRealtimeMs = 100_000L, initialEpochDay = 20_000L)
        fakeStore = InMemoryPolicyStore(
            FocusConfig(
                enabledAppIds = setOf("instagram"),
                mindfulPauseSeconds = 10,
                sessionDurationMinutes = 5,
                cooldownMinutes = 10,
                dailyQuotaMinutes = 30,
                progressivePauseEnabled = true,
                selectedAnchorHabit = "Ler 5 páginas 📖"
            )
        )
        engine = FocusPolicyEngine(store = fakeStore, timeProvider = fakeTime)
    }

    @Test
    fun `ignora aplicativos nao monitorados e pacotes de teclado virtual ou dialogos de sistema`() {
        val whatsappAction = engine.onWindowPackageChanged("com.whatsapp")
        assertEquals(InterventionAction.DismissOverlay, whatsappAction)
        assertFalse(engine.shouldRunTicker())

        val keyboardAction = engine.onWindowPackageChanged("com.google.android.inputmethod.latin")
        assertEquals(InterventionAction.NoOp, keyboardAction)

        val swiftKeyAction = engine.onWindowPackageChanged("com.touchtype.swiftkey")
        assertEquals(InterventionAction.NoOp, swiftKeyAction)

        val samsungKeyboardAction = engine.onWindowPackageChanged("com.samsung.android.honeyboard")
        assertEquals(InterventionAction.NoOp, samsungKeyboardAction)

        val permissionControllerAction = engine.onWindowPackageChanged("com.google.android.permissioncontroller")
        assertEquals(InterventionAction.NoOp, permissionControllerAction)
    }

    @Test
    fun `onScreenTurnedOff pausa a sessao imediatamente impedindo gasto de cota com a tela apagada`() {
        engine.onWindowPackageChanged("com.instagram.android")
        repeat(10) {
            fakeTime.advanceSeconds(1)
            engine.onSecondTick()
        }
        engine.onConfirmStartSession()

        // Usa por 30 segundos (restam 270s da sessão)
        repeat(30) {
            fakeTime.advanceSeconds(1)
            engine.onSecondTick()
        }

        // Usuário desliga a tela do celular com o Instagram aberto
        val screenOffAction = engine.onScreenTurnedOff()
        assertEquals(InterventionAction.DismissOverlay, screenOffAction)
        assertFalse(engine.shouldRunTicker())

        // Passam 120 segundos com o celular no bolso (não deve descontar nada)
        fakeTime.advanceSeconds(120)
        assertEquals(InterventionAction.NoOp, engine.onSecondTick())

        // Usuário liga a tela de volta no Instagram: retoma exatamente de 270s
        val resumeAction = engine.onWindowPackageChanged("com.instagram.android") as InterventionAction.ShowFloatingTimer
        assertEquals(270L, resumeAction.remainingSessionSeconds)
    }

    @Test
    fun `credita saida voluntaria automaticamente quando sessao pausada expira em segundo plano`() {
        engine.onWindowPackageChanged("com.instagram.android")
        repeat(10) {
            fakeTime.advanceSeconds(1)
            engine.onSecondTick()
        }
        engine.onConfirmStartSession() // 300s

        // Usa apenas 120s (2 min) e sai para a Home (sobram 180s = 3 min não gastos)
        repeat(120) {
            fakeTime.advanceSeconds(1)
            engine.onSecondTick()
        }
        engine.onWindowPackageChanged("com.android.launcher3")

        // Fica fora por 10 minutos (600s > graceLimitMs de 300s)
        fakeTime.advanceSeconds(600)

        // Quando abre o Instagram mais tarde, os 3 min poupados na sessão anterior foram creditados!
        engine.onWindowPackageChanged("com.instagram.android")
        val summary = engine.getRewardSummary()
        assertEquals(1, summary.voluntaryExitsToday)
        assertEquals(3L, summary.reclaimedMinutesToday)
    }

    @Test
    fun `fluxo completo - pausa consciente de 10s libera sessao de 5 min ou micro-sessao`() {
        // 1. Usuário abre o Instagram
        val openAction = engine.onWindowPackageChanged("com.instagram.android") as InterventionAction.ShowMindfulPause
        assertEquals("instagram", openAction.appId)
        assertEquals(10, openAction.remainingPauseSeconds)
        assertFalse(openAction.readyToStartSession)
        assertEquals("Fechar e ir: Ler 5 páginas 📖", openAction.anchorActionLabel)
        assertFalse(openAction.allowQuickMicroSession)
        assertTrue(engine.shouldRunTicker())

        // Tentativa prematura de confirmar antes dos 10s deve ser ignorada
        assertEquals(InterventionAction.NoOp, engine.onConfirmStartSession())

        // 2. Passam 9 segundos
        repeat(9) {
            fakeTime.advanceSeconds(1)
            engine.onSecondTick()
        }

        // 3. No 10º segundo, os botões de iniciar sessão (completa e 1 min) são habilitados
        fakeTime.advanceSeconds(1)
        val readyAction = engine.onSecondTick() as InterventionAction.ShowMindfulPause
        assertEquals(0, readyAction.remainingPauseSeconds)
        assertTrue(readyAction.readyToStartSession)
        assertTrue(readyAction.allowQuickMicroSession)
        assertFalse(engine.shouldRunTicker())

        // 4. Usuário confirma início da sessão completa de 5 minutos (300s)
        val sessionAction = engine.onConfirmStartSession(isQuickMicroSession = false)
        assertEquals(
            InterventionAction.ShowFloatingTimer(
                appId = "instagram",
                displayName = "Instagram",
                remainingSessionSeconds = 300L,
                remainingDailyQuotaSeconds = 1800L
            ),
            sessionAction
        )
        assertTrue(engine.shouldRunTicker())
    }

    @Test
    fun `pausa o cronometro quando usuario sai para o Launcher e retoma ao voltar`() {
        engine.onWindowPackageChanged("com.instagram.android")
        repeat(10) {
            fakeTime.advanceSeconds(1)
            engine.onSecondTick()
        }
        engine.onConfirmStartSession()

        // Usa por 60 segundos (restam 240s da sessão e 1740s da cota diária)
        repeat(60) {
            fakeTime.advanceSeconds(1)
            engine.onSecondTick()
        }

        // Sai para o Launcher / Home
        val exitAction = engine.onWindowPackageChanged("com.android.launcher3")
        assertEquals(InterventionAction.DismissOverlay, exitAction)
        assertFalse(engine.shouldRunTicker())

        // Passam 30 segundos fora do Instagram (não deve descontar da sessão nem da cota)
        fakeTime.advanceSeconds(30)
        assertEquals(InterventionAction.NoOp, engine.onSecondTick())

        // Volta para o Instagram dentro da janela: retoma de 240s sem exigir nova pausa de 10s
        val resumeAction = engine.onWindowPackageChanged("com.instagram.android")
        assertEquals(
            InterventionAction.ShowFloatingTimer(
                appId = "instagram",
                displayName = "Instagram",
                remainingSessionSeconds = 240L,
                remainingDailyQuotaSeconds = 1740L
            ),
            resumeAction
        )
        assertTrue(engine.shouldRunTicker())
    }

    @Test
    fun `expulsa para a Home ao fim da sessao de 5 min e aplica Cooldown de 10 min`() {
        engine.onWindowPackageChanged("com.instagram.android")
        repeat(10) {
            fakeTime.advanceSeconds(1)
            engine.onSecondTick()
        }
        engine.onConfirmStartSession()

        // Consome 299 segundos da sessão de 300s
        repeat(299) {
            fakeTime.advanceSeconds(1)
            engine.onSecondTick()
        }

        // No 300º segundo, a sessão expira e expulsa para a Home com Cooldown ativo
        fakeTime.advanceSeconds(1)
        val kickAction = engine.onSecondTick()
        assertEquals(
            InterventionAction.KickToHome(appId = "instagram", reason = BlockReason.COOLDOWN_ACTIVE),
            kickAction
        )

        // Trava anti-sabotagem nas configurações deve estar ATIVA durante o cooldown
        assertTrue(engine.getSettingsLockStatus().isLocked)

        // Se o usuário tentar reabrir 2 segundos depois, recebe ShowHardBlock de Cooldown
        fakeTime.advanceSeconds(2)
        val reopenBlocked = engine.onWindowPackageChanged("com.instagram.android")
        assertEquals(
            InterventionAction.ShowHardBlock(
                appId = "instagram",
                displayName = "Instagram",
                reason = BlockReason.COOLDOWN_ACTIVE,
                remainingBlockSeconds = 598L
            ),
            reopenBlocked
        )

        // Após os 10 minutos completos de Cooldown, o app volta a permitir a Pausa Consciente
        // Com Pausa Progressiva ativa (1 sessão concluída hoje), a 2ª pausa passa de 10s para 15s!
        fakeTime.advanceSeconds(600)
        assertFalse(engine.getSettingsLockStatus().isLocked)
        val reopenAfterCooldown = engine.onWindowPackageChanged("com.instagram.android") as InterventionAction.ShowMindfulPause
        assertEquals(15, reopenAfterCooldown.remainingPauseSeconds)
    }

    @Test
    fun `bloqueia pelo resto do dia quando a Cota Diaria de 30 min esgota e reseta no dia seguinte`() {
        fakeStore.saveUsageState(
            AppUsageState(
                appId = "instagram",
                epochDay = fakeTime.currentEpochDay(),
                usedDailySeconds = 1799L
            )
        )

        engine.onWindowPackageChanged("com.instagram.android")
        repeat(10) {
            fakeTime.advanceSeconds(1)
            engine.onSecondTick()
        }

        // A sessão liberada será truncada para apenas 1 segundo restante da cota diária
        val startAction = engine.onConfirmStartSession()
        assertEquals(
            InterventionAction.ShowFloatingTimer(
                appId = "instagram",
                displayName = "Instagram",
                remainingSessionSeconds = 1L,
                remainingDailyQuotaSeconds = 1L
            ),
            startAction
        )

        // Após 1 segundo, estoura a cota diária e expulsa com DAILY_QUOTA_EXCEEDED
        fakeTime.advanceSeconds(1)
        val kickAction = engine.onSecondTick()
        assertEquals(
            InterventionAction.KickToHome(appId = "instagram", reason = BlockReason.DAILY_QUOTA_EXCEEDED),
            kickAction
        )

        // Configurações ficam travadas contra sabotagem
        assertTrue(engine.getSettingsLockStatus().isLocked)

        // Virada do dia (meia-noite -> novo epochDay) reseta a cota automaticamente (lazy reset)
        fakeTime.advanceDays(1)
        fakeTime.advanceSeconds(600) // além do cooldown anterior
        assertFalse(engine.getSettingsLockStatus().isLocked)

        val nextDayOpen = engine.onWindowPackageChanged("com.instagram.android") as InterventionAction.ShowMindfulPause
        assertEquals(10, nextDayOpen.remainingPauseSeconds)
        assertEquals(1800L, nextDayOpen.remainingDailyQuotaSeconds)
    }

    @Test
    fun `reforco positivo contabiliza desistencias na porta, minutos resgatados e equivalentes reais`() {
        // 3 aberturas por impulso seguidas de desistência consciente na barreira
        repeat(3) {
            engine.onWindowPackageChanged("com.instagram.android")
            engine.onUserAborted()
        }

        val summary = engine.getRewardSummary()
        assertEquals(3, summary.attemptsToday)
        assertEquals(3, summary.abortedToday)
        assertEquals(100, summary.masteryPercentToday)
        // 3 desistências * 5 min por sessão = 15 min resgatados
        assertEquals(15L, summary.reclaimedMinutesToday)
        assertEquals(15L, summary.totalReclaimedMinutes)
        assertTrue(summary.tangibleEquivalentText.contains("páginas lidas"))
        assertEquals("Iniciante Consciente 🌱", summary.focusLevelTitle)
    }

    @Test
    fun `micro sessao de 1 min e bonus de saida voluntaria reduzem cooldown e somam tempo poupado`() {
        // Abre Instagram, aguarda 10s e escolhe Micro-Sessão de 1 min (60s)
        engine.onWindowPackageChanged("com.instagram.android")
        repeat(10) {
            fakeTime.advanceSeconds(1)
            engine.onSecondTick()
        }

        val microStart = engine.onConfirmStartSession(isQuickMicroSession = true) as InterventionAction.ShowFloatingTimer
        assertEquals(60L, microStart.remainingSessionSeconds)

        // Usa por apenas 20 segundos e aciona Saída Voluntária Antecipada (restavam 40s)
        repeat(20) {
            fakeTime.advanceSeconds(1)
            engine.onSecondTick()
        }
        val earlyExitAction = engine.onUserEndedSessionEarly()
        assertEquals(InterventionAction.DismissOverlay, earlyExitAction)

        // Como usou menos de 60s e saiu voluntariamente, fica isento de Cooldown!
        assertFalse(engine.getSettingsLockStatus().isLocked)
        val summary = engine.getRewardSummary()
        assertEquals(1, summary.voluntaryExitsToday)

        // Agora inicia uma sessão completa de 5 min (300s), usa por 120s (2 min) e encerra voluntariamente (sobram 180s = 3 min)
        engine.onWindowPackageChanged("com.instagram.android")
        repeat(15) { // 2ª sessão do dia = 15s de pausa progressiva
            fakeTime.advanceSeconds(1)
            engine.onSecondTick()
        }
        engine.onConfirmStartSession(isQuickMicroSession = false)
        repeat(120) {
            fakeTime.advanceSeconds(1)
            engine.onSecondTick()
        }
        engine.onUserEndedSessionEarly()

        // Como usou >= 60s mas saiu antes do fim, ganha 50% de desconto no Cooldown (5 min = 300s em vez de 600s)
        val reopenDuringHalfCooldown = engine.onWindowPackageChanged("com.instagram.android") as InterventionAction.ShowHardBlock
        assertEquals(BlockReason.COOLDOWN_ACTIVE, reopenDuringHalfCooldown.reason)
        assertEquals(300L, reopenDuringHalfCooldown.remainingBlockSeconds)
    }

    private class FakeTimeProvider(
        initialRealtimeMs: Long,
        initialEpochDay: Long
    ) : TimeProvider {
        private var realtimeMs: Long = initialRealtimeMs
        private var epochDay: Long = initialEpochDay

        override fun elapsedRealtimeMs(): Long = realtimeMs
        override fun currentEpochDay(): Long = epochDay

        fun advanceSeconds(seconds: Long) {
            realtimeMs += seconds * 1000L
        }

        fun advanceDays(days: Long) {
            epochDay += days
        }
    }

    private class InMemoryPolicyStore(
        private var config: FocusConfig
    ) : FocusPolicyStore {
        private val usageByApp = mutableMapOf<String, AppUsageState>()
        private var rewardState: FocusRewardState? = null

        override fun getConfig(): FocusConfig = config

        override fun saveConfig(config: FocusConfig) {
            this.config = config
        }

        override fun getUsageState(appId: String, currentEpochDay: Long): AppUsageState {
            val existing = usageByApp[appId]
            if (existing == null || existing.epochDay != currentEpochDay) {
                val preservedCooldown = existing?.cooldownUntilRealtimeMs ?: 0L
                val fresh = AppUsageState(
                    appId = appId,
                    epochDay = currentEpochDay,
                    cooldownUntilRealtimeMs = preservedCooldown
                )
                usageByApp[appId] = fresh
                return fresh
            }
            return existing
        }

        override fun saveUsageState(state: AppUsageState) {
            usageByApp[state.appId] = state
        }

        override fun getRewardState(currentEpochDay: Long): FocusRewardState {
            val current = rewardState
            if (current == null) {
                val initial = FocusRewardState(epochDay = currentEpochDay)
                rewardState = initial
                return initial
            }
            if (current.epochDay != currentEpochDay) {
                val delta = (currentEpochDay - current.epochDay).coerceIn(1L, 62L).toInt()
                val newMask = (current.consistencyBitmask shl delta) or ((1L shl delta) - 1L)
                val rolled = FocusRewardState(
                    epochDay = currentEpochDay,
                    totalReclaimedSeconds = current.totalReclaimedSeconds,
                    totalImpulsesBeaten = current.totalImpulsesBeaten,
                    consistencyBitmask = newMask,
                    trackedDaysCount = (current.trackedDaysCount + delta).coerceIn(1, 62)
                )
                rewardState = rolled
                return rolled
            }
            return current
        }

        override fun saveRewardState(state: FocusRewardState) {
            rewardState = state
        }
    }
}
