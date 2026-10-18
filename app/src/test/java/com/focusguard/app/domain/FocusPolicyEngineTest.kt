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
                dailyQuotaMinutes = 30
            )
        )
        engine = FocusPolicyEngine(store = fakeStore, timeProvider = fakeTime)
    }

    @Test
    fun `ignora aplicativos nao monitorados e pacotes de teclado virtual`() {
        val whatsappAction = engine.onWindowPackageChanged("com.whatsapp")
        assertEquals(InterventionAction.DismissOverlay, whatsappAction)
        assertFalse(engine.shouldRunTicker())

        val keyboardAction = engine.onWindowPackageChanged("com.google.android.inputmethod.latin")
        assertEquals(InterventionAction.NoOp, keyboardAction)
    }

    @Test
    fun `fluxo completo - pausa consciente de 10s libera sessao de 5 min`() {
        // 1. Usuário abre o Instagram
        val openAction = engine.onWindowPackageChanged("com.instagram.android")
        assertEquals(
            InterventionAction.ShowMindfulPause(
                appId = "instagram",
                displayName = "Instagram",
                remainingPauseSeconds = 10,
                readyToStartSession = false,
                remainingDailyQuotaSeconds = 1800L
            ),
            openAction
        )
        assertTrue(engine.shouldRunTicker())

        // Tentativa prematura de confirmar antes dos 10s deve ser ignorada
        assertEquals(InterventionAction.NoOp, engine.onConfirmStartSession())

        // 2. Passam 9 segundos
        repeat(9) {
            fakeTime.advanceSeconds(1)
            engine.onSecondTick()
        }

        // 3. No 10º segundo, o botão de iniciar sessão é habilitado
        fakeTime.advanceSeconds(1)
        val readyAction = engine.onSecondTick()
        assertEquals(
            InterventionAction.ShowMindfulPause(
                appId = "instagram",
                displayName = "Instagram",
                remainingPauseSeconds = 0,
                readyToStartSession = true,
                remainingDailyQuotaSeconds = 1800L
            ),
            readyAction
        )
        assertFalse(engine.shouldRunTicker())

        // 4. Usuário confirma início da sessão de 5 minutos (300s)
        val sessionAction = engine.onConfirmStartSession()
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
        // Abre Instagram e conclui a pausa de 10s
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
        fakeTime.advanceSeconds(600)
        assertFalse(engine.getSettingsLockStatus().isLocked)
        val reopenAfterCooldown = engine.onWindowPackageChanged("com.instagram.android")
        assertTrue(reopenAfterCooldown is InterventionAction.ShowMindfulPause)
    }

    @Test
    fun `bloqueia pelo resto do dia quando a Cota Diaria de 30 min esgota e reseta no dia seguinte`() {
        // Simula que o usuário já usou 29 min e 59s hoje (1799s de 1800s)
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

        val nextDayOpen = engine.onWindowPackageChanged("com.instagram.android")
        assertEquals(
            InterventionAction.ShowMindfulPause(
                appId = "instagram",
                displayName = "Instagram",
                remainingPauseSeconds = 10,
                readyToStartSession = false,
                remainingDailyQuotaSeconds = 1800L
            ),
            nextDayOpen
        )
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

        override fun getConfig(): FocusConfig = config

        override fun saveConfig(config: FocusConfig) {
            this.config = config
        }

        override fun getUsageState(appId: String, currentEpochDay: Long): AppUsageState {
            val existing = usageByApp[appId]
            if (existing == null || existing.epochDay != currentEpochDay) {
                val fresh = AppUsageState(appId = appId, epochDay = currentEpochDay)
                usageByApp[appId] = fresh
                return fresh
            }
            return existing
        }

        override fun saveUsageState(state: AppUsageState) {
            usageByApp[state.appId] = state
        }
    }
}
