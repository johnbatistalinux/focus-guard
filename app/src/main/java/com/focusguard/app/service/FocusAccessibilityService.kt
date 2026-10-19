package com.focusguard.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
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
 * 3. Pausa imediatamente a contagem caso a tela do aparelho seja desligada (`!powerManager.isInteractive`).
 */
class FocusAccessibilityService : AccessibilityService() {

    private lateinit var store: FocusPreferencesStore
    private lateinit var policyEngine: FocusPolicyEngine
    private lateinit var overlayController: OverlayController
    private lateinit var ticker: MainThreadTicker
    private lateinit var powerManager: PowerManager

    private var isListeningToAllPackages = false

    override fun onCreate() {
        super.onCreate()
        store = FocusRuntime.getStore(this)
        policyEngine = FocusRuntime.getEngine(this)
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        overlayController = OverlayController(
            context = this,
            onExitToHomeClicked = ::handleUserExitToHome,
            onConfirmSessionClicked = ::handleUserConfirmSession,
            onEndSessionEarlyClicked = ::handleUserEndSessionEarly
        )
        ticker = MainThreadTicker(onTick = ::handleSecondTick)
        activeInstance = this
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        syncDynamicPackageFilter(forceTargetedOnly = true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }
        val pkgName = event.packageName?.toString() ?: return
        if (pkgName == packageName) return

        val action = policyEngine.onWindowPackageChanged(pkgName)
        applyInterventionAction(action)
    }

    override fun onInterrupt() {
        ticker.stop()
        overlayController.dismissAll()
    }

    override fun onDestroy() {
        if (activeInstance === this) {
            activeInstance = null
        }
        ticker.stop()
        overlayController.dismissAll()
        super.onDestroy()
    }

    private fun handleUserExitToHome() {
        val action = policyEngine.onUserAborted()
        applyInterventionAction(action)
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private fun handleUserConfirmSession(isQuickMicroSession: Boolean) {
        val action = policyEngine.onConfirmStartSession(isQuickMicroSession)
        applyInterventionAction(action)
    }

    private fun handleUserEndSessionEarly() {
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

    private fun applyInterventionAction(action: InterventionAction) {
        overlayController.render(action)

        if (action is InterventionAction.KickToHome) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }

        if (policyEngine.shouldRunTicker()) {
            ticker.start()
        } else {
            ticker.stop()
        }

        // Quando há barreira ou sessão ativa, escuta qualquer troca de janela para pausar ao sair;
        // quando volta para DismissOverlay/KickToHome, restringe o filtro apenas às redes sociais ativas.
        val needsBroadExitDetection = action is InterventionAction.ShowMindfulPause ||
            action is InterventionAction.ShowFloatingTimer ||
            action is InterventionAction.ShowHardBlock

        if (needsBroadExitDetection && !isListeningToAllPackages) {
            syncDynamicPackageFilter(forceTargetedOnly = false)
        } else if (!needsBroadExitDetection && isListeningToAllPackages) {
            syncDynamicPackageFilter(forceTargetedOnly = true)
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
