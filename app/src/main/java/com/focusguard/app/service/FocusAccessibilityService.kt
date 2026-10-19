package com.focusguard.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import com.focusguard.app.domain.SocialAppCatalog

/**
 * Serviço de Acessibilidade Ultraleve do FocusGuard.
 *
 * Princípios de Eficiência:
 * 1. Em estado ocioso (`Idle`), aplica filtro estrito em `serviceInfo.packageNames` contendo
 *    apenas os pacotes das redes sociais ativadas pelo usuário. O sistema operacional ignora
 *    eventos de todos os outros apps sem acordar o processo.
 * 2. Quando uma sessão ou pausa está em andamento, expande temporariamente a escuta de janela
 *    para detectar imediatamente quando o usuário sai para o Launcher ou outro app (ex: WhatsApp),
 *    pausando o cronômetro e removendo o overlay na hora.
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
    private val cachedImePackages = HashSet<String>(8)
    private var lastImeCacheRefreshMs = 0L

    private var currentForegroundPackage: String? = null
    private var lastMonitoredPackage: String? = null
    private var suppressedPackageOnHomeExit: String? = null
    private var suppressPackageUntilRealtimeMs: Long = 0L

    private val delayedDismissRunnable = Runnable {
        overlayController.dismissAll()
    }

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
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
        activeInstance = this
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

        // Ignora janelas de método de entrada (teclado virtual), sistema ou overlays de acessibilidade
        if (isTransientWindowEvent(event) ||
            isSystemInputMethodPackage(pkgName) ||
            policyEngine.isTransientSystemPackage(pkgName) ||
            policyEngine.isTransientWindowClassName(clsName)
        ) {
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

        if (!policyEngine.isTransientSystemPackage(pkgName) && !policyEngine.isTransientWindowClassName(clsName)) {
            currentForegroundPackage = pkgName
        }

        if (SocialAppCatalog.findByPackageName(pkgName) != null) {
            lastMonitoredPackage = pkgName
        }

        val action = policyEngine.onWindowPackageChanged(pkgName, clsName)
        applyInterventionAction(action)
    }

    override fun onInterrupt() {
        mainHandler.removeCallbacks(delayedDismissRunnable)
        ticker.stop()
        overlayController.dismissAll()
    }

    override fun onDestroy() {
        if (activeInstance === this) {
            activeInstance = null
        }
        unregisterScreenStateReceiver()
        mainHandler.removeCallbacks(delayedDismissRunnable)
        ticker.stop()
        overlayController.dismissAll()
        super.onDestroy()
    }

    private fun handleUserExitToHome() {
        suppressCurrentMonitoredPackageOnExit()
        currentForegroundPackage = null
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
        applyInterventionAction(action)
    }

    private fun handleUserEndSessionEarly() {
        suppressCurrentMonitoredPackageOnExit()
        currentForegroundPackage = null
        val action = policyEngine.onUserEndedSessionEarly()
        applyInterventionAction(action)
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private fun handleSecondTick() {
        if (!powerManager.isInteractive) {
            val offAction = policyEngine.onScreenTurnedOff()
            applyInterventionAction(offAction)
            return
        }
        val action = policyEngine.onSecondTick()
        applyInterventionAction(action)
    }

    private fun resumeMonitoredAppIfStillInForeground() {
        val fgPkg = currentForegroundPackage ?: return
        if (SocialAppCatalog.findByPackageName(fgPkg) != null) {
            val action = policyEngine.onWindowPackageChanged(fgPkg)
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
     * não-fullscreen exibido enquanto o teclado virtual está aberto na tela.
     */
    private fun isTransientWindowEvent(event: AccessibilityEvent): Boolean {
        val windowId = event.windowId
        val interactiveWindows = runCatching { windows }.getOrNull() ?: return false
        var isImeVisibleOnScreen = false

        for (window in interactiveWindows) {
            if (window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                isImeVisibleOnScreen = true
            }
            if (windowId >= 0 && window.id == windowId && window.type != AccessibilityWindowInfo.TYPE_APPLICATION) {
                return true
            }
        }

        if (!event.isFullScreen && isImeVisibleOnScreen) {
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
            val enabledIds = store.getConfig().enabledAppIds
            val targetPackages = SocialAppCatalog.resolveAndroidPackages(enabledIds)
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
        private const val IME_CACHE_TTL_MS = 30_000L
        private const val HOME_EXIT_BOUNCE_GUARD_MS = 800L
        private const val CELEBRATION_DISMISS_DELAY_MS = 180L

        @Volatile
        private var activeInstance: FocusAccessibilityService? = null

        /**
         * Permite que a `MainActivity` notifique o serviço em execução imediatamente ao salvar alterações,
         * sem precisar de BroadcastReceivers pesados.
         */
        fun notifyConfigUpdated() {
            activeInstance?.reloadConfigAndFilter()
        }

        fun isServiceRunning(): Boolean = activeInstance != null
    }
}
