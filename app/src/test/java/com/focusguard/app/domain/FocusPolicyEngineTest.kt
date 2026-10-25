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
                selectedAnchorHabit = "Ler 5 páginas"
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
    fun `abrir teclado virtual ou autofill dentro do Instagram mantem cronometro e escuta ativos sem sumir`() {
        engine.onWindowPackageChanged("com.instagram.android")
        repeat(10) {
            fakeTime.advanceSeconds(1)
            engine.onSecondTick()
        }
        engine.onConfirmStartSession()
        assertTrue(engine.shouldRunTicker())
        assertTrue(engine.shouldListenToAllPackages())

        // Eventos emitidos ao abrir o teclado no Instagram (Gboard, Autofill do Android, System Intelligence e SoftInputWindow)
        assertEquals(InterventionAction.NoOp, engine.onWindowPackageChanged("com.google.android.inputmethod.latin"))
        assertEquals(InterventionAction.NoOp, engine.onWindowPackageChanged("android"))
        assertEquals(InterventionAction.NoOp, engine.onWindowPackageChanged("com.google.android.as"))
        assertEquals(InterventionAction.NoOp, engine.onWindowPackageChanged("com.android.credentialmanager"))
        assertEquals(
            InterventionAction.NoOp,
            engine.onWindowPackageChanged("org.custom.ime", "android.inputmethodservice.SoftInputWindow")
        )
        assertEquals(
            InterventionAction.NoOp,
            engine.onWindowPackageChanged("com.instagram.android", "android.widget.PopupWindow\$PopupDecorView")
        )

        // O cronômetro e a escuta de saída continuam 100% ativos durante a digitação
        assertTrue(engine.shouldRunTicker())
        assertTrue(engine.shouldListenToAllPackages())

        fakeTime.advanceSeconds(1)
        val tickAction = engine.onSecondTick() as InterventionAction.ShowFloatingTimer
        assertEquals(299L, tickAction.remainingSessionSeconds)
    }

    @Test
    fun `abrir teclado padrao da samsung e servicos auxiliares no Instagram mantem sessao ativa e cronometro visivel`() {
        engine.onWindowPackageChanged("com.instagram.android")
        repeat(10) {
            fakeTime.advanceSeconds(1)
            engine.onSecondTick()
        }
        engine.onConfirmStartSession()
        assertTrue(engine.shouldRunTicker())
        assertTrue(engine.shouldListenToAllPackages())

        // Samsung Honeyboard principal
        assertEquals(InterventionAction.NoOp, engine.onWindowPackageChanged("com.samsung.android.honeyboard"))
        assertEquals(InterventionAction.NoOp, engine.onWindowPackageChanged("com.sec.android.inputmethod"))

        // Samsung Smart Suggestions (helper que abre sobre o teclado Samsung One UI)
        assertEquals(InterventionAction.NoOp, engine.onWindowPackageChanged("com.samsung.android.smartsuggestions"))
        assertEquals(InterventionAction.NoOp, engine.onWindowPackageChanged("com.samsung.android.scs"))

        // Componentes e painéis auxiliares do teclado Samsung
        assertEquals(InterventionAction.NoOp, engine.onWindowPackageChanged("com.samsung.android.icecone"))
        assertEquals(InterventionAction.NoOp, engine.onWindowPackageChanged("com.samsung.android.neatart"))
        assertEquals(InterventionAction.NoOp, engine.onWindowPackageChanged("com.samsung.android.sdk.handwriting"))
        assertEquals(InterventionAction.NoOp, engine.onWindowPackageChanged("com.samsung.android.stickercenter"))
        assertEquals(InterventionAction.NoOp, engine.onWindowPackageChanged("com.samsung.android.aremoji"))
        assertEquals(InterventionAction.NoOp, engine.onWindowPackageChanged("com.samsung.android.intelligence"))
        assertEquals(InterventionAction.NoOp, engine.onWindowPackageChanged("com.samsung.android.bixby.service"))
        assertEquals(InterventionAction.NoOp, engine.onWindowPackageChanged("com.samsung.android.app.clipboardedge"))

        // Classes de janela do teclado e sugestões Samsung
        assertEquals(
            InterventionAction.NoOp,
            engine.onWindowPackageChanged("com.samsung.android.honeyboard", "com.samsung.android.honeyboard.ui.KeyboardScreenView")
        )
        assertEquals(
            InterventionAction.NoOp,
            engine.onWindowPackageChanged("com.samsung.android.smartsuggestions", "com.samsung.android.smartsuggestions.SuggestionsView")
        )

        // O cronômetro continua rodando e a escuta ampla permanece ativa
        assertTrue(engine.shouldRunTicker())
        assertTrue(engine.shouldListenToAllPackages())

        fakeTime.advanceSeconds(1)
        val tickAction = engine.onSecondTick() as InterventionAction.ShowFloatingTimer
        assertEquals(299L, tickAction.remainingSessionSeconds)
    }

    @Test
    fun `hasForegroundSession reflete sessao ativa pausada e encerrada`() {
        assertFalse(engine.hasForegroundSession())

        engine.onWindowPackageChanged("com.instagram.android")
        repeat(10) {
            fakeTime.advanceSeconds(1)
            engine.onSecondTick()
        }
        engine.onConfirmStartSession()
        assertTrue(engine.hasForegroundSession())

        // Saída real para um app não monitorado pausa a sessão em segundo plano.
        engine.onWindowPackageChanged("com.whatsapp")
        assertFalse(engine.hasForegroundSession())

        // Voltar ao Instagram retoma a sessão em primeiro plano.
        engine.onWindowPackageChanged("com.instagram.android")
        assertTrue(engine.hasForegroundSession())

        engine.onUserEndedSessionEarly()
        assertFalse(engine.hasForegroundSession())
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
        assertEquals("Fechar e ir: Ler 5 páginas", openAction.anchorActionLabel)
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
                remainingDailyQuotaSeconds = 1800L,
                totalSessionSeconds = 300L
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
                remainingDailyQuotaSeconds = 1740L,
                totalSessionSeconds = 240L
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
                remainingDailyQuotaSeconds = 1L,
                totalSessionSeconds = 1L
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
        assertEquals("Iniciante consciente", summary.focusLevelTitle)
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

    @Test
    fun `alternar entre dois apps monitorados preserva sessao pausada sem creditar saida voluntaria falsa`() {
        fakeStore.saveConfig(
            fakeStore.getConfig().copy(enabledAppIds = setOf("instagram", "tiktok"))
        )

        // Inicia sessão de 5 min (300s) no Instagram e usa por 30s (restam 270s)
        engine.onWindowPackageChanged("com.instagram.android")
        repeat(10) {
            fakeTime.advanceSeconds(1)
            engine.onSecondTick()
        }
        engine.onConfirmStartSession()
        repeat(30) {
            fakeTime.advanceSeconds(1)
            engine.onSecondTick()
        }

        // Enquanto a sessão está ativa, configurações ficam travadas (Bug #10)
        assertTrue(engine.getSettingsLockStatus().isLocked)

        // Alterna direto para o TikTok: pausa o Instagram e abre Pausa Consciente do TikTok
        val tiktokPause = engine.onWindowPackageChanged("com.zhiliaoapp.musically")
        assertTrue(tiktokPause is InterventionAction.ShowMindfulPause)
        assertEquals("tiktok", (tiktokPause as InterventionAction.ShowMindfulPause).appId)

        // Mesmo na barreira do TikTok, a sessão pausada do Instagram mantém as configurações travadas
        assertTrue(engine.getSettingsLockStatus().isLocked)

        // Desiste do TikTok após 5s e volta ao Instagram dentro da janela de 45s
        fakeTime.advanceSeconds(5)
        engine.onUserAborted()

        val resumedInstagram = engine.onWindowPackageChanged("com.instagram.android")
        assertEquals(
            InterventionAction.ShowFloatingTimer(
                appId = "instagram",
                displayName = "Instagram",
                remainingSessionSeconds = 270L,
                remainingDailyQuotaSeconds = 1770L,
                totalSessionSeconds = 270L
            ),
            resumedInstagram
        )
        // Não deve ter creditado saída voluntária antecipada falsa no Instagram
        assertEquals(0, engine.getRewardSummary().voluntaryExitsToday)
    }

    @Test
    fun `fim de cooldown em ShowHardBlock transiciona para DAILY_QUOTA_EXCEEDED quando cota diaria zerou`() {
        // Entra na tela de bloqueio por Cooldown ativo (2s restantes)
        fakeStore.saveUsageState(
            AppUsageState(
                appId = "instagram",
                epochDay = 20_000L,
                usedDailySeconds = 1500L,
                cooldownUntilRealtimeMs = fakeTime.elapsedRealtimeMs() + 2_000L,
                sessionsStartedToday = 5
            )
        )

        val initialBlock = engine.onWindowPackageChanged("com.instagram.android") as InterventionAction.ShowHardBlock
        assertEquals(BlockReason.COOLDOWN_ACTIVE, initialBlock.reason)
        assertEquals(2L, initialBlock.remainingBlockSeconds)

        fakeTime.advanceSeconds(1)
        val tick1 = engine.onSecondTick() as InterventionAction.ShowHardBlock
        assertEquals(BlockReason.COOLDOWN_ACTIVE, tick1.reason)
        assertEquals(1L, tick1.remainingBlockSeconds)

        // Simula cota diária atingida (1800s) no momento em que o cooldown zera
        fakeStore.saveUsageState(
            AppUsageState(
                appId = "instagram",
                epochDay = 20_000L,
                usedDailySeconds = 1800L,
                cooldownUntilRealtimeMs = fakeTime.elapsedRealtimeMs() + 1_000L,
                sessionsStartedToday = 6
            )
        )

        // Quando o cooldown zera mas a cota diária está esgotada, transiciona para DAILY_QUOTA_EXCEEDED em vez de ShowMindfulPause
        fakeTime.advanceSeconds(1)
        val tick2 = engine.onSecondTick() as InterventionAction.ShowHardBlock
        assertEquals(BlockReason.DAILY_QUOTA_EXCEEDED, tick2.reason)
    }

    @Test
    fun `distingue MainActivity do FocusGuard de janelas de overlay e ignora classes de teclado ou popup`() {
        engine.onWindowPackageChanged("com.instagram.android")
        repeat(10) {
            fakeTime.advanceSeconds(1)
            engine.onSecondTick()
        }
        engine.onConfirmStartSession()

        // Evento de overlay do próprio FocusGuard (sem className de MainActivity) -> NoOp
        assertEquals(
            InterventionAction.NoOp,
            engine.onWindowPackageChanged("com.focusguard.app", className = "android.widget.FrameLayout")
        )

        // Evento de janela transitória (SoftInputWindow / PopupWindow) mesmo com pacote desconhecido -> NoOp
        assertEquals(
            InterventionAction.NoOp,
            engine.onWindowPackageChanged("com.oem.customkeyboard", className = "android.inputmethodservice.SoftInputWindow")
        )

        // Abrir a MainActivity real do FocusGuard pausa a sessão e oculta o overlay para não cobrir a UI
        assertEquals(
            InterventionAction.DismissOverlay,
            engine.onWindowPackageChanged("com.focusguard.app", className = "com.focusguard.app.ui.MainActivity")
        )
    }

    @Test
    fun `focus presets apply correct values within validation limits`() {
        val initialConfig = FocusConfig()

        val balanced = FocusPreset.BALANCED.applyTo(initialConfig)
        assertEquals(10, balanced.mindfulPauseSeconds)
        assertEquals(5, balanced.sessionDurationMinutes)
        assertEquals(10, balanced.cooldownMinutes)
        assertEquals(30, balanced.dailyQuotaMinutes)

        val deep = FocusPreset.DEEP_FOCUS.applyTo(initialConfig)
        assertEquals(20, deep.mindfulPauseSeconds)
        assertEquals(3, deep.sessionDurationMinutes)
        assertEquals(20, deep.cooldownMinutes)
        assertEquals(15, deep.dailyQuotaMinutes)

        val light = FocusPreset.LIGHT_DETOX.applyTo(initialConfig)
        assertEquals(5, light.mindfulPauseSeconds)
        assertEquals(10, light.sessionDurationMinutes)
        assertEquals(5, light.cooldownMinutes)
        assertEquals(60, light.dailyQuotaMinutes)
    }

    @Test
    fun `catalog apps define valid hex brand colors`() {
        assertTrue(SocialAppCatalog.SUPPORTED_APPS.isNotEmpty())
        for (app in SocialAppCatalog.SUPPORTED_APPS) {
            assertTrue(app.brandColorHex.startsWith("#"), "App ${app.id} must have a valid hex color")
            assertEquals(7, app.brandColorHex.length, "App ${app.id} brandColorHex must be in format #RRGGBB")
        }
    }

    @Test
    fun `cota compartilhada no mesmo grupo bloqueia outros apps do grupo ao ser esgotada`() {
        val socialGroup = FocusGroup(
            id = "group_social_test",
            name = "Redes Sociais",
            iconKey = "social",
            enabled = true,
            appIds = setOf("instagram", "tiktok"),
            mindfulPauseSeconds = 5,
            sessionDurationMinutes = 5,
            cooldownMinutes = 2,
            dailyQuotaMinutes = 10,
            progressivePauseEnabled = false,
            sharedQuota = true
        )
        fakeStore.saveConfig(
            FocusConfig(
                groups = listOf(socialGroup)
            )
        )

        // 1. Abre Instagram e faz a pausa de 5s
        engine.onWindowPackageChanged("com.instagram.android")
        repeat(5) {
            fakeTime.advanceSeconds(1)
            engine.onSecondTick()
        }
        engine.onConfirmStartSession(isQuickMicroSession = false)

        // Usa Instagram por 300 segundos (5 minutos)
        repeat(300) {
            fakeTime.advanceSeconds(1)
            engine.onSecondTick()
        }

        // Aguarda passar o cooldown de 2 minutos (120s)
        fakeTime.advanceSeconds(121)

        // 2. Abre o TikTok do mesmo grupo: a cota restante deve ser 300s (5 minutos restantes de 10)
        val tiktokPause = engine.onWindowPackageChanged("com.zhiliaoapp.musically") as InterventionAction.ShowMindfulPause
        assertEquals(300L, tiktokPause.remainingDailyQuotaSeconds)
        repeat(5) {
            fakeTime.advanceSeconds(1)
            engine.onSecondTick()
        }
        engine.onConfirmStartSession(isQuickMicroSession = false)

        // Usa TikTok pelos 300s restantes
        repeat(300) {
            fakeTime.advanceSeconds(1)
            engine.onSecondTick()
        }

        // 3. A cota total de 10 min do grupo foi esgotada! Tentar abrir o Instagram agora deve gerar HardBlock imediato
        val blockedInstagram = engine.onWindowPackageChanged("com.instagram.android")
        assertTrue(blockedInstagram is InterventionAction.ShowHardBlock, "Was $blockedInstagram")
        assertEquals(BlockReason.DAILY_QUOTA_EXCEEDED, (blockedInstagram as InterventionAction.ShowHardBlock).reason)
        assertEquals("Redes Sociais", blockedInstagram.groupName)
    }

    @Test
    fun `isolamento entre grupos distintos nao propaga bloqueio de cota esgotada`() {
        val customGameApp = TargetApp(
            id = "custom_brawl",
            displayName = "Brawl Stars",
            packageNames = setOf("com.supercell.brawlstars"),
            isCustom = true
        )
        val socialGroup = FocusGroup(
            id = "group_social_test",
            name = "Redes Sociais",
            enabled = true,
            appIds = setOf("instagram"),
            dailyQuotaMinutes = 5,
            sharedQuota = true
        )
        val gamesGroup = FocusGroup(
            id = "group_games_test",
            name = "Jogos",
            iconKey = "games",
            enabled = true,
            appIds = setOf("custom_brawl"),
            dailyQuotaMinutes = 20,
            sharedQuota = true
        )
        fakeStore.saveConfig(
            FocusConfig(
                groups = listOf(socialGroup, gamesGroup),
                customApps = listOf(customGameApp)
            )
        )

        // Esgota o grupo de Redes Sociais no Instagram
        engine.onWindowPackageChanged("com.instagram.android")
        repeat(10) {
            fakeTime.advanceSeconds(1)
            engine.onSecondTick()
        }
        engine.onConfirmStartSession()
        repeat(300) {
            fakeTime.advanceSeconds(1)
            engine.onSecondTick()
        }

        // Instagram agora está bloqueado por cota diária
        val instagramBlocked = engine.onWindowPackageChanged("com.instagram.android")
        assertTrue(instagramBlocked is InterventionAction.ShowHardBlock)

        // Abrir o jogo Brawl Stars do grupo Jogos deve abrir a Pausa Consciente normalmente
        val gameAction = engine.onWindowPackageChanged("com.supercell.brawlstars")
        assertTrue(gameAction is InterventionAction.ShowMindfulPause)
        val pause = gameAction as InterventionAction.ShowMindfulPause
        assertEquals("Brawl Stars", pause.displayName)
        assertEquals("Jogos", pause.groupName)
        assertEquals(1200L, pause.remainingDailyQuotaSeconds)
    }

    @Test
    fun `trava anti-sabotagem por grupo bloqueia alteracoes durante sessao ativa em app customizado`() {
        val customApp = TargetApp(
            id = "custom_game",
            displayName = "Meu Jogo",
            packageNames = setOf("com.example.game"),
            isCustom = true
        )
        val gamesGroup = FocusGroup(
            id = "group_games",
            name = "Jogos",
            appIds = setOf("custom_game"),
            sharedQuota = true
        )
        fakeStore.saveConfig(
            FocusConfig(
                groups = listOf(gamesGroup),
                customApps = listOf(customApp)
            )
        )

        engine.onWindowPackageChanged("com.example.game")
        repeat(10) {
            fakeTime.advanceSeconds(1)
            engine.onSecondTick()
        }
        engine.onConfirmStartSession()

        val lockStatus = engine.getSettingsLockStatus()
        assertTrue(lockStatus.isLocked)
        assertTrue(lockStatus.reasonMessage?.contains("Meu Jogo") == true)
    }

    @Test
    fun `pausar e retomar sessao multiplas vezes nao duplica contagem de cota diaria`() {
        // Cota diária = 30 min (1800s), Sessão = 5 min (300s)
        engine.onWindowPackageChanged("com.instagram.android")
        repeat(10) {
            fakeTime.advanceSeconds(1)
            engine.onSecondTick()
        }
        engine.onConfirmStartSession()

        // Segmento 1: Usa por 60 segundos
        repeat(60) {
            fakeTime.advanceSeconds(1)
            val tick = engine.onSecondTick() as InterventionAction.ShowFloatingTimer
            assertEquals(300L - (it + 1), tick.remainingSessionSeconds)
            assertEquals(1800L - (it + 1), tick.remainingDailyQuotaSeconds)
        }

        // Sai para a Home por 20 segundos
        engine.onWindowPackageChanged("com.android.launcher3")
        fakeTime.advanceSeconds(20)

        // Retoma o Instagram: restam 240s da sessão e 1740s da cota
        val resume1 = engine.onWindowPackageChanged("com.instagram.android") as InterventionAction.ShowFloatingTimer
        assertEquals(240L, resume1.remainingSessionSeconds)
        assertEquals(1740L, resume1.remainingDailyQuotaSeconds)

        // Segmento 2: Usa por mais 60 segundos (total usado: 120s)
        repeat(60) {
            fakeTime.advanceSeconds(1)
            val tick = engine.onSecondTick() as InterventionAction.ShowFloatingTimer
            assertEquals(240L - (it + 1), tick.remainingSessionSeconds)
            assertEquals(1740L - (it + 1), tick.remainingDailyQuotaSeconds)
        }

        // Sai para a Home pela 2ª vez por 15 segundos
        engine.onWindowPackageChanged("com.android.launcher3")
        fakeTime.advanceSeconds(15)

        // Retoma o Instagram pela 2ª vez: restam 180s da sessão e 1680s da cota (1800 - 120)
        val resume2 = engine.onWindowPackageChanged("com.instagram.android") as InterventionAction.ShowFloatingTimer
        assertEquals(180L, resume2.remainingSessionSeconds)
        assertEquals(1680L, resume2.remainingDailyQuotaSeconds)

        // Segmento 3: Usa por mais 30 segundos (total usado: 150s)
        repeat(30) {
            fakeTime.advanceSeconds(1)
            val tick = engine.onSecondTick() as InterventionAction.ShowFloatingTimer
            assertEquals(180L - (it + 1), tick.remainingSessionSeconds)
            assertEquals(1680L - (it + 1), tick.remainingDailyQuotaSeconds)
        }

        // Verifica estado persistido no storage: 150 segundos exatos consumidos hoje
        val finalUsage = fakeStore.getUsageState("instagram", fakeTime.currentEpochDay())
        assertEquals(150L, finalUsage.usedDailySeconds)
    }

    @Test
    fun `encerrar o servico finaliza sessao ativa e aplica cooldown ao reabrir`() {
        engine.onWindowPackageChanged("com.instagram.android")
        repeat(10) {
            fakeTime.advanceSeconds(1)
            engine.onSecondTick()
        }
        engine.onConfirmStartSession()
        repeat(90) {
            fakeTime.advanceSeconds(1)
            engine.onSecondTick()
        }

        engine.onServiceStopped()
        assertFalse(engine.shouldRunTicker())
        assertTrue(fakeStore.getSessionGuards().isEmpty())
        assertEquals(90L, fakeStore.getUsageState("instagram", fakeTime.currentEpochDay()).usedDailySeconds)

        val reopened = FocusPolicyEngine(store = fakeStore, timeProvider = fakeTime)
            .onWindowPackageChanged("com.instagram.android") as InterventionAction.ShowHardBlock
        assertEquals(BlockReason.COOLDOWN_ACTIVE, reopened.reason)
        assertEquals(600L, reopened.remainingBlockSeconds)
    }

    @Test
    fun `recupera sessao interrompida por morte do processo aplicando cooldown e limpando guardas`() {
        engine.onWindowPackageChanged("com.instagram.android")
        repeat(10) {
            fakeTime.advanceSeconds(1)
            engine.onSecondTick()
        }
        engine.onConfirmStartSession()
        repeat(30) {
            fakeTime.advanceSeconds(1)
            engine.onSecondTick()
        }

        // O processo morre sem onServiceStopped: a guarda persistida continua no storage
        assertTrue(fakeStore.getSessionGuards().any { it.appId == "instagram" })

        val recovered = FocusPolicyEngine(store = fakeStore, timeProvider = fakeTime)
        assertTrue(fakeStore.getSessionGuards().isEmpty())

        val blocked = recovered.onWindowPackageChanged("com.instagram.android") as InterventionAction.ShowHardBlock
        assertEquals(BlockReason.COOLDOWN_ACTIVE, blocked.reason)
        assertEquals(600L, blocked.remainingBlockSeconds)
    }

    @Test
    fun `app customizado com pacote que parece transiente e interceptado normalmente`() {
        val customApp = TargetApp(
            id = "custom_vision",
            displayName = "Vision Board",
            packageNames = setOf("com.example.visionboard"),
            isCustom = true
        )
        val customGroup = FocusGroup(
            id = "group_custom",
            name = "Custom",
            enabled = true,
            appIds = setOf("custom_vision"),
            mindfulPauseSeconds = 5,
            sessionDurationMinutes = 5,
            dailyQuotaMinutes = 30,
            sharedQuota = false
        )
        fakeStore.saveConfig(
            FocusConfig(
                groups = listOf(customGroup),
                customApps = listOf(customApp)
            )
        )

        val action = engine.onWindowPackageChanged("com.example.visionboard")
        assertTrue(action is InterventionAction.ShowMindfulPause, "Was $action")
        assertEquals("custom_vision", (action as InterventionAction.ShowMindfulPause).appId)
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
        private val sessionGuards = mutableListOf<SessionGuard>()

        override fun getConfig(): FocusConfig = config

        override fun saveConfig(config: FocusConfig) {
            this.config = config
        }

        override fun getUsageState(appId: String, currentEpochDay: Long): AppUsageState {
            val existing = usageByApp[appId]
            if (existing == null || existing.epochDay < currentEpochDay) {
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

        override fun getSessionGuards(): List<SessionGuard> = sessionGuards.toList()

        override fun saveSessionGuards(guards: List<SessionGuard>) {
            sessionGuards.clear()
            sessionGuards.addAll(guards)
        }
    }
}
