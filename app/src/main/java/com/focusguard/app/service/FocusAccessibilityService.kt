package com.focusguard.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import com.focusguard.app.data.FocusPreferencesStore
import com.focusguard.app.data.FocusRuntime
import com.focusguard.app.domain.FocusPolicyEngine
import com.focusguard.app.domain.InterventionAction
import com.focusguard.app.domain.SessionExitTracker
import com.focusguard.app.domain.SocialAppCatalog
import com.focusguard.app.domain.WindowExitArbiter
import java.lang.ref.WeakReference

/**
 * Serviço de Acessibilidade Ultraleve do FocusGuard.
 *
 * Princípios de Eficiência:
 * 1. Em estado ocioso (`Idle`), aplica filtro estrito em `serviceInfo.packageNames` contendo
 *    apenas os pacotes das redes sociais ativadas pelo usuário. O sistema operacional ignora
 *    eventos de todos os outros apps sem acordar o processo.
 * 2. Fluxo invertido: a ENTRADA no app monitorado continua orientada a eventos, mas a SAÍDA deixa
 *    de confiar no pacote que emitiu o evento. Durante uma sessão ativa o ticker de 1 Hz amostra a
 *    lista de janelas interativas (`getWindows()`) e resolve o primeiro plano pelas janelas
 *    `TYPE_APPLICATION`; teclados, autofill e painéis auxiliares da OEM nunca pausam o cronômetro.
 * 3. Pausa imediatamente a contagem caso a tela do aparelho seja desligada e restaura a barreira/sessão
 *    ao desbloquear a tela caso a rede social continue em primeiro plano.
 */
class FocusAccessibilityService : AccessibilityService() {

    private lateinit var store: FocusPreferencesStore
    private lateinit var policyEngine: FocusPolicyEngine
    private lateinit var overlayController: OverlayController
    private lateinit var ticker: MainThreadTicker
    private lateinit var powerManager: PowerManager
    private var keyguardManager: KeyguardManager? = null
    private var inputMethodManager: InputMethodManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var isListeningToAllPackages = false
    private var isScreenReceiverRegistered = false
    private val cachedImePackages = HashSet<String>(16)
    private val cachedLauncherPackages = HashSet<String>(4)
    private var isLauncherCacheInitialized = false
    private val cachedAppLaunchableState = HashMap<String, Boolean>(64)
    private var lastImeCacheRefreshMs = 0L
    private var lastWindowSnapshotMs = 0L
    private var cachedIsImeVisibleOnScreen = false
    private val cachedNonAppWindowIds = HashSet<Int>(8)

    private var currentForegroundPackage: String? = null
    private var lastMonitoredPackage: String? = null
    private var suppressedPackageOnHomeExit: String? = null
    private var suppressPackageUntilRealtimeMs: Long = 0L

    private val exitTracker = SessionExitTracker()

