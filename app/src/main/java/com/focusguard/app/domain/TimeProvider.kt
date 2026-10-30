package com.focusguard.app.domain

/**
 * Abstração de relógio para permitir testes unitários 100% determinísticos sem depender do Android SDK.
 */
interface TimeProvider {
    /**
     * Retorna o tempo monotônico desde o boot em milissegundos (`SystemClock.elapsedRealtime()`).
     * Imune a alterações manuais do relógio do sistema pelo usuário.
     */
    fun elapsedRealtimeMs(): Long

    /**
     * Retorna o dia atual (`LocalDate.now().toEpochDay()`) para controle de reset diário à meia-noite.
     */
    fun currentEpochDay(): Long

    /**
     * Retorna o minuto atual do dia local (`0..1439`, onde `0` = `00:00` e `720` = `12:00`),
     * usado na avaliação pura do Toque de Recolher Circadiano.
     */
    fun currentMinuteOfDay(): Int = 720
}

/**
 * Guarda persistente de uma sessão ativa no momento em que o processo/serviço morreu.
 * Permite aplicar o cooldown devido na próxima inicialização, evitando burlar a barreira
 * ao encerrar o AccessibilityService à força.
 */
data class SessionGuard(
    val appId: String,
    val groupId: String,
    val cooldownSeconds: Long
)

/**
 * Contrato de leitura/escrita de configurações, estado de uso e reforço positivo.
 */
interface FocusPolicyStore {
    fun getConfig(): FocusConfig
    fun saveConfig(config: FocusConfig)
    fun getUsageState(appId: String, currentEpochDay: Long): AppUsageState
    fun saveUsageState(state: AppUsageState)
    fun getRewardState(currentEpochDay: Long): FocusRewardState
    fun saveRewardState(state: FocusRewardState)
    fun getSessionGuards(): List<SessionGuard>
    fun saveSessionGuards(guards: List<SessionGuard>)
}
