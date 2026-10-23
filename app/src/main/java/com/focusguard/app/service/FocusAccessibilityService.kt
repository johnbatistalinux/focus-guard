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
import com.focusguard.app.domain.SocialAppCatalog
import java.lang.ref.WeakReference

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

        // Ignora janelas de método de entrada (teclado virtual), sistema ou overlays de acessibilidade
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

        if (!policyEngine.isTransientSystemPackage(pkgName) && !policyEngine.isTransientWindowClassName(clsName)) {
            currentForegroundPackage = pkgName
        }

        if (isMonitoredPackage(pkgName)) {
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
        if (activeInstanceRef?.get() === this) {
            activeInstanceRef = null
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
        if (isMonitoredPackage(fgPkg)) {
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
    }
}