    private val delayedDismissRunnable = Runnable {
        overlayController.dismissAll()
    }

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    exitTracker.reset()
                    val offAction = policyEngine.onScreenTurnedOff()
                    applyInterventionAction(offAction)
                }
                Intent.ACTION_USER_PRESENT -> {
                    resumeMonitoredAppIfStillInForeground()
                }
                Intent.ACTION_SCREEN_ON -> {
                    if (keyguardManager?.isKeyguardLocked != true) {
                        resumeMonitoredAppIfStillInForeground()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = FocusRuntime.getStore(this)
        policyEngine = FocusRuntime.getEngine(this)
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        refreshImePackageCache()
        overlayController = OverlayController(
            context = this,
            onExitToHomeClicked = ::handleUserExitToHome,
            onConfirmSessionClicked = ::handleUserConfirmSession,
            onEndSessionEarlyClicked = ::handleUserEndSessionEarly
        )
        ticker = MainThreadTicker(onTick = ::handleSecondTick)
        registerScreenStateReceiver()
        activeInstanceRef = WeakReference(this)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        refreshImePackageCache()
        syncDynamicPackageFilter(forceTargetedOnly = true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }
        val pkgName = event.packageName?.toString() ?: return
        val clsName = event.className?.toString()

        // Ignora janelas do próprio overlay do FocusGuard, mas permite detectar abertura da MainActivity
        if (pkgName == packageName && clsName?.endsWith(".MainActivity") != true) {
            return
        }

        // Filtro barato de janelas transitórias (teclado, sistema, overlays): apenas uma otimização
        // para evitar trabalho desnecessário, não é mais o que sustenta a decisão de saída.
        if (isTransientPackageOrWindow(pkgName, clsName, event)) {
            return
        }

        val nowMs = SystemClock.elapsedRealtime()
        if (suppressedPackageOnHomeExit != null) {
            if (nowMs < suppressPackageUntilRealtimeMs && pkgName == suppressedPackageOnHomeExit) {
                return
            }
            if (pkgName != suppressedPackageOnHomeExit && !policyEngine.isTransientSystemPackage(pkgName)) {
                suppressedPackageOnHomeExit = null
                suppressPackageUntilRealtimeMs = 0L
            }
        }

        DebugEventLog.log("event") { "pkg=$pkgName cls=$clsName windowId=${event.windowId}" }

        // ENTRADA: abrir um app monitorado (ou um alias dele) recomeça/retoma a sessão.
        if (isMonitoredPackage(pkgName)) {
            lastMonitoredPackage = pkgName
            currentForegroundPackage = pkgName
            exitTracker.reset()
            val action = policyEngine.onWindowPackageChanged(pkgName, clsName)
            logAction(action)
            applyInterventionAction(action)
            return
        }

        // SAÍDA EXPLÍCITA: Launcher e o próprio dashboard do FocusGuard são inequívocos.
        if (isHomeLauncherPackage(pkgName) || pkgName == packageName) {
            currentForegroundPackage = pkgName
            exitTracker.reset()
            val action = policyEngine.onForegroundLost()
            logAction(action)
            applyInterventionAction(action)
            return
        }

        // Qualquer outra janela (IME, autofill, OEM, app de terceiros) nunca chega ao motor:
        // a saída é confirmada exclusivamente pela amostragem de janelas do ticker.
    }

    override fun onInterrupt() {
        policyEngine.onServiceStopped()
        exitTracker.reset()
        mainHandler.removeCallbacks(delayedDismissRunnable)
        ticker.stop()
        overlayController.dismissAll()
    }

    override fun onDestroy() {
        if (activeInstanceRef?.get() === this) {
            activeInstanceRef = null
        }
        policyEngine.onServiceStopped()
        unregisterScreenStateReceiver()
        exitTracker.reset()
        mainHandler.removeCallbacks(delayedDismissRunnable)
        ticker.stop()
        overlayController.dismissAll()
        super.onDestroy()
    }

    private fun handleUserExitToHome() {
        suppressCurrentMonitoredPackageOnExit()
        currentForegroundPackage = null
        exitTracker.reset()
        policyEngine.onUserAborted()
        performGlobalAction(GLOBAL_ACTION_HOME)
        ticker.stop()
        if (isListeningToAllPackages) {
            syncDynamicPackageFilter(forceTargetedOnly = true)
        }
        mainHandler.removeCallbacks(delayedDismissRunnable)
        mainHandler.postDelayed(delayedDismissRunnable, CELEBRATION_DISMISS_DELAY_MS)
    }

    private fun handleUserConfirmSession(isQuickMicroSession: Boolean) {
        val action = policyEngine.onConfirmStartSession(isQuickMicroSession)
        logAction(action)
        applyInterventionAction(action)
    }

    /**
     * Shows a demo barrier for the settings screen without mutating engine state.
     */
    fun previewBarrier(): Boolean {
        if (overlayController.isShowingBarrier()) {
            overlayController.dismissAll()
        }
        overlayController.renderPreview()
        return overlayController.isShowingBarrier()
    }

    /**
     * Ends the active or paused session triggered from the dashboard.
     */
    fun endSessionFromDashboard(): Boolean {
        val action = policyEngine.onUserEndedSessionFromDashboard()
        if (action is InterventionAction.NoOp) return false
        logAction(action)
        applyInterventionAction(action)
        return true
    }

    private fun handleUserEndSessionEarly() {
        suppressCurrentMonitoredPackageOnExit()
        currentForegroundPackage = null
        exitTracker.reset()
        val action = policyEngine.onUserEndedSessionEarly()
        logAction(action)
        applyInterventionAction(action)
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    /**
     * Amostragem de 1 Hz. A saída do app monitorado deixa de ser orientada a eventos: a cada
     * segundo, enquanto houver sessão em primeiro plano, a lista de janelas interativas é
     * resolvida em um veredito e o [SessionExitTracker] decide se a sessão deve pausar.
     */
    private fun handleSecondTick() {
        if (!powerManager.isInteractive) {
            exitTracker.reset()
            val offAction = policyEngine.onScreenTurnedOff()
            logAction(offAction)
            applyInterventionAction(offAction)
            return
        }

        if (policyEngine.hasForegroundSession()) {
            val monitoredPackages = monitoredSessionPackages()
            val snapshot = captureForegroundSnapshot()
            val verdict = WindowExitArbiter.evaluate(monitoredPackages, snapshot)
            DebugEventLog.log("tick") {
                "apps=${snapshot.applicationPackages} count=${snapshot.applicationWindowCount} " +
                    "ime=${snapshot.isImeVisible} verdict=$verdict"
            }
            if (exitTracker.onTick(verdict)) {
                exitTracker.reset()
                currentForegroundPackage = null
                DebugEventLog.log("exit") { "confirmed verdict=$verdict" }
                val action = policyEngine.onForegroundLost()
                logAction(action)
                applyInterventionAction(action)
                return
            }
            // O estado do motor manda: sessão ativa implica pílula na tela, sem depender de evento.
            overlayController.ensurePillAttached()
        } else {
            exitTracker.reset()
        }

        val action = policyEngine.onSecondTick()
        logAction(action)
        applyInterventionAction(action)
    }

    private fun resumeMonitoredAppIfStillInForeground() {
        val fgPkg = currentForegroundPackage ?: return
        if (isMonitoredPackage(fgPkg)) {
            exitTracker.reset()
            val action = policyEngine.onWindowPackageChanged(fgPkg)
            logAction(action)
            applyInterventionAction(action)
        }
    }

    private fun applyInterventionAction(action: InterventionAction) {
        if (action is InterventionAction.NoOp) {
            return
        }

        mainHandler.removeCallbacks(delayedDismissRunnable)

        if (action is InterventionAction.KickToHome) {
            suppressCurrentMonitoredPackageOnExit()
            currentForegroundPackage = null
        }

        overlayController.render(action)

        if (action is InterventionAction.KickToHome) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }

        if (policyEngine.shouldRunTicker()) {
            ticker.start()
        } else {
            ticker.stop()
            exitTracker.reset()
        }

        // Mantém a escuta ampla enquanto houver barreira ou sessão ativa em primeiro plano;
        // restringe o filtro às redes sociais ativas apenas quando realmente volta ao estado ocioso/pausado.
        val needsBroadExitDetection = policyEngine.shouldListenToAllPackages()

        if (needsBroadExitDetection && !isListeningToAllPackages) {
            syncDynamicPackageFilter(forceTargetedOnly = false)
        } else if (!needsBroadExitDetection && isListeningToAllPackages) {
            syncDynamicPackageFilter(forceTargetedOnly = true)
        }
    }

    private fun logAction(action: InterventionAction) {
        if (action is InterventionAction.NoOp) return
        // O render do cronômetro se repete a cada segundo e já está coberto pela linha "tick";
        // registrá-lo encheria o ring buffer de debug sem informação nova.
        if (action is InterventionAction.ShowFloatingTimer) return
        DebugEventLog.log("engine") { "action=${action::class.java.simpleName}" }
    }

    private fun registerScreenStateReceiver() {
        if (isScreenReceiverRegistered) return
        runCatching {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            registerReceiver(screenStateReceiver, filter)
            isScreenReceiverRegistered = true
        }
    }

    private fun unregisterScreenStateReceiver() {
        if (!isScreenReceiverRegistered) return
        runCatching {
            unregisterReceiver(screenStateReceiver)
        }
        isScreenReceiverRegistered = false
    }

    private fun suppressCurrentMonitoredPackageOnExit() {
        val pkg = lastMonitoredPackage ?: return
        suppressedPackageOnHomeExit = pkg
        suppressPackageUntilRealtimeMs = SystemClock.elapsedRealtime() + HOME_EXIT_BOUNCE_GUARD_MS
    }

    /**
     * Verifica se a janela que disparou o evento é um teclado virtual (`TYPE_INPUT_METHOD`),
     * painel do sistema (`TYPE_SYSTEM`), overlay (`TYPE_ACCESSIBILITY_OVERLAY`) ou um popup
     * exibido enquanto o teclado virtual está ativo na tela.
     */
    private fun isTransientWindowEvent(event: AccessibilityEvent): Boolean {
        val windowId = event.windowId
        val now = SystemClock.elapsedRealtime()

        if (now - lastWindowSnapshotMs > WINDOW_SNAPSHOT_TTL_MS) {
            lastWindowSnapshotMs = now
            val interactiveWindows = runCatching { windows }.getOrNull()
            if (interactiveWindows != null) {
                cachedNonAppWindowIds.clear()
                var imeFound = false
                for (window in interactiveWindows) {
                    if (window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                        imeFound = true
                    }
                    if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) {
                        cachedNonAppWindowIds.add(window.id)
                    }
                }
                cachedIsImeVisibleOnScreen = imeFound
            }
        }

        if (windowId >= 0 && windowId in cachedNonAppWindowIds) {
            return true
        }

        // Se o teclado virtual está na tela e o evento não é de um app de rede social alvo,
        // trata-se de janela transitória gerada pelo ecossistema do IME ou sistema.
        if (cachedIsImeVisibleOnScreen) {
            val pkg = event.packageName?.toString().orEmpty()
            if (!isMonitoredPackage(pkg)) {
                return true
            }
        }
        return false
    }

    private fun isHomeLauncherPackage(pkgName: String): Boolean {
        if (!isLauncherCacheInitialized) {
            runCatching {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                val resolveInfoList = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                for (resolveInfo in resolveInfoList) {
                    val p = resolveInfo.activityInfo?.packageName
                    if (!p.isNullOrBlank()) {
                        cachedLauncherPackages.add(p)
                    }
                }
                isLauncherCacheInitialized = true
            }
        }
        return pkgName in cachedLauncherPackages
    }

    private fun isLaunchableUserApp(pkgName: String): Boolean {
        cachedAppLaunchableState[pkgName]?.let { return it }
        val isLaunchable = runCatching {
            packageManager.getLaunchIntentForPackage(pkgName) != null
        }.getOrDefault(false)
        cachedAppLaunchableState[pkgName] = isLaunchable
        return isLaunchable
    }

    private fun isMonitoredPackage(pkgName: String): Boolean {
        if (!::store.isInitialized) return false
        val config = store.getConfig()
        return SocialAppCatalog.findByPackageName(pkgName, config.customApps) != null
    }

    /**
     * Pacotes do app que detém a sessão ativa, incluindo aliases (ex: Instagram e Instagram Lite).
     */
    private fun monitoredSessionPackages(): Set<String> {
        val pkg = lastMonitoredPackage ?: return emptySet()
        if (!::store.isInitialized) return setOf(pkg)
        val aliases = SocialAppCatalog.findByPackageName(pkg, store.getConfig().customApps)?.packageNames
        return if (aliases.isNullOrEmpty()) setOf(pkg) else aliases
    }

    /**
     * Fotografa as janelas interativas para descobrir quem realmente está em primeiro plano.
     * Executado no máximo uma vez por segundo e somente enquanto houver sessão ativa; a lista de
     * janelas é a única fonte de verdade, nunca o pacote que emitiu o evento.
     */
    private fun captureForegroundSnapshot(): WindowExitArbiter.ForegroundSnapshot {
        val interactiveWindows = runCatching { windows }.getOrNull()
            ?: return WindowExitArbiter.ForegroundSnapshot.UNKNOWN

        var isImeVisible = false
        var applicationWindowCount = 0
        val applicationPackages = ArrayList<String>(2)
        for (window in interactiveWindows) {
            when (window.type) {
                AccessibilityWindowInfo.TYPE_INPUT_METHOD -> isImeVisible = true
                AccessibilityWindowInfo.TYPE_APPLICATION -> {
                    applicationWindowCount++
                    val windowPackage = runCatching { window.root?.packageName?.toString() }.getOrNull()
                    if (!windowPackage.isNullOrBlank()) {
                        applicationPackages.add(windowPackage)
                    }
                }
            }
        }
        cachedIsImeVisibleOnScreen = isImeVisible
        return WindowExitArbiter.ForegroundSnapshot(
            applicationPackages = applicationPackages,
            applicationWindowCount = applicationWindowCount,
            isImeVisible = isImeVisible
        )
    }

    private fun isTransientPackageOrWindow(pkgName: String, clsName: String?, event: AccessibilityEvent): Boolean {
        // 1. Caminho ultra-rápido em O(1): Se for uma das redes sociais/apps monitorados, NUNCA é transitório
        if (isMonitoredPackage(pkgName)) {
            return false
        }

        // 2. Se for a tela de configurações do próprio FocusGuard, não é transitório
        if (pkgName == packageName) {
            return false
        }

        // 3. Se for o Launcher da tela inicial (One UI Home, Pixel Launcher, etc.), é saída real para o Home
        if (isHomeLauncherPackage(pkgName)) {
            return false
        }

        // 4. Verificações em memória (zero IPC): IMEs conhecidos, classes transitórias e pacotes de sistema
        if (isSystemInputMethodPackage(pkgName) ||
            policyEngine.isTransientSystemPackage(pkgName) ||
            policyEngine.isTransientWindowClassName(clsName)
        ) {
            return true
        }

        // 5. Se durante uma sessão ativa o pacote NÃO for um aplicativo inicializável pelo usuário
        // (ex: serviços internos de teclado, previsão, painéis flutuantes da OEM sem ícone no launcher),
        // trata-se de janela transitória sobre o app em primeiro plano.
        if (policyEngine.shouldListenToAllPackages() && !isLaunchableUserApp(pkgName)) {
            return true
        }

        // 6. Fallback final: inspeciona a hierarquia de janelas do sistema (IPC) apenas quando estritamente necessário
        if (isTransientWindowEvent(event)) {
            return true
        }

        return false
    }

    /**
     * Identifica qualquer teclado virtual (IME) instalado/ativo no aparelho consultando o InputMethodManager
     * e o `Settings.Secure.DEFAULT_INPUT_METHOD`, com cache leve para zero alocação em eventos repetidos.
     */
    private fun isSystemInputMethodPackage(pkgName: String): Boolean {
        if (pkgName in cachedImePackages) return true
        val nowMs = SystemClock.elapsedRealtime()
        if (nowMs - lastImeCacheRefreshMs > IME_CACHE_TTL_MS) {
            refreshImePackageCache()
            return pkgName in cachedImePackages
        }
        return false
    }

    private fun refreshImePackageCache() {
        lastImeCacheRefreshMs = SystemClock.elapsedRealtime()
        runCatching {
            // Teclados e auxiliares comuns pré-carregados para resposta imediata
            cachedImePackages.add("com.samsung.android.honeyboard")
            cachedImePackages.add("com.sec.android.inputmethod")
            cachedImePackages.add("com.samsung.android.smartsuggestions")
            cachedImePackages.add("com.samsung.android.scs")
            cachedImePackages.add("com.google.android.inputmethod.latin")
            cachedImePackages.add("com.touchtype.swiftkey")

            val defaultIme = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD
            )
            if (!defaultIme.isNullOrBlank()) {
                val imePkg = defaultIme.substringBefore('/')
                if (imePkg.isNotBlank()) {
                    cachedImePackages.add(imePkg)
                }
            }
            inputMethodManager?.enabledInputMethodList?.forEach { info ->
                if (info.packageName.isNotBlank()) {
                    cachedImePackages.add(info.packageName)
                }
            }
            inputMethodManager?.inputMethodList?.forEach { info ->
                if (info.packageName.isNotBlank()) {
                    cachedImePackages.add(info.packageName)
                }
            }
        }
    }

    /**
     * Atualiza a lista de pacotes monitorados diretamente no AccessibilityManager do Android.
     */
    fun reloadConfigAndFilter() {
        if (!::store.isInitialized) return
        store.invalidateConfigCache()
        syncDynamicPackageFilter(forceTargetedOnly = !isListeningToAllPackages)
    }

    private fun syncDynamicPackageFilter(forceTargetedOnly: Boolean) {
        val currentInfo = serviceInfo ?: AccessibilityServiceInfo()
        currentInfo.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        currentInfo.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        currentInfo.flags = AccessibilityServiceInfo.DEFAULT or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        currentInfo.notificationTimeout = 100L

        if (forceTargetedOnly) {
            val config = store.getConfig()
            val targetPackages = SocialAppCatalog.resolveAndroidPackagesForConfig(config)
            currentInfo.packageNames = if (targetPackages.isEmpty()) {
                arrayOf(NOOP_PACKAGE_SENTINEL)
            } else {
                targetPackages.toTypedArray()
            }
            isListeningToAllPackages = false
        } else {
            currentInfo.packageNames = null
            isListeningToAllPackages = true
        }

        serviceInfo = currentInfo
    }

    companion object {
        private const val NOOP_PACKAGE_SENTINEL = "com.focusguard.app.noop"
        private const val WINDOW_SNAPSHOT_TTL_MS = 300L
        private const val IME_CACHE_TTL_MS = 30_000L
        private const val HOME_EXIT_BOUNCE_GUARD_MS = 800L
        private const val CELEBRATION_DISMISS_DELAY_MS = 180L

        @Volatile
        private var activeInstanceRef: WeakReference<FocusAccessibilityService>? = null

        /**
         * Permite que a `MainActivity` notifique o serviço em execução imediatamente ao salvar alterações,
         * sem precisar de BroadcastReceivers pesados.
         */
        fun notifyConfigUpdated() {
            activeInstanceRef?.get()?.reloadConfigAndFilter()
        }

        fun isServiceRunning(): Boolean = activeInstanceRef?.get() != null

        /**
         * Renders the barrier in preview mode from the settings screen.
         * Returns false when the service is not connected.
         */
        fun showBarrierPreview(): Boolean {
            val service = activeInstanceRef?.get() ?: return false
            return service.previewBarrier()
        }

        fun endSessionFromDashboard(): Boolean {
            val service = activeInstanceRef?.get() ?: return false
            return service.endSessionFromDashboard()
        }
    }
}
